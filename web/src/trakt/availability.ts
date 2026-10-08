// Logique pure (sans React ni base) du rapprochement Trakt ↔ catalogue : testable telle quelle.
// Règle stricte : un élément de watchlist / recommandation n'est montré que s'il existe dans la playlist.

import type { TraktItem, TraktShowItem } from "@/cloud/client";
import { matchKey, yearsClose } from "./match";

export interface CatRef { ref: number; year: number | null }
/** matchKey du titre → lignes du catalogue (ordre d'insertion conservé). */
export type CatIndex = Map<string, CatRef[]>;

export function addToIndex(idx: CatIndex, titles: (string | null | undefined)[], ref: number, year: number | null): void {
  const seen = new Set<string>();
  for (const t of titles) {
    const k = matchKey(t);
    if (!k || seen.has(k)) continue;
    seen.add(k);
    const l = idx.get(k);
    if (l) l.push({ ref, year }); else idx.set(k, [{ ref, year }]);
  }
}

/** Meilleure ligne du catalogue pour un élément Trakt : année exacte d'abord, puis la plus proche, puis la première. */
export function resolveItem(idx: CatIndex, item: TraktItem): number | null {
  let best: CatRef | null = null;
  let bestD = Infinity;
  for (const k of item.keys) {
    for (const c of idx.get(k) ?? []) {
      if (!yearsClose(c.year, item.year)) continue;
      const d = c.year && item.year ? Math.abs(c.year - item.year) : 0.5;
      if (d < bestD) { best = c; bestD = d; }
    }
  }
  return best ? best.ref : null;
}

export interface Available { kind: "movie" | "show"; ref: number; item: TraktItem }

/** Éléments disponibles, ordre Trakt conservé, une ligne de catalogue au plus par élément (et par ligne). */
export function availableItems(items: TraktItem[], movies: CatIndex | null, series: CatIndex | null): Available[] {
  const out: Available[] = [];
  const used = new Set<string>();
  for (const item of items) {
    const idx = item.type === "movie" ? movies : series;
    if (!idx) continue;
    const ref = resolveItem(idx, item);
    if (ref == null) continue;
    const id = `${item.type}:${ref}`;
    if (used.has(id)) continue;
    used.add(id);
    out.push({ kind: item.type, ref, item });
  }
  return out;
}

export interface WatchedIndex { movies: Map<string, TraktItem[]>; shows: Map<string, TraktShowItem[]> }

export function buildWatched(w: { movies: TraktItem[]; shows: TraktShowItem[] } | null | undefined): WatchedIndex {
  const idx: WatchedIndex = { movies: new Map(), shows: new Map() };
  const put = <T extends TraktItem>(m: Map<string, T[]>, i: T) => { for (const k of new Set(i.keys)) { const l = m.get(k); if (l) l.push(i); else m.set(k, [i]); } };
  for (const i of w?.movies ?? []) put(idx.movies, i);
  for (const i of w?.shows ?? []) put(idx.shows, i);
  return idx;
}

function find<T extends TraktItem>(m: Map<string, T[]>, title: string, year: number | null | undefined): T | null {
  const k = matchKey(title);
  if (!k) return null;
  const l = (m.get(k) ?? []).filter((i) => yearsClose(year, i.year));
  if (!l.length) return null;
  return l.find((i) => year && i.year === year) ?? l[0]!;
}

export const isWatchedMovie = (w: WatchedIndex, title: string, year?: number | null) => find(w.movies, title, year) != null;

/** Épisodes vus d'une série du catalogue (« SxE »), ou null si la série n'est pas dans l'historique Trakt. */
export function watchedEpisodes(w: WatchedIndex, title: string, year?: number | null): Set<string> | null {
  const s = find(w.shows, title, year);
  return s ? new Set(s.episodes) : null;
}
