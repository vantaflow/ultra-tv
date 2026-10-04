// Langue d'une catégorie IPTV déduite de son nom. Un seul code canonique par langue : « FR », « fr », « FRA »,
// « French », « VF », « VOSTFR », « 🇫🇷 », « |FR| », « FR - » donnent tous « FR » (sinon les filtres sont dupliqués).

import { isIsoCountry, parseCategoryName } from "./channelName";

export const OTHER_LANG = "OTHER";

const GROUPS: Record<string, string> = {
  FR: "FR FRA FRE FRENCH FRANCAIS FRANCE VF VFF VFQ VOSTFR VOST TRUEFRENCH",
  UK: "UK GB GBR ENG EN ENGLISH ANGLAIS",
  ES: "ES ESP SPA SPANISH ESPANOL ESPAGNOL",
  DE: "DE GER DEU GERMAN DEUTSCH ALLEMAND",
  IT: "IT ITA ITALIAN ITALIANO",
  PT: "PT POR PRT PORTUGUESE PORTUGUES",
  NL: "NL NLD DUTCH",
  TR: "TR TUR TURKISH TURC",
  AR: "AR ARA ARB ARABIC ARABE",
  MULTI: "MULTI MULTILANG MULTILANGUE MULTILINGUAL",
};
const ALIAS = new Map<string, string>();
for (const [code, list] of Object.entries(GROUPS)) for (const a of list.split(" ")) ALIAS.set(a, code);

/**
 * Pays → langue (un filtre par LANGUE, pas par pays : US, AU, IE, NZ = anglais ; BR = portugais ; MA = arabe…).
 * « UK » reste le code canonique de l'anglais (compatibilité avec les réglages déjà enregistrés).
 * Les pays multilingues ou ambigus (CA, BE, CH, IN…) restent des régions, affichées par leur nom.
 */
const COUNTRY_LANG: Record<string, string> = {
  GB: "UK", US: "UK", AU: "UK", IE: "UK", NZ: "UK", JM: "UK",
  BR: "PT", AO: "PT", MZ: "PT",
  AT: "DE", LI: "DE",
  MX: "ES", CO: "ES", CL: "ES", PE: "ES", VE: "ES", EC: "ES", UY: "ES", PY: "ES", BO: "ES", DO: "ES", CU: "ES", GT: "ES", HN: "ES", NI: "ES", PA: "ES", LA: "ES",
  MA: "AR", TN: "AR", DZ: "AR", EG: "AR", SA: "AR", AE: "AR", QA: "AR", KW: "AR", IQ: "AR", SY: "AR", LB: "AR", JO: "AR", LY: "AR", OM: "AR", YE: "AR", BH: "AR", PS: "AR", SD: "AR",
  MC: "FR", LU: "FR", SN: "FR", CI: "FR", CM: "FR", HT: "FR",
  SE: "SV", DK: "DA", NO: "NB", GR: "EL", CY: "EL", IL: "HE", IR: "FA", CZ: "CS", AL: "SQ", XK: "SQ",
  JP: "JA", KR: "KO", CN: "ZH", HK: "ZH", TW: "ZH", SI: "SL", PK: "UR", VN: "VI", MY: "MS", AM: "HY", GE: "KA", KZ: "KK",
  RS: "SR", BA: "BS", UA: "UK_UA",
};
/** Codes de LANGUE produits (les autres codes à deux lettres sont des régions). */
const LANG_CODES = new Set<string>([
  ...Object.keys(GROUPS), ...Object.values(COUNTRY_LANG),
  "PL", "RU", "RO", "HU", "BG", "HR", "LT", "LV", "ET", "FI", "TH", "ID", "UZ", "AZ", "MK", "MT", "SK", "IS", "HI", "BN", "TA",
]);
/** Code ISO 639-1 d'un code canonique, pour les noms localisés (Intl.DisplayNames). */
const ISO639: Record<string, string> = { UK: "en", UK_UA: "uk", NB: "nb" };

const fold = (s: string) => s.normalize("NFD").replace(/\p{M}/gu, "").toUpperCase();

/** Code canonique d'un jeton (alias, sinon code pays ISO tel quel), ou null. */
export function canonicalLang(token: string): string | null {
  const k = fold(token.trim());
  const a = ALIAS.get(k);
  if (a) return a;
  if (LANG_CODES.has(k)) return k;
  if (COUNTRY_LANG[k]) return COUNTRY_LANG[k]!;
  if (k === "ASIA" || k === "LATINO" || k === "LATAM" || k === "AFRICA" || k === "AFRIQUE") return k === "LATINO" || k === "LATAM" ? "ES" : k;
  return k.length === 2 && isIsoCountry(k) ? k : null;
}

