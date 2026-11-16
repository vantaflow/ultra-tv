// Rapprochement des programmes du guide qui diffusent le MÊME événement sportif (« PSG - OM », « Paris SG vs Marseille »,
// « Ligue 1 : PSG / OM »…) sur plusieurs chaînes. Logique pure, port fidèle d'Android (SportMatching.kt) : mêmes alias, mêmes
// tolérances, mêmes tests. Prudent : en cas de doute (pas d'équipes identifiables, compétitions différentes, horaires éloignés)
// les programmes restent séparés.
import { fold, tokens, type SportEntry } from "./sport";
import { qualityLabel } from "./channelName";
import type { ChannelRow } from "@/db/types";

/** Un événement décrit par un titre : compétition reconnue (clé stable) et clé d'identité. */
export interface ParsedEvent { competition: string | null; key: string; isMatch: boolean }

/** Tolérance d'horaire d'une rencontre « A - B » (les fournisseurs décalent de quelques minutes). */
export const MATCH_TOLERANCE_MS = 15 * 60_000;
/** Tolérance d'un événement sans équipes (titre identique exigé) : plus stricte, la confiance est plus basse. */
export const OTHER_TOLERANCE_MS = 5 * 60_000;

const BRACKETS = /\([^)]*\)|\[[^\]]*\]/g;
const LIVE_PREFIX = /^\s*(?:live|direct|en direct|en vivo|en directo|na zywo|na żywo|مباشر)\s*[:|•\-–]\s*/i;
const TRAILING = /\s*(?:[-–|]\s*)?(?:\d{1,2}\s*[:h]\s*\d{2}|hd|fhd|4k|live|en direct|en vivo|direct)\s*$/i;
const SEP = /\s+(?:vs\.?|v|versus|contre|x|ضد|[-–—/×])\s+/i;
const QUALITY_WORDS = new Set(["hd", "fhd", "4k", "live", "direct", "vivo", "directo"]);

/** Mots sans valeur d'identité dans un nom d'équipe (statuts de clubs, articles). */
const NOISE = new Set(["fc", "cf", "ac", "sc", "cd", "ca", "afc", "sv", "fk", "bk", "rc", "ud", "cs", "us", "as", "club", "de", "du", "the", "al", "el"]);
const SYNONYM: Record<string, string> = { utd: "united", atl: "atletico", st: "saint" };

// Alias de clubs : variantes courantes (français, anglais, espagnol, arabe translittéré) -> forme canonique.
const ALIAS_SRC: Record<string, string[]> = {
  psg: ["psg", "paris", "paris sg", "paris saint germain", "paris s g", "باريس سان جيرمان"],
  marseille: ["om", "o marseille", "olympique marseille", "olympique de marseille", "marseille"],
  lyon: ["ol", "lyon", "lyonnais", "olympique lyonnais", "olympique lyon"],
  "man united": ["man united", "man utd", "manchester united", "manchester utd", "mufc", "مانشستر يونايتد"],
  "man city": ["man city", "manchester city", "mcfc", "مانشستر سيتي"],
  "real madrid": ["real madrid", "real", "r madrid", "ريال مدريد"],
  barcelona: ["barcelona", "barcelone", "barca", "fc barcelona", "برشلونة"],
  "atletico madrid": ["atletico madrid", "atletico", "atl madrid", "atm", "اتلتيكو مدريد"],
  bayern: ["bayern", "bayern munich", "bayern munchen", "fc bayern", "بايرن ميونخ"],
  inter: ["inter", "inter milan", "internazionale", "inter milano", "انتر ميلان"],
  juventus: ["juve", "juventus", "يوفنتوس"],
  tottenham: ["spurs", "tottenham", "tottenham hotspur"],
  wolves: ["wolves", "wolverhampton", "wolverhampton wanderers"],
  newcastle: ["newcastle", "newcastle united", "nufc"],
  dortmund: ["bvb", "dortmund", "borussia dortmund"],
  gladbach: ["gladbach", "monchengladbach", "borussia monchengladbach"],
  leverkusen: ["leverkusen", "bayer leverkusen", "bayer 04 leverkusen"],
  sporting: ["sporting", "sporting cp", "sporting lisbon", "sporting lisbonne"],
  benfica: ["benfica", "sl benfica"],
  ahly: ["ahly", "al ahly", "الأهلي"],
  zamalek: ["zamalek", "الزمالك"],
  wydad: ["wydad", "wydad casablanca", "wydad athletic", "الوداد"],
  raja: ["raja", "raja casablanca", "raja club athletic", "الرجاء"],
  hilal: ["hilal", "al hilal", "الهلال"],
  nassr: ["nassr", "nasr", "al nassr", "النصر"],
  esperance: ["esperance", "esperance tunis", "esperance sportive tunis", "الترجي"],
};

