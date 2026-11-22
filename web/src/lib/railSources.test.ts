import { describe, expect, it } from "vitest";
import { railSourceChoices } from "./profileSources";

const L = [{ id: 1, name: "A" }, { id: 2, name: "B" }, { id: 3, name: "C" }];

describe("sélecteur de source du rail", () => {
  it("affiché seulement quand le profil voit plusieurs sources", () => {
    expect(railSourceChoices(L, undefined).map((s) => s.id)).toEqual([1, 2, 3]);
    expect(railSourceChoices(L, ["B", "C"]).map((s) => s.id)).toEqual([2, 3]);
    expect(railSourceChoices(L, ["B"])).toEqual([]);
    expect(railSourceChoices([L[0]!], undefined)).toEqual([]);
  });
});
