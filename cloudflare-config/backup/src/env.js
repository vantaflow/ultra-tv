// Couche `env` du serveur de secours : fabrique, à partir des variables d'environnement Vercel, l'environnement que le
// Worker attend (liaisons Turso, limiteurs, Durable Objects absents). Aucune valeur n'est jamais journalisée.

import { withStorage } from "../../src/turso.js";

/** Variables obligatoires ; sans elles, toutes les routes répondent 503 « non configuré ». */
export const REQUIRED_VARS = ["TURSO_URL", "TURSO_AUTH_TOKEN", "PROVIDER_ENC_KEY"];

/** Noms des variables obligatoires absentes ou vides (jamais leurs valeurs). */
export function missingVars(p) {
  return REQUIRED_VARS.filter((k) => typeof p?.[k] !== "string" || p[k].trim() === "");
}

/** Limiteur simple en mémoire (fenêtre glissante), même forme que les liaisons natives : `limit({ key }) → { success }`. */
export function makeLimiter(limit, periodMs = 60_000, now = () => Date.now()) {
  const hits = new Map();
  return {
    async limit({ key }) {
      const t = now();
      const recent = (hits.get(key) || []).filter((x) => t - x < periodMs);
      if (recent.length >= limit) { hits.set(key, recent); return { success: false }; }
      recent.push(t);
      hits.set(key, recent);
      if (hits.size > 5000) for (const [k, v] of hits) if (!v.some((x) => t - x < periodMs)) hits.delete(k);
      return { success: true };
    },
  };
}

// Les limiteurs sont gardés au niveau du module (une instance de fonction = un compteur), pas recréés par requête.
let limiters = null;
function sharedLimiters() {
  return limiters || (limiters = { RL_10: makeLimiter(10), RL_30: makeLimiter(30), RL_60: makeLimiter(60), RL_120: makeLimiter(120) });
}

/** Durable Objects absents : tout appel échoue, et le Worker traite l'échec comme « limiteur / verrou indisponible ». */
export const NO_DURABLE_OBJECTS = {
  idFromName(name) { return name; },
  get() { throw new Error("durable_objects_unavailable"); },
};

export class ReadOnlyError extends Error {
  constructor() { super("backup_read_only"); this.name = "ReadOnlyError"; }
}

const READ_SQL = /^\s*(select|pragma|with)\b/i;
const refuse = async () => { throw new ReadOnlyError(); };

/** Base en lecture seule : seules les requêtes SELECT / PRAGMA / WITH passent ; toute écriture est rejetée. */
export function readOnlyDb(db) {
  return {
    get queries() { return db.queries; },
    prepare(sql) {
      if (READ_SQL.test(sql)) return db.prepare(sql);
      const blocked = { blocked: true, bind() { return blocked; }, first: refuse, all: refuse, run: refuse, raw: refuse };
      return blocked;
    },
    async batch(stmts) {
      if (stmts.some((s) => s.blocked)) throw new ReadOnlyError();
      return db.batch(stmts);
    },
    exec: refuse,
  };
}

/** KV en lecture seule : lectures et listes passent, put / delete / purge sont rejetés. */
export function readOnlyKv(kv) {
  return {
    get: (...a) => kv.get(...a),
    getWithMetadata: (...a) => kv.getWithMetadata(...a),
    getMany: (...a) => kv.getMany(...a),
    list: (...a) => kv.list(...a),
    put: refuse, delete: refuse, deleteMany: refuse, purgeExpired: refuse,
  };
}

/**
 * Environnement d'une requête. `connect` : pilote Turso (injecté en test). Retourne null si la configuration est incomplète
 * (le gestionnaire répond alors 503 « non configuré »).
 */
export function buildEnv(p, { connect, limitersOverride } = {}) {
  if (missingVars(p).length) return null;
  const base = {
    STORAGE_MODE: "turso",
    TURSO_URL: p.TURSO_URL.trim(),
    TURSO_AUTH_TOKEN: p.TURSO_AUTH_TOKEN.trim(),
    PROVIDER_ENC_KEY: p.PROVIDER_ENC_KEY.trim(),
    ...(p.PROVIDER_ENC_KEY_PREVIOUS ? { PROVIDER_ENC_KEY_PREVIOUS: p.PROVIDER_ENC_KEY_PREVIOUS.trim() } : {}),
    ...(p.SITE_HOST ? { SITE_HOST: p.SITE_HOST.trim() } : {}),
    BACKUP_MODE: "1",
    GUARD: NO_DURABLE_OBJECTS,
    ...(limitersOverride || sharedLimiters()),
  };
  const withDb = withStorage(base, connect);
  // STORAGE est posé par withStorage : le Worker ne reconstruit pas l'environnement (il rendrait les écritures possibles).
  return { ...withDb, DB: readOnlyDb(withDb.DB), CONFIG: readOnlyKv(withDb.CONFIG) };
}
