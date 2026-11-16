// Serveurs multimédias personnels Jellyfin et Emby (API REST quasi identique) : logique pure (adresse, en-têtes,
// analyse des réponses, adresses de lecture, reprise, rapports de lecture) puis client réseau qui n'en est qu'une fine couche.
//
// Stockage d'une source (sans changement de schéma) : `type` = "jellyfin" | "emby", `server` = adresse du serveur,
// `username` / `password` = identifiant et mot de passe (chiffrés au repos, voir db/sources.ts) ; identifiant VIDE =
// le « mot de passe » est une clé API. Le jeton de session n'est JAMAIS stocké : il est obtenu à la première utilisation
// (mémorisé en mémoire) et n'apparaît que dans les adresses de lecture construites à la volée, jamais journalisées.
// Les lignes du catalogue gardent un lien interne `media:<identifiant>` (le serveur et le jeton sont ajoutés à la lecture).

import { HttpError, transportFetch, type Transport } from "./transport";

export const DEVICE_ID = "ultra-tv-desktop";
export const CLIENT_NAME = "Ultra TV";
export const PAGE_SIZE = 300;
const TICKS_PER_SEC = 10_000_000;

export const isMediaType = (t: string | undefined): t is "jellyfin" | "emby" => t === "jellyfin" || t === "emby";

