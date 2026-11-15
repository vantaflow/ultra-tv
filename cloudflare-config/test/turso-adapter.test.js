import { env } from "cloudflare:workers";
import { beforeAll, describe, expect, it } from "vitest";
import { TursoD1, TursoKV, KV_TABLE_SQL, withStorage } from "../src/turso.js";

// Connexion simulée sur la base locale des tests (même SQL que Turso).
const conn = {
  async batch(steps) {
    const res = await env.DB.batch(steps.map((s) => env.DB.prepare(s.sql).bind(...(s.args || []))));
    return res.map((r) => ({ columns: r.results?.length ? Object.keys(r.results[0]) : [], rows: r.results || [], rowsAffected: r.meta?.changes ?? 0, lastInsertRowid: r.meta?.last_row_id ?? null }));
  },
};

describe("adaptateur Turso", () => {
  let now = 1_000_000;
  const db = new TursoD1(conn);
  const kv = new TursoKV(db, () => now);
  beforeAll(async () => { await env.DB.prepare(KV_TABLE_SQL).run(); });

  it("KV : put / get / expiration / métadonnées", async () => {
    await kv.put("a:1", "un", { expirationTtl: 60, metadata: { f: 1 } });
    expect(await kv.get("a:1")).toBe("un");
    expect((await kv.getWithMetadata("a:1", "json")).metadata).toEqual({ f: 1 });
    now += 61_000;
    expect(await kv.get("a:1")).toBeNull();
  });

  it("KV : list par préfixe, paginée comme Cloudflare", async () => {
    for (let i = 0; i < 5; i++) await kv.put(`p:${i}`, String(i));
    await kv.put("q:0", "x");
    const p1 = await kv.list({ prefix: "p:", limit: 2 });
    expect(p1.keys.map((k) => k.name)).toEqual(["p:0", "p:1"]);
    expect(p1.list_complete).toBe(false);
    const p2 = await kv.list({ prefix: "p:", limit: 10, cursor: p1.cursor });
    expect(p2.keys.map((k) => k.name)).toEqual(["p:2", "p:3", "p:4"]);
    expect(p2.list_complete).toBe(true);
    await kv.delete("p:0");
    expect(await kv.get("p:0")).toBeNull();
    expect(await kv.get("json-absent", "json")).toBeNull();
  });

  it("D1 : first(colonne), run (changes) et batch atomique", async () => {
    await db.prepare("CREATE TABLE IF NOT EXISTS t_ad (id INTEGER PRIMARY KEY, v TEXT)").run();
    const r = await db.prepare("INSERT INTO t_ad (v) VALUES (?)").bind("x").run();
    expect(r.meta.changes).toBe(1);
    await db.batch([db.prepare("INSERT INTO t_ad (v) VALUES (?)").bind("y"), db.prepare("INSERT INTO t_ad (v) VALUES (?)").bind("z")]);
    expect(await db.prepare("SELECT count(*) AS n FROM t_ad").first("n")).toBe(3);
    expect((await db.prepare("SELECT v FROM t_ad ORDER BY id").all()).results.map((x) => x.v)).toEqual(["x", "y", "z"]);
  });

  it("sans TURSO_URL ou en mode d1 : environnement inchangé", () => {
    const e = { STORAGE_MODE: "d1", DB: 1, CONFIG: 2 };
    expect(withStorage(e)).toBe(e);
    const f = { STORAGE_MODE: "turso", DB: 1, CONFIG: 2 };
    expect(withStorage(f)).toBe(f);
  });
});
