// Plex : logique pure (en-têtes, code PIN plex.tv/link, serveurs du compte et choix de la connexion, analyse des réponses,
// adresses de lecture, reprise, rapports `/:/timeline`) puis client réseau qui n'en est qu'une fine couche. Le client
// présente la même interface que celui de Jellyfin / Emby (`MediaApi`, voir mediaServer.ts) : synchro, fiches et lecteur
// n'ont pas à distinguer les deux.
//
// Stockage d'une source (sans changement de schéma) : `type` = "plex", `server` = adresse de la connexion choisie du serveur,
// `username` = identifiant de la machine du serveur, `password` = jeton d'accès DU SERVEUR (chiffré au repos, voir db/sources.ts).
// Le jeton n'est JAMAIS stocké dans une adresse ni journalisé : il est ajouté en paramètre `X-Plex-Token` à l'instant de la
// requête, de l'affichage d'une image ou de la lecture (un navigateur ne sait pas poser d'en-tête sur <img> ni <video>).
// Les lignes du catalogue gardent un lien interne `media:<ratingKey>`.

import { getSetting, setSetting } from "@/db/db";
import type { Source } from "@/db/types";
import { HttpError, transportFetch, type Transport } from "./transport";
import { normalizeServerUrl, PAGE_SIZE, type Item, type Library, type MediaApi, type MediaCreds, type ReportKind } from "./mediaServer";

export const PLEX_PRODUCT = "Ultra TV";
export const PLEX_LINK_URL = "https://plex.tv/link";
const PINS_URL = "https://plex.tv/api/v2/pins?strong=false";
const RESOURCES_URL = "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1";
/** Un code PIN expire au bout d'environ 15 minutes. */
export const PIN_TTL_MS = 15 * 60 * 1000;
export const PIN_POLL_MS = 2_500;
const MS_TO_TICKS = 10_000;

// ---------------------------------------------------------------- identifiant d'installation

const CLIENT_ID_KEY = "plex.clientId";

/** Identifiant stable de cette installation (en-tête X-Plex-Client-Identifier), créé à la première utilisation. */
export async function plexClientId(): Promise<string> {
  const cur = await getSetting<string>(CLIENT_ID_KEY, "");
  if (/^[A-Za-z0-9-]{16,64}$/.test(cur)) return cur;
  const id = `ultratv-${crypto.randomUUID()}`;
  await setSetting(CLIENT_ID_KEY, id);
  return id;
}

// ---------------------------------------------------------------- adresses et paramètres d'identification

const q = encodeURIComponent;

/**
 * Ajoute à une adresse les paramètres d'identification de l'application (et le jeton s'il est fourni). Les navigateurs ne
 * relaient pas d'en-tête personnalisé via le proxy : Plex accepte les mêmes valeurs en paramètres `X-Plex-*`.
 */
export function withPlexParams(url: string, clientId: string, token?: string | null): string {
  const sep = url.includes("?") ? "&" : "?";
  return `${url}${sep}X-Plex-Client-Identifier=${q(clientId)}&X-Plex-Product=${q(PLEX_PRODUCT)}&X-Plex-Version=1&X-Plex-Platform=Desktop&X-Plex-Device-Name=${q(PLEX_PRODUCT)}` +
    (token ? `&X-Plex-Token=${q(token)}` : "");
}

export const sectionsUrl = (base: string) => `${base}/library/sections`;
/** [plexType] : 1 = films, 2 = séries. */
export const itemsUrl = (base: string, sectionId: string, plexType: 1 | 2, start: number, size = PAGE_SIZE) =>
  `${base}/library/sections/${q(sectionId)}/all?type=${plexType}&X-Plex-Container-Start=${start}&X-Plex-Container-Size=${size}`;
export const episodesUrl = (base: string, showKey: string) => `${base}/library/metadata/${q(showKey)}/allLeaves`;
export const itemUrl = (base: string, ratingKey: string) => `${base}/library/metadata/${q(ratingKey)}`;

