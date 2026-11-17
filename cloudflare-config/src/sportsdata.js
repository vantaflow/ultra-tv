// Espace Sport : scores en direct, calendriers, classements et feuille de match, en croisant plusieurs sources gratuites.
// Les appareils n'appellent JAMAIS un fournisseur : tout passe par ce Worker, par ligue, avec cache Cloudflare partagé
//   scores en direct 60 s (un seul jeu d'appels amont partagé par toutes les ligues) · calendriers 1 h · classements 6 h
//   feuille de match 2 min (en cours) ou 6 h (terminé).
// Fournisseurs (voir sportsprov.js) : football-data.org, Jolpica-F1, API-Football (100 requêtes/jour, réservé à la Botola
// et aux enrichissements : compositions, statistiques), TheSportsDB. Pour chaque requête : le meilleur fournisseur qui
// couvre la ligue ET la capacité ET a du budget (sportsbudget.js) ; sinon le suivant ; sinon la dernière réponse connue
// (jusqu'à 24 h), marquée `stale`. Toutes les réponses ont le même format quel que soit le fournisseur.
// Aucune donnée utilisateur n'est transmise : seulement une clé de ligue de la liste blanche ou un identifiant de match.
// Plafond : 50 sous-requêtes (fetch + cache) par appel du Worker, pas de boucle ouverte.

import { createCtx } from "./sportsbudget.js";
import { HOUR, httpsOrNull, mergeEvents, num, sameMatch, stateOf, str } from "./sportscommon.js";
import { afSeason, candidates, LONG_TTL, PROVIDERS } from "./sportsprov.js";

export { stateOf };

const json = (body, status = 200, maxAge = 0) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json", "cache-control": maxAge > 0 ? `public, max-age=${maxAge}` : "no-store" },
});

export const TTL = { live: 60, schedule: HOUR, table: 6 * HOUR, matchLive: 120, matchDone: 6 * HOUR, stale: 24 * HOUR, fail: 30 };

/** Ligues proposées (liste blanche) : id TheSportsDB, discipline, saison « 2026-2027 » (split) ou « 2026 » (année civile). */
export const LEAGUES = {
  ligue1: { id: 4334, sport: "football", split: true },
  premier: { id: 4328, sport: "football", split: true },
  liga: { id: 4335, sport: "football", split: true },
  seriea: { id: 4332, sport: "football", split: true },
  bundesliga: { id: 4331, sport: "football", split: true },
  ucl: { id: 4480, sport: "football", split: true },
  botola: { id: 4520, sport: "football", split: true },
  nba: { id: 4387, sport: "basket", split: true },
  nfl: { id: 4391, sport: "americanfootball", split: false },
  nhl: { id: 4380, sport: "hockey", split: true },
  mlb: { id: 4424, sport: "baseball", split: false },
  top14: { id: 4430, sport: "rugby", split: true },
  premrugby: { id: 4414, sport: "rugby", split: true },
  f1: { id: 4370, sport: "motor", split: false },
};

/** Saison de la ligue à cette date (les saisons « split » commencent en juillet). */
export function seasonOf(league, now = Date.now()) {
  const d = new Date(now);
  const y = d.getUTCFullYear();
  if (!league.split) return String(y);
  return d.getUTCMonth() >= 6 ? `${y}-${y + 1}` : `${y - 1}-${y}`;
}

// ---- TheSportsDB : réduction des données ------------------------------------------------------------------------------

/** UTC : TheSportsDB donne « 2026-11-08T19:45:00 » sans fuseau. */
function stamp(e) {
  const raw = e.strTimestamp || (e.dateEvent ? `${e.dateEvent}T${e.strTime || "00:00:00"}` : null);
  if (!raw) return null;
  const t = Date.parse(/[zZ]|[+-]\d\d:?\d\d$/.test(raw) ? raw : `${raw}Z`);
  return Number.isFinite(t) ? t : null;
}

