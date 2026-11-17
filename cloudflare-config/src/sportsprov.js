// Fournisseurs de données sportives : couverture par ligue (avec rang de préférence), capacités, budget et adaptateurs
// qui ramènent chaque réponse amont au format unique renvoyé aux appareils :
//   match  { i, t, n, h, a, hb, ab, hs, as, st, p, r }   classement { rk, team, badge, g, p, w, d, l, gf, ga, gd, pts, form, note }
//   temps forts { k, d, h, m, pl, as }                    statistiques { k, h, a }
// Rang : plus petit = préféré. API-Football (100 requêtes/JOUR) n'est préféré que pour ce que les autres ne couvrent pas
// (Botola) ; pour les autres ligues il passe en dernier (rang 9) et ses capacités propres (compositions, statistiques
// des joueurs) servent d'enrichissement derrière un cache long. TheSportsDB est défini dans sportsdata.js (adaptateur
// historique) mais déclaré ici pour que le choix se fasse en un seul endroit.
// Les secrets (FOOTBALL_DATA_KEY, API_FOOTBALL_KEY, SPORTSDB_API_KEY) ne vivent que dans `env` : jamais dans une URL de
// cache, une réponse ou un journal.

import { httpsOrNull, num, stateOf, str } from "./sportscommon.js";

const ms = (iso) => { const t = Date.parse(iso); return Number.isFinite(t) ? t : null; };
const MIN = 60000;

// ---- football-data.org v4 (10 requêtes/min) --------------------------------------------------------------------------

const FD_CODES = { ligue1: "FL1", premier: "PL", liga: "PD", seriea: "SA", bundesliga: "BL1", ucl: "CL" };
const FD_BASE = "https://api.football-data.org/v4";
const fdHeaders = (env) => ({ "X-Auth-Token": env.FOOTBALL_DATA_KEY });

const FD_STATE = { IN_PLAY: "live", PAUSED: "live", LIVE: "live", FINISHED: "post", AWARDED: "post", POSTPONED: "off", CANCELLED: "off", SUSPENDED: "off" };

export function fdEvent(m) {
  if (!m || !m.id) return null;
  const hs = num(m.score?.fullTime?.home);
  const as = num(m.score?.fullTime?.away);
  const st = FD_STATE[m.status] || "pre";
  const h = str(m.homeTeam?.shortName || m.homeTeam?.name);
  const a = str(m.awayTeam?.shortName || m.awayTeam?.name);
  return {
    i: `fd-${m.id}`, t: ms(m.utcDate), n: h && a ? `${h} vs ${a}` : null, h, a,
    hb: httpsOrNull(m.homeTeam?.crest), ab: httpsOrNull(m.awayTeam?.crest),
    hs, as, st, p: m.status === "PAUSED" ? "HT" : (m.minute ? str(String(m.minute), 12) : null), r: num(m.matchday),
  };
}

export function fdRow(r, group) {
  if (!r) return null;
  return {
    rk: num(r.position), team: str(r.team?.shortName || r.team?.name), badge: httpsOrNull(r.team?.crest), g: group ? str(String(group).replace(/_/g, " "), 40) : null,
    p: num(r.playedGames), w: num(r.won), d: num(r.draw), l: num(r.lost), gf: num(r.goalsFor), ga: num(r.goalsAgainst),
    gd: num(r.goalDifference), pts: num(r.points), form: str(r.form, 10), note: null,
  };
}

const FD_STATS = { ball_possession: "Possession", shots: "Shots", shots_on_goal: "Shots on target", corner_kicks: "Corners", fouls: "Fouls", offsides: "Offsides", yellow_cards: "Yellow cards", red_cards: "Red cards", saves: "Saves" };

