// Bibliothèque Trakt d'un compte, préparée pour les appareils : watchlist, recommandations, tendances, populaires,
// films et épisodes vus.
//   GET /api/device/trakt/library?lang=fr
//     → { linked, updatedAt, watchlist[], recommendations[], trending[], popular[], watched{movies[], shows[]} }
// Chaque titre porte ses clés de rapprochement (`keys`, voir matchKey) : titre Trakt (anglais), titre localisé et
// titre original TMDB. L'appareil n'affiche que ce que SA playlist contient (rapprochement titre + année).

const TRAKT_API = "https://api.trakt.tv";
const TMDB_API = "https://api.themoviedb.org/3/";
const USER_AGENT = "UltraTV/1.0";

export const LIB_TTL_S = 15 * 60;
const MAX_WATCHLIST = 200;
const MAX_RECS = 20; // par type
const MAX_PUBLIC = 40; // tendances / populaires, par type
const MAX_WATCHED_MOVIES = 1500;
const MAX_WATCHED_SHOWS = 400;
/** Titres TMDB complétés par rafraîchissement (borne de sous-requêtes ; le reste suit au rafraîchissement suivant). */
export const TMDB_PER_REFRESH = 25;
export const LANGS = new Set(["fr", "en", "es", "ar"]);

const QUALITY = new RegExp(String.raw`(?<![\p{L}\p{N}])(4k|uhd|fhd|hd|sd|hevc|h26[45]|x26[45]|multi|vostfr|vost|vff|vf|vo|truefrench|subfrench|french)(?![\p{L}\p{N}])`, "giu");

/**
 * Clé de rapprochement d'un titre : sans préfixe de catégorie (« FR - », « |FR| », « [VOD] »), sans mention de
 * qualité ni année, sans accents, « & » → « and », ponctuation → espace, sans article initial. Règle partagée avec
 * les applications : vecteurs dans test/fixtures/trakt-match-vectors.json.
 */
export function matchKey(raw) {
  let s = String(raw ?? "");
  // Préfixe en MAJUSCULES seulement (« FR - », « 4K - », « AR: ») : « Dune: Part Two » garde « Dune ».
  s = s.replace(/^\s*(\|[^|]{1,12}\||\[[^\]]{1,12}\]|[A-Z0-9]{2,4}\s*[-:|]\s*)/u, "");
  s = s.normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase();
  s = s.replace(QUALITY, " ");
  s = s.replace(/\(\s*(19|20)\d{2}\s*\)/g, " ");
  s = s.replace(/&/g, " and ");
  s = s.replace(/[^\p{L}\p{N}]+/gu, " ").replace(/\s+/g, " ").trim();
  const noYear = s.replace(/\s(19|20)\d{2}$/, "");
  if (noYear) s = noYear;
  s = s.replace(/^(the|le|la|les|l|el|los|las|a|an)\s+(?=\S)/, "");
  return s;
}

/** Vrai si un titre du catalogue correspond à un élément Trakt (une clé commune, années à ±1 an quand connues). */
export function titleMatches(catalogTitle, catalogYear, titles, year) {
  const k = matchKey(catalogTitle);
  if (!k) return false;
  if (catalogYear && year && Math.abs(catalogYear - year) > 1) return false;
  return titles.some((t) => matchKey(t) === k);
}

const apiHeaders = (clientId, access) => ({
  accept: "application/json", "user-agent": USER_AGENT, "trakt-api-version": "2", "trakt-api-key": clientId, authorization: `Bearer ${access}`,
});

async function traktGet(clientId, access, path, fetchFn) {
  const r = await fetchFn(`${TRAKT_API}${path}`, { headers: apiHeaders(clientId, access), redirect: "manual" });
  if (r.status === 401 || r.status === 403) throw Object.assign(new Error("unauthorized"), { unauthorized: true });
  if (r.status !== 200) throw Object.assign(new Error(`trakt ${r.status}`), { status: r.status });
  const d = await r.json();
  return Array.isArray(d) ? d : [];
}

const yearOf = (v) => (Number.isInteger(v) && v >= 1900 && v <= 2100 ? v : null);
const tmdbOf = (o) => (Number.isInteger(o?.ids?.tmdb) && o.ids.tmdb > 0 ? o.ids.tmdb : null);
const titleOf = (o) => String(o?.title ?? "").slice(0, 200);

/** Élément normalisé { type, tmdb, year, title } (tmdb peut manquer : rapprochement par titre seul). */
const item = (type, o) => ({ type, tmdb: tmdbOf(o), year: yearOf(o?.year), title: titleOf(o) });

/**
 * Lit Trakt (6 appels en parallèle) → données brutes normalisées.
 * Un appel en échec (autre qu'un refus d'accès) laisse simplement sa liste vide.
 */
