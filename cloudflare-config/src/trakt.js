// Trakt : connexion depuis l'espace client (OAuth 2 + PKCE, client public : pas de secret), puis « scrobble » relayé
// par le Worker. Les appareils ne voient jamais de jeton Trakt : ils disent seulement au Worker ce qu'ils lisent.
//   POST /api/device/trakt/scrobble {action, progress, kind, title, year?, tmdb?, season?, episode?}
// L'identifiant TMDB manquant est retrouvé par le Worker (titre + année) avant l'envoi à Trakt.

import { b64uEncode, randomToken } from "./crypto.js";

export const TRAKT_AUTHORIZE = "https://trakt.tv/oauth/authorize";
const TRAKT_API = "https://api.trakt.tv";
const TMDB_API = "https://api.themoviedb.org/3/";
const USER_AGENT = "UltraTV/1.0";
const ACTIONS = new Set(["start", "pause", "stop"]);

export const TRAKT_STATE_TTL_S = 600;

/** Paire PKCE : vérificateur aléatoire + défi S256. */
export async function pkcePair() {
  const verifier = randomToken(48);
  const challenge = b64uEncode(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
  return { verifier, challenge };
}

export function authorizeUrl(clientId, redirectUri, state, challenge) {
  const u = new URL(TRAKT_AUTHORIZE);
  u.searchParams.set("response_type", "code");
  u.searchParams.set("client_id", clientId);
  u.searchParams.set("redirect_uri", redirectUri);
  u.searchParams.set("state", state);
  u.searchParams.set("code_challenge", challenge);
  u.searchParams.set("code_challenge_method", "S256");
  return u.toString();
}

const apiHeaders = (clientId, access) => ({
  "content-type": "application/json", accept: "application/json", "user-agent": USER_AGENT,
  "trakt-api-version": "2", "trakt-api-key": clientId,
  ...(access ? { authorization: `Bearer ${access}` } : {}),
});

/** Jetons normalisés depuis une réponse /oauth/token, ou null. */
function tokensOf(d, now) {
  if (!d || typeof d.access_token !== "string" || typeof d.refresh_token !== "string") return null;
  const life = Number(d.expires_in) > 0 ? Number(d.expires_in) : 7 * 86400;
  return { access: d.access_token.slice(0, 512), refresh: d.refresh_token.slice(0, 512), expiresAt: now + life * 1000 };
}

/** Échange du code d'autorisation (PKCE) → jetons, ou { error }. */
export async function exchangeCode(clientId, redirectUri, code, verifier, fetchFn = fetch, now = Date.now()) {
  let r;
  try {
    r = await fetchFn(`${TRAKT_API}/oauth/token`, {
      method: "POST", redirect: "manual", headers: apiHeaders(clientId),
      body: JSON.stringify({ code, client_id: clientId, redirect_uri: redirectUri, grant_type: "authorization_code", code_verifier: verifier }),
    });
  } catch { return { error: "upstream" }; }
  if (r.status === 400 || r.status === 401 || r.status === 403) return { error: "denied" };
  if (r.status !== 200) return { error: "upstream" };
  const t = tokensOf(await r.json().catch(() => null), now);
  return t || { error: "upstream" };
}

/** Renouvellement → jetons, { error: "revoked" } (reconnexion nécessaire) ou { error: "upstream" }. */
export async function refreshTokens(clientId, redirectUri, refresh, fetchFn = fetch, now = Date.now()) {
  let r;
  try {
    r = await fetchFn(`${TRAKT_API}/oauth/token`, {
      method: "POST", redirect: "manual", headers: apiHeaders(clientId),
      body: JSON.stringify({ refresh_token: refresh, client_id: clientId, redirect_uri: redirectUri, grant_type: "refresh_token" }),
    });
  } catch { return { error: "upstream" }; }
  if (r.status === 400 || r.status === 401 || r.status === 403) return { error: "revoked" };
  if (r.status !== 200) return { error: "upstream" };
  const t = tokensOf(await r.json().catch(() => null), now);
  return t || { error: "upstream" };
}

/** Nom d'utilisateur Trakt (affichage dans l'espace client), ou "" si indisponible. */
export async function traktUsername(clientId, access, fetchFn = fetch) {
  try {
    const r = await fetchFn(`${TRAKT_API}/users/settings`, { headers: apiHeaders(clientId, access), redirect: "manual" });
    if (r.status !== 200) return "";
    const d = await r.json();
    return String(d?.user?.username || d?.user?.name || "").slice(0, 60);
  } catch { return ""; }
}

/** Révocation au déliage (au mieux : un échec n'empêche pas d'oublier les jetons). */
export async function revokeToken(clientId, access, fetchFn = fetch) {
  try {
    await fetchFn(`${TRAKT_API}/oauth/revoke`, { method: "POST", redirect: "manual", headers: apiHeaders(clientId), body: JSON.stringify({ token: access, client_id: clientId }) });
  } catch { /* au mieux */ }
}

const cleanText = (v, max) => (typeof v === "string" ? v.replace(/[\u0000-\u001f]/g, " ").trim().slice(0, max) : "");
const intIn = (v, lo, hi) => { const n = Number(v); return Number.isInteger(n) && n >= lo && n <= hi ? n : null; };

/** Corps de scrobble envoyé par un appareil, validé. null si invalide. */
export function parseScrobble(body) {
  if (!body || typeof body !== "object") return null;
  if (!ACTIONS.has(body.action)) return null;
  const progress = Number(body.progress);
  if (!Number.isFinite(progress) || progress < 0 || progress > 100) return null;
  const kind = body.kind === "movie" || body.kind === "episode" ? body.kind : null;
  const title = cleanText(body.title, 200);
  if (!kind || !title) return null;
  const out = { action: body.action, progress: Math.round(progress * 100) / 100, kind, title, year: intIn(body.year, 1900, 2100), tmdb: intIn(body.tmdb, 1, 99_999_999) };
  if (kind === "episode") {
    out.season = intIn(body.season, 0, 500);
    out.episode = intIn(body.episode, 0, 20_000);
    if (out.season === null || out.episode === null) return null;
  }
  return out;
}

/**
 * Titre IPTV → requête TMDB : retire les préfixes de catégorie (« FR - », « |FR| »), les mentions de qualité et
 * l'année entre parenthèses.
 */
export function searchTitle(raw) {
  return raw
    .replace(/^\s*(\|[^|]{1,12}\||\[[^\]]{1,12}\]|[A-Z]{2,4}\s*[-:|]\s)/, "")
    .replace(/\b(4K|UHD|FHD|HD|SD|HEVC|H\.?26[45]|x26[45]|MULTI|VOSTFR|VF|VFF|VO|TRUEFRENCH|FRENCH)\b/gi, " ")
    .replace(/\(\s*(19|20)\d{2}\s*\)|\b(19|20)\d{2}\s*$/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120);
}

