import { describe, expect, it } from "vitest";
import { UI_SCALES, parseUiScale, uiScaleLabel } from "./uiScale";

describe("taille de l'interface", () => {
  it("de 90 % à 150 %, 100 % par défaut", () => {
    expect(UI_SCALES[0]).toBe(0.9);
    expect(UI_SCALES[UI_SCALES.length - 1]).toBe(1.5);
    expect(parseUiScale(undefined)).toBe(1);
    expect(parseUiScale(1.25)).toBe(1.25);
    expect(parseUiScale("1.1")).toBe(1.1);
    expect(parseUiScale(3)).toBe(1);
    expect(parseUiScale("abc")).toBe(1);
  });
  it("libellé en pourcentage", () => {
    expect(uiScaleLabel(1.25, "en-GB")).toBe("125%");
    expect(uiScaleLabel(0.9, "fr-FR").replace(/\s/g, " ")).toBe("90 %");
  });
});
