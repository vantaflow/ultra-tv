// Chaînes masquées et ordre des catégories PAR PROFIL dans les réglages du compte : conversions pures (nom de profil, traduction des identifiants).
import { describe, expect, it } from "vitest";
import { m3uMapper } from "./idmap";
import { pHiddenChToWire, planChannelPrefs, planPHiddenCh, toPrefs, type HiddenLocal } from "./prefs";

const profs = [{ id: "main", name: "Principal" }, { id: "p2", name: "Enfants" }];
const h = (streamId: number, catExt: string | null = "5", name = `c${streamId}`): HiddenLocal => ({ streamId, catExt, name });
const local = (o: Record<string, HiddenLocal[]>) => new Map(Object.entries(o).map(([k, v]) => [k, new Map(v.map((x) => [x.streamId, x]))]));

describe("pHiddenCh (envoi)", () => {
  it("une entrée par profil, par NOM, seulement les profils qui masquent quelque chose", () => {
    const w = pHiddenChToWire([{ profile: "Principal", items: [h(1), h(2)] }, { profile: "Enfants", items: [] }]);
    expect(w).toEqual([{ p: "Principal", ch: [{ i: "1", c: "5", n: "c1" }, { i: "2", c: "5", n: "c2" }] }]);
  });
  it("identifiants traduits vers ceux du compte (M3U : tvg-id)", () => {
    const map = m3uMapper(["bein1", null, "tf1"]);
    const w = pHiddenChToWire([{ profile: "Principal", items: [h(1, "Sport"), h(3, "Info")] }], [], map);
    expect(w).toEqual([{ p: "Principal", ch: [{ i: "bein1", c: "g:Sport", n: "c1" }, { i: "tf1", c: "g:Info", n: "c3" }] }]);
  });
  it("entrées étrangères conservées (profil inconnu), sans doublon avec les chaînes locales", () => {
    const w = pHiddenChToWire([{ profile: "Principal", items: [h(1)] }], [{ p: "Inconnu", ch: [{ i: "x", c: null, n: "" }] }, { p: "principal", ch: [{ i: "1", c: null, n: "" }, { i: "z", c: null, n: "" }] }]);
    expect(w.find((e) => e.p === "Inconnu")?.ch).toHaveLength(1);
    expect(w.find((e) => e.p === "Principal")?.ch.map((c) => c.i)).toEqual(["1", "z"]);
  });
  it("plafonds du Worker : au plus 3000 chaînes par profil", () => {
    const items = Array.from({ length: 3500 }, (_, i) => h(i + 1));
    expect(pHiddenChToWire([{ profile: "Principal", items }])[0]!.ch).toHaveLength(3000);
  });
});

describe("pHiddenCh (réception)", () => {
  it("rattache par nom sans casse, traduit les identifiants et garde les entrées non interprétables", () => {
    const map = m3uMapper(["bein1", null, "tf1"]);
    const p = planPHiddenCh([
      { p: "enfants", ch: [{ i: "bein1", c: "g:Sport", n: "BeIN" }, { i: "inconnue", c: null, n: "?" }] },
      { p: "Autre", ch: [{ i: "tf1", c: null, n: "" }] },
    ], profs, map);
    expect([...p.byProfile.get("p2")!]).toEqual([1]);
    expect([...p.byProfile.get("main")!]).toEqual([]);
    expect(p.infos.get(1)).toEqual({ streamId: 1, catExt: "Sport", name: "BeIN" });
    expect(p.foreign).toEqual([{ p: "enfants", ch: [{ i: "inconnue", c: null, n: "?" }] }, { p: "Autre", ch: [{ i: "tf1", c: null, n: "" }] }]);
  });
});

