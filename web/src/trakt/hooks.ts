import { useEffect, useMemo, useRef, useState } from "react";
import { useCloud } from "@/cloud/service";
import { db } from "@/db/db";
import type { MovieRow, SeriesRow, Source } from "@/db/types";
import { usePrefs } from "@/state/prefs";
import { histKey } from "@/db/queries";
import type { HistoryRow } from "@/db/types";
import type { TraktItem, TraktPlayback } from "@/cloud/client";
import { availableItems, isWatchedMovie, watchedEpisodes, type Available } from "./availability";
import { buildCatalogIndex, useCatalogIndex, wantedTokens, type CatalogIndex } from "./catalog";
import { episodesOf } from "./episodes";
import { refreshTraktLibrary, TRAKT_REFRESH_MS, useTraktLibrary } from "./library";
import { useTraktPrefs, visibleLists } from "./prefs";
import { nextEpisode, resumeDecision, upNextCandidates, UP_NEXT_MAX, type Linked } from "./progress";

/** À monter une fois (App) : charge la bibliothèque à l'appairage, au changement de langue ou de worker, puis toutes les 15 min. */
export function useTraktSync(sourceId: number | undefined): void {
  const paired = useCloud((s) => s.paired);
  const worker = useCloud((s) => s.worker);
  const lang = usePrefs((s) => s.lang);
  useEffect(() => { void refreshTraktLibrary(); }, [paired, worker, lang]);
  // Changement de source : rafraîchissement forcé (sauf au premier rendu, déjà couvert ci-dessus).
  const [first, setFirst] = useState(true);
  useEffect(() => { if (first) { setFirst(false); return; } void refreshTraktLibrary(true); }, [sourceId]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!paired) return;
    const t = setInterval(() => void refreshTraktLibrary(), TRAKT_REFRESH_MS);
    return () => clearInterval(t);
  }, [paired]);
}

export type TraktRow =
  | { kind: "movie"; ref: number; row: MovieRow }
  | { kind: "show"; ref: number; row: SeriesRow };

async function rowsOf(source: Source, av: Available[]): Promise<TraktRow[]> {
  const mv = av.filter((a) => a.kind === "movie").map((a) => [source.cid, a.ref] as [number, number]);
  const sh = av.filter((a) => a.kind === "show").map((a) => [source.cid, a.ref] as [number, number]);
  const [ms, ss] = await Promise.all([
    mv.length ? db.movies.where("[sourceId+streamId]").anyOf(mv).toArray() : Promise.resolve([] as MovieRow[]),
    sh.length ? db.series.where("[sourceId+seriesId]").anyOf(sh).toArray() : Promise.resolve([] as SeriesRow[]),
  ]);
  const mm = new Map(ms.map((r) => [r.streamId, r]));
  const sm = new Map(ss.map((r) => [r.seriesId, r]));
  const out: TraktRow[] = [];
  for (const a of av) {
    if (a.kind === "movie") { const row = mm.get(a.ref); if (row) out.push({ kind: "movie", ref: a.ref, row }); }
    else { const row = sm.get(a.ref); if (row) out.push({ kind: "show", ref: a.ref, row }); }
  }
  return out;
}

/** « À suivre » : la série du catalogue et le prochain épisode DISPONIBLE dans la playlist. */
export interface UpNextRow { ref: number; row: SeriesRow; season: number; episode: number }
export interface TraktListRows { id: string; name: string; rows: TraktRow[] }
export interface TraktRows {
  watchlist: TraktRow[]; recommendations: TraktRow[]; trending: TraktRow[]; popular: TraktRow[];
  lists: TraktListRows[]; upNext: UpNextRow[];
}
const NONE: TraktRows = { watchlist: [], recommendations: [], trending: [], popular: [], lists: [], upNext: [] };
/** Fiches de séries demandées à la source par calcul (« À suivre », reprises d'épisodes). */
const EPISODE_LOADS = 3;
const LISTS = ["watchlist", "recommendations", "trending", "popular"] as const;
type Refs = Record<(typeof LISTS)[number], [("movie" | "show"), number][]>;

