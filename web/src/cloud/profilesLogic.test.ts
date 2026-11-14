import { describe, expect, it } from "vitest";
import type { Profile } from "@/state/prefs";
import { applyActions, argbToHex, diffLog, hexToArgb, localEntries, parseRemote, plan, type ProfileLog, type SharedProfile } from "./profilesLogic";

const P = (id: string, name: string, extra: Partial<Profile> = {}): Profile => ({ id, name, color: "#D91E2B", ...extra });
const S = (n: string, at: number, extra: Partial<SharedProfile> = {}): SharedProfile => ({ n, c: hexToArgb("#D91E2B"), k: false, at, on: true, ...extra });

describe("couleurs ARGB", () => {
  it("convertit dans les deux sens, en entier signé 32 bits", () => {
    expect(hexToArgb("#3B82F6")).toBe(0xff3b82f6 | 0);
    expect(hexToArgb("#3B82F6")).toBeLessThan(0);
    expect(argbToHex(0xff3b82f6 | 0)).toBe("#3B82F6");
    expect(argbToHex(hexToArgb("#0ea5e9"))).toBe("#0EA5E9");
    expect(argbToHex(4278190080 + 0x10203)).toBe("#010203"); // forme non signée acceptée par le Worker
  });
  it("0 = couleur absente, hex invalide = noir opaque", () => {
    expect(argbToHex(0)).toBeNull();
    expect(hexToArgb("rouge")).toBe(0xff000000 | 0);
  });
});

describe("journal", () => {
  it("création, renommage (ancien nom gardé), couleur, suppression", () => {
    let log: ProfileLog = {};
    log = diffLog([P("main", "")], [P("main", ""), P("p1", "Papa")], log, 10);
    expect(log.papa).toEqual({ n: "Papa", at: 10, on: true });
    log = diffLog([P("p1", "Papa")], [P("p1", "Papa 2")], log, 20);
    expect(log.papa).toEqual({ n: "Papa", at: 20, on: false });
    expect(log["papa 2"]).toEqual({ n: "Papa 2", at: 20, on: true, from: "Papa" });
    log = diffLog([P("p1", "Papa 2")], [P("p1", "Papa 2", { color: "#0EA5E9" })], log, 30);
    expect(log["papa 2"]).toEqual({ n: "Papa 2", at: 30, on: true, from: "Papa" });
    log = diffLog([P("main", ""), P("p1", "Papa 2")], [P("main", "")], log, 40);
    expect(log["papa 2"]).toEqual({ n: "Papa 2", at: 40, on: false });
  });
  it("recréer un profil supprimé le remet « on »", () => {
    let log = diffLog([P("a", "X")], [], {}, 5);
    log = diffLog([], [P("b", "x")], log, 9);
    expect(log.x).toEqual({ n: "x", at: 9, on: true });
  });
});

describe("localEntries", () => {
  it("profil sans nom = Principal, date du journal sinon de création, tombes jointes, langue envoyée", () => {
    const log: ProfileLog = { papa: { n: "Papa", at: 500, on: false } };
    const out = localEntries([P("main", ""), P("p1700000000000", "Zoé", { lang: "es", kids: true })], log);
    expect(out[0]).toMatchObject({ n: "Principal", at: 1, on: true });
    expect(out[0]).not.toHaveProperty("l");
    expect(out[1]).toMatchObject({ n: "Zoé", at: 1700000000000, k: true, l: "es" });
    expect(out[2]).toMatchObject({ n: "Papa", at: 500, on: false });
  });
});