describe("plan de réception des chaînes masquées", () => {
  it("nouvel appareil : chaque profil reçoit ses choix propres + la liste « par tous »", () => {
    const plan = planChannelPrefs({
      hidden: [{ i: "9", c: "5", n: "tous" }],
      pHiddenCh: [{ p: "Principal", ch: [{ i: "1", c: "5", n: "a" }] }, { p: "Enfants", ch: [{ i: "2", c: "5", n: "b" }] }],
    }, local({ main: [], p2: [] }), profs);
    expect(plan.replace.get("main")!.map((x) => x.streamId).sort()).toEqual([1, 9]);
    expect(plan.replace.get("p2")!.map((x) => x.streamId).sort()).toEqual([2, 9]);
  });
  it("ancien appareil (hidden seul) : seule la liste « par tous » change, les choix propres restent", () => {
    // Ici : 7 masquée par tous, 1 propre au profil principal. L'ancien appareil réaffiche 7 et masque 8.
    const plan = planChannelPrefs({ hidden: [{ i: "8", c: null, n: "" }] }, local({ main: [h(7), h(1)], p2: [h(7)] }), profs);
    expect(plan.replace.get("main")!.map((x) => x.streamId).sort()).toEqual([1, 8]);
    expect(plan.replace.get("p2")!.map((x) => x.streamId)).toEqual([8]);
    expect(plan.foreignPHiddenCh).toBeNull();
  });
  it("aucun changement : aucun profil réécrit", () => {
    const plan = planChannelPrefs({ hidden: [{ i: "7", c: null, n: "" }], pHiddenCh: [{ p: "Principal", ch: [{ i: "1", c: null, n: "" }] }] }, local({ main: [h(7), h(1)], p2: [h(7)] }), profs);
    expect(plan.replace.size).toBe(0);
  });
  it("pHiddenCh seul (sans hidden) : la liste « par tous » locale est conservée", () => {
    const plan = planChannelPrefs({ pHiddenCh: [] }, local({ main: [h(7)], p2: [h(7), h(3)] }), profs);
    // 7 est masquée par tous ici : elle le reste ; 3 (propre à p2) n'est plus reçue : réaffichée.
    expect(plan.replace.has("main")).toBe(false);
    expect(plan.replace.get("p2")!.map((x) => x.streamId)).toEqual([7]);
  });
  it("identifiants M3U traduits aussi pour hidden et pHiddenCh", () => {
    const map = m3uMapper(["a", "b", "c"]);
    const plan = planChannelPrefs({ hidden: [{ i: "c", c: "g:X", n: "" }], pHiddenCh: [{ p: "Principal", ch: [{ i: "b", c: "g:X", n: "" }] }] }, local({ main: [], p2: [] }), profs, map);
    expect(plan.replace.get("main")!.map((x) => x.streamId).sort()).toEqual([2, 3]);
    expect(plan.replace.get("p2")!.map((x) => x.streamId)).toEqual([3]);
  });
});

describe("publication : hidden, pHiddenCh, pOrder, order", () => {
  const cats = [
    { kind: "live" as const, extId: "a", enabled: 1 as const, ord: 0 }, { kind: "live" as const, extId: "b", enabled: 1 as const, ord: 1 },
  ];
  it("order = ordre du premier profil, pOrder = ordre de chaque profil par nom", () => {
    const out = toPrefs(null, cats, 1000, {
      hidden: [h(9)], pHiddenCh: [{ profile: "Principal", items: [h(1)] }],
      legacyOrder: { live: ["b", "a"] },
      pOrder: [{ profile: "Principal", kind: "live", ids: ["b", "a"] }, { profile: "Enfants", kind: "live", ids: ["a", "b"] }],
    });
    expect(out.order?.live).toEqual(["b", "a"]);
    expect(out.pOrder).toEqual([{ p: "Principal", k: "live", ids: ["b", "a"] }, { p: "Enfants", k: "live", ids: ["a", "b"] }]);
    expect(out.hidden).toEqual([{ i: "9", c: "5", n: "c9" }]);
    expect(out.pHiddenCh).toEqual([{ p: "Principal", ch: [{ i: "1", c: "5", n: "c1" }] }]);
  });
  it("sans ordre de premier profil : `order` garde l'ordre des catégories chargées", () => {
    expect(toPrefs(null, cats, 1, {}).order?.live).toEqual(["a", "b"]);
  });
  it("champs facultatifs absents quand non fournis (compatibilité)", () => {
    const out = toPrefs(null, cats, 1, {});
    expect(out.pHiddenCh).toBeUndefined();
    expect(out.pOrder).toBeUndefined();
  });
  it("ids de catégories d'un M3U traduits en « g:groupe » dans pOrder et order", () => {
    const map = m3uMapper([]);
    const out = toPrefs(null, cats, 1, { map, legacyOrder: { live: ["b", "a"] }, pOrder: [{ profile: "Principal", kind: "live", ids: ["b", "a"].map((x) => map.catOut("live", x)) }] });
    expect(out.order?.live).toEqual(["g:b", "g:a"]);
    expect(out.pOrder![0]!.ids).toEqual(["g:b", "g:a"]);
  });
});