/** Nom affiché d'un code (langue ou région), dans la langue de l'interface ; repli : le code. */
export function langLabel(code: string, uiLang: string): string {
  if (code === OTHER_LANG) return code;
  try {
    if (LANG_CODES.has(code)) {
      const n = new Intl.DisplayNames([uiLang], { type: "language" }).of(ISO639[code] ?? code.toLowerCase());
      if (n && n.toLowerCase() !== (ISO639[code] ?? code).toLowerCase()) return n.charAt(0).toLocaleUpperCase(uiLang) + n.slice(1);
    }
    if (code === "ASIA") return uiLang.startsWith("fr") ? "Asie" : uiLang.startsWith("es") ? "Asia" : uiLang.startsWith("ar") ? "آسيا" : "Asia";
    if (code === "AFRICA" || code === "AFRIQUE") return uiLang.startsWith("fr") ? "Afrique" : uiLang.startsWith("ar") ? "أفريقيا" : "Africa";
    if (code.length === 2) {
      const r = new Intl.DisplayNames([uiLang], { type: "region" }).of(code);
      if (r && r !== code) return r;
    }
  } catch { /* Intl indisponible */ }
  return code;
}

/** Langue du protocole (minuscule, ISO 639-1 quand c'est une langue) ↔ code canonique. */
export function protocolLang(code: string): string {
  return (ISO639[code] ?? code).toLowerCase();
}

const RI_BASE = 0x1f1e6;
function flagCode(s: string): string | null {
  const m = /([\u{1F1E6}-\u{1F1FF}])([\u{1F1E6}-\u{1F1FF}])/u.exec(s);
  if (!m) return null;
  const c = String.fromCharCode(65 + m[1]!.codePointAt(0)! - RI_BASE, 65 + m[2]!.codePointAt(0)! - RI_BASE);
  return canonicalLang(c);
}

// Préfixe : « FR| », « |FR| », « FR - », « [FR] », « (FR) », « FR: », « FR_ ».
const PREFIX = /^[\s|\[(\-_#*•]*(\p{L}{2,12})\s*(?:[|\])):]|\s-\s|-|_)/u;
const KEYWORD = /(?<![\p{L}\p{N}])(vostfr|vost|vff|vfq|vf|truefrench|french|francais|english|arabic|spanish|german|italian|portuguese)(?![\p{L}\p{N}])/iu;

export function categoryLang(raw: string): string {
  const n = (raw ?? "").normalize("NFKC").trim();
  const fl = flagCode(n);
  if (fl) return fl;
  const m = PREFIX.exec(n);
  if (m) { const c = canonicalLang(m[1]!); if (c) return c; }
  const b = parseCategoryName(n).badge;
  if (b) return canonicalLang(b) ?? b;
  const k = KEYWORD.exec(fold(n));
  if (k) { const c = canonicalLang(k[1]!); if (c) return c; }
  return OTHER_LANG;
}

export interface LangStat { code: string; total: number; on: number }

/** Union dédoublonnée des langues d'un ensemble de catégories, avec compteurs ; « OTHER » en dernier. */
export function languageStats(rows: { name: string; enabled: 0 | 1 }[]): LangStat[] {
  const m = new Map<string, LangStat>();
  for (const r of rows) {
    const code = categoryLang(r.name);
    const e = m.get(code) ?? { code, total: 0, on: 0 };
    e.total++; if (r.enabled) e.on++;
    m.set(code, e);
  }
  return [...m.values()].sort((a, b) => (a.code === OTHER_LANG ? 1 : b.code === OTHER_LANG ? -1 : b.total - a.total || a.code.localeCompare(b.code)));
}

/** Vue filtrée : langue (null = toutes), recherche déjà normalisée par l'appelant via `match`. */
export function filterCategories<T extends { name: string; kind: string }>(rows: T[], f: { lang: string | null; kind: string | null; match?: (r: T) => boolean }): T[] {
  return rows.filter((r) => (f.kind == null || r.kind === f.kind) && (f.lang == null || categoryLang(r.name) === f.lang) && (!f.match || f.match(r)));
}