/** Adresse saisie → adresse de base : schéma ajouté (http par défaut), « / » final et page web retirés ; null si inexploitable. */
export function normalizeServerUrl(input: string): string | null {
  let s = input.trim();
  if (!s || /\s/.test(s)) return null;
  if (!s.includes("://")) s = `http://${s}`;
  if (!/^https?:\/\//i.test(s)) return null;
  s = s.split("#")[0]!.split("?")[0]!.replace(/\/+$/, "");
  if (!s.includes("://")) return null;
  const m = /\/web(\/index\.html?)?$/i.exec(s);
  if (m) s = s.slice(0, m.index).replace(/\/+$/, "");
  const host = s.slice(s.indexOf("://") + 3).split("/")[0]!.split("@").pop()!;
  return !host || host.startsWith(":") ? null : s;
}

/** En-tête d'identification d'appareil attendu par les deux serveurs. Les guillemets des valeurs sont retirés. */
export function authorization(deviceId = DEVICE_ID, token?: string): string {
  const q = (v: string) => v.replace(/"/g, "");
  return `MediaBrowser Client="${q(CLIENT_NAME)}", Device="Desktop", DeviceId="${q(deviceId)}", Version="1"` + (token ? `, Token="${q(token)}"` : "");
}

export const authBody = (username: string, password: string) => JSON.stringify({ Username: username, Pw: password });

// ---------------------------------------------------------------- analyse des réponses

type J = Record<string, unknown>;
const obj = (v: unknown): J | null => (v && typeof v === "object" && !Array.isArray(v) ? (v as J) : null);
const str = (v: unknown): string | null => (typeof v === "string" && v.trim() ? v : null);
const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
function parse(text: string): unknown { try { return JSON.parse(text); } catch { return null; } }

export interface Auth { token: string; userId: string; userName: string }

/** Réponse de `AuthenticateByName` ; null si le jeton ou l'utilisateur manque. */
export function parseAuth(text: string): Auth | null {
  const o = obj(parse(text));
  const token = str(o?.AccessToken);
  const u = obj(o?.User);
  const id = str(u?.Id);
  return token && id ? { token, userId: id, userName: str(u?.Name) ?? "" } : null;
}

/** Utilisateur d'une liste `GET /Users` (mode clé API) : celui dont le nom correspond, sinon un administrateur, sinon le premier. */
export function pickUser(text: string, wanted: string): { id: string; name: string } | null {
  const arr = parse(text);
  if (!Array.isArray(arr)) return null;
  const users = arr.flatMap((e) => { const u = obj(e); const id = str(u?.Id); return u && id ? [{ id, name: str(u.Name) ?? "", admin: obj(u.Policy)?.IsAdministrator === true }] : []; });
  if (!users.length) return null;
  const w = wanted.trim().toLowerCase();
  const hit = (w && users.find((u) => u.name.toLowerCase() === w)) || users.find((u) => u.admin) || users[0]!;
  return { id: hit.id, name: hit.name };
}

export interface Library { id: string; name: string; type: "movies" | "tvshows" }

/** `GET /Users/{id}/Views` : seules les bibliothèques de films et de séries sont gardées. */
export function parseViews(text: string): Library[] {
  const items = obj(parse(text))?.Items;
  if (!Array.isArray(items)) return [];
  return items.flatMap((e) => {
    const v = obj(e);
    const id = str(v?.Id);
    const type = str(v?.CollectionType)?.toLowerCase();
    return v && id && (type === "movies" || type === "tvshows") ? [{ id, name: str(v.Name) ?? id, type }] : [];
  });
}

export interface Item {
  id: string; name: string; type: string; year: number | null; rating: number | null; overview: string | null;
  genres: string[]; cast: string[]; directors: string[]; runtimeTicks: number | null; container: string | null; mediaSourceId: string | null;
  primaryTag: string | null; backdropTag: string | null; season: number | null; episode: number | null; added: number;
  positionTicks: number; played: boolean;
}

function item(o: J): Item | null {
  const id = str(o.Id), name = str(o.Name);
  if (!id || !name) return null;
  const src = obj(Array.isArray(o.MediaSources) ? o.MediaSources[0] : null);
  const ud = obj(o.UserData);
  const people = Array.isArray(o.People) ? o.People.map(obj).filter((p): p is J => !!p) : [];
  const names = (type: string) => people.filter((p) => str(p.Type) === type).map((p) => str(p.Name)).filter((n): n is string => !!n);
  const strs = (v: unknown) => (Array.isArray(v) ? v.map(str).filter((x): x is string => !!x) : []);
  return {
    id, name, type: str(o.Type) ?? "", year: num(o.ProductionYear), rating: num(o.CommunityRating), overview: str(o.Overview),
    genres: strs(o.Genres), cast: names("Actor"), directors: names("Director"),
    runtimeTicks: num(o.RunTimeTicks) ?? num(src?.RunTimeTicks), container: str(src?.Container)?.split(",")[0] ?? str(o.Container),
    mediaSourceId: str(src?.Id), primaryTag: str(obj(o.ImageTags)?.Primary), backdropTag: strs(o.BackdropImageTags)[0] ?? null,
    season: num(o.ParentIndexNumber), episode: num(o.IndexNumber), added: Date.parse(str(o.DateCreated) ?? "") || 0,
    positionTicks: num(ud?.PlaybackPositionTicks) ?? 0, played: ud?.Played === true,
  };
}

/** Réponse paginée `{"Items":[…],"TotalRecordCount":N}` ; illisible = page vide. */
export function parseItems(text: string): { total: number; items: Item[] } {
  const o = obj(parse(text));
  const raw = Array.isArray(o?.Items) ? o!.Items : [];
  const items = raw.map(obj).filter((x): x is J => !!x).map(item).filter((x): x is Item => !!x);
  return { total: num(o?.TotalRecordCount) ?? items.length, items };
}
export const parseItem = (text: string): Item | null => { const o = obj(parse(text)); return o ? item(o) : null; };

// ---------------------------------------------------------------- identifiants et liens internes

/**
 * Identifiant numérique local (les lignes du catalogue sont indexées par nombre) : l'identifiant lui-même s'il est numérique
 * (Emby), sinon les 48 premiers bits de l'identifiant hexadécimal du serveur (Jellyfin : 32 chiffres hexadécimaux aléatoires).
 */
export function numericId(guid: string): number {
  if (/^\d{1,15}$/.test(guid)) return Number(guid);
  const hex = guid.replace(/[^0-9a-f]/gi, "").slice(0, 12);
  if (hex.length >= 8) return parseInt(hex, 16);
  let h = 0;
  for (let i = 0; i < guid.length; i++) h = (Math.imul(h, 31) + guid.charCodeAt(i)) >>> 0;
  return h;
}

export const mediaLink = (itemId: string) => `media:${itemId}`;
export const isMediaLink = (u: string | null | undefined): u is string => !!u && u.startsWith("media:");
export function parseMediaLink(u: string): string | null {
  const m = /^media:([0-9A-Za-z_-]+)$/.exec(u);
  return m ? m[1]! : null;
}

// ---------------------------------------------------------------- adresses

const q = encodeURIComponent;
export const LIST_FIELDS = "Overview,Genres,People,ProductionYear,CommunityRating,DateCreated,MediaSources,RunTimeTicks,BackdropImageTags,ImageTags";

/** Affiche du serveur : jamais d'authentification dans la valeur stockée. */
export const imageUrl = (base: string, itemId: string, type = "Primary", tag?: string | null, maxWidth = 480) =>
  `${base}/Items/${itemId}/Images/${type}?maxWidth=${maxWidth}&quality=90` + (tag ? `&tag=${q(tag)}` : "");

export const itemsUrl = (base: string, userId: string, libraryId: string, type: string, start: number, limit = PAGE_SIZE) =>
  `${base}/Users/${userId}/Items?ParentId=${libraryId}&IncludeItemTypes=${type}&Recursive=true&Fields=${LIST_FIELDS}&SortBy=SortName&SortOrder=Ascending&StartIndex=${start}&Limit=${limit}`;
export const episodesUrl = (base: string, seriesId: string, userId: string) =>
  `${base}/Shows/${seriesId}/Episodes?UserId=${userId}&Fields=Overview,MediaSources,RunTimeTicks,ImageTags`;
export const itemUrl = (base: string, userId: string, itemId: string) => `${base}/Users/${userId}/Items/${itemId}`;

/** Lecture directe (Direct Play : fichier d'origine, sans transcodage). Le jeton ne sert qu'ici, jamais stocké. */
export function playUrl(base: string, itemId: string, token: string, mediaSourceId?: string | null, container?: string | null): string {
  const ext = container && /^[0-9a-z]{1,5}$/i.test(container) ? `.${container.toLowerCase()}` : "";
  return `${base}/Videos/${itemId}/stream${ext}?static=true${mediaSourceId ? `&MediaSourceId=${q(mediaSourceId)}` : ""}&api_key=${q(token)}`;
}

/** Repli (audio AC-3 / DTS illisible par le navigateur) : flux HLS que le serveur remux ou transcode (Direct Stream si possible). */
export function hlsUrl(base: string, itemId: string, token: string, mediaSourceId?: string | null): string {
  return `${base}/Videos/${itemId}/master.m3u8?${mediaSourceId ? `MediaSourceId=${q(mediaSourceId)}&` : ""}VideoCodec=h264&AudioCodec=aac,mp3&SegmentContainer=ts&MinSegments=1&BreakOnNonKeyFrames=true&TranscodingMaxAudioChannels=2&api_key=${q(token)}`;
}

// ---------------------------------------------------------------- reprise et rapports de lecture

export const ticksToSec = (ticks: number) => Math.max(0, ticks / TICKS_PER_SEC);
export const secToTicks = (sec: number) => Math.round(Math.max(0, sec) * TICKS_PER_SEC);

/**
 * Position de reprise (secondes) : celle du SERVEUR si elle existe (elle suit l'utilisateur d'un appareil à l'autre), sinon
 * [local]. Un élément vu en entier (`played`, position remise à zéro) repart du début.
 */
export function resumeSeconds(server: Pick<Item, "positionTicks" | "played"> | null, local: number | undefined): number | undefined {
  if (!server) return local;
  if (server.positionTicks > 0) return ticksToSec(server.positionTicks);
  return server.played ? undefined : local;
}

export type ReportKind = "start" | "progress" | "stop";
const REPORT_PATH: Record<ReportKind, string> = { start: "/Sessions/Playing", progress: "/Sessions/Playing/Progress", stop: "/Sessions/Playing/Stopped" };
export const reportPath = (k: ReportKind) => REPORT_PATH[k];

export const reportBody = (itemId: string, positionSec: number, paused: boolean) =>
  JSON.stringify({ ItemId: itemId, PositionTicks: secToTicks(positionSec), IsPaused: paused, PlayMethod: "DirectPlay", CanSeek: true });

/** « 01:23:45 » depuis des ticks ; null si inconnue. */
export function durationSecs(ticks: number | null): number {
  return ticks ? Math.round(ticks / TICKS_PER_SEC) : 0;
}

// ---------------------------------------------------------------- fiches (format des fiches Xtream lues par l'écran de détail)

/** Fiche de film présentée comme `get_vod_info` ; `resumePos` = position de reprise enregistrée sur le serveur (secondes, 0 = aucune). */
export function vodInfoOf(it: Item) {
  return {
    info: {
      plot: it.overview ?? undefined, genre: it.genres.join(", ") || undefined, cast: it.cast.join(", ") || undefined,
      director: it.directors.join(", ") || undefined, duration_secs: durationSecs(it.runtimeTicks) || undefined,
      releasedate: it.year ? String(it.year) : undefined,
    },
    resumePos: it.positionTicks > 0 ? Math.floor(ticksToSec(it.positionTicks)) : 0,
  };
}

/** Épisodes d'une série présentés comme `get_series_info` (par saison) + lien interne de chaque épisode (`media`, voir resolve.ts). */
export function seriesInfoOf(eps: Item[], base: string) {
  const episodes: Record<string, Array<{ id: number; title: string; episode_num: number; container_extension: string; info: { plot?: string; movie_image?: string; duration_secs?: number } }>> = {};
  const media: Array<{ id: number; url: string }> = [];
  for (const e of eps) {
    const season = e.season ?? 1;
    const list = (episodes[String(season)] ??= []);
    const id = numericId(e.id);
    list.push({
      id, title: e.name, episode_num: e.episode ?? list.length + 1, container_extension: e.container ?? "",
      info: { plot: e.overview ?? undefined, movie_image: e.primaryTag ? imageUrl(base, e.id, "Primary", e.primaryTag, 640) : undefined, duration_secs: durationSecs(e.runtimeTicks) || undefined },
    });
    media.push({ id, url: mediaLink(e.id) });
  }
  return { episodes, media };
}

// ---------------------------------------------------------------- client réseau

export interface MediaCreds { server: string; username: string; password: string }
interface Session { token: string; userId: string }

/** Client d'un serveur : connexion paresseuse (mémorisée, refaite une fois sur 401), listes, fiche, rapports. Jamais de journal d'adresse. */
export class MediaClient {
  private sess: Promise<Session> | null = null;
  readonly base: string;
  constructor(private t: Transport, private c: MediaCreds, private signal?: AbortSignal) {
    const b = normalizeServerUrl(c.server);
    if (!b) throw new Error("auth");
    this.base = b;
  }

  /** Connexion : identifiant + mot de passe, ou (identifiant vide) clé API. Lève `auth` si refusée. */
  async login(): Promise<Session> {
    const key = !this.c.username.trim();
    try {
      if (key) {
        const token = this.c.password.trim();
        if (!token) throw new Error("auth");
        const u = pickUser(await this.text(`${this.base}/Users?api_key=${q(token)}`), "");
        if (!u) throw new Error("auth");
        return { token, userId: u.id };
      }
      const res = await transportFetch(this.t, `${this.base}/Users/AuthenticateByName`, { signal: this.signal, post: authBody(this.c.username.trim(), this.c.password), headers: { authorization: authorization() } });
      const a = parseAuth(await res.text());
      if (!a) throw new Error("auth");
      return { token: a.token, userId: a.userId };
    } catch (e) {
      if (e instanceof HttpError && (e.status === 401 || e.status === 403)) throw new Error("auth");
      throw e;
    }
  }

  session(): Promise<Session> {
    if (!this.sess) {
      const p = this.login();
      p.catch(() => { if (this.sess === p) this.sess = null; });
      this.sess = p;
    }
    return this.sess;
  }

  private async text(url: string): Promise<string> {
    return (await transportFetch(this.t, url, { signal: this.signal })).text();
  }

  /** GET authentifié (clé API en paramètre) ; un 401 déclenche UNE nouvelle connexion. */
  private async get(build: (s: Session) => string): Promise<string> {
    for (let attempt = 0; ; attempt++) {
      const s = await this.session();
      try {
        const url = build(s);
        return await this.text(url + (url.includes("?") ? "&" : "?") + `api_key=${q(s.token)}`);
      } catch (e) {
        if (e instanceof HttpError && e.status === 401 && attempt === 0) { this.sess = null; continue; }
        throw e;
      }
    }
  }

  /** Vérifie la connexion (formulaire d'ajout). */
  async probe(): Promise<void> { await this.session(); }

  libraries = async (): Promise<Library[]> => parseViews(await this.get((s) => `${this.base}/Users/${s.userId}/Views`));

  /** Tous les éléments d'un type dans une bibliothèque, page par page. */
  async items(libraryId: string, type: "Movie" | "Series"): Promise<Item[]> {
    const out: Item[] = [];
    for (let start = 0; start < 200_000; start += PAGE_SIZE) {
      const page = parseItems(await this.get((s) => itemsUrl(this.base, s.userId, libraryId, type, start)));
      out.push(...page.items);
      if (!page.items.length || out.length >= page.total) break;
    }
    return out;
  }

  async episodes(seriesId: string): Promise<Item[]> {
    const page = parseItems(await this.get((s) => episodesUrl(this.base, seriesId, s.userId)));
    return page.items.filter((i) => !i.type || i.type === "Episode");
  }

  item = async (id: string): Promise<Item | null> => parseItem(await this.get((s) => itemUrl(this.base, s.userId, id)));

  /** Adresses de lecture d'un élément : directe d'abord, HLS (serveur) ensuite ; fiche (reprise) jointe si lisible. */
  async playback(id: string): Promise<{ urls: { direct: string; hls: string }; item: Item | null }> {
    const s = await this.session();
    const it = await this.item(id).catch(() => null);
    return { urls: { direct: playUrl(this.base, id, s.token, it?.mediaSourceId, it?.container), hls: hlsUrl(this.base, id, s.token, it?.mediaSourceId) }, item: it };
  }

  /** Rapport de lecture ; ne lève jamais (hors annulation). Nécessite POST : proxy Electron ou accès direct. */
  async report(kind: ReportKind, id: string, positionSec: number, paused: boolean): Promise<void> {
    try {
      const s = await this.session();
      await transportFetch(this.t, `${this.base}${reportPath(kind)}?api_key=${q(s.token)}`, { post: reportBody(id, positionSec, paused) });
    } catch (e) {
      if ((e as Error).name === "AbortError") throw e;
    }
  }
}
