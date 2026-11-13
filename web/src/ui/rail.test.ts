import { describe, expect, it } from "vitest";
import { railBadge, syncPercent } from "./Rail";

describe("syncPercent", () => {
  it("avance de phase en phase", () => {
    const p = (phase: "categories" | "live" | "movie" | "series" | "epg" | "done", ratio: number) => ({ phase, ratio, counts: { live: 0, movie: 0, series: 0 } });
    expect(syncPercent(null)).toBeNull();
    expect(syncPercent(p("categories", 0))).toBe(0);
    expect(syncPercent(p("movie", 0.5))).toBe(50);
    expect(syncPercent(p("epg", 1))).toBe(100);
    expect(syncPercent(p("done", 0))).toBe(100);
    expect(syncPercent(p("live", 7))).toBe(40);
  });
});

describe("railBadge", () => {
  const run = (n: number) => `${n} en cours`;
  it("nombre de favoris, enregistrements en cours", () => {
    expect(railBadge("/favorites", 12, 0, run)).toBe("12");
    expect(railBadge("/favorites", 0, 0, run)).toBeNull();
    expect(railBadge("/recordings", 0, 1, run)).toBe("1 en cours");
    expect(railBadge("/recordings", 4, 0, run)).toBeNull();
    expect(railBadge("/", 3, 2, run)).toBeNull();
  });
});