/** Match réduit : i id, t début (ms), n nom, h/a équipes, hb/ab écussons, hs/as scores, st état, p progression, r journée. */
export function reduceEvent(e, now = Date.now()) {
  if (!e || !e.idEvent) return null;
  const t = stamp(e);
  const hs = num(e.intHomeScore);
  const as = num(e.intAwayScore);
  return {
    i: String(e.idEvent), t, n: str(e.strEvent, 120),
    h: str(e.strHomeTeam), a: str(e.strAwayTeam),
    hb: httpsOrNull(e.strHomeTeamBadge), ab: httpsOrNull(e.strAwayTeamBadge),
    hs, as, st: stateOf(e.strStatus, t, hs !== null || as !== null, now),
    p: str(e.strProgress, 12), r: num(e.intRound),
  };
}

const dedupe = (list) => {
  const m = new Map();
  for (const e of list) if (e) m.set(e.i, e);
  return [...m.values()].sort((x, y) => (x.t ?? 0) - (y.t ?? 0));
};

/** Fenêtre utile : 7 jours en arrière, 21 jours en avant, au plus 80 matchs. */
export function windowed(events, now = Date.now()) {
  const from = now - 7 * 86400000;
  const to = now + 21 * 86400000;
  return events.filter((e) => e.t === null || (e.t >= from && e.t <= to)).slice(0, 80);
}

/** Ligne de classement réduite. */
export function reduceRow(r) {
  if (!r) return null;
  return {
    rk: num(r.intRank), team: str(r.strTeam), badge: httpsOrNull(r.strBadge), g: str(r.strGroup, 40),
    p: num(r.intPlayed), w: num(r.intWin), d: num(r.intDraw), l: num(r.intLoss),
    gf: num(r.intGoalsFor), ga: num(r.intGoalsAgainst), gd: num(r.intGoalDifference), pts: num(r.intPoints),
    form: str(r.strForm, 10), note: str(r.strDescription, 60),
  };
}

const TL_KIND = (k) => {
  const s = String(k || "").toLowerCase();
  if (s === "goal") return "goal";
  if (s === "card") return "card";
  if (s.startsWith("subst")) return "sub";
  if (s === "var") return "var";
  return "other";
};

/** Temps forts réduits : buts, cartons, remplacements (côté domicile : h true). */
export function reduceTimeline(list) {
  return (Array.isArray(list) ? list : []).map((x) => ({
    k: TL_KIND(x.strTimeline), d: str(x.strTimelineDetail, 40), h: String(x.strHome).toLowerCase() === "yes",
    m: num(x.intTime), pl: str(x.strPlayer), as: str(x.strAssist),
  })).filter((x) => x.k !== "other").sort((a, b) => (a.m ?? 0) - (b.m ?? 0)).slice(0, 60);
}

export function reduceStats(list) {
  return (Array.isArray(list) ? list : []).map((x) => ({ k: str(x.strStat, 40), h: num(x.intHome), a: num(x.intAway) }))
    .filter((x) => x.k && x.h !== null && x.a !== null).slice(0, 30);
}

// ---- TheSportsDB : adaptateur (même contrat que les autres fournisseurs) ---------------------------------------------

const sdbBase = (ctx) => `https://www.thesportsdb.com/api/v1/json/${encodeURIComponent(ctx.env?.SPORTSDB_API_KEY || "123")}`;
const sdbGet = (ctx, cap, path) => ctx.fetchJson("sdb", cap, `${sdbBase(ctx)}/${path}`);
const SDB_KEY_OF = Object.fromEntries(Object.entries(LEAGUES).map(([k, l]) => [String(l.id), k]));