/** Feuille de match football-data : score, buts / cartons / remplacements, statistiques si l'offre les fournit. */
export function fdMatchBody(m) {
  const event = fdEvent(m);
  if (!event) return null;
  const homeId = m.homeTeam?.id;
  const timeline = [];
  for (const g of m.goals || []) timeline.push({ k: "goal", d: str(g.type, 40), h: g.team?.id === homeId, m: num(g.minute), pl: str(g.scorer?.name), as: str(g.assist?.name) });
  for (const b of m.bookings || []) timeline.push({ k: "card", d: str(b.card, 40), h: b.team?.id === homeId, m: num(b.minute), pl: str(b.player?.name), as: null });
  for (const s of m.substitutions || []) timeline.push({ k: "sub", d: null, h: s.team?.id === homeId, m: num(s.minute), pl: str(s.playerIn?.name), as: str(s.playerOut?.name) });
  timeline.sort((x, y) => (x.m ?? 0) - (y.m ?? 0));
  const stats = [];
  for (const [key, label] of Object.entries(FD_STATS)) {
    const h = num(m.homeTeam?.statistics?.[key]);
    const a = num(m.awayTeam?.statistics?.[key]);
    if (h !== null && a !== null) stats.push({ k: label, h, a });
  }
  return {
    event: { ...event, v: str(m.venue), lg: str(m.competition?.name) },
    timeline: timeline.slice(0, 60), stats, _comp: m.competition?.code || null,
  };
}

const fd = {
  async live(ctx) {
    const b = await ctx.fetchJson("fd", "live", `${FD_BASE}/matches?status=IN_PLAY,PAUSED`, fdHeaders(ctx.env));
    if (!b) return null;
    const rev = Object.fromEntries(Object.entries(FD_CODES).map(([k, c]) => [c, k]));
    return (b.matches || []).map((m) => ({ league: rev[m.competition?.code], ev: fdEvent(m) })).filter((x) => x.league && x.ev && x.ev.st === "live");
  },
  async schedule(ctx, key, _league, code, now) {
    const day = (d) => new Date(now + d * 86400000).toISOString().slice(0, 10);
    const b = await ctx.fetchJson("fd", "schedule", `${FD_BASE}/competitions/${code}/matches?dateFrom=${day(-7)}&dateTo=${day(21)}`, fdHeaders(ctx.env));
    if (!b || !Array.isArray(b.matches)) return null;
    return { season: str(b.filters?.season) || null, events: b.matches.map(fdEvent).filter(Boolean) };
  },
  async table(ctx, key, league, code) {
    const b = await ctx.fetchJson("fd", "table", `${FD_BASE}/competitions/${code}/standings`, fdHeaders(ctx.env));
    if (!b || !Array.isArray(b.standings)) return null;
    const y = num(String(b.season?.startDate || "").slice(0, 4));
    const season = y === null ? null : (league.split ? `${y}-${y + 1}` : String(y));
    const rows = b.standings.filter((s) => s.type === "TOTAL").flatMap((s) => (s.table || []).map((r) => fdRow(r, s.group))).filter(Boolean);
    return { season, rows: rows.slice(0, 40) };
  },
  async match(ctx, raw) {
    const b = await ctx.fetchJson("fd", "match", `${FD_BASE}/matches/${raw}`, fdHeaders(ctx.env));
    return b ? fdMatchBody(b) : null;
  },
};

// ---- Jolpica-F1 (Ergast, sans clé) -----------------------------------------------------------------------------------

const JO_BASE = "https://api.jolpi.ca/ergast/f1";

export function joEvent(r, now = Date.now()) {
  if (!r || !r.round || !r.season) return null;
  const t = ms(`${r.date}T${r.time || "00:00:00Z"}`);
  const loc = [r.Circuit?.Location?.locality, r.Circuit?.Location?.country].filter(Boolean).join(", ");
  return {
    i: `jo-${r.season}-${r.round}`, t, n: str(r.raceName, 120), h: null, a: null, hb: null, ab: null, hs: null, as: null,
    st: t && now - t > 3 * 3600000 ? "post" : "pre", p: null, r: num(r.round), v: str(loc),
  };
}

export function joRow(r) {
  if (!r) return null;
  const name = [r.Driver?.givenName, r.Driver?.familyName].filter(Boolean).join(" ");
  return {
    rk: num(r.position), team: str(name), badge: null, g: null, p: null, w: num(r.wins), d: null, l: null, gf: null, ga: null, gd: null,
    pts: num(r.points), form: null, note: str(r.Constructors?.[0]?.name, 60),
  };
}

