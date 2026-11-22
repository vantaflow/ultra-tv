import { describe, expect, it } from "vitest";
import { blockedCategories, isAdultCategory, needsCode, parseLocks, toggleLock, type GateInput } from "./contentGate";

const S = (...x: string[]) => new Set(x);
const base: GateInput = { hidden: S("h"), locked: S("l"), adult: S("a"), unlocked: S(), kids: false };

describe("isAdultCategory", () => {
  it("reconnaît les catégories adultes (nom ou indicateur)", () => {
    for (const n of ["XXX", "Adult 18+", "FR | ADULTE", "+18 Movies", "Erotic", "Érotique", "قنوات للكبار"]) expect(isAdultCategory({ name: n }), n).toBe(true);
    expect(isAdultCategory({ name: "Divers", adult: 1 })).toBe(true);
  });
  it("ne prend pas les faux amis", () => {
    for (const n of ["Heroes", "Zero Dark", "Hero Academia", "Sport", "Kids"]) expect(isAdultCategory({ name: n }), n).toBe(false);
  });
});

describe("blockedCategories", () => {
  it("profil normal, listes : masquées et verrouillées écartées, adulte visible", () => {
    expect([...blockedCategories(base, "list")].sort()).toEqual(["h", "l"]);
  });
  it("accueil : masquées, verrouillées ET adultes toujours écartées", () => {
    expect([...blockedCategories(base, "home")].sort()).toEqual(["a", "h", "l"]);
  });
  it("accueil : une catégorie déverrouillée pendant la session reste hors de l'accueil", () => {
    expect([...blockedCategories({ ...base, unlocked: S("l", "a") }, "home")].sort()).toEqual(["a", "h", "l"]);
  });
  it("profil Enfants : adultes écartées des listes", () => {
    expect([...blockedCategories({ ...base, kids: true }, "list")].sort()).toEqual(["a", "h", "l"]);
  });
  it("code saisi : la catégorie verrouillée revient dans les listes", () => {
    expect([...blockedCategories({ ...base, unlocked: S("l") }, "list")].sort()).toEqual(["h"]);
  });
});

describe("needsCode", () => {
  it("verrouillée ou adulte (Enfants) sans déverrouillage", () => {
    expect(needsCode("l", base)).toBe(true);
    expect(needsCode("a", base)).toBe(false);
    expect(needsCode("a", { ...base, kids: true })).toBe(true);
    expect(needsCode("l", { ...base, unlocked: S("l") })).toBe(false);
    expect(needsCode("x", base)).toBe(false);
  });
});

describe("verrous : stockage", () => {
  it("pose et retire", () => {
    let l = toggleLock({}, 3, "live", "12", true);
    expect(l).toEqual({ "3:live": ["12"] });
    l = toggleLock(l, 3, "live", "13", true);
    expect(l["3:live"]!.sort()).toEqual(["12", "13"]);
    l = toggleLock(toggleLock(l, 3, "live", "12", false), 3, "live", "13", false);
    expect(l).toEqual({});
  });
  it("lecture tolérante", () => {
    expect(parseLocks(null)).toEqual({});
    expect(parseLocks([1])).toEqual({});
    expect(parseLocks({ "3:live": ["1", 2, ""], bad: ["x"], "4:movie": "x" })).toEqual({ "3:live": ["1"] });
  });
});
