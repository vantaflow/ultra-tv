// Client du middleware Stalker / Ministra (portail « boîtier MAG », authentification par adresse MAC).
// Implémenté d'après le fonctionnement public du protocole : requêtes GET `?type=…&action=…&JsHttpRequest=1-xml`,
// réponses `{"js": …}`. Utilisable depuis le thread principal et le worker de synchro.
//
// Stockage d'une source Stalker (sans changement de schéma) : `server` = adresse du portail telle que saisie,
// `username` = MAC normalisée (00:1A:79:XX:XX:XX), `password` = identifiant et mot de passe FACULTATIFS du portail
// empaquetés « login\u0000motdepasse » (chiffrés au repos comme ceux d'Xtream, voir db/sources.ts).
// Les lignes du catalogue gardent la commande Stalker (`cmd`) dans une adresse interne `stalker:…` : le vrai lien,
// à durée de vie courte, n'est demandé au portail (create_link) qu'au moment de la lecture.

import { parseTolerant } from "./json";
import { HttpError, transportFetch, type Transport } from "./transport";

export const STALKER_UA = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3";
export const STALKER_X_UA = "Model: MAG250; Link: WiFi";
export const MAC_PREFIX = "00:1A:79:";

// ---------------------------------------------------------------- MAC, identifiants, adresses internes

/** « 00-1a-79-aa-bb-cc », « 001a79aabbcc »… → « 00:1A:79:AA:BB:CC » ; null si incomplète. */
export function normalizeMac(raw: string): string | null {
  const hex = raw.replace(/[^0-9a-f]/gi, "").toUpperCase();
  return hex.length === 12 ? hex.match(/.{2}/g)!.join(":") : null;
}

/** Saisie au fil de la frappe : chiffres hexadécimaux seulement (12 au plus), « : » remis automatiquement. */
export function formatMacTyping(raw: string): string {
  const hex = raw.replace(/[^0-9a-f]/gi, "").toUpperCase().slice(0, 12);
  return (hex.match(/.{1,2}/g) ?? []).join(":");
}

/** Affichage sans divulguer l'adresse : seuls les deux derniers octets restent lisibles. */
export const maskMac = (mac: string) => { const n = normalizeMac(mac); return n ? `••:••:••:••:${n.slice(-5)}` : "••:••:••:••:••:••"; };

export function packCreds(login: string, password: string): string {
  return !login && !password ? "" : `${login}\u0000${password}`;
}
export function unpackCreds(stored: string): { login: string; password: string } {
  if (!stored) return { login: "", password: "" };
  const i = stored.indexOf("\u0000");
  return i < 0 ? { login: "", password: stored } : { login: stored.slice(0, i), password: stored.slice(i + 1) };
}

/** Adresse interne stockée dans le catalogue : `stalker:<itv|vod>:<épisode>:<commande>`. */
export const stalkerLink = (type: "itv" | "vod", cmd: string, series?: number | null) => `stalker:${type}:${series ?? ""}:${cmd}`;
export const isStalkerLink = (u: string | null | undefined): u is string => !!u && u.startsWith("stalker:");
export function parseStalkerLink(u: string): { type: "itv" | "vod"; cmd: string; series: number | null } | null {
  const m = /^stalker:(itv|vod):(\d*):([\s\S]+)$/.exec(u);
  return m ? { type: m[1] as "itv" | "vod", series: m[2] ? Number(m[2]) : null, cmd: m[3]! } : null;
}

/** Lien du portail : préfixe de lecteur retiré (« ffmpeg http://… », « auto http://… »). */
export function cleanCmd(cmd: string): string {
  const s = cmd.trim();
  const m = /^(ffmpeg|ffrt\d?|auto)\s+/i.exec(s);
  return m ? s.slice(m[0].length).trim() : s;
}

/** Commande déjà jouable telle quelle (vrai serveur, pas « localhost ») : repli si create_link échoue. */
export function directUrlOf(cmd: string): string | null {
  const s = cleanCmd(cmd).split(" ")[0]!;
  try {
    const u = new URL(s);
    if (!/^(https?|rtmp):$/.test(u.protocol) || u.hostname === "localhost" || u.hostname === "127.0.0.1") return null;
    return s;
  } catch { return null; }
}

