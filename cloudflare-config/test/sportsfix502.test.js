// Non-régression « 502 sur Botola / F1 / NBA » : User-Agent explicite vers les amonts, journal des échecs (sans clé),
// Retry-After respecté, repli API-Football → TheSportsDB, et toutes les ligues répondent 200 dès qu'un amont répond.
// Les fixtures sont de vraies réponses (Jolpica-F1, TheSportsDB) enregistrées le 17/11/2026 et allégées.
import { afterEach, describe, expect, it, vi } from "vitest";
import { sportsLeagueProxy } from "../src/sportsdata.js";
import { safeUrl, UPSTREAM_UA } from "../src/sportsbudget.js";
import jolpica from "./fixtures/sports-jolpica-real.json";
import sdbReal from "./fixtures/sports-sdb-real.json";

const sp = (s) => new URL("https://x.test/?" + s).searchParams;
const NOW = Date.parse("2026-11-17T10:00:00Z");
const ok = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", ...headers } });
const KEYS = { FOOTBALL_DATA_KEY: "FDSECRET123", API_FOOTBALL_KEY: "AFSECRET456", SPORTSDB_API_KEY: "SDBSECRET789" };

function memCache() {
  const m = new Map();
  return {
    async match(req) { const v = m.get(req.url); return v ? new Response(v) : undefined; },
    async put(req, res) { m.set(req.url, await res.text()); },
    dump: () => [...m.values()].join("\n"),
  };
}

