import { describe, expect, it } from "vitest";
import type { TraktItem, TraktShowItem } from "@/cloud/client";
import { matchKey } from "./match";
import { addToIndex, availableItems, buildWatched, isWatchedMovie, watchedEpisodes, type CatIndex } from "./availability";

const item = (type: "movie" | "show", title: string, year: number | null, extra: string[] = []): TraktItem =>
  ({ type, tmdb: null, year, title, keys: [...new Set([title, ...extra].map(matchKey))] });
const idx = (rows: [string, number, number | null][]): CatIndex => { const i: CatIndex = new Map(); for (const [t, ref, y] of rows) addToIndex(i, [t], ref, y); return i; };

describe("availableItems", () => {
  const movies = idx([["FR - Alien (1979) 4K", 1, 1979], ["Dune", 2, 2021], ["Dune", 3, 1984], ["Heat", 4, null]]);
  const series = idx([["|FR| La Casa de Papel HD", 10, 2017]]);

  it("exclut ce qui n'est pas dans la playlist", () => {
    const r = availableItems([item("movie", "Alien", 1979), item("movie", "Blade Runner", 1982)], movies, series);
    expect(r.map((a) => a.ref)).toEqual([1]);
  });
  it("garde l'ordre Trakt, films et séries mêlés", () => {
    const r = availableItems([item("show", "Money Heist", 2017, ["La casa de papel"]), item("movie", "Heat", 1995), item("movie", "Alien", 1979)], movies, series);
    expect(r.map((a) => `${a.kind}${a.ref}`)).toEqual(["show10", "movie4", "movie1"]);
  });
  it("tolère ±1 an et refuse au-delà", () => {
    expect(availableItems([item("movie", "Alien", 1980)], movies, null).length).toBe(1);
    expect(availableItems([item("movie", "Alien", 1985)], movies, null).length).toBe(0);
  });
  it("préfère l'année exacte", () => {
    expect(availableItems([item("movie", "Dune", 1984)], movies, null)[0]!.ref).toBe(3);
    expect(availableItems([item("movie", "Dune", 2021)], movies, null)[0]!.ref).toBe(2);
  });
  it("une ligne du catalogue au plus par élément, et par ligne", () => {
    const r = availableItems([item("movie", "Dune", 2021), item("movie", "Dune", 2021), item("movie", "Heat", 1995, ["Dune"])], movies, null);
    expect(r.map((a) => a.ref)).toEqual([2, 4]);
  });
  it("un film ne se rapproche pas d'une série du même titre", () => {
    expect(availableItems([item("movie", "Money Heist", 2017, ["La casa de papel"])], movies, series)).toEqual([]);
  });
  it("index absent = rien", () => {
    expect(availableItems([item("movie", "Alien", 1979)], null, null)).toEqual([]);
  });
});

describe("déjà vu", () => {
  const show: TraktShowItem = { ...item("show", "Breaking Bad", 2008, ["Breaking Bad"]), episodes: ["1x1", "1x2", "2x1"] };
  const w = buildWatched({ movies: [item("movie", "Alien", 1979)], shows: [show] });
  it("film vu, avec tolérance d'année", () => {
    expect(isWatchedMovie(w, "FR - Alien 4K", 1979)).toBe(true);
    expect(isWatchedMovie(w, "Alien", null)).toBe(true);
    expect(isWatchedMovie(w, "Alien", 2024)).toBe(false);
    expect(isWatchedMovie(w, "Aliens", 1986)).toBe(false);
  });
  it("épisodes vus d'une série", () => {
    const s = watchedEpisodes(w, "|FR| Breaking Bad HD", 2008);
    expect(s?.has("1x2")).toBe(true);
    expect(s?.has("1x3")).toBe(false);
    expect(watchedEpisodes(w, "Autre série", 2008)).toBeNull();
  });
  it("sans bibliothèque : rien de vu", () => {
    expect(isWatchedMovie(buildWatched(null), "Alien", 1979)).toBe(false);
  });
});
