import { readFileSync } from "node:fs";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { ACCENT_DEFS } from "@/lib/themes";
import { usePrefs } from "@/state/prefs";
import { translate } from "@/i18n";
import { ThemePicker } from "./ThemePicker";

describe("Thèmes faciles à trouver", () => {
  it("ThemePicker : les 4 thèmes et toutes les couleurs d'accent", () => {
    const html = renderToStaticMarkup(createElement(ThemePicker));
    const lang = usePrefs.getState().lang;
    for (const k of ["set.themeAuto", "set.themeDark", "set.themeLight", "set.themeOled"] as const) expect(html).toContain(translate(lang, k));
    expect(html.match(/class="swatch"/g)?.length).toBe(ACCENT_DEFS.length);
  });
  it("Réglages › Affichage : thème en tête de la rubrique ; assistant de première source : thème proposé", () => {
    const set = readFileSync(new URL("../screens/Settings.tsx", import.meta.url), "utf8");
    const pane = set.slice(set.indexOf("function DisplayPane"));
    expect(pane.indexOf("<ThemePicker />")).toBeGreaterThan(0);
    expect(pane.indexOf("<ThemePicker />")).toBeLessThan(pane.indexOf("<Pref"));
    const onb = readFileSync(new URL("../screens/Onboarding.tsx", import.meta.url), "utf8");
    expect(onb).toContain("<ThemePicker />");
  });
});
