import { describe, expect, it } from "vitest";
import { isShown, pHiddenToWire, planPHidden, planVisibility, type HiddenMap } from "./profileCategories";

const hm = (o: Record<string, string[]>): HiddenMap => new Map(Object.entries(o).map(([k, v]) => [k, new Set(v)]));

describe("visibilité", () => {
  it("affichée = téléchargée ET non masquée par le profil", () => {
    expect(isShown({ enabled: 1, extId: "a" }, new Set())).toBe(true);
    expect(isShown({ enabled: 1, extId: "a" }, new Set(["a"]))).toBe(false);
    expect(isShown({ enabled: 0, extId: "a" }, new Set())).toBe(false);
  });
});

describe("planVisibility", () => {
  const P = ["me", "b", "c"];
  it("masquer pour moi seul : la catégorie reste téléchargée tant qu'un autre profil l'affiche", () => {
    const p = planVisibility("me", ["1"], false, new Set(["1"]), hm({}), P);
    expect(p.hide.get("me")).toEqual(["1"]);
    expect(p.disable).toEqual([]);
  });
  it("masquer quand tous les autres la masquent déjà : plus téléchargée (union vide)", () => {
    const p = planVisibility("me", ["1", "2"], false, new Set(["1", "2"]), hm({ b: ["1"], c: ["1", "2"] }), P);
    expect(p.disable).toEqual(["1"]);
  });
  it("profil unique : masquer = ne plus télécharger", () => {
    expect(planVisibility("me", ["1"], false, new Set(["1"]), hm({}), ["me"]).disable).toEqual(["1"]);
  });
  it("afficher une catégorie téléchargée : seulement pour moi", () => {
    const p = planVisibility("me", ["1"], true, new Set(["1"]), hm({ me: ["1"] }), P);
    expect(p.unhide.get("me")).toEqual(["1"]);
    expect(p.enable).toEqual([]);
    expect(p.hide.size).toBe(0);
  });
  it("réactiver une catégorie non téléchargée : téléchargée, et masquée pour tous les AUTRES profils", () => {
    const p = planVisibility("me", ["1"], true, new Set(), hm({ me: ["1"], b: ["1"] }), P);
    expect(p.enable).toEqual(["1"]);
    expect(p.unhide.get("me")).toEqual(["1"]);
    expect(p.hide.get("b")).toEqual(["1"]);
    expect(p.hide.get("c")).toEqual(["1"]);
    expect(p.hide.has("me")).toBe(false);
  });
});

describe("pHidden : envoi", () => {
  it("une entrée par profil et par type ayant des masquées, vides ignorées", () => {
    const w = pHiddenToWire([
      { profile: "Principal", kind: "live", ids: ["1", "2", "1"] },
      { profile: "Principal", kind: "movie", ids: [] },
      { profile: "Kids", kind: "series", ids: ["9"] },
    ]);
    expect(w).toEqual([{ p: "Principal", k: "live", ids: ["1", "2"] }, { p: "Kids", k: "series", ids: ["9"] }]);
  });
  it("entrées étrangères renvoyées telles quelles, sauf si le profil a une liste locale pour la même clé", () => {
    const keep = [{ p: "Inconnu", k: "live" as const, ids: ["5"] }, { p: "kids", k: "series" as const, ids: ["7"] }];
    const w = pHiddenToWire([{ profile: "Kids", kind: "series", ids: ["9"] }], keep);
    expect(w).toEqual([{ p: "Kids", k: "series", ids: ["9"] }, { p: "Inconnu", k: "live", ids: ["5"] }]);
  });
});

describe("pHidden : réception", () => {
  const profiles = [{ id: "main", name: "Principal" }, { id: "k", name: "Kids" }];
  it("rattache par nom sans casse ; un profil sans entrée n'a plus rien de masqué ; inconnus mis de côté", () => {
    const r = planPHidden([{ p: "kids", k: "movie", ids: ["3", "4"] }, { p: "Autre", k: "live", ids: ["8"] }], profiles);
    expect(r.byProfile.get("k")).toEqual({ live: [], movie: ["3", "4"], series: [] });
    expect(r.byProfile.get("main")).toEqual({ live: [], movie: [], series: [] });
    expect(r.foreign).toEqual([{ p: "Autre", k: "live", ids: ["8"] }]);
  });
});
