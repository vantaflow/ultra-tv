import { describe, expect, it } from "vitest";
import { levelShows, parseSettingsLevel } from "./settingsLevel";

describe("niveaux de réglages", () => {
  it("Standard par défaut (valeur absente ou inconnue)", () => {
    expect(parseSettingsLevel(undefined)).toBe("standard");
    expect(parseSettingsLevel("nope")).toBe("standard");
    expect(parseSettingsLevel("expert")).toBe("expert");
  });
  it("Essentiel masque les réglages techniques, Standard les réglages experts", () => {
    expect(levelShows("essential", "basic")).toBe(true);
    expect(levelShows("essential", "tech")).toBe(false);
    expect(levelShows("essential", "expert")).toBe(false);
    expect(levelShows("standard", "tech")).toBe(true);
    expect(levelShows("standard", "expert")).toBe(false);
    expect(levelShows("expert", "expert")).toBe(true);
  });
  it("un réglage modifié reste toujours affiché", () => {
    expect(levelShows("essential", "expert", true)).toBe(true);
    expect(levelShows("standard", "expert", true)).toBe(true);
  });
});
