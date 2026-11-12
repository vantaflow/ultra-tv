// Logos / fonds Fanart.tv et calendrier TVmaze des fiches et de l'accueil. Seulement sur le bureau appairé (mêmes
// proxys et même jeton d'appareil que TMDB et les notes) ; sinon rien de plus ne s'affiche. Mode démo (VITE_DEMO=1) :
// données fictives, aucun appel réseau.
// Cache : mémoire + table `details` (clés `ext:…`, aucune migration Dexie) ; les échecs (réseau, 401, 429) ne sont pas mémorisés.
import { useEffect, useState } from "react";
import { cloudAvailable, fanartGet, tmdbGet, tvmazeGet } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";
import { db } from "@/db/db";
import type { FavoriteRow, HistoryRow, Source } from "@/db/types";
import {
  DAY_MS, NO_ART, firstSearchId, parseExternalIds, parseFanart, parseTvShow, pickCandidates, pickNewEpisodes,
  type ExternalIds, type FanartArt, type NewEpCandidate, type NewEpisode, type TvShow,
} from "@/lib/extmeta";
import { parseTmdbSeason, type TmdbEpisode } from "@/lib/episodeTitles";
import { normText } from "@/lib/text";
import { tmdbOf } from "@/player/trakt";
import { usePrefs } from "@/state/prefs";
import { TMDB_LANG } from "./trailer";

const DEMO = import.meta.env.VITE_DEMO === "1";

/** Titre à enrichir : identifiant TMDB s'il est connu, sinon titre (+ année) ; `detailKey` : fiche Xtream en cache où lire l'identifiant. */
export interface TitleRef { kind: "movie" | "tv"; tmdb?: number; title?: string; year?: number | null; detailKey?: string }

const H = 3600_000;
const TTL = { ids: 30 * DAY_MS, find: 30 * DAY_MS, fanart: 7 * DAY_MS, tvmaze: 6 * H, season: 7 * DAY_MS };

const mem = new Map<string, { v: unknown; at: number }>();
const inflight = new Map<string, Promise<unknown>>();

/** Valeur en cache (mémoire, puis disque) si plus jeune que `ttl`, sinon produite puis mémorisée. Les erreurs remontent. */
async function cachedValue<T>(key: string, ttl: number, produce: () => Promise<T>): Promise<T> {
  const m = mem.get(key);
  if (m && Date.now() - m.at < ttl) return m.v as T;
  const running = inflight.get(key);
  if (running) return running as Promise<T>;
  const p = (async () => {
    try {
      const row = await db.details.get(key);
      if (row && Date.now() - row.fetchedAt < ttl) { mem.set(key, { v: row.json, at: row.fetchedAt }); return row.json as T; }
    } catch { /* base indisponible : on interroge */ }
    const v = await produce();
    const at = Date.now();
    mem.set(key, { v, at });
    void db.details.put({ key, json: v, fetchedAt: at }).catch(() => {});
    return v;
  })();
  inflight.set(key, p);
  try { return await p; } finally { inflight.delete(key); }
}

interface Ctx { worker: string; token: string }
async function cloudCtx(): Promise<Ctx | null> {
  const s = useCloud.getState();
  if (!s.paired || !cloudAvailable()) return null;
  const token = await getToken().catch(() => "");
  return token ? { worker: s.worker, token } : null;
}

async function resolveTmdbId(c: Ctx, ref: TitleRef, lang: string): Promise<number | null> {
  if (ref.tmdb) return ref.tmdb;
  if (ref.detailKey) {
    const row = await db.details.get(ref.detailKey).catch(() => undefined);
    const info = (row?.json as { info?: { tmdb?: unknown; tmdb_id?: unknown } } | undefined)?.info;
    const id = tmdbOf(info?.tmdb) ?? tmdbOf(info?.tmdb_id);
    if (id) return id;
  }
  const title = ref.title?.trim();
  if (!title) return null;
  const year = ref.year && ref.year >= 1900 && ref.year <= 2099 ? ref.year : null;
  return cachedValue(`ext:find:${ref.kind}:${normText(title)}:${year ?? ""}`, TTL.find, async () => {
    const p = new URLSearchParams({ query: title.slice(0, 120), language: TMDB_LANG[lang as keyof typeof TMDB_LANG] ?? "en-US" });
    if (year) p.set(ref.kind === "tv" ? "first_air_date_year" : "year", String(year));
    return firstSearchId(await tmdbGet(c.worker, c.token, `search/${ref.kind}?${p}`));
  });
}