/** Affiche redimensionnée par le serveur. SANS jeton (valeur stockée) : voir [withImageToken]. */
export const imageUrl = (base: string, path: string, width = 480, height = 720) =>
  `${base}/photo/:/transcode?width=${width}&height=${height}&minSize=1&upscale=1&url=${q(path)}`;

export const isPlexImage = (url: string) => /^https?:\/\/[^/]+\/photo\/:\/transcode\?/i.test(url);

/** Lecture directe : fichier d'origine `/library/parts/{id}/{ts}/file.ext` ; null si l'élément n'a pas de fichier. Sans jeton. */
export const directUrl = (base: string, partKey: string | null | undefined): string | null => (partKey && partKey.startsWith("/") ? base + partKey : null);

/** Repli HLS : le serveur remuxe ou transcode (Direct Stream si possible). Sans jeton ni identifiant : voir [withPlexParams]. */
export const hlsUrl = (base: string, ratingKey: string, session: string) =>
  `${base}/video/:/transcode/universal/start.m3u8?hasMDE=1&path=${q(`/library/metadata/${ratingKey}`)}&mediaIndex=0&partIndex=0&protocol=hls&fastSeek=1` +
  `&directPlay=0&directStream=1&subtitleSize=100&audioBoost=100&X-Plex-Session-Identifier=${q(session)}`;

export const timelineUrl = (base: string, ratingKey: string, state: "playing" | "paused" | "stopped", timeMs: number, durationMs: number) =>
  `${base}/:/timeline?ratingKey=${q(ratingKey)}&key=${q(`/library/metadata/${ratingKey}`)}&identifier=com.plexapp.plugins.library` +
  `&state=${state}&time=${Math.max(0, Math.round(timeMs))}&duration=${Math.max(0, Math.round(durationMs))}&hasMDE=1`;

export const timelineState = (kind: ReportKind, paused: boolean): "playing" | "paused" | "stopped" => (kind === "stop" ? "stopped" : paused ? "paused" : "playing");

// ---------------------------------------------------------------- analyse des réponses

type J = Record<string, unknown>;
const obj = (v: unknown): J | null => (v && typeof v === "object" && !Array.isArray(v) ? (v as J) : null);
const str = (v: unknown): string | null => (typeof v === "string" && v.trim() ? v : typeof v === "number" ? String(v) : null);
const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
function parse(text: string): unknown { try { return JSON.parse(text); } catch { return null; } }

export interface Pin { id: number; code: string; authToken: string | null; expiresIn: number | null }

export function parsePin(text: string): Pin | null {
  const o = obj(parse(text));
  const id = num(o?.id), code = str(o?.code);
  return o && id !== null && code ? { id, code, authToken: str(o.authToken), expiresIn: num(o.expiresIn) } : null;
}

export interface PlexConnection { uri: string; local: boolean; relay: boolean; https: boolean; ipv6: boolean }
export interface PlexServer { name: string; machineId: string; accessToken: string; owned: boolean; connections: PlexConnection[] }

/** Locale d'abord, puis distante directe, puis relais Plex ; à égalité IPv4 avant IPv6 et HTTPS avant HTTP. */
export function orderConnections(list: PlexConnection[]): PlexConnection[] {
  const seen = new Set<string>();
  const rank = (c: PlexConnection) => [c.relay ? 1 : 0, c.local ? 0 : 1, c.ipv6 ? 1 : 0, c.https ? 0 : 1];
  return list.filter((c) => !seen.has(c.uri) && !!seen.add(c.uri)).sort((a, b) => {
    const ra = rank(a), rb = rank(b);
    for (let i = 0; i < ra.length; i++) if (ra[i] !== rb[i]) return ra[i]! - rb[i]!;
    return 0;
  });
}