const withScheme = (s: string) => (/^https?:\/\//i.test(s) ? s : `http://${s}`);

/** Adresse saisie avec « / » final (Referer attendu par les portails). */
export const portalReferer = (portal: string) => { const s = withScheme(portal.trim()); return s.endsWith("/") ? s : `${s}/`; };

/**
 * Points d'entrée possibles, dans l'ordre d'essai : `<saisie>/portal.php`, `<saisie sans /c>/portal.php`,
 * `<saisie sans /c>/server/load.php`, `/stalker_portal/server/load.php`, `portal.php` à la racine.
 */
export function endpointCandidates(portal: string): string[] {
  const raw = withScheme(portal.trim()).split(/[?#]/)[0]!;
  if (/\.php$/i.test(raw)) return [raw];
  const base = raw.replace(/\/+$/, "");
  let origin = base;
  try { origin = new URL(base).origin; } catch { /* adresse brute */ }
  const noC = /\/c$/i.test(base) ? base.slice(0, -2) : base;
  return [...new Set([`${base}/portal.php`, `${noC}/portal.php`, `${noC}/server/load.php`, `${origin}/stalker_portal/server/load.php`, `${origin}/portal.php`])];
}

/** Logo / affiche : absolu, relatif à l'hôte ou simple nom de fichier du portail. */
export function absoluteUrl(portal: string, path: unknown): string | null {
  const s = typeof path === "string" ? path.trim() : "";
  if (!s || s === "null") return null;
  if (/^https?:\/\//i.test(s)) return s;
  const base = withScheme(portal.trim()).split(/[?#]/)[0]!.replace(/\/+$/, "");
  let origin: string;
  try { origin = new URL(base).origin; } catch { return null; }
  if (s.startsWith("/")) return origin + s;
  return `${/\/c$/i.test(base) ? base.slice(0, -2) : base}/misc/logos/320/${s}`;
}

/** Dates d'expiration (« 2025-12-31 00:00:00 », secondes Unix, « December 31, 2025, 12:00 am »…) → ms ; null si illimité. */
export function parseStalkerDate(raw: unknown): number | null {
  const s = raw == null ? "" : String(raw).trim();
  if (!s || s.startsWith("0000") || /^(null|unlimited)$/i.test(s)) return null;
  if (/^\d+$/.test(s)) { const n = Number(s); return n <= 0 ? null : n < 1e11 ? n * 1000 : n; }
  const iso = /^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?$/.exec(s);
  if (iso) return Date.UTC(+iso[1]!, +iso[2]! - 1, +iso[3]!, +(iso[4] ?? 0), +(iso[5] ?? 0), +(iso[6] ?? 0));
  const t = Date.parse(`${s.replace(/,\s*(\d{1,2}:\d{2})/, " $1")} UTC`);
  return Number.isFinite(t) ? t : null;
}

// ---------------------------------------------------------------- identité de boîtier (dérivée de la MAC)

async function sha256Hex(s: string): Promise<string> {
  const d = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
  return [...d].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** MD5 compact (WebCrypto ne le propose pas) : seulement pour le numéro de série dérivé, comme les lecteurs MAG. */
export function md5Hex(input: string): string {
  const bytes = new TextEncoder().encode(input);
  const words: number[] = [];
  for (let i = 0; i < bytes.length; i++) words[i >> 2] = (words[i >> 2] ?? 0) | (bytes[i]! << ((i % 4) * 8));
  const bitLen = bytes.length * 8;
  words[bitLen >> 5] = (words[bitLen >> 5] ?? 0) | (0x80 << (bitLen % 32));
  words[(((bitLen + 64) >>> 9) << 4) + 14] = bitLen;
  const S = [7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21];
  const K = Array.from({ length: 64 }, (_, i) => Math.floor(Math.abs(Math.sin(i + 1)) * 2 ** 32) | 0);
  let a0 = 0x67452301, b0 = 0xefcdab89 | 0, c0 = 0x98badcfe | 0, d0 = 0x10325476;
  const total = ((((bitLen + 64) >>> 9) << 4) + 16);
  for (let off = 0; off < total; off += 16) {
    let a = a0, b = b0, c = c0, d = d0;
    for (let i = 0; i < 64; i++) {
      let f: number, g: number;
      if (i < 16) { f = (b & c) | (~b & d); g = i; }
      else if (i < 32) { f = (d & b) | (~d & c); g = (5 * i + 1) % 16; }
      else if (i < 48) { f = b ^ c ^ d; g = (3 * i + 5) % 16; }
      else { f = c ^ (b | ~d); g = (7 * i) % 16; }
      const tmp = d; d = c; c = b;
      const x = (a + f + K[i]! + (words[off + g] ?? 0)) | 0;
      const s = S[(i >> 4) * 4 + (i % 4)]!;
      b = (b + ((x << s) | (x >>> (32 - s)))) | 0;
      a = tmp;
    }
    a0 = (a0 + a) | 0; b0 = (b0 + b) | 0; c0 = (c0 + c) | 0; d0 = (d0 + d) | 0;
  }
  return [a0, b0, c0, d0].map((w) => [0, 8, 16, 24].map((sh) => ((w >>> sh) & 0xff).toString(16).padStart(2, "0")).join("")).join("");
}

export interface StalkerDevice { sn: string; deviceId: string; deviceId2: string; signature: string; model: string }

export async function deviceOf(mac: string): Promise<StalkerDevice> {
  const sn = md5Hex(mac).toUpperCase().slice(0, 13);
  const id = (await sha256Hex(mac)).toUpperCase();
  return { sn, deviceId: id, deviceId2: id, signature: (await sha256Hex(sn + mac)).toUpperCase(), model: "MAG250" };
}

// ---------------------------------------------------------------- client

export interface StalkerAccount { portal: string; mac: string; login?: string; password?: string }
type Obj = Record<string, unknown>;
type Params = Record<string, string | number>;

const str = (o: Obj, k: string): string | null => {
  const v = o[k];
  if (v == null || typeof v === "object") return null;
  const s = String(v).trim();
  return s && s !== "null" ? s : null;
};
const flag = (o: Obj, k: string) => o[k] === true || o[k] === 1 || o[k] === "1" || o[k] === "true";
const asObjs = (v: unknown): Obj[] => (Array.isArray(v) ? v.filter((x): x is Obj => !!x && typeof x === "object") : []);

/** Réponse refusée par le portail (jeton périmé, MAC inconnue) : présentée comme « identifiants refusés ». */
export class StalkerAuthError extends Error { constructor() { super("auth"); this.name = "StalkerAuthError"; } }

export interface StalkerCategory { id: string; title: string }
export interface StalkerItem extends Obj { id?: string | number; name?: string; cmd?: string }
export interface StalkerEpisode { id: number; season: number; num: number; title: string; url: string; plot: string | null; img: string | null }

/**
 * Session : point d'entrée sondé (une fois, puis mémorisé via `onEndpoint`), `stb/handshake` → jeton,
 * `stb/get_profile`, `stb/do_auth` si un identifiant est fourni. Un refus (« Authorization failed », 401/403,
 * réponse qui n'est pas du JSON du portail) provoque UNE nouvelle poignée de main puis un nouvel essai.
 */
export class StalkerClient {
  private session: { api: string; token: string } | null = null;
  private opening: Promise<{ api: string; token: string }> | null = null;

  constructor(
    private t: Transport,
    private a: StalkerAccount,
    private opts: { endpoint?: string | null; onEndpoint?: (api: string) => void; signal?: AbortSignal } = {},
  ) {}

  get endpoint(): string | null { return this.session?.api ?? this.opts.endpoint ?? null; }

  private headers(token: string | null): Record<string, string> {
    const h: Record<string, string> = {
      "X-User-Agent": STALKER_X_UA,
      Cookie: `mac=${encodeURIComponent(this.a.mac)}; stb_lang=en; timezone=UTC`,
    };
    if (token) h.Authorization = `Bearer ${token}`;
    return h;
  }

  private async get(api: string, token: string | null, params: Params): Promise<string> {
    const u = new URL(api);
    for (const [k, v] of Object.entries(params)) u.searchParams.set(k, String(v));
    u.searchParams.set("JsHttpRequest", "1-xml");
    const res = await transportFetch(this.t, u.toString(), {
      signal: this.opts.signal, userAgent: STALKER_UA, referer: portalReferer(this.a.portal), headers: this.headers(token),
    });
    return res.text();
  }

  private async handshake(api: string): Promise<string | null> {
    const body = await this.get(api, null, { type: "stb", action: "handshake", token: "", prehash: 0 });
    try { const js = (parseTolerant<Obj>(body)).js as Obj | undefined; return js ? str(js, "token") : null; } catch { return null; }
  }

  private async profileParams(): Promise<Params> {
    const d = await deviceOf(this.a.mac);
    return {
      type: "stb", action: "get_profile", hd: 1, ver: "ImageDescription: 0.2.18-r23-250; PORTAL version: 5.6.1; API Version: JS API version: 343",
      num_banks: 2, sn: d.sn, stb_type: d.model, client_type: "STB", image_version: 218, video_out: "hdmi", device_id: d.deviceId,
      device_id2: d.deviceId2, signature: d.signature, auth_second_step: 1, hw_version: "1.7-BD-00", not_valid_token: 0,
      api_signature: 262, prehash: 0, timestamp: Math.floor(Date.now() / 1000),
    };
  }

  private async open(): Promise<{ api: string; token: string }> {
    const known = this.opts.endpoint ?? null;
    const list = [...(known ? [known] : []), ...endpointCandidates(this.a.portal).filter((x) => x !== known)];
    let last: unknown = null;
    for (const api of list) {
      let token: string | null = null;
      try { token = await this.handshake(api); } catch (e) { if ((e as Error).name === "AbortError") throw e; last = e; }
      if (!token) continue;
      const s = { api, token };
      await this.get(api, token, await this.profileParams()).catch(() => undefined);
      if (this.a.login) {
        const d = await deviceOf(this.a.mac);
        const body = await this.get(api, token, { type: "stb", action: "do_auth", login: this.a.login, password: this.a.password ?? "", device_id: d.deviceId, device_id2: d.deviceId2 });
        let js: unknown = null;
        try { js = parseTolerant<Obj>(body).js; } catch { /* refus */ }
        if (js === false || js === "false" || js == null) throw new StalkerAuthError();
      }
      if (api !== known) this.opts.onEndpoint?.(api);
      this.opts.endpoint = api;
      return s;
    }
    throw last instanceof HttpError ? last : new StalkerAuthError();
  }

  private async ensure(fresh = false): Promise<{ api: string; token: string }> {
    if (fresh) this.session = null;
    if (this.session) return this.session;
    this.opening ??= this.open().finally(() => { this.opening = null; });
    this.session = await this.opening;
    return this.session;
  }

  /** Vérifie la connexion (formulaire d'ajout) et renvoie le point d'entrée qui répond. */
  async probe(): Promise<string> { return (await this.ensure(true)).api; }

  /** Appel authentifié → racine `js` ; une seule nouvelle poignée de main si le portail refuse le jeton. */
  async call(params: Params): Promise<unknown> {
    for (let attempt = 0; attempt < 2; attempt++) {
      const s = await this.ensure();
      let body: string;
      try { body = await this.get(s.api, s.token, params); } catch (e) {
        if (e instanceof HttpError && (e.status === 401 || e.status === 403) && attempt === 0) { this.session = null; continue; }
        throw e;
      }
      const refused = !body.trimStart().startsWith("{") || /Authorization failed/i.test(body);
      if (!refused) {
        try { const root = parseTolerant<Obj>(body); if (root && typeof root === "object" && "js" in root) return root.js; } catch { /* refus */ }
      }
      if (attempt === 0) { this.session = null; continue; }
    }
    throw new StalkerAuthError();
  }

  /** Toutes les pages d'une liste `get_ordered_list` (3 à la fois après la première ; plafond 3 000 pages). */
  async paged(params: Params): Promise<StalkerItem[]> {
    const first = (await this.call({ ...params, p: 1 })) as Obj | null;
    const items = asObjs(first?.data);
    const total = Number(first?.total_items ?? items.length) || items.length;
    const per = Number(first?.max_page_items ?? items.length) || items.length;
    const pages = per > 0 ? Math.min(3000, Math.max(1, Math.ceil(total / per))) : 1;
    const out: StalkerItem[] = [...items];
    if (pages > 1 && items.length) {
      const queue = Array.from({ length: pages - 1 }, (_, i) => i + 2);
      const results = new Map<number, Obj[]>();
      await Promise.all(Array.from({ length: 3 }, async () => {
        for (let n = queue.shift(); n !== undefined; n = queue.shift()) results.set(n, asObjs(((await this.call({ ...params, p: n })) as Obj | null)?.data));
      }));
      for (let n = 2; n <= pages; n++) out.push(...(results.get(n) ?? []));
    }
    const seen = new Set<string>();
    return out.filter((o) => { const id = str(o, "id"); if (!id) return true; if (seen.has(id)) return false; seen.add(id); return true; });
  }

  private static categories(js: unknown): StalkerCategory[] {
    const list = Array.isArray(js) ? js : asObjs((js as Obj | null)?.data);
    const out: StalkerCategory[] = [];
    for (const o of asObjs(list)) {
      const id = str(o, "id"); const title = str(o, "title") ?? str(o, "name");
      if (id && title && id !== "*" && !out.some((c) => c.id === id)) out.push({ id, title });
    }
    return out;
  }

  genres = async () => StalkerClient.categories(await this.call({ type: "itv", action: "get_genres" }));
  vodCategories = async () => StalkerClient.categories(await this.call({ type: "vod", action: "get_categories" }));

  /** Tout le direct : `get_all_channels`, sinon la liste paginée `genre=*`. */
  async allChannels(): Promise<StalkerItem[]> {
    try {
      const js = (await this.call({ type: "itv", action: "get_all_channels" })) as Obj | null;
      const data = asObjs(js?.data);
      if (data.length) return data;
    } catch (e) { if ((e as Error).name === "AbortError") throw e; }
    return this.paged({ type: "itv", action: "get_ordered_list", genre: "*", fav: 0, sortby: "number" });
  }

  vodOfCategory = (id: string) => this.paged({ type: "vod", action: "get_ordered_list", category: id, fav: 0, sortby: "added" });

  /**
   * Épisodes d'une série : saisons du portail récent (`is_season` puis `is_episode`), sinon le tableau `series`
   * de l'élément (numéros d'épisodes) et sa commande.
   */
  async episodes(seriesId: string, cmd: string | null, numbers: number[] | null): Promise<StalkerEpisode[]> {
    const base = { type: "vod", action: "get_ordered_list", movie_id: seriesId, season_id: 0, episode_id: 0, fav: 0, sortby: "added" };
    const listed = await this.paged(base).catch(() => [] as StalkerItem[]);
    const seasons = listed.filter((o) => flag(o, "is_season"));
    const out: StalkerEpisode[] = [];
    if (seasons.length) {
      for (const [i, se] of seasons.entries()) {
        const no = Number(str(se, "season_number")) || Number(/(\d+)/.exec(str(se, "name") ?? "")?.[1]) || i + 1;
        const sid = str(se, "id"); if (!sid) continue;
        const eps = (await this.paged({ ...base, season_id: sid }).catch(() => [] as StalkerItem[])).filter((e) => flag(e, "is_episode") || str(e, "cmd"));
        if (eps.length) {
          eps.forEach((e, j) => {
            const c = str(e, "cmd"); if (!c) return;
            const n = Number(str(e, "series_number") ?? str(e, "episode_number")) || j + 1;
            out.push({ id: no * 10_000 + n, season: no, num: n, title: str(e, "name") ?? "", url: stalkerLink("vod", c, n), plot: str(e, "description"), img: str(e, "screenshot_uri") });
          });
        } else {
          const c = str(se, "cmd");
          const nums = Array.isArray(se.series) ? (se.series as unknown[]).map(Number).filter(Number.isFinite) : [];
          if (c) for (const n of nums) out.push({ id: no * 10_000 + n, season: no, num: n, title: "", url: stalkerLink("vod", c, n), plot: null, img: null });
        }
      }
      return out;
    }
    const own = listed.find((o) => str(o, "id") === seriesId);
    const c = (own && str(own, "cmd")) ?? cmd;
    const nums = own && Array.isArray(own.series) ? (own.series as unknown[]).map(Number).filter(Number.isFinite) : numbers ?? [];
    if (!c) return [];
    return [...new Set(nums)].sort((a, b) => a - b).map((n) => ({ id: 10_000 + n, season: 1, num: n, title: "", url: stalkerLink("vod", c, n), plot: null, img: null }));
  }

  /** Lien de lecture à durée de vie courte, demandé au moment de jouer. */
  async createLink(type: "itv" | "vod", cmd: string, series?: number | null): Promise<string> {
    let js: unknown;
    try {
      js = await this.call({ type, action: "create_link", cmd, series: series ?? "", forced_storage: "undefined", disable_ad: 0, download: 0 });
    } catch (e) {
      const direct = directUrlOf(cmd);
      if (direct) return direct;
      throw e;
    }
    const link = js && typeof js === "object" ? str(js as Obj, "cmd") : null;
    const out = link ? cleanCmd(link) : directUrlOf(cmd);
    if (!out) throw new Error("empty-link");
    return out;
  }

  async shortEpg(chId: string | number): Promise<Array<{ title: string; desc: string; start: number; end: number }>> {
    const js = await this.call({ type: "itv", action: "get_short_epg", ch_id: chId, size: 10 });
    return asObjs(Array.isArray(js) ? js : (js as Obj | null)?.data).flatMap((o) => {
      const title = str(o, "name");
      const start = Number(str(o, "start_timestamp")) * 1000 || parseStalkerDate(str(o, "time"));
      if (!title || !start) return [];
      const end = Number(str(o, "stop_timestamp")) * 1000 || parseStalkerDate(str(o, "time_to")) || start + 30 * 60_000;
      return [{ title, desc: str(o, "descr") ?? "", start, end }];
    });
  }

  /** Abonnement : échéance (`account_info/get_main_info`) et statut (blocage du profil). */
  async account(now = Date.now()): Promise<{ status: "Active" | "Expired" | "Disabled"; expDate: number | null }> {
    const info = ((await this.call({ type: "account_info", action: "get_main_info" }).catch(() => null)) ?? {}) as Obj;
    const prof = ((await this.call(await this.profileParams()).catch(() => null)) ?? {}) as Obj;
    return accountFrom(info, prof, now);
  }
}

export function accountFrom(info: Obj, prof: Obj, now = Date.now()): { status: "Active" | "Expired" | "Disabled"; expDate: number | null } {
  const keys = ["end_date", "expire_billing_date", "tariff_expired_date", "expire_date", "exp_date", "account_expire"];
  let exp: number | null = null;
  for (const k of keys) { exp = parseStalkerDate(info[k]) ?? parseStalkerDate(prof[k]); if (exp) break; }
  if (!exp) { const m = /\d{4}-\d{2}-\d{2}( \d{2}:\d{2}(:\d{2})?)?/.exec(String(info.phone ?? "")); if (m) exp = parseStalkerDate(m[0]); }
  const blocked = flag(prof, "blocked") || String(prof.status ?? "") === "2";
  return { status: blocked ? "Disabled" : exp != null && exp < now ? "Expired" : "Active", expDate: exp };
}
