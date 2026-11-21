// Réécriture des anciennes lignes de télémétrie avec le nettoyage actuel (`sanitizeLog`), à lancer APRÈS le déploiement
// du Worker corrigé. Variante « réécrire » (pas de suppression) : toutes les lignes `events` antérieures à la date du
// déploiement repassent par `sanitizeLog`, même celles qu'aucun motif SQL ne repère ; les plantages utiles sont gardés.
//
//   TURSO_URL=libsql://… TURSO_AUTH_TOKEN=… LOG_HOST_SALT=… \
//     node scripts/resanitize-events.mjs --before 2026-11-22T10:00:00Z            compte seulement (par défaut)
//   … node scripts/resanitize-events.mjs --before 2026-11-22T10:00:00Z --apply    écrit
//
// Options : --batch <n> (50 par défaut, 100 au plus), --after-id <id> (reprise après une coupure : le dernier id traité
// est affiché à chaque lot), --no-host-fingerprint (sans LOG_HOST_SALT : hôtes écrits <host>, sans empreinte).
// Base locale de test : TURSO_URL=file:/chemin/base.sqlite (node:sqlite, Node 22.5 ou plus), sans jeton.
//
// Garanties :
// - refuse de tourner sans --before (date ISO valide, pas dans le futur) : les lignes écrites par le nouveau Worker ne
//   sont jamais relues ;
// - sans --apply : aucune écriture ;
// - idempotent (sanitizeLog(sanitizeLog(x)) = sanitizeLog(x)) et relançable : une seconde passe ne change rien ;
// - n'affiche JAMAIS le contenu d'une ligne : seulement des compteurs et des identifiants numériques ;
// - par petits lots (une lecture puis au plus une écriture groupée par lot, dans une transaction) ;
// - vide aussi la colonne `mac` (plus jamais remplie par le Worker).
//
// LOG_HOST_SALT doit être LE MÊME secret que celui du Worker (sinon les empreintes des anciennes et des nouvelles lignes
// diffèrent). Ne jamais le passer en argument de la ligne de commande (historique du terminal) : variable d'environnement.

import { sanitizeLog } from "../src/sanitize.js";

/** Champs réécrits et longueur maximale de chacun (mêmes bornes qu'à l'ingestion). */
const FIELDS = { message: 4000, stack: 32 * 1024, tag: 32, device: 128, version: 32 };
const SELECT = "SELECT id, kind, message, stack, tag, device, version, mac FROM events WHERE ts < ? AND id > ? ORDER BY id LIMIT ?";
const UPDATE = "UPDATE events SET message = ?, stack = ?, tag = ?, device = ?, version = ?, mac = NULL WHERE id = ? AND ts < ?";

/** Arguments de la ligne de commande ; lève une erreur lisible si --before manque ou est invalide. */
export function parseArgs(argv, now = Date.now()) {
  const opt = { apply: false, batch: 50, afterId: 0, hostFingerprint: true, before: null };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--apply") opt.apply = true;
    else if (a === "--dry-run") opt.apply = false;
    else if (a === "--no-host-fingerprint") opt.hostFingerprint = false;
    else if (a === "--before") opt.before = argv[++i];
    else if (a === "--batch") opt.batch = Number(argv[++i]);
    else if (a === "--after-id") opt.afterId = Number(argv[++i]);
    else throw new Error(`option inconnue : ${a}`);
  }
  if (!opt.before) throw new Error("--before <date ISO du déploiement> est obligatoire (ex. --before 2026-11-22T10:00:00Z)");
  if (!/^\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?(Z|[+-]\d{2}:\d{2}))?$/.test(opt.before)) throw new Error("--before : date ISO attendue (2026-11-22 ou 2026-11-22T10:00:00Z)");
  opt.beforeMs = Date.parse(opt.before);
  if (!Number.isFinite(opt.beforeMs)) throw new Error("--before : date invalide");
  if (opt.beforeMs > now) throw new Error("--before : date dans le futur refusée (elle relirait les lignes du nouveau Worker)");
  if (!Number.isInteger(opt.batch) || opt.batch < 1 || opt.batch > 100) throw new Error("--batch : entier de 1 à 100");
  if (!Number.isInteger(opt.afterId) || opt.afterId < 0) throw new Error("--after-id : entier positif");
  return opt;
}

/** Ligne nettoyée : `{ changed, values, fields }` (fields = noms des champs modifiés, jamais leur contenu). */
export function resanitizeRow(row, hostKey) {
  const values = {}, fields = [];
  for (const [f, max] of Object.entries(FIELDS)) {
    const v = row[f];
    if (typeof v !== "string") { values[f] = v ?? null; continue; }
    // Jamais de coupe supplémentaire : la borne est au moins la longueur actuelle.
    const out = sanitizeLog(v, Math.max(max, v.length), { hostKey });
    values[f] = f === "tag" && !out ? "app" : out;
    if (values[f] !== v) fields.push(f);
  }
  if (row.mac != null) fields.push("mac");
  return { changed: fields.length > 0, values, fields };
}

