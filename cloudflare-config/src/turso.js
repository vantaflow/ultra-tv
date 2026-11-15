// Stockage sur Turso (libSQL), présenté avec les interfaces de D1 (`env.DB`) et de KV (`env.CONFIG`) : le reste du
// Worker ne change pas. Pilote `@tursodatabase/serverless` : uniquement fetch(), aucun système de fichiers (Workers).
//
// Mode choisi par la variable STORAGE_MODE :
//   "d1"    (défaut) D1 + KV de Cloudflare, Turso ignoré ;
//   "tee"   D1 + KV restent la source de vérité, chaque écriture est recopiée dans Turso (migration sans perte) ;
//   "turso" Turso seul (D1 et KV ne sont plus touchés : ils restent intacts, en secours).
//
// Pourquoi : quotas gratuits Cloudflare (D1 100 000 écritures/jour, KV 1 000) ; Turso gratuit ≈ 10 M écritures/mois.

import { connect } from "@tursodatabase/serverless";

/** Table qui remplace KV : valeur, expiration (ms) et métadonnées JSON. */
export const KV_TABLE_SQL = "CREATE TABLE IF NOT EXISTS kv_store (k TEXT PRIMARY KEY, v TEXT NOT NULL, exp INTEGER, meta TEXT)";

/** Ligne du pilote (accès par nom et par index) → objet simple, comme les résultats de D1. */
function plain(columns, row) {
  const o = {};
  columns.forEach((c, i) => { o[c] = row[c] !== undefined ? row[c] : row[i]; });
  return o;
}

function resultOf(r) {
  const columns = r?.columns || [];
  const results = (r?.rows || []).map((row) => plain(columns, row));
  return {
    results,
    success: true,
    meta: { changes: r?.rowsAffected ?? 0, last_row_id: r?.lastInsertRowid ?? null, rows_read: r?.rowsRead ?? 0, rows_written: r?.rowsWritten ?? 0 },
  };
}

class TursoStatement {
  constructor(db, sql, args = []) { this.db = db; this.sql = sql; this.args = args; }
  bind(...args) { return new TursoStatement(this.db, this.sql, args); }
  get step() { return { sql: this.sql, args: this.args }; }
  async exec() { const [r] = await this.db.conn.batch([this.step]); return resultOf(r); }
  async all() { return this.exec(); }
  async run() { return this.exec(); }
  async first(col) {
    const { results } = await this.exec();
    const row = results[0] ?? null;
    return col === undefined || row === null ? row : (row[col] ?? null);
  }
  async raw() { const { results } = await this.exec(); return results.map((o) => Object.values(o)); }
}

/** Base Turso avec l'interface de D1 : prepare/bind/first/all/run/raw et batch (atomique, une seule requête HTTP). */
export class TursoD1 {
  constructor(conn) { this.conn = conn; }
  prepare(sql) { return new TursoStatement(this, sql); }
  async batch(stmts) {
    if (!stmts.length) return [];
    const res = await this.conn.batch(stmts.map((s) => s.step), "write");
    return res.map(resultOf);
  }
  async exec(sql) { await this.conn.sequence(sql); return { count: 1 }; }
}

const decode = (v, type) => {
  if (v === null || v === undefined) return null;
  if (type === "json") { try { return JSON.parse(v); } catch { return null; } }
  if (type === "arrayBuffer") return new TextEncoder().encode(v).buffer;
  return v;
};
const typeOf = (opt) => (typeof opt === "string" ? opt : opt?.type) || "text";

