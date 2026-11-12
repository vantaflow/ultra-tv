import { describe, expect, it, vi } from "vitest";
import {
  ACCENT_DEFS, AA_TEXT, DEFAULT_ACCENT, DEFAULT_THEME, INK, SURFACES, THEMES, WHITE, accentHex, accentVars, contrast, ensureContrast, onColor,
  parseAccent, parseTheme, themeAttr,
} from "./themes";

describe("thèmes : identifiants communs avec Android", () => {
  it("Automatique, Sombre, Clair, Noir (OLED)", () => {
    expect([...THEMES]).toEqual(["auto", "dark", "light", "oled"]);
  });
  it("valeur inconnue ou absente : repli sur le défaut, sans erreur", () => {
    for (const v of [undefined, null, "", "neon", 3, {}, "OLED2"]) expect(parseTheme(v)).toBe(DEFAULT_THEME);
    expect(parseTheme("oled")).toBe("oled");
    expect(parseTheme("OLED")).toBe("oled");
    expect(parseTheme("AMOLED")).toBe("oled");
    for (const t of THEMES) expect(parseTheme(t)).toBe(t);
  });
  it("Automatique, Sombre et Clair se comportent comme avant ; Noir (OLED) est sombre et distinct", () => {
    expect(themeAttr("auto", true)).toBe("light");
    expect(themeAttr("auto", false)).toBe("dark");
    expect(themeAttr("dark", true)).toBe("dark");
    expect(themeAttr("light", false)).toBe("light");
    expect(themeAttr("oled", true)).toBe("oled");
    expect(themeAttr("oled", false)).toBe("oled");
  });
  it("OLED : fond #000000, surfaces très sombres", () => {
    expect(SURFACES.oled[0]).toBe("#000000");
    for (const c of SURFACES.oled) expect(contrast(c, "#000000")).toBeLessThan(1.4);
  });
});

describe("accents : douze couleurs, mêmes clés qu'Android", () => {
  const ids = ["red", "blue", "violet", "green", "orange", "pink", "ocean", "emerald", "amber", "teal", "indigo", "fuchsia"];
  it("clés et couleurs uniques", () => {
    expect(ACCENT_DEFS.map((a) => a.id)).toEqual(ids);
    expect(new Set(ACCENT_DEFS.map((a) => a.hex)).size).toBe(12);
  });
  it("valeur inconnue : repli sur le rouge, sans erreur", () => {
    for (const v of [undefined, null, "", "chartreuse", 7, {}, "#123456"]) expect(parseAccent(v)).toBe(DEFAULT_ACCENT);
    for (const id of ids) expect(parseAccent(id)).toBe(id);
    expect(parseAccent("OCEAN")).toBe("ocean");
  });
  it("anciens réglages (hexadécimal) migrés", () => {
    expect(parseAccent("#FF3B47")).toBe("red");
    expect(parseAccent("#d91e2b")).toBe("red");
    expect(parseAccent("#7C3AED")).toBe("violet");
    expect(parseAccent("#0EA5E9")).toBe("ocean");
    expect(parseAccent("#F59E0B")).toBe("amber");
  });
  it("le rouge laisse les jetons du thème décider", () => {
    for (const m of ["dark", "light", "oled"] as const) expect(accentVars("red", m)).toBeNull();
  });
  it("texte posé sur l'accent ≥ 4,5:1 (choisi automatiquement)", () => {
    for (const a of ACCENT_DEFS) {
      const on = onColor(a.hex);
      expect([WHITE, INK]).toContain(on);
      expect(contrast(on, a.hex), a.id).toBeGreaterThanOrEqual(AA_TEXT);
    }
  });
  it("accent en texte ≥ 4,5:1 sur toutes les surfaces, en sombre, OLED et clair", () => {
    for (const a of ACCENT_DEFS) for (const mode of ["dark", "oled", "light"] as const) {
      const ink = ensureContrast(accentHex(a.id), SURFACES[mode]);
      for (const bg of SURFACES[mode]) expect(contrast(ink, bg), `${a.id} ${mode} ${bg}`).toBeGreaterThanOrEqual(AA_TEXT);
      const v = accentVars(a.id, mode);
      if (a.id !== "red") expect(v?.accentText).toBe(ink);
    }
  });
  it("anneau de focus ≥ 3:1 sur les fonds de page, tous thèmes", () => {
    for (const a of ACCENT_DEFS) for (const mode of ["dark", "oled", "light"] as const) {
      for (const bg of [SURFACES[mode][0], SURFACES[mode][2]]) expect(contrast(a.hex, bg), `${a.id} ${mode} ${bg}`).toBeGreaterThanOrEqual(3);
    }
  });
});

describe("préférences enregistrées : valeurs d'un autre appareil", () => {
  const load = async (stored: object) => {
    const raw = JSON.stringify(stored);
    vi.stubGlobal("localStorage", { getItem: () => raw, setItem: () => {}, removeItem: () => {} });
    vi.resetModules();
    const m = await import("@/state/prefs");
    vi.unstubAllGlobals();
    return m.usePrefs.getState();
  };
  it("accent / thème inconnus : repli sur les défauts, sans plantage", async () => {
    const p = await load({ theme: "hologram", accent: "plaid", lang: "fr" });
    expect(p.theme).toBe("auto");
    expect(p.accent).toBe("red");
  });
  it("ancien accent hexadécimal migré, nouveaux identifiants conservés", async () => {
    expect((await load({ accent: "#7C3AED" })).accent).toBe("violet");
    const p = await load({ theme: "oled", accent: "emerald", timeFormat: "12" });
    expect([p.theme, p.accent, p.timeFormat]).toEqual(["oled", "emerald", "12"]);
  });
  it("format de l'heure inconnu : Automatique", async () => {
    expect((await load({ timeFormat: "36" })).timeFormat).toBe("auto");
  });
});
