import { describe, expect, it, vi } from "vitest";
import { sportsLeagueProxy, sportsMatchProxy } from "../src/sportsdata.js";
import { createCtx, MAX_SUBREQUESTS } from "../src/sportsbudget.js";
import { mergeEvents, normTeam, sameMatch, sameTeam } from "../src/sportscommon.js";
import { afEvent, afMatchBody, afRow, candidates, fdEvent, fdMatchBody, fdRow, joEvent, joMatchBody, joRow, PROVIDERS } from "../src/sportsprov.js";

const sp = (s) => new URL("https://x.test/?" + s).searchParams;
const ok = (body) => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
const NOW = Date.parse("2026-11-16T20:00:00Z");
const KEYS = { FOOTBALL_DATA_KEY: "FDSECRET123", API_FOOTBALL_KEY: "AFSECRET456", SPORTSDB_API_KEY: "SDBSECRET789" };

function memCache() {
  const m = new Map();
  return {
    async match(req) { const v = m.get(req.url); return v ? new Response(v) : undefined; },
    async put(req, res) { m.set(req.url, await res.text()); },
    keys: () => [...m.keys()],
    dump: () => [...m.values()].join("\n"),
  };
}

const fdMatch = (over = {}) => ({
  id: 501, utcDate: "2026-11-16T19:45:00Z", status: "FINISHED", matchday: 12,
  homeTeam: { id: 1, name: "Paris Saint-Germain FC", shortName: "PSG", crest: "https://c/psg.png" },
  awayTeam: { id: 2, name: "Olympique de Marseille", shortName: "Marseille", crest: "http://c/om.png" },
  score: { fullTime: { home: 2, away: 1 } }, competition: { code: "FL1", name: "Ligue 1" }, ...over,
});
const afFix = (over = {}) => ({
  fixture: { id: 900, timestamp: Date.parse("2026-11-16T19:45:00Z") / 1000, status: { short: "FT", elapsed: 90 }, venue: { name: "Parc" } },
  league: { id: 61, name: "Ligue 1", round: "Regular Season - 12" },
  teams: { home: { id: 10, name: "Paris Saint Germain", logo: "https://l/p.png" }, away: { id: 11, name: "Marseille", logo: "https://l/m.png" } },
  goals: { home: 2, away: 1 }, ...over,
});

describe("rapprochement des équipes et fusion", () => {
  it("noms normalisés : accents, sigles, alias", () => {
    expect(normTeam("Paris Saint-Germain FC")).toBe(normTeam("Paris SG"));
    expect(normTeam("Olympique de Marseille")).toBe("marseille");
    expect(normTeam("FC Bayern München")).toBe("bayern");
    expect(sameTeam("Manchester United FC", "Man Utd")).toBe(true);
    expect(sameTeam("Real Madrid CF", "Real Betis")).toBe(false);
  });
  it("même match : ±3 h et équipes ; au-delà ou équipes différentes : non", () => {
    const a = { t: NOW, h: "PSG", a: "Marseille" };
    expect(sameMatch(a, { t: NOW + 2 * 3600e3, h: "Paris Saint-Germain FC", a: "Olympique de Marseille" })).toBe(true);
    expect(sameMatch(a, { t: NOW + 4 * 3600e3, h: "PSG", a: "Marseille" })).toBe(false);
    expect(sameMatch(a, { t: NOW, h: "Lyon", a: "Marseille" })).toBe(false);
  });
  it("fusion de deux sources : la première fait foi, la seconde complète (progression, état plus avancé, matchs inconnus)", () => {
    const p = [{ i: "fd-1", t: NOW, h: "PSG", a: "Marseille", hb: null, ab: null, hs: null, as: null, st: "pre", p: null, r: 12, n: null }];
    const s = [
      { i: "9", t: NOW + 600e3, h: "Paris Saint Germain", a: "Olympique Marseille", hb: "https://b", ab: null, hs: 1, as: 0, st: "live", p: "34", r: null, n: "X" },
      { i: "8", t: NOW, h: "Lille", a: "Nice", hs: 0, as: 0, st: "live", p: "5", hb: null, ab: null, r: null, n: null },
    ];
    const m = mergeEvents(p, s, { addUnmatched: true });
    expect(m).toHaveLength(2);
    expect(m[0]).toMatchObject({ i: "fd-1", st: "live", hs: 1, as: 0, p: "34", hb: "https://b", r: 12 });
    expect(mergeEvents(p, s)).toHaveLength(1);
  });
});

