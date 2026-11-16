import { describe, expect, it } from "vitest";
import {
  addUsage, advance, allowedUntil, creditMs, decode, emptyRules, encode, evaluate, extend, forToday, hhmm, nextAllowed, nextMidnight, normalize, parseHhmm, prune,
  splitByDay, usedToday, zoneFor, type Rules, type Verdict,
} from "./screenTime";

const paris = zoneFor("Europe/Paris");
const min = 60_000;
/** « 2026-11-16T10:00 » en heure du mur de la zone. */
const at = (s: string, z = paris) => { const [d, t] = s.split("T") as [string, string]; const [h, m] = t.split(":").map(Number) as [number, number]; return z.epoch(d, h * 60 + m); };
const w = (start: number, end: number) => ({ start, end });
const rules = (limit: number | null, days: Rules["days"]): Rules => ({ limit, days });
// 2026-11-16 est un lundi (heure d'hiver, UTC+1).
const monday = rules(null, { 1: [w(480, 1200)] });
const blocked = (v: Verdict) => v as Extract<Verdict, { kind: "blocked" }>;
const allowed = (v: Verdict) => v as Extract<Verdict, { kind: "allowed" }>;

describe("codage", () => {
  it("aller-retour", () => {
    const r = rules(90, { 1: [w(480, 720), w(840, 1200)], 6: [], 7: [w(0, 1440)] });
    const s = encode(r);
    expect(s).toBe("L90;1=480-720,840-1200;6=;7=0-1440");
    expect(decode(s)).toEqual(r);
  });
  it("vide et illisible", () => {
    expect(decode(null)).toEqual(emptyRules());
    expect(decode("")).toEqual(emptyRules());
    expect(decode("n'importe quoi")).toEqual(emptyRules());
    expect(decode("x".repeat(500))).toEqual(emptyRules());
    expect(encode(emptyRules())).toBe("");
  });
  it("tolérant : borne, trie et fusionne", () => {
    const r = decode("L0;L5000;8=1-2;1=900-800,600-700,650-720,0-0,1500-1600;2=480-1200,x,5");
    expect(r.limit).toBeNull();
    expect(r.days[8]).toBeUndefined();
    expect(r.days[1]).toEqual([w(600, 720)]);
    expect(r.days[2]).toEqual([w(480, 1200)]);
  });
  it("normalize fusionne les plages qui se touchent et borne à quatre", () => {
    expect(normalize([w(0, 100), w(100, 300), w(50, 60)])).toEqual([w(0, 300)]);
    expect(normalize(Array.from({ length: 8 }, (_, i) => w(i * 100, i * 100 + 50)))).toHaveLength(4);
  });
});

describe("plages", () => {
  it("dans la plage, avec le temps restant", () => {
    expect(evaluate(monday, 0, at("2026-11-16T10:00"), paris)).toEqual({ kind: "allowed", left: 10 * 3600, warn: false });
  });
  it("avertissement cinq minutes avant la fin de plage", () => {
    const v = allowed(evaluate(monday, 0, at("2026-11-16T19:55"), paris));
    expect(v.warn).toBe(true); expect(v.left).toBe(300);
    expect(allowed(evaluate(monday, 0, at("2026-11-16T19:54"), paris)).warn).toBe(false);
  });
  it("hors plage : bloqué avec la prochaine plage", () => {
    // Le mardi n'a aucune restriction : autorisé dès minuit.
    expect(evaluate(monday, 0, at("2026-11-16T20:00"), paris)).toEqual({ kind: "blocked", reason: "outside", next: at("2026-11-17T00:00") });
    expect(evaluate(monday, 0, at("2026-11-16T07:30"), paris)).toEqual({ kind: "blocked", reason: "outside", next: at("2026-11-16T08:00") });
  });
  it("jour interdit toute la journée puis lendemain en plage", () => {
    const r = rules(null, { 1: [], 2: [w(540, 600)] });
    expect(evaluate(r, 0, at("2026-11-16T12:00"), paris)).toEqual({ kind: "blocked", reason: "outside", next: at("2026-11-17T09:00") });
  });
  it("semaine entièrement interdite : aucune prochaine plage", () => {
    const r = rules(null, Object.fromEntries([1, 2, 3, 4, 5, 6, 7].map((d) => [d, []])));
    expect(evaluate(r, 0, at("2026-11-16T12:00"), paris)).toEqual({ kind: "blocked", reason: "outside", next: null });
  });
  it("plusieurs plages dans la journée", () => {
    const r = rules(null, { 1: [w(480, 720), w(840, 1200)] });
    expect(evaluate(r, 0, at("2026-11-16T13:00"), paris)).toEqual({ kind: "blocked", reason: "outside", next: at("2026-11-16T14:00") });
    expect(evaluate(r, 0, at("2026-11-16T14:00"), paris).kind).toBe("allowed");
  });
  it("plages enchaînées à minuit", () => {
    const r = rules(null, { 1: [w(1200, 1440)], 2: [w(0, 360)] });
    expect(allowedUntil(r, at("2026-11-16T21:00"), paris)).toBe(at("2026-11-17T06:00"));
    const v = allowed(evaluate(r, 0, at("2026-11-16T23:57"), paris));
    expect(v.warn).toBe(false); expect(v.left).toBe(6 * 3600 + 3 * 60);
  });
  it("jour sans restriction enchaîné avec la plage du lendemain", () => {
    expect(allowedUntil(monday, at("2026-11-15T23:00"), paris)).toBe(at("2026-11-16T00:00"));
    expect(evaluate(monday, 0, at("2026-11-16T00:00"), paris)).toEqual({ kind: "blocked", reason: "outside", next: at("2026-11-16T08:00") });
  });
  it("aucune contrainte horaire dans la semaine à venir", () => {
    expect(allowedUntil(rules(60, {}), at("2026-11-16T10:00"), paris)).toBeNull();
  });
});