const sdb = {
  async live(ctx) {
    const body = await sdbGet(ctx, "live", "livescore.php");
    if (!body) return null;
    const list = Array.isArray(body.livescore) ? body.livescore : [];
    return list
      .map((e) => ({ league: SDB_KEY_OF[String(e.idLeague)], ev: reduceEvent({ ...e, strEvent: `${e.strHomeTeam} vs ${e.strAwayTeam}` }, ctx.now) }))
      .filter((x) => x.league && x.ev && x.ev.st === "live");
  },
  async schedule(ctx, key, league, id, now) {
    const season = seasonOf(league, now);
    const next = await sdbGet(ctx, "schedule", `eventsnextleague.php?id=${id}`);
    const past = await sdbGet(ctx, "schedule", `eventspastleague.php?id=${id}`);
    if (!next && !past) return null;
    let events = [...(next?.events || []), ...(past?.events || [])].map((e) => reduceEvent(e, now));
    // La clé gratuite ne rend qu'un match par appel : on complète par la journée (ou le jour) concernée (au plus 4 appels).
    const rounds = [...new Set(events.map((e) => e?.r).filter((r) => r))].slice(0, 2);
    if (rounds.length) {
      const wanted = new Set(rounds);
      if (rounds.length === 1 && rounds[0] > 1) wanted.add(rounds[0] - 1);
      for (const r of wanted) {
        const body = await sdbGet(ctx, "schedule", `eventsround.php?id=${id}&r=${r}&s=${season}`);
        if (body?.events) events = events.concat(body.events.map((e) => reduceEvent(e, now)));
      }
    } else {
      for (const off of [0, 1]) {
        const day = new Date(now + off * 86400000).toISOString().slice(0, 10);
        const body = await sdbGet(ctx, "schedule", `eventsday.php?d=${day}&l=${id}`);
        if (body?.events) events = events.concat(body.events.map((e) => reduceEvent(e, now)));
      }
    }
    return { season, events: events.filter(Boolean) };
  },
  // classement : saison courante, sinon la précédente (début de saison)
  async table(ctx, key, league, id, now) {
    const tryS = async (s) => {
      const b = await sdbGet(ctx, "table", `lookuptable.php?l=${id}&s=${s}`);
      return b === null ? undefined : (Array.isArray(b.table) ? b.table : []);
    };
    let season = seasonOf(league, now);
    let rows = await tryS(season);
    if (rows === undefined) return null;
    if (!rows.length) {
      const prev = league.split ? season.split("-").map((x) => Number(x) - 1).join("-") : String(Number(season) - 1);
      const r2 = await tryS(prev);
      if (r2?.length) { rows = r2; season = prev; }
    }
    return { season, rows: rows.map(reduceRow).filter(Boolean).slice(0, 40) };
  },
  async match(ctx, id, now) {
    const ev = await sdbGet(ctx, "match", `lookupevent.php?id=${id}`);
    const event = reduceEvent(ev?.events?.[0], now);
    if (!event) return null;
    const tl = await sdbGet(ctx, "match", `lookuptimeline.php?id=${id}`);
    const stats = event.st === "pre" ? null : await sdbGet(ctx, "match", `lookupeventstats.php?id=${id}`);
    return {
      event: { ...event, v: str(ev.events[0].strVenue), lg: str(ev.events[0].strLeague) },
      timeline: reduceTimeline(tl?.timeline), stats: reduceStats(stats?.eventstats),
    };
  },
};

PROVIDERS.sdb.adapter = sdb;
// TheSportsDB couvre toute la liste blanche : rang 5 (après football-data / Jolpica, avant API-Football hors Botola) ;
// Botola : rang 6, API-Football passe devant (rang 1) tant qu'il a du budget.
PROVIDERS.sdb.leagues = Object.fromEntries(Object.entries(LEAGUES).map(([k, l]) => [k, { code: l.id, rank: k === "botola" ? 6 : 5 }]));

// ---- cache avec reprise sur l'ancienne réponse ----------------------------------------------------------------------

const keyReq = (k) => new Request(`https://sportsdata.cache/${k}`);

async function put(cache, k, ttl, body) {
  if (!cache) return;
  await cache.put(keyReq(k), new Response(JSON.stringify(body), { headers: { "content-type": "application/json", "cache-control": `public, max-age=${ttl}` } }));
}
async function read(cache, k) {
  const hit = cache ? await cache.match(keyReq(k)) : undefined;
  return hit ? hit.json() : null;
}

/**
 * Réponse mémorisée [ttl] ; sinon [produce] (qui renvoie un objet, ou null en cas d'échec amont ; `__ttl` y impose un
 * cache plus long). Un échec resert la copie ancienne (24 h, marquée stale) et, à défaut, mémorise l'échec 30 s.
 */
