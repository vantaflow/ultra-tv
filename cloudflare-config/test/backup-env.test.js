import { describe, expect, it } from "vitest";
import { buildEnv, missingVars, makeLimiter, readOnlyDb, ReadOnlyError, NO_DURABLE_OBJECTS } from "../backup/src/env.js";

const VARS = { TURSO_URL: "libsql://x.test", TURSO_AUTH_TOKEN: "tok", PROVIDER_ENC_KEY: "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" };
// Connexion simulée : chaque requête renvoie une ligne { n: 1 } ; les requêtes envoyées sont gardées.
const fakeConn = (sent = []) => ({
  batch: async (steps) => { sent.push(...steps.map((s) => s.sql)); return steps.map(() => ({ columns: ["n"], rows: [{ n: 1 }] })); },
  exec: async () => {},
});

describe("secours : couche env", () => {
  it("signale les variables absentes ou vides, sans valeurs", () => {
    expect(missingVars({})).toEqual(["TURSO_URL", "TURSO_AUTH_TOKEN", "PROVIDER_ENC_KEY"]);
    expect(missingVars({ ...VARS, TURSO_AUTH_TOKEN: "  " })).toEqual(["TURSO_AUTH_TOKEN"]);
    expect(missingVars(VARS)).toEqual([]);
    expect(buildEnv({ TURSO_URL: "libsql://x.test" })).toBeNull();
  });

  it("fabrique l'environnement du Worker : Turso, clés, limiteurs, sans Durable Object", async () => {
    const env = buildEnv({ ...VARS, PROVIDER_ENC_KEY_PREVIOUS: "old", SITE_HOST: "site.test" }, { connect: () => fakeConn() });
    expect(env.STORAGE).toBe("turso");
    expect(env.STORAGE_MODE).toBe("turso");
    expect(env.PROVIDER_ENC_KEY).toBe(VARS.PROVIDER_ENC_KEY);
    expect(env.PROVIDER_ENC_KEY_PREVIOUS).toBe("old");
    expect(env.SITE_HOST).toBe("site.test");
    for (const k of ["RL_10", "RL_30", "RL_60", "RL_120"]) expect(typeof env[k].limit).toBe("function");
    expect(() => env.GUARD.get(env.GUARD.idFromName("x"))).toThrow("durable_objects_unavailable");
    expect(env.SESSION_SECRET).toBeUndefined();
    expect(env.OPS_TOKEN).toBeUndefined();
  });

  it("lecture seule : les SELECT passent, toute écriture est refusée", async () => {
    const sent = [];
    const env = buildEnv(VARS, { connect: () => fakeConn(sent) });
    expect(await env.DB.prepare("SELECT 1 AS n").first("n")).toBe(1);
    await expect(env.DB.prepare("INSERT INTO kv_docs (k, v) VALUES (?, ?)").bind("a", "b").run()).rejects.toBeInstanceOf(ReadOnlyError);
    await expect(env.DB.prepare("UPDATE kv_docs SET v = 1").run()).rejects.toThrow("backup_read_only");
    await expect(env.DB.prepare("DELETE FROM kv_docs").first()).rejects.toThrow("backup_read_only");
    await expect(env.DB.batch([env.DB.prepare("SELECT 1"), env.DB.prepare("DROP TABLE kv_docs")])).rejects.toThrow("backup_read_only");
    await expect(env.DB.exec("CREATE TABLE t (a)")).rejects.toThrow("backup_read_only");
    await expect(env.CONFIG.put("k", "v")).rejects.toThrow("backup_read_only");
    await expect(env.CONFIG.delete("k")).rejects.toThrow("backup_read_only");
    await expect(env.CONFIG.deleteMany(["k"])).rejects.toThrow("backup_read_only");
    await expect(env.CONFIG.purgeExpired()).rejects.toThrow("backup_read_only");
    await expect(env.CONFIG.get("k")).resolves.toBeNull(); // la lecture ne lève pas
    // Aucune écriture n'est partie vers Turso.
    expect(sent.every((s) => /^\s*select/i.test(s))).toBe(true);
  });

  it("readOnlyDb garde le compteur de requêtes de la base", () => {
    const db = readOnlyDb({ queries: 7, prepare: () => ({}), batch: async () => [] });
    expect(db.queries).toBe(7);
  });

  it("limiteur simple : refuse au-delà de la limite, par clé, puis libère la fenêtre", async () => {
    let t = 0;
    const l = makeLimiter(2, 60_000, () => t);
    expect((await l.limit({ key: "a" })).success).toBe(true);
    expect((await l.limit({ key: "a" })).success).toBe(true);
    expect((await l.limit({ key: "a" })).success).toBe(false);
    expect((await l.limit({ key: "b" })).success).toBe(true);
    t = 61_000;
    expect((await l.limit({ key: "a" })).success).toBe(true);
  });

  it("Durable Objects : idFromName rend le nom, get échoue", () => {
    expect(NO_DURABLE_OBJECTS.idFromName("n")).toBe("n");
    expect(() => NO_DURABLE_OBJECTS.get("n")).toThrow();
  });
});