// Dernières rangées résolues, par génération de catalogue (cid) : réaffichées tout de suite au lancement
// (simple lecture par clé en base), puis recalculées en arrière-plan quand l'index est prêt.
export const ROWS_STORAGE_KEY = "utv.trakt.rows.v1";
export function loadSavedRefs(cid: number): Refs | null {
  try {
    const o = JSON.parse(localStorage.getItem(ROWS_STORAGE_KEY) ?? "null") as { cid?: number; refs?: Refs } | null;
    if (!o || o.cid !== cid || !o.refs) return null;
    const out = {} as Refs;
    for (const k of LISTS) out[k] = Array.isArray(o.refs[k]) ? o.refs[k].filter((p) => Array.isArray(p) && (p[0] === "movie" || p[0] === "show") && typeof p[1] === "number") : [];
    return out;
  } catch { return null; }
}
export function saveRefs(cid: number, rows: TraktRows): void {
  try {
    const refs = {} as Refs;
    for (const k of LISTS) refs[k] = rows[k].map((r) => [r.kind, r.ref]);
    localStorage.setItem(ROWS_STORAGE_KEY, JSON.stringify({ cid, refs }));
  } catch { /* stockage indisponible : sans effet */ }
}
const rowsFromRefs = async (source: Source, refs: Refs): Promise<TraktRows> => {
  const o = { lists: [], upNext: [] } as unknown as TraktRows;
  const all = await Promise.all(LISTS.map((k) => rowsOf(source, refs[k].map(([kind, ref]) => ({ kind, ref, item: null as never })))));
  LISTS.forEach((k, n) => { o[k] = all[n]!; });
  return o;
};

/** Toutes les listes Trakt à rapprocher du catalogue (rangées, listes perso, reprises, séries « À suivre »). */
export function wantedLists(lib: Linked): TraktItem[][] {
  return [lib.watchlist, lib.recommendations, lib.trending, lib.popular, ...lib.lists.map((l) => l.items), lib.playback, upNextCandidates(lib)];
}

/** Premiers mots des clés de la bibliothèque (pré-filtre d'indexation), stable tant que la bibliothèque ne change pas. */
export function useWantedTokens(): Set<string> | null {
  const lib = useTraktLibrary((s) => s.lib);
  return useMemo(() => (lib ? wantedTokens(wantedLists(lib)) : null), [lib]);
}

/** « À suivre » : séries regardées (les plus récentes d'abord) dont l'épisode suivant existe dans la playlist. */
export async function computeUpNext(source: Source, lib: Linked, idx: CatalogIndex, budget = { left: EPISODE_LOADS }): Promise<UpNextRow[]> {
  const out: UpNextRow[] = [];
  const av = availableItems(upNextCandidates(lib), null, idx.series);
  for (const a of av) {
    if (out.length >= UP_NEXT_MAX) break;
    const show = a.item as TraktItem & { episodes?: string[] };
    const eps = await episodesOf(source, a.ref, budget);
    const next = nextEpisode(show.episodes ?? [], eps.map((e) => ({ season: e.season, episode: e.num })));
    if (!next) continue;
    const row = await db.series.where("[sourceId+seriesId]").equals([source.cid, a.ref]).first();
    if (row) out.push({ ref: a.ref, row, season: next.season, episode: next.episode });
  }
  return out;
}

// Reprises Trakt déjà importées (une pause n'est importée qu'une fois, même si la carte est ensuite retirée).
export const IMPORTED_KEY = "utv.trakt.imported.v1";
const MAX_IMPORTED = 400;
function loadImported(): string[] { try { const a = JSON.parse(localStorage.getItem(IMPORTED_KEY) ?? "[]"); return Array.isArray(a) ? a.filter((x) => typeof x === "string") : []; } catch { return []; } }
function saveImported(a: string[]): void { try { localStorage.setItem(IMPORTED_KEY, JSON.stringify(a.slice(-MAX_IMPORTED))); } catch { /* stockage indisponible */ } }

/**
 * Importe les reprises Trakt (/sync/playback) dans l'historique local : seulement là où l'historique n'a rien de plus
 * récent. Renvoie le nombre de lignes écrites.
 */
export async function importPlayback(source: Source, profile: string, playback: TraktPlayback[], idx: CatalogIndex, budget = { left: EPISODE_LOADS }, now = Date.now()): Promise<number> {
  const done = loadImported();
  const doneSet = new Set(done);
  let written = 0;
  for (const p of playback) {
    const [a] = availableItems([p], idx.movies, idx.series);
    if (!a) continue;
    const runtime = p.runtime ? p.runtime * 60 : 0;
    let row: HistoryRow | null = null;
    if (a.kind === "movie") {
      const key = histKey(profile, source.id!, "movie", a.ref);
      const tag = `${key}|${p.pausedAt}`;
      if (doneSet.has(tag)) continue;
      const m = await db.movies.where("[sourceId+streamId]").equals([source.cid, a.ref]).first();
      if (!m) continue;
      const det = await db.details.get(`vod:${source.id}:${a.ref}`);
      const secs = runtime || Number((det?.json as { info?: { duration_secs?: unknown } } | undefined)?.info?.duration_secs) || 0;
      const d = resumeDecision(await db.history.get(key), p.progress, p.pausedAt, secs, now);
      if (d) row = { key, profile, sourceId: source.id!, kind: "movie", refId: a.ref, title: m.title, image: m.poster, ext: m.ext, ...d };
      done.push(tag); doneSet.add(tag);
    } else {
      const ep = (await episodesOf(source, a.ref, budget)).find((e) => e.season === p.season && e.num === p.episode);
      if (!ep) continue; // fiche inconnue : retentée au calcul suivant
      const key = histKey(profile, source.id!, "series", a.ref) + `:${ep.id}`;
      const tag = `${key}|${p.pausedAt}`;
      if (doneSet.has(tag)) continue;
      const s = await db.series.where("[sourceId+seriesId]").equals([source.cid, a.ref]).first();
      const d = resumeDecision(await db.history.get(key), p.progress, p.pausedAt, runtime || ep.secs, now);
      if (d && s) row = { key, profile, sourceId: source.id!, kind: "series", refId: a.ref, title: s.title, image: s.poster, ext: ep.ext, epId: ep.id, seriesId: a.ref, season: ep.season, episode: ep.num, ...d };
      done.push(tag); doneSet.add(tag);
    }
    if (row) { await db.history.put(row); written++; }
  }
  saveImported(done);
  return written;
}

