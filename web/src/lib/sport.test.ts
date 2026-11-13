// Équivalents des tests Kotlin (SportDetectorTest, SportLogicTest, TeamBadgesTest, SportRenderTest) pour le bureau.
import { describe, expect, it } from "vitest";
import type { ChannelRow, ProgramRow } from "@/db/types";
import {
  BADGE_TTL_MS, isPlaceholderChannel, MAX_LIVE, MAX_UPCOMING, TITLE_HINT, badgeOfResponse, dayOffset, dayStart, fold, isSportChannel, isSportProgramme, liveNow,
  matchLineOf, ofKind, playMinute, presentKinds, progress, readBadge, sportChannels, sportKindOf, teamKey, teamNamesOf, teamQueryName, upcoming,
  type SportEntry,
} from "./sport";

const sport = (title: string, desc?: string) => isSportProgramme(title, desc);

describe("SportDetector", () => {
  it("fold : accents, casse et lettres spéciales", () => {
    expect(fold("  LIGUE 1 – Éléments ")).toBe("ligue 1 elements");
    expect(fold("FÚTBOL")).toBe("futbol");
    expect(fold("Piłka nożna")).toBe("pilka nozna");
    expect(fold("𝗦𝗣𝗢𝗥𝗧")).toBe("sport");
  });

  it("programme français", () => {
    for (const t of ["Le Journal des Sports", "Ligue 1 : PSG - OM", "Rugby : Top 14", "Live: Lyon - Marseille", "Direct : Wydad - Raja",
      "Real Madrid vs FC Barcelone", "Barça - Real Madrid", "Tennis - Roland Garros", "FORMULE 1 : Grand Prix de Monaco"]) expect(sport(t), t).toBe(true);
  });

  it("programme anglais", () => {
    expect(sport("Premier League: Arsenal v Chelsea") || sport("Premier League: Arsenal vs Chelsea")).toBe(true);
    for (const t of ["NBA: Lakers vs Celtics", "UFC 300 Main Event", "MotoGP Qualifying", "Live: Manchester United vs Liverpool", "World Cup Highlights"]) expect(sport(t), t).toBe(true);
  });

  it("programme espagnol", () => {
    for (const t of ["Fútbol: Sevilla - Betis", "Baloncesto ACB", "En directo: Valencia vs Villarreal", "Deportes Cuatro"]) expect(sport(t), t).toBe(true);
  });

  it("programme arabe", () => {
    for (const t of ["مباراة الأهلي والزمالك", "كرة القدم: المغرب ضد الجزائر", "الدوري الإسباني", "النشرة الرياضية"]) expect(sport(t), t).toBe(true);
  });

  it("programme polonais", () => {
    expect(sport("Mecz Ekstraklasy: Legia - Lech")).toBe(true);
    expect(sport("Piłka nożna: Liga Mistrzów")).toBe(true);
    expect(sport("Koszykówka: Energa Basket Liga") || sport("Siatkówka: PlusLiga")).toBe(true);
  });

  it("fausses alertes", () => {
    for (const t of ["Transport en commun", "Passport to Paradise", "Support technique", "Godzilla vs Kong", "Paris - New York", "Mon Mercredi - Le Film",
      "Live: Concert Coldplay", "Les Feux de l'amour", "   ", "eSports Weekly"]) expect(sport(t), t).toBe(false);
  });

  it("téléachat écarté même sur une chaîne de sport", () => {
    expect(isSportProgramme("Téléachat", null, true)).toBe(false);
    expect(isSportProgramme("Teleshopping Sport", null, true)).toBe(false);
    expect(isSportProgramme("Plateau d'avant-match", null, true)).toBe(true);
  });

  it("la description ne compte que pour les compétitions (120 premiers caractères)", () => {
    expect(sport("Match du jour", "Retransmission de la Premier League")).toBe(true);
    expect(sport("Le grand film", "Un champion de tennis revient chez lui")).toBe(false);
    expect(sport("Le grand film", "x".repeat(130) + " Premier League")).toBe(false);
  });

  it("chaîne par nom", () => {
    for (const n of ["beIN SPORTS 1 HD", "FR | Canal+ Sport 360", "RMC Sport 2", "DAZN 1", "Eurosport 1", "SuperSport Premier League", "Sky Sports Main Event",
      "Arena Sport 1", "ESPN 2", "TSN 3", "Sport TV 1", "AD Sports 1", "SSC 2", "beIN 1 HD", "Canal+ Foot", "Arryadia", "Movistar Deportes"]) {
      expect(isSportChannel(n, null), n).toBe(true);
    }
    expect(isSportChannel("بي ان سبورت 1", null)).toBe(true);
  });

  it("chaîne par catégorie", () => {
    expect(isSportChannel("Chaine X", "FR | SPORTS")).toBe(true);
    expect(isSportChannel("Chaine X", "قنوات رياضية")).toBe(true);
    expect(isSportChannel("Chaine X", "DEPORTES")).toBe(true);
    expect(isSportChannel("Chaine X", "PL | Sportowe")).toBe(true);
  });

  it("chaîne : fausses alertes", () => {
    expect(isSportChannel("Transport TV", null)).toBe(false);
    expect(isSportChannel("beIN Movies 1", "Cinéma")).toBe(false);
    expect(isSportChannel("Movies HD", "Cinema")).toBe(false);
    expect(isSportChannel("eSports TV", null)).toBe(false);
    expect(isSportChannel("Teleshopping Sport", null)).toBe(false);
    expect(isSportChannel(null, null)).toBe(false);
  });

  it("pré-filtre des titres : laisse passer les candidats courants", () => {
    for (const t of ["Ligue 1 : PSG - OM", "NBA: Lakers vs Celtics", "Live: Lyon - Marseille", "كرة القدم", "Fútbol: Sevilla - Betis", "Le Journal des Sports"]) expect(TITLE_HINT.test(t), t).toBe(true);
    expect(TITLE_HINT.test("Les Feux de l'amour")).toBe(false);
  });
});

