// Proxy TMDB : les identifiants restent dans les secrets Wrangler, jamais dans l'application.
//   TMDB_READ_TOKEN  jeton v4, envoyé en « Authorization: Bearer » (à privilégier) ;
//   TMDB_API_KEY     clé v3 (?api_key=), en repli seulement si le jeton est absent.
// Seuls les chemins de la liste blanche sont relayés ; les paramètres sont filtrés.

const API = "https://api.themoviedb.org/3/";
const ID = "[1-9][0-9]{0,9}";
const ALLOWED_PATHS = [
  /^search\/(movie|tv)$/,
  new RegExp(`^(movie|tv)/${ID}$`),
  new RegExp(`^(movie|tv)/${ID}/(images|videos|credits)$`),
];
const APPEND_OK = new Set(["credits", "videos", "images"]);
const LANG = /^[a-z]{2}(-[A-Z]{2})?$/;
const YEAR = /^(19|20)[0-9]{2}$/;
const IMG_LANG = /^[a-z]{2}(,[a-z]{2}|,null)*$/;

export const CACHE_TTL_S = 24 * 3600;

/** Renvoie { path, params } normalisés, ou null si la requête sort de la liste blanche. */
export function tmdbTarget(rest, searchParams) {
  const path = rest.replace(/^\/+/, "");
  if (!ALLOWED_PATHS.some((re) => re.test(path))) return null;
  const params = new URLSearchParams();
  const q = searchParams.get("query");
  if (q !== null) {
    if (!path.startsWith("search/") || q.trim() === "" || q.length > 120) return null;
    params.set("query", q.trim());
  } else if (path.startsWith("search/")) return null;
  for (const [k, re] of [["year", YEAR], ["first_air_date_year", YEAR], ["language", LANG], ["include_image_language", IMG_LANG]]) {
    const v = searchParams.get(k);
    if (v === null) continue;
    if (!re.test(v)) return null;
    params.set(k, v);
  }
  const app = searchParams.get("append_to_response");
  if (app !== null) {
    const parts = app.split(",");
    if (!parts.every((p) => APPEND_OK.has(p))) return null;
    params.set("append_to_response", [...new Set(parts)].sort().join(","));
  }
  params.sort();
  return { path, params };
}

/** Clé de cache : ne contient jamais la clé d'API. */
export function cacheKey(t) {
  return new Request(`https://tmdb-cache.invalid/${t.path}?${t.params}`);
}

/**
 * @param rest chemin après /api/tmdb/
 * @param fetchFn / cache injectables pour les tests
 */
export async function tmdbProxy(rest, searchParams, env, { fetchFn = fetch, cache = globalThis.caches?.default } = {}) {
  const t = tmdbTarget(rest, searchParams);
  if (!t) return new Response(JSON.stringify({ error: "not_allowed" }), { status: 404, headers: { "content-type": "application/json" } });
  if (!env.TMDB_READ_TOKEN && !env.TMDB_API_KEY) return new Response(JSON.stringify({ error: "tmdb_not_configured" }), { status: 503, headers: { "content-type": "application/json" } });
  const key = cacheKey(t);
  const hit = cache ? await cache.match(key) : undefined;
  if (hit) return hit;
  const up = new URL(API + t.path);
  for (const [k, v] of t.params) up.searchParams.set(k, v);
  const headers = { accept: "application/json" };
  if (env.TMDB_READ_TOKEN) headers.authorization = `Bearer ${env.TMDB_READ_TOKEN}`;
  else up.searchParams.set("api_key", env.TMDB_API_KEY);
  const r = await fetchFn(up.toString(), { headers, redirect: "manual" });
  const body = await r.text();
  const ok = r.status === 200;
  const res = new Response(ok ? body : JSON.stringify({ error: r.status === 404 ? "not_found" : "upstream" }), {
    status: ok ? 200 : r.status === 404 ? 404 : 502,
    headers: { "content-type": "application/json", "cache-control": ok ? `public, max-age=${CACHE_TTL_S}` : "no-store" },
  });
  if (ok && cache) await cache.put(key, res.clone());
  return res;
}
