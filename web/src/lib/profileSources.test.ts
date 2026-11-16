import { describe, expect, it } from "vitest";
import { activeToSet, cleanNames, toggleSource, visibleSources } from "./profileSources";
import { applyActions, diffLog, localEntries, parseRemote, plan } from "@/cloud/profilesLogic";
import type { Profile } from "@/state/prefs";

const list = [{ id: 1, name: "Alpha" }, { id: 2, name: "Beta" }, { id: 3, name: "Gamma" }];
const P = (id: string, name: string, extra: Partial<Profile> = {}): Profile => ({ id, name, color: "#D91E2B", ...extra });

describe("sources visibles par profil", () => {
  it("toutes sans choix ; noms inconnus = toutes (jamais un catalogue vide)", () => {
    expect(visibleSources(list, undefined)).toEqual(list);
    expect(visibleSources(list, ["beta", " GAMMA "]).map((s) => s.id)).toEqual([2, 3]);
    expect(visibleSources(list, ["Inconnue"])).toEqual(list);
  });
  it("la source active passe à la première visible, sinon rien ne change", () => {
    expect(activeToSet(list, ["Beta", "Gamma"], 1)).toBe(2);
    expect(activeToSet(list, ["Alpha", "Beta"], 1)).toBeNull();
    expect(activeToSet(list, undefined, 1)).toBeNull();
    expect(activeToSet([], ["x"], null)).toBeNull();
  });
  it("cocher / décocher ; tout coché = toutes", () => {
    const names = ["A", "B", "C"];
    expect(toggleSource(names, undefined, "A")).toEqual(["B", "C"]);
    expect(toggleSource(names, ["B", "C"], "A")).toBeUndefined();
    expect(toggleSource(names, ["B"], "B")).toBeUndefined();
    expect(cleanNames(["A", "a", " ", "B"])).toEqual(["A", "B"]);
    expect(cleanNames(Array.from({ length: 20 }, (_, i) => `S${i}`))).toHaveLength(12);
  });
});

describe("sources visibles : synchro des profils", () => {
  it("envoyées dans « s » (rien si toutes), journalisées, reçues et appliquées", () => {
    expect(localEntries([P("main", "", { sources: ["A", "B"] })], {})[0]!.s).toEqual(["A", "B"]);
    expect(localEntries([P("main", "")], {})[0]!.s).toBeUndefined();
    const log = diffLog([P("p1", "Papa")], [P("p1", "Papa", { sources: ["A"] })], {}, 50);
    expect(log.papa).toEqual({ n: "Papa", at: 50, on: true });
    const remote = parseRemote([{ n: "Papa", c: 0, k: false, at: 500, on: true, s: ["A", "B", 3] }]);
    expect(remote[0]!.s).toEqual(["A", "B"]);
    const profiles = [P("p1", "Papa", { sources: ["A"] })];
    const acts = plan(profiles, { papa: { n: "Papa", at: 100, on: true } }, remote);
    expect(acts).toHaveLength(1);
    expect(applyActions(profiles, "p1", {}, acts, 1000).profiles[0]!.sources).toEqual(["A", "B"]);
  });
  it("choix retiré ailleurs (entrée plus récente sans « s ») : retour à toutes les sources", () => {
    const profiles = [P("p1", "Papa", { sources: ["A"] })];
    const remote = parseRemote([{ n: "Papa", c: 0, k: false, at: 500, on: true }]);
    const acts = plan(profiles, { papa: { n: "Papa", at: 100, on: true } }, remote);
    expect(acts).toHaveLength(1);
    expect(applyActions(profiles, "p1", {}, acts, 1000).profiles[0]!.sources).toBeUndefined();
  });
});
