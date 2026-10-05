// Client du Worker de configuration cloud (même protocole que CloudPairingClient.kt sur Android) :
//   POST /api/pair/start   {label}                  -> {code, pollSecret, expiresIn, interval}
//   POST /api/pair/poll    {code, pollSecret}       -> 202 en attente · 404/410 expiré · 200 {token, deviceId}
//   GET  /api/config       Bearer (If-None-Match)   -> {version, devices, providers[]} · 304 · 401 jeton révoqué
//   POST /api/device/rotate Bearer                  -> {token, deviceId}
//   POST /api/device/providers Bearer               -> ajoute / met à jour un fournisseur du compte
//   DELETE /api/device/providers/:id Bearer
//   PATCH /api/device {name} Bearer                  -> renomme l'appareil courant
// HTTPS obligatoire, aucune redirection suivie (un 30x vers http:// ferait fuiter le jeton).
// Dans Electron, les requêtes sont faites par le processus principal (`window.ultratv.cloudRequest`).

import { bridge } from "@/net/transport";

export const DEFAULT_WORKER = "https://ultratv-config.khalilbenaz.workers.dev";

export class TokenRejectedError extends Error { constructor() { super("token-rejected"); this.name = "TokenRejectedError"; } }
export class RateLimitedError extends Error { constructor(public retryAfterSec: number) { super("rate-limited"); this.name = "RateLimitedError"; } }
export class NotFoundError extends Error { constructor() { super("not-found"); this.name = "NotFoundError"; } }
export class InvalidFieldError extends Error { constructor(public field: string) { super(`invalid:${field}`); this.name = "InvalidFieldError"; } }
export class CloudError extends Error { constructor(message: string, public status = 0) { super(message); this.name = "CloudError"; } }

/** URL de base du Worker : HTTPS (HTTP seulement vers la boucle locale, pour les tests). */
export function normalizeWorkerUrl(raw: string, allowLoopbackHttp = false): string | null {
  const s = raw.trim().replace(/\/+$/, "");
  let u: URL;
  try { u = new URL(s); } catch { return null; }
  const loop = ["127.0.0.1", "localhost", "[::1]"].includes(u.hostname);
  if (u.protocol !== "https:" && !(allowLoopbackHttp && loop && u.protocol === "http:")) return null;
  if (!u.hostname || u.username || u.password || u.search || u.hash) return null;
  return s;
}

export interface HttpRequest { url: string; method?: string; headers?: Record<string, string>; body?: string }
export interface HttpResult { status: number; text: string; retryAfter: string; etag: string }
export type CloudHttp = (req: HttpRequest) => Promise<HttpResult>;

const defaultHttp: CloudHttp = async (req) => {
  const b = bridge() as { cloudRequest?: CloudHttp } | undefined;
  if (b?.cloudRequest) return b.cloudRequest(req);
  const res = await fetch(req.url, { method: req.method ?? "GET", headers: req.headers, body: req.body, redirect: "manual" });
  return { status: res.status, text: await res.text(), retryAfter: res.headers.get("retry-after") ?? "", etag: res.headers.get("etag") ?? "" };
};

let http: CloudHttp = defaultHttp;
/** Remplace le transport (tests). */
export function setCloudHttp(h: CloudHttp | null) { http = h ?? defaultHttp; }

/** Le cloud n'est proposé que dans l'application de bureau (le Worker n'émet pas d'en-têtes CORS). */
export const cloudAvailable = () => !!(bridge() as { cloudRequest?: unknown } | undefined)?.cloudRequest;

const json = (o: unknown) => JSON.stringify(o);
const HJSON = { "content-type": "application/json", accept: "application/json" };

function common(r: HttpResult) {
  if (r.status === 429) throw new RateLimitedError(parseInt(r.retryAfter, 10) || 30);
  if (r.status >= 300 && r.status < 400 && r.status !== 304) throw new CloudError("redirect", r.status);
}
function parse<T>(r: HttpResult): T {
  try { return JSON.parse(r.text) as T; } catch { throw new CloudError("bad-json", r.status); }
}

export interface PairingSession { code: string; pollSecret: string; expiresInSec: number; intervalSec: number }
export type PollResult = { state: "pending" } | { state: "gone" } | { state: "paired"; token: string; deviceId: string };

export async function startPairing(base: string, label: string): Promise<PairingSession> {
  const r = await http({ url: `${base}/api/pair/start`, method: "POST", headers: HJSON, body: json({ label }) });
  common(r);
  if (r.status < 200 || r.status >= 300) throw new CloudError("pair-start", r.status);
  const o = parse<{ code: string; pollSecret: string; expiresIn?: number; interval?: number }>(r);
  if (!o.code || !o.pollSecret) throw new CloudError("bad-response", r.status);
  return { code: o.code, pollSecret: o.pollSecret, expiresInSec: o.expiresIn ?? 600, intervalSec: o.interval ?? 3 };
}

