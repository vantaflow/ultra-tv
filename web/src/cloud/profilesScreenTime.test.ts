import { describe, expect, it } from "vitest";
import type { Profile } from "@/state/prefs";
import { applyActions, diffLog, hexToArgb, localEntries, parseRemote, plan, type SharedProfile } from "./profilesLogic";

const P = (id: string, name: string, extra: Partial<Profile> = {}): Profile => ({ id, name, color: "#D91E2B", ...extra });
const S = (n: string, at: number, extra: Partial<SharedProfile> = {}): SharedProfile => ({ n, c: hexToArgb("#D91E2B"), k: false, at, on: true, ...extra });

describe("temps d'écran dans la synchro des profils", () => {
  it("règles reçues d'ailleurs : mise à jour, et rien si identiques", () => {
    const local = [P("p1", "Léa")];
    const remote = [S("Léa", 500, { t: "L60;1=480-1200" })];
    expect(plan(local, { léa: { n: "Léa", at: 100, on: true } }, remote)).toEqual([{ type: "update", id: "p1", p: remote[0] }]);
    expect(plan([P("p1", "Léa", { screenTime: "L60;1=480-1200" })], { léa: { n: "Léa", at: 100, on: true } }, remote)).toEqual([]);
  });
  it("champ absent (ancienne version) : on n'y touche pas ; vide : règles retirées", () => {
    const log = { léa: { n: "Léa", at: 100, on: true } };
    const local = [P("p1", "Léa", { screenTime: "L60" })];
    expect(plan(local, log, [S("Léa", 500)])).toEqual([]);
    const act = plan(local, log, [S("Léa", 500, { t: "" })]);
    expect(act).toHaveLength(1);
    const r = applyActions(local, "p1", log, act, 1000);
    expect(r.profiles[0]!.screenTime).toBeUndefined();
  });
  it("application : règles normalisées, jamais de texte brut du réseau", () => {
    const local = [P("p1", "Léa")];
    const act = plan(local, { léa: { n: "Léa", at: 100, on: true } }, [S("Léa", 500, { t: "L90;3=600-700,650-720;<b>" })]);
    expect(applyActions(local, "p1", {}, act, 1000).profiles[0]!.screenTime).toBe("L90;3=600-720");
  });
  it("création sur un autre appareil : règles reprises", () => {
    const act = plan([P("main", "")], {}, [S("Enfants", 500, { k: true, t: "L45" })]);
    const r = applyActions([P("main", "")], "main", {}, act, 1000);
    expect(r.profiles.find((p) => p.name === "Enfants")).toMatchObject({ kids: true, screenTime: "L45" });
  });
  it("envoi : t toujours présent (vide = pas de règle) ; journal mis à jour au changement local", () => {
    expect(localEntries([P("p1", "Léa", { screenTime: "L60" })], {})[0]!.t).toBe("L60");
    expect(localEntries([P("p1", "Léa")], {})[0]!.t).toBe("");
    const log = diffLog([P("p1", "Léa")], [P("p1", "Léa", { screenTime: "L60" })], {}, 77);
    expect(log.léa).toEqual({ n: "Léa", at: 77, on: true });
  });
  it("lecture de la réponse du Worker : t conservé, absent reste absent", () => {
    const [a, b] = parseRemote([{ n: "A", at: 1, t: "L60" }, { n: "B", at: 1 }]);
    expect(a!.t).toBe("L60");
    expect("t" in b!).toBe(false);
  });
});
