// Métadonnées complémentaires (Fanart.tv, TVmaze, identifiants externes TMDB) : logique pure, sans réseau.
// Les réponses viennent du Worker (cloudflare-config/src/extmeta.js) ; tout est revalidé ici (types, URL HTTPS).

export interface ExternalIds { imdbId: string | null; tvdbId: number | null }
export interface FanartArt { logo: string | null; background: string | null; thumb: string | null }
export interface TvEpisode { season: number | null; number: number | null; name: string | null; airAt: number | null; image: string | null; summary: string | null }
export interface TvShow { id: number | null; status: string | null; network: string | null; previous: TvEpisode | null; next: TvEpisode | null }

const IMDB = /^tt[0-9]{5,10}$/;
const isObj = (v: unknown): v is Record<string, unknown> => !!v && typeof v === "object" && !Array.isArray(v);
const str = (v: unknown): string | null => (typeof v === "string" && v.trim() ? v.trim() : null);
const int = (v: unknown): number | null => (typeof v === "number" && Number.isInteger(v) ? v : null);
const posInt = (v: unknown): number | null => { const n = typeof v === "string" && /^[0-9]+$/.test(v) ? Number(v) : int(v); return n != null && n > 0 ? n : null; };
/** URL d'image acceptée : HTTPS uniquement. */
export const httpsUrl = (v: unknown): string | null => { const s = str(v); return s && /^https:\/\/[^\s"]+$/i.test(s) ? s : null; };

/**
 * Identifiants IMDb / TheTVDB d'une fiche TMDB demandée avec `append_to_response=external_ids`.
 * Film : `imdb_id` est aussi à la racine ; série : seulement dans `external_ids`.
 */
export function parseExternalIds(detail: unknown): ExternalIds {
  if (!isObj(detail)) return { imdbId: null, tvdbId: null };
  const ext = isObj(detail.external_ids) ? detail.external_ids : {};
  const imdb = [ext.imdb_id, detail.imdb_id].map(str).find((s) => !!s && IMDB.test(s)) ?? null;
  return { imdbId: imdb, tvdbId: posInt(ext.tvdb_id) };
}

/** Premier identifiant d'une recherche TMDB (`search/movie|tv`). */
export function firstSearchId(search: unknown): number | null {
  const results = isObj(search) && Array.isArray(search.results) ? search.results : [];
  for (const r of results) { const id = isObj(r) ? posInt(r.id) : null; if (id) return id; }
  return null;
}

export const NO_ART: FanartArt = { logo: null, background: null, thumb: null };

export function parseFanart(body: unknown): FanartArt {
  if (!isObj(body)) return NO_ART;
  return { logo: httpsUrl(body.logo), background: httpsUrl(body.background), thumb: httpsUrl(body.thumb) };
}

function parseEpisode(e: unknown): TvEpisode | null {
  if (!isObj(e)) return null;
  const airAt = typeof e.airAt === "number" && Number.isFinite(e.airAt) && e.airAt > 0 ? e.airAt : null;
  return { season: int(e.season), number: int(e.number), name: str(e.name), airAt, image: httpsUrl(e.image), summary: str(e.summary) };
}

export function parseTvShow(body: unknown): TvShow | null {
  if (!isObj(body)) return null;
  return { id: posInt(body.id), status: str(body.status), network: str(body.network), previous: parseEpisode(body.previous), next: parseEpisode(body.next) };
}

/** État de diffusion d'une série pour la fiche : prochain épisode daté, sinon série terminée, sinon dernier épisode diffusé. */
export type AirState = { kind: "next"; ep: TvEpisode } | { kind: "ended"; ep: TvEpisode | null } | { kind: "last"; ep: TvEpisode } | null;

export function airState(show: TvShow | null | undefined, now: number): AirState {
  if (!show) return null;
  if (show.next && show.next.airAt != null && show.next.airAt >= now - 6 * 3600_000) return { kind: "next", ep: show.next };
  if (show.status === "Ended") return { kind: "ended", ep: show.previous };
  if (show.previous && show.previous.airAt != null) return { kind: "last", ep: show.previous };
  return null;
}

export const DAY_MS = 86400_000;

/** Candidat de la rangée « Nouveaux épisodes » : série de la playlist. */
export interface NewEpCandidate { seriesId: number; title: string }
export interface NewEpisode<C extends NewEpCandidate = NewEpCandidate> { series: C; ep: TvEpisode; aired: boolean }

/**
 * Séries à interroger : en cours (historique récent d'abord), puis favorites ; sans doublon, au plus `max`.
 */
export function pickCandidates(
  history: ReadonlyArray<{ kind: string; seriesId?: number; updatedAt: number; title: string }>,
  favorites: ReadonlyArray<{ kind: string; refId: number; name: string; addedAt: number }>,
  max = 20,
): NewEpCandidate[] {
  const out = new Map<number, NewEpCandidate>();
  for (const h of [...history].filter((x) => x.kind === "series" && x.seriesId != null).sort((a, b) => b.updatedAt - a.updatedAt)) {
    if (!out.has(h.seriesId!)) out.set(h.seriesId!, { seriesId: h.seriesId!, title: h.title });
  }
  for (const f of [...favorites].filter((x) => x.kind === "series").sort((a, b) => b.addedAt - a.addedAt)) {
    if (!out.has(f.refId)) out.set(f.refId, { seriesId: f.refId, title: f.name });
  }
  return [...out.values()].slice(0, Math.max(0, max));
}

/**
 * Épisodes à montrer : sorti dans les `days` derniers jours (prioritaire) ou à venir dans les `days` jours.
 * Tri : épisodes sortis du plus récent au plus ancien, puis épisodes à venir du plus proche au plus lointain.
 */
export function pickNewEpisodes<C extends NewEpCandidate>(entries: ReadonlyArray<{ series: C; show: TvShow | null }>, now: number, days = 7): NewEpisode<C>[] {
  const win = days * DAY_MS;
  const out: NewEpisode<C>[] = [];
  for (const { series, show } of entries) {
    if (!show) continue;
    const p = show.previous;
    const n = show.next;
    if (p?.airAt != null && p.airAt <= now && now - p.airAt <= win) out.push({ series, ep: p, aired: true });
    else if (n?.airAt != null && n.airAt > now && n.airAt - now <= win) out.push({ series, ep: n, aired: false });
    else if (n?.airAt != null && n.airAt <= now && now - n.airAt <= win) out.push({ series, ep: n, aired: true });
  }
  return out.sort((a, b) => (a.aired !== b.aired ? (a.aired ? -1 : 1) : a.aired ? b.ep.airAt! - a.ep.airAt! : a.ep.airAt! - b.ep.airAt!));
}

/** Jour de diffusion lisible : « aujourd'hui », « demain », « hier », sinon « mar. 18 nov. » (heure locale). */
export function fmtAirDay(airAt: number, now: number, locale: string): string {
  const startOf = (t: number) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
  const diff = Math.round((startOf(airAt) - startOf(now)) / DAY_MS);
  if (Math.abs(diff) <= 1) return new Intl.RelativeTimeFormat(locale, { numeric: "auto" }).format(diff, "day");
  const sameYear = new Date(airAt).getFullYear() === new Date(now).getFullYear();
  return new Intl.DateTimeFormat(locale, { weekday: "short", day: "numeric", month: "short", ...(sameYear ? {} : { year: "numeric" }) }).format(airAt);
}

/** « S2 É5 · titre · demain » : `code` reçoit (saison, épisode) et rend le code localisé. Parties absentes omises. */
export function fmtEpisodeLine(ep: TvEpisode, now: number, locale: string, code: (s: number, e: number) => string, withName = true): string {
  return [
    ep.season != null && ep.number != null ? code(ep.season, ep.number) : null,
    withName ? ep.name : null,
    ep.airAt != null ? fmtAirDay(ep.airAt, now, locale) : null,
  ].filter(Boolean).join(" · ");
}
