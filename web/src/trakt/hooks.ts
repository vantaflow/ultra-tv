import { useEffect, useMemo, useState } from "react";
import { useCloud } from "@/cloud/service";
import { db } from "@/db/db";
import type { MovieRow, SeriesRow, Source } from "@/db/types";
import { usePrefs } from "@/state/prefs";
import { availableItems, isWatchedMovie, watchedEpisodes, type Available } from "./availability";
import { useCatalogIndex } from "./catalog";
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

/** Watchlist et recommandations Trakt, réduites à ce qui est disponible dans la source active. Vides tant que rien n'est prêt. */
export function useTraktRows(source: Source | undefined): { watchlist: TraktRow[]; recommendations: TraktRow[] } {
  const lib = useTraktLibrary((s) => s.lib);
  const idx = useCatalogIndex(source);
  const [out, setOut] = useState<{ watchlist: TraktRow[]; recommendations: TraktRow[] }>({ watchlist: [], recommendations: [] });
  useEffect(() => {
    let live = true;
    if (!lib || !idx || !source?.cid) { setOut({ watchlist: [], recommendations: [] }); return; }
    void (async () => {
      const [watchlist, recommendations] = await Promise.all([
        rowsOf(source, availableItems(lib.watchlist, idx.movies, idx.series)),
        rowsOf(source, availableItems(lib.recommendations, idx.movies, idx.series)),
      ]);
      if (live) setOut({ watchlist, recommendations });
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
