import { describe, expect, it } from "vitest";
import { LEAGUES, reduceEvent, reduceStats, reduceTimeline, seasonOf, sportsLeagueProxy, sportsMatchProxy, stateOf } from "../src/sportsdata.js";

const sp = (s) => new URL("https://x.test/?" + s).searchParams;
const ok = (body) => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
const NOW = Date.parse("2026-11-16T20:00:00Z");

/** Cache mémoire minimal (API Cache : match / put). */
function memCache() {
  const m = new Map();
  return {
    async match(req) { const v = m.get(req.url); return v ? new Response(v) : undefined; },
    async put(req, res) { m.set(req.url, await res.text()); },
    size: () => m.size,
  };
}

const ev = (over = {}) => ({ idEvent: "1", strTimestamp: "2026-11-16T19:45:00", strEvent: "A vs B", strHomeTeam: "A", strAwayTeam: "B", intHomeScore: "1", intAwayScore: "0", strStatus: "FT", intRound: "11", strHomeTeamBadge: "https://b/a.png", ...over });

describe("réduction des données", () => {
  it("état : terminé, à venir, en cours, reporté", () => {
    expect(stateOf("FT", NOW, true, NOW)).toBe("post");
    expect(stateOf("NS", NOW + 3600e3, false, NOW)).toBe("pre");
    expect(stateOf("2H", NOW - 3600e3, true, NOW)).toBe("live");
    expect(stateOf("PST", NOW, false, NOW)).toBe("off");
    expect(stateOf("1H", NOW - 9 * 3600e3, true, NOW)).toBe("post");
  });
  it("match : heure en UTC, score entier, écusson https seulement", () => {
    const r = reduceEvent(ev({ strHomeTeamBadge: "http://x" }), NOW);
    expect(r).toMatchObject({ i: "1", t: Date.parse("2026-11-16T19:45:00Z"), hs: 1, as: 0, st: "post", hb: null, r: 11 });
    expect(reduceEvent({}, NOW)).toBeNull();
  });
  it("saison : split à partir de juillet, année civile sinon", () => {
    expect(seasonOf(LEAGUES.ligue1, NOW)).toBe("2026-2027");
    expect(seasonOf(LEAGUES.ligue1, Date.parse("2027-03-01"))).toBe("2026-2027");
    expect(seasonOf(LEAGUES.nfl, NOW)).toBe("2026");
  });
  it("temps forts et statistiques", () => {
    const t = reduceTimeline([{ strTimeline: "Goal", strHome: "Yes", intTime: "20", strPlayer: "X" }, { strTimeline: "Weird" }, { strTimeline: "Card", strHome: "No", intTime: "5", strPlayer: "Y" }]);
    expect(t.map((x) => x.k)).toEqual(["card", "goal"]);
    expect(t[1].h).toBe(true);
    expect(reduceStats([{ strStat: "Shots", intHome: "3", intAway: "4" }, { strStat: "x", intHome: null, intAway: "1" }])).toEqual([{ k: "Shots", h: 3, a: 4 }]);
  });
});