/** Identifiant TMDB par recherche (titre + année), mis en cache 30 jours. null si introuvable ou TMDB non configuré. */
export async function resolveTmdb(env, kind, title, year, { fetchFn = fetch, cache = globalThis.caches?.default } = {}) {
  if (!env.TMDB_READ_TOKEN && !env.TMDB_API_KEY) return null;
  const q = searchTitle(title);
  if (!q) return null;
  const path = kind === "movie" ? "search/movie" : "search/tv";
  const up = new URL(TMDB_API + path);
  up.searchParams.set("query", q);
  if (year) up.searchParams.set(kind === "movie" ? "year" : "first_air_date_year", String(year));
  const key = new Request(`https://trakt-tmdb.invalid/${path}?${up.searchParams}`);
  const hit = cache ? await cache.match(key) : undefined;
  if (hit) { const id = Number(await hit.text()); return id > 0 ? id : null; }
  const headers = { accept: "application/json" };
  if (env.TMDB_READ_TOKEN) headers.authorization = `Bearer ${env.TMDB_READ_TOKEN}`;
  else up.searchParams.set("api_key", env.TMDB_API_KEY);
  let id = 0;
  try {
    const r = await fetchFn(up.toString(), { headers, redirect: "manual" });
    if (r.status !== 200) return null;
    const first = (await r.json())?.results?.[0];
    id = Number(first?.id) > 0 ? Number(first.id) : 0;
  } catch { return null; }
  if (cache) await cache.put(key, new Response(String(id), { headers: { "cache-control": `public, max-age=${30 * 86400}` } }));
  return id || null;
}

