// Logique pure (sans React ni base) de Trakt « complet » : appartenance à la watchlist, notes, corrections locales
// après une écriture, « À suivre » et décision d'import des reprises. Même règles que l'application Android.

import type { TraktItem, TraktLibraryResult, TraktRatedItem, TraktShowItem } from "@/cloud/client";
import { matchKey, yearsClose } from "./match";

export type Linked = Extract<TraktLibraryResult, { linked: true }>;
export type Kind = "movie" | "show";

const hits = (i: TraktItem, key: string, year: number | null | undefined) => !!key && i.keys.includes(key) && yearsClose(year, i.year);
const localItem = (type: Kind, title: string, year: number | null | undefined): TraktItem => {
  const k = matchKey(title);
  return { type, tmdb: null, year: year ?? null, title, keys: k ? [k] : [] };
};

/** Ce titre du catalogue est-il dans la watchlist Trakt ? */
export function inWatchlist(lib: Linked | null, type: Kind, title: string, year?: number | null): boolean {
  if (!lib) return false;
  const k = matchKey(title);
  return lib.watchlist.some((i) => i.type === type && hits(i, k, year));
}

/** Note (1..10) donnée sur Trakt à ce titre du catalogue, ou null. */
export function ratingFor(lib: Linked | null, type: Kind, title: string, year?: number | null): number | null {
  if (!lib) return null;
  const k = matchKey(title);
  return (type === "movie" ? lib.ratings.movies : lib.ratings.shows).find((i) => hits(i, k, year))?.rating ?? null;
}

// ── Corrections locales immédiates (après une écriture réussie, avant la relecture du Worker) ──

export function withWatchlist(lib: Linked, type: Kind, title: string, year: number | null | undefined, add: boolean): Linked {
  const k = matchKey(title);
  const rest = lib.watchlist.filter((i) => !(i.type === type && hits(i, k, year)));
  return { ...lib, watchlist: add ? [localItem(type, title, year), ...rest] : rest };
}

export function withMovieWatched(lib: Linked, title: string, year: number | null | undefined, watched: boolean): Linked {
  const k = matchKey(title);
  const rest = lib.watched.movies.filter((i) => !hits(i, k, year));
  return { ...lib, watched: { ...lib.watched, movies: watched ? [...rest, localItem("movie", title, year)] : rest } };
}

export function withEpisodes(lib: Linked, title: string, year: number | null | undefined, eps: string[], watched: boolean, now = Date.now()): Linked {
  const k = matchKey(title);
  let found = false;
  const shows: TraktShowItem[] = [];
  for (const s of lib.watched.shows) {
    if (!hits(s, k, year)) { shows.push(s); continue; }
    found = true;
    const set = new Set(s.episodes);
    for (const e of eps) { if (watched) set.add(e); else set.delete(e); }
    if (set.size) shows.push({ ...s, episodes: [...set], lastWatchedAt: watched ? now : s.lastWatchedAt });
  }
  if (!found && watched) shows.push({ ...localItem("show", title, year), episodes: [...new Set(eps)], lastWatchedAt: now });
  return { ...lib, watched: { ...lib.watched, shows } };
}

export function withRating(lib: Linked, type: Kind, title: string, year: number | null | undefined, rating: number): Linked {
  const k = matchKey(title);
  const key = type === "movie" ? "movies" : "shows";
  const rest = lib.ratings[key].filter((i) => !hits(i, k, year));
  const next: TraktRatedItem[] = rating >= 1 && rating <= 10 ? [{ ...localItem(type, title, year), rating }, ...rest] : rest;
  return { ...lib, ratings: { ...lib.ratings, [key]: next } };
}

// ── À suivre ──

export interface EpRef { season: number; episode: number }
const parseEp = (k: string): EpRef | null => { const m = /^(\d+)x(\d+)$/.exec(k); return m ? { season: Number(m[1]), episode: Number(m[2]) } : null; };
const cmp = (a: EpRef, b: EpRef) => a.season - b.season || a.episode - b.episode;

/**
 * Premier épisode disponible APRÈS le plus avancé des épisodes vus (saisons « spéciales » 0 ignorées) et non vu
 * lui-même ; null si rien n'a été vu, si la série est à jour ou si la playlist ne propose pas la suite.
 */
export function nextEpisode(watched: Iterable<string>, available: EpRef[]): EpRef | null {
  const seen = new Set(watched);
  let last: EpRef | null = null;
  for (const k of seen) { const e = parseEp(k); if (e && e.season > 0 && (!last || cmp(e, last) > 0)) last = e; }
  if (!last) return null;
  let best: EpRef | null = null;
  for (const e of available) {
    if (e.season <= 0 || cmp(e, last) <= 0 || seen.has(`${e.season}x${e.episode}`)) continue;
    if (!best || cmp(e, best) < 0) best = e;
  }
  return best;
}

export const UP_NEXT_CANDIDATES = 20;
export const UP_NEXT_MAX = 15;

/** Séries regardées, les plus récentes d'abord. */
export function upNextCandidates(lib: Linked | null, limit = UP_NEXT_CANDIDATES): TraktShowItem[] {
  if (!lib) return [];
  return lib.watched.shows.filter((s) => s.episodes.some((k) => (parseEp(k)?.season ?? 0) > 0))
    .sort((a, b) => (b.lastWatchedAt ?? 0) - (a.lastWatchedAt ?? 0)).slice(0, limit);
}

// ── Import des reprises ──

/** En deçà, une reprise n'a pas d'intérêt (secondes). */
export const MIN_RESUME_SEC = 30;

/**
 * Position à écrire dans l'historique local (secondes), ou null : position locale PLUS RÉCENTE que la pause Trakt
 * (jamais écrasée), durée inconnue, position trop courte ou quasi finie.
 */
export function resumeDecision(existing: { updatedAt: number; dur: number } | undefined, progress: number, pausedAt: number, durSec: number, now = Date.now()): { pos: number; dur: number; updatedAt: number } | null {
  if (existing && existing.updatedAt >= pausedAt) return null;
  const dur = existing && existing.dur > 0 ? existing.dur : durSec;
  if (!(dur > 0)) return null;
  const pos = (progress / 100) * dur;
  if (pos < MIN_RESUME_SEC || pos / dur > 0.95) return null;
  return { pos, dur, updatedAt: pausedAt > 0 && pausedAt <= now ? pausedAt : now };
}