describe("normalisation de chaque fournisseur", () => {
  it("football-data : match, classement, feuille de match", () => {
    expect(fdEvent(fdMatch())).toMatchObject({ i: "fd-501", t: Date.parse("2026-11-16T19:45:00Z"), h: "PSG", a: "Marseille", hs: 2, as: 1, st: "post", r: 12, hb: "https://c/psg.png", ab: null });
    expect(fdEvent(fdMatch({ status: "PAUSED" }))).toMatchObject({ st: "live", p: "HT" });
    expect(fdEvent(fdMatch({ status: "TIMED", score: {} }))).toMatchObject({ st: "pre", hs: null });
    expect(fdRow({ position: 1, team: { name: "PSG", crest: "https://c" }, playedGames: 12, won: 9, draw: 2, lost: 1, goalsFor: 30, goalsAgainst: 10, goalDifference: 20, points: 29, form: "W,W" }, "GROUP_A"))
      .toMatchObject({ rk: 1, team: "PSG", p: 12, w: 9, pts: 29, gd: 20, g: "GROUP A" });
    const b = fdMatchBody(fdMatch({
      goals: [{ minute: 20, team: { id: 1 }, scorer: { name: "Dembele" }, assist: { name: "Vitinha" }, type: "REGULAR" }],
      bookings: [{ minute: 5, team: { id: 2 }, player: { name: "X" }, card: "YELLOW" }],
      homeTeam: { ...fdMatch().homeTeam, statistics: { shots: 10, ball_possession: 60 } },
      awayTeam: { ...fdMatch().awayTeam, statistics: { shots: 4, ball_possession: 40 } },
    }));
    expect(b.timeline.map((x) => x.k)).toEqual(["card", "goal"]);
    expect(b.timeline[1]).toMatchObject({ h: true, pl: "Dembele", as: "Vitinha", m: 20 });
    expect(b.stats).toEqual([{ k: "Possession", h: 60, a: 40 }, { k: "Shots", h: 10, a: 4 }]);
    expect(b.event).toMatchObject({ lg: "Ligue 1" });
  });

  it("Jolpica-F1 : course, pilote, résultat", () => {
    const race = { season: "2026", round: "12", raceName: "Grand Prix X", date: "2026-11-15", time: "14:00:00Z", Circuit: { Location: { locality: "Spa", country: "Belgium" } } };
    expect(joEvent(race, NOW)).toMatchObject({ i: "jo-2026-12", n: "Grand Prix X", h: null, st: "post", r: 12, v: "Spa, Belgium" });
    expect(joEvent(race, Date.parse("2026-11-14T00:00:00Z")).st).toBe("pre");
    expect(joRow({ position: "1", points: "300", wins: "8", Driver: { givenName: "Max", familyName: "Verstappen" }, Constructors: [{ name: "Red Bull" }] }))
      .toMatchObject({ rk: 1, team: "Max Verstappen", pts: 300, w: 8, note: "Red Bull" });
    const body = joMatchBody({ ...race, Results: [{ position: "1", Driver: { givenName: "Max", familyName: "Verstappen" }, Constructor: { name: "Red Bull" }, Time: { time: "1:30:00" }, grid: "2", points: "25" }] }, NOW);
    expect(body.results[0]).toMatchObject({ pos: 1, driver: "Max Verstappen", team: "Red Bull", pts: 25 });
  });

  it("API-Football : match, classement, feuille avec statistiques, compositions et joueurs", () => {
    expect(afEvent(afFix())).toMatchObject({ i: "af-900", h: "Paris Saint Germain", hs: 2, r: 12, hb: "https://l/p.png" });
    expect(afRow({ rank: 2, team: { name: "OM", logo: "https://l" }, points: 25, goalsDiff: 8, group: "Ligue 1", form: "WWD", all: { played: 12, win: 8, draw: 1, lose: 3, goals: { for: 20, against: 12 } } }))
      .toMatchObject({ rk: 2, team: "OM", p: 12, w: 8, gf: 20, ga: 12, gd: 8, pts: 25 });
    const b = afMatchBody(afFix({
      events: [{ type: "Goal", detail: "Normal Goal", time: { elapsed: 20 }, team: { id: 10 }, player: { name: "A" }, assist: { name: "B" } }, { type: "Weird", team: { id: 10 } }],
      statistics: [{ team: { id: 10 }, statistics: [{ type: "Ball Possession", value: "60%" }, { type: "Total Shots", value: 12 }] }, { team: { id: 11 }, statistics: [{ type: "Ball Possession", value: "40%" }, { type: "Total Shots", value: null }] }],
      lineups: [{ team: { id: 10 }, formation: "4-3-3", coach: { name: "LE" }, startXI: [{ player: { number: 1, name: "G", pos: "G" } }], substitutes: [] }],
      players: [{ team: { id: 10 }, players: [{ player: { name: "A" }, statistics: [{ games: { minutes: 90, rating: "7.5" }, goals: { total: 1, assists: 0 } }] }] }],
    }), NOW);
    expect(b.timeline).toHaveLength(1);
    expect(b.timeline[0]).toMatchObject({ k: "goal", h: true, pl: "A", as: "B", m: 20 });
    expect(b.stats).toEqual([{ k: "Ball Possession", h: 60, a: 40 }, { k: "Total Shots", h: 12, a: 0 }]);
    expect(b.lineups[0]).toMatchObject({ h: true, f: "4-3-3", c: "LE" });
    expect(b.players[0]).toMatchObject({ h: true, p: "A", min: 90, rt: 7.5, g: 1 });
  });
});

