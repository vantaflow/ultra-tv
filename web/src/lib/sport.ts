// Rubrique Sport : détection, disciplines, regroupements et écussons — logique pure (aucune dépendance au DOM ni à la base).
// Port fidèle de android-native/…/data/sport (SportDetector, SportKind, SportLogic, TeamBadgeLogic) : mêmes listes de mots,
// mêmes règles, mêmes tests (sport.test.ts). Tout vient du guide et des catégories de LA source de l'utilisateur.
import type { ChannelRow, ProgramRow } from "@/db/types";

// ───────────────────────── Repli du texte ─────────────────────────

const NON_LETTER = /[^\p{L}\p{N}]+/gu;
const MARK = /\p{Mn}/u;

/**
 * Minuscules, décomposition de compatibilité (lettres « fantaisie » des noms de chaînes), sans accents ni voyelles
 * arabes, quelques lettres sans décomposition (ł, ß, variantes de l'alef), tout le reste en espaces simples.
 */
export function fold(s: string): string {
  const n = s.normalize("NFKD").toLowerCase();
  let out = "";
  for (const ch of n) {
    if (MARK.test(ch) || ch === "ـ") continue;
    switch (ch) {
      case "ł": out += "l"; break;
      case "ß": out += "ss"; break;
      case "ø": out += "o"; break;
      case "أ": case "إ": case "آ": out += "ا"; break;
      case "ة": out += "ه"; break;
      case "ى": out += "ي"; break;
      default: out += ch;
    }
  }
  return out.replace(NON_LETTER, " ").trim();
}

const stripArticle = (t: string) => (t.length > 4 && t.startsWith("ال") ? t.slice(2) : t);

/** Mots d'un texte replié ; l'article arabe « ال » est retiré (« الدوري » = « دوري »). */
export const tokens = (folded: string): string[] => (folded ? folded.split(" ").map(stripArticle) : []);

class Words {
  private exact: Set<string>;
  private starts: string[];
  private phr: string[];
  constructor(words: string[], prefixes: string[] = [], phrases: string[] = []) {
    this.exact = new Set(words.map((w) => stripArticle(fold(w))));
    this.starts = prefixes.map(fold);
    this.phr = phrases.map((p) => ` ${fold(p)} `);
  }
  matches(folded: string, toks: string[] = tokens(folded)): boolean {
    if (!folded) return false;
    for (const t of toks) {
      if (this.exact.has(t)) return true;
      for (const p of this.starts) if (t.startsWith(p)) return true;
    }
    if (!this.phr.length) return false;
    const padded = ` ${folded} `;
    return this.phr.some((p) => padded.includes(p));
  }
}

// ── Disciplines ──
const DISCIPLINES = new Words(
  [
    "football", "foot", "soccer", "futbol", "fussball", "calcio", "basket", "nba", "nfl", "nhl", "mlb", "tennis", "tenis",
    "rugby", "f1", "motogp", "boxe", "boxing", "boxeo", "ufc", "mma", "cycling", "volley", "golf", "cricket", "baseball",
    "hockey", "nascar", "wta", "atp", "motocross", "snooker", "darts", "mecz", "meczu", "mecze", "pilka", "ekstraklasa",
    "koszykowka", "siatkowka", "zuzel", "baloncesto", "ciclismo", "balonmano", "automovilismo", "motociclismo", "automobilisme",
    "مباراة", "مباريات", "دوري", "كأس",
  ],
  ["footbal", "basketbal", "handbal", "volleybal", "cyclis", "athleti", "gymnast", "fussbal", "natation", "swimming", "formul"],
  [
    "moto gp", "moto 3", "moto2", "kick boxing", "arts martiaux", "pilka nozna", "pilka reczna", "skoki narciarskie", "tennis de table",
    "كرة القدم", "كرة السلة", "كرة اليد", "كرة الطائرة", "كرة المضرب",
  ],
);

