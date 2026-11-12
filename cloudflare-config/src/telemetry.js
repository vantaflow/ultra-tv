// Télémétrie en D1 (binding `DB`) : journaux / plantages, relevés anonymes, « dernière activité » des appareils.
// Pourquoi : KV gratuit = 1 000 écritures par jour (erreur 10048) ; ces écritures très fréquentes sont donc en D1
// (100 000 écritures par jour). Comptes, appareils et fournisseurs restent en KV (faible fréquence).
//
// Dégradation silencieuse : sans binding `DB`, ou si D1 échoue, la télémétrie est perdue mais la requête n'échoue
// jamais, et on n'écrit JAMAIS en repli dans KV (ce serait reconsommer le quota).
//
// Transition : les anciennes lignes KV (`event:*`, `crash:*`, `anon:*`) ne sont plus lues par les vues
// d'administration (elles expirent seules, au plus 7 / 30 / 180 jours). Seule exception : à la première insertion
// d'une installation anonyme, on récupère `first` / `web` de l'ancienne ligne KV (lecture seulement).

export const EVENT_TTL_S = 7 * 24 * 3600;
export const CRASH_TTL_S = 30 * 24 * 3600;
export const ANON_TTL_S = 180 * 24 * 3600;
export const SEEN_TTL_S = 180 * 24 * 3600;
export const SEEN_REFRESH_MS = 3600_000;
export const ANON_REFRESH_MS = 6 * 3600_000;
/** Probabilité de purger les lignes expirées à chaque insertion de journal (et à chaque ouverture des pages). */
const PURGE_PROBABILITY = 0.02;

/** Exécute [fn] ; toute erreur (D1 absent, quota, SQL) est avalée et renvoie [fallback]. */
export async function safe(env, fn, fallback = null) {
  if (!env?.DB) return fallback;
  try { return await fn(env.DB); } catch (e) {
    console.error("telemetrie D1:", e?.message);
    return fallback;
  }
}

/** Supprime ce qui a dépassé sa durée de conservation (appelé aussi par le cron `scheduled`). */
export async function purgeTelemetry(env, now = Date.now()) {
  await safe(env, (db) => db.batch([
    db.prepare("DELETE FROM events WHERE kind = 'event' AND ts < ?").bind(now - EVENT_TTL_S * 1000),
    db.prepare("DELETE FROM events WHERE kind = 'crash' AND ts < ?").bind(now - CRASH_TTL_S * 1000),
    db.prepare("DELETE FROM anon_pings WHERE last_seen < ?").bind(now - ANON_TTL_S * 1000),
    db.prepare("DELETE FROM device_seen WHERE seen < ?").bind(now - SEEN_TTL_S * 1000),
  ]));
}

export const maybePurge = (env) => (Math.random() < PURGE_PROBABILITY ? purgeTelemetry(env) : undefined);

/** Enregistre un journal ou un plantage ([entry] = objet construit par `ingest`). */
export async function recordEvent(env, kind, entry) {
  await safe(env, (db) => db.prepare(
    "INSERT INTO events (kind, ts, device_id, mac, version, version_code, device, acct, country, level, tag, message, android_sdk, stack) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
  ).bind(kind, entry.ts, entry.deviceId ?? null, entry.mac ?? null, entry.version ?? null, entry.versionCode ?? null, entry.device ?? null,
    entry.acct ?? null, entry.country ?? null, entry.level ?? null, entry.tag ?? null, entry.message ?? null, entry.androidSdk ?? null, entry.stack ?? null).run());
  await maybePurge(env);
}

/** Ligne D1 `events` → entrée de journal / plantage (même format que les anciennes entrées KV). */
export const eventFromRow = (kind, r) => ({
  v: 2, ts: r.ts, deviceId: r.device_id, mac: r.mac, version: r.version, versionCode: r.version_code, device: r.device,
  acct: r.acct, country: r.country,
  ...(kind === "crash" ? { androidSdk: r.android_sdk, stack: r.stack } : { level: r.level, tag: r.tag, message: r.message }),
});


/** Plateformes acceptées pour une installation sans compte (liste blanche). */
export const ANON_PLATFORMS = ["android-tv", "android-mobile", "windows", "macos", "linux"];
export const PLATFORM_LABELS = { "android-tv": "Android TV", "android-mobile": "Android mobile", windows: "Windows", macos: "macOS", linux: "Linux" };

/** Plateforme déclarée (liste blanche) ou null. */
export const platformOf = (raw) => (typeof raw === "string" && ANON_PLATFORMS.includes(raw.trim()) ? raw.trim() : null);

/** Modèle d'appareil : texte court nettoyé (contrôles et guillemets retirés, 64 caractères au plus) ou null. */
export function modelOf(raw) {
  if (typeof raw !== "string") return null;
  // eslint-disable-next-line no-control-regex
  const m = raw.replace(/[\u0000-\u001f\u007f<>"'`]/g, " ").replace(/\s+/g, " ").trim().slice(0, 64);
  return m || null;
}

/**
 * Relevé d'une installation anonyme. Au plus une écriture par 6 h et par installation (sauf nouvelle version,
 * nouvelle plateforme ou envoi web). [legacy] : lit l'ancienne ligne KV pour une installation encore inconnue de D1.
 */
export async function anonTouch(env, fingerprint, { version = null, web = 0, country = null, platform = null, model = null, legacy = null } = {}) {
  await safe(env, async (db) => {
    const now = Date.now();
    const cur = await db.prepare("SELECT first_seen, last_seen, version, country, web, platform, model FROM anon_pings WHERE fingerprint = ?").bind(fingerprint).first();
    const ver = version || cur?.version || null;
    const plat = platform || cur?.platform || null;
    if (cur && !web && now - cur.last_seen < ANON_REFRESH_MS && cur.version === ver && cur.platform === plat) return;
    let old = null;
    if (!cur && legacy) { try { old = await legacy(); } catch { old = null; } }
    await db.prepare(
      "INSERT INTO anon_pings (fingerprint, first_seen, last_seen, version, country, web, platform, model) VALUES (?,?,?,?,?,?,?,?) "
      + "ON CONFLICT(fingerprint) DO UPDATE SET last_seen = excluded.last_seen, version = excluded.version, country = excluded.country, web = excluded.web, platform = excluded.platform, model = excluded.model",
    ).bind(fingerprint, cur?.first_seen || old?.f || now, now, ver || old?.v || null, country || cur?.country || old?.c || null,
      (cur?.web ?? old?.w ?? 0) + web, plat, model || cur?.model || null).run();
  });
}

/** Marque l'appareil [hash] comme vu (au plus une écriture par heure), sans toucher à KV. */
export async function touchDeviceSeen(env, hash) {
  await safe(env, (db) => db.prepare(
    "INSERT INTO device_seen (hash, seen) VALUES (?, ?) ON CONFLICT(hash) DO UPDATE SET seen = excluded.seen WHERE device_seen.seen < excluded.seen - ?",
  ).bind(hash, Date.now(), SEEN_REFRESH_MS).run());
}

/** Dernière activité de l'appareil [hash] : D1, sinon l'ancienne valeur [fallback] (champ `seen` de `dev:<hash>`). */
export async function deviceSeen(env, hash, fallback = 0) {
  const row = await safe(env, (db) => db.prepare("SELECT seen FROM device_seen WHERE hash = ?").bind(hash).first());
  return row?.seen || fallback || 0;
}

/** Oublie l'appareil [hash] (révocation / rotation du jeton). */
export async function forgetDeviceSeen(env, hash) {
  await safe(env, (db) => db.prepare("DELETE FROM device_seen WHERE hash = ?").bind(hash).run());
}