describe("choix du fournisseur", () => {
  it("couverture et rang : football-data d'abord pour la L1, Jolpica pour la F1, API-Football d'abord pour la Botola seulement", () => {
    const ids = (k, c, env = KEYS) => candidates(k, c, env).map((p) => p.id);
    expect(ids("ligue1", "schedule")).toEqual(["fd", "sdb", "af"]);
    expect(ids("f1", "table")).toEqual(["jo", "sdb"]);
    expect(ids("botola", "schedule")).toEqual(["af", "sdb"]);
    expect(ids("nba", "schedule")).toEqual(["sdb"]);
    expect(ids("ligue1", "lineups")).toEqual(["af"]);
    expect(ids("ligue1", "schedule", {})).toEqual(["sdb"]); // sans clés : seuls les fournisseurs sans clé
    expect(ids("f1", "live", KEYS)).toEqual(["sdb"]);
  });

  it("calendrier L1 : football-data seul appelé, clé en en-tête (jamais dans l'URL), format normalisé", async () => {
    const calls = [];
    const fetchFn = async (u, init) => { calls.push({ u, h: init?.headers }); return ok({ matches: [fdMatch(), fdMatch({ id: 502, status: "TIMED", utcDate: "2026-11-22T19:45:00Z", score: {} })] }); };
    const r = await sportsLeagueProxy(sp("id=ligue1&view=schedule"), KEYS, { cache: memCache(), fetchFn, now: NOW });
    const b = await r.json();
    expect(calls).toHaveLength(1);
    expect(calls[0].u).toContain("api.football-data.org/v4/competitions/FL1/matches");
    expect(calls[0].u).not.toContain("FDSECRET123");
    expect(calls[0].h["X-Auth-Token"]).toBe("FDSECRET123");
    expect(b.src).toBe("fd");
    expect(b.events.map((e) => e.i)).toEqual(["fd-501", "fd-502"]);
    expect(JSON.stringify(b)).not.toMatch(/SECRET/);
  });

  it("Botola : API-Football avec cache long (3 h) ; F1 : Jolpica", async () => {
    const calls = [];
    const fetchFn = async (u, init) => {
      calls.push({ u, h: init?.headers });
      if (u.includes("api-sports.io")) return ok({ errors: [], response: [afFix({ league: { id: 200, round: "Regular Season - 9" } })] });
      return ok({ MRData: { RaceTable: { season: "2026", Races: [{ season: "2026", round: "13", raceName: "GP Y", date: "2026-11-22", time: "13:00:00Z" }] } } });
    };
    const bo = await sportsLeagueProxy(sp("id=botola&view=schedule"), KEYS, { cache: memCache(), fetchFn, now: NOW });
    expect(calls[0].u).toContain("v3.football.api-sports.io/fixtures?league=200&season=2026");
    expect(calls[0].h["x-apisports-key"]).toBe("AFSECRET456");
    expect(bo.headers.get("cache-control")).toContain("max-age=10800");
    const f1 = await (await sportsLeagueProxy(sp("id=f1&view=schedule"), KEYS, { cache: memCache(), fetchFn, now: NOW })).json();
    expect(calls[1].u).toContain("api.jolpi.ca/ergast/f1/current.json");
    expect(f1.events[0]).toMatchObject({ i: "jo-2026-13", n: "GP Y", st: "pre" });
    expect(f1.src).toBe("jo");
  });

  it("API-Football reste en dernier pour la L1 : TheSportsDB passe avant lui quand football-data manque", async () => {
    const calls = [];
    const fetchFn = async (u) => { calls.push(u); return ok({ table: [{ intRank: "1", strTeam: "PSG", intPoints: "30" }] }); };
    const b = await (await sportsLeagueProxy(sp("id=premier&view=table"), { API_FOOTBALL_KEY: "AFSECRET456" }, { cache: memCache(), fetchFn, now: NOW })).json();
    expect(b.src).toBe("sdb");
    expect(calls.every((u) => u.includes("thesportsdb"))).toBe(true);
  });

  it("bascule : football-data renvoie 429 → TheSportsDB répond, football-data mis en pause pour les appels suivants", async () => {
    const cache = memCache();
    const calls = [];
    const fetchFn = async (u) => {
      calls.push(u);
      if (u.includes("football-data")) return new Response("slow down", { status: 429 });
      return ok({ table: [{ intRank: "1", strTeam: "PSG", intPoints: "30" }] });
    };
    const a = await (await sportsLeagueProxy(sp("id=ligue1&view=table"), KEYS, { cache, fetchFn, now: NOW })).json();
    expect(a.src).toBe("sdb");
    expect(a.rows[0]).toMatchObject({ team: "PSG", pts: 30 });
    const fdCalls = calls.filter((u) => u.includes("football-data")).length;
    expect(fdCalls).toBe(1);
    await sportsLeagueProxy(sp("id=premier&view=table"), KEYS, { cache, fetchFn, now: NOW + 5000 });
    expect(calls.filter((u) => u.includes("football-data")).length).toBe(1); // en pause : plus aucun appel
  });

  it("tous les fournisseurs en échec : dernière réponse en cache, même périmée, marquée stale", async () => {
    const cache = memCache();
    await cache.put(new Request("https://sportsdata.cache/stale/schedule/ligue1"), new Response(JSON.stringify({ league: "ligue1", events: [{ i: "fd-1" }] })));
    const fetchFn = async () => new Response("x", { status: 500 });
    const r = await sportsLeagueProxy(sp("id=ligue1&view=schedule"), KEYS, { cache, fetchFn, now: NOW });
    const b = await r.json();
    expect(r.status).toBe(200);
    expect(b.stale).toBe(true);
    expect(b.events[0].i).toBe("fd-1");
  });
});