async function through(cache, k, ttl, produce) {
  const fresh = await read(cache, k);
  if (fresh && fresh.err !== true) return json(fresh, 200, ttl);
  if (fresh && fresh.err === true) return json({ error: "upstream" }, 502);
  let body = null;
  try { body = await produce(); } catch { body = null; }
  if (body) {
    const t = body.__ttl || ttl;
    delete body.__ttl;
    await put(cache, k, t, body);
    await put(cache, `stale/${k}`, TTL.stale, body);
    return json(body, 200, t);
  }
  const old = await read(cache, `stale/${k}`);
  if (old) return json({ ...old, stale: true }, 200, TTL.fail);
  await put(cache, k, TTL.fail, { err: true });
  return json({ error: "upstream" }, 502);
}

/**
 * Premier fournisseur (rang, puis couverture, puis budget) dont [run] renvoie un résultat utilisable. Un résultat vide
 * (`isEmpty`) laisse la main au suivant mais reste le recours si tous les autres échouent.
 */
async function viaProviders(ctx, key, cap, run, isEmpty = () => false) {
  let emptyBest = null;
  for (const p of candidates(key, cap, ctx.env)) {
    if (!(await ctx.canUse(p.id, cap))) continue;
    let r = null;
    try { r = await run(p, p.leagues[key].code); } catch { r = null; }
    if (!r) continue;
    if (isEmpty(r)) { emptyBest ||= { prov: p.id, r }; continue; }
    return { prov: p.id, r };
  }
  return emptyBest;
}

// ---- scores en direct -----------------------------------------------------------------------------------------------

/** Fusionne des listes {league, ev} de plusieurs sources : la première fait foi, les suivantes complètent et ajoutent. */
function mergeLive(lists) {
  const byLeague = new Map();
  for (const list of lists) {
    const grouped = new Map();
    for (const x of list) { if (!grouped.has(x.league)) grouped.set(x.league, []); grouped.get(x.league).push(x.ev); }
    for (const [lg, evs] of grouped) byLeague.set(lg, byLeague.has(lg) ? mergeEvents(byLeague.get(lg), evs, { addUnmatched: true }) : evs);
  }
  return [...byLeague].flatMap(([league, evs]) => evs.filter((e) => e.st === "live").map((ev) => ({ league, ev })));
}

/**
 * Tous les scores en cours d'un coup, partagés par toutes les ligues : football-data et TheSportsDB (un appel chacun par
 * minute au plus), croisés par date ±3 h + équipes. API-Football n'en fait pas partie (quota journalier).
 */
async function liveAll(ctx, key, now) {
  const res = await through(ctx.cache, "live/all", TTL.live, async () => {
    const lists = [];
    for (const id of ["fd", "sdb"]) {
      if (!(await ctx.canUse(id, "live"))) continue;
      let l = null;
      try { l = await PROVIDERS[id].adapter.live(ctx); } catch { l = null; }
      if (l) lists.push(l);
    }
    if (!lists.length) return null;
    return { events: mergeLive(lists) };
  });
  if (res.status !== 200) return null;
  return (await res.json()).events || [];
}

/** Direct d'une ligue que seul API-Football couvre bien (Botola) : appel seulement si un match est plausible, cache 5 min. */
async function liveReserved(ctx, key, league, now) {
  const af = PROVIDERS.af;
  if (!af.leagues[key] || af.leagues[key].rank >= 5 || !af.hasKey(ctx.env)) return [];
  const sched = await read(ctx.cache, `schedule/${key}`);
  // calendrier connu et aucun match autour de maintenant : inutile de dépenser un jeton
  if (sched?.events && !sched.events.some((e) => e.t !== null && Math.abs(e.t - now) <= 3 * 3600000)) return [];
  const res = await through(ctx.cache, `live/af/${key}`, LONG_TTL.af.live, async () => {
    if (!(await ctx.canUse("af", "live"))) return null;
    const l = await af.adapter.live(ctx, key, league, af.leagues[key].code);
    return l ? { events: l.map((x) => x.ev) } : null;
  });
  return res.status === 200 ? (await res.json()).events || [] : [];
}