// ── Compétitions ──
const COMPETITIONS = new Words(
  ["bundesliga", "laliga", "eredivisie", "botola", "wimbledon", "afcon", "ucl", "uefa", "fifa", "olympics"],
  [],
  [
    "premier league", "champions league", "europa league", "conference league", "la liga", "liga mx", "liga portugal", "serie a",
    "ligue 1", "ligue 2", "ligue des champions", "ligue europa", "coupe du monde", "world cup", "copa america", "copa libertadores",
    "copa del rey", "fa cup", "coupe de france", "carabao cup", "grand prix", "formula 1", "formule 1", "formula one", "tour de france",
    "roland garros", "us open", "australian open", "grand slam", "super bowl", "six nations", "top 14", "champions cup",
    "coupe d afrique", "africa cup", "jeux olympiques", "diamond league", "liga mistrzow", "puchar polski", "liga de campeones",
    "كأس العالم", "دوري أبطال", "الدوري الإسباني", "الدوري الإنجليزي", "الدوري الإيطالي",
  ],
);

// ── Mots génériques « sport » ──
const GENERIC = new Words(
  ["sport", "sports", "spor", "deporte", "deportes", "deportivo", "deportiva", "sportif", "sportive", "sportifs", "sportowy", "sportowe", "رياضة", "رياضي", "رياضية", "رياضات"],
  ["sport", "deport"],
);

/** Tournures qui écartent un programme (téléachat). Le mot entier suffit pour « transport », « support »… */
const NEGATIVE = new Words(["teleshopping", "teleachat", "televente"], [], ["tele achat", "tele shopping", "tele boutique", "home shopping"]);

/** Mots d'équipes : clubs et sigles courants, assez distinctifs pour valider un « A - B » hors chaîne de sport. */
const TEAM_WORDS = new Words(
  [
    "fc", "cf", "ac", "cd", "ca", "rc", "ud", "afc", "sv", "fk", "bk", "psg", "om", "ol", "asse", "ogcn", "losc", "rcl", "oss",
    "athletic", "atletico", "sporting", "olympique", "olympiakos", "racing", "dynamo", "dinamo", "rovers", "wanderers", "rangers",
    "celtic", "juventus", "milan", "napoli", "lazio", "barcelona", "barcelone", "barca", "madrid", "marseille", "lyon", "monaco",
    "lille", "rennes", "nantes", "strasbourg", "bayern", "munich", "dortmund", "leverkusen", "schalke", "chelsea", "arsenal",
    "liverpool", "tottenham", "everton", "manchester", "newcastle", "wydad", "raja", "hassania", "berkane", "ahly", "zamalek",
    "esperance", "sevilla", "valencia", "betis", "villarreal", "porto", "benfica", "ajax", "galatasaray", "fenerbahce", "besiktas",
    "lakers", "celtics", "warriors", "knicks", "lech", "legia", "wisla", "gornik", "cracovia", "slask", "lechia",
    "الأهلي", "الزمالك", "الهلال", "النصر", "الوداد", "الرجاء", "ريال", "برشلونة",
  ],
  ["united", "juventus", "atletic"],
);

/** Chaîne dont le NOM ou la catégorie annonce du sport (en plus des mots génériques). */
const CHANNEL = new Words(
  [
    "dazn", "tsn", "ssc", "foot", "nba", "nfl", "nhl", "mlb", "ufc", "wwe", "golf", "tennis", "f1", "gol", "mutv", "arryadia", "alkass",
    "botola", "laliga", "bundesliga", "سبورت", "سبورتس", "الرياضية", "الرياضة",
  ],
  ["beinsport", "espn", "eurosport", "supersport", "dsport", "motorsport", "racing", "fight"],
  ["bein sport", "bein sports", "ligue 1", "premier league", "champions league", "serie a", "la liga"],
);

/** « beIN » seul est du sport, sauf ses chaînes de cinéma, séries, enfants et cuisine. */
const BEIN_NOT_SPORT = new Words(["movies", "drama", "series", "serie", "junior", "gourmet", "cinema", "box", "premium"]);

const VERSUS = /^(.{2,60}?)\s+(?:vs\.?|versus|v\/s)\s+(.{2,60})$/i;
const DASH = /^(.{2,40}?)\s+[-–—]\s+(.{2,40}?)\s*(?:\(.*\)|\[.*\])?\s*$/;
const LIVE_PREFIX = /^\s*(?:live|direct|en direct|en vivo|en directo|na zywo|na żywo|مباشر)\s*[:|•\-–]\s*/i;