export async function pollPairing(base: string, s: PairingSession): Promise<PollResult> {
  const r = await http({ url: `${base}/api/pair/poll`, method: "POST", headers: HJSON, body: json({ code: s.code, pollSecret: s.pollSecret }) });
  common(r);
  if (r.status === 202) return { state: "pending" };
  if (r.status === 404 || r.status === 410) return { state: "gone" };
  if (r.status === 200) {
    const o = parse<{ token: string; deviceId: string }>(r);
    if (!o.token) throw new CloudError("bad-response", r.status);
    return { state: "paired", token: o.token, deviceId: o.deviceId };
  }
  throw new CloudError("pair-poll", r.status);
}

export interface CloudProvider {
  id: string;
  kind: string;
  name: string;
  url: string;
  username?: string;
  password?: string;
  originDeviceId?: string;
  originName?: string;
  createdAt?: number;
  updatedAt?: number;
  /** Affectations (quand le Worker les expose) : « all » ou identifiants d'appareils. */
  sharedWith?: string[] | "all";
  /** Réglages d'affichage partagés entre appareils (null = jamais publiés). */
  prefs?: CloudPrefs | null;
}
/** Langues en minuscules (« fr », « other », « multi ») ; null = toutes. `disabled` : identifiants de catégorie du fournisseur. */
export interface CloudPrefs { langs: string[] | null; disabled: { live: string[]; movie: string[]; series: string[] }; updatedAt: number; by?: string }
export interface CloudDevice { id: string; name: string; model?: string; lastSeen?: number; isCurrent?: boolean }
export interface CloudConfig { version: number; devices: number | CloudDevice[]; providers: CloudProvider[]; deviceName?: string; /** Identifiant de l'appareil courant. */ self?: string }

export type ConfigResult = { unchanged: true } | { unchanged: false; config: CloudConfig; etag: string };

const auth = (token: string, extra: Record<string, string> = {}) => ({ authorization: `Bearer ${token}`, accept: "application/json", ...extra });

export async function fetchConfig(base: string, token: string, etag?: string | null): Promise<ConfigResult> {
  const r = await http({ url: `${base}/api/config`, headers: auth(token, etag ? { "if-none-match": etag } : {}) });
  if (r.status === 401) throw new TokenRejectedError();
  common(r);
  if (r.status === 304) return { unchanged: true };
  if (r.status < 200 || r.status >= 300) throw new CloudError("config", r.status);
  const o = parse<Partial<CloudConfig>>(r);
  return {
    unchanged: false, etag: r.etag,
    config: { version: o.version ?? 0, devices: o.devices ?? 0, providers: Array.isArray(o.providers) ? o.providers : [], deviceName: o.deviceName, self: o.self },
  };
}

export async function rotateToken(base: string, token: string): Promise<{ token: string; deviceId: string }> {
  const r = await http({ url: `${base}/api/device/rotate`, method: "POST", headers: auth(token), body: "" });
  if (r.status === 401) throw new TokenRejectedError();
  common(r);
  if (r.status < 200 || r.status >= 300) throw new CloudError("rotate", r.status);
  return parse<{ token: string; deviceId: string }>(r);
}

export interface ProviderInput {
  id?: string;
  kind: "XTREAM" | "M3U";
  name: string;
  url: string;
  username?: string;
  password?: string;
  /** « all » ou liste d'identifiants d'appareils. */
  shareWith?: "all" | string[];
}

/** Ajoute (ou met à jour avec `id`) un fournisseur du compte. 404 sans `id` = endpoint absent du Worker. */
export async function putProvider(base: string, token: string, input: ProviderInput): Promise<CloudProvider> {
  const r = await http({ url: `${base}/api/device/providers`, method: "POST", headers: auth(token, { "content-type": "application/json" }), body: json(input) });
  if (r.status === 401) throw new TokenRejectedError();
  common(r);
  if (r.status === 404) throw new NotFoundError();
  if (r.status === 400) {
    const o = (() => { try { return JSON.parse(r.text) as { field?: string }; } catch { return {}; } })() as { field?: string };
    throw new InvalidFieldError(o.field ?? "unknown");
  }
  if (r.status === 409) throw new CloudError("limit", 409);
  if (r.status < 200 || r.status >= 300) throw new CloudError("put-provider", r.status);
  const o = parse<{ provider?: CloudProvider }>(r);
  if (!o.provider?.id) throw new CloudError("bad-response", r.status);
  return o.provider;
}

export type PutPrefsResult = { status: "ok" } | { status: "stale"; prefs: CloudPrefs } | { status: "unknown" };

/** Publie les réglages d'affichage d'une source. 409 « stale » : le cloud a une version plus récente (à appliquer). */
export async function putPrefs(base: string, token: string, providerId: string, prefs: Omit<CloudPrefs, "by">): Promise<PutPrefsResult> {
  const r = await http({ url: `${base}/api/device/providers/${encodeURIComponent(providerId)}/prefs`, method: "PUT", headers: auth(token, { "content-type": "application/json" }), body: json(prefs) });
  if (r.status === 401) throw new TokenRejectedError();
  common(r);
  if (r.status === 404) return { status: "unknown" };
  if (r.status === 409) {
    const o = parse<{ prefs?: CloudPrefs }>(r);
    if (!o.prefs) throw new CloudError("bad-response", 409);
    return { status: "stale", prefs: o.prefs };
  }
  if (r.status < 200 || r.status >= 300) throw new CloudError("put-prefs", r.status);
  return { status: "ok" };
}

