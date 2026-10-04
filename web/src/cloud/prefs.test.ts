import { describe, expect, it, vi } from "vitest";
import { OTHER_LANG } from "@/lib/categoryLang";
import { createBatcher, fromPrefs, langFromWire, langToWire, sameLangs, shouldApply, toPrefs } from "./prefs";

const c = (kind: "live" | "movie" | "series", extId: string, enabled: 0 | 1, id = 0) => ({ id, kind, extId, enabled });

describe("conversion local -> prefs", () => {
  it("langues en minuscules, OTHER -> other, UK -> en, dédoublonnées ; null = toutes", () => {
    expect(toPrefs(["FR", "UK", OTHER_LANG, "FR"], [], 5).langs).toEqual(["en", "fr", "other"]);
    expect(toPrefs(null, [], 5).langs).toBeNull();
  });
  it("seules les catégories désactivées partent, par type, extId tels quels (casse conservée)", () => {
    const p = toPrefs(null, [c("live", "12", 0), c("live", "13", 1), c("movie", "Ab_C", 0), c("series", "9", 0)], 42);
    expect(p).toEqual({ langs: null, disabled: { live: ["12"], movie: ["Ab_C"], series: ["9"] }, updatedAt: 42 });
  });
  it("respecte les plafonds (128 caractères, 5000 ids)", () => {
    const many = Array.from({ length: 5100 }, (_, i) => c("live", String(i + 1), 0));
    const p = toPrefs(null, [...many, c("movie", "x".repeat(129), 0)], 1);
    expect(p.disabled.live).toHaveLength(5000);
    expect(p.disabled.movie).toEqual([]);
  });
});

describe("conversion prefs -> local", () => {
  const prefs = { langs: ["en", "other", "fr"], disabled: { live: ["1"], movie: [], series: ["7"] }, updatedAt: 10 };
  it("langues locales", () => expect(fromPrefs(prefs, []).langs).toEqual(["UK", OTHER_LANG, "FR"]));
  it("enabled = non listé comme désactivé ; ne retourne que les différences", () => {
    const plan = fromPrefs(prefs, [c("live", "1", 1, 1), c("live", "2", 0, 2), c("live", "3", 1, 3), c("series", "7", 1, 4), c("movie", "1", 1, 5)]);
    expect(plan.changes).toEqual([{ id: 1, enabled: 0 }, { id: 2, enabled: 1 }, { id: 4, enabled: 0 }]);
  });
  it("aller-retour stable", () => {
    const cats = [c("live", "1", 0, 1), c("live", "2", 1, 2), c("movie", "A", 0, 3)];
    expect(fromPrefs({ ...toPrefs(["FR", OTHER_LANG], cats, 1) }, cats).changes).toEqual([]);
    expect(langFromWire(langToWire("UK"))).toBe("UK");
    expect(sameLangs(["FR", "UK"], ["UK", "FR"])).toBe(true);
  });
});

describe("le plus récent gagne", () => {
  const p = { langs: null, disabled: { live: [], movie: [], series: [] }, updatedAt: 100 };
  it("strictement plus récent seulement", () => {
    expect(shouldApply(p, 99)).toBe(true);
    expect(shouldApply(p, 100)).toBe(false);
    expect(shouldApply(p, 200)).toBe(false);
    expect(shouldApply(null, 0)).toBe(false);
  });
});

describe("anti-rebond", () => {
  it("regroupe les changements en un seul envoi après le calme", () => {
    vi.useFakeTimers();
    const fn = vi.fn();
    const b = createBatcher<number>(1500, fn);
    b.note(1); vi.advanceTimersByTime(1000); b.note(1); b.note(2); vi.advanceTimersByTime(1000);
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(600);
    expect(fn).toHaveBeenCalledTimes(1);
    expect(fn).toHaveBeenCalledWith([1, 2]);
    vi.useRealTimers();
  });
});