// Compétitions reconnues : clé stable <- phrases repliées (la première liste qui correspond gagne ; du plus précis au plus large).
const COMP_SRC: [string, string[]][] = [
  ["caf-cl", ["caf champions league", "ligue des champions caf", "دوري ابطال افريقيا"]],
  ["ucl", ["champions league", "ligue des champions", "ucl", "ldc", "liga de campeones", "دوري ابطال"]],
  ["uel", ["europa league", "ligue europa", "uel"]],
  ["uecl", ["conference league"]],
  ["ligue1", ["ligue 1"]],
  ["ligue2", ["ligue 2"]],
  ["epl", ["premier league", "epl"]],
  ["ligaportugal", ["liga portugal"]],
  ["laliga", ["la liga", "laliga", "primera division", "liga"]],
  ["seriea", ["serie a"]],
  ["bundesliga", ["bundesliga"]],
  ["botola", ["botola"]],
  ["worldcup", ["coupe du monde", "world cup", "copa del mundo", "كاس العالم"]],
  ["afcon", ["afcon", "africa cup", "coupe d afrique", "can"]],
  ["facup", ["fa cup"]],
  ["coupedefrance", ["coupe de france"]],
  ["copadelrey", ["copa del rey"]],
  ["carabao", ["carabao cup", "efl cup"]],
  ["nba", ["nba"]], ["nfl", ["nfl"]], ["nhl", ["nhl"]], ["ufc", ["ufc"]],
  ["f1", ["formula 1", "formule 1", "formula one", "f1"]],
  ["motogp", ["motogp", "moto gp"]],
];

function teamNorm(raw: string): string {
  const w = tokens(fold(raw)).map((t) => SYNONYM[t] ?? t);
  if (w.length >= 2 && w[0] === "man") w[0] = "manchester";
  const kept = w.filter((t) => t && !NOISE.has(t));
  return (kept.length ? kept : w).join(" ");
}

const ALIASES = new Map<string, string>();
for (const [canon, variants] of Object.entries(ALIAS_SRC)) for (const v of variants) ALIASES.set(teamNorm(v), canon);

/** Forme canonique d'un nom d'équipe (alias courants, statuts de clubs et accents ignorés) ; vide si rien d'exploitable. */
export function canonTeam(raw: string): string {
  const n = teamNorm(raw);
  return ALIASES.get(n) ?? n;
}

const COMPETITIONS = COMP_SRC.map(([k, ps]) => [k, ps.map((p) => ` ${fold(p)} `)] as [string, string[]]);

/** Clé de la compétition annoncée par `text` (« Ligue 1 Uber Eats » -> « ligue1 »), ou null. */
export function competitionOf(text: string): string | null {
  const padded = ` ${tokens(fold(text)).join(" ")} `;
  return COMPETITIONS.find(([, ps]) => ps.some((p) => padded.includes(p)))?.[0] ?? null;
}

/** Analyse un titre de programme : compétition, équipes (clé indépendante de l'ordre) ou titre replié sans bruit. */
export function parseEvent(title: string): ParsedEvent {
  let s = title.replace(BRACKETS, " ").trim().replace(LIVE_PREFIX, "");
  for (let prev = ""; prev !== s;) { prev = s; s = s.replace(TRAILING, "").trim(); }
  let comp: string | null = null;
  for (let n = 0; n < 2; n++) {
    const i = s.search(/[:|]/);
    if (i >= 2 && i <= 40) {
      const head = s.slice(0, i);
      const tail = s.slice(i + 1).trim();
      if (!SEP.test(head) && SEP.test(tail)) { comp = comp ?? competitionOf(head); s = tail; }
    }
  }
  let parts = s.split(SEP).map((p) => p.trim());
  // split avec un motif sans groupe capturant : pas de séparateurs dans le résultat (SEP n'a que des groupes non capturants).
  if (parts.length === 3 && competitionOf(parts[0]!) != null) { comp = comp ?? competitionOf(parts[0]!); parts = parts.slice(1); }
  if (parts.length === 2) {
    const a = canonTeam(parts[0]!);
    const b = canonTeam(parts[1]!);
    if (a && b && a !== b) return { competition: comp, key: [a, b].sort().join("|"), isMatch: true };
  }
  const plain = tokens(fold(s)).filter((t) => !QUALITY_WORDS.has(t)).join(" ");
  return { competition: comp, key: `t:${plain}`, isMatch: false };
}