/** Résultat d'un Grand Prix : classement d'arrivée en plus de l'événement (champ `results`, sans effet sur les anciens écrans). */
export function joMatchBody(race, now) {
  const event = joEvent(race, now);
  if (!event) return null;
  const results = (race.Results || []).slice(0, 30).map((x) => ({
    pos: num(x.position), driver: str([x.Driver?.givenName, x.Driver?.familyName].filter(Boolean).join(" ")),
    team: str(x.Constructor?.name, 60), time: str(x.Time?.time || x.status, 30), grid: num(x.grid), pts: num(x.points),
  }));
  return { event: { ...event, lg: "Formula 1" }, timeline: [], stats: [], results };
}

const jo = {
  async schedule(ctx, _key, _league, _code, now) {
    const b = await ctx.fetchJson("jo", "schedule", `${JO_BASE}/current.json`);
    const races = b?.MRData?.RaceTable?.Races;
    if (!Array.isArray(races)) return null;
    return { season: str(b.MRData.RaceTable.season), events: races.map((r) => joEvent(r, now)).filter(Boolean) };
  },
  async table(ctx) {
    const b = await ctx.fetchJson("jo", "table", `${JO_BASE}/current/driverStandings.json`);
    const list = b?.MRData?.StandingsTable?.StandingsLists;
    if (!Array.isArray(list)) return null;
    return { season: str(b.MRData.StandingsTable.season), rows: (list[0]?.DriverStandings || []).map(joRow).filter(Boolean).slice(0, 40) };
  },
  async match(ctx, raw, now) {
    const m = /^(\d{4})-(\d{1,2})$/.exec(raw);
    if (!m) return null;
    const b = await ctx.fetchJson("jo", "match", `${JO_BASE}/${m[1]}/${m[2]}/results.json`);
    const race = b?.MRData?.RaceTable?.Races?.[0];
    if (race) return joMatchBody(race, now);
    // course pas encore courue : calendrier seul
    const s = await ctx.fetchJson("jo", "match", `${JO_BASE}/${m[1]}/${m[2]}.json`);
    const r2 = s?.MRData?.RaceTable?.Races?.[0];
    return r2 ? joMatchBody(r2, now) : null;
  },
};

// ---- API-Football v3 (100 requêtes/JOUR : à ménager) -----------------------------------------------------------------

const AF_IDS = { ligue1: 61, premier: 39, liga: 140, seriea: 135, bundesliga: 78, ucl: 2, botola: 200 };
const AF_BASE = "https://v3.football.api-sports.io";
const afHeaders = (env) => ({ "x-apisports-key": env.API_FOOTBALL_KEY });

/** Saison API-Football = année de départ (2026 pour 2026-2027). */
export const afSeason = (league, now) => {
  const d = new Date(now);
  const y = d.getUTCFullYear();
  return league.split && d.getUTCMonth() < 6 ? y - 1 : y;
};

export function afEvent(f) {
  const fx = f?.fixture;
  if (!fx?.id) return null;
  const t = fx.timestamp ? fx.timestamp * 1000 : ms(fx.date);
  const hs = num(f.goals?.home);
  const as = num(f.goals?.away);
  const h = str(f.teams?.home?.name);
  const a = str(f.teams?.away?.name);
  const el = num(fx.status?.elapsed);
  return {
    i: `af-${fx.id}`, t, n: h && a ? `${h} vs ${a}` : null, h, a,
    hb: httpsOrNull(f.teams?.home?.logo), ab: httpsOrNull(f.teams?.away?.logo),
    hs, as, st: stateOf(fx.status?.short, t, hs !== null || as !== null, Date.now()), p: el !== null ? String(el) : null,
    r: num(String(f.league?.round || "").match(/(\d+)\s*$/)?.[1]),
  };
}
/** Variante avec horloge explicite (tests, appels différés). */
const afEventAt = (f, now) => {
  const e = afEvent(f);
  if (e) e.st = stateOf(f.fixture?.status?.short, e.t, e.hs !== null || e.as !== null, now);
  return e;
};

