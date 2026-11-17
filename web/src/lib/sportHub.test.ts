import { describe, expect, it } from "vitest";
import { crestColor, filterUpcoming, hasEurope, initialsOf, liveCards, minuteOf, orderChannels, parseForm, qualityOf, roundOf, sharePercent, zoneOf } from "./sportHub";
import { parseEvents, parseSheet, type LeagueMatch, type StandingRow } from "./sportLeagues";
import type { SportEntry } from "./sport";

const MIN = 60_000;
const NOW = 1_700_000_000_000;
const entry = (id: number, title: string, start: number, q = 2, end = start + 120 * MIN): SportEntry => ({
  channel: { streamId: id, display: `Chaîne ${id}`, num: id, q } as unknown as SportEntry["channel"],
  prog: { title, start, end } as unknown as SportEntry["prog"], sportChannel: true, strict: true,
});
const live = (id: string, home: string, away: string, hs: number | null, as: number | null, progress: string | null = "67"): LeagueMatch => ({
  id, start: NOW - 67 * MIN, name: null, home, away, homeBadge: null, awayBadge: null, homeScore: hs, awayScore: as, state: "live", progress, round: 14,
});

describe("zones et forme des classements", () => {
  it("Ligue 1 : champions, Europa, relégation ; rien ailleurs", () => {
    expect(zoneOf("ligue1", 3, 18)).toBe("qualified");
    expect(zoneOf("ligue1", 4, 18)).toBe("europa");
    expect(zoneOf("ligue1", 9, 18)).toBe("none");
    expect(zoneOf("ligue1", 16, 18)).toBe("relegation");
    expect(zoneOf("bundesliga", 17, 18)).toBe("relegation");
    expect(zoneOf("bundesliga", 16, 18)).toBe("none");
    expect(zoneOf("nba", 1, 30)).toBe("none");
    expect(zoneOf("ligue1", 1, 18, true)).toBe("none");
    expect(zoneOf("ligue1", 1, 4)).toBe("none");
    expect(hasEurope("ligue1")).toBe(true);
    expect(hasEurope("botola")).toBe(false);
  });
  it("forme : cinq derniers résultats normalisés", () => {
    expect(parseForm("xWDLWW")).toEqual(["W", "D", "L", "W", "W"]);
    expect(parseForm("VVNP")).toEqual(["W", "W", "D", "L"]);
    expect(parseForm(null)).toEqual([]);
  });
});

