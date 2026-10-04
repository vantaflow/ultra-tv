import { describe, expect, it } from "vitest";
import { ar } from "./ar";
import { en } from "./en";
import { es } from "./es";
import { fr } from "./fr";
import { translate } from "./index";

describe("i18n", () => {
  it("toutes les langues ont exactement les clés du français", () => {
    const keys = Object.keys(fr).sort();
    for (const d of [en, es, ar]) expect(Object.keys(d).sort()).toEqual(keys);
  });
  it("les variables {x} de chaque traduction sont celles du français", () => {
    const vars = (s: string) => (s.match(/\{\w+\}/g) ?? []).sort().join();
    for (const [k, v] of Object.entries(fr)) for (const d of [en, es, ar]) expect(vars((d as Record<string, string>)[k]!), k).toBe(vars(v));
  });
  it("interpole", () => {
    expect(translate("fr", "common.resumeAt", { t: "1:12:40" })).toBe("Reprendre à 1:12:40");
    expect(translate("en", "common.season", { n: 2 })).toBe("Season 2");
  });
});

// Complétude : « toute clé du français existe ailleurs » ne suffit pas, une valeur copiée telle quelle reste non traduite.
const ARABIC = /[؀-ۿ]/;
// Noms propres, formats et valeurs techniques identiques dans toutes les langues.
const SAME_AS_EN_OK = new Set(["app.name", "src.xtream", "src.serverPh", "src.userAgent", "src.referer", "sync.percent"]);
describe("i18n : complétude arabe", () => {
  it("aucune valeur arabe sans caractère arabe, hors liste blanche", () => {
    const bad = Object.entries(ar).filter(([k, v]) => !ARABIC.test(v) && !SAME_AS_EN_OK.has(k)).map(([k]) => k);
    expect(bad).toEqual([]);
  });
  it("aucune valeur arabe identique à l'anglais ou au français, hors liste blanche", () => {
    const bad = Object.keys(fr).filter((k) => !SAME_AS_EN_OK.has(k) && (ar[k as keyof typeof ar] === en[k as keyof typeof en] || ar[k as keyof typeof ar] === fr[k as keyof typeof fr]));
    expect(bad).toEqual([]);
  });
  it("aucun texte français ou anglais d'interface ne reste en dur dans les composants", async () => {
    const files = import.meta.glob(["../screens/*.tsx", "../ui/*.tsx", "../player/*.tsx", "../state/sync.ts"], { query: "?raw", import: "default", eager: true }) as Record<string, string>;
    const hard = /(aria-label|title|placeholder|alt)="(?!https?:)[A-Za-zÀ-ÿ]{3}|toast\("|<span className="kbd">[A-Za-zÀ-ÿ]{2,}</;
    const found = Object.entries(files).filter(([f, src]) => !f.includes(".test.") && hard.test(src)).map(([f]) => f);
    expect(found).toEqual([]);
  });
});
