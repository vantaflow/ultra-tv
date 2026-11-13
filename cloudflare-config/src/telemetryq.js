// Lectures D1 des vues d'administration (pagination / tri / filtres en SQL : GROUP BY, COUNT, LIMIT / OFFSET).
// Les fragments WHERE viennent de admin.js (valeurs toujours liées, colonnes de tri en liste blanche).

import { safe, maybePurge, eventFromRow } from "./telemetry.js";
import { fixedCrashCond } from "./admin.js";
import { paginate } from "./admin.js";

/** Page d'installations sans compte (requête construite par `anonQuery`) : { pg, rows }. */
export async function queryAnon(env, q, page, size) {
  return safe(env, async (db) => {
    const total = (await db.prepare(`SELECT COUNT(*) AS n FROM anon_pings ${q.where}`).bind(...q.args).first())?.n || 0;
    const pg = paginate(total, page, size);
    const rows = (await db.prepare(
      `SELECT first_seen, last_seen, version, country, web, platform, model FROM anon_pings ${q.where} ORDER BY ${q.order} LIMIT ? OFFSET ?`,
    ).bind(...q.args, size, pg.offset).all()).results;
    return { pg, rows: rows.map((r) => ({ first: r.first_seen, last: r.last_seen, version: r.version || "", country: r.country || null, web: r.web || 0, platform: r.platform || null, model: r.model || "" })) };
  }, { pg: paginate(0, 1, size), rows: [] });
}

/** Valeurs distinctes pour les listes de filtre : { versions, countries } des installations sans compte. */
export async function anonFacets(env) {
  return safe(env, async (db) => {
    const col = async (c) => (await db.prepare(`SELECT ${c} AS v, COUNT(*) AS n FROM anon_pings WHERE ${c} IS NOT NULL GROUP BY ${c} ORDER BY n DESC LIMIT 200`).all()).results.map((r) => r.v);
    return { versions: await col("version"), countries: await col("country") };
  }, { versions: [], countries: [] });
}

/** Chiffres de la vue d'ensemble pour les installations sans compte. « Aujourd'hui » = depuis 00 h UTC. */
export async function anonOverview(env, now = Date.now()) {
  const empty = { total: 0, a24: 0, a7: 0, a30: 0, new1: 0, new7: 0, web: 0, byPlatform: [], byVersion: [], byCountry: [] };
  return safe(env, async (db) => {
    const day = 86_400_000;
    const today = now - (now % day);
    const c = await db.prepare(
      "SELECT COUNT(*) AS total, "
      + "COALESCE(SUM(last_seen >= ?), 0) AS a24, COALESCE(SUM(last_seen >= ?), 0) AS a7, COALESCE(SUM(last_seen >= ?), 0) AS a30, "
      + "COALESCE(SUM(first_seen >= ?), 0) AS new1, COALESCE(SUM(first_seen >= ?), 0) AS new7, COALESCE(SUM(web), 0) AS web FROM anon_pings",
    ).bind(now - day, now - 7 * day, now - 30 * day, today, now - 7 * day).first();
    const grp = async (col) => (await db.prepare(`SELECT ${col} AS k, COUNT(*) AS n FROM anon_pings GROUP BY ${col} ORDER BY n DESC LIMIT 300`).all()).results.map((r) => [r.k, r.n]);
    return { ...c, byPlatform: await grp("platform"), byVersion: await grp("version"), byCountry: await grp("country") };
  }, empty);
}

/** Nombre de journaux / plantages [kind] depuis [since] (ms). */
export async function countEvents(env, kind, since) {
  // Plantages : seulement ceux qui ne sont pas déjà corrigés (tuile « plantages (24 h) »).
  const fx = kind === "crash" ? fixedCrashCond() : null;
  const sql = `SELECT COUNT(*) AS n FROM events WHERE kind = ? AND ts >= ?${fx ? ` AND NOT ${fx.sql}` : ""}`;
  return safe(env, async (db) => (await db.prepare(sql).bind(kind, since, ...(fx ? fx.args : [])).first())?.n || 0, 0);
}

/** Page de journaux / plantages (requête construite par `eventsQuery`) : { pg, rows } au format des entrées KV d'origine. */
export async function queryEvents(env, kind, q, page, size) {
  await maybePurge(env);
  return safe(env, async (db) => {
    const total = (await db.prepare(`SELECT COUNT(*) AS n FROM events ${q.where}`).bind(...q.args).first())?.n || 0;
    const pg = paginate(total, page, size);
    const rows = (await db.prepare(`SELECT * FROM events ${q.where} ORDER BY ${q.order} LIMIT ? OFFSET ?`).bind(...q.args, size, pg.offset).all()).results;
    return { pg, rows: rows.map((r) => eventFromRow(kind, r)) };
  }, { pg: paginate(0, 1, size), rows: [] });
}

/** Versions et pays distincts d'un type d'événement (listes de filtre). */
export async function eventFacets(env, kind) {
  return safe(env, async (db) => {
    const col = async (c) => (await db.prepare(`SELECT ${c} AS v, COUNT(*) AS n FROM events WHERE kind = ? AND ${c} IS NOT NULL GROUP BY ${c} ORDER BY n DESC LIMIT 200`).bind(kind).all()).results.map((r) => r.v);
    return { versions: await col("version"), countries: await col("country") };
  }, { versions: [], countries: [] });
}
