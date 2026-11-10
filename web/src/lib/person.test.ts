import { describe, expect, it } from "vitest";
import { creditMatches, personCredits, personSearchBest } from "./person";

describe("pages acteurs", () => {
  it("préfère le nom identique", () => {
    expect(personSearchBest({ results: [{ id: 1, name: "Cillian Murphy Jr" }, { id: 2037, name: "Cillian Murphy" }] }, "cillian murphy")).toBe(2037);
  });
  it("filmographie fusionnée, la plus récente d'abord", () => {
    const c = personCredits({
      cast: [{ media_type: "movie", title: "Inception", release_date: "2010-07-15" }, { media_type: "tv", name: "Peaky Blinders", first_air_date: "2013-09-12" }, { media_type: "movie", title: "Oppenheimer", release_date: "2023-07-19" }],
      crew: [{ media_type: "movie", title: "Oppenheimer", release_date: "2023-07-19" }],
    });
    expect(c.map((x) => x.title)).toEqual(["Oppenheimer", "Peaky Blinders", "Inception"]);
  });
  it("rapprochement sans accents, ±1 an", () => {
    const c = { tv: false, title: "Le Fabuleux Destin d'Amélie Poulain", original: null, year: 2001 };
    expect(creditMatches(c, "Le fabuleux destin d amelie poulain", 2002)).toBe(true);
    expect(creditMatches(c, "Le Fabuleux Destin d'Amélie Poulain", 2010)).toBe(false);
  });
});
