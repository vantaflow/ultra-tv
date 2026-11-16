import { beforeEach, describe, expect, it } from "vitest";
import {
  DEFAULT_PREFS, LEAGUES, allLeagues, channelsFor, decodePrefs, encodePrefs, fixturesOf, hasSpoiler, liveOf, mayBeLive, mergeMatches, moveLeague,
  parseEvents, parseSheet, parseTable, resultsOf, setLeagueHidden, showScore, showTable, visibleLeagues, type LeagueMatch,
} from "./sportLeagues";
import type { SportEntry } from "./sport";
import { COOLDOWN_MS, fetchCached, resetSportLeaguesCache } from "@/hooks/sportLeagues";

const MIN = 60_000;
const T0 = 1_700_000_000_000;
const m = (over: Partial<LeagueMatch> = {}): LeagueMatch => ({
  id: "1", start: T0, name: null, home: "A", away: "B", homeBadge: null, awayBadge: null, homeScore: null, awayScore: null, state: "pre", progress: null, round: null, ...over,
});

describe("ordre et masquage des ligues", () => {
  it("défaut vide, aller-retour, clés inconnues ignorées, nouvelles ligues ajoutées", () => {
    expect(decodePrefs(null)).toEqual(DEFAULT_PREFS);
    expect(encodePrefs(DEFAULT_PREFS)).toBe("");
    const p = setLeagueHidden(moveLeague(DEFAULT_PREFS, "nba", -3), "nfl", true);
    expect(decodePrefs(encodePrefs(p))).toEqual(p);
    expect(visibleLeagues(p).some((l) => l.key === "nfl")).toBe(false);
    expect(allLeagues(p)).toHaveLength(LEAGUES.length);
    const q = decodePrefs("nba,zzz,-liga");
    expect(q.order.slice(0, 2)).toEqual(["nba", "liga"]);
    expect(q.hidden).toEqual(["liga"]);
    expect(q.order).toHaveLength(LEAGUES.length);
    expect(moveLeague(DEFAULT_PREFS, "ligue1", -1)).toBe(DEFAULT_PREFS);
    expect(setLeagueHidden(DEFAULT_PREFS, "inconnue", true)).toBe(DEFAULT_PREFS);
  });
});

describe("lecture des réponses du Worker", () => {
  it("calendrier : écussons https seulement, état, scores", () => {
    const l = parseEvents({ events: [{ i: "1", t: 1000, h: "A", a: "B", hb: "https://b/a.png", ab: "http://x", hs: 2, as: 1, st: "post", r: 10 }, { x: 1 }, { i: "2", st: "weird" }] })!;
    expect(l).toHaveLength(2);
    expect(l[0]).toMatchObject({ homeBadge: "https://b/a.png", awayBadge: null, homeScore: 2, state: "post", round: 10 });
    expect(l[1]!.state).toBe("pre");
    expect(parseEvents("x")).toBeNull();
    expect(parseEvents({})).toBeNull();
  });
  it("classement et feuille de match", () => {
    expect(parseTable({ rows: [{ rk: 1, team: "PSG", pts: 25, gd: 15 }, { rk: 2 }] })).toHaveLength(1);
    const s = parseSheet({ event: { i: "9", h: "A", a: "B", hs: 1, as: 0, st: "post", v: "Stade" }, timeline: [{ k: "goal", h: true, m: 20, pl: "X" }, { d: "?" }], stats: [{ k: "Shots", h: 5, a: 2 }, { k: "bad" }] })!;
    expect(s.venue).toBe("Stade");
    expect(s.timeline).toHaveLength(1);
    expect(s.stats).toEqual([{ label: "Shots", home: 5, away: 2 }]);
    expect(parseSheet({})).toBeNull();
  });
});