describe("budgets", () => {
  const reg = PROVIDERS;
  it("football-data : 8 jetons par minute, puis refus ; la minute suivante repart", async () => {
    const ctx = createCtx(KEYS, { cache: null, now: NOW, registry: reg });
    for (let i = 0; i < 8; i++) expect(await ctx.take("fd", "schedule")).toBe(true);
    expect(await ctx.take("fd", "schedule")).toBe(false);
    expect(await ctx.canUse("fd", "table")).toBe(false);
  });
  it("l'état persiste d'un appel à l'autre (une écriture groupée par appel) et se renouvelle chaque minute", async () => {
    const cache = memCache();
    const puts = vi.spyOn(cache, "put");
    const a = createCtx(KEYS, { cache, now: NOW, registry: reg });
    for (let i = 0; i < 8; i++) await a.take("fd", "schedule");
    await a.flush();
    await a.flush(); // rien de neuf : pas de seconde écriture
    expect(puts).toHaveBeenCalledTimes(1);
    const b = createCtx(KEYS, { cache, now: NOW + 1000, registry: reg });
    expect(await b.canUse("fd", "schedule")).toBe(false);
    const c = createCtx(KEYS, { cache, now: NOW + 61000, registry: reg });
    expect(await c.canUse("fd", "schedule")).toBe(true);
  });
  it("API-Football : 25 directs, 40 feuilles de match par jour ; 90 au total ; sans clé : jamais", async () => {
    const day = (n) => createCtx(KEYS, { cache: null, now: NOW + n * 60000 * 2, registry: reg });
    let used = 0;
    const ctx = day(0);
    // le plafond par minute (8) est franchi : on avance l'horloge en recréant un contexte partageant le même cache
    const cache = memCache();
    for (let n = 0; n < 12 && used < 40; n++) {
      const c = createCtx(KEYS, { cache, now: NOW + n * 120000, registry: reg });
      for (let i = 0; i < 8; i++) if (await c.take("af", "live")) used++;
      await c.flush();
    }
    expect(used).toBe(25);
    const c2 = createCtx(KEYS, { cache, now: NOW + 20 * 120000, registry: reg });
    expect(await c2.canUse("af", "live")).toBe(false);
    expect(await c2.canUse("af", "match")).toBe(true);
    expect(await ctx.canUse("af", "match")).toBe(true);
    expect(await createCtx({}, { cache: null, now: NOW, registry: reg }).canUse("af", "match")).toBe(false);
  });
  it("sous-requêtes : fetch et cache comptent ; au-delà de 50, plus rien ne part", async () => {
    let fetched = 0;
    const fetchFn = async () => { fetched++; return ok({}); };
    const ctx = createCtx({ SPORTSDB_API_KEY: "k" }, { cache: null, fetchFn, now: NOW, registry: { x: { hasKey: () => true, budget: () => ({}) } } });
    let okCount = 0;
    for (let i = 0; i < 60; i++) if (await ctx.fetchJson("x", "c", "https://x/")) okCount++;
    expect(okCount).toBe(MAX_SUBREQUESTS);
    expect(fetched).toBe(MAX_SUBREQUESTS);
  });
  it("un appel du Worker ne dépasse jamais 50 sous-requêtes, même calendrier TheSportsDB complet", async () => {
    let fetched = 0;
    const fetchFn = async () => { fetched++; return ok({ events: [{ idEvent: "1", intRound: "5", strTimestamp: "2026-11-16T19:45:00", strHomeTeam: "A", strAwayTeam: "B" }] }); };
    await sportsLeagueProxy(sp("id=nba&view=schedule"), {}, { cache: memCache(), fetchFn, now: NOW });
    expect(fetched).toBeLessThanOrEqual(6);
  });
});

