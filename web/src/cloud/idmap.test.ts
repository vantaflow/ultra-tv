import { describe, expect, it } from "vitest";
import { identityMapper, m3uMapper, m3uWireIds, stalkerMapper } from "./idmap";
import { chOrderToWire, foreignHidden, fromPrefs, hiddenFromWire, hiddenToWire, planChOrder, toPrefs } from "./prefs";
import { packTvgIds, unpackTvgIds } from "./idmapLoad";

describe("m3uWireIds : mêmes règles que M3uParser.kt", () => {
  it("tvg-id unique gardé, sans tvg-id = m3u-N, répété = tvgId#N, compteur partagé", () => {
    const ids = m3uWireIds(["a", null, "b", "a", "", "a", "  ", "c"]);
    // seq : entrée 1 -> m3u-0 ; entrée 3 -> a#1 ; entrée 4 -> m3u-2 ; entrée 5 -> a#3 ; entrée 6 -> m3u-4
    expect(ids).toEqual(["a", "m3u-0", "b", "a#1", "m3u-2", "a#3", "m3u-4", "c"]);
  });
  it("le tvg-id n'est pas rogné quand il est valide", () => {
    expect(m3uWireIds([" x "])).toEqual([" x "]);
  });
  it("stockage compact : aller-retour", () => {
    const l = ["a", "", "b", " "];
    expect(unpackTvgIds(packTvgIds(l))).toEqual(["a", "", "b", ""]);
    expect(m3uWireIds(unpackTvgIds(packTvgIds(l)))).toEqual(m3uWireIds(l));
  });
});

describe("m3uMapper", () => {
  const m = m3uMapper(["a", null, "b", "a"]); // wire : a, m3u-0, b, a#1 ; streamId = indice + 1
  it("local -> compte et retour (direct, film, épisode)", () => {
    expect(m.out("live", 1)).toBe("a");
    expect(m.out("movie", 2)).toBe("m3u-0");
    expect(m.epOut(99, 4)).toBe("a#1");
    expect(m.inn("live", "b")).toBe(3);
    expect(m.inn("movie", "a#1")).toBe(4);
    expect(m.epIn("m3u-0")).toBe(2);
  });
  it("numéro d'entrée brut d'une ancienne version : accepté s'il n'est pas un identifiant connu", () => {
    expect(m.inn("live", "3")).toBe(3);
    expect(m.inn("live", "9")).toBeNull();
    expect(m.inn("live", "zzz")).toBeNull();
  });
  it("un tvg-id numérique prime sur le numéro brut", () => {
    const n = m3uMapper(["2", "1"]);
    expect(n.inn("live", "2")).toBe(1);
    expect(n.inn("live", "1")).toBe(2);
  });
  it("catégories : g:<groupe>", () => {
    expect(m.catOut("live", "Sport")).toBe("g:Sport");
    expect(m.catIn("live", "g:Sport")).toBe("Sport");
    expect(m.catIn("live", "Sport")).toBe("Sport");
  });
  it("séries regroupées du bureau : identifiants conservés", () => {
    expect(m.out("series", 7)).toBe("7");
    expect(m.inn("series", "7")).toBe(7);
  });
});

describe("stalkerMapper", () => {
  const m = stalkerMapper(new Map([[2 ** 41 + 5, "ab12"]]));
  it("séries du module type=series : préfixe s:", () => {
    expect(m.out("series", 2 ** 41 + 5)).toBe("s:ab12");
    expect(m.inn("series", "s:ab12")).toBe(2 ** 41 + 5);
    expect(m.inn("series", "s:inconnue")).toBeNull();
  });
  it("séries de la VOD, films et direct : identifiant du portail en clair", () => {
    expect(m.out("series", 42)).toBe("42");
    expect(m.inn("series", "42")).toBe(42);
    expect(m.out("movie", 9)).toBe("9");
  });
  it("épisodes : <série>:<saison>:<numéro>", () => {
    expect(m.epOut(2 ** 41 + 5, 2 * 10_000 + 7)).toBe("s:ab12:2:7");
    expect(m.epOut(42, 10_000 + 3)).toBe("42:1:3");
    expect(m.epIn("s:ab12:2:7")).toBe(20_007);
    expect(m.epIn("42:1:3")).toBe(10_003);
    expect(m.parentOut(2 ** 41 + 5)).toBe("s:ab12");
    expect(m.parentIn("s:ab12")).toBe(2 ** 41 + 5);
  });
  it("épisode illisible : null", () => {
    expect(m.epIn("n'importe quoi")).toBeNull();
  });
});