/** Identifiants externes (IMDb, TheTVDB) d'un titre TMDB, demandés avec la fiche (`append_to_response=external_ids`). */
async function externalIds(c: Ctx, kind: "movie" | "tv", tmdb: number): Promise<ExternalIds> {
  return cachedValue(`ext:ids:${kind}:${tmdb}`, TTL.ids, async () =>
    parseExternalIds(await tmdbGet(c.worker, c.token, `${kind}/${tmdb}?append_to_response=external_ids`)));
}

/** Logo / fond Fanart d'un titre ; NO_ART si rien (ou Fanart non configuré sur le Worker). Lève sur erreur réseau. */
export async function loadFanart(ref: TitleRef, lang: string): Promise<FanartArt> {
  if (DEMO) return (await import("@/demoExt")).demoFanart(ref);
  const c = await cloudCtx();
  if (!c) return NO_ART;
  const tmdb = await resolveTmdbId(c, ref, lang);
  if (!tmdb) return NO_ART;
  const id = ref.kind === "movie" ? tmdb : (await externalIds(c, "tv", tmdb)).tvdbId;
  if (!id) return NO_ART;
  return cachedValue(`ext:fanart:${ref.kind}:${id}:${lang}`, TTL.fanart, async () => parseFanart(await fanartGet(c.worker, c.token, ref.kind, id, lang)));
}

/** Série TVmaze (statut, épisodes précédent / prochain) ; null si introuvable. Lève sur erreur réseau. */
export async function loadTvShow(ref: TitleRef, lang: string): Promise<TvShow | null> {
  if (DEMO) return (await import("@/demoExt")).demoTvShow(ref);
  const c = await cloudCtx();
  if (!c) return null;
  const tmdb = await resolveTmdbId(c, { ...ref, kind: "tv" }, lang);
  if (!tmdb) return null;
  const ids = await externalIds(c, "tv", tmdb);
  const q = ids.imdbId ? `imdb=${ids.imdbId}` : ids.tvdbId ? `tvdb=${ids.tvdbId}` : null;
  if (!q) return null;
  return cachedValue(`ext:tvmaze:${q}`, TTL.tvmaze, async () => parseTvShow(await tvmazeGet(c.worker, c.token, { imdb: ids.imdbId, tvdb: ids.tvdbId })));
}

const refKey = (r: TitleRef | null) => (r ? `${r.kind}:${r.tmdb ?? ""}:${r.detailKey ?? ""}:${r.tmdb ? "" : `${r.title ?? ""}:${r.year ?? ""}`}` : "");
const enabled = (paired: boolean) => DEMO || (paired && cloudAvailable());