const isTeamLike = (side: string) => TEAM_WORDS.matches(fold(side));
const matchesAnyKeyword = (f: string, toks: string[]) => GENERIC.matches(f, toks) || DISCIPLINES.matches(f, toks) || COMPETITIONS.matches(f, toks);

/**
 * Ce programme est-il du sport ? `onSportChannel` : il passe sur une chaîne de sport (tout y est du sport, sauf téléachat).
 * Sinon : un mot de sport ou de compétition dans le titre, « A vs B » avec une équipe reconnue (ou précédé de « Live : »),
 * « A - B » où les deux côtés sont des équipes, « Live : / Direct : » suivi de sport. `description` (120 premiers
 * caractères) : seuls les noms de COMPÉTITIONS y comptent, pour ne pas transformer un film qui parle de tennis en match.
 */
export function isSportProgramme(title: string, description?: string | null, onSportChannel = false): boolean {
  if (!title.trim()) return false;
  const f = fold(title);
  if (!f) return false;
  const toks = tokens(f);
  if (NEGATIVE.matches(f, toks)) return false;
  if (onSportChannel) return true;
  if (matchesAnyKeyword(f, toks)) return true;
  const live = LIVE_PREFIX.exec(title);
  const rest = live ? title.slice(live.index + live[0].length).trim() : title.trim();
  const vs = VERSUS.exec(rest);
  if (vs && (live || isTeamLike(vs[1]!) || isTeamLike(vs[2]!))) return true;
  const dash = DASH.exec(rest);
  if (dash) {
    const a = isTeamLike(dash[1]!);
    const b = isTeamLike(dash[2]!);
    if ((a && b) || (live && (a || b))) return true;
  }
  if (description && COMPETITIONS.matches(fold(description.slice(0, 120)))) return true;
  return false;
}

function isSportName(name: string | null | undefined): boolean {
  if (!name || !name.trim()) return false;
  const f = fold(name);
  if (!f) return false;
  const toks = tokens(f);
  if (NEGATIVE.matches(f, toks)) return false;
  if (GENERIC.matches(f, toks) || CHANNEL.matches(f, toks)) return true;
  return toks.includes("bein") && !BEIN_NOT_SPORT.matches(f, toks);
}

/** Chaîne de sport d'après son nom et/ou le nom de sa catégorie. */
export const isSportChannel = (channelName: string | null | undefined, categoryName: string | null | undefined): boolean =>
  isSportName(channelName) || isSportName(categoryName);

/**
 * Pré-filtre rapide des titres (équivalent des motifs LIKE d'Android, insensible à la casse) : le filtre exact reste
 * isSportProgramme. Sert à ne replier que les titres candidats quand on parcourt tout le guide.
 */
export const TITLE_HINT = /sport|football|soccer| vs\.? |^live ?:|^direct ?:|ligue 1|league|liga|bundesliga|serie a|nba|nfl|nhl|ufc|mma|tennis|tenis|rugby|basket|handball|cyclisme|formul|motogp|boxe|boxing|f.tbol|mecz|coupe du monde|grand prix|botola|derby|كرة|مباراة|دوري|رياض/i;

// ───────────────────────── Disciplines (onglets) ─────────────────────────

export type SportKind = "football" | "basket" | "tennis" | "combat" | "motor" | "rugby";

const KINDS: { k: SportKind; words: Set<string>; phrases: string[] }[] = ([
  {
    k: "football",
    words: new Set(["football", "foot", "soccer", "futbol", "fussball", "calcio", "bundesliga", "uefa", "ekstraklasa"]),
    phrases: ["ligue 1", "ligue 2", "liga", "serie a", "premier league", "ligue des champions", "champions league", "coupe du monde", "europa league", "pilka nozna", "كرة القدم"],
  },
  { k: "basket", words: new Set(["basket", "basketball", "nba", "baloncesto", "koszykowka", "euroleague"]), phrases: ["كرة السلة"] },
  { k: "tennis", words: new Set(["tennis", "tenis", "atp", "wta", "roland", "wimbledon"]), phrases: ["roland garros", "us open", "australian open"] },
  { k: "combat", words: new Set(["boxe", "boxing", "boxeo", "ufc", "mma", "judo", "kickboxing"]), phrases: ["arts martiaux", "kick boxing"] },
  { k: "motor", words: new Set(["f1", "motogp", "nascar", "rallye", "rally", "formule", "formula", "automobilisme", "motocross"]), phrases: ["moto gp", "grand prix"] },
  { k: "rugby", words: new Set(["rugby"]), phrases: ["top 14", "six nations"] },
] as { k: SportKind; words: Set<string>; phrases: string[] }[]).map((d) => ({ ...d, phrases: d.phrases.map((p) => ` ${fold(p)} `) }));

