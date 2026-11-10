// Notes IMDb / Rotten Tomatoes / Metacritic via OMDb. La clé reste dans les secrets Wrangler (OMDB_API_KEY).
// Entrée : identifiant TMDB (film ou série), sinon titre + année (retrouvé sur TMDB). L'identifiant IMDb est lu sur
// TMDB (external_ids), puis OMDb est interrogé par cet identifiant. Réponses mises en cache 7 jours (quota OMDb gratuit :
// 1 000 requêtes / jour pour toute l'instance).

import { resolveTmdb } from "./trakt.js";

const TMDB_API = "https://api.themoviedb.org/3/";
const OMDB_API = "https://www.omdbapi.com/";
export const RATINGS_TTL_S = 7 * 24 * 3600;
const IMDB_ID = /^tt[0-9]{5,10}$/;

/** Paramètres normalisés, ou null s'ils sont invalides. */
export function ratingsTarget(sp) {
  const kind = sp.get("kind");
  if (kind !== "movie" && kind !== "tv") return null;
  const tmdbRaw = sp.get("tmdb");
  const tmdb = tmdbRaw !== null && /^[1-9][0-9]{0,9}$/.test(tmdbRaw) ? Number(tmdbRaw) : null;
  if (tmdbRaw !== null && tmdb === null) return null;
  const title = (sp.get("title") ?? "").trim();
  const yearRaw = sp.get("year");
  const year = yearRaw && /^(19|20)[0-9]{2}$/.test(yearRaw) ? Number(yearRaw) : null;
  if (yearRaw && year === null) return null;
  if (tmdb === null && (title === "" || title.length > 120)) return null;
  return { kind, tmdb, title: tmdb === null ? title : "", year: tmdb === null ? year : null };
}

/** Réponse OMDb → { imdb: 7.8, imdbVotes, rt: 92, mc: 74 } (champ absent = inconnu). null si OMDb n'a rien. */
export function parseOmdb(o) {
  if (!o || o.Response === "False") return null;
  const out = {};
  const imdb = Number(o.imdbRating);
  if (Number.isFinite(imdb) && imdb > 0 && imdb <= 10) out.imdb = Math.round(imdb * 10) / 10;
  const votes = Number(String(o.imdbVotes ?? "").replace(/[^0-9]/g, ""));
  if (votes > 0) out.imdbVotes = votes;
  for (const r of Array.isArray(o.Ratings) ? o.Ratings : []) {
    const v = String(r?.Value ?? "");
    if (r?.Source === "Rotten Tomatoes" && /^\d{1,3}%$/.test(v)) out.rt = Number(v.slice(0, -1));
    if (r?.Source === "Metacritic" && /^\d{1,3}\/100$/.test(v)) out.mc = Number(v.split("/")[0]);
  }
  if (out.mc == null) { const m = Number(o.Metascore); if (Number.isFinite(m) && m > 0) out.mc = m; }
  return Object.keys(out).length ? out : null;
}

const jsonRes = (body, status, maxAge) => new Response(JSON.stringify(body), {
  status, headers: { "content-type": "application/json", "cache-control": maxAge ? `public, max-age=${maxAge}` : "no-store" },
});

async function imdbIdOf(env, kind, tmdb, fetchFn) {
  const up = new URL(`${TMDB_API}${kind}/${tmdb}/external_ids`);
  const headers = { accept: "application/json" };
  if (env.TMDB_READ_TOKEN) headers.authorization = `Bearer ${env.TMDB_READ_TOKEN}`;
  else up.searchParams.set("api_key", env.TMDB_API_KEY);
  const r = await fetchFn(up.toString(), { headers, redirect: "manual" });
  if (r.status !== 200) return null;
  const id = (await r.json())?.imdb_id;
  return typeof id === "string" && IMDB_ID.test(id) ? id : null;
}

/** GET /api/ratings?kind=movie|tv&tmdb=… (ou &title=…&year=…) → { ratings: {…} | null } */
export async function ratingsProxy(sp, env, { fetchFn = fetch, cache = globalThis.caches?.default } = {}) {
  const t = ratingsTarget(sp);
  if (!t) return jsonRes({ error: "invalid" }, 400);
  if (!env.OMDB_API_KEY || (!env.TMDB_READ_TOKEN && !env.TMDB_API_KEY)) return jsonRes({ error: "ratings_not_configured" }, 503);
  const key = new Request(`https://ratings-cache.invalid/${t.kind}?tmdb=${t.tmdb ?? ""}&title=${encodeURIComponent(t.title)}&year=${t.year ?? ""}`);
  const hit = cache ? await cache.match(key) : undefined;
  if (hit) return hit;
  let ratings = null;
  try {
    const tmdb = t.tmdb ?? (await resolveTmdb(env, t.kind, t.title, t.year, { fetchFn, cache }));
    const imdbId = tmdb ? await imdbIdOf(env, t.kind, tmdb, fetchFn) : null;
    if (imdbId) {
      const up = new URL(OMDB_API);
      up.searchParams.set("i", imdbId);
      up.searchParams.set("apikey", env.OMDB_API_KEY);
      const r = await fetchFn(up.toString(), { headers: { accept: "application/json" }, redirect: "manual" });
      if (r.status !== 200) return jsonRes({ error: "upstream" }, 502);
      const o = await r.json();
      // Quota dépassé / clé invalide : pas de mise en cache, on réessaiera.
      if (o?.Response === "False" && /limit|invalid api key/i.test(String(o.Error ?? ""))) return jsonRes({ error: "upstream" }, 502);
      ratings = parseOmdb(o);
      if (ratings) ratings.imdbId = imdbId;
    }
  } catch { return jsonRes({ error: "upstream" }, 502); }
  const res = jsonRes({ ratings }, 200, RATINGS_TTL_S);
  if (cache) await cache.put(key, res.clone());
  return res;
}
