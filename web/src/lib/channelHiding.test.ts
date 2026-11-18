import { describe, expect, it } from "vitest";
import { buildShift, globalToProfiles, hiddenByAll, mergeRemote, NO_SHIFT, partial, profileIdsFromPrefs, shownNum } from "./channelHiding";

const per = (o: Record<string, number[]>) => new Map(Object.entries(o).map(([k, v]) => [k, new Set(v)]));
const sorted = (m: Map<string, Set<number>>) => Object.fromEntries([...m].map(([k, v]) => [k, [...v].sort((a, b) => a - b)]));

describe("répartition « tous » / « par profil »", () => {
  it("masquées par tous = intersection des profils", () => {
    expect([...hiddenByAll(per({ a: [1, 2, 3], b: [2, 3, 4], c: [3, 2] }))].sort()).toEqual([2, 3]);
  });
  it("un seul profil : tout ce qu'il masque est « par tous »", () => {
    expect([...hiddenByAll(per({ a: [1, 2] }))].sort()).toEqual([1, 2]);
  });
  it("aucun profil, ou un profil qui ne masque rien : rien n'est supprimé", () => {
    expect(hiddenByAll(per({})).size).toBe(0);
    expect(hiddenByAll(per({ a: [1], b: [] })).size).toBe(0);
  });
  it("chaîne masquée par un profil seulement : reste « en propre »", () => {
    const p = per({ a: [1, 2], b: [2] });
    const all = hiddenByAll(p);
    expect([...partial(p.get("a")!, all)]).toEqual([1]);
    expect([...partial(p.get("b")!, all)]).toEqual([]);
  });
});

describe("fusion des réglages reçus (règle d'Android : mergeRemote)", () => {
  it("résultat d'un profil = ses choix propres reçus + la liste « par tous »", () => {
    const m = mergeRemote(per({ a: [], b: [] }), new Set([9]), per({ a: [1], b: [2] }));
    expect(sorted(m)).toEqual({ a: [1, 9], b: [2, 9] });
  });
  it("un profil sans entrée reçue n'a que la liste « par tous »", () => {
    const m = mergeRemote(per({ a: [5], b: [5, 6] }), new Set([9]), per({ a: [1] }));
    expect(sorted(m)).toEqual({ a: [1, 9], b: [9] });
  });
  it("ancien appareil (sans pHiddenCh) : les choix propres locaux sont conservés, seule la liste « par tous » change", () => {
    // 7 est masquée par tous ici ; 1 et 2 sont propres à a et b.
    const local = per({ a: [7, 1], b: [7, 2] });
    // L'ancien appareil renvoie sa liste globale : 7 n'y est plus (réaffichée), 8 y est (masquée par tous).
    const m = mergeRemote(local, new Set([8]), null);
    expect(sorted(m)).toEqual({ a: [1, 8], b: [2, 8] });
  });
  it("ancien appareil : rien à changer quand la liste globale est celle d'ici", () => {
    const local = per({ a: [7, 1], b: [7] });
    expect(sorted(mergeRemote(local, new Set([7]), null))).toEqual({ a: [1, 7], b: [7] });
  });
  it("une chaîne propre à un profil n'est pas masquée pour les autres", () => {
    const m = mergeRemote(per({ a: [], b: [] }), new Set(), per({ a: [4] }));
    expect(sorted(m)).toEqual({ a: [4], b: [] });
  });
});

describe("numéros par profil", () => {
  const shift = buildShift([{ catExt: "c1", num: 2 }, { catExt: "c1", num: 5 }, { catExt: "c2", num: 1 }]);
  it("numéro affiché = num moins les chaînes masquées de la même catégorie qui précèdent", () => {
    expect(shownNum(shift, { catExt: "c1", num: 1 })).toBe(1);
    expect(shownNum(shift, { catExt: "c1", num: 3 })).toBe(2);
    expect(shownNum(shift, { catExt: "c1", num: 4 })).toBe(3);
    expect(shownNum(shift, { catExt: "c1", num: 6 })).toBe(4);
  });
  it("les autres catégories ne sont pas touchées", () => {
    expect(shownNum(shift, { catExt: "c3", num: 9 })).toBe(9);
    expect(shownNum(shift, { catExt: "c2", num: 3 })).toBe(2);
  });
  it("sans chaîne masquée : numéros inchangés", () => {
    expect(shownNum(NO_SHIFT, { catExt: "c1", num: 7 })).toBe(7);
  });
  it("numérotation continue : les chaînes visibles d'une catégorie sont numérotées 1..n", () => {
    const hidden = [{ catExt: "c", num: 2 }, { catExt: "c", num: 3 }, { catExt: "c", num: 6 }];
    const visible = [1, 4, 5, 7, 8];
    expect(visible.map((n) => shownNum(buildShift(hidden), { catExt: "c", num: n }))).toEqual([1, 2, 3, 4, 5]);
  });
});

describe("migration", () => {
  it("chaque chaîne masquée globale devient masquée par tous les profils", () => {
    const rows = [{ sourceId: 1, streamId: 10, catExt: "5", name: "A" }, { sourceId: 2, streamId: 11, catExt: null, name: "B" }];
    const out = globalToProfiles(rows, ["main", "p1"]);
    expect(out).toHaveLength(4);
    expect(out.map((r) => r.key).sort()).toEqual(["main:1:10", "main:2:11", "p1:1:10", "p1:2:11"]);
    // Résultat : intersection = les deux chaînes, donc rien ne réapparaît.
    const perProfile = new Map<string, Set<number>>();
    for (const r of out.filter((x) => x.sourceId === 1)) (perProfile.get(r.profile) ?? perProfile.set(r.profile, new Set()).get(r.profile)!).add(r.streamId);
    expect([...hiddenByAll(perProfile)]).toEqual([10]);
  });
  it("profils lus dans les préférences enregistrées, « main » à défaut", () => {
    expect(profileIdsFromPrefs(JSON.stringify({ profiles: [{ id: "main" }, { id: "p123" }, { id: "main" }] }))).toEqual(["main", "p123"]);
    expect(profileIdsFromPrefs(null)).toEqual(["main"]);
    expect(profileIdsFromPrefs("pas du json")).toEqual(["main"]);
    expect(profileIdsFromPrefs(JSON.stringify({ profiles: [] }))).toEqual(["main"]);
  });
});