export const SPORT_KINDS: readonly SportKind[] = KINDS.map((d) => d.k);

/** Discipline du texte (titre, compétition…) ou null. Premier onglet qui correspond, dans l'ordre de déclaration. */
export function sportKindOf(...texts: Array<string | null | undefined>): SportKind | null {
  const folded = fold(texts.filter((t): t is string => !!t).join(" "));
  if (!folded) return null;
  const toks = new Set(folded.split(" "));
  const padded = ` ${folded} `;
  return KINDS.find((d) => [...d.words].some((w) => toks.has(w)) || d.phrases.some((p) => padded.includes(p)))?.k ?? null;
}

/** Titre de programme décomposé pour une carte : compétition éventuelle (« Ligue 1 : PSG - OM »), puis deux côtés ou le titre entier. */
export interface MatchLine { competition: string | null; home: string | null; away: string | null; title: string }

const MATCH_SEPARATOR = /\s+(?:vs\.?|v|contre|[-–—])\s+/i;

/** Décompose le titre d'un programme de sport ; sans séparateur reconnu, `home` et `away` sont nuls. */
export function matchLineOf(rawTitle: string): MatchLine {
  const title = rawTitle.trim();
  let competition: string | null = null;
  let rest = title;
  const colon = title.indexOf(":");
  if (colon >= 3 && colon <= 30) {
    const tail = title.slice(colon + 1).trim();
    if (MATCH_SEPARATOR.test(tail)) { competition = title.slice(0, colon).trim(); rest = tail; }
  }
  const m = MATCH_SEPARATOR.exec(rest);
  if (m) {
    const home = rest.slice(0, m.index).trim();
    const away = rest.slice(m.index + m[0].length).trim();
    if (home && away) return { competition, home, away, title };
  }
  return { competition: null, home: null, away: null, title };
}

// ───────────────────────── Regroupements de l'écran ─────────────────────────

/**
 * Un programme de sport candidat sur une chaîne. `sportChannel` : la chaîne est une chaîne de sport ; `strict` : le programme
 * est reconnu comme sport par son seul titre (les chaînes de sport diffusent aussi des plateaux et des rediffusions : la liste
 * « À venir » ne garde que les rencontres et événements reconnus).
 */
export interface SportEntry { channel: ChannelRow; prog: ProgramRow; sportChannel: boolean; strict: boolean }

/** Les programmes d'une journée locale (`dayStart` = minuit local). */
export interface SportDay { dayStart: number; entries: SportEntry[] }

/** Fuseau des calculs de jour : celui de la machine, ou UTC (tests). */
export type Zone = "local" | "utc";

export const HOUR = 3_600_000;
export const DAY = 24 * HOUR;
export const UPCOMING_MS = 48 * HOUR;
export const MAX_LIVE = 60;
export const MAX_UPCOMING = 120;

/** Clé stable d'une chaîne (identifiant local, sinon numéro de flux). */
export const chanKey = (c: ChannelRow): number => c.id ?? c.streamId;

const byText = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

/**
 * « En direct maintenant » : programmes en cours, UN par chaîne (le plus récemment commencé), chaînes de sport d'abord,
 * puis début le plus ancien, puis titre. Au plus `limit`.
 */