export async function fetchTraktLibrary(clientId, access, fetchFn = fetch) {
  // Une liste en échec reste vide ; TOUTES en échec : erreur (sinon une panne de Trakt passerait pour un compte vide,
  // et ce vide serait gardé 15 min en cache).
  let failed = 0;
  const get = (p) => traktGet(clientId, access, p, fetchFn).catch((e) => {
    if (e.unauthorized) throw e;
    failed++;
    console.log(JSON.stringify({ trakt: "library", path: p.split("?")[0], status: e.status ?? "network" }));
    return [];
  });
  const [wm, ws, rm, rs, hm, hs, tm, ts, pm, ps] = await Promise.all([
    get("/sync/watchlist/movies"), get("/sync/watchlist/shows"),
    get(`/recommendations/movies?limit=${MAX_RECS}&ignore_collected=true&ignore_watchlisted=true`),
    get(`/recommendations/shows?limit=${MAX_RECS}&ignore_collected=true&ignore_watchlisted=true`),
    get("/sync/watched/movies"), get("/sync/watched/shows"),
    get(`/movies/trending?limit=${MAX_PUBLIC}`), get(`/shows/trending?limit=${MAX_PUBLIC}`),
    get(`/movies/popular?limit=${MAX_PUBLIC}`), get(`/shows/popular?limit=${MAX_PUBLIC}`),
  ]);
  if (failed === 10) throw Object.assign(new Error("trakt indisponible"), { upstream: true });
  const watchlist = [...wm.map((x) => item("movie", x.movie)), ...ws.map((x) => item("show", x.show))]
    .filter((x) => x.title).slice(0, MAX_WATCHLIST);
  const recommendations = [...rm.map((x) => item("movie", x)), ...rs.map((x) => item("show", x))].filter((x) => x.title);
  const movies = hm.map((x) => item("movie", x.movie)).filter((x) => x.title).slice(0, MAX_WATCHED_MOVIES);
  const shows = hs.map((x) => {
    const episodes = [];
    for (const se of Array.isArray(x.seasons) ? x.seasons : []) {
      for (const ep of Array.isArray(se.episodes) ? se.episodes : []) {
        if (Number.isInteger(se.number) && Number.isInteger(ep.number)) episodes.push(`${se.number}x${ep.number}`);
      }
    }
    return { ...item("show", x.show), episodes };
  }).filter((x) => x.title).slice(0, MAX_WATCHED_SHOWS);
  // Tendances (regardés en ce moment) et populaires : listes publiques, films et séries entrelacés.
  const mix = (a, b) => { const out = []; for (let i = 0; i < Math.max(a.length, b.length); i++) { if (a[i]) out.push(a[i]); if (b[i]) out.push(b[i]); } return out.filter((x) => x.title); };
  const trending = mix(tm.map((x) => item("movie", x.movie)), ts.map((x) => item("show", x.show)));
  const popular = mix(pm.map((x) => item("movie", x)), ps.map((x) => item("show", x)));
  return { watchlist, recommendations, watched: { movies, shows }, trending, popular };
}

/** Titres localisé + original d'un élément TMDB, ou null. */
export async function tmdbTitles(env, type, tmdb, lang, fetchFn = fetch) {
  if (!env.TMDB_READ_TOKEN && !env.TMDB_API_KEY) return null;
  const up = new URL(`${TMDB_API}${type === "movie" ? "movie" : "tv"}/${tmdb}`);
  up.searchParams.set("language", lang);
  const headers = { accept: "application/json" };
  if (env.TMDB_READ_TOKEN) headers.authorization = `Bearer ${env.TMDB_READ_TOKEN}`;
  else up.searchParams.set("api_key", env.TMDB_API_KEY);
  try {
    const r = await fetchFn(up.toString(), { headers, redirect: "manual" });
    if (r.status !== 200) return null;
    const d = await r.json();
    return [d.title ?? d.name, d.original_title ?? d.original_name].filter((t) => typeof t === "string" && t).map((t) => t.slice(0, 200));
  } catch { return null; }
}

/**
 * Complète les titres (dictionnaire `known` : "m123" / "s456" → [titres]) : au plus TMDB_PER_REFRESH nouveaux par
 * appel, priorité watchlist puis recommandations puis vus. Renvoie le dictionnaire mis à jour.
 */
export async function enrichTitles(env, lib, known, lang, fetchFn = fetch) {
  const dict = { ...known };
  const order = [...lib.watchlist, ...lib.recommendations, ...(lib.trending || []), ...(lib.popular || []), ...lib.watched.shows, ...lib.watched.movies];
  const todo = [];
  const seen = new Set();
  for (const x of order) {
    if (!x.tmdb) continue;
    const k = `${x.type === "movie" ? "m" : "s"}${x.tmdb}`;
    if (dict[k] || seen.has(k)) continue;
    seen.add(k);
    todo.push({ k, x });
    if (todo.length >= TMDB_PER_REFRESH) break;
  }
  const got = await Promise.all(todo.map(({ x }) => tmdbTitles(env, x.type, x.tmdb, lang, fetchFn)));
  todo.forEach(({ k }, i) => { if (got[i]) dict[k] = got[i]; });
  return dict;
}

/** Réponse pour l'appareil : chaque élément porte ses clés de rapprochement (dédoublonnées, non vides). */
export function libraryForDevice(lib, dict, updatedAt) {
  const keysOf = (x) => {
    const extra = x.tmdb ? dict[`${x.type === "movie" ? "m" : "s"}${x.tmdb}`] || [] : [];
    return [...new Set([x.title, ...extra].map(matchKey).filter(Boolean))];
  };
  const out = (x) => ({ type: x.type, tmdb: x.tmdb, year: x.year, title: x.title, keys: keysOf(x) });
  return {
    linked: true,
    updatedAt,
    watchlist: lib.watchlist.map(out),
    recommendations: lib.recommendations.map(out),
    trending: (lib.trending || []).map(out),
    popular: (lib.popular || []).map(out),
    watched: {
      movies: lib.watched.movies.map(out),
      shows: lib.watched.shows.map((x) => ({ ...out(x), episodes: x.episodes })),
    },
  };
}