// ───────────────────────── Regroupements ─────────────────────────

const H = 3_600_000;
const base = 1_700_000_000_000;
const now = base - (base % (24 * H)) + 10 * H; // 10:00 UTC

const ch = (id: number, name = `Chaine ${id}`, num = 0): ChannelRow => ({
  id, sourceId: 1, catExt: "c", ord: id, streamId: 100 + id, num, name, display: name, norm: name.toLowerCase(), country: null, q: 0, flags: 0, sep: 0, logo: null, epg: `e${id}`, archive: 0,
});
const e = (c: ChannelRow, title: string, start: number, end: number, sportChannel = false, strict = true): SportEntry => ({
  channel: c, prog: { id: c.id! * 1000 + Math.floor(start / H), sourceId: 1, epg: c.epg!, title, desc: "", start, end } as ProgramRow, sportChannel, strict,
});

describe("SportLogic", () => {
  it("en direct : chaînes de sport d'abord, puis début, et une seule par chaîne", () => {
    const a = ch(1, "Generaliste"); const b = ch(2, "beIN SPORTS 1"); const c = ch(3, "Eurosport");
    const list = [
      e(a, "Match A", now - 2 * H, now + H),
      e(b, "Match B", now - H, now + H, true),
      e(c, "Match C", now - 3 * H, now + H, true),
      e(c, "Studio C", now - H / 2, now + 2 * H, true),
      e(b, "Plus tard", now + H, now + 2 * H, true),
      e(a, "Passé", now - 5 * H, now - 4 * H),
    ];
    expect(liveNow(list, now).map((x) => x.prog.title)).toEqual(["Match B", "Studio C", "Match A"]);
  });

  it("en direct : début inclus, fin exclue", () => {
    expect(liveNow([e(ch(1), "Fini", now - H, now), e(ch(2), "Commence", now, now + H)], now).map((x) => x.prog.title)).toEqual(["Commence"]);
  });

  it("en direct : limite", () => {
    const list = Array.from({ length: 100 }, (_, i) => e(ch(i + 1), `T${i}`, now - H, now + H));
    expect(liveNow(list, now)).toHaveLength(MAX_LIVE);
    expect(liveNow(list, now, 5)).toHaveLength(5);
  });

  it("à venir : fenêtre de 48 h, dédoublonné, groupé par jour", () => {
    const a = ch(1); const b = ch(2);
    const list = [
      e(a, "Ce soir", now + 4 * H, now + 6 * H),
      e(a, "Ce soir", now + 4 * H, now + 6 * H),
      e(b, "Demain matin", now + 20 * H, now + 22 * H),
      e(b, "Demain soir", now + 30 * H, now + 32 * H),
      e(a, "Dans 47 h", now + 47 * H, now + 48 * H),
      e(a, "Dans 49 h", now + 49 * H, now + 50 * H),
      e(a, "En cours", now - H, now + H),
      e(a, "Plateau", now + 2 * H, now + 3 * H, false, false),
    ];
    const days = upcoming(list, now, "utc");
    expect(days).toHaveLength(3);
    expect(days[0]!.entries.map((x) => x.prog.title)).toEqual(["Ce soir"]);
    expect(days[1]!.entries.map((x) => x.prog.title)).toEqual(["Demain matin", "Demain soir"]);
    expect(days[2]!.entries.map((x) => x.prog.title)).toEqual(["Dans 47 h"]);
    expect(days.map((d) => dayOffset(d.dayStart, now, "utc"))).toEqual([0, 1, 2]);
  });

  it("à venir : tri par heure, puis chaîne de sport d'abord", () => {
    const a = ch(1, "Zebre"); const b = ch(2, "Alpha");
    const t = now + 3 * H;
    const days = upcoming([e(b, "Meme heure B", t, t + H), e(a, "Meme heure A", t, t + H, true), e(a, "Plus tot", t - H, t)], now, "utc");
    expect(days.flatMap((d) => d.entries).map((x) => x.prog.title)).toEqual(["Plus tot", "Meme heure A", "Meme heure B"]);
  });

  it("à venir : limite et liste vide", () => {
    const list = Array.from({ length: 300 }, (_, i) => e(ch(i + 1), `T${i}`, now + H + (i + 1) * 1000, now + 2 * H));
    expect(upcoming(list, now, "utc").reduce((n, d) => n + d.entries.length, 0)).toBe(MAX_UPCOMING);
    expect(upcoming([], now, "utc")).toEqual([]);
  });

  it("minuit du jour", () => {
    expect(dayStart(now, "utc")).toBe(now - 10 * H);
    const s = dayStart(now);
    expect(s <= now && now - s < 25 * H).toBe(true);
  });

  it("progression bornée", () => {
    expect(progress(0, 100, 50)).toBeCloseTo(0.5);
    expect(progress(100, 200, 50)).toBe(0);
    expect(progress(0, 100, 500)).toBe(1);
    expect(progress(10, 10, 10)).toBe(0);
  });

  it("chaînes sport : sans doublon, numérotées d'abord, limite", () => {
    const list = [ch(1, "Zed", 0), ch(2, "Beta", 7), ch(3, "Alpha", 3), ch(3, "Alpha", 3), ch(4, "Alpha 2", 0)];
    expect(sportChannels(list).map((c) => c.id)).toEqual([3, 2, 4, 1]);
    expect(sportChannels(list, 2)).toHaveLength(2);
  });

  it("disciplines présentes et filtre d'onglet", () => {
    const a = ch(1);
    const list = [e(a, "Rugby : Ours – Béliers", now, now + H), e(a, "NBA : Lions vs Requins", now, now + H), e(a, "Magazine", now, now + H), e(a, "Ligue 1 : Nord – Sud", now, now + H)];
    expect(presentKinds(list)).toEqual(["football", "basket", "rugby"]);
    expect(ofKind(list, "basket").map((x) => x.prog.title)).toEqual(["NBA : Lions vs Requins"]);
    expect(ofKind(list, null)).toHaveLength(4);
  });

  it("minute de jeu : football et rugby seulement", () => {
    expect(playMinute("football", now - 23 * 60_000, now)).toBe(23);
    expect(playMinute("tennis", now - 23 * 60_000, now)).toBeNull();
    expect(playMinute("football", now + 60_000, now)).toBeNull();
    expect(playMinute("rugby", now - 200 * 60_000, now)).toBeNull();
  });
});