/**
 * Parcourt `events` (ts < beforeMs) par lots, dans l'ordre des id. [db] : interface D1 (`prepare/bind/all`, `batch`),
 * par exemple `TursoD1` (src/turso.js). [log] ne reçoit que des compteurs.
 */
export async function resanitize(db, { beforeMs, apply = false, batch = 50, afterId = 0, hostKey = null, log = () => {} }) {
  const stats = { scanned: 0, changed: 0, written: 0, byKind: {}, byField: {}, lastId: afterId };
  let cursor = afterId;
  for (;;) {
    const { results } = await db.prepare(SELECT).bind(beforeMs, cursor, batch).all();
    if (!results.length) break;
    const updates = [];
    for (const row of results) {
      stats.scanned++;
      const r = resanitizeRow(row, hostKey);
      if (!r.changed) continue;
      stats.changed++;
      stats.byKind[row.kind] = (stats.byKind[row.kind] || 0) + 1;
      for (const f of r.fields) stats.byField[f] = (stats.byField[f] || 0) + 1;
      const v = r.values;
      updates.push(db.prepare(UPDATE).bind(v.message, v.stack, v.tag, v.device, v.version, row.id, beforeMs));
    }
    if (apply && updates.length) {
      await db.batch(updates); // un seul envoi, atomique
      stats.written += updates.length;
    }
    cursor = results[results.length - 1].id;
    stats.lastId = cursor;
    log(`lot jusqu'à l'id ${cursor} : ${results.length} lue(s), ${updates.length} à réécrire${apply ? ", écrites" : ""} (total : ${stats.scanned} lues, ${stats.changed} à réécrire)`);
    if (results.length < batch) break;
  }
  return stats;
}

/** Base SQLite locale (tests manuels) avec la même interface que D1 : prepare/bind/all/run, batch transactionnel. */
async function sqliteD1(path) {
  const { DatabaseSync } = await import("node:sqlite");
  const sdb = new DatabaseSync(path);
  const stmt = (sql, args = []) => ({
    bind: (...a) => stmt(sql, a),
    all: async () => ({ results: sdb.prepare(sql).all(...args) }),
    run: async () => { const r = sdb.prepare(sql).run(...args); return { meta: { changes: r.changes } }; },
    _exec: () => sdb.prepare(sql).run(...args),
  });
  return {
    prepare: (sql) => stmt(sql),
    async batch(list) {
      sdb.exec("BEGIN IMMEDIATE");
      try { for (const s of list) s._exec(); sdb.exec("COMMIT"); } catch (e) { sdb.exec("ROLLBACK"); throw e; }
      return [];
    },
  };
}

async function main() {
  let opt;
  try { opt = parseArgs(process.argv.slice(2)); } catch (e) { console.error(e.message); process.exit(2); }
  const url = process.env.TURSO_URL;
  if (!url) { console.error("TURSO_URL manquant (libsql://… ou file:/chemin/base.sqlite)"); process.exit(2); }
  const hostKey = process.env.LOG_HOST_SALT || null;
  if (opt.hostFingerprint && (!hostKey || hostKey.length < 32)) {
    console.error("LOG_HOST_SALT manquant ou trop court : donnez le même secret que le Worker, ou --no-host-fingerprint");
    process.exit(2);
  }
  let db;
  if (url.startsWith("file:")) db = await sqliteD1(url.slice("file:".length));
  else {
    if (!process.env.TURSO_AUTH_TOKEN) { console.error("TURSO_AUTH_TOKEN manquant"); process.exit(2); }
    const { connect } = await import("@tursodatabase/serverless");
    const { TursoD1 } = await import("../src/turso.js");
    db = new TursoD1(connect({ url, authToken: process.env.TURSO_AUTH_TOKEN }));
  }
  console.log(`${opt.apply ? "ÉCRITURE" : "COMPTE SEUL (--apply pour écrire)"} : lignes antérieures à ${new Date(opt.beforeMs).toISOString()}, lots de ${opt.batch}, après l'id ${opt.afterId}`);
  try {
    const s = await resanitize(db, { ...opt, hostKey: opt.hostFingerprint ? hostKey : null, log: (m) => console.log(m) });
    console.log(JSON.stringify({ apply: opt.apply, scanned: s.scanned, changed: s.changed, written: s.written, byKind: s.byKind, byField: s.byField, lastId: s.lastId }));
  } catch (e) {
    // Message d'erreur du pilote seulement (jamais une ligne) ; reprise possible avec --after-id.
    console.error("échec :", sanitizeLog(String(e?.message ?? e), 300), "— relancer avec --after-id <dernier id affiché>");
    process.exit(1);
  }
}

// Lancé en ligne de commande (pas importé par un test).
if (typeof process !== "undefined" && process.argv?.[1]?.endsWith("resanitize-events.mjs")) await main();