/** KV sur une table Turso : get / getWithMetadata / put (expirationTtl, expiration, metadata) / delete / list. */
export class TursoKV {
  constructor(db, now = () => Date.now()) { this.db = db; this.now = now; }
  async row(k) {
    const r = await this.db.prepare("SELECT v, exp, meta FROM kv_store WHERE k = ?").bind(k).first();
    if (!r || (r.exp && r.exp <= this.now())) return null;
    return r;
  }
  async get(k, opt) { const r = await this.row(k); return r ? decode(r.v, typeOf(opt)) : null; }
  async getWithMetadata(k, opt) {
    const r = await this.row(k);
    return { value: r ? decode(r.v, typeOf(opt)) : null, metadata: r?.meta ? JSON.parse(r.meta) : null };
  }
  async put(k, v, opts = {}) {
    const exp = opts.expiration ? opts.expiration * 1000 : opts.expirationTtl ? this.now() + opts.expirationTtl * 1000 : null;
    const val = typeof v === "string" ? v : new TextDecoder().decode(v);
    await this.db.prepare("INSERT INTO kv_store (k, v, exp, meta) VALUES (?, ?, ?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v, exp = excluded.exp, meta = excluded.meta")
      .bind(k, val, exp, opts.metadata === undefined ? null : JSON.stringify(opts.metadata)).run();
  }
  async delete(k) { await this.db.prepare("DELETE FROM kv_store WHERE k = ?").bind(k).run(); }
  /** Comme KV : clés triées, `cursor` = dernière clé de la page précédente, 1 000 clés au plus par page. */
  async list({ prefix = "", cursor, limit = 1000 } = {}) {
    const lim = Math.min(Math.max(1, limit), 1000);
    const { results } = await this.db.prepare(
      "SELECT k, exp, meta FROM kv_store WHERE substr(k, 1, ?) = ? AND k > ? AND (exp IS NULL OR exp > ?) ORDER BY k LIMIT ?",
    ).bind(prefix.length, prefix, cursor || "", this.now(), lim + 1).all();
    const page = results.slice(0, lim);
    const more = results.length > lim;
    return {
      keys: page.map((r) => ({ name: r.k, ...(r.exp ? { expiration: Math.floor(r.exp / 1000) } : {}), ...(r.meta ? { metadata: JSON.parse(r.meta) } : {}) })),
      list_complete: !more,
      ...(more ? { cursor: page[page.length - 1].k } : {}),
    };
  }
}

// ---- double écriture (phase de migration) -------------------------------------------------------------------

const mirrorErr = (e) => console.error("miroir Turso:", e?.message);

class TeeStatement {
  constructor(p, m) { this.p = p; this.m = m; }
  bind(...a) { return new TeeStatement(this.p.bind(...a), this.m.bind(...a)); }
  first(col) { return this.p.first(col); }
  all() { return this.p.all(); }
  raw(o) { return this.p.raw(o); }
  async run() {
    const [r] = await Promise.all([this.p.run(), this.m.run().catch(mirrorErr)]);
    return r;
  }
}

/** D1 (vérité) + copie de chaque écriture dans Turso ; une erreur côté Turso n'affecte jamais la réponse. */
export class TeeD1 {
  constructor(primary, mirror) { this.p = primary; this.m = mirror; }
  prepare(sql) { return new TeeStatement(this.p.prepare(sql), this.m.prepare(sql)); }
  async batch(stmts) {
    const [r] = await Promise.all([this.p.batch(stmts.map((s) => s.p)), this.m.batch(stmts.map((s) => s.m)).catch(mirrorErr)]);
    return r;
  }
  exec(sql) { return this.p.exec(sql); }
}

/** KV (vérité) + copie des écritures dans Turso. */
export class TeeKV {
  constructor(primary, mirror) { this.p = primary; this.m = mirror; }
  get(...a) { return this.p.get(...a); }
  getWithMetadata(...a) { return this.p.getWithMetadata(...a); }
  list(...a) { return this.p.list(...a); }
  async put(k, v, opts) { await Promise.all([this.p.put(k, v, opts), this.m.put(k, v, opts).catch(mirrorErr)]); }
  async delete(k) { await Promise.all([this.p.delete(k), this.m.delete(k).catch(mirrorErr)]); }
}

/**
 * Environnement de la requête selon STORAGE_MODE. Sans TURSO_URL ou TURSO_AUTH_TOKEN, ou en mode "d1" : `env`
 * inchangé (tests, auto-hébergement sans Turso). Une connexion par requête : pas d'état partagé entre requêtes.
 */
let connectOverride = null;
/** Tests uniquement : remplace le pilote par une connexion simulée. */
export function __setTursoConnect(fn) { connectOverride = fn; }

export function withStorage(env, connectImpl = connect) {
  const mode = env?.STORAGE_MODE || "d1";
  if (mode === "d1" || !env.TURSO_URL || !env.TURSO_AUTH_TOKEN || env.STORAGE) return env;
  const tdb = new TursoD1((connectOverride || connectImpl)({ url: env.TURSO_URL, authToken: env.TURSO_AUTH_TOKEN }));
  const tkv = new TursoKV(tdb);
  if (mode === "turso") return { ...env, DB: tdb, CONFIG: tkv, STORAGE: "turso" };
  if (mode === "tee") {
    return { ...env, DB: env.DB ? new TeeD1(env.DB, tdb) : tdb, CONFIG: env.CONFIG ? new TeeKV(env.CONFIG, tkv) : tkv, STORAGE: "tee" };
  }
  return env;
}
