import { describe, expect, it } from "vitest";
import { addToGroup, createGroup, deleteGroup, groupIdOf, groupScope, isGroupScope, parseGroups, removeFromGroup, renameGroup } from "./channelGroups";

describe("groupes de chaînes : opérations", () => {
  it("créer, renommer, supprimer", () => {
    let l = createGroup([], "  Sport   FR ", "a");
    expect(l).toEqual([{ id: "a", name: "Sport FR", ids: [] }]);
    expect(createGroup(l, "   ", "b")).toBe(l);
    expect(createGroup(l, "Autre", "a")).toBe(l);
    l = renameGroup(l, "a", "Foot");
    expect(l[0]!.name).toBe("Foot");
    expect(renameGroup(l, "a", " ")).toBe(l);
    expect(deleteGroup(l, "a")).toEqual([]);
  });
  it("ajouter et retirer des chaînes (sans doublon, ordre d'ajout)", () => {
    let l = createGroup([], "G", "g");
    l = addToGroup(l, "g", [5, 3, 5]);
    l = addToGroup(l, "g", [3, 9]);
    expect(l[0]!.ids).toEqual([5, 3, 9]);
    l = removeFromGroup(l, "g", [3]);
    expect(l[0]!.ids).toEqual([5, 9]);
    const same = removeFromGroup(l, "g", [42]);
    expect(same).toEqual(l);
  });
  it("portée d'un groupe distincte d'une catégorie", () => {
    expect(isGroupScope(groupScope("x"))).toBe(true);
    expect(isGroupScope("12")).toBe(false);
    expect(isGroupScope(null)).toBe(false);
    expect(groupIdOf(groupScope("x"))).toBe("x");
  });
  it("lecture tolérante du stockage", () => {
    expect(parseGroups(null)).toEqual([]);
    expect(parseGroups([{ id: "a", name: "A", ids: [1, "2", 1, 3.5, 4] }, { id: "a", name: "dup" }, { id: "", name: "x" }, { id: "b", name: "" }, 7])).toEqual([{ id: "a", name: "A", ids: [1, 4] }]);
  });
});
