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