export function afRow(r) {
  if (!r) return null;
  return {
    rk: num(r.rank), team: str(r.team?.name), badge: httpsOrNull(r.team?.logo), g: str(r.group, 40),
    p: num(r.all?.played), w: num(r.all?.win), d: num(r.all?.draw), l: num(r.all?.lose),
    gf: num(r.all?.goals?.for), ga: num(r.all?.goals?.against), gd: num(r.goalsDiff), pts: num(r.points),
    form: str(r.form, 10), note: str(r.description, 60),
  };
}

const AF_KIND = (t) => { const s = String(t || "").toLowerCase(); return s === "goal" ? "goal" : s === "card" ? "card" : s.startsWith("subst") ? "sub" : s === "var" ? "var" : "other"; };

/** Feuille de match API-Football : temps forts, statistiques d'équipe, compositions et statistiques des joueurs. */
export function afMatchBody(f, now = Date.now()) {
  const event = afEventAt(f, now);
  if (!event) return null;
  const homeId = f.teams?.home?.id;
  const timeline = (f.events || []).map((x) => ({
    k: AF_KIND(x.type), d: str(x.detail, 40), h: x.team?.id === homeId, m: num(x.time?.elapsed), pl: str(x.player?.name), as: str(x.assist?.name),
  })).filter((x) => x.k !== "other").sort((a, b) => (a.m ?? 0) - (b.m ?? 0)).slice(0, 60);
  const sideStats = (id) => Object.fromEntries(((f.statistics || []).find((s) => s.team?.id === id)?.statistics || []).map((s) => [s.type, s.value]));
  const hv = sideStats(homeId);
  const av = sideStats(f.teams?.away?.id);
  const val = (v) => (typeof v === "string" && v.endsWith("%") ? num(v) : num(v));
  const stats = Object.keys(hv).map((k) => ({ k: str(k, 40), h: val(hv[k]) ?? 0, a: val(av[k]) ?? 0 })).filter((x) => x.k).slice(0, 30);
  const lineups = (f.lineups || []).map((l) => ({
    h: l.team?.id === homeId, f: str(l.formation, 12), c: str(l.coach?.name),
    xi: (l.startXI || []).map((x) => ({ n: num(x.player?.number), p: str(x.player?.name), pos: str(x.player?.pos, 4) })).slice(0, 11),
    sub: (l.substitutes || []).map((x) => ({ n: num(x.player?.number), p: str(x.player?.name), pos: str(x.player?.pos, 4) })).slice(0, 15),
  })).slice(0, 2);
  const players = (f.players || []).flatMap((t) => (t.players || []).map((p) => {
    const s = p.statistics?.[0] || {};
    return { h: t.team?.id === homeId, p: str(p.player?.name), min: num(s.games?.minutes), rt: Number.parseFloat(s.games?.rating) || null, g: num(s.goals?.total) ?? 0, as: num(s.goals?.assists) ?? 0 };
  })).filter((p) => p.p && p.min !== null).slice(0, 44);
  return { event: { ...event, v: str(f.fixture?.venue?.name), lg: str(f.league?.name) }, timeline, stats, lineups, players, _lid: num(f.league?.id) };
}