/** `GET /api/v2/resources` : seuls les serveurs avec jeton et au moins une connexion ; propres d'abord. */
export function parseServers(text: string): PlexServer[] {
  const out: PlexServer[] = [];
  for (const e of arr(parse(text))) {
    const r = obj(e);
    if (!r || !(str(r.provides) ?? "").split(",").map((s) => s.trim()).includes("server")) continue;
    const machineId = str(r.clientIdentifier), accessToken = str(r.accessToken);
    if (!machineId || !accessToken) continue;
    const connections = orderConnections(arr(r.connections).flatMap((c) => {
      const o = obj(c);
      const uri = str(o?.uri)?.replace(/\/+$/, "");
      return o && uri ? [{ uri, local: o.local === true, relay: o.relay === true, https: (str(o.protocol) ?? uri.split(":")[0]!).toLowerCase() === "https", ipv6: o.IPv6 === true }] : [];
    }));
    if (connections.length) out.push({ name: str(r.name) ?? machineId, machineId, accessToken, owned: r.owned === true, connections });
  }
  return out.sort((a, b) => Number(b.owned) - Number(a.owned) || a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
}

/** `GET /library/sections` : seules les bibliothèques de films et de séries. */
export function parseSections(text: string): Library[] {
  const c = obj(obj(parse(text))?.MediaContainer);
  return arr(c?.Directory).flatMap((e) => {
    const d = obj(e);
    const id = str(d?.key);
    const type = d?.type === "movie" ? "movies" : d?.type === "show" ? "tvshows" : null;
    return d && id && type ? [{ id, name: str(d.title) ?? id, type } as Library] : [];
  });
}

/** Élément Plex ramené au modèle commun ([Item]) : durées et positions en « ticks » (1 ms = 10 000), image = chemin Plex. */
function item(o: J): Item | null {
  const id = str(o.ratingKey), name = str(o.title);
  if (!id || !name) return null;
  const media = obj(arr(o.Media)[0]);
  const part = obj(arr(media?.Part)[0]);
  const tags = (k: string) => arr(o[k]).map((x) => str(obj(x)?.tag)).filter((x): x is string => !!x);
  const dur = num(o.duration) ?? num(media?.duration);
  return {
    id, name, type: str(o.type) ?? "", year: num(o.year), rating: num(o.audienceRating) ?? num(o.rating), overview: str(o.summary),
    genres: tags("Genre"), cast: tags("Role"), directors: tags("Director"),
    runtimeTicks: dur ? dur * MS_TO_TICKS : null, container: (str(part?.container) ?? str(media?.container))?.split(",")[0] ?? null,
    mediaSourceId: str(part?.key), primaryTag: str(o.thumb), backdropTag: str(o.art),
    season: num(o.parentIndex), episode: num(o.index), added: (num(o.addedAt) ?? 0) * 1000,
    positionTicks: (num(o.viewOffset) ?? 0) * MS_TO_TICKS, played: (num(o.viewCount) ?? 0) > 0,
  };
}

/** Réponse `{"MediaContainer":{"totalSize":N,"Metadata":[…]}}` ; illisible = page vide. */
export function parseItems(text: string): { total: number; items: Item[] } {
  const c = obj(obj(parse(text))?.MediaContainer);
  const items = arr(c?.Metadata).map(obj).filter((x): x is J => !!x).map(item).filter((x): x is Item => !!x);
  return { total: num(c?.totalSize) ?? items.length, items };
}
export const parseItem = (text: string): Item | null => parseItems(text).items[0] ?? null;

// ---------------------------------------------------------------- images (jeton ajouté à l'affichage, jamais stocké)

const imageTokens = new Map<string, string>();
const originOf = (u: string): string | null => { try { return new URL(u).origin; } catch { return null; } };

/** Retient en mémoire le jeton des sources Plex (origine du serveur → jeton), pour les affiches. Jamais écrit nulle part. */
export function registerPlexTokens(sources: Source[]): void {
  imageTokens.clear();
  for (const s of sources) if (s.type === "plex" && s.password) { const o = originOf(normalizeServerUrl(s.server) ?? ""); if (o) imageTokens.set(o, s.password); }
}

/** Adresse d'affiche prête à charger : le jeton est ajouté ici seulement pour un serveur Plex connu (jamais pour un autre hôte). */
export function withImageToken(url: string): string {
  if (!isPlexImage(url)) return url;
  const tok = imageTokens.get(originOf(url) ?? "");
  return tok ? `${url}&X-Plex-Token=${q(tok)}` : url;
}

// ---------------------------------------------------------------- connexion par code PIN

/** Issue de l'attente du code : jeton du compte, ou code expiré. */
export type PinResult = { token: string } | { expired: true };

/**
 * Boucle de connexion par code PIN : demande un code, le fait afficher, sonde toutes les [PIN_POLL_MS] ms jusqu'au jeton ou
 * à l'expiration (délai du serveur, au plus 15 min ; 404 = expiré). Une erreur réseau passagère ne l'interrompt pas. Le temps
 * est compté sur les attentes : testable sans horloge réelle. La demande initiale qui échoue lève son erreur.
 */
export async function awaitPlexToken(deps: {
  create: () => Promise<Pin>; poll: (id: number) => Promise<string | null>; onCode: (code: string) => void;
  wait?: (ms: number) => Promise<void>; signal?: AbortSignal; ttlMs?: number; pollMs?: number;
}): Promise<PinResult> {
  const wait = deps.wait ?? ((ms) => new Promise<void>((r) => setTimeout(r, ms)));
  const pollMs = deps.pollMs ?? PIN_POLL_MS;
  const pin = await deps.create();
  deps.onCode(pin.code);
  const ttl = deps.ttlMs ?? PIN_TTL_MS;
  const limit = pin.expiresIn && pin.expiresIn > 0 ? Math.min(pin.expiresIn * 1000, ttl) : ttl;
  for (let elapsed = 0; elapsed < limit; elapsed += pollMs) {
    await wait(pollMs);
    if (deps.signal?.aborted) throw new DOMException("annulé", "AbortError");
    try {
      const tok = await deps.poll(pin.id);
      if (tok) return { token: tok };
    } catch (e) {
      if ((e as Error).name === "AbortError") throw e;
      if (e instanceof HttpError && e.status === 404) return { expired: true };
    }
  }
  return { expired: true };
}

// ---------------------------------------------------------------- client réseau

/** Appels vers plex.tv (code PIN, serveurs du compte) et choix de la connexion d'un serveur. */
export class PlexAccount {
  constructor(private t: Transport, private clientId: () => Promise<string> = plexClientId, private signal?: AbortSignal) {}

  private async json(url: string, token?: string | null, post = false): Promise<string> {
    const id = await this.clientId();
    return (await transportFetch(this.t, withPlexParams(url, id, token), { signal: this.signal, accept: "application/json", ...(post ? { post: "" } : {}) })).text();
  }

  async createPin(): Promise<Pin> {
    const p = parsePin(await this.json(PINS_URL, null, true));
    if (!p) throw new Error("plex");
    return p;
  }
  async pollPin(id: number): Promise<string | null> { return parsePin(await this.json(`https://plex.tv/api/v2/pins/${id}`))?.authToken ?? null; }
  async servers(accountToken: string): Promise<PlexServer[]> { return parseServers(await this.json(RESOURCES_URL, accountToken)); }

  /** Première connexion du serveur qui répond (locale d'abord, puis distante, puis relais) ; null si aucune. */
  async bestConnection(s: PlexServer): Promise<PlexConnection | null> {
    const id = await this.clientId();
    for (const c of s.connections) {
      try {
        const ctrl = new AbortController();
        const timer = setTimeout(() => ctrl.abort(), 5_000);
        try { await transportFetch(this.t, withPlexParams(`${c.uri}/identity`, id, s.accessToken), { signal: ctrl.signal, accept: "application/json" }); return c; }
        finally { clearTimeout(timer); }
      } catch (e) {
        if (this.signal?.aborted) throw e;
      }
    }
    return null;
  }
}

/** Client d'un serveur Plex ajouté comme source ; même interface que celui de Jellyfin / Emby. Jamais de journal d'adresse. */
export class PlexClient implements MediaApi {
  readonly base: string;
  private durations = new Map<string, number>();
  constructor(private t: Transport, private c: MediaCreds, private signal?: AbortSignal, private clientId: () => Promise<string> = plexClientId) {
    const b = normalizeServerUrl(c.server);
    if (!b || !c.password) throw new Error("auth");
    this.base = b;
  }

  private async get(url: string): Promise<string> {
    try {
      const id = await this.clientId();
      return await (await transportFetch(this.t, withPlexParams(url, id, this.c.password), { signal: this.signal, accept: "application/json" })).text();
    } catch (e) {
      if (e instanceof HttpError && (e.status === 401 || e.status === 403)) throw new Error("auth");
      throw e;
    }
  }

  /** Vérifie que le serveur répond avec ce jeton (formulaire d'ajout). */
  async probe(): Promise<void> { await this.get(`${this.base}/identity`); }

  libraries = async (): Promise<Library[]> => parseSections(await this.get(sectionsUrl(this.base)));

  async items(libraryId: string, type: "Movie" | "Series"): Promise<Item[]> {
    const out: Item[] = [];
    for (let start = 0; start < 200_000; start += PAGE_SIZE) {
      const page = parseItems(await this.get(itemsUrl(this.base, libraryId, type === "Movie" ? 1 : 2, start)));
      out.push(...page.items);
      if (!page.items.length || out.length >= page.total) break;
    }
    return out;
  }

  async episodes(seriesId: string): Promise<Item[]> {
    return parseItems(await this.get(episodesUrl(this.base, seriesId))).items.filter((i) => !i.type || i.type === "episode");
  }

  item = async (id: string): Promise<Item | null> => {
    const it = parseItem(await this.get(itemUrl(this.base, id)));
    if (it?.runtimeTicks) this.durations.set(id, it.runtimeTicks / MS_TO_TICKS);
    return it;
  };

  image(it: Item, type: "Primary" | "Backdrop", maxWidth = 480): string | undefined {
    const path = type === "Primary" ? it.primaryTag : it.backdropTag;
    return path ? imageUrl(this.base, path, maxWidth, type === "Primary" ? Math.round(maxWidth * 1.5) : Math.round(maxWidth * 9 / 16)) : undefined;
  }

  /** Adresses de lecture (jeton ajouté à l'instant) : directe, puis HLS ; fiche (reprise) jointe si lisible. */
  async playback(id: string): Promise<{ urls: { direct: string; hls: string }; item: Item | null }> {
    const cid = await this.clientId();
    const it = await this.item(id);
    const direct = directUrl(this.base, it?.mediaSourceId);
    if (!direct) throw new Error("plex: no media part");
    return { urls: { direct: withPlexParams(direct, cid, this.c.password), hls: withPlexParams(hlsUrl(this.base, id, `${cid}-${id}`), cid, this.c.password) }, item: it };
  }

  /** Rapport de lecture (`/:/timeline`) ; ne lève jamais (hors annulation). */
  async report(kind: ReportKind, id: string, positionSec: number, paused: boolean): Promise<void> {
    try {
      let dur = this.durations.get(id);
      if (dur === undefined) { await this.item(id); dur = this.durations.get(id); }
      await this.get(timelineUrl(this.base, id, timelineState(kind, paused), positionSec * 1000, dur ?? 0));
    } catch (e) {
      if ((e as Error).name === "AbortError") throw e;
    }
  }
}
