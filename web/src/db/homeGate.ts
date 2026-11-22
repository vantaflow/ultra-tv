// Rangées de l'accueil : écarter les titres des catégories exclues de l'accueil (voir lib/contentGate, contexte « home »).
import { db } from "./db";
import type { HistoryRow } from "./types";
import type { TraktRow, UpNextRow } from "@/trakt/hooks";

/**
 * Reprises de l'accueil : sans les films et séries d'une catégorie écartée sur l'accueil ([movies] / [series] : catégories écartées).
 * Un titre absent du catalogue local (catégorie non téléchargée) reste affiché, comme avant.
 */
export async function homeAllowedHistory(cid: number, list: HistoryRow[], movies: ReadonlySet<string>, series: ReadonlySet<string>): Promise<HistoryRow[]> {
  if (!movies.size && !series.size) return list;
  const cats = await Promise.all(list.map(async (h) => {
    if (h.kind === "movie") return (await db.movies.where("[sourceId+streamId]").equals([cid, h.refId]).first())?.catExt;
    if (h.kind === "series") return (await db.series.where("[sourceId+seriesId]").equals([cid, h.seriesId ?? h.refId]).first())?.catExt;
    return undefined;
  }));
  return list.filter((h, i) => {
    const set = h.kind === "movie" ? movies : h.kind === "series" ? series : null;
    if (!set || !set.size) return true;
    const c = cats[i];
    return c === undefined || !set.has(c);
  });
}

type TraktLike = { watchlist: TraktRow[]; recommendations: TraktRow[]; trending: TraktRow[]; popular: TraktRow[]; recent: TraktRow[]; upNext: UpNextRow[]; lists: { id: string; name: string; rows: TraktRow[] }[] };
/** Rangées Trakt / Simkl de l'accueil : sans les titres d'une catégorie écartée. */
export function homeAllowedTrakt<T extends TraktLike>(r: T, movies: ReadonlySet<string>, series: ReadonlySet<string>): T {
  if (!movies.size && !series.size) return r;
  const ok = (x: TraktRow) => !(x.kind === "movie" ? movies : series).has(x.row.catExt);
  return {
    ...r,
    watchlist: r.watchlist.filter(ok), recommendations: r.recommendations.filter(ok), trending: r.trending.filter(ok), popular: r.popular.filter(ok), recent: r.recent.filter(ok),
    upNext: r.upNext.filter((u) => !series.has(u.row.catExt)),
    lists: r.lists.map((l) => ({ ...l, rows: l.rows.filter(ok) })),
  };
}