describe("/api/sports/league", () => {
  it("ligue ou vue hors liste blanche : 404, aucun appel amont", async () => {
    let called = 0;
    const fetchFn = async () => { called++; return ok({}); };
    expect((await sportsLeagueProxy(sp("id=../x"), {}, { cache: null, fetchFn })).status).toBe(404);
    expect((await sportsLeagueProxy(sp("id=toString"), {}, { cache: null, fetchFn })).status).toBe(404);
    expect((await sportsLeagueProxy(sp("id=ligue1&view=zzz"), {}, { cache: null, fetchFn })).status).toBe(404);
    expect(called).toBe(0);
  });

  it("calendrier : complété par les journées, mis en cache 1 h (un seul jeu d'appels)", async () => {
    const urls = [];
    const cache = memCache();
    const fetchFn = async (u) => {
      urls.push(u);
      if (u.includes("eventsnextleague")) return ok({ events: [ev({ idEvent: "2", strTimestamp: "2026-11-20T19:45:00", intHomeScore: null, intAwayScore: null, strStatus: "NS", intRound: "12" })] });
      if (u.includes("eventspastleague")) return ok({ events: [ev()] });
      if (u.includes("eventsround")) return ok({ events: [ev({ idEvent: "3", strTimestamp: "2026-11-21T19:45:00", intHomeScore: null, intAwayScore: null, strStatus: "NS", intRound: "12" })] });
      return ok({});
    };
    const a = await (await sportsLeagueProxy(sp("id=ligue1&view=schedule"), { SPORTSDB_API_KEY: "secret" }, { cache, fetchFn, now: NOW })).json();
    expect(a.events.map((e) => e.i)).toEqual(["1", "2", "3"]);
    expect(urls.some((u) => u.includes("/json/secret/eventsround.php?id=4334&r=11&s=2026-2027"))).toBe(true);
    const n = urls.length;
    const b = await sportsLeagueProxy(sp("id=ligue1&view=schedule"), {}, { cache, fetchFn, now: NOW });
    expect(urls.length).toBe(n);
    expect(b.headers.get("cache-control")).toContain("max-age=3600");
    expect(JSON.stringify(a)).not.toContain("secret");
  });

  it("amont refusé (429) : copie ancienne resservie, sinon 502 mémorisé 30 s", async () => {
    const cache = memCache();
    let fail = false;
    let calls = 0;
    const fetchFn = async () => { calls++; return fail ? new Response("x", { status: 429 }) : ok({ table: [{ intRank: "1", strTeam: "PSG", intPoints: "30", strBadge: "https://b" }] }); };
    const good = await (await sportsLeagueProxy(sp("id=ligue1&view=table"), {}, { cache, fetchFn, now: NOW })).json();
    expect(good.rows[0]).toMatchObject({ rk: 1, team: "PSG", pts: 30 });
    // copie fraîche expirée : on simule en vidant seulement la clé fraîche
    const cache2 = memCache();
    await cache2.put(new Request("https://sportsdata.cache/stale/table/ligue1"), new Response(JSON.stringify(good)));
    fail = true;
    const r = await sportsLeagueProxy(sp("id=ligue1&view=table"), {}, { cache: cache2, fetchFn, now: NOW });
    expect(r.status).toBe(200);
    expect((await r.json()).stale).toBe(true);
    const cache3 = memCache();
    expect((await sportsLeagueProxy(sp("id=nba&view=table"), {}, { cache: cache3, fetchFn, now: NOW })).status).toBe(502);
    const before = calls;
    expect((await sportsLeagueProxy(sp("id=nba&view=table"), {}, { cache: cache3, fetchFn, now: NOW })).status).toBe(502);
    expect(calls).toBe(before);
  });

  it("classement : repli sur la saison précédente quand la courante est vide", async () => {
    const urls = [];
    const fetchFn = async (u) => { urls.push(u); return ok({ table: u.includes("2025-2026") ? [{ intRank: "1", strTeam: "X" }] : null }); };
    const r = await (await sportsLeagueProxy(sp("id=liga&view=table"), {}, { cache: null, fetchFn, now: NOW })).json();
    expect(r.season).toBe("2025-2026");
    expect(urls.length).toBe(2);
  });

  it("direct : une seule requête amont partagée, filtrée par ligue, seuls les matchs en cours", async () => {
    const urls = [];
    const cache = memCache();
    const fetchFn = async (u) => {
      urls.push(u);
      return ok({ livescore: [
        { idEvent: "9", idLeague: "4334", strHomeTeam: "A", strAwayTeam: "B", intHomeScore: "1", intAwayScore: "1", strStatus: "2H", strProgress: "67", strTimestamp: "2026-11-16T19:00:00" },
        { idEvent: "8", idLeague: "4334", strHomeTeam: "C", strAwayTeam: "D", intHomeScore: "2", intAwayScore: "0", strStatus: "FT", strTimestamp: "2026-11-16T17:00:00" },
        { idEvent: "7", idLeague: "4328", strHomeTeam: "E", strAwayTeam: "F", intHomeScore: "0", intAwayScore: "0", strStatus: "1H", strProgress: "10", strTimestamp: "2026-11-16T19:50:00" },
      ] });
    };
    const l1 = await (await sportsLeagueProxy(sp("id=ligue1&view=live"), {}, { cache, fetchFn, now: NOW })).json();
    expect(l1.events.map((e) => e.i)).toEqual(["9"]);
    const pl = await (await sportsLeagueProxy(sp("id=premier&view=live"), {}, { cache, fetchFn, now: NOW })).json();
    expect(pl.events.map((e) => e.i)).toEqual(["7"]);
    expect(urls.length).toBe(1);
  });
});

describe("/api/sports/match", () => {
  it("identifiant invalide : 404", async () => {
    expect((await sportsMatchProxy(sp("id=1;drop"), {}, { cache: null })).status).toBe(404);
  });
  it("feuille de match : score, temps forts, statistiques, 3 appels puis cache", async () => {
    const urls = [];
    const cache = memCache();
    const fetchFn = async (u) => {
      urls.push(u);
      if (u.includes("lookupevent.php")) return ok({ events: [ev({ strVenue: "Stade" })] });
      if (u.includes("lookuptimeline")) return ok({ timeline: [{ strTimeline: "Goal", strHome: "Yes", intTime: "20", strPlayer: "Z" }] });
      return ok({ eventstats: [{ strStat: "Shots", intHome: "5", intAway: "2" }] });
    };
    const a = await (await sportsMatchProxy(sp("id=1"), {}, { cache, fetchFn, now: NOW })).json();
    expect(a.event).toMatchObject({ i: "1", hs: 1, v: "Stade" });
    expect(a.timeline[0]).toMatchObject({ k: "goal", pl: "Z", m: 20, h: true });
    expect(a.stats).toEqual([{ k: "Shots", h: 5, a: 2 }]);
    expect(urls.length).toBe(3);
    await sportsMatchProxy(sp("id=1"), {}, { cache, fetchFn, now: NOW });
    expect(urls.length).toBe(3);
  });
});