/** Corps Trakt /scrobble/{action}. null si le contenu n'a pas pu être identifié. */
export function traktScrobbleBody(s, tmdb) {
  if (!tmdb) return null;
  const app = { app_version: "1.0" };
  if (s.kind === "movie") return { movie: { ids: { tmdb } }, progress: s.progress, ...app };
  return { show: { ids: { tmdb } }, episode: { season: s.season, number: s.episode }, progress: s.progress, ...app };
}

/** Envoi à Trakt → "ok" | "unauthorized" | "unmatched" | "upstream". */
export async function sendScrobble(clientId, access, action, body, fetchFn = fetch) {
  let r;
  try {
    r = await fetchFn(`${TRAKT_API}/scrobble/${action}`, { method: "POST", redirect: "manual", headers: apiHeaders(clientId, access), body: JSON.stringify(body) });
  } catch { return "upstream"; }
  // Statut seul (jamais de jeton ni de titre) : diagnostic via `wrangler tail`.
  console.log(JSON.stringify({ trakt: "scrobble", action, status: r.status }));
  // 409 : déjà scrobblé il y a peu (Trakt dédoublonne) — sans gravité.
  if (r.status === 200 || r.status === 201 || r.status === 409) return "ok";
  if (r.status === 401 || r.status === 403) return "unauthorized";
  if (r.status === 404) return "unmatched";
  return "upstream";
}

// ---- écritures depuis les appareils (watchlist, historique, notes) -------------------------------------------------
//   POST /api/device/trakt/watchlist {action: add|remove, type: movie|show, title, year?, tmdb?}
//   POST /api/device/trakt/history   {action: add|remove, type: movie|episodes, title, year?, tmdb?, episodes?: [{season, episode}]}
//   POST /api/device/trakt/rating    {type: movie|show, title, year?, tmdb?, rating: 0..10}   (0 = retirer la note)
// Comme le scrobble : l'appareil décrit l'œuvre, le Worker l'identifie (TMDB) et écrit chez Trakt avec SES jetons.

const WRITE_ACTIONS = new Set(["add", "remove"]);
export const MAX_HISTORY_EPISODES = 200;

/** Champs communs (titre, année, tmdb) validés, ou null. */
function workOf(body) {
  const title = cleanText(body.title, 200);
  if (!title) return null;
  return { title, year: intIn(body.year, 1900, 2100), tmdb: intIn(body.tmdb, 1, 99_999_999) };
}

/** Corps « watchlist » d'un appareil, validé. null si invalide. */
export function parseWatchlist(body) {
  if (!body || typeof body !== "object" || !WRITE_ACTIONS.has(body.action)) return null;
  if (body.type !== "movie" && body.type !== "show") return null;
  const w = workOf(body);
  return w && { action: body.action, type: body.type, ...w };
}

/** Corps « historique » (vu / non vu) d'un appareil, validé. Épisodes dédoublonnés, au plus MAX_HISTORY_EPISODES. */
export function parseHistory(body) {
  if (!body || typeof body !== "object" || !WRITE_ACTIONS.has(body.action)) return null;
  if (body.type !== "movie" && body.type !== "episodes") return null;
  const w = workOf(body);
  if (!w) return null;
  const out = { action: body.action, type: body.type, ...w };
  if (body.type === "episodes") {
    if (!Array.isArray(body.episodes) || body.episodes.length === 0 || body.episodes.length > MAX_HISTORY_EPISODES) return null;
    const seen = new Set();
    out.episodes = [];
    for (const e of body.episodes) {
      const season = intIn(e?.season, 0, 500);
      const episode = intIn(e?.episode, 0, 20_000);
      if (season === null || episode === null) return null;
      const k = `${season}x${episode}`;
      if (!seen.has(k)) { seen.add(k); out.episodes.push({ season, episode }); }
    }
  }
  return out;
}