// ---- points d'entrée ------------------------------------------------------------------------------------------------

const wrapOpts = (env, { fetchFn = fetch, cache = globalThis.caches?.default, now = Date.now() } = {}) =>
  createCtx(env, { fetchFn, cache, now, registry: PROVIDERS });

/** Superpose les scores en direct déjà connus (cache partagé, aucun appel amont) aux matchs du calendrier en cours. */
async function overlayLive(ctx, key, res) {
  if (res.status !== 200) return res;
  let body;
  try { body = await res.clone().json(); } catch { return res; }
  if (!body.events?.some((e) => e.t !== null && Math.abs(e.t - ctx.now) <= 4 * 3600000 && e.st !== "post")) return res;
  const snap = await read(ctx.cache, "live/all");
  const live = (snap?.events || []).filter((x) => x.league === key).map((x) => x.ev);
  if (!live.length) return res;
  return new Response(JSON.stringify({ ...body, events: mergeEvents(body.events, live) }), { status: 200, headers: res.headers });
}

/** /api/sports/league?id=ligue1&view=live|schedule|table */
export async function sportsLeagueProxy(searchParams, env, opts = {}) {
  const key = searchParams.get("id") || "";
  const league = Object.hasOwn(LEAGUES, key) ? LEAGUES[key] : null;
  const view = searchParams.get("view") || "schedule";
  if (!league || !["live", "schedule", "table"].includes(view)) return json({ error: "not_allowed" }, 404);
  const ctx = wrapOpts(env, opts);
  const now = ctx.now;
  try {
    if (view === "live") {
      const all = await liveAll(ctx, key, now);
      const extra = await liveReserved(ctx, key, league, now);
      if (all === null && !extra.length) return json({ error: "upstream" }, 502);
      let events = (all || []).filter((x) => x.league === key).map((x) => x.ev);
      if (extra.length) events = mergeEvents(events, extra, { addUnmatched: true });
      return json({ league: key, events }, 200, TTL.live);
    }

    if (view === "schedule") {
      const res = await through(ctx.cache, `schedule/${key}`, TTL.schedule, async () => {
        const got = await viaProviders(ctx, key, "schedule", (p, code) => p.adapter.schedule(ctx, key, league, code, now), (r) => !r.events.length);
        if (!got) return null;
        const body = { league: key, season: got.r.season || seasonOf(league, now), events: windowed(dedupe(got.r.events), now), src: got.prov };
        if (LONG_TTL[got.prov]) body.__ttl = LONG_TTL[got.prov].schedule;
        return body;
      });
      return await overlayLive(ctx, key, res);
    }

    return await through(ctx.cache, `table/${key}`, TTL.table, async () => {
      const got = await viaProviders(ctx, key, "table", (p, code) => p.adapter.table(ctx, key, league, code, now), (r) => !r.rows.length);
      if (!got) return null;
      const body = { league: key, season: got.r.season || seasonOf(league, now), rows: got.r.rows.slice(0, 40), src: got.prov };
      if (LONG_TTL[got.prov]) body.__ttl = LONG_TTL[got.prov].table;
      return body;
    });
  } finally {
    await ctx.flush();
  }
}

// ---- feuille de match -----------------------------------------------------------------------------------------------

/** Identifiants : numérique = TheSportsDB (historique) ; fd-N football-data ; af-N API-Football ; jo-AAAA-N Jolpica. */
function parseMatchId(id) {
  let m = /^(?:(fd|af)-)?([1-9][0-9]{0,9})$/.exec(id);
  if (m) return { prov: m[1] || "sdb", raw: m[2] };
  m = /^jo-(20[0-9]{2}-[1-9][0-9]?)$/.exec(id);
  return m ? { prov: "jo", raw: m[1] } : null;
}