describe("limite quotidienne", () => {
  it("atteinte : bloqué jusqu'à minuit", () => {
    expect(evaluate(rules(60, {}), 3600, at("2026-11-16T15:00"), paris)).toEqual({ kind: "blocked", reason: "limit", next: at("2026-11-17T00:00") });
  });
  it("atteinte : prochaine plage du lendemain", () => {
    const r = rules(60, { 1: [w(480, 1200)], 2: [w(480, 1200)] });
    expect(evaluate(r, 4000, at("2026-11-16T15:00"), paris)).toEqual({ kind: "blocked", reason: "limit", next: at("2026-11-17T08:00") });
  });
  it("avertissement à cinq minutes de la limite ; la contrainte la plus proche gagne", () => {
    const r = rules(60, { 1: [w(480, 1200)] });
    const v = allowed(evaluate(r, 3300, at("2026-11-16T10:00"), paris));
    expect(v.warn).toBe(true); expect(v.left).toBe(300);
    const v2 = allowed(evaluate(r, 600, at("2026-11-16T19:50"), paris));
    expect(v2.left).toBe(600); expect(v2.warn).toBe(false);
  });
  it("hors plage prime sur la limite", () => {
    expect(blocked(evaluate(rules(60, { 1: [w(480, 1200)] }), 5000, at("2026-11-16T21:00"), paris)).reason).toBe("outside");
  });
  it("sans règle, jamais de blocage", () => {
    expect(evaluate(emptyRules(), 999_999, at("2026-11-16T03:00"), paris)).toEqual({ kind: "free" });
  });
});

describe("dérogations", () => {
  it("+30 minutes passe outre plage et limite puis re-bloque", () => {
    const now = at("2026-11-16T21:00");
    const g = extend(null, now);
    expect(g.until).toBe(now + 30 * min);
    const during = allowed(evaluate(monday, 0, now + 10 * min, paris, g));
    expect(during.left).toBe(20 * 60); expect(during.warn).toBe(false);
    expect(allowed(evaluate(monday, 0, now + 26 * min, paris, g)).warn).toBe(true);
    expect(evaluate(monday, 0, now + 30 * min, paris, g).kind).toBe("blocked");
  });
  it("cumulative, mais une dérogation expirée ne s'additionne pas", () => {
    const now = at("2026-11-16T21:00");
    const g1 = extend(null, now);
    expect(extend(g1, now + 5 * min).until).toBe(now + 60 * min);
    expect(extend(g1, now + 90 * min).until).toBe(now + 120 * min);
  });
  it("pour aujourd'hui : jusqu'à minuit, sans avertissement", () => {
    const now = at("2026-11-16T21:00");
    const g = forToday(now, paris);
    expect(g.until).toBe(at("2026-11-17T00:00"));
    expect(allowed(evaluate(monday, 99999, at("2026-11-16T23:58"), paris, g)).warn).toBe(false);
    expect(evaluate(monday, 0, at("2026-11-23T21:00"), paris, g).kind).toBe("blocked");
  });
});