describe("mise en page et direct", () => {
  it("fusion direct + calendrier ; sections", () => {
    const sched = [m({ id: "1", start: 1000, state: "post", homeScore: 2, awayScore: 1 }), m({ id: "2", start: 3000 }), m({ id: "3", start: 2000, state: "live", homeScore: 0, awayScore: 0 })];
    const all = mergeMatches(sched, [{ ...sched[2]!, homeScore: 1 }]);
    expect(all).toHaveLength(3);
    expect(all.find((x) => x.id === "3")!.homeScore).toBe(1);
    expect(liveOf(all).map((x) => x.id)).toEqual(["3"]);
    expect(fixturesOf(all, 0).map((x) => x.id)).toEqual(["2"]);
    expect(resultsOf(all).map((x) => x.id)).toEqual(["1"]);
  });
  it("le direct n'est interrogé que si un match est en cours ou proche", () => {
    expect(mayBeLive([m({ start: T0 + 180 * MIN })], T0)).toBe(false);
    expect(mayBeLive([m({ start: T0 + 5 * MIN })], T0)).toBe(true);
    expect(mayBeLive([m({ start: T0 - 30 * MIN })], T0)).toBe(true);
    expect(mayBeLive([m({ start: T0 - 540 * MIN })], T0)).toBe(false);
    expect(mayBeLive([m({ state: "post" })], T0)).toBe(false);
    expect(mayBeLive([m({ state: "live", start: null })], T0)).toBe(true);
  });
});

describe("mode sans spoiler", () => {
  it("score masqué jusqu'à révélation ; rien à cacher avant le coup d'envoi", () => {
    const done = m({ homeScore: 2, awayScore: 1, state: "post" });
    expect(showScore(false, "1", new Set())).toBe(true);
    expect(showScore(true, "1", new Set())).toBe(false);
    expect(showScore(true, "1", new Set(["1"]))).toBe(true);
    expect(hasSpoiler(done)).toBe(true);
    expect(hasSpoiler(m())).toBe(false);
    expect(showTable(true, "ligue1", new Set())).toBe(false);
    expect(showTable(true, "ligue1", new Set(["ligue1"]))).toBe(true);
  });
});

const entry = (id: number, title: string, start: number, q = 2): SportEntry => ({
  channel: { streamId: id, display: `Chaîne ${id}`, num: id, q } as unknown as SportEntry["channel"],
  prog: { title, start, end: start + 120 * MIN } as unknown as SportEntry["prog"], sportChannel: true, strict: true,
});

describe("chaînes d'un match", () => {
  it("alias, horaire proche, meilleure qualité d'abord", () => {
    const match = m({ home: "Paris Saint-Germain", away: "Marseille" });
    const got = channelsFor(match, [entry(1, "Ligue 1 : PSG - OM", T0 + 5 * MIN, 2), entry(2, "Paris SG vs Marseille", T0, 3), entry(3, "PSG - OM", T0 - 180 * MIN), entry(4, "Lyon - Lille", T0)]);
    expect(got.map((e) => e.channel.streamId)).toEqual([2, 1]);
    expect(channelsFor(m({ home: null }), [entry(1, "PSG - OM", T0)])).toEqual([]);
    expect(channelsFor(m({ start: null }), [entry(1, "PSG - OM", T0)])).toEqual([]);
  });
});

describe("lecture mémorisée (appareil)", () => {
  beforeEach(() => resetSportLeaguesCache());
  const parse = (b: unknown) => (typeof b === "object" && b ? (b as { v: number }).v : null);
  it("1 h pour le calendrier : un seul appel dans la fenêtre", async () => {
    let calls = 0;
    const get = async () => { calls++; return { v: 1 }; };
    expect((await fetchCached("s/a", "p", 3_600_000, parse, 0, get)).status).toBe("ok");
    await fetchCached("s/a", "p", 3_600_000, parse, 30 * MIN, get);
    expect(calls).toBe(1);
    await fetchCached("s/a", "p", 3_600_000, parse, 61 * MIN, get);
    expect(calls).toBe(2);
  });
  it("échec : pause de 30 s, puis ancienne valeur servie", async () => {
    let calls = 0;
    let ok = false;
    const get = async () => { calls++; if (!ok) return null; return { v: 7 }; };
    expect((await fetchCached("x", "p", 1000, parse, 0, get)).status).toBe("unavailable");
    await fetchCached("x", "p", 1000, parse, COOLDOWN_MS - 1, get);
    expect(calls).toBe(1);
    ok = true;
    expect((await fetchCached("x", "p", 1000, parse, COOLDOWN_MS + 1, get)).status).toBe("ok");
    ok = false;
    const stale = await fetchCached("x", "p", 1000, parse, 10 * MIN, get);
    expect(stale).toEqual({ status: "ok", value: 7, stale: true });
  });
  it("une exception réseau est un échec, pas une erreur", async () => {
    const r = await fetchCached("y", "p", 1000, parse, 0, async () => { throw new Error("réseau"); });
    expect(r.status).toBe("unavailable");
  });
});