/** À monter une fois (App) : importe les reprises Trakt de la source active quand l'index du catalogue est prêt. */
export function useTraktResumeImport(source: Source | undefined): void {
  const lib = useTraktLibrary((s) => s.lib);
  const profile = usePrefs((s) => s.profileId);
  const wanted = useWantedTokens();
  const idx = useCatalogIndex(source, wanted);
  useEffect(() => {
    if (!lib || !source?.id || !idx || !lib.playback.length) return;
    void importPlayback(source, profile, lib.playback, idx).catch(() => {});
  }, [lib, idx, source, profile]);
}

/** À monter une fois (App) : construit l'index du catalogue de la source active dès le démarrage, sans attendre l'accueil. */
export function useTraktPrewarm(source: Source | undefined): void {
  const wanted = useWantedTokens();
  const cid = source?.cid ?? 0;
  useEffect(() => { if (cid && wanted) void buildCatalogIndex(cid, wanted).catch(() => {}); }, [cid, wanted]);
}

/** Rangées Trakt (watchlist, recommandations, tendances, populaires), réduites à ce qui est disponible dans la source active. */
export function useTraktRows(source: Source | undefined): TraktRows {
  const all = useAllTraktRows(source);
  const hidden = useTraktPrefs((s) => s.hiddenLists);
  return useMemo(() => ({ ...all, lists: visibleLists(all.lists.filter((l) => l.rows.length), hidden) }), [all, hidden]);
}

function useAllTraktRows(source: Source | undefined): TraktRows {
  const lib = useTraktLibrary((s) => s.lib);
  const wanted = useWantedTokens();
  const idx = useCatalogIndex(source, wanted);
  const [out, setOut] = useState<TraktRows>(NONE);
  const computed = useRef(false);
  const cid = source?.cid ?? 0;
  // Affichage instantané : dernières rangées connues, tant que l'index n'est pas prêt.
  useEffect(() => {
    computed.current = false;
    if (!lib || !source?.cid) return;
    const refs = loadSavedRefs(source.cid);
    if (!refs) return;
    let live = true;
    void rowsFromRefs(source, refs).then((r) => { if (live && !computed.current) setOut(r); }).catch(() => {});
    return () => { live = false; };
  }, [cid, !!lib]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    let live = true;
    if (!lib || !source?.cid) { setOut(NONE); return; }
    if (!idx) return;
    void (async () => {
      const all = await Promise.all(LISTS.map((k) => rowsOf(source, availableItems(lib[k], idx.movies, idx.series))));
      const lists = await Promise.all(lib.lists.map(async (l) => ({ id: l.id, name: l.name, rows: await rowsOf(source, availableItems(l.items, idx.movies, idx.series)) })));
      const upNext = await computeUpNext(source, lib, idx).catch(() => [] as UpNextRow[]);
      const r: TraktRows = { watchlist: all[0]!, recommendations: all[1]!, trending: all[2]!, popular: all[3]!, lists, upNext };
      if (!live) return;
      computed.current = true;
      setOut(r);
      saveRefs(source.cid, r);
    })().catch(() => {});
    return () => { live = false; };
  }, [lib, idx, source]);
  return out;
}

/** Films vus d'après Trakt : `(titre, année) => boolean` (toujours faux si le compte n'est pas lié). */
export function useIsWatchedMovie(): (title: string, year?: number | null) => boolean {
  const watched = useTraktLibrary((s) => s.watched);
  return useMemo(() => (title, year) => isWatchedMovie(watched, title, year), [watched]);
}

/** Épisodes vus (« SxE ») d'une série du catalogue, ou null. */
export function useWatchedEpisodes(title: string | undefined, year?: number | null): Set<string> | null {
  const watched = useTraktLibrary((s) => s.watched);
  return useMemo(() => (title ? watchedEpisodes(watched, title, year) : null), [watched, title, year]);
}
