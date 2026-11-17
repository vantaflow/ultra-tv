// Outils communs à l'espace Sport : conversions sûres, état d'un match, rapprochement d'équipes et fusion de sources.

export const HOUR = 3600;
export const num = (v) => { const n = Number.parseInt(v, 10); return Number.isFinite(n) ? n : null; };
export const httpsOrNull = (u) => (typeof u === "string" && u.startsWith("https://") ? u : null);
export const str = (v, max = 80) => (typeof v === "string" && v.trim() ? v.trim().slice(0, max) : null);

const DONE = new Set(["FT", "AET", "PEN", "AOT", "AP", "FINAL", "MATCH FINISHED", "FINISHED", "AFTER OVERTIME", "AFTER PENALTIES", "F/OT", "AWARDED", "AWD", "WO"]);
const LATER = new Set(["NS", "NOT STARTED", "TBD", "TIME TO BE DEFINED", ""]);
const OFF = new Set(["PST", "POSTPONED", "CANC", "CANCELLED", "ABD", "ABANDONED", "SUSP", "SUSPENDED"]);

/** État normalisé d'un match : pre (à venir), live, post (terminé), off (reporté / annulé). */
export function stateOf(status, tsMs, hasScore, now = Date.now()) {
  const s = String(status ?? "").trim().toUpperCase();
  if (OFF.has(s)) return "off";
  if (DONE.has(s)) return "post";
  if (LATER.has(s)) {
    if (tsMs && now - tsMs > 4 * HOUR * 1000 && hasScore) return "post";
    return "pre";
  }
  // tout autre statut (1H, HT, 2H, ET, Q1…Q4, OT, P1…P3, « In Progress », « 45' ») = en cours, sauf si très ancien
  if (tsMs && now - tsMs > 6 * HOUR * 1000) return hasScore ? "post" : "off";
  return "live";
}

// ---- rapprochement des équipes entre sources -------------------------------------------------------------------------

/** Alias connus : variantes de noms d'une source à l'autre → forme courte commune. */
const ALIAS = new Map(Object.entries({
  "paris saint germain": "psg", "paris sg": "psg",
  "manchester united": "man utd", "manchester city": "man city", "tottenham hotspur": "tottenham",
  "wolverhampton wanderers": "wolves", "newcastle united": "newcastle", "west ham united": "west ham",
  "brighton and hove albion": "brighton", "nottingham forest": "nottingham",
  "atletico madrid": "atletico", "athletic club": "athletic", "athletic bilbao": "athletic",
  "internazionale": "inter", "inter milan": "inter", "borussia monchengladbach": "gladbach", "borussia dortmund": "dortmund",
  "bayer leverkusen": "leverkusen", "bayer 04 leverkusen": "leverkusen", "bayern munich": "bayern", "bayern munchen": "bayern",
  "eintracht frankfurt": "frankfurt", "olympique de marseille": "marseille", "olympique marseille": "marseille",
  "olympique lyonnais": "lyon", "stade rennais": "rennes", "wydad athletic": "wydad", "wydad ac": "wydad",
  "raja club athletic": "raja", "raja casablanca": "raja", "renaissance sportive de berkane": "berkane", "rs berkane": "berkane",
}));
const NOISE = new Set(["fc", "cf", "afc", "sc", "ac", "as", "ss", "ssc", "us", "rc", "rcd", "ud", "cd", "sv", "vfl", "vfb", "fsv", "club", "de", "calcio", "1", "04", "05", "1899", "1909", "1846", "the"]);

/** Nom d'équipe normalisé : minuscules, sans accents ni ponctuation ni sigles (FC, AC…), alias connus appliqués. */
export function normTeam(name) {
  if (typeof name !== "string") return "";
  const base = name.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/&/g, " and ").replace(/[^a-z0-9 ]+/g, " ").replace(/\s+/g, " ").trim();
  if (ALIAS.has(base)) return ALIAS.get(base);
  const kept = base.split(" ").filter((t) => t && !NOISE.has(t)).join(" ");
  return ALIAS.get(kept) || kept || base;
}

/** Deux noms désignent la même équipe : égaux, ou tous les mots du plus court figurent dans le plus long. */
export function sameTeam(a, b) {
  const x = normTeam(a);
  const y = normTeam(b);
  if (!x || !y) return false;
  if (x === y) return true;
  const [s, l] = x.length <= y.length ? [x, y] : [y, x];
  const lt = new Set(l.split(" "));
  return s.split(" ").every((t) => lt.has(t));
}

/** Même match dans deux sources : coups d'envoi à ±3 h et mêmes équipes (domicile avec domicile). */
export function sameMatch(a, b) {
  if (!a || !b) return false;
  if (a.t !== null && b.t !== null && Math.abs(a.t - b.t) > 3 * HOUR * 1000) return false;
  return sameTeam(a.h, b.h) && sameTeam(a.a, b.a);
}

/**
 * Croise deux listes de matchs : [primary] fait foi ; les champs absents sont complétés par [secondary]. Un match de
 * [primary] adopte l'état, le score et la progression de [secondary] si celui-ci est plus avancé. Les matchs de
 * [secondary] inconnus de [primary] sont ajoutés si [addUnmatched].
 */
export function mergeEvents(primary, secondary, { addUnmatched = false } = {}) {
  const out = primary.map((e) => ({ ...e }));
  const rank = { off: 0, pre: 1, live: 2, post: 3 };
  for (const s of secondary) {
    const p = out.find((e) => sameMatch(e, s));
    if (!p) { if (addUnmatched) out.push({ ...s }); continue; }
    for (const f of ["n", "hb", "ab", "r", "p", "t"]) if (p[f] === null || p[f] === undefined) p[f] = s[f] ?? null;
    if ((rank[s.st] ?? 0) > (rank[p.st] ?? 0) && s.st !== "off") {
      p.st = s.st; p.hs = s.hs; p.as = s.as; p.p = s.p ?? p.p;
    } else if (p.hs === null && p.as === null && (s.hs !== null || s.as !== null)) {
      p.hs = s.hs; p.as = s.as;
    }
  }
  return out;
}