describe("Titres de rencontre et disciplines", () => {
  it("séparateurs tiret, tiret long et vs", () => {
    for (const t of ["Club Nord - Club Sud", "Club Nord – Club Sud", "Club Nord vs Club Sud", "Club Nord vs. Club Sud"]) {
      const m = matchLineOf(t);
      expect([m.home, m.away], t).toEqual(["Club Nord", "Club Sud"]);
    }
    const c = matchLineOf("Ligue Fictive : Club Nord – Club Sud");
    expect([c.competition, c.home, c.away]).toEqual(["Ligue Fictive", "Club Nord", "Club Sud"]);
  });

  it("trait d'union dans un nom : pas un séparateur", () => {
    const m = matchLineOf("Saint-Ouen Club - Club Sud");
    expect([m.home, m.away]).toEqual(["Saint-Ouen Club", "Club Sud"]);
    expect(matchLineOf("Magazine du sport").home).toBeNull();
  });

  it("compétition et équipes, titre sans séparateur", () => {
    const m = matchLineOf("Ligue 1 : PSG – Marseille");
    expect([m.competition, m.home, m.away]).toEqual(["Ligue 1", "PSG", "Marseille"]);
    const v = matchLineOf("Lakers vs Celtics");
    expect([v.competition, v.home, v.away]).toEqual([null, "Lakers", "Celtics"]);
    const u = matchLineOf("UFC 320");
    expect([u.home, u.away, u.title]).toEqual([null, null, "UFC 320"]);
  });

  it("discipline reconnue par mots entiers", () => {
    expect(sportKindOf("Ligue 1 : PSG – Marseille")).toBe("football");
    expect(sportKindOf("NBA : Celtics – Lakers")).toBe("basket");
    expect(sportKindOf("ATP Paris : Sinner – Rune")).toBe("tennis");
    expect(sportKindOf("UFC 320")).toBe("combat");
    expect(sportKindOf("Formule 1 : Qualifications")).toBe("motor");
    expect(sportKindOf("Série policière : épisode 4")).toBeNull();
  });
});