export interface SharedFav { p: string; k: string; r: string; on: boolean; at: number }
export interface SharedHist { p: string; k: string; r: string; t: string; img: string | null; pos: number; dur: number; at: number; par: string | null }
export interface SharedState { fav: SharedFav[]; hist: SharedHist[] }

/** Échange l'état partagé d'une source (favoris, reprises, derniers vus) ; renvoie l'état fusionné, null si inconnue. */
export async function syncState(base: string, token: string, providerId: string, body: SharedState): Promise<SharedState | null> {
  const r = await http({ url: `${base}/api/device/providers/${encodeURIComponent(providerId)}/state`, method: "POST", headers: auth(token, { "content-type": "application/json" }), body: json(body) });
  if (r.status === 401) throw new TokenRejectedError();
  common(r);
  if (r.status === 404) return null;
  if (r.status < 200 || r.status >= 300) throw new CloudError("sync-state", r.status);
  const o = parse<Partial<SharedState>>(r);
  return { fav: Array.isArray(o.fav) ? o.fav : [], hist: Array.isArray(o.hist) ? o.hist : [] };
}

/** Renomme l'appareil courant dans le compte (le tableau de bord et les autres appareils le voient). */
export async function renameDevice(base: string, token: string, name: string): Promise<void> {
  const r = await http({ url: `${base}/api/device`, method: "PATCH", headers: auth(token, { "content-type": "application/json" }), body: json({ name }) });
  if (r.status === 401) throw new TokenRejectedError();
  common(r);
  if (r.status === 404) throw new NotFoundError();
  if (r.status < 200 || r.status >= 300) throw new CloudError("rename", r.status);
}

export async function deleteProvider(base: string, token: string, id: string): Promise<void> {
  const r = await http({ url: `${base}/api/device/providers/${encodeURIComponent(id)}`, method: "DELETE", headers: auth(token) });
  if (r.status === 401) throw new TokenRejectedError();
  common(r);
  if (r.status === 404) throw new NotFoundError();
  if (r.status < 200 || r.status >= 300) throw new CloudError("delete-provider", r.status);
}

/** « K7Q2M9XF » → « K7Q2-M9XF » ; un code déjà tiré est conservé. */
export const groupPairingCode = (code: string) =>
  code.includes("-") || code.length < 6 ? code : `${code.slice(0, code.length / 2)}-${code.slice(code.length / 2)}`;

export type PairingEvent =
  | { type: "code"; code: string; expiresInSec: number }
  | { type: "paired"; token: string; deviceId: string }
  | { type: "expired" }
  | { type: "failed"; error: unknown };

/** Déroulé de l'appairage : code -> interrogation jusqu'à la saisie sur le tableau de bord. */
export async function runPairing(
  base: string, label: string, onEvent: (e: PairingEvent) => void, signal: AbortSignal,
  sleep: (ms: number) => Promise<void> = (ms) => new Promise((r) => setTimeout(r, ms)),
): Promise<void> {
  let session: PairingSession;
  try { session = await startPairing(base, label); } catch (error) { onEvent({ type: "failed", error }); return; }
  onEvent({ type: "code", code: session.code, expiresInSec: session.expiresInSec });
  let waited = 0;
  const limit = session.expiresInSec * 1000;
  let interval = Math.min(30, Math.max(1, session.intervalSec)) * 1000;
  while (waited <= limit) {
    await sleep(interval);
    if (signal.aborted) return;
    waited += interval;
    try {
      const r = await pollPairing(base, session);
      if (r.state === "gone") { onEvent({ type: "expired" }); return; }
      if (r.state === "paired") { onEvent({ type: "paired", token: r.token, deviceId: r.deviceId }); return; }
    } catch (e) {
      if (e instanceof RateLimitedError) { interval = Math.min(60, Math.max(1, e.retryAfterSec)) * 1000; continue; }
      onEvent({ type: "failed", error: e });
      return;
    }
  }
  onEvent({ type: "expired" });
}


/** Recherche TMDB via le proxy du Worker (clés côté serveur). Renvoie le chemin d'affiche (`/abc.jpg`) ou null. */
export async function tmdbPosterPath(base: string, token: string, kind: "movie" | "tv", query: string, year?: number | null, language = "fr-FR"): Promise<string | null> {
  const q = new URLSearchParams({ query: query.slice(0, 120), language });
  if (year && year >= 1900 && year <= 2099) q.set(kind === "tv" ? "first_air_date_year" : "year", String(year));
  const r = await http({ url: `${base}/api/tmdb/search/${kind}?${q}`, headers: auth(token) });
  if (r.status === 401) throw new TokenRejectedError();
  common(r);
  if (r.status < 200 || r.status >= 300) return null;
  const o = parse<{ results?: { poster_path?: string | null }[] }>(r);
  const hit = (o.results ?? []).find((x) => typeof x.poster_path === "string" && /^\/[\w.-]+$/.test(x.poster_path));
  return hit?.poster_path ?? null;
}
