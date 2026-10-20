// Gestes Trakt depuis les fiches (watchlist, vu / non vu, note) : tout passe par le Worker, jamais de jeton Trakt ici.
// Après un succès, la bibliothèque en mémoire est corrigée tout de suite puis relue (le Worker a oublié son cache).

import { traktWrite, type TraktWritePath, type TraktWriteResult } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";
import { patchTraktLibrary, refreshTraktLibrary } from "./library";
import { withEpisodes, withMovieWatched, withRating, withWatchlist, type EpRef, type Kind, type Linked } from "./progress";

/** Œuvre du catalogue décrite au Worker (il l'identifie lui-même si `tmdb` manque). */
export interface TraktWork { type: Kind; title: string; year?: number | null; tmdb?: number }

/** Même borne que le Worker (MAX_HISTORY_EPISODES). */
export const MAX_EPISODES = 200;

const base = (w: TraktWork) => ({ title: w.title, ...(w.year ? { year: w.year } : {}), ...(w.tmdb ? { tmdb: w.tmdb } : {}) });

async function write(path: TraktWritePath, body: Record<string, unknown>, patch: (l: Linked) => Linked): Promise<TraktWriteResult> {
  const { paired, worker } = useCloud.getState();
  if (!paired) return "not-linked";
  const token = await getToken();
  if (!token) return "not-linked";
  const r = await traktWrite(worker, token, path, body);
  if (r === "ok") {
    patchTraktLibrary(patch);
    void refreshTraktLibrary(true);
  }
  return r;
}

export const setWatchlist = (w: TraktWork, add: boolean) =>
  write("watchlist", { ...base(w), action: add ? "add" : "remove", type: w.type }, (l) => withWatchlist(l, w.type, w.title, w.year, add));

export const setMovieWatched = (w: TraktWork, watched: boolean) =>
  write("history", { ...base(w), action: watched ? "add" : "remove", type: "movie" }, (l) => withMovieWatched(l, w.title, w.year, watched));

/** Épisodes d'une série (une saison entière ou un seul) marqués vus / non vus. */
export function setEpisodesWatched(w: TraktWork, eps: EpRef[], watched: boolean): Promise<TraktWriteResult> {
  const uniq = [...new Map(eps.map((e) => [`${e.season}x${e.episode}`, e])).values()].slice(0, MAX_EPISODES);
  if (!uniq.length) return Promise.resolve("ok");
  return write("history", { ...base(w), action: watched ? "add" : "remove", type: "episodes", episodes: uniq },
    (l) => withEpisodes(l, w.title, w.year, uniq.map((e) => `${e.season}x${e.episode}`), watched));
}

/** Note 1..10, ou 0 pour la retirer. */
export const rate = (w: TraktWork, rating: number) =>
  write("rating", { ...base(w), type: w.type, rating }, (l) => withRating(l, w.type, w.title, w.year, rating));
