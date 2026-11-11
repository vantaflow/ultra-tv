import { describe, expect, it } from "vitest";
import { filterActive, matchesFilter, NO_FILTER } from "./vodFilter";

describe("filtres Films / Séries", () => {
  it("sans filtre : tout passe", () => {
    expect(filterActive(NO_FILTER)).toBe(false);
    expect(matchesFilter({ rating: 0, year: null }, NO_FILTER)).toBe(true);
  });
  it("note minimale et période", () => {
    const f = { minRating: 7, period: "2010s" as const };
    expect(matchesFilter({ rating: 7.4, year: 2014 }, f)).toBe(true);
    expect(matchesFilter({ rating: 6.9, year: 2014 }, f)).toBe(false);
    expect(matchesFilter({ rating: 8, year: 2021 }, f)).toBe(false);
    expect(matchesFilter({ rating: 8, year: null }, f)).toBe(false);
    expect(matchesFilter({ rating: 5, year: 1985 }, { minRating: 0, period: "older" })).toBe(true);
  });
});
