import { describe, expect, it, vi } from "vitest";
import { OTHER_LANG } from "@/lib/categoryLang";
import { chOrderToWire, createBatcher, fromPrefs, hiddenFromWire, hiddenToWire, langFromWire, langToWire, sameLangs, planChOrder, shouldApply, toPrefs } from "./prefs";

const c = (kind: "live" | "movie" | "series", extId: string, enabled: 0 | 1, id = 0, ord = 0) => ({ id, kind, extId, enabled, ord });

describe("conversion local -> prefs", () => {
  it("langues en minuscules, OTHER -> other, UK -> en, dédoublonnées ; null = toutes", () => {
    expect(toPrefs(["FR", "UK", OTHER_LANG, "FR"], [], 5).langs).toEqual(["en", "fr", "other"]);
    expect(toPrefs(null, [], 5).langs).toBeNull();
  });
  it("seules les catégories désactivées partent, par type, extId tels quels (casse conservée)", () => {
    const p = toPrefs(null, [c("live", "12", 0), c("live", "13", 1), c("movie", "Ab_C", 0), c("series", "9", 0)], 42);
    expect(p).toEqual({ langs: null, disabled: { live: ["12"], movie: ["Ab_C"], series: ["9"] }, order: { live: ["12", "13"], movie: ["Ab_C"], series: ["9"] }, updatedAt: 42 });
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

describe("ordre des catégories", () => {
  it("envoi : par type, trié par ord (pas par ordre d'arrivée)", () => {
    const p = toPrefs(null, [c("live", "a", 1, 1, 2), c("live", "b", 1, 2, 0), c("live", "c", 1, 3, 1), c("movie", "m", 1, 4, 0)], 1);
    expect(p.order).toEqual({ live: ["b", "c", "a"], movie: ["m"], series: [] });
  });
  it("réception : connues dans l'ordre reçu, inconnues à la suite en gardant leur place relative", () => {
    const cats = [c("live", "a", 1, 1, 0), c("live", "x", 1, 2, 1), c("live", "b", 1, 3, 2), c("live", "y", 1, 4, 3), c("movie", "m", 1, 5, 0)];
    const plan = fromPrefs({ langs: null, disabled: { live: [], movie: [], series: [] }, order: { live: ["b", "z", "a"], movie: [], series: [] }, updatedAt: 1 }, cats);
    // b, a (reçus), puis x, y (inconnus de l'envoyeur) ; « z » absent ici est ignoré ; type movie (liste vide) intact.
    expect(plan.ords).toEqual([{ id: 3, ord: 0 }, { id: 1, ord: 1 }, { id: 2, ord: 2 }]);
    const out = cats.map((x) => ({ ...x, ord: plan.ords.find((o) => o.id === x.id)?.ord ?? x.ord })).filter((x) => x.kind === "live").sort((p, q) => p.ord - q.ord).map((x) => x.extId);
    expect(out).toEqual(["b", "a", "x", "y"]);
  });
  it("champ absent (ancien appareil) : aucune modification d'ordre ; aller-retour stable", () => {
    const cats = [c("live", "a", 1, 1, 0), c("live", "b", 1, 2, 1)];
    expect(fromPrefs({ langs: null, disabled: { live: [], movie: [], series: [] }, updatedAt: 1 }, cats).ords).toEqual([]);
    expect(fromPrefs(toPrefs(null, cats, 1) as never, cats).ords).toEqual([]);
  });
});

describe("chaînes masquées", () => {
  it("envoi : identifiant en chaîne, catégorie ou null, nom tronqué, plafond 3000", () => {
    const w = hiddenToWire([{ streamId: 7, catExt: "5", name: "TF1" }, { streamId: 8, catExt: null, name: "x".repeat(300) }]);
    expect(w[0]).toEqual({ i: "7", c: "5", n: "TF1" });
    expect(w[1]!.c).toBeNull();
    expect(w[1]!.n).toHaveLength(120);
    expect(hiddenToWire(Array.from({ length: 3100 }, (_, i) => ({ streamId: i, catExt: null, name: "" })))).toHaveLength(3000);
  });
  it("réception : identifiants non numériques et doublons ignorés", () => {
    expect(hiddenFromWire([{ i: "7", c: "5", n: "A" }, { i: "abc", c: null, n: "B" }, { i: "7", c: "5", n: "A" }, { i: "9", c: null, n: "C" }]))
      .toEqual([{ streamId: 7, catExt: "5", name: "A" }, { streamId: 9, catExt: null, name: "C" }]);
  });
  it("toPrefs ne les envoie que si fournis (sinon le Worker garde ceux du compte)", () => {
    expect("hidden" in toPrefs(null, [], 1)).toBe(false);
    expect(toPrefs(null, [], 1, { hidden: [] }).hidden).toEqual([]);
  });
});

describe("ordre personnel des chaînes", () => {
  it("envoi : nom de profil ≤ 24, listes vides ignorées, ids en chaînes, doublon : le dernier gagne", () => {
    const w = chOrderToWire([
      { profile: "Principal", scope: "__fav__", ids: [3, 1] }, { profile: "Principal", scope: "12", ids: [] },
      { profile: "n".repeat(40), scope: "12", ids: [5] }, { profile: "principal", scope: "__fav__", ids: [9] },
    ]);
    expect(w).toEqual([{ p: "principal", s: "__fav__", k: ["9"] }, { p: "n".repeat(24), s: "12", k: ["5"] }]);
  });
  it("réception : profils connus par nom sans casse, inconnus ignorés, listes locales absentes effacées", () => {
    const profiles = [{ id: "main", name: "Principal" }, { id: "p2", name: "Enfants" }];
    const plan = planChOrder(
      [{ p: "PRINCIPAL", s: "__fav__", k: ["3", "1"] }, { p: "Principal", s: "9", k: ["1", "x"] }, { p: "Invité", s: "7", k: ["1"] }, { p: "enfants", s: "7", k: [] }],
      profiles,
      [{ profile: "main", scope: "__fav__" }, { profile: "main", scope: "7" }, { profile: "p2", scope: "7" }],
    );
    expect(plan.put).toEqual([{ profile: "main", scope: "__fav__", ids: [3, 1] }]);
    expect(plan.remove).toEqual([{ profile: "main", scope: "7" }, { profile: "p2", scope: "7" }]);
    // Profil inconnu ici ou identifiants non numériques : gardés tels quels pour être renvoyés.
    expect(plan.foreign).toEqual([{ p: "Principal", s: "9", k: ["1", "x"] }, { p: "Invité", s: "7", k: ["1"] }]);
  });
});
