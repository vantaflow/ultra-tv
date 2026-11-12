// Métadonnées complémentaires, relayées pour les appareils appairés (clés côté serveur, réponses réduites et mises en cache) :
//   Fanart.tv    logos officiels des titres (« clearlogo ») et fonds HD — secret FANART_API_KEY (clé gratuite) ;
//   TVmaze       épisode précédent / prochain d'une série (calendrier de diffusion) — sans clé ;
//   TheSportsDB  écusson et compétition d'une équipe (cartes Sport) — clé publique gratuite « 123 » ou SPORTSDB_API_KEY.
// Rien d'identifiant n'est transmis : seulement un identifiant de titre (TMDB, TheTVDB, IMDb) ou un nom d'équipe.

const json = (body, status = 200, maxAge = 0) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json", "cache-control": maxAge > 0 ? `public, max-age=${maxAge}` : "no-store" },
});

const ID = /^[1-9][0-9]{0,9}$/;
const IMDB = /^tt[0-9]{5,10}$/;
const LANG = /^[a-z]{2}$/;
const DAY = 86400;

/** Cache Cloudflare : clé neutre (jamais l'URL amont, qui peut porter une clé). */
async function cached(cache, key, ttl, produce) {
  const req = new Request(`https://extmeta.cache/${key}`);
  const hit = cache ? await cache.match(req) : undefined;
  if (hit) return hit;
  const res = await produce();
  if (res.status === 200 && cache) {
    const copy = new Response(res.clone().body, { status: 200, headers: { "content-type": "application/json", "cache-control": `public, max-age=${ttl}` } });
    await cache.put(req, copy);
  }
  return res;
}

// ---- Fanart.tv ---------------------------------------------------------------------------------------------------

/** Meilleure image d'une liste Fanart : langue demandée, puis anglais, puis sans langue, puis la plus aimée. */
export function pickFanart(list, lang) {
  if (!Array.isArray(list) || !list.length) return null;
  const score = (x) => (x.lang === lang ? 3 : x.lang === "en" ? 2 : (x.lang === "00" || !x.lang) ? 1 : 0) * 100000 + (Number(x.likes) || 0);
  const best = [...list].filter((x) => typeof x?.url === "string" && x.url.startsWith("https://")).sort((a, b) => score(b) - score(a))[0];
  return best ? best.url : null;
}

/** Réponse Fanart réduite : logo du titre, fond HD, vignette paysage. */
export function reduceFanart(kind, body, lang) {
  if (kind === "movie") {
    return {
      logo: pickFanart(body.hdmovielogo, lang) || pickFanart(body.movielogo, lang),
      background: pickFanart(body.moviebackground, lang),
      thumb: pickFanart(body.moviethumb, lang),
    };
  }
  return {
    logo: pickFanart(body.hdtvlogo, lang) || pickFanart(body.clearlogo, lang),
    background: pickFanart(body.showbackground, lang),
    thumb: pickFanart(body.tvthumb, lang),
  };
}

/** /api/fanart/movie/{tmdbId} ou /api/fanart/tv/{tvdbId}?lang=fr */
export async function fanartProxy(rest, searchParams, env, { fetchFn = fetch, cache = globalThis.caches?.default } = {}) {
  const m = rest.match(/^(movie|tv)\/([0-9]+)$/);
  if (!m || !ID.test(m[2])) return json({ error: "not_allowed" }, 404);
  const lang = LANG.test(searchParams.get("lang") || "") ? searchParams.get("lang") : "en";
  if (!env.FANART_API_KEY) return json({ error: "fanart_not_configured" }, 503);
  const [, kind, id] = m;
  return cached(cache, `fanart/${kind}/${id}/${lang}`, 7 * DAY, async () => {
    const up = `https://webservice.fanart.tv/v3/${kind === "movie" ? "movies" : "tv"}/${id}?api_key=${encodeURIComponent(env.FANART_API_KEY)}`;
    const r = await fetchFn(up, { headers: { accept: "application/json" }, redirect: "manual" });
    if (r.status === 404) return json({ logo: null, background: null, thumb: null }, 200, 7 * DAY);
    if (r.status !== 200) return json({ error: "upstream" }, 502);
    return json(reduceFanart(kind, await r.json(), lang), 200, 7 * DAY);
  });
}

