// Équivalent web de SportMatchingTest (Android) : rapprochement des programmes d'un même événement.
import { describe, expect, it } from "vitest";
import type { ChannelRow, ProgramRow } from "@/db/types";
import type { SportEntry } from "./sport";
import { alsoOn, alsoOnMap, badgesOf, canonTeam, collapseEvents, competitionOf, entryKey, groupEvents, parseEvent, sameEvent } from "./sportMatch";

const MIN = 60_000;
const T0 = 1_700_000_000_000;

const ch = (id: number, over: Partial<ChannelRow> = {}): ChannelRow => ({
  id, sourceId: 1, catExt: "1", ord: id, streamId: id, num: 0, name: `Chaine ${id}`, display: `Chaine ${id}`, norm: `chaine ${id}`, country: null,
  q: 0, flags: 0, sep: 0, logo: null, epg: null, archive: 0, ...over,
});
const e = (c: ChannelRow, title: string, start = T0, sportChannel = false): SportEntry => ({
  channel: c, sportChannel, strict: true,
  prog: { sourceId: 1, epg: `e${c.id}`, start, end: start + 120 * MIN, title } as ProgramRow,
});
const key = (t: string) => parseEvent(t).key;
const same = (a: string, b: string, dMin = 0) => sameEvent(parseEvent(a), T0, parseEvent(b), T0 + dMin * MIN);

