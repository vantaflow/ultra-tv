import { describe, expect, it } from "vitest";
import type { TraktItem, TraktShowItem } from "@/cloud/client";
import { matchKey } from "./match";
import { addToIndex, availableItems, buildWatched, firstTokens, mayMatch, resolveItem, isWatchedMovie, watchedEpisodes, type CatIndex } from "./availability";

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

describe("pré-filtre par premier mot", () => {
  const titles = ["FR - Alien (1979) 4K", "Dune", "The Dark Knight", "Léon", "Tom & Jerry", "|FR| La Casa de Papel HD", "L'Auberge espagnole", "Amélie", "Heat", "", "Zorro 2020"];
  const wanted = [item("movie", "Alien", 1979), item("movie", "Dark Knight", 2008), item("movie", "Leon", 1994), item("show", "Tom and Jerry", 1940), item("show", "Casa de Papel", 2017), item("movie", "Auberge espagnole", 2002), item("movie", "Zorro", 2020)];
  it("même index avec et sans pré-filtre", () => {
    const full: CatIndex = new Map(); const fast: CatIndex = new Map();
    const w = firstTokens([wanted]);
    titles.forEach((t, n) => { addToIndex(full, [t], n, 2000); addToIndex(fast, [t], n, 2000, w); });
    // Le pré-filtre ne retire que des clés sans demande : les résolutions sont identiques.
    for (const it of wanted) expect(resolveItem(fast, it)).toBe(resolveItem(full, it));
    expect(availableItems(wanted, full, full).map((a) => a.ref)).toEqual(availableItems(wanted, fast, fast).map((a) => a.ref));
    expect(fast.size).toBeLessThan(full.size);
  });
  it("mayMatch garde « & » et les accents", () => {
    expect(mayMatch(["Tom & Jerry"], new Set(["and"]))).toBe(true);
    expect(mayMatch(["Léon"], new Set(["leon"]))).toBe(true);
    expect(mayMatch(["Heat"], new Set(["alien"]))).toBe(false);
  });
  it("aléatoire : toute clé voulue reste résolue", () => {
    const words = ["the", "la", "alien", "dune", "heat", "x", "2049", "uhd", "fr", "über", "ça", "go"];
    let seed = 7; const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
    const pick = () => Array.from({ length: 1 + Math.floor(rnd() * 3) }, () => words[Math.floor(rnd() * words.length)]!).join(" ");
    for (let r = 0; r < 200; r++) {
      const rows = Array.from({ length: 12 }, pick); const want = Array.from({ length: 3 }, () => item("movie", pick(), null));
      const full: CatIndex = new Map(); const fast: CatIndex = new Map(); const w = firstTokens([want]);
      rows.forEach((t, n) => { addToIndex(full, [t], n, null); addToIndex(fast, [t], n, null, w); });
      expect(availableItems(want, fast, null)).toEqual(availableItems(want, full, null));
    }
  });
});
