import { describe, expect, it } from "vitest";
import { fuzzyPrefixes, fuzzyRank, similarity } from "./fuzzy";

const titles = ["Breaking Bad", "Bad Boys", "Better Call Saul", "La Casa de Papel", "Le Bureau des légendes", "The Office"];

describe("recherche approchée", () => {
  it("préfixes de 3 lettres", () => {
    expect(fuzzyPrefixes("Brekin bad")).toEqual(["bre", "bad"]);
    expect(fuzzyPrefixes("a b")).toEqual([]);
  });
  it("retrouve les titres malgré les fautes", () => {
    expect(fuzzyRank("brekin bad", titles, (x) => x, 3)[0]).toBe("Breaking Bad");
    expect(fuzzyRank("bureau des legendez", titles, (x) => x, 3)[0]).toBe("Le Bureau des légendes");
    expect(fuzzyRank("casa de papl", titles, (x) => x, 3)[0]).toBe("La Casa de Papel");
  });
  it("ne propose rien sans rapport", () => {
    expect(fuzzyRank("matrix", titles, (x) => x, 5)).toEqual([]);
  });
  it("transposition = une seule faute", () => expect(similarity("ofifce", "office")).toBeGreaterThanOrEqual(0.8));
});
