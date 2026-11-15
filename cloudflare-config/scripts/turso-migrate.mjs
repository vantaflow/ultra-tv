// Migration D1 + KV (Cloudflare) → Turso, rejouable sans risque.
//
//   TURSO_URL=libsql://… TURSO_AUTH_TOKEN=… node scripts/turso-migrate.mjs schema   tables (migrations/ + kv_store)
//   … node scripts/turso-migrate.mjs sync     Turso := D1 + KV (ajouts, mises à jour, suppressions), par lots atomiques
//   … node scripts/turso-migrate.mjs verify   compte les écarts restants (0 attendu avant la bascule)
//
// D1 est lu avec `wrangler d1 execute --remote`, KV avec l'API REST de Cloudflare (jeton de `wrangler auth token`).
// Pendant la migration le Worker tourne en STORAGE_MODE="tee" : D1 et KV restent la vérité, Turso reçoit chaque
// écriture ; `sync` rattrape l'historique et corrige les écarts, `verify` confirme zéro écart avant STORAGE_MODE="turso".

import { connect } from "@tursodatabase/serverless";
import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync } from "node:fs";

const D1_NAME = "ultratv-telemetry";
const KV_ID = "4d450f36e3e34ddf9520e53bf6c241ea";
const TABLES = ["events", "anon_pings", "device_seen", "device_activity", "shared_state", "kv_docs"];
const CHUNK = 100;

const conn = connect({ url: process.env.TURSO_URL, authToken: process.env.TURSO_AUTH_TOKEN });
const wrangler = (args) => execFileSync("npx", ["wrangler", ...args], { encoding: "utf8", maxBuffer: 512 * 1024 * 1024, stdio: ["ignore", "pipe", "ignore"] });

async function q(sql, args = []) {
  const [r] = await conn.batch([{ sql, args }]);
  return (r.rows || []).map((row) => Object.fromEntries(r.columns.map((c, i) => [c, row[c] !== undefined ? row[c] : row[i]])));
}

async function writeAll(steps) {
  for (let i = 0; i < steps.length; i += CHUNK) await conn.batch(steps.slice(i, i + CHUNK), "immediate");
}

function d1All(sql) {
  const out = JSON.parse(wrangler(["d1", "execute", D1_NAME, "--remote", "--json", "--command", sql]));
  return out[0].results;
}

async function pkOf(table) {
  const cols = await q(`PRAGMA table_info(${table})`);
  const pk = cols.filter((c) => Number(c.pk) > 0).sort((a, b) => a.pk - b.pk).map((c) => c.name);
  return { pk, cols: cols.map((c) => c.name) };
}

const keyOf = (row, pk) => JSON.stringify(pk.map((c) => row[c]));
const same = (a, b, cols) => cols.every((c) => (a[c] ?? null) === (b[c] ?? null));

/** Lignes D1 de la table, par pages (une réponse wrangler par page). */
function d1Rows(table, pk) {
  const rows = [];
  const order = pk.join(", ");
  for (let off = 0; ; off += 5000) {
    const page = d1All(`SELECT * FROM ${table} ORDER BY ${order} LIMIT 5000 OFFSET ${off}`);
    rows.push(...page);
    if (page.length < 5000) return rows;
  }
}

async function diffTable(table, apply) {
  const { pk, cols } = await pkOf(table);
  const src = d1Rows(table, pk);
  const dst = await q(`SELECT * FROM ${table}`);
  const have = new Map(dst.map((r) => [keyOf(r, pk), r]));
  const want = new Set();
  const steps = [];
  let add = 0, upd = 0, del = 0;
  for (const r of src) {
    const k = keyOf(r, pk);
    want.add(k);
    const cur = have.get(k);
    if (cur && same(cur, r, cols)) continue;
    cur ? upd++ : add++;
    steps.push({ sql: `INSERT OR REPLACE INTO ${table} (${cols.join(", ")}) VALUES (${cols.map(() => "?").join(", ")})`, args: cols.map((c) => r[c] ?? null) });
  }
  for (const [k, r] of have) {
    if (want.has(k)) continue;
    del++;
    steps.push({ sql: `DELETE FROM ${table} WHERE ${pk.map((c) => `${c} = ?`).join(" AND ")}`, args: pk.map((c) => r[c]) });
  }
  if (apply) await writeAll(steps);
  return { table, source: src.length, ajouts: add, maj: upd, suppr: del };
}

// ---- KV ----

const cfToken = () => wrangler(["auth", "token"]).trim().split("\n").pop();
const cfAccount = () => wrangler(["whoami"]).match(/[0-9a-f]{32}/)[0];