// ---- TVmaze ------------------------------------------------------------------------------------------------------

const stripHtml = (s) => (typeof s === "string" ? s.replace(/<[^>]*>/g, "").replace(/\s+/g, " ").trim() : null) || null;

export function reduceEpisode(e) {
  if (!e || typeof e !== "object") return null;
  const at = Date.parse(e.airstamp || "");
  return {
    season: Number.isInteger(e.season) ? e.season : null,
    number: Number.isInteger(e.number) ? e.number : null,
    name: typeof e.name === "string" ? e.name : null,
    airAt: Number.isFinite(at) ? at : null,
    image: e.image?.original || e.image?.medium || null,
    summary: stripHtml(e.summary),
  };
}

/** Série TVmaze réduite : statut, chaîne, épisodes précédent et prochain. */
export function reduceShow(s) {
  return {
    id: s.id,
    status: typeof s.status === "string" ? s.status : null,
    network: s.network?.name || s.webChannel?.name || null,
    previous: reduceEpisode(s._embedded?.previousepisode),
    next: reduceEpisode(s._embedded?.nextepisode),
  };
}

/** /api/tvmaze/show?imdb=tt0944947 ou ?tvdb=121361 */
export async function tvmazeProxy(searchParams, env, { fetchFn = fetch, cache = globalThis.caches?.default } = {}) {
  const imdb = searchParams.get("imdb");
  const tvdb = searchParams.get("tvdb");
  let q;
  if (imdb && IMDB.test(imdb)) q = `imdb=${imdb}`;
  else if (tvdb && ID.test(tvdb)) q = `thetvdb=${tvdb}`;
  else return json({ error: "not_allowed" }, 404);
  return cached(cache, `tvmaze/${q}`, 6 * 3600, async () => {
    const look = await fetchFn(`https://api.tvmaze.com/lookup/shows?${q}`, { headers: { accept: "application/json" }, redirect: "manual" });
    let showId = null;
    if (look.status === 301 || look.status === 302) showId = (look.headers.get("location") || "").match(/\/shows\/([0-9]+)/)?.[1] || null;
    else if (look.status === 200) showId = (await look.json())?.id ?? null;
    else if (look.status === 404) return json({ id: null, status: null, network: null, previous: null, next: null }, 200, DAY);
    if (!showId) return json({ error: "upstream" }, 502);
    const r = await fetchFn(`https://api.tvmaze.com/shows/${showId}?embed[]=previousepisode&embed[]=nextepisode`, { headers: { accept: "application/json" } });
    if (r.status !== 200) return json({ error: "upstream" }, 502);
    return json(reduceShow(await r.json()), 200, 6 * 3600);
  });
}

// ---- TheSportsDB -------------------------------------------------------------------------------------------------

/** Nom d'équipe tel qu'écrit dans un guide (« PSG », « Paris SG », « Real Madrid ») : 2 à 40 caractères simples. */
const TEAM = /^[\p{L}\p{N} .'&()-]{2,40}$/u;

export function reduceTeam(t) {
  if (!t) return null;
  return {
    name: t.strTeam || null,
    short: t.strTeamShort || null,
    badge: t.strBadge || t.strTeamBadge || null,
    league: t.strLeague || null,
    sport: t.strSport || null,
  };
}

/** /api/sports/team?name=Paris%20SG → écusson et compétition (null si inconnue). */
export async function sportsTeamProxy(searchParams, env, { fetchFn = fetch, cache = globalThis.caches?.default } = {}) {
  const name = (searchParams.get("name") || "").trim();
  if (!TEAM.test(name)) return json({ error: "not_allowed" }, 404);
  const key = env.SPORTSDB_API_KEY || "123";
  const norm = name.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
  return cached(cache, `sports/team/${encodeURIComponent(norm)}`, 30 * DAY, async () => {
    const r = await fetchFn(`https://www.thesportsdb.com/api/v1/json/${encodeURIComponent(key)}/searchteams.php?t=${encodeURIComponent(name)}`, { headers: { accept: "application/json" } });
    if (r.status !== 200) return json({ error: "upstream" }, 502);
    const body = await r.json();
    const teams = Array.isArray(body?.teams) ? body.teams : [];
    return json({ team: reduceTeam(teams[0]) }, 200, 30 * DAY);
  });
}
