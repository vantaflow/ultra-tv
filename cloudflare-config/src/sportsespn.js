// ESPN (API publique du site, gratuite, sans clé) : scores en direct de TOUT le football du jour en un seul appel
// (sélections nationales, qualifications CAN / Coupe du monde, matchs amicaux, championnats étrangers) et feuille de
// match (temps forts, statistiques). Sert de complément pour les compétitions que football-data / API-Football /
// TheSportsDB ne couvrent pas. API non officielle : en cas d'échec, l'appli garde simplement la feuille sans score.
// Aucune donnée utilisateur transmise ; résultat partagé par tous les appareils derrière le cache du Worker (60 s).

import { httpsOrNull, num, str } from "./sportscommon.js";

export const ES_BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer/all";
const STATE = { pre: "pre", in: "live", post: "post" };

const ms = (iso) => { const t = Date.parse(iso); return Number.isFinite(t) ? t : null; };
const logoOf = (team) => httpsOrNull(team?.logo || team?.logos?.[0]?.href);
const nameOf = (team) => str(team?.shortDisplayName || team?.displayName || team?.name);

/** Match ESPN (événement du tableau des scores, ou en-tête d'une feuille) au format commun { i, t, n, h, a, … }. */
export function esEvent(e) {
  const c = e?.competitions?.[0];
  if (!e?.id || !c) return null;
  const home = c.competitors?.find((x) => x.homeAway === "home");
  const away = c.competitors?.find((x) => x.homeAway === "away");
  if (!home || !away) return null;
  const status = e.status || c.status || {};
  let st = STATE[status.type?.state] || "pre";
  if (/POSTPONED|CANCELED|CANCELLED|ABANDONED|SUSPENDED|DELAYED/.test(status.type?.name || "")) st = "off";
  const scored = st === "live" || st === "post";
  let p = null;
  if (st === "live") {
    p = (status.type?.shortDetail === "HT" || status.type?.name === "STATUS_HALFTIME") ? "HT" : str(String(status.displayClock || "").replace(/'/g, ""), 12);
  }
  const h = nameOf(home.team);
  const a = nameOf(away.team);
  return {
    i: `es-${e.id}`, t: ms(e.date || c.date), n: h && a ? `${h} vs ${a}` : null, h, a,
    hb: logoOf(home.team), ab: logoOf(away.team),
    hs: scored ? num(home.score) : null, as: scored ? num(away.score) : null, st, p, r: null,
  };
}

const KIND = (t) => {
  const s = String(t || "").toLowerCase();
  if (s.includes("goal") && !s.includes("kick")) return "goal";
  if (s === "yellow-card" || s === "red-card" || s === "yellow-red-card") return "card";
  if (s.startsWith("substitution")) return "sub";
  return null;
};

const STATS = { Possession: "Possession", SHOTS: "Shots", "ON GOAL": "Shots on target", "Corner Kicks": "Corners", Fouls: "Fouls", Offsides: "Offsides", "Yellow Cards": "Yellow cards", "Red Cards": "Red cards", Saves: "Saves" };

/** Feuille de match ESPN (réponse de /summary) : score, temps forts, statistiques, stade, compétition. */
export function esMatchBody(sum) {
  const hc = sum?.header?.competitions?.[0];
  if (!hc) return null;
  const event = esEvent({ id: sum.header.id, date: hc.date, status: hc.status, competitions: [hc] });
  if (!event) return null;
  const homeId = hc.competitors.find((x) => x.homeAway === "home")?.team?.id;
  const timeline = [];
  for (const k of Array.isArray(sum.keyEvents) ? sum.keyEvents : []) {
    const kind = KIND(k.type?.type);
    if (!kind) continue;
    const ppl = Array.isArray(k.participants) ? k.participants : [];
    const minute = num(String(k.clock?.displayValue || "").replace(/'.*$/, ""));
    timeline.push({
      k: kind, d: kind === "card" ? str(k.type?.type, 40) : str(k.type?.text, 40), h: k.team?.id === homeId, m: minute,
      pl: str(ppl[0]?.athlete?.displayName), as: str(ppl[1]?.athlete?.displayName),
    });
  }
  timeline.sort((x, y) => (x.m ?? 0) - (y.m ?? 0));
  const teams = Array.isArray(sum.boxscore?.teams) ? sum.boxscore.teams : [];
  const th = teams.find((t) => t.homeAway === "home");
  const ta = teams.find((t) => t.homeAway === "away");
  const stats = [];
  if (th && ta) {
    const val = (t, label) => num((t.statistics || []).find((s) => s.label === label)?.displayValue);
    for (const [label, k] of Object.entries(STATS)) {
      const h = val(th, label); const a = val(ta, label);
      if (h !== null && a !== null) stats.push({ k, h, a });
    }
  }
  return {
    event: { ...event, v: str(sum.gameInfo?.venue?.fullName), lg: str(sum.header?.league?.name) },
    timeline: timeline.slice(0, 60), stats,
  };
}

export const es = {
  /** Tous les matchs de football du jour (direct, terminés, à venir) : un seul appel. */
  async day(ctx) {
    const b = await ctx.fetchJson("es", "live", `${ES_BASE}/scoreboard`);
    if (!b || !Array.isArray(b.events)) return null;
    return b.events.map(esEvent).filter(Boolean);
  },
  async match(ctx, raw) {
    const b = await ctx.fetchJson("es", "match", `${ES_BASE}/summary?event=${encodeURIComponent(raw)}`);
    return b ? esMatchBody(b) : null;
  },
};