describe("plan", () => {
  const log: ProfileLog = { léa: { n: "Léa", at: 100, on: true } };
  it("crée un profil inconnu, ignore un profil plus ancien déjà connu", () => {
    expect(plan([P("main", "")], {}, [S("Papa", 50)])).toEqual([{ type: "create", p: S("Papa", 50) }]);
    expect(plan([P("a", "Léa")], log, [S("léa", 90, { c: hexToArgb("#10B981") })])).toEqual([]);
  });
  it("met à jour couleur / enfants / langue quand la version reçue est plus récente", () => {
    const a = plan([P("a", "Léa")], log, [S("Léa", 200, { c: hexToArgb("#10B981") })]);
    expect(a).toEqual([{ type: "update", id: "a", p: S("Léa", 200, { c: hexToArgb("#10B981") }) }]);
    expect(plan([P("a", "Léa")], log, [S("Léa", 200, { l: "fr" })])).toHaveLength(1);
    expect(plan([P("a", "Léa", { lang: "fr" })], log, [S("Léa", 200, { l: "fr" })])).toEqual([]);
    expect(plan([P("a", "Léa")], log, [S("Léa", 200, { k: true })])).toHaveLength(1);
  });
  it("renommage reçu : renomme le profil local (même id) au lieu de supprimer + créer", () => {
    const l: ProfileLog = { papa: { n: "Papa", at: 100, on: true } };
    const a = plan([P("a", "Papa")], l, [S("Papa", 300, { on: false }), S("Papa 2", 300, { f: "Papa" })]);
    expect(a).toEqual([{ type: "rename", id: "a", p: S("Papa 2", 300, { f: "Papa" }) }]);
  });
  it("tombe plus récente = suppression ; tombe plus ancienne que le profil local = ignorée", () => {
    expect(plan([P("main", ""), P("a", "Léa")], log, [S("Léa", 200, { on: false })])).toEqual([{ type: "delete", id: "a", p: S("Léa", 200, { on: false }) }]);
    expect(plan([P("main", ""), P("a", "Léa")], log, [S("Léa", 50, { on: false })])).toEqual([]);
  });
  it("profil supprimé ici : ne revient pas si sa tombe locale est plus récente", () => {
    const l: ProfileLog = { léa: { n: "Léa", at: 300, on: false } };
    expect(plan([P("main", "")], l, [S("Léa", 200)])).toEqual([]);
    expect(plan([P("main", "")], l, [S("Léa", 400)])).toEqual([{ type: "create", p: S("Léa", 400) }]);
  });
  it("tombe d'un profil inconnu ici : rien à faire", () => {
    expect(plan([P("main", "")], {}, [S("Zed", 200, { on: false })])).toEqual([]);
  });
});

describe("applyActions", () => {
  it("crée avec couleur ARGB convertie, langue et enfants ; le journal reprend la date reçue", () => {
    const r = applyActions([P("main", "")], "main", {}, [{ type: "create", p: S("Papa", 50, { c: hexToArgb("#3B82F6"), k: true, l: "pl" }) }], 1000);
    expect(r.profiles[1]).toMatchObject({ name: "Papa", color: "#3B82F6", kids: true, lang: "pl" });
    expect(r.log.papa).toEqual({ n: "Papa", at: 50, on: true });
  });
  it("renomme en gardant l'id, journal : tombe de l'ancien nom + nouveau nom avec « from »", () => {
    const r = applyActions([P("main", ""), P("a", "Papa")], "a", {}, [{ type: "rename", id: "a", p: S("Papa 2", 300, { f: "Papa" }) }], 1000);
    expect(r.profiles[1]).toMatchObject({ id: "a", name: "Papa 2" });
    expect(r.profileId).toBe("a");
    expect(r.log.papa).toEqual({ n: "Papa", at: 300, on: false });
    expect(r.log["papa 2"]).toEqual({ n: "Papa 2", at: 300, on: true, from: "Papa" });
  });
  it("langue « system » retire la langue du profil ; langue inconnue ignorée", () => {
    const r = applyActions([P("a", "Léa", { lang: "fr" })], "a", {}, [{ type: "update", id: "a", p: S("Léa", 9, { l: "system" }) }], 1);
    expect(r.profiles[0]!.lang).toBeUndefined();
    const r2 = applyActions([P("a", "Léa", { lang: "fr" })], "a", {}, [{ type: "update", id: "a", p: S("Léa", 9, { l: "de", k: true }) }], 1);
    expect(r2.profiles[0]).toMatchObject({ lang: "fr", kids: true });
  });
  it("supprime et bascule sur le premier profil ; jamais le dernier", () => {
    const del = (n: string, at = 9): SharedProfile => S(n, at, { on: false });
    const r = applyActions([P("main", ""), P("a", "Léa")], "a", {}, [{ type: "delete", id: "a", p: del("Léa") }], 1);
    expect(r.profiles.map((p) => p.id)).toEqual(["main"]);
    expect(r.profileId).toBe("main");
    expect(r.deletedIds).toEqual(["a"]);
    const last = applyActions([P("main", "")], "main", {}, [{ type: "delete", id: "main", p: del("Principal") }], 1);
    expect(last.profiles).toHaveLength(1);
    expect(last.deletedIds).toEqual([]);
  });
  it("plusieurs créations dans la même milliseconde : ids distincts", () => {
    const r = applyActions([P("main", "")], "main", {}, [{ type: "create", p: S("A", 1) }, { type: "create", p: S("B", 1) }], 1000);
    expect(new Set(r.profiles.map((p) => p.id)).size).toBe(3);
  });
});

describe("parseRemote", () => {
  it("écarte les entrées invalides et coupe les noms à 24 caractères", () => {
    const out = parseRemote([{ n: "ok", at: 5 }, { n: "", at: 5 }, { n: "x", at: 0 }, null, { n: "a".repeat(40), at: 1, c: -1, k: true, l: "fr" }]);
    expect(out).toHaveLength(2);
    expect(out[1]).toMatchObject({ n: "a".repeat(24), c: -1, k: true, l: "fr", on: true });
  });
});
