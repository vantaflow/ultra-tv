import { describe, expect, it } from "vitest";
import { sortGuideChannels } from "./guideOrder";

const c = (num: number | null, display: string, ord = 0) => ({ num: num as number, display, ord });

describe("ordre des chaînes du guide", () => {
  it("numéro croissant, chaînes sans numéro à la fin par nom", () => {
    const got = sortGuideChannels([c(12, "M6"), c(0, "Zeta"), c(3, "TF1"), c(null, "Alpha"), c(1, "Canal")]);
    expect(got.map((x) => x.display)).toEqual(["Canal", "TF1", "M6", "Alpha", "Zeta"]);
  });
  it("les Favoris (ordre d'ajout) sont triés par numéro aussi", () => {
    const favs = [c(40, "Sport"), c(2, "Info"), c(15, "Ciné")];
    expect(sortGuideChannels(favs).map((x) => x.num)).toEqual([2, 15, 40]);
  });
  it("numéro égal : ordre du catalogue ; entrée non modifiée", () => {
    const input = [c(5, "B", 2), c(5, "A", 1)];
    expect(sortGuideChannels(input).map((x) => x.display)).toEqual(["A", "B"]);
    expect(input[0]!.display).toBe("B");
  });
  it("tri numérique, pas lexicographique", () => {
    expect(sortGuideChannels([c(100, "a"), c(9, "b"), c(20, "c")]).map((x) => x.num)).toEqual([9, 20, 100]);
  });
});