describe("sportMatch", () => {
  it("français : variantes du même match", () => {
    const k = key("PSG - OM");
    for (const t of ["Paris SG vs Marseille", "Ligue 1 : PSG / OM", "Ligue 1 : Paris Saint-Germain - Olympique de Marseille", "Direct : Paris SG contre OM (HD)", "OM - PSG"]) expect(key(t), t).toBe(k);
    expect(parseEvent("Ligue 1 : PSG / OM").competition).toBe("ligue1");
    expect(parseEvent("FR | Ligue 1 Uber Eats - PSG - OM").competition).toBe("ligue1");
  });

  it("anglais : abréviations courantes", () => {
    expect(key("Man Utd vs Liverpool")).toBe(key("Manchester United v Liverpool FC"));
    expect(key("Man City vs Spurs")).toBe(key("Premier League: Manchester City - Tottenham Hotspur"));
    expect(key("Real Madrid vs Barca")).toBe(key("Real Madrid CF - FC Barcelona"));
    expect(key("Bayern Munich vs BVB")).toBe(key("FC Bayern München - Borussia Dortmund"));
    expect(key("Inter vs Juve")).toBe(key("Inter Milan - Juventus FC 20:45"));
    expect(parseEvent("Premier League: Man City v Spurs").competition).toBe("epl");
  });

  it("espagnol et portugais", () => {
    expect(key("Atlético de Madrid vs Barcelona")).toBe(key("Atletico Madrid x FC Barcelona"));
    expect(parseEvent("LaLiga: Sevilla - Betis").competition).toBe("laliga");
  });

  it("arabe translittéré et arabe", () => {
    expect(key("Al Ahly vs Zamalek")).toBe(key("الأهلي - الزمالك"));
    expect(key("Al-Ahly SC vs Zamalek SC")).toBe(key("Ahly - Zamalek"));
    expect(key("Wydad AC - Raja CA")).toBe(key("Wydad Casablanca vs Raja Casablanca"));
    expect(key("Wydad AC - Raja CA")).toBe(key("الوداد - الرجاء"));
    expect(key("Al Hilal - Al Nassr")).toBe(key("الهلال ضد النصر"));
    expect(parseEvent("Botola Pro: Wydad - Raja").competition).toBe("botola");
  });

  it("horaire : tolérance de 15 minutes", () => {
    expect(same("PSG - OM", "Paris SG vs Marseille", 0)).toBe(true);
    expect(same("PSG - OM", "Paris SG vs Marseille", 15)).toBe(true);
    expect(same("PSG - OM", "Paris SG vs Marseille", -15)).toBe(true);
    expect(same("PSG - OM", "Paris SG vs Marseille", 16)).toBe(false);
    expect(same("PSG - OM", "PSG - OM", 24 * 60)).toBe(false);
  });

  it("compétitions différentes : pas de mélange", () => {
    expect(same("Ligue 1 : PSG - OM", "Coupe de France : PSG - OM")).toBe(false);
    expect(same("Ligue 1 : PSG - OM", "PSG - OM")).toBe(true);
  });

  it("équipes différentes : non rapprochées", () => {
    expect(same("PSG - OM", "PSG - Lyon")).toBe(false);
    expect(same("Man City - Arsenal", "Man Utd - Arsenal")).toBe(false);
    expect(same("Real Madrid - Barcelona", "Atletico Madrid - Barcelona")).toBe(false);
  });

  it("sans équipes : titre identique et horaire strict", () => {
    expect(same("UFC 300 : Main Card", "UFC 300 : Main Card (HD)", 0)).toBe(true);
    expect(same("Grand Prix de Monaco", "Grand Prix de Monaco - Live", 5)).toBe(true);
    expect(same("Grand Prix de Monaco", "Grand Prix de Monaco", 6)).toBe(false);
    expect(same("Football", "Basketball")).toBe(false);
    expect(same("Magazine du sport", "Studio foot")).toBe(false);
    expect(same("", "")).toBe(false);
  });

  it("tirets dans un nom d'équipe", () => {
    const p = parseEvent("Paris Saint-Germain - Marseille");
    expect(p.isMatch).toBe(true);
    expect(p.key).toBe(key("PSG - OM"));
    expect(parseEvent("PSG - PSG").isMatch).toBe(false);
    expect(parseEvent("Documentaire - Histoire - Culture").isMatch).toBe(false);
  });

  it("competitionOf et canonTeam", () => {
    expect(competitionOf("UEFA Champions League")).toBe("ucl");
    expect(competitionOf("Ligue des Champions")).toBe("ucl");
    expect(competitionOf("CAF Champions League")).toBe("caf-cl");
    expect(competitionOf("La Liga EA Sports")).toBe("laliga");
    expect(competitionOf("Football")).toBeNull();
    expect(canonTeam("Paris S.G.")).toBe("psg");
    expect(canonTeam("Bayern München")).toBe("bayern");
    expect(canonTeam("FC Nantes")).toBe(canonTeam("Nantes"));
    expect(canonTeam("  ")).toBe("");
  });

  it("groupEvents : un groupe par événement", () => {
    const [a, b, c, d] = [ch(1), ch(2), ch(3), ch(4)];
    const g = groupEvents([e(a!, "PSG - OM"), e(b!, "Ligue 1 : Paris SG vs Marseille", T0 + 10 * MIN), e(c!, "Lyon - Monaco"), e(d!, "PSG - OM", T0 + 3 * 24 * 60 * MIN)]);
    expect(g).toHaveLength(3);
    expect(g.find((x) => x.length === 2)!.map((x) => x.channel.id).sort()).toEqual([1, 2]);
  });

  it("alsoOn : autres chaînes seulement, triées (qualité, puis numéro)", () => {
    const a = ch(1, { display: "TV A", num: 5 });
    const b = ch(2, { display: "TV B", num: 3 });
    const c = ch(3, { display: "TV C", num: 1 });
    const hd = ch(4, { display: "TV D", q: 3 });
    const list = [e(a, "PSG - OM"), e(b, "Paris SG vs Marseille", T0 + 5 * MIN), e(c, "PSG / OM"), e(hd, "PSG - OM"), e(ch(5), "Lyon - Nice")];
    expect(alsoOn(list[0]!, list).map((x) => x.channel.id)).toEqual([4, 3, 2]);
    expect(alsoOn(list[4]!, list)).toEqual([]);
  });

  it("alsoOn : une entrée par chaîne", () => {
    const [a, b] = [ch(1), ch(2)];
    const list = [e(a!, "PSG - OM"), e(b!, "PSG - OM"), e(b!, "Paris SG vs Marseille", T0 + 5 * MIN)];
    expect(alsoOn(list[0]!, list)).toHaveLength(1);
  });

  it("alsoOnMap : une clé pour chaque membre", () => {
    const [a, b, c] = [ch(1), ch(2), ch(3)];
    const list = [e(a!, "PSG - OM"), e(b!, "Paris SG vs Marseille"), e(c!, "Autre chose")];
    const m = alsoOnMap(list);
    expect(m.get(entryKey(list[0]!))!.map((x) => x.channel.id)).toEqual([2]);
    expect(m.get(entryKey(list[1]!))!.map((x) => x.channel.id)).toEqual([1]);
    expect(m.has(entryKey(list[2]!))).toBe(false);
  });

  it("collapseEvents : garde la première et l'ordre", () => {
    const [a, b, c] = [ch(1), ch(2), ch(3)];
    expect(collapseEvents([e(a!, "Lyon - Nice"), e(b!, "PSG - OM"), e(c!, "Paris SG vs Marseille", T0 + MIN)]).map((x) => x.channel.id)).toEqual([1, 2]);
    expect(collapseEvents([])).toEqual([]);
  });

  it("badgesOf : qualité et pays", () => {
    expect(badgesOf(ch(1, { q: 3, country: "fr" }))).toBe("FHD · FR");
    expect(badgesOf(ch(1, { q: 2 }))).toBe("HD");
    expect(badgesOf(ch(1))).toBe("");
  });
});