async function kvAll() {
  const tok = cfToken(), acc = cfAccount();
  const base = `https://api.cloudflare.com/client/v4/accounts/${acc}/storage/kv/namespaces/${KV_ID}`;
  const H = { Authorization: `Bearer ${tok}`, "Content-Type": "application/json" };
  const keys = [];
  let cursor = "";
  do {
    const r = await (await fetch(`${base}/keys?limit=1000${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ""}`, { headers: H })).json();
    keys.push(...r.result);
    cursor = r.result_info?.cursor || "";
  } while (cursor);
  const out = [];
  for (let i = 0; i < keys.length; i += 100) {
    const part = keys.slice(i, i + 100);
    const r = await (await fetch(`${base}/bulk/get`, { method: "POST", headers: H, body: JSON.stringify({ keys: part.map((k) => k.name), type: "text", withMetadata: true }) })).json();
    if (!r.success) throw new Error("KV bulk/get : " + JSON.stringify(r.errors));
    for (const k of part) {
      const v = r.result.values[k.name];
      if (v === null || v === undefined) continue; // expirée entre-temps
      const value = typeof v === "object" && v !== null && "value" in v ? v.value : v;
      const meta = typeof v === "object" && v !== null && "metadata" in v ? v.metadata : k.metadata;
      out.push({ k: k.name, v: typeof value === "string" ? value : JSON.stringify(value), exp: k.expiration ? k.expiration * 1000 : null, meta: meta == null ? null : JSON.stringify(meta) });
    }
  }
  return out;
}

async function diffKv(apply) {
  const src = await kvAll();
  const dst = await q("SELECT k, v, exp, meta FROM kv_store");
  const have = new Map(dst.map((r) => [r.k, r]));
  const want = new Set();
  const steps = [];
  let add = 0, upd = 0, del = 0;
  for (const r of src) {
    want.add(r.k);
    const cur = have.get(r.k);
    if (cur && cur.v === r.v && (cur.exp ?? null) === r.exp && (cur.meta ?? null) === r.meta) continue;
    cur ? upd++ : add++;
    if (process.env.VERBOSE && add + upd <= 15) console.log(cur ? "maj" : "ajout", r.k, r.exp ? new Date(r.exp).toISOString() : "");
    steps.push({ sql: "INSERT OR REPLACE INTO kv_store (k, v, exp, meta) VALUES (?, ?, ?, ?)", args: [r.k, r.v, r.exp, r.meta] });
  }
  for (const k of have.keys()) if (!want.has(k)) { del++; steps.push({ sql: "DELETE FROM kv_store WHERE k = ?", args: [k] }); }
  if (apply) await writeAll(steps);
  return { table: "kv_store (KV)", source: src.length, ajouts: add, maj: upd, suppr: del };
}

async function schema() {
  for (const f of readdirSync("migrations").sort()) {
    // Idempotent : chaque migration ne crée que des objets « IF NOT EXISTS » ou est rejouée sur une base vide.
    const sql = readFileSync(`migrations/${f}`, "utf8").replace(/CREATE TABLE (?!IF NOT EXISTS)/gi, "CREATE TABLE IF NOT EXISTS ").replace(/CREATE INDEX (?!IF NOT EXISTS)/gi, "CREATE INDEX IF NOT EXISTS ");
    try { await conn.exec(sql); console.log("schéma :", f); } catch (e) { console.log("schéma :", f, "→", e.message); }
  }
  await conn.exec("CREATE TABLE IF NOT EXISTS kv_store (k TEXT PRIMARY KEY, v TEXT NOT NULL, exp INTEGER, meta TEXT); CREATE INDEX IF NOT EXISTS idx_kv_store_exp ON kv_store (exp) WHERE exp IS NOT NULL;");
  console.log("tables :", (await q("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")).map((r) => r.name).join(", "));
}

const cmd = process.argv[2];
if (cmd === "schema") await schema();
else if (cmd === "sync" || cmd === "verify") {
  const apply = cmd === "sync";
  const report = [];
  for (const t of TABLES) report.push(await diffTable(t, apply));
  report.push(await diffKv(apply));
  console.table(report);
  const ecarts = report.reduce((n, r) => n + r.ajouts + r.maj + r.suppr, 0);
  console.log(apply ? `appliqué : ${ecarts} écriture(s)` : `écarts restants : ${ecarts}`);
} else {
  console.log("usage : node scripts/turso-migrate.mjs schema|sync|verify");
  process.exit(1);
}
