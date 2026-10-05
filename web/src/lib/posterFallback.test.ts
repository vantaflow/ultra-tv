import { describe, it, expect } from "vitest";
import { cleanTitle, dedupeByTitle, dedupeKey, fallbackPoster, setPosterFinder } from "./posterFallback";

describe("cleanTitle", () => {
  it("retire préfixe de langue, qualité et année", () => {
    expect(cleanTitle("FR - Malicious (2018)")).toEqual({ query: "Malicious", year: 2018 });
    expect(cleanTitle("|FR| The Pirates of Somalia 4K")).toEqual({ query: "The Pirates of Somalia", year: null });
    expect(cleanTitle("[EN] Malignant [2021] MULTI")).toEqual({ query: "Malignant", year: 2021 });
    expect(cleanTitle("Malibu Rescue: The Next Wave")).toEqual({ query: "Malibu Rescue: The Next Wave", year: null });
  });
});

describe("dedupeByTitle", () => {
  it("une entrée par œuvre, de préférence celle qui a une affiche", () => {
    const items = [
      { id: 1, title: "Malicious", year: null, poster: null },
      { id: 2, title: "FR - Malicious", year: null, poster: "http://img.test/m.jpg" },
      { id: 3, title: "Malignant", year: 2021, poster: null },
    ];
    expect(dedupeByTitle(items).map((x) => x.id)).toEqual([2, 3]);
  });
  it("années différentes = œuvres différentes", () => {
    expect(dedupeKey("Dune", 1984)).not.toBe(dedupeKey("Dune", 2021));
  });
});

describe("fallbackPoster", () => {
  it("cherche une fois, met en cache, repli sans année", async () => {
    const calls: string[] = [];
    setPosterFinder(async (kind, q, y) => { calls.push(`${kind}|${q}|${y}`); return y ? null : "/abc.jpg"; });
    const u1 = await fallbackPoster("Zzz Test Film (2019)", "movie");
    const u2 = await fallbackPoster("Zzz Test Film (2019)", "movie");
    expect(u1).toBe("https://image.tmdb.org/t/p/w342/abc.jpg");
    expect(u2).toBe(u1);
    expect(calls).toEqual(["movie|Zzz Test Film|2019", "movie|Zzz Test Film|null"]);
    setPosterFinder(null);
    expect(await fallbackPoster("Autre film", "movie")).toBeNull();
  });
});

describe("fallbackPoster — sous-titre", () => {
  it("réessaie sur le titre principal quand le titre complet ne donne rien", async () => {
    const calls: string[] = [];
    setPosterFinder(async (_k, q) => { calls.push(q); return q === "Zzz Saga" ? "/s.jpg" : null; });
    const u = await fallbackPoster("Zzz Saga : La lignée immortelle (2025)", "movie");
    expect(u).toBe("https://image.tmdb.org/t/p/w342/s.jpg");
    expect(calls).toContain("Zzz Saga");
    setPosterFinder(null);
  });
});