describe("réglages d'affichage traduits", () => {
  const m = m3uMapper(["a", null, "b"]);
  it("chaînes masquées : aller-retour avec catégorie g:", () => {
    const wire = hiddenToWire([{ streamId: 3, catExt: "Sport", name: "B" }], [], m);
    expect(wire).toEqual([{ i: "b", c: "g:Sport", n: "B" }]);
    expect(hiddenFromWire(wire, m)).toEqual([{ streamId: 3, catExt: "Sport", name: "B" }]);
    expect(foreignHidden([{ i: "b", c: null, n: "" }, { i: "inconnu", c: null, n: "" }], m)).toEqual([{ i: "inconnu", c: null, n: "" }]);
  });
  it("sans traducteur : comportement historique (entiers uniquement)", () => {
    expect(hiddenFromWire([{ i: "12", c: null, n: "" }, { i: "x", c: null, n: "" }])).toEqual([{ streamId: 12, catExt: null, name: "" }]);
    expect(hiddenToWire([{ streamId: 12, catExt: "G", name: "n" }])).toEqual([{ i: "12", c: "G", n: "n" }]);
  });
  it("ordre des chaînes : portée = catégorie du compte, favoris inchangés", () => {
    const w = chOrderToWire([{ profile: "Principal", scope: "Sport", ids: [3, 1] }, { profile: "Principal", scope: "__fav__", ids: [2] }], [], m);
    expect(w).toEqual([{ p: "Principal", s: "g:Sport", k: ["b", "a"] }, { p: "Principal", s: "__fav__", k: ["m3u-0"] }]);
    const plan = planChOrder(w, [{ id: "p1", name: "Principal" }], [{ profile: "p1", scope: "Sport" }, { profile: "p1", scope: "Vieux" }], m);
    expect(plan.put).toEqual([{ profile: "p1", scope: "Sport", ids: [3, 1] }, { profile: "p1", scope: "__fav__", ids: [2] }]);
    expect(plan.remove).toEqual([{ profile: "p1", scope: "Vieux" }]);
    expect(plan.foreign).toEqual([]);
  });
  it("ordre reçu avec un identifiant inconnu : étranger, jamais perdu", () => {
    const plan = planChOrder([{ p: "Principal", s: "g:Sport", k: ["a", "zzz"] }], [{ id: "p1", name: "Principal" }], [{ profile: "p1", scope: "Sport" }], m);
    expect(plan.put).toEqual([]);
    expect(plan.foreign).toHaveLength(1);
    expect(plan.remove).toEqual([]);
  });
  it("catégories désactivées et ordre : g:<groupe> sur le compte, groupe en local", () => {
    const cats = [
      { kind: "live" as const, extId: "Sport", enabled: 0 as const, ord: 1, id: 1 },
      { kind: "live" as const, extId: "News", enabled: 1 as const, ord: 0, id: 2 },
    ];
    const p = toPrefs(null, cats, 1, { map: m });
    expect(p.disabled.live).toEqual(["g:Sport"]);
    expect(p.order?.live).toEqual(["g:News", "g:Sport"]);
    const local = [{ kind: "live" as const, extId: "Sport", enabled: 1 as const, ord: 0, id: 1 }, { kind: "live" as const, extId: "News", enabled: 1 as const, ord: 1, id: 2 }];
    const plan = fromPrefs({ ...p, updatedAt: 2, by: "x" } as never, local, m);
    expect(plan.changes).toEqual([{ id: 1, enabled: 0 }]);
    expect(plan.ords).toEqual(expect.arrayContaining([{ id: 2, ord: 0 }, { id: 1, ord: 1 }]));
  });
  it("identité : rien ne change", () => {
    expect(identityMapper.active).toBe(false);
    expect(identityMapper.catOut("live", "x")).toBe("x");
  });
});
