// Simkl : second service de suivi au choix (avec Trakt). Connexion depuis l'espace client (OAuth 2, code d'autorisation),
// puis lecture/écriture relayées par le Worker : les appareils ne voient jamais de jeton Simkl.
// Même contrat côté appareil que Trakt (routes /api/device/trakt/* ou /api/device/tracking/*) : le Worker choisit le
// service actif du compte. Les jetons Simkl n'expirent pas (pas de renouvellement). Il faut SIMKL_CLIENT_ID et
// SIMKL_CLIENT_SECRET : sans eux, Simkl est masqué partout.

// Auth V2 de Simkl (application « serveur ») : PKCE S256 obligatoire + secret côté serveur.
export const SIMKL_AUTHORIZE = "https://simkl.com/oauth2/authorize";
const SIMKL_API = "https://api.simkl.com";
const USER_AGENT = "UltraTV/1.0";
export const SIMKL_STATE_TTL_S = 600;

/** Simkl est proposé seulement si l'identifiant ET le secret de l'application sont configurés. */
export const simklEnabled = (env) => !!(env && env.SIMKL_CLIENT_ID && env.SIMKL_CLIENT_SECRET);

export function simklAuthorizeUrl(clientId, redirectUri, state, challenge) {
  const u = new URL(SIMKL_AUTHORIZE);
  if (challenge) { u.searchParams.set("code_challenge", challenge); u.searchParams.set("code_challenge_method", "S256"); }
  u.searchParams.set("response_type", "code");
  u.searchParams.set("client_id", clientId);
  u.searchParams.set("redirect_uri", redirectUri);
  u.searchParams.set("state", state);
  return u.toString();
}

const apiHeaders = (clientId, access) => ({
  "content-type": "application/json", accept: "application/json", "user-agent": USER_AGENT,
  "simkl-api-key": clientId,
  ...(access ? { authorization: `Bearer ${access}` } : {}),
});

/** Échange du code d'autorisation → { access } ou { error: "denied" | "upstream" }. */
export async function simklExchangeCode(clientId, clientSecret, redirectUri, code, fetchFn = fetch, verifier = null) {
  let r;
  try {
    r = await fetchFn(`${SIMKL_API}/oauth/token`, {
      method: "POST", redirect: "manual", headers: apiHeaders(clientId),
      body: JSON.stringify({ code, client_id: clientId, client_secret: clientSecret, redirect_uri: redirectUri, grant_type: "authorization_code", ...(verifier ? { code_verifier: verifier } : {}) }),
    });
  } catch { return { error: "upstream" }; }
  if (r.status === 400 || r.status === 401 || r.status === 403) return { error: "denied" };
  if (r.status !== 200) return { error: "upstream" };
  const d = await r.json().catch(() => null);
  if (!d || typeof d.access_token !== "string" || !d.access_token) return { error: "upstream" };
  return { access: d.access_token.slice(0, 512) };
}

/** Nom d'utilisateur Simkl (affichage dans l'espace client), ou "" si indisponible. */
export async function simklUsername(clientId, access, fetchFn = fetch) {
  try {
    const r = await fetchFn(`${SIMKL_API}/users/settings`, { method: "POST", headers: apiHeaders(clientId, access), redirect: "manual", body: "{}" });
    if (r.status !== 200) return "";
    const d = await r.json();
    return String(d?.user?.name || "").slice(0, 60);
  } catch { return ""; }
}

// ---- lecture : bibliothèque au même format que Trakt (voir traktlib.js) --------------------------------------------

const MAX_WATCHLIST = 200;
const MAX_WATCHED_MOVIES = 1500;
const MAX_WATCHED_SHOWS = 400;
const MAX_RATINGS = 1000;

const yearOf = (v) => { const n = Number(v); return Number.isInteger(n) && n >= 1900 && n <= 2100 ? n : null; };
const tmdbOf = (o) => { const n = Number(o?.ids?.tmdb); return Number.isInteger(n) && n > 0 ? n : null; };
const titleOf = (o) => String(o?.title ?? "").slice(0, 200);
const item = (type, o) => ({ type, tmdb: tmdbOf(o), year: yearOf(o?.year), title: titleOf(o) });
const msOf = (v) => { const t = Date.parse(typeof v === "string" ? v : ""); return Number.isFinite(t) ? t : 0; };

/** GET /sync/all-items/... → objet { movies?, shows? }, ou lève { unauthorized } / { status }. */
async function simklGet(clientId, access, path, fetchFn) {
  const r = await fetchFn(`${SIMKL_API}${path}`, { headers: apiHeaders(clientId, access), redirect: "manual" });
  if (r.status === 401 || r.status === 403) throw Object.assign(new Error("unauthorized"), { unauthorized: true });
  if (r.status !== 200) throw Object.assign(new Error(`simkl ${r.status}`), { status: r.status });
  const d = await r.json().catch(() => null);
  return d && typeof d === "object" ? d : {};
}

const note = (v) => (Number.isInteger(v) && v >= 1 && v <= 10 ? v : null);

/**
 * Lit Simkl (6 appels en parallèle : à voir, déjà vus, en cours, en pause) → mêmes données brutes que
 * fetchTraktLibrary. Simkl n'a ni recommandations, ni tendances, ni listes personnelles, ni reprises : listes vides.
 * Un appel en échec (autre qu'un refus d'accès) laisse sa liste vide ; tous en échec : erreur.
 */