describe("Écussons d'équipes", () => {
  it("nom interrogé : nettoyé et borné", () => {
    expect(teamQueryName("  Club   Nord (F) ")).toBe("Club Nord");
    expect(teamQueryName("Club Nord [HD]")).toBe("Club Nord");
    expect(teamQueryName("Olympique Fictif ★")).toBe("Olympique Fictif");
    expect(teamQueryName("St. Club's & Co")).toBe("St. Club's & Co");
    expect(teamQueryName("A")).toBeNull();
    expect(teamQueryName("2024")).toBeNull();
    expect(teamQueryName("x".repeat(41))).toBeNull();
    expect(teamQueryName(null)).toBeNull();
  });

  it("clé : casse et accents ignorés", () => {
    expect(teamKey("Séville Fictif")).toBe(teamKey("SEVILLE fictif"));
  });

  it("noms à résoudre : sans doublon, sports individuels ignorés, limite", () => {
    const a = ch(1, "Stade 1");
    const names = teamNamesOf([
      e(a, "Ligue 1 : Club Nord – Club Sud", 0, 1),
      e(a, "Ligue 1 : club nord – Club Est", 0, 1),
      e(a, "ATP : Joueur Un – Joueur Deux", 0, 1),
      e(a, "UFC 320", 0, 1),
      e(a, "NBA : Celtes – Lacs", 0, 1),
    ]);
    expect(names).toEqual(["Club Nord", "Club Sud", "Club Est", "Celtes", "Lacs"]);
    expect(teamNamesOf([e(a, "Club Nord – Club Sud", 0, 1), e(a, "Club Est – Club Ouest", 0, 1)], 2)).toHaveLength(2);
  });

  it("mémorisation : inconnue comprise, périmée ignorée, HTTPS seulement", () => {
    expect(readBadge({ badge: "https://img.example/b.png", at: 0 }, 10)!.badge).toBe("https://img.example/b.png");
    expect(readBadge({ badge: null, at: 0 }, 10)!.badge).toBeNull();
    expect(readBadge({ badge: null, at: 0 }, BADGE_TTL_MS)).toBeNull();
    expect(readBadge("illisible", 0)).toBeNull();
    expect(readBadge({ badge: "http://img.example/b.png", at: 0 }, 10)!.badge).toBeNull();
  });

  it("réponse du Worker", () => {
    expect(badgeOfResponse({ team: { name: "X", badge: "https://img.example/x.png" } })).toBe("https://img.example/x.png");
    expect(badgeOfResponse({ team: null })).toBeNull();
    expect(badgeOfResponse({ team: { badge: "javascript:alert(1)" } })).toBeNull();
    expect(badgeOfResponse("x")).toBeUndefined();
  });
});

describe("isPlaceholderChannel", () => {
  it("écarte les chaînes réservées sans évènement", () => {
    expect(isPlaceholderChannel("NO EVENT STREAMING NOW - I EXTRA 12")).toBe(true);
    expect(isPlaceholderChannel("DAZN 3: No Events")).toBe(true);
    expect(isPlaceholderChannel("PPV 4 | Event Offline")).toBe(true);
    expect(isPlaceholderChannel("beIN Sports 1")).toBe(false);
    expect(isPlaceholderChannel("Eventos Deportivos")).toBe(false);
    expect(isPlaceholderChannel("Canal+ Sport 360")).toBe(false);
  });
});
