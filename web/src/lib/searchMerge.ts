// Recherche dans plusieurs sources : fusion des résultats par source (règle pure, testée à part).
import type { ChannelRow, MovieRow, ProgramRow, SeriesRow } from "@/db/types";

export interface SourceHits {
  channels: ChannelRow[];
  movies: MovieRow[];
  series: SeriesRow[];
  programs: { p: ProgramRow; c: ChannelRow }[];
  /** Films et séries trouvés seulement par le repli tolérant aux fautes. */
  fuzzy: boolean;
}

/**
 * Fusion des résultats de plusieurs sources (une seule : inchangé). Les titres proches ne comptent que si AUCUNE source
 * n'a de résultat exact ; programmes triés par heure, plafonnés à 60.
 */
export function mergeSearch(all: SourceHits[]): SourceHits {
  const exact = all.some((r) => !r.fuzzy && (r.movies.length > 0 || r.series.length > 0));
  const use = exact ? all.map((r) => (r.fuzzy ? { ...r, movies: [], series: [], fuzzy: false } : r)) : all;
  return {
    channels: use.flatMap((r) => r.channels),
    movies: use.flatMap((r) => r.movies),
    series: use.flatMap((r) => r.series),
    programs: use.flatMap((r) => r.programs).sort((a, b) => a.p.start - b.p.start).slice(0, 60),
    fuzzy: !exact && use.some((r) => r.fuzzy),
  };
}