/** Amont simulé à partir des réponses réelles : chemin TheSportsDB (sans clé) → corps ; Jolpica ; sinon corps vides valides. */
function upstream(calls = []) {
  return async (u, init) => {
    calls.push({ u, h: init?.headers || {} });
    if (u.includes("api.jolpi.ca")) return ok(u.includes("driverStandings") ? jolpica.jolpica_standings : jolpica.jolpica_current);
    if (u.includes("thesportsdb.com")) {
      const path = u.replace(/^https:\/\/www\.thesportsdb\.com\/api\/v1\/json\/[^/]+\//, "");
      // TheSportsDB répond « 200 » avec un corps VIDE quand il n'a pas de classement (NBA, UCL, NFL, NHL…) : fixture "" = corps vide
      if (sdbReal[path] === "") return new Response("", { status: 200 });
      return ok(sdbReal[path] ?? (path.startsWith("lookuptable") ? { table: null } : path.startsWith("livescore") ? { livescore: null } : { events: null }));
    }
    return new Response("nope", { status: 404 });
  };
}

let spies = [];
const captureLogs = () => {
  const logs = [];
  spies = ["log", "warn", "error", "info"].map((m) => vi.spyOn(console, m).mockImplementation((...a) => logs.push(a.join(" "))));
  return logs;
};
afterEach(() => { spies.forEach((s) => s.mockRestore()); spies = []; });

const get = async (q, env, fetchFn, cache = memCache()) => {
  const r = await sportsLeagueProxy(sp(q), env, { cache, fetchFn, now: NOW });
  return { r, b: await r.json() };
};

describe("F1 (Jolpica, réponse réelle)", () => {
  it("calendrier : 200, course du 22/11, 29/11 et 06/12 dans la fenêtre, saison 2026, User-Agent envoyé", async () => {
    const calls = [];
    const { r, b } = await get("id=f1&view=schedule", {}, upstream(calls));
    expect(r.status).toBe(200);
    expect(b).toMatchObject({ league: "f1", season: "2026", src: "jo" });
    expect(b.events.map((e) => e.i)).toEqual(["jo-2026-21", "jo-2026-22", "jo-2026-23"]);
    expect(calls).toHaveLength(1);
    expect(calls[0].u).toBe("https://api.jolpi.ca/ergast/f1/current.json");
    expect(calls[0].h["user-agent"]).toBe(UPSTREAM_UA);
  });
  it("classement des pilotes : 200, Antonelli en tête", async () => {
    const { r, b } = await get("id=f1&view=table", {}, upstream());
    expect(r.status).toBe(200);
    expect(b.rows[0]).toMatchObject({ rk: 1, team: "Andrea Kimi Antonelli", pts: 390 });
  });
  it("Jolpica refuse (403) : TheSportsDB prend le relais, journal clair sans clé, jamais de 502", async () => {
    const logs = captureLogs();
    const f = upstream();
    const fetchFn = async (u, i) => (u.includes("jolpi.ca") ? new Response("blocked", { status: 403 }) : f(u, i));
    const { r, b } = await get("id=f1&view=schedule", KEYS, fetchFn);
    expect(r.status).toBe(200);
    expect(b.src).toBe("sdb");
    expect(logs.join("\n")).toContain("[sports] jo/schedule HTTP 403 api.jolpi.ca/ergast/f1/current.json");
    expect(logs.join("\n")).not.toMatch(/SDBSECRET789/);
  });
});

describe("NBA (TheSportsDB, réponses réelles)", () => {
  it("calendrier : 200, saison 2026-2027, matchs du jour réduits, User-Agent envoyé, clé jamais journalisée", async () => {
    const calls = [];
    const logs = captureLogs();
    const { r, b } = await get("id=nba&view=schedule", KEYS, upstream(calls));
    expect(r.status).toBe(200);
    expect(b).toMatchObject({ league: "nba", season: "2026-2027", src: "sdb" });
    expect(b.events.length).toBeGreaterThan(0);
    expect(b.events[0]).toMatchObject({ i: expect.any(String), h: expect.any(String), a: expect.any(String) });
    expect(calls.every((c) => c.h["user-agent"] === UPSTREAM_UA)).toBe(true);
    expect(logs.join("\n")).not.toMatch(/SDBSECRET789/);
  });
  it("classement : TheSportsDB répond 200 avec un corps VIDE (réel) : 200 avec liste vide, jamais 502", async () => {
    const { r, b } = await get("id=nba&view=table", {}, upstream());
    expect(r.status).toBe(200);
    expect(b).toMatchObject({ league: "nba", rows: [], src: "sdb" });
  });
  it("429 de TheSportsDB : 502 journalisé (statut, point d'accès sans clé) ; Retry-After borne la pause", async () => {
    const logs = captureLogs();
    let n = 0;
    const fetchFn = async () => { n++; return new Response("error code: 1015", { status: 429, headers: { "retry-after": "120" } }); };
    const cache = memCache();
    const { r } = await get("id=nba&view=schedule", KEYS, fetchFn, cache);
    expect(r.status).toBe(502);
    const text = logs.join("\n");
    expect(text).toContain("[sports] sdb/schedule HTTP 429 (pause 120 s) www.thesportsdb.com/api/v1/json/…/eventsnextleague.php?id=4387");
    expect(text).toContain("502 : aucun fournisseur n'a répondu");
    expect(text).not.toMatch(/SDBSECRET789/);
    expect(n).toBe(1); // la pause coupe les appels suivants du même passage
  });
  it("exception réseau : journalisée, pas de clé", async () => {
    const logs = captureLogs();
    const fetchFn = async () => { throw new Error("connection reset"); };
    const { r } = await get("id=nba&view=schedule", KEYS, fetchFn);
    expect(r.status).toBe(502);
    expect(logs.join("\n")).toMatch(/\[sports\] sdb\/schedule exception réseau \(connection reset\) www\.thesportsdb\.com\/api\/v1\/json\/…\//);
  });
});

describe("Botola (API-Football puis TheSportsDB)", () => {
  const afBotola = {
    get: "fixtures", errors: [], results: 1, paging: { current: 1, total: 1 },
    response: [{
      fixture: { id: 1300001, date: "2026-11-21T19:00:00+00:00", timestamp: Date.parse("2026-11-21T19:00:00Z") / 1000, status: { long: "Not Started", short: "NS", elapsed: null } },
      league: { id: 200, name: "Botola Pro", country: "Morocco", season: 2026, round: "Regular Season - 8" },
      teams: { home: { id: 967, name: "Raja Casablanca", logo: "https://media.api-sports.io/football/teams/967.png" }, away: { id: 968, name: "Wydad AC", logo: "https://media.api-sports.io/football/teams/968.png" } },
      goals: { home: null, away: null },
    }],
  };
  it("API-Football répond : saison 2026 (année de début), clé en en-tête, User-Agent envoyé", async () => {
    const calls = [];
    const fetchFn = async (u, i) => { calls.push({ u, h: i?.headers || {} }); return u.includes("api-sports") ? ok(afBotola) : new Response("x", { status: 404 }); };
    const { r, b } = await get("id=botola&view=schedule", KEYS, fetchFn);
    expect(r.status).toBe(200);
    expect(b).toMatchObject({ src: "af", season: "2026-2027" });
    expect(b.events[0]).toMatchObject({ i: "af-1300001", h: "Raja Casablanca", a: "Wydad AC", st: "pre", r: 8 });
    expect(calls).toHaveLength(1);
    expect(calls[0].u).toContain("league=200&season=2026&from=2026-11-10&to=2026-12-08");
    expect(calls[0].u).not.toContain("AFSECRET456");
    expect(calls[0].h["x-apisports-key"]).toBe("AFSECRET456");
    expect(calls[0].h["user-agent"]).toBe(UPSTREAM_UA);
  });
  it("offre gratuite d'API-Football (saison non couverte, erreur applicative en 200) : repli TheSportsDB, 200, journalisé", async () => {
    const logs = captureLogs();
    const f = upstream();
    const fetchFn = async (u, i) => (u.includes("api-sports")
      ? ok({ get: "fixtures", errors: { plan: "Free plans do not have access to this season, try from 2022 to 2024." }, results: 0, response: [] })
      : f(u, i));
    const { r, b } = await get("id=botola&view=schedule", KEYS, fetchFn);
    expect(r.status).toBe(200);
    expect(b.src).toBe("sdb");
    expect(logs.join("\n")).toContain("[sports] af/schedule erreur applicative (error)");
    expect(logs.join("\n")).not.toMatch(/AFSECRET456|SDBSECRET789/);
  });
  it("sans clé API-Football : TheSportsDB directement (calendrier et classement, réponses réelles)", async () => {
    const calls = [];
    const s = await get("id=botola&view=schedule", {}, upstream(calls));
    expect(s.r.status).toBe(200);
    expect(s.b).toMatchObject({ src: "sdb", season: "2026-2027" });
    expect(calls.some((c) => c.u.includes("api-sports"))).toBe(false);
    const t = await get("id=botola&view=table", {}, upstream());
    expect(t.r.status).toBe(200);
    expect(t.b.rows.length).toBeGreaterThan(0);
  });
});

describe("toutes les ligues", () => {
  const ids = ["ligue1", "premier", "liga", "seriea", "bundesliga", "ucl", "botola", "nba", "nfl", "nhl", "mlb", "top14", "premrugby", "f1"];
  it.each(ids)("%s : calendrier, classement et direct répondent 200 quand l'amont répond (liste vide hors saison acceptée)", async (id) => {
    for (const view of ["schedule", "table", "live"]) {
      const { r, b } = await get(`id=${id}&view=${view}`, {}, upstream());
      expect(r.status, `${id} ${view}`).toBe(200);
      expect(Array.isArray(b.events || b.rows)).toBe(true);
    }
  });
});

describe("journal sans secret", () => {
  it("safeUrl masque la clé TheSportsDB (segment de chemin) et les paramètres secrets", () => {
    expect(safeUrl("https://www.thesportsdb.com/api/v1/json/SDBSECRET789/eventsday.php?d=2026-11-17&l=4387")).toBe("www.thesportsdb.com/api/v1/json/…/eventsday.php?d=2026-11-17&l=4387");
    expect(safeUrl("https://h.test/x?apikey=ABC&id=1")).not.toContain("ABC");
  });
});