const af = {
  async live(ctx, key, _league, code) {
    const b = await ctx.fetchJson("af", "live", `${AF_BASE}/fixtures?live=${code}`, afHeaders(ctx.env));
    if (!b) return null;
    return (b.response || []).map((f) => ({ league: key, ev: afEventAt(f, ctx.now) })).filter((x) => x.ev && x.ev.st === "live");
  },
  async schedule(ctx, key, league, code, now) {
    const day = (d) => new Date(now + d * 86400000).toISOString().slice(0, 10);
    const s = afSeason(league, now);
    const b = await ctx.fetchJson("af", "schedule", `${AF_BASE}/fixtures?league=${code}&season=${s}&from=${day(-7)}&to=${day(21)}`, afHeaders(ctx.env));
    if (!b || !Array.isArray(b.response)) return null;
    return { season: league.split ? `${s}-${s + 1}` : String(s), events: b.response.map((f) => afEventAt(f, now)).filter(Boolean) };
  },
  async table(ctx, key, league, code, now) {
    const s = afSeason(league, now);
    const b = await ctx.fetchJson("af", "table", `${AF_BASE}/standings?league=${code}&season=${s}`, afHeaders(ctx.env));
    const groups = b?.response?.[0]?.league?.standings;
    if (!Array.isArray(groups)) return null;
    return { season: league.split ? `${s}-${s + 1}` : String(s), rows: groups.flat().map(afRow).filter(Boolean).slice(0, 40) };
  },
  async match(ctx, raw, now) {
    const b = await ctx.fetchJson("af", "match", `${AF_BASE}/fixtures?id=${raw}`, afHeaders(ctx.env));
    const f = b?.response?.[0];
    return f ? afMatchBody(f, now) : null;
  },
};

// ---- registre --------------------------------------------------------------------------------------------------------

const only = (map, rank) => Object.fromEntries(Object.entries(map).map(([k, code]) => [k, { code, rank }]));
const afLeagues = Object.fromEntries(Object.entries(AF_IDS).map(([k, code]) => [k, { code, rank: k === "botola" ? 1 : 9 }]));

/** Les erreurs applicatives d'API-Football arrivent en 200 : { errors: { rateLimit | requests: "…" } }. */
const afFailed = (body) => {
  const e = body?.errors;
  if (!e || (Array.isArray(e) ? !e.length : !Object.keys(e).length)) return null;
  const txt = JSON.stringify(e).toLowerCase();
  return /request|limit|quota/.test(txt) ? "quota" : "error";
};

/**
 * Budgets : jetons par minute / par jour / par capacité et par jour, volontairement sous le quota réel (les compteurs
 * sont par centre de données). API-Football : 90/jour dont direct 25, feuille de match 40, calendrier 15, classement 10.
 */
export const PROVIDERS = {
  fd: {
    id: "fd", name: "football-data.org", caps: ["live", "schedule", "table", "match"], leagues: only(FD_CODES, 1), adapter: fd,
    hasKey: (env) => Boolean(env?.FOOTBALL_DATA_KEY), budget: () => ({ perMin: 8 }),
  },
  jo: {
    id: "jo", name: "Jolpica-F1", caps: ["schedule", "table", "match"], leagues: { f1: { code: "f1", rank: 1 } }, adapter: jo,
    hasKey: () => true, budget: () => ({ perMin: 60 }),
  },
  af: {
    id: "af", name: "API-Football", caps: ["live", "schedule", "table", "match", "lineups", "players"], leagues: afLeagues, adapter: af,
    hasKey: (env) => Boolean(env?.API_FOOTBALL_KEY), budget: () => ({ perMin: 8, perDay: 90, perCapDay: { live: 25, match: 40, schedule: 15, table: 10 } }),
    failed: afFailed,
  },
  sdb: {
    // adaptateur fourni par sportsdata.js (historique) ; la clé publique « 123 » suffit
    id: "sdb", name: "TheSportsDB", caps: ["live", "schedule", "table", "match"], adapter: null,
    leagues: null, // rempli par sportsdata.js (liste blanche complète, rang 5 ; Botola 6)
    hasKey: () => true, budget: (env) => ({ perMin: env?.SPORTSDB_API_KEY ? 90 : 25 }),
  },
};

/** Durées de cache par fournisseur : API-Football toujours derrière un cache long (quota journalier). */
export const LONG_TTL = { af: { live: 300, schedule: 3 * 3600, table: 12 * 3600, matchLive: 300, matchDone: 24 * 3600 } };

/** Fournisseurs candidats pour (ligue, capacité), du préféré au dernier ; la disponibilité (budget) est testée par l'appelant. */
export function candidates(key, cap, env) {
  return Object.values(PROVIDERS)
    .filter((p) => p.leagues?.[key] && p.caps.includes(cap) && p.hasKey(env))
    .sort((a, b) => a.leagues[key].rank - b.leagues[key].rank);
}

export { MIN };
