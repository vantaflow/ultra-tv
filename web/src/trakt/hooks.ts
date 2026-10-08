import { useEffect, useMemo, useRef, useState } from "react";
import { useCloud } from "@/cloud/service";
import { db } from "@/db/db";
import type { MovieRow, SeriesRow, Source } from "@/db/types";
import { usePrefs } from "@/state/prefs";
import { availableItems, isWatchedMovie, watchedEpisodes, type Available } from "./availability";
import { buildCatalogIndex, useCatalogIndex, wantedTokens } from "./catalog";
import { refreshTraktLibrary, TRAKT_REFRESH_MS, useTraktLibrary } from "./library";

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

export interface TraktRows { watchlist: TraktRow[]; recommendations: TraktRow[]; trending: TraktRow[]; popular: TraktRow[] }
const NONE: TraktRows = { watchlist: [], recommendations: [], trending: [], popular: [] };
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
  const o = {} as TraktRows;
  const all = await Promise.all(LISTS.map((k) => rowsOf(source, refs[k].map(([kind, ref]) => ({ kind, ref, item: null as never })))));
  LISTS.forEach((k, n) => { o[k] = all[n]!; });
  return o;
};

/** Premiers mots des clés de la bibliothèque (pré-filtre d'indexation), stable tant que la bibliothèque ne change pas. */
export function useWantedTokens(): Set<string> | null {
  const lib = useTraktLibrary((s) => s.lib);
  return useMemo(() => (lib ? wantedTokens([lib.watchlist, lib.recommendations, lib.trending, lib.popular]) : null), [lib]);
}

/** À monter une fois (App) : construit l'index du catalogue de la source active dès le démarrage, sans attendre l'accueil. */
export function useTraktPrewarm(source: Source | undefined): void {
  const wanted = useWantedTokens();
  const cid = source?.cid ?? 0;
  useEffect(() => { if (cid && wanted) void buildCatalogIndex(cid, wanted).catch(() => {}); }, [cid, wanted]);
}

/** Rangées Trakt (watchlist, recommandations, tendances, populaires), réduites à ce qui est disponible dans la source active. */
export function useTraktRows(source: Source | undefined): TraktRows {
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
      const r = { watchlist: all[0]!, recommendations: all[1]!, trending: all[2]!, popular: all[3]! };
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
