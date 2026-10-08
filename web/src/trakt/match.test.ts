import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { matchKey, titleMatches } from "./match";

// Vecteurs PARTAGÉS avec le Worker et Android (lus par fs : le fichier est hors de la racine web/).
const path = fileURLToPath(new URL("../../../cloudflare-config/test/fixtures/trakt-match-vectors.json", import.meta.url));
const V = JSON.parse(readFileSync(path, "utf8")) as {
  keys: [string, string][];
  matches: { catalog: string; catalogYear: number | null; titles: string[]; year: number | null; match: boolean }[];
};

describe("matchKey (vecteurs partagés)", () => {
  it("charge des vecteurs", () => { expect(V.keys.length).toBeGreaterThan(10); expect(V.matches.length).toBeGreaterThan(5); });
  for (const [raw, key] of V.keys) it(`${JSON.stringify(raw)} -> ${JSON.stringify(key)}`, () => expect(matchKey(raw)).toBe(key));
});

describe("titleMatches (vecteurs partagés)", () => {
  for (const c of V.matches) it(`${c.catalog} (${c.catalogYear}) vs ${c.titles.join("/")} (${c.year}) = ${c.match}`, () => {
    expect(titleMatches(c.catalog, c.catalogYear, c.titles, c.year)).toBe(c.match);
  });
});
