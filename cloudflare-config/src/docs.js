// Stockage des documents du Worker (comptes, appareils, jetons, caches) dans D1, avec la même forme que KV.
// Pourquoi : KV gratuit = 1 000 écritures/jour (atteint : appairage impossible pour tout le monde) ; D1 gratuit = 100 000.
// Migration au fil de l'eau : lecture D1 d'abord, sinon KV ; un compte ou un appareil lu dans KV est recopié dans D1.
// D1 indisponible : on retombe sur KV (lecture et écriture), le service continue.

const MIGRATE = ["acct:", "dev:"];

async function d1(env, fn) {
  if (!env?.DB) return undefined;
  try { return await fn(env.DB); } catch (e) { console.error("docs D1:", e?.message); return undefined; }
}

/** Valeur D1 : chaîne, null (absente ou expirée), undefined (D1 indisponible ou jamais écrite → lire KV). */
async function d1Get(env, k, now = Date.now()) {
  const row = await d1(env, (db) => db.prepare("SELECT v, exp FROM kv_docs WHERE k = ?").bind(k).first());
  if (row === undefined) return undefined;
  if (row === null) return undefined;
  if (row.exp && row.exp < now) return null;
  return row.v;
}

async function d1Put(env, k, v, ttlS) {
  const exp = ttlS ? Date.now() + ttlS * 1000 : null;
  const ok = await d1(env, (db) => db.prepare("INSERT INTO kv_docs (k, v, exp) VALUES (?, ?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v, exp = excluded.exp").bind(k, v, exp).run());
  return ok !== undefined;
}

export const docs = {
  async get(env, k) {
    const v = await d1Get(env, k);
    if (v !== undefined) return v;
    const kv = await env.CONFIG.get(k);
    if (kv !== null && MIGRATE.some((p) => k.startsWith(p))) await d1Put(env, k, kv, null);
    return kv;
  },
  /** opts.expirationTtl en secondes, comme KV. */
  async put(env, k, v, opts = {}) {
    if (!(await d1Put(env, k, v, opts.expirationTtl))) await env.CONFIG.put(k, v, opts);
  },
  /** Supprime partout ; dans KV seulement si la clé y est encore (une suppression KV compte comme une écriture). */
  async delete(env, k) {
    await d1(env, (db) => db.prepare("DELETE FROM kv_docs WHERE k = ?").bind(k).run());
    if ((await env.CONFIG.get(k)) !== null) await env.CONFIG.delete(k);
  },
  /**
   * Plusieurs documents en une requête par tranche de 100 (au lieu d'une par clé) : Map clé → valeur (absentes omises).
   * Indispensable avec Turso : chaque requête est une sous-requête, plafonnée à 50 par appel du Worker gratuit.
   */
  async getMany(env, keys) {
    const out = new Map();
    const now = Date.now();
    for (let i = 0; i < keys.length; i += 100) {
      const part = keys.slice(i, i + 100);
      const rows = await d1(env, (db) => db.prepare(`SELECT k, v, exp FROM kv_docs WHERE k IN (${part.map(() => "?").join(",")})`).bind(...part).all());
      for (const r of rows?.results || []) if (!r.exp || r.exp >= now) out.set(r.k, r.v);
    }
    const missing = keys.filter((k) => !out.has(k));
    if (missing.length) {
      if (typeof env.CONFIG.getMany === "function") for (const [k, v] of await env.CONFIG.getMany(missing)) out.set(k, v);
      else for (const k of missing) { const v = await env.CONFIG.get(k); if (v !== null) out.set(k, v); }
    }
    return out;
  },
  /** Supprime plusieurs documents (une requête par tranche de 100 ; KV : seulement les clés encore présentes). */
  async deleteMany(env, keys) {
    if (!keys.length) return;
    for (let i = 0; i < keys.length; i += 100) {
      const part = keys.slice(i, i + 100);
      await d1(env, (db) => db.prepare(`DELETE FROM kv_docs WHERE k IN (${part.map(() => "?").join(",")})`).bind(...part).run());
    }
    if (typeof env.CONFIG.deleteMany === "function") await env.CONFIG.deleteMany(keys);
    else for (const k of keys) if ((await env.CONFIG.get(k)) !== null) await env.CONFIG.delete(k);
  },
  /** Clés d'un préfixe (D1 + KV pas encore migré), sans doublon ; `max` borne le résultat. */
  async keys(env, prefix, max = 5000) {
    const out = new Set();
    const rows = await d1(env, (db) => db.prepare("SELECT k FROM kv_docs WHERE substr(k, 1, ?) = ? LIMIT ?").bind(prefix.length, prefix, max).all());
    for (const r of rows?.results || []) out.add(r.k);
    let cursor;
    do {
      const page = await env.CONFIG.list({ prefix, cursor });
      for (const x of page.keys) out.add(x.name);
      cursor = page.list_complete ? undefined : page.cursor;
    } while (cursor && out.size < max);
    return [...out];
  },
  /** Purge des documents expirés (cron quotidien). */
  async purgeExpired(env, now = Date.now()) {
    await d1(env, (db) => db.prepare("DELETE FROM kv_docs WHERE exp IS NOT NULL AND exp < ?").bind(now).run());
  },
};