/**
 * Enrichit une feuille de match football (football-data ou TheSportsDB) avec API-Football : statistiques d'équipe,
 * compositions et statistiques des joueurs. Au plus 2 requêtes (jour de la ligue — mis en cache 6 h et partagé — puis
 * fixture), seulement pour un match commencé, et seulement s'il reste plus de 40 % du budget « feuille de match ».
 */
async function enrichWithAf(ctx, key, body) {
  const af = PROVIDERS.af;
  const lg = key && af.leagues[key];
  const ev = body.event;
  if (!lg || !af.hasKey(ctx.env) || ev.st === "pre" || ev.t === null) return body;
  if (!(await ctx.canUse("af", "match")) || (await ctx.headroom("af", "match")) < 0.4) return body;
  const day = new Date(ev.t).toISOString().slice(0, 10);
  const league = LEAGUES[key];
  const dk = `afday/${key}/${day}`;
  let list = await read(ctx.cache, dk);
  if (!list) {
    const s = afSeason(league, ev.t);
    const b = await ctx.fetchJson("af", "match", `https://v3.football.api-sports.io/fixtures?league=${lg.code}&season=${s}&date=${day}`, { "x-apisports-key": ctx.env.API_FOOTBALL_KEY });
    if (!b || !Array.isArray(b.response)) return body;
    list = { events: b.response.map((f) => ({ i: String(f.fixture?.id), t: f.fixture?.timestamp ? f.fixture.timestamp * 1000 : null, h: f.teams?.home?.name ?? null, a: f.teams?.away?.name ?? null })) };
    await put(ctx.cache, dk, 6 * HOUR, list);
  }
  const hit = list.events.find((x) => sameMatch(x, ev));
  if (!hit) return body;
  const extra = await af.adapter.match(ctx, hit.i, ctx.now);
  if (!extra) return body;
  return {
    ...body,
    timeline: body.timeline.length ? body.timeline : extra.timeline,
    stats: body.stats.length ? body.stats : extra.stats,
    lineups: extra.lineups, players: extra.players,
  };
}

/** /api/sports/match?id=… → score, temps forts, statistiques (et compositions / joueurs si disponibles), mis en cache. */
export async function sportsMatchProxy(searchParams, env, opts = {}) {
  const id = searchParams.get("id") || "";
  const parsed = parseMatchId(id);
  if (!parsed) return json({ error: "not_allowed" }, 404);
  const ctx = wrapOpts(env, opts);
  const now = ctx.now;
  const k = `match/${id}`;
  try {
    // Le TTL dépend de l'état du match, connu seulement après la lecture : on lit d'abord la copie récente.
    const fresh = await read(ctx.cache, k);
    if (fresh && fresh.err !== true) return json(fresh, 200, 60);
    const staleOrFail = async () => {
      const old = await read(ctx.cache, `stale/${k}`);
      return old ? json({ ...old, stale: true }, 200, TTL.fail) : json({ error: "upstream" }, 502);
    };
    const prov = PROVIDERS[parsed.prov];
    if (!(await ctx.canUse(prov.id, "match"))) return await staleOrFail();
    let body = null;
    try { body = await prov.adapter.match(ctx, parsed.raw, now); } catch { body = null; }
    if (!body) return await staleOrFail();
    if (parsed.prov === "fd") {
      // la ligue se déduit du code de compétition football-data ; l'enrichissement est facultatif
      const key = Object.keys(PROVIDERS.fd.leagues).find((x) => PROVIDERS.fd.leagues[x].code === body._comp);
      try { body = await enrichWithAf(ctx, key, body); } catch { /* ignoré */ }
    }
    delete body._comp; delete body._lid;
    const long = LONG_TTL[parsed.prov];
    const st = body.event.st;
    const ttl = st === "post" ? (long?.matchDone ?? TTL.matchDone) : st === "live" ? (long?.matchLive ?? TTL.matchLive) : TTL.schedule;
    await put(ctx.cache, k, ttl, body);
    await put(ctx.cache, `stale/${k}`, TTL.stale, body);
    return json(body, 200, Math.min(ttl, 120));
  } finally {
    await ctx.flush();
  }
}