describe("minuit, changement d'heure, cumul", () => {
  it("cumul à cheval sur minuit réparti par jour local", () => {
    expect(splitByDay(at("2026-11-16T23:50"), at("2026-11-17T00:20"), paris)).toEqual({ "2026-11-16": 10 * min, "2026-11-17": 20 * min });
  });
  it("plusieurs jours", () => {
    expect(splitByDay(at("2026-11-16T22:00"), at("2026-11-18T01:00"), paris)).toEqual({ "2026-11-16": 120 * min, "2026-11-17": 1440 * min, "2026-11-18": 60 * min });
  });
  it("intervalle vide ou inversé", () => {
    expect(splitByDay(1000, 1000, paris)).toEqual({});
    expect(splitByDay(2000, 1000, paris)).toEqual({});
  });
  it("le jour du passage à l'heure d'été dure 23 heures", () => {
    const from = at("2026-03-29T00:00"); const to = at("2026-03-30T00:00");
    expect(to - from).toBe(23 * 60 * min);
    expect(splitByDay(from, to, paris)).toEqual({ "2026-03-29": 23 * 60 * min });
    expect(nextMidnight(from, paris)).toBe(to);
  });
  it("le jour du retour à l'heure d'hiver dure 25 heures", () => {
    const from = at("2026-10-25T00:00"); const to = at("2026-10-26T00:00");
    expect(to - from).toBe(25 * 60 * min);
    expect(splitByDay(from, to, paris)).toEqual({ "2026-10-25": 25 * 60 * min });
  });
  it("fin de plage correcte le jour du changement d'heure (durée réelle)", () => {
    const r = rules(null, { 7: [w(0, 1439)] });
    const until = allowedUntil(r, at("2026-03-29T08:00"), paris)!;
    expect(until).toBe(at("2026-03-29T23:59"));
    expect((until - at("2026-03-29T08:00")) / 1000).toBe(15 * 3600 + 59 * 60);
  });
  it("plage dans le trou d'heure d'été : décalée, sans exception", () => {
    const r = rules(null, { 7: [w(150, 200)] });
    expect(nextAllowed(r, at("2026-03-29T00:30"), paris)).toBe(at("2026-03-29T03:30"));
  });
  it("jour local, jour de la semaine et minute suivent le fuseau", () => {
    const utc = zoneFor("UTC"); const tokyo = zoneFor("Asia/Tokyo");
    const t = at("2026-11-16T23:30", utc);
    expect(utc.parts(t)).toEqual({ date: "2026-11-16", dow: 1, minute: 23 * 60 + 30 });
    expect(tokyo.parts(t)).toEqual({ date: "2026-11-17", dow: 2, minute: 8 * 60 + 30 });
  });
  it("cumul : ajout, jour courant, élagage", () => {
    let u = addUsage({}, at("2026-11-16T10:00"), at("2026-11-16T10:30"), paris);
    u = addUsage(u, at("2026-11-16T11:00"), at("2026-11-16T11:10"), paris);
    expect(usedToday(u, at("2026-11-16T18:00"), paris)).toBe(40 * 60);
    expect(usedToday(u, at("2026-11-17T08:00"), paris)).toBe(0);
    const many = Object.fromEntries(Array.from({ length: 30 }, (_, i) => [`2026-10-${String(i + 1).padStart(2, "0")}`, 1]));
    const kept = prune(many);
    expect(Object.keys(kept)).toHaveLength(14);
    expect("2026-10-30" in kept).toBe(true); expect("2026-10-01" in kept).toBe(false);
  });
  it("crédit plafonné et jamais négatif", () => {
    expect(creditMs(0, 1000)).toBe(1000);
    expect(creditMs(0, 600_000)).toBe(5000);
    expect(creditMs(5000, 1000)).toBe(0);
  });
});

describe("heures", () => {
  it("hhmm et parseHhmm", () => {
    expect(hhmm(0)).toBe("00:00"); expect(hhmm(510)).toBe("08:30"); expect(hhmm(1440)).toBe("24:00");
    expect(parseHhmm("08:30")).toBe(510); expect(parseHhmm("24:00")).toBe(1440); expect(parseHhmm("25:00")).toBeNull(); expect(parseHhmm("x")).toBeNull();
  });
});

describe("advance (un pas du compteur)", () => {
  const limit = rules(1, {});
  it("compte la lecture autorisée, plafonnée à 5 s par pas", () => {
    const t0 = at("2026-11-16T10:00");
    const a = advance(limit, {}, null, true, t0, t0 + 1000, paris);
    expect(usedToday(a.usage, t0 + 1000, paris)).toBe(1);
    const b = advance(limit, {}, null, true, t0, t0 + 600_000, paris);
    expect(usedToday(b.usage, t0 + 600_000, paris)).toBe(5);
  });
  it("ne compte rien hors lecture ni une fois bloqué ; bloque quand la limite est atteinte", () => {
    const t0 = at("2026-11-16T10:00");
    expect(advance(limit, {}, null, false, t0, t0 + 1000, paris).counted).toBe(false);
    let u = {}; let t = t0; let v: Verdict = { kind: "free" };
    for (let i = 0; i < 70; i++) { const r = advance(limit, u, null, true, t, t + 1000, paris); u = r.usage; t += 1000; v = r.verdict; }
    expect(v.kind).toBe("blocked");
    expect(usedToday(u, t, paris)).toBe(60); // rien de plus une fois bloqué
  });
  it("une dérogation rouvre la lecture", () => {
    const t0 = at("2026-11-16T10:00");
    const u = { "2026-11-16": 3600_000 };
    expect(advance(limit, u, null, true, t0, t0 + 1000, paris).verdict.kind).toBe("blocked");
    expect(advance(limit, u, extend(null, t0), true, t0, t0 + 1000, paris).verdict.kind).toBe("allowed");
  });
});