describe("croisement de sources", () => {
  it("direct : football-data + TheSportsDB fusionnés (progression complétée), un seul jeu d'appels par minute", async () => {
    const cache = memCache();
    const calls = [];
    const fetchFn = async (u) => {
      calls.push(u);
      if (u.includes("football-data")) return ok({ matches: [fdMatch({ status: "IN_PLAY", score: { fullTime: { home: 1, away: 0 } }, utcDate: "2026-11-16T19:00:00Z" })] });
      return ok({ livescore: [
        { idEvent: "77", idLeague: "4334", strHomeTeam: "Paris Saint Germain", strAwayTeam: "Olympique Marseille", intHomeScore: "1", intAwayScore: "0", strStatus: "2H", strProgress: "61", strTimestamp: "2026-11-16T19:00:00" },
        { idEvent: "78", idLeague: "4328", strHomeTeam: "Arsenal", strAwayTeam: "Chelsea", intHomeScore: "0", intAwayScore: "0", strStatus: "1H", strProgress: "12", strTimestamp: "2026-11-16T19:48:00" },
      ] });
    };
    const l1 = await (await sportsLeagueProxy(sp("id=ligue1&view=live"), KEYS, { cache, fetchFn, now: NOW })).json();
    expect(l1.events).toHaveLength(1);
    expect(l1.events[0]).toMatchObject({ i: "fd-501", p: "61", hs: 1, as: 0, st: "live" });
    const pl = await (await sportsLeagueProxy(sp("id=premier&view=live"), KEYS, { cache, fetchFn, now: NOW })).json();
    expect(pl.events.map((e) => e.h)).toEqual(["Arsenal"]);
    expect(calls).toHaveLength(2);
  });

  it("calendrier football-data + score en direct déjà connu : superposé sans appel supplémentaire", async () => {
    const cache = memCache();
    await cache.put(new Request("https://sportsdata.cache/live/all"), new Response(JSON.stringify({ events: [
      { league: "ligue1", ev: { i: "77", t: NOW - 3600e3, h: "Paris Saint Germain", a: "Olympique Marseille", hs: 2, as: 1, st: "live", p: "70", hb: null, ab: null, r: null, n: null } },
    ] })));
    const fetchFn = async () => ok({ matches: [fdMatch({ status: "TIMED", score: {}, utcDate: "2026-11-16T19:00:00Z" })] });
    const b = await (await sportsLeagueProxy(sp("id=ligue1&view=schedule"), { FOOTBALL_DATA_KEY: "FDSECRET123" }, { cache, fetchFn, now: NOW })).json();
    expect(b.events[0]).toMatchObject({ i: "fd-501", st: "live", hs: 2, as: 1, p: "70" });
  });

  it("feuille de match football-data enrichie par API-Football (statistiques, compositions), rapprochée par date et équipes", async () => {
    const cache = memCache();
    const urls = [];
    const fetchFn = async (u) => {
      urls.push(u);
      if (u.includes("football-data")) return ok(fdMatch());
      if (u.includes("date=")) return ok({ errors: [], response: [afFix()] });
      return ok({ errors: [], response: [afFix({
        statistics: [{ team: { id: 10 }, statistics: [{ type: "Shots", value: 9 }] }, { team: { id: 11 }, statistics: [{ type: "Shots", value: 3 }] }],
        lineups: [{ team: { id: 10 }, formation: "4-3-3", startXI: [], substitutes: [] }],
      })] });
    };
    const r = await sportsMatchProxy(sp("id=fd-501"), KEYS, { cache, fetchFn, now: NOW });
    const b = await r.json();
    expect(b.event).toMatchObject({ i: "fd-501", hs: 2, lg: "Ligue 1" });
    expect(b.stats).toEqual([{ k: "Shots", h: 9, a: 3 }]);
    expect(b.lineups[0].f).toBe("4-3-3");
    expect(urls).toHaveLength(3);
    const n = urls.length;
    await sportsMatchProxy(sp("id=fd-501"), KEYS, { cache, fetchFn, now: NOW });
    expect(urls.length).toBe(n); // 2e lecture : cache
    expect(JSON.stringify(b)).not.toMatch(/SECRET|_comp/);
  });

  it("sans budget API-Football, la feuille football-data est servie telle quelle", async () => {
    const fetchFn = async (u) => { if (u.includes("football-data")) return ok(fdMatch()); throw new Error("ne devrait pas être appelé"); };
    const b = await (await sportsMatchProxy(sp("id=fd-501"), { FOOTBALL_DATA_KEY: "FDSECRET123" }, { cache: memCache(), fetchFn, now: NOW })).json();
    expect(b.event.i).toBe("fd-501");
    expect(b.lineups).toBeUndefined();
  });

  it("identifiants de match : fd-, af-, jo-, numérique ; le reste est refusé", async () => {
    const fetchFn = async () => ok({});
    for (const bad of ["fd-", "xx-5", "jo-1999-1", "af-0", "1;2", "fd-1-2"]) {
      expect((await sportsMatchProxy(sp(`id=${bad}`), KEYS, { cache: null, fetchFn })).status).toBe(404);
    }
    const jo = await sportsMatchProxy(sp("id=jo-2026-12"), KEYS, {
      cache: null, now: NOW,
      fetchFn: async () => ok({ MRData: { RaceTable: { Races: [{ season: "2026", round: "12", raceName: "GP", date: "2026-11-15", time: "14:00:00Z", Results: [{ position: "1", Driver: { givenName: "A", familyName: "B" }, Constructor: { name: "C" }, points: "25" }] }] } } }),
    });
    expect((await jo.json()).results[0].driver).toBe("A B");
  });

  it("fournisseur de la feuille épuisé : copie ancienne marquée stale, sinon 502, sans appel", async () => {
    const cache = memCache();
    await cache.put(new Request("https://sportsdata.cache/stale/match/af-900"), new Response(JSON.stringify({ event: { i: "af-900" }, timeline: [], stats: [] })));
    const fetchFn = async () => { throw new Error("aucun appel attendu"); };
    // sans clé API-Football : indisponible
    const r = await sportsMatchProxy(sp("id=af-900"), {}, { cache, fetchFn, now: NOW });
    expect((await r.json()).stale).toBe(true);
    expect((await sportsMatchProxy(sp("id=af-901"), {}, { cache, fetchFn, now: NOW })).status).toBe(502);
  });
});

