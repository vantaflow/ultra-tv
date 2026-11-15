import { env } from "cloudflare:workers";
import { __setTursoConnect, KV_TABLE_SQL } from "../src/turso.js";

// Connexion Turso simulée : les requêtes du pilote sont exécutées sur la base SQLite locale des tests (même SQL que
// Turso). Valide l'adaptateur (formes de résultats, batch, KV sur table) sur toute la suite.
await env.DB.prepare(KV_TABLE_SQL).run();
__setTursoConnect(() => ({
  async batch(steps) {
    const stmts = steps.map((s) => (typeof s === "string" ? env.DB.prepare(s) : env.DB.prepare(s.sql).bind(...(s.args || []))));
    const res = await env.DB.batch(stmts);
    return res.map((r) => ({
      columns: r.results?.length ? Object.keys(r.results[0]) : [],
      rows: r.results || [],
      rowsAffected: r.meta?.changes ?? 0,
      lastInsertRowid: r.meta?.last_row_id ?? null,
    }));
  },
  async exec(sql) { await env.DB.exec(sql); },
}));