describe("cartes et barres", () => {
  it("qualité, minute, part, dernière journée", () => {
    const c = (q: number) => ({ q }) as unknown as SportEntry["channel"];
    expect([qualityOf(c(4)), qualityOf(c(3)), qualityOf(c(2)), qualityOf(c(1)), qualityOf(c(0))]).toEqual(["4K", "HD", "HD", "SD", ""]);
    expect(minuteOf(live("1", "A", "B", 0, 0, "67"))).toBe("67'");
    expect(minuteOf(live("1", "A", "B", 0, 0, "HT"))).toBe("HT");
    expect(minuteOf(live("1", "A", "B", 0, 0, null))).toBeNull();
    expect(sharePercent(61, 39)).toBe(61);
    expect(sharePercent(0, 0)).toBe(50);
    const r = (played: number | null) => ({ rank: 1, team: "A", badge: null, group: null, played, win: null, draw: null, loss: null, goalDiff: null, points: null, form: null }) as StandingRow;
    expect(roundOf([r(13), r(14), r(null)])).toBe(14);
    expect(roundOf([r(null)])).toBeNull();
  });
  it("ligue choisie : score et chaînes triées par qualité (identifiant « fd-123 »)", () => {
    const entries = [entry(1, "Ligue 1 : PSG – Marseille", NOW - 67 * MIN, 2), entry(2, "Ligue 1 : Paris SG vs OM", NOW - 66 * MIN, 4)];
    const cards = liveCards({ leagueKey: "ligue1", leagueName: "Ligue 1", kind: "football", matches: [live("fd-123", "Paris SG", "Marseille", 2, 1)], guideLive: [], entries, alsoOn: new Map(), now: NOW, programmeLabel: () => "Programme" });
    expect(cards).toHaveLength(1);
    expect(cards[0]).toMatchObject({ key: "mfd-123", league: "Ligue 1", minute: "67'", homeScore: 2, awayScore: 1 });
    expect(cards[0]!.channels.map((e) => e.channel.streamId)).toEqual([2, 1]);
    expect(cards[0]!.progress).not.toBeNull();
  });
  it("« Tout » : directs du guide, score ajouté quand le match est connu, émission sans score", () => {
    const pm = entry(1, "Ligue 1 : PSG – Marseille", NOW - 60 * MIN);
    const mag = entry(2, "Canal Football Club", NOW - 10 * MIN);
    const cards = liveCards({ leagueKey: null, leagueName: "Ligue 1", kind: "football", matches: [live("7", "Paris SG", "Marseille", 1, 0)], guideLive: [pm, mag], entries: [pm, mag], alsoOn: new Map(), now: NOW, programmeLabel: () => "Programme" });
    expect(cards).toHaveLength(2);
    expect(cards[0]!.homeScore).toBe(1);
    expect(cards[1]!.homeScore).toBeNull();
    expect(cards[1]).toMatchObject({ title: "Canal Football Club", league: "Programme" });
  });
  it("orderChannels : meilleure qualité d'abord, une par chaîne", () => {
    const got = orderChannels([entry(1, "x", NOW, 2), entry(2, "x", NOW, 4), entry(1, "y", NOW, 2)]);
    expect(got.map((e) => e.channel.streamId)).toEqual([2, 1]);
  });
});

describe("à venir", () => {
  it("ligue choisie : ses matchs et sa compétition ; « Tout » garde les magazines", () => {
    const derby = entry(1, "Ligue 1 : Lyon – Lille", NOW + 120 * MIN);
    const nba = entry(1, "NBA : Lakers – Celtics", NOW + 180 * MIN);
    const mag = entry(1, "Canal Football Club", NOW + 240 * MIN);
    const days = [{ dayStart: 0, entries: [derby, nba, mag] }];
    const fixture: LeagueMatch = { id: "af-9", start: NOW + 120 * MIN, name: null, home: "Lyon", away: "Lille", homeBadge: null, awayBadge: null, homeScore: null, awayScore: null, state: "pre", progress: null, round: 15 };
    expect(filterUpcoming(null, [], [], days).flatMap((d) => d.entries)).toHaveLength(3);
    expect(filterUpcoming("ligue1", [fixture], [derby, nba, mag], days).flatMap((d) => d.entries)).toEqual([derby]);
    expect(filterUpcoming("nba", [], [derby, nba, mag], days).flatMap((d) => d.entries)).toEqual([nba]);
  });
});

describe("pastilles d'équipe", () => {
  it("initiales et couleur stable", () => {
    expect(initialsOf("PSG")).toBe("PSG");
    expect(initialsOf("Paris SG")).toBe("PS");
    expect(initialsOf("Marseille")).toBe("MAR");
    expect(crestColor("Paris SG")).toBe(crestColor("PSG"));
  });
});

describe("identifiants de match opaques", () => {
  it("« fd-123 », « jo-2026-12 » et nombre JSON sont lus comme des chaînes", () => {
    const ev = parseEvents({ events: [{ i: "fd-123", t: 1, h: "A", a: "B", st: "pre" }, { i: 456, t: 2, h: "C", a: "D", st: "pre" }, { i: "jo-2026-12", t: 3, n: "GP", st: "pre" }] })!;
    expect(ev.map((e) => e.id)).toEqual(["fd-123", "456", "jo-2026-12"]);
    expect(parseSheet({ event: { i: "fd-123", t: 5, h: "A", a: "B", hs: 1, as: 0, st: "post", src: "fd", stale: true }, timeline: [], stats: [] })!.match.id).toBe("fd-123");
  });
});
