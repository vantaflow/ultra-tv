import { describe, expect, it } from "vitest";
import { mergeCategoryOrder, moveIndex, sortAZ } from "./categoryOrder";

describe("ordre des catégories", () => {
  it("fusion : reçu d'abord, inconnus à la suite, identifiants inconnus ici ignorés, liste vide = inchangé", () => {
    expect(mergeCategoryOrder(["a", "b", "c", "d"], ["c", "zz", "a", "c"])).toEqual(["c", "a", "b", "d"]);
    expect(mergeCategoryOrder(["a", "b"], [])).toEqual(["a", "b"]);
    expect(mergeCategoryOrder(["a", "b"], undefined)).toEqual(["a", "b"]);
  });
  it("déplacement borné", () => {
    expect(moveIndex(["a", "b", "c"], 2, -1)).toEqual(["a", "c", "b"]);
    const l = ["a", "b"];
    expect(moveIndex(l, 0, -1)).toBe(l);
    expect(moveIndex(l, 5, 1)).toBe(l);
  });
  it("tri A-Z sans tenir compte de la casse, des accents ni du rang numérique", () => {
    expect(sortAZ(["b", "Écran", "a10", "a2", "A1"], (x) => x)).toEqual(["A1", "a2", "a10", "b", "Écran"]);
  });
});