export async function fetchSimklLibrary(clientId, access, fetchFn = fetch) {
  let failed = 0;
  const get = (p) => simklGet(clientId, access, p, fetchFn).catch((e) => {
    if (e.unauthorized) throw e;
    failed++;
    console.log(JSON.stringify({ simkl: "library", path: p.split("?")[0], status: e.status ?? "network" }));
    return {};
  });
  const q = "?extended=full&episode_watched_at=yes";
  const paths = [
    `/sync/all-items/movies/plantowatch${q}`, `/sync/all-items/shows/plantowatch${q}`,
    `/sync/all-items/movies/completed${q}`, `/sync/all-items/shows/completed${q}`,
    `/sync/all-items/shows/watching${q}`, `/sync/all-items/shows/hold${q}`,
  ];
  const [pm, ps, cm, cs, ws, hs] = await Promise.all(paths.map(get));
  if (failed === paths.length) throw Object.assign(new Error("simkl indisponible"), { upstream: true });
  const arr = (d, k) => (Array.isArray(d?.[k]) ? d[k] : []);
  const watchlist = [...arr(pm, "movies").map((x) => item("movie", x.movie)), ...arr(ps, "shows").map((x) => item("show", x.show))]
    .filter((x) => x.title).slice(0, MAX_WATCHLIST);
  const movies = arr(cm, "movies").map((x) => item("movie", x.movie)).filter((x) => x.title).slice(0, MAX_WATCHED_MOVIES);
  const shows = [...arr(ws, "shows"), ...arr(hs, "shows"), ...arr(cs, "shows")].map((x) => {
    const episodes = [];
    for (const se of Array.isArray(x.seasons) ? x.seasons : []) {
      for (const ep of Array.isArray(se.episodes) ? se.episodes : []) {
        if (Number.isInteger(se.number) && Number.isInteger(ep.number)) episodes.push(`${se.number}x${ep.number}`);
      }
    }
    return { ...item("show", x.show), episodes, lastWatchedAt: msOf(x.last_watched_at) };
  }).filter((x) => x.title).slice(0, MAX_WATCHED_SHOWS);
  const rated = (lists, type, key) => lists.flatMap((d) => arr(d, type === "movie" ? "movies" : "shows"))
    .map((x) => ({ ...item(type, x[key]), rating: note(x.user_rating) }))
    .filter((x) => x.title && x.rating).slice(0, MAX_RATINGS);
  const ratings = { movies: rated([pm, cm], "movie", "movie"), shows: rated([ps, ws, hs, cs], "show", "show") };
  return { watchlist, recommendations: [], watched: { movies, shows }, trending: [], popular: [], ratings, lists: [], playback: [] };
}

// ---- écritures : watchlist, historique, notes, scrobble ------------------------------------------------------------

/**
 * Requête Simkl correspondant à une écriture validée (mêmes entrées que traktWriteRequest) → { path, body } ou null
 * quand Simkl ne sait pas le faire (retrait de la watchlist : pas de point d'accès documenté).
 */
export function simklWriteRequest(kind, p, tmdb, now = new Date().toISOString()) {
  const ids = { tmdb };
  if (kind === "watchlist") {
    if (p.action !== "add") return null;
    return { path: "/sync/add-to-list", body: p.type === "movie" ? { movies: [{ to: "plantowatch", ids }] } : { shows: [{ to: "plantowatch", ids }] } };
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
  const one = p.rating > 0 ? { ids, rating: p.rating, rated_at: now } : { ids };
  return { path: p.rating > 0 ? "/sync/ratings" : "/sync/ratings/remove", body: p.type === "movie" ? { movies: [one] } : { shows: [one] } };
}

/** Corps Simkl /scrobble/{action} (même entrée que traktScrobbleBody). null si le contenu n'a pas été identifié. */
export function simklScrobbleBody(s, tmdb) {
  if (!tmdb) return null;
  if (s.kind === "movie") return { movie: { ids: { tmdb } }, progress: s.progress };
  return { show: { ids: { tmdb } }, episode: { season: s.season, number: s.episode }, progress: s.progress };
}

function anyNotFound(d) {
  const nf = d?.not_found;
  return !!nf && typeof nf === "object" && Object.values(nf).some((v) => Array.isArray(v) && v.length > 0);
}
function countOf(d) {
  let n = 0;
  for (const k of ["added", "existing", "deleted", "updated"]) {
    const o = d?.[k];
    if (o && typeof o === "object") for (const v of Object.values(o)) if (Number.isFinite(v)) n += v;
  }
  return n;
}

/** Envoi à Simkl (écriture /sync ou /scrobble) → "ok" | "unauthorized" | "unmatched" | "upstream". */
export async function simklSend(clientId, access, path, body, fetchFn = fetch) {
  let r;
  try {
    r = await fetchFn(`${SIMKL_API}${path}`, { method: "POST", redirect: "manual", headers: apiHeaders(clientId, access), body: JSON.stringify(body) });
  } catch { return "upstream"; }
  // Statut seul (jamais de jeton ni de titre).
  console.log(JSON.stringify({ simkl: "send", path, status: r.status }));
  if (r.status === 401 || r.status === 403) return "unauthorized";
  if (r.status === 404) return "unmatched";
  if (r.status === 409) return "ok"; // déjà enregistré
  if (r.status !== 200 && r.status !== 201 && r.status !== 204) return "upstream";
  const d = r.status === 204 ? null : await r.json().catch(() => null);
  if (d && anyNotFound(d) && countOf(d) === 0) return "unmatched";
  return "ok";
}
