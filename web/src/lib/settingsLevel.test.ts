import { describe, expect, it } from "vitest";
import { initialSettingsLevel, levelShows, parseSettingsLevel } from "./settingsLevel";

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
  it("niveau au démarrage : existant sans niveau → Expert ; installation neuve → Standard ; choix explicite conservé", () => {
    expect(initialSettingsLevel(undefined, true)).toBe("expert");
    expect(initialSettingsLevel(undefined, false)).toBe("standard");
    expect(initialSettingsLevel("essential", true)).toBe("essential");
    expect(initialSettingsLevel("standard", true)).toBe("standard");
    expect(initialSettingsLevel("expert", false)).toBe("expert");
    expect(initialSettingsLevel("n'importe quoi", true)).toBe("expert");
  });
  it("un réglage modifié reste toujours affiché", () => {
    expect(levelShows("essential", "expert", true)).toBe(true);
    expect(levelShows("standard", "expert", true)).toBe(true);
  });
});
