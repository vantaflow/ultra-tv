// Espace Sport : scores en direct, calendriers, classements et feuille de match (TheSportsDB).
// Les appareils n'appellent JAMAIS TheSportsDB : tout passe par ce Worker, par ligue, avec cache Cloudflare
//   scores en direct 60 s (un seul appel amont partagé par toutes les ligues) · calendriers 1 h · classements 6 h
//   feuille de match 2 min (en cours) ou 6 h (terminé).
// Aucune donnée utilisateur n'est transmise : seulement une clé de ligue de la liste blanche ou un identifiant de match.
// Clé : SPORTSDB_API_KEY (offre payante, plus d'événements par appel) sinon la clé publique gratuite « 123 ».
// La clé gratuite est limitée (≈ 30 appels/min) : en cas de refus (429) ou d'erreur, la dernière réponse connue est
// resservie (jusqu'à 24 h) et un échec est mémorisé 30 s pour ne pas marteler l'amont.

const json = (body, status = 200, maxAge = 0) => new Response(JSON.stringify(body), {
  status,
  headers: { "content-type": "application/json", "cache-control": maxAge > 0 ? `public, max-age=${maxAge}` : "no-store" },
});

const HOUR = 3600;
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

/** Saison TheSportsDB de la ligue à cette date (les saisons « split » commencent en juillet). */
export function seasonOf(league, now = Date.now()) {
  const d = new Date(now);
  const y = d.getUTCFullYear();
  if (!league.split) return String(y);
  return d.getUTCMonth() >= 6 ? `${y}-${y + 1}` : `${y - 1}-${y}`;
}

const num = (v) => { const n = Number.parseInt(v, 10); return Number.isFinite(n) ? n : null; };
const httpsOrNull = (u) => (typeof u === "string" && u.startsWith("https://") ? u : null);
const str = (v, max = 80) => (typeof v === "string" && v.trim() ? v.trim().slice(0, max) : null);

const DONE = new Set(["FT", "AET", "PEN", "AOT", "AP", "FINAL", "MATCH FINISHED", "FINISHED", "AFTER OVERTIME", "AFTER PENALTIES", "F/OT", "AWARDED", "WO"]);
const LATER = new Set(["NS", "NOT STARTED", "TBD", "TIME TO BE DEFINED", ""]);
const OFF = new Set(["PST", "POSTPONED", "CANC", "CANCELLED", "ABD", "ABANDONED", "SUSP", "SUSPENDED"]);

