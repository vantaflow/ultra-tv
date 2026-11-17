import { describe, expect, it } from "vitest";
import { esEvent, esMatchBody } from "../src/sportsespn.js";
import { sportsDayProxy, sportsMatchProxy } from "../src/sportsdata.js";

const NOW = Date.parse("2026-11-17T15:00:00Z");
const ok = (body) => new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
function memCache() {
  const m = new Map();
  return { async match(req) { const v = m.get(req.url); return v ? new Response(v) : undefined; }, async put(req, res) { m.set(req.url, await res.text()); } };
}
const team = (id, name, logo = `https://a.espncdn.com/${id}.png`) => ({ id, displayName: name, shortDisplayName: name, logo });
const ev = (id, state, clock, hs, as, over = {}) => ({
  id, date: "2026-11-17T14:00Z", name: "Malawi at Egypt",
  status: { displayClock: clock, type: { state, name: state === "in" ? "STATUS_IN_PROGRESS" : "STATUS_X", shortDetail: clock } },
  competitions: [{ competitors: [
    { homeAway: "home", score: String(hs), team: team("1", "Egypt") },
    { homeAway: "away", score: String(as), team: team("2", "Malawi", "http://x/no.png") },
  ] }], ...over,
});

describe("ESPN", () => {
  it("réduit un match en direct (minute, score, écussons https seulement)", () => {
    const e = esEvent(ev("42", "in", "57'", 1, 0));
    expect(e).toMatchObject({ i: "es-42", h: "Egypt", a: "Malawi", hs: 1, as: 0, st: "live", p: "57", hb: "https://a.espncdn.com/1.png", ab: null });
    expect(e.t).toBe(Date.parse("2026-11-17T14:00Z"));
  });
  it("pas de score avant le coup d'envoi, mi-temps = HT", () => {
    expect(esEvent(ev("1", "pre", "0'", 0, 0))).toMatchObject({ st: "pre", hs: null, as: null, p: null });
    const ht = ev("2", "in", "45'+2'", 0, 0); ht.status.type.shortDetail = "HT";
    expect(esEvent(ht).p).toBe("HT");
    expect(esEvent(ev("3", "in", "90'+4'", 0, 0)).p).toBe("90+4");
  });
  it("feuille de match : buts, cartons, remplacements, statistiques, stade, compétition", () => {
    const hc = { date: "2026-11-17T14:00Z", status: { displayClock: "60'", type: { state: "in", shortDetail: "60'" } }, competitors: ev("9", "in", "", 1, 0).competitions[0].competitors };
    const body = esMatchBody({
      header: { id: "9", league: { name: "Africa Cup of Nations Qualifying" }, competitions: [hc] },
      gameInfo: { venue: { fullName: "Cairo Stadium" } },
      keyEvents: [
        { type: { type: "kickoff" }, clock: { displayValue: "" } },
        { type: { type: "goal---header", text: "Goal - Header" }, clock: { displayValue: "34'" }, team: { id: "1" }, participants: [{ athlete: { displayName: "Salah" } }, { athlete: { displayName: "Marmoush" } }] },
        { type: { type: "yellow-card", text: "Yellow Card" }, clock: { displayValue: "12'" }, team: { id: "2" }, participants: [{ athlete: { displayName: "Banda" } }] },
      ],
      boxscore: { teams: [
        { homeAway: "home", statistics: [{ label: "Possession", displayValue: "61.3" }, { label: "SHOTS", displayValue: "9" }] },
        { homeAway: "away", statistics: [{ label: "Possession", displayValue: "38.7" }, { label: "SHOTS", displayValue: "3" }] },
      ] },
    });
    expect(body.event).toMatchObject({ i: "es-9", st: "live", hs: 1, v: "Cairo Stadium", lg: "Africa Cup of Nations Qualifying" });
    expect(body.timeline).toEqual([
      { k: "card", d: "yellow-card", h: false, m: 12, pl: "Banda", as: null },
      { k: "goal", d: "Goal - Header", h: true, m: 34, pl: "Salah", as: "Marmoush" },
    ]);
    expect(body.stats).toEqual([{ k: "Possession", h: 61, a: 38 }, { k: "Shots", h: 9, a: 3 }]);
  });
  it("/api/sports/day : directs et terminés récents seulement, un seul appel amont mis en cache", async () => {
    let calls = 0;
    const fetchFn = async (url) => { calls++; expect(url).toBe("https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard"); return ok({ events: [ev("1", "in", "57'", 1, 0), ev("2", "pre", "0'", 0, 0), ev("3", "post", "FT", 2, 2), ev("4", "post", "FT", 0, 1, { date: "2026-11-16T10:00Z" })] }); };
    const cache = memCache();
    const r = await sportsDayProxy({}, { fetchFn, cache, now: NOW });
    expect(r.status).toBe(200);
    expect((await r.json()).events.map((e) => e.i)).toEqual(["es-1", "es-3"]);
    await sportsDayProxy({}, { fetchFn, cache, now: NOW });
    expect(calls).toBe(1);
  });
  it("/api/sports/match?id=es-N lit la feuille ESPN", async () => {
    const hc = { date: "2026-11-17T14:00Z", status: { displayClock: "FT", type: { state: "post", shortDetail: "FT" } }, competitors: ev("9", "post", "", 2, 1).competitions[0].competitors };
    const fetchFn = async (url) => { expect(url).toContain("/summary?event=77"); return ok({ header: { id: "77", competitions: [hc] } }); };
    const r = await sportsMatchProxy(new URL("https://x/?id=es-77").searchParams, {}, { fetchFn, cache: memCache(), now: NOW });
    expect(r.status).toBe(200);
    expect((await r.json()).event).toMatchObject({ i: "es-77", st: "post", hs: 2, as: 1 });
  });
});