function useLoaded<T>(ref: TitleRef | null, empty: T, load: (r: TitleRef, lang: string) => Promise<T>, prefix: string): T {
  const paired = useCloud((s) => s.paired);
  const lang = usePrefs((s) => s.lang);
  const key = ref ? `${prefix}:${refKey(ref)}:${lang}` : "";
  const [state, setState] = useState<{ key: string; v: T }>({ key: "", v: empty });
  useEffect(() => {
    if (!ref || !enabled(paired) || (!ref.tmdb && !ref.title && !ref.detailKey)) return;
    let dead = false;
    void load(ref, lang).then((v) => { if (!dead) setState({ key, v }); }, () => { /* réseau, 401, 429 : rien de plus */ });
    return () => { dead = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, paired]);
  return state.key === key ? state.v : empty;
}

/** Logo et fond Fanart du titre (NO_ART tant que rien n'est connu). */
export const useFanart = (ref: TitleRef | null): FanartArt => useLoaded(ref, NO_ART, loadFanart, "fanart");
/** Calendrier TVmaze d'une série (null tant que rien n'est connu). */
export const useTvShow = (ref: TitleRef | null): TvShow | null => useLoaded<TvShow | null>(ref, null, loadTvShow, "tvmaze");

/**
 * Épisodes TMDB d'une saison (titres, résumés, vignettes) dans la langue de l'appli ; liste vide si rien.
 * Cache `tmdbseason:{id}:{n}:{lang}` (mémoire + table `details`, 7 jours). Lève sur erreur réseau (non mémorisée).
 */
export async function loadTmdbSeason(ref: TitleRef, season: number, lang: string): Promise<TmdbEpisode[]> {
  if (DEMO) return []; // démo : aucun appel réseau (les épisodes fictifs ont déjà leurs titres)
  if (!Number.isInteger(season) || season < 0) return [];
  const c = await cloudCtx();
  if (!c) return [];
  const tmdb = await resolveTmdbId(c, { ...ref, kind: "tv" }, lang);
  if (!tmdb) return [];
  const tl = TMDB_LANG[lang as keyof typeof TMDB_LANG] ?? "en-US";
  return cachedValue(`tmdbseason:${tmdb}:${season}:${lang}`, TTL.season, async () =>
    parseTmdbSeason(await tmdbGet(c.worker, c.token, `tv/${tmdb}/season/${season}?language=${encodeURIComponent(tl)}`)));
}

/** Épisodes TMDB de la saison affichée (vide tant que rien n'est connu, hors appairage ou en cas d'échec). */
export function useTmdbSeason(ref: TitleRef | null, season: number | null | undefined): TmdbEpisode[] {
  const s = season ?? null;
  return useLoaded<TmdbEpisode[]>(s != null ? ref : null, EMPTY_EPS, (x, lang) => loadTmdbSeason(x, s ?? 0, lang), `season${s ?? ""}`);
}
const EMPTY_EPS: TmdbEpisode[] = [];

export interface NewEpSeries extends NewEpCandidate { year: number | null; poster: string | null }

/** Tâches en parallèle limitées (le Worker limite le débit par appareil). */
async function pool<T, R>(items: T[], n: number, fn: (x: T) => Promise<R>): Promise<R[]> {
  const out: R[] = new Array(items.length);
  let i = 0;
  await Promise.all(Array.from({ length: Math.min(n, items.length) }, async () => {
    while (i < items.length) { const k = i++; out[k] = await fn(items[k]!); }
  }));
  return out;
}

/** Rangée « Nouveaux épisodes » : séries en cours ou favorites (au plus 20 interrogées) avec un épisode à ±7 jours. */
export async function loadNewEpisodes(source: Source, history: HistoryRow[], favorites: FavoriteRow[], lang: string, now = Date.now()): Promise<NewEpisode<NewEpSeries>[]> {
  const cands = pickCandidates(history, favorites, 20);
  const rows = await pool(cands, 4, async (c) => {
    const row = await db.series.where("[sourceId+seriesId]").equals([source.cid, c.seriesId]).first().catch(() => undefined);
    if (!row) return null; // plus dans la playlist
    const series: NewEpSeries = { seriesId: c.seriesId, title: row.title, year: row.year ?? null, poster: row.poster ?? null };
    const show = await loadTvShow({ kind: "tv", title: row.title, year: row.year, detailKey: `series:${source.id}:${c.seriesId}` }, lang).catch(() => null);
    return { series, show };
  });
  return pickNewEpisodes(rows.filter((r): r is { series: NewEpSeries; show: TvShow | null } => !!r), now);
}

export function useNewEpisodes(source: Source, history: HistoryRow[], favorites: FavoriteRow[]): NewEpisode<NewEpSeries>[] {
  const paired = useCloud((s) => s.paired);
  const lang = usePrefs((s) => s.lang);
  const ids = [...new Set([...history.filter((h) => h.kind === "series").map((h) => h.seriesId), ...favorites.filter((f) => f.kind === "series").map((f) => f.refId)])].join(",");
  const [list, setList] = useState<NewEpisode<NewEpSeries>[]>([]);
  useEffect(() => {
    if (!enabled(paired) || !ids) { setList([]); return; }
    let dead = false;
    void loadNewEpisodes(source, history, favorites, lang).then((l) => { if (!dead) setList(l); }, () => {});
    return () => { dead = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ids, paired, lang, source.cid]);
  return list;
}

/** Tests : vide le cache mémoire. */
export const _resetExtCache = () => { mem.clear(); inflight.clear(); };