/** Corps « note » d'un appareil, validé (0 = retirer la note). null si invalide. */
export function parseRating(body) {
  if (!body || typeof body !== "object") return null;
  if (body.type !== "movie" && body.type !== "show") return null;
  // Nombre entier strict : null ou "8" ne doivent jamais devenir « retirer la note » ou une note implicite.
  if (!Number.isInteger(body.rating) || body.rating < 0 || body.rating > 10) return null;
  const rating = body.rating;
  const w = workOf(body);
  return w && { type: body.type, rating, ...w };
}

/**
 * Requête Trakt correspondant à une écriture validée → { path, body }. `now` = date ISO (vu / noté « maintenant »).
 * kind : "watchlist" | "history" | "rating".
 */
export function traktWriteRequest(kind, p, tmdb, now = new Date().toISOString()) {
  const ids = { tmdb };
  if (kind === "watchlist") {
    return { path: p.action === "add" ? "/sync/watchlist" : "/sync/watchlist/remove", body: p.type === "movie" ? { movies: [{ ids }] } : { shows: [{ ids }] } };
  }
  if (kind === "history") {
    const path = p.action === "add" ? "/sync/history" : "/sync/history/remove";
    const at = p.action === "add" ? { watched_at: now } : {};
    if (p.type === "movie") return { path, body: { movies: [{ ids, ...at }] } };
    const bySeason = new Map();
    for (const e of p.episodes) {
      if (!bySeason.has(e.season)) bySeason.set(e.season, []);
      bySeason.get(e.season).push({ number: e.episode, ...at });
    }
    const seasons = [...bySeason].sort((a, b) => a[0] - b[0]).map(([number, episodes]) => ({ number, episodes }));
    return { path, body: { shows: [{ ids, seasons }] } };
  }
  // Note : 1..10 → /sync/ratings ; 0 → /sync/ratings/remove.
  const one = p.rating > 0 ? { ids, rating: p.rating, rated_at: now } : { ids };
  return { path: p.rating > 0 ? "/sync/ratings" : "/sync/ratings/remove", body: p.type === "movie" ? { movies: [one] } : { shows: [one] } };
}

/** Somme des compteurs d'une réponse /sync (added / existing / deleted / updated). */
function countOf(d) {
  let n = 0;
  for (const k of ["added", "existing", "deleted", "updated"]) {
    const o = d?.[k];
    if (o && typeof o === "object") for (const v of Object.values(o)) if (Number.isFinite(v)) n += v;
  }
  return n;
}
/** Vrai si Trakt n'a reconnu aucun élément (listes `not_found` non vides). */
function anyNotFound(d) {
  const nf = d?.not_found;
  return !!nf && typeof nf === "object" && Object.values(nf).some((v) => Array.isArray(v) && v.length > 0);
}

/** Écriture /sync chez Trakt → "ok" | "unauthorized" | "unmatched" | "upstream". */
export async function sendSync(clientId, access, path, body, fetchFn = fetch) {
  let r;
  try {
    r = await fetchFn(`${TRAKT_API}${path}`, { method: "POST", redirect: "manual", headers: apiHeaders(clientId, access), body: JSON.stringify(body) });
  } catch { return "upstream"; }
  console.log(JSON.stringify({ trakt: "sync", path, status: r.status }));
  if (r.status === 401 || r.status === 403) return "unauthorized";
  if (r.status !== 200 && r.status !== 201 && r.status !== 204) return "upstream";
  const d = r.status === 204 ? null : await r.json().catch(() => null);
  if (d && anyNotFound(d) && countOf(d) === 0) return "unmatched";
  return "ok";
}
