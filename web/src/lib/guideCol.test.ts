import { describe, expect, it } from "vitest";
import { chanLabel, guideColPx, guideColRem, parseGuideCol } from "./guideCol";

describe("colonne des noms du guide", () => {
  it("défaut : normale", () => { expect(parseGuideCol(undefined)).toBe("normal"); expect(parseGuideCol("énorme")).toBe("normal"); });
  it("largeurs croissantes", () => { expect(guideColRem("narrow")).toBeLessThan(guideColRem("normal")); expect(guideColRem("normal")).toBeLessThan(guideColRem("wide")); });
  it("la défaut est plus large que l'ancienne colonne (224 px)", () => { expect(guideColPx("normal")).toBeGreaterThan(224); });
  it("étroite = ancienne colonne", () => { expect(guideColPx("narrow")).toBe(224); });
  it("pixels = rem × 16", () => { expect(guideColPx("wide")).toBe(guideColRem("wide") * 16); });
});

describe("libellé sans logo", () => {
  it("numéro et nom", () => { expect(chanLabel(7, "TF1")).toBe("7 · TF1"); });
  it("sans numéro : nom seul", () => { expect(chanLabel(0, "TF1")).toBe("TF1"); expect(chanLabel(undefined, "TF1")).toBe("TF1"); });
});