/** État normalisé d'un match : pre (à venir), live, post (terminé), off (reporté / annulé). */
export function stateOf(status, tsMs, hasScore, now = Date.now()) {
  const s = String(status ?? "").trim().toUpperCase();
  if (OFF.has(s)) return "off";
  if (DONE.has(s)) return "post";
  if (LATER.has(s)) {
    if (tsMs && now - tsMs > 4 * HOUR * 1000 && hasScore) return "post";
    return "pre";
  }
  // tout autre statut (1H, HT, 2H, ET, Q1…Q4, OT, P1…P3, « In Progress », « 45' ») = en cours, sauf si très ancien
  if (tsMs && now - tsMs > 6 * HOUR * 1000) return hasScore ? "post" : "off";
  return "live";
}

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
 * Réponse mémorisée [ttl] ; sinon [produce] (qui renvoie un objet, ou null en cas d'échec amont). Un échec resert
 * la copie ancienne (24 h) et, à défaut, mémorise l'échec 30 s.
 */
async function through(cache, k, ttl, produce) {
  const fresh = await read(cache, k);
  if (fresh && fresh.err !== true) return json(fresh, 200, ttl);
  if (fresh && fresh.err === true) return json({ error: "upstream" }, 502);
  let body = null;
  try { body = await produce(); } catch { body = null; }
  if (body) {
    await put(cache, k, ttl, body);
    await put(cache, `stale/${k}`, TTL.stale, body);
    return json(body, 200, ttl);
  }
  const old = await read(cache, `stale/${k}`);
  if (old) return json({ ...old, stale: true }, 200, TTL.fail);
  await put(cache, k, TTL.fail, { err: true });
  return json({ error: "upstream" }, 502);
}

function api(env, fetchFn) {
  const key = env.SPORTSDB_API_KEY || "123";
  const base = `https://www.thesportsdb.com/api/v1/json/${encodeURIComponent(key)}`;
  /** JSON de l'amont ou null (429, erreur, corps illisible). */
  return async (path) => {
    const r = await fetchFn(`${base}/${path}`, { headers: { accept: "application/json" } });
    if (r.status !== 200) return null;
    try { return await r.json(); } catch { return null; }
  };
}

// ---- scores en direct -----------------------------------------------------------------------------------------------

/** Tous les scores en cours d'un coup (une seule requête amont par minute, partagée par toutes les ligues). */
async function liveAll(cache, get, now) {
  const res = await through(cache, "live/all", TTL.live, async () => {
    const body = await get("livescore.php");
    if (!body) return null;
    const list = Array.isArray(body.livescore) ? body.livescore : [];
    return {
      events: list
        .map((e) => ({ league: String(e.idLeague), ev: reduceEvent({ ...e, strEvent: `${e.strHomeTeam} vs ${e.strAwayTeam}` }, now) }))
        .filter((x) => x.ev && x.ev.st === "live"),
    };
  });
  if (res.status !== 200) return null;
  return (await res.json()).events || [];
}

// ---- points d'entrée ------------------------------------------------------------------------------------------------

/** /api/sports/league?id=ligue1&view=live|schedule|table */
export async function sportsLeagueProxy(searchParams, env, { fetchFn = fetch, cache = globalThis.caches?.default, now = Date.now() } = {}) {
  const key = searchParams.get("id") || "";
  const league = Object.hasOwn(LEAGUES, key) ? LEAGUES[key] : null;
  const view = searchParams.get("view") || "schedule";
  if (!league || !["live", "schedule", "table"].includes(view)) return json({ error: "not_allowed" }, 404);
  const get = api(env, fetchFn);

  if (view === "live") {
    const all = await liveAll(cache, get, now);
    if (all === null) return json({ error: "upstream" }, 502);
    return json({ league: key, events: all.filter((x) => x.league === String(league.id)).map((x) => x.ev) }, 200, TTL.live);
  }

  if (view === "schedule") {
    return through(cache, `schedule/${key}`, TTL.schedule, async () => {
      const season = seasonOf(league, now);
      const next = await get(`eventsnextleague.php?id=${league.id}`);
      const past = await get(`eventspastleague.php?id=${league.id}`);
      if (!next && !past) return null;
      let events = [...(next?.events || []), ...(past?.events || [])].map((e) => reduceEvent(e, now));
      // La clé gratuite ne rend qu'un match par appel : on complète par la journée (ou le jour) concernée.
      const rounds = [...new Set(events.map((e) => e?.r).filter((r) => r))].slice(0, 2);
      if (rounds.length) {
        const wanted = new Set(rounds);
        if (rounds.length === 1 && rounds[0] > 1) wanted.add(rounds[0] - 1);
        for (const r of wanted) {
          const body = await get(`eventsround.php?id=${league.id}&r=${r}&s=${season}`);
          if (body?.events) events = events.concat(body.events.map((e) => reduceEvent(e, now)));
        }
      } else {
        for (const off of [0, 1]) {
          const day = new Date(now + off * 86400000).toISOString().slice(0, 10);
          const body = await get(`eventsday.php?d=${day}&l=${league.id}`);
          if (body?.events) events = events.concat(body.events.map((e) => reduceEvent(e, now)));
        }
      }
      return { league: key, season, events: windowed(dedupe(events), now) };
    });
  }

  // classement : saison courante, sinon la précédente (début de saison)
  return through(cache, `table/${key}`, TTL.table, async () => {
    const tryS = async (s) => {
      const b = await get(`lookuptable.php?l=${league.id}&s=${s}`);
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
    return { league: key, season, rows: rows.map(reduceRow).filter(Boolean).slice(0, 40) };
  });
}

/** /api/sports/match?id=2489551 → score, temps forts et statistiques (3 requêtes amont au plus, mises en cache). */
export async function sportsMatchProxy(searchParams, env, { fetchFn = fetch, cache = globalThis.caches?.default, now = Date.now() } = {}) {
  const id = searchParams.get("id") || "";
  if (!/^[1-9][0-9]{0,9}$/.test(id)) return json({ error: "not_allowed" }, 404);
  const get = api(env, fetchFn);
  // Le TTL dépend de l'état du match, connu seulement après la lecture : on lit d'abord la copie récente.
  const k = `match/${id}`;
  const fresh = await read(cache, k);
  if (fresh && fresh.err !== true) return json(fresh, 200, 60);
  const ev = await get(`lookupevent.php?id=${id}`).catch(() => null);
  const event = reduceEvent(ev?.events?.[0], now);
  if (!event) {
    const old = await read(cache, `stale/${k}`);
    return old ? json({ ...old, stale: true }, 200, TTL.fail) : json({ error: "upstream" }, 502);
  }
  const tl = await get(`lookuptimeline.php?id=${id}`).catch(() => null);
  const stats = event.st === "pre" ? null : await get(`lookupeventstats.php?id=${id}`).catch(() => null);
  const body = {
    event: { ...event, v: str(ev.events[0].strVenue), lg: str(ev.events[0].strLeague) },
    timeline: reduceTimeline(tl?.timeline),
    stats: reduceStats(stats?.eventstats),
  };
  const ttl = event.st === "post" ? TTL.matchDone : event.st === "live" ? TTL.matchLive : TTL.schedule;
  await put(cache, k, ttl, body);
  await put(cache, `stale/${k}`, TTL.stale, body);
  return json(body, 200, Math.min(ttl, 120));
}