/** Deux programmes (titre + début) décrivent-ils le même événement ? */
export function sameEvent(a: ParsedEvent, aStart: number, b: ParsedEvent, bStart: number): boolean {
  if (a.key !== b.key || a.key === "t:") return false;
  if (a.competition && b.competition && a.competition !== b.competition) return false;
  return Math.abs(aStart - bStart) <= (a.isMatch ? MATCH_TOLERANCE_MS : OTHER_TOLERANCE_MS);
}

const chanId = (c: ChannelRow): number => c.id ?? c.streamId;

/** Regroupe les entrées par événement (chaque groupe : début croissant) ; les groupes d'une seule entrée sont gardés. */
export function groupEvents(entries: SportEntry[]): SportEntry[][] {
  const byKey = new Map<string, { p: ParsedEvent; e: SportEntry }[]>();
  for (const e of entries) {
    const p = parseEvent(e.prog.title);
    const l = byKey.get(p.key);
    if (l) l.push({ p, e }); else byKey.set(p.key, [{ p, e }]);
  }
  const out: SportEntry[][] = [];
  for (const list of byKey.values()) {
    const clusters: { p: ParsedEvent; e: SportEntry }[][] = [];
    for (const item of [...list].sort((x, y) => x.e.prog.start - y.e.prog.start)) {
      const home = clusters.find((c) => sameEvent(c[0]!.p, c[0]!.e.prog.start, item.p, item.e.prog.start));
      if (home) home.push(item); else clusters.push([item]);
    }
    for (const c of clusters) out.push(c.map((x) => x.e));
  }
  return out;
}

const byText = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

/** Ordre d'affichage des chaînes d'un événement : meilleure qualité, chaînes de sport, numéro (connu d'abord), nom. */
const channelOrder = (a: SportEntry, b: SportEntry): number =>
  b.channel.q - a.channel.q || Number(b.sportChannel) - Number(a.sportChannel) || Number(a.channel.num === 0) - Number(b.channel.num === 0)
  || a.channel.num - b.channel.num || byText(a.channel.display, b.channel.display) || chanId(a.channel) - chanId(b.channel);

/** Les autres chaînes du groupe `g` pour `self` : une entrée par chaîne, sans la sienne, triées. */
function others(g: SportEntry[], self: SportEntry): SportEntry[] {
  const seen = new Set<number>();
  return g.filter((x) => chanId(x.channel) !== chanId(self.channel)).sort(channelOrder)
    .filter((x) => { const k = chanId(x.channel); if (seen.has(k)) return false; seen.add(k); return true; });
}

/** Clé d'une entrée dans `alsoOnMap` : chaîne + début du programme. */
export const entryKey = (e: SportEntry): string => `${chanId(e.channel)}|${e.prog.start}`;

/** Pour chaque entrée dont l'événement passe aussi ailleurs : les autres chaînes (« Aussi sur… »). Absente = aucune autre. */
export function alsoOnMap(entries: SportEntry[]): Map<string, SportEntry[]> {
  const m = new Map<string, SportEntry[]>();
  for (const g of groupEvents(entries)) {
    if (g.length < 2) continue;
    for (const e of g) { const o = others(g, e); if (o.length) m.set(entryKey(e), o); }
  }
  return m;
}

/** Les autres chaînes qui diffusent le même événement que `entry` parmi `entries`. */
export function alsoOn(entry: SportEntry, entries: SportEntry[]): SportEntry[] {
  const p = parseEvent(entry.prog.title);
  return others(entries.filter((x) => x === entry || sameEvent(p, entry.prog.start, parseEvent(x.prog.title), x.prog.start)), entry);
}

/** Une seule entrée par événement (la première de `entries`), ordre conservé : remplace le dédoublonnage par titre exact. */
export function collapseEvents(entries: SportEntry[]): SportEntry[] {
  const kept = new Map<string, { p: ParsedEvent; start: number }[]>();
  const out: SportEntry[] = [];
  for (const e of entries) {
    const p = parseEvent(e.prog.title);
    let l = kept.get(p.key);
    if (!l) { l = []; kept.set(p.key, l); }
    if (!l.some((k) => sameEvent(k.p, k.start, p, e.prog.start))) { l.push({ p, start: e.prog.start }); out.push(e); }
  }
  return out;
}

/** Étiquette « qualité · pays » d'une chaîne d'après ses badges (SD/HD/FHD/4K ; préfixe de pays) ; vide si inconnu. */
export function badgesOf(c: ChannelRow): string {
  return [qualityLabel(c.q), (c.country ?? "").toUpperCase()].filter(Boolean).join(" · ");
}