export function liveNow(entries: SportEntry[], now: number, limit = MAX_LIVE): SportEntry[] {
  const best = new Map<number, SportEntry>();
  for (const e of entries) {
    if (!(e.prog.start <= now && e.prog.end > now)) continue;
    const k = chanKey(e.channel);
    const cur = best.get(k);
    if (!cur || e.prog.start > cur.prog.start) best.set(k, e);
  }
  return [...best.values()]
    .sort((a, b) => Number(b.sportChannel) - Number(a.sportChannel) || a.prog.start - b.prog.start || byText(a.channel.display, b.channel.display) || chanKey(a.channel) - chanKey(b.channel))
    .slice(0, limit);
}

/** Minuit du jour de `ms`. */
export function dayStart(ms: number, zone: Zone = "local"): number {
  if (zone === "utc") return ms - (((ms % DAY) + DAY) % DAY);
  const d = new Date(ms);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Écart en jours calendaires entre `day` (minuit) et le jour de `now` : 0 aujourd'hui, 1 demain. */
export const dayOffset = (day: number, now: number, zone: Zone = "local"): number => Math.round((day - dayStart(now, zone)) / DAY);

/**
 * « À venir (48 h) » : programmes reconnus (`strict`) qui commencent après `now` et au plus tard dans 48 h, dédoublonnés
 * (même chaîne, même début), regroupés par jour, triés par heure. Au plus `limit` au total.
 */
export function upcoming(entries: SportEntry[], now: number, zone: Zone = "local", limit = MAX_UPCOMING): SportDay[] {
  const to = now + UPCOMING_MS;
  const seen = new Set<string>();
  const picked = entries
    .filter((e) => e.strict && e.prog.start > now && e.prog.start <= to)
    .filter((e) => { const k = `${chanKey(e.channel)}|${e.prog.start}`; if (seen.has(k)) return false; seen.add(k); return true; })
    .sort((a, b) => a.prog.start - b.prog.start || Number(b.sportChannel) - Number(a.sportChannel) || byText(a.channel.display, b.channel.display) || chanKey(a.channel) - chanKey(b.channel))
    .slice(0, limit);
  const days = new Map<number, SportEntry[]>();
  for (const e of picked) {
    const d = dayStart(e.prog.start, zone);
    const list = days.get(d);
    if (list) list.push(e); else days.set(d, [e]);
  }
  return [...days].map(([d, list]) => ({ dayStart: d, entries: list })).sort((a, b) => a.dayStart - b.dayStart);
}

/** Part écoulée d'un programme en cours, 0..1. */
export const progress = (start: number, end: number, now: number): number =>
  Math.min(1, Math.max(0, (now - start) / Math.max(1, end - start)));

/**
 * Chaîne « réservée » sans évènement (« NO EVENT STREAMING NOW », « No events », « Event offline »…) : les fournisseurs
 * en publient des centaines pour leurs évènements à la demande ; vides, elles noyaient la liste.
 */
export function isPlaceholderChannel(name: string): boolean {
  return /\bno\s+(live\s+)?events?\b|\bevents?\s+(offline|not\s+(live|started))\b|\bno\s+stream(ing)?\b/i.test(name);
}

/** Chaînes de sport : sans doublon ni chaîne réservée vide, triées par numéro (ceux sans numéro en dernier) puis nom, au plus `limit`. */
export function sportChannels(channels: ChannelRow[], limit = 200): ChannelRow[] {
  const seen = new Set<number>();
  return channels
    .filter((c) => { const k = chanKey(c); if (seen.has(k) || isPlaceholderChannel(c.display)) return false; seen.add(k); return true; })
    .sort((a, b) => Number(a.num === 0) - Number(b.num === 0) || a.num - b.num || byText(a.display, b.display) || chanKey(a) - chanKey(b))
    .slice(0, limit);
}

/** Disciplines présentes dans `list`, dans l'ordre de déclaration (onglets proposés). */
export function presentKinds(list: SportEntry[]): SportKind[] {
  const found = new Set<SportKind>();
  for (const e of list) { const k = sportKindOf(e.prog.title, e.prog.desc); if (k) found.add(k); }
  return SPORT_KINDS.filter((k) => found.has(k));
}

/** Filtre d'onglet : null = « Tout ». */
export const ofKind = (list: SportEntry[], kind: SportKind | null): SportEntry[] =>
  kind == null ? list : list.filter((e) => sportKindOf(e.prog.title, e.prog.desc) === kind);

/** Minute de jeu affichée sur une carte (football, rugby) : null hors de 1..150 ou pour les autres disciplines. */
export function playMinute(kind: SportKind | null, start: number, now: number): number | null {
  const m = Math.floor((now - start) / 60_000);
  return (kind === "football" || kind === "rugby") && m >= 1 && m <= 150 ? m : null;
}

// ───────────────────────── Écussons d'équipes ─────────────────────────

/** Durée de mémorisation d'un écusson (et d'une équipe inconnue). */
export const BADGE_TTL_MS = 30 * DAY;
/** Équipes résolues au plus par passage de l'écran Sport. */
export const BADGE_MAX_NAMES = 40;

const BRACKETS = /\([^)]*\)|\[[^\]]*\]/g;
const NOT_ALLOWED = /[^\p{L}\p{N} .'&-]+/gu;

/**
 * Nom envoyé au Worker : sans parenthèses ni crochets, caractères simples seulement (ceux qu'accepte le Worker),
 * 2 à 40 caractères, pas uniquement des chiffres ; null sinon. Jamais la chaîne ni l'adresse du flux.
 */
export function teamQueryName(raw: string | null | undefined): string | null {
  if (!raw || !raw.trim()) return null;
  let s = raw.replace(BRACKETS, " ").replace(NOT_ALLOWED, " ").replace(/\s+/g, " ").trim();
  s = s.replace(/^[-. ]+|[-. ]+$/g, "");
  if (s.length < 2 || s.length > 40 || /^[0-9 ]+$/.test(s)) return null;
  return s;
}

/** Clé de cache d'un nom brut (repliée : casse et accents ignorés) ; null si le nom n'est pas interrogeable. */
export function teamKey(raw: string | null | undefined): string | null {
  const q = teamQueryName(raw);
  const k = q ? fold(q) : "";
  return k || null;
}

/** Disciplines à équipes : les joueurs de tennis, combattants et pilotes ne sont pas cherchés. */
export const isTeamSport = (kind: SportKind | null): boolean => kind == null || kind === "football" || kind === "basket" || kind === "rugby";

/** Noms d'équipes à résoudre pour ces programmes : côtés « A – B » des disciplines à équipes, sans doublon, au plus `max`. */
export function teamNamesOf(entries: SportEntry[], max = BADGE_MAX_NAMES): string[] {
  const out = new Map<string, string>();
  for (const e of entries) {
    if (!isTeamSport(sportKindOf(e.prog.title, e.prog.desc))) continue;
    const line = matchLineOf(e.prog.title);
    for (const side of [line.home, line.away]) {
      const q = teamQueryName(side);
      const k = q ? fold(q) : "";
      if (!q || !k) continue;
      if (!out.has(k)) out.set(k, q);
      if (out.size >= max) return [...out.values()];
    }
  }
  return [...out.values()];
}

/** Écusson mémorisé : `badge` nul = équipe inconnue. */
export interface BadgeHit { badge: string | null; at: number }

/** Entrée mémorisée encore valable (30 jours) ; null si absente, illisible ou périmée. Seules les URL HTTPS sont gardées. */
export function readBadge(v: unknown, now: number): BadgeHit | null {
  if (!v || typeof v !== "object") return null;
  const o = v as { badge?: unknown; at?: unknown };
  if (typeof o.at !== "number" || !Number.isFinite(o.at)) return null;
  const age = now - o.at;
  if (age < 0 || age >= BADGE_TTL_MS) return null;
  return { badge: typeof o.badge === "string" && /^https:\/\/[^\s"]+$/i.test(o.badge) ? o.badge : null, at: o.at };
}

/** Écusson d'une réponse du Worker (`{team:{badge}}|{team:null}`) : URL HTTPS ou null (inconnue) ; undefined si illisible. */
export function badgeOfResponse(body: unknown): string | null | undefined {
  if (!body || typeof body !== "object" || !("team" in body)) return undefined;
  const team = (body as { team?: unknown }).team;
  if (!team || typeof team !== "object") return null;
  const b = (team as { badge?: unknown }).badge;
  return typeof b === "string" && /^https:\/\/[^\s"]+$/i.test(b) ? b : null;
}