describe("secrets", () => {
  it("aucune clé dans les réponses, les clés de cache ni les journaux, même en cas d'échec", async () => {
    const cache = memCache();
    const logs = [];
    const spies = ["log", "warn", "error", "info"].map((m) => vi.spyOn(console, m).mockImplementation((...a) => logs.push(a.join(" "))));
    const fetchFn = async (u) => {
      if (u.includes("api-sports")) return new Response(JSON.stringify({ errors: { requests: "limit reached AFSECRET456" } }), { status: 200 });
      if (u.includes("football-data")) return new Response("FDSECRET123", { status: 403 });
      return ok({ events: [] });
    };
    const out = [];
    for (const q of ["id=ligue1&view=schedule", "id=botola&view=table", "id=ligue1&view=live", "id=f1&view=schedule"]) {
      out.push(await (await sportsLeagueProxy(sp(q), KEYS, { cache, fetchFn, now: NOW })).text());
    }
    out.push(await (await sportsMatchProxy(sp("id=af-5"), KEYS, { cache, fetchFn, now: NOW })).text());
    spies.forEach((s) => s.mockRestore());
    const all = out.join("\n") + logs.join("\n") + cache.keys().join("\n") + cache.dump();
    expect(all).not.toMatch(/FDSECRET123|AFSECRET456|SDBSECRET789/);
  });
});
