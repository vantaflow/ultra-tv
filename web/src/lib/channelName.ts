// Analyse d'un nom de chaîne IPTV (port fidèle du ChannelNameParser de l'appli Android TV).
//  - séparateur : « ##### 4K ᵁᴴᴰ ³⁸⁴⁰ᴾ ##### », « ===== » → en-tête de section, jamais une chaîne ;
//  - displayName : sans préfixe de pays/étiquette ni marqueur de qualité (NFKC : exposants → lettres) ;
//  - country : code ISO (liste blanche) — « TV », « GOLD », « VIP », « 4K: » ne sont pas des pays ;
//  - quality : SD / HD / FHD / 4K ; « 8K » n'est PAS une définition (étiquette fournisseur) sauf 4320P / 7680 ;
//  - flags : HEVC, HDR, 50/60 FPS, RAW, BACKUP, LQ, VIP.

export const Q_NONE = 0;
export const Q_SD = 1;
export const Q_HD = 2;
export const Q_FHD = 3;
export const Q_4K = 4;
export const Q_8K = 5;

export const F_HEVC = 1;
export const F_HDR = 2;
export const F_50FPS = 4;
export const F_60FPS = 8;
export const F_RAW = 16;
export const F_BACKUP = 32;
export const F_LQ = 64;
export const F_VIP = 128;

export interface ParsedChannel {
  isSeparator: boolean;
  displayName: string;
  country: string | null;
  quality: number;
  flags: number;
}

const ISO = new Set(
  ("AD AE AF AG AI AL AM AO AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BM BN BO BR BS BT BW BY BZ CA CD CF CG CH CI CK CL CM CN CO CR CU CV CW CY CZ " +
    "DE DJ DK DM DO DZ EC EE EG ER ES ET FI FJ FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GT GU GW GY HK HN HR HT HU ID IE IL IM IN IQ IR IS IT JE JM JO JP " +
    "KE KG KH KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MG MK ML MM MN MO MQ MR MT MU MV MW MX MY MZ NA NC NE NG NI NL NO NP NZ OM PA PE PF PG PH PK PL PR PS PT PW PY QA " +
    "RE RO RS RU RW SA SB SC SD SE SG SI SK SL SM SN SO SR SS ST SV SY SZ TD TG TH TJ TL TM TN TO TR TT TW TZ UA UG US UY UZ VA VC VE VG VI VN VU WS YE ZA ZM ZW UK").split(" "),
);
export const isIsoCountry = (c: string) => ISO.has(c);

const DECOR = new Set([..."#=-_*~<>|•●★☆▬▪■□◆◇─━═·.:;+  "]);

function trimBy(s: string, pred: (c: string) => boolean): string {
  const a = [...s];
  let i = 0;
  let j = a.length;
  while (i < j && pred(a[i]!)) i++;
  while (j > i && pred(a[j - 1]!)) j--;
  return a.slice(i, j).join("");
}
const trimDecor = (s: string) => trimBy(s, (c) => DECOR.has(c));

const leadingDecor = /^[#=\-_*~<>|•●★☆▬▪■□◆◇─━═]{3,}/u;
const prefixSep = /^\s*([\p{L}\p{N}+ \-]{1,14}?)\s*(?:[:|]|\s-\s)\s*/u;
const bracketPrefix = /^\s*(?:\[([^\]]{1,12})\]|\(([A-Za-z]{2,4})\))\s*/u;
const TOKEN = /(?<![\p{L}\p{N}])(8K|4K|UHD|FHD|FULLHD|HD|SD|LQ|HEVC|H265|H\.265|HDR|HDR10|RAW|BACKUP|VIP|2160P|3840P|4320P|7680P|1080P|1080I|720P|576P|480P|50FPS|60FPS|25FPS|30FPS)(?![\p{L}\p{N}])/giu;

const norm = (s: string) => s.normalize("NFKC").trim();
const isLetterOrDigit = (c: string) => /[\p{L}\p{N}]/u.test(c);
const hasLower = (s: string) => /\p{Ll}/u.test(s);

function cleanMarkers(text: string): { text: string; q: number; flags: number } {
  let q = Q_NONE;
  let flags = 0;
  const out = text.replace(TOKEN, (m) => {
    switch (m.toUpperCase()) {
      case "4K": case "UHD": case "2160P": case "3840P": q = Math.max(q, Q_4K); break;
      case "4320P": case "7680P": q = Math.max(q, Q_8K); break;
      case "8K": break; // étiquette fournisseur, pas une définition
      case "FHD": case "FULLHD": case "1080P": case "1080I": q = Math.max(q, Q_FHD); break;
      case "HD": case "720P": q = Math.max(q, Q_HD); break;
      case "SD": case "576P": case "480P": q = Math.max(q, Q_SD); break;
      case "LQ": flags |= F_LQ; if (q === Q_NONE) q = Q_SD; break;
      case "HEVC": case "H265": case "H.265": flags |= F_HEVC; break;
      case "HDR": case "HDR10": flags |= F_HDR; break;
      case "50FPS": flags |= F_50FPS; break;
      case "60FPS": flags |= F_60FPS; break;
      case "RAW": flags |= F_RAW; break;
      case "BACKUP": flags |= F_BACKUP; break;
      case "VIP": flags |= F_VIP; break;
    }
    return " ";
  });
  return { text: out.replace(/\s{2,}/g, " ").replace(/\(\s*\)/g, "").trim(), q, flags };
}

export function parseChannelName(raw: string): ParsedChannel {
  const n = norm(raw);
  const alnum = [...n].filter(isLetterOrDigit).length;
  if (n.length > 0 && (alnum === 0 || leadingDecor.test(n))) {
    const stripped = trimDecor(n);
    const label = trimDecor(cleanMarkers(stripped).text) || stripped;
    return { isSeparator: true, displayName: label.toUpperCase(), country: null, quality: Q_NONE, flags: 0 };
  }
  let s = n;
  let country: string | null = null;
  for (let pass = 0; pass < 3; pass++) {
    const b = bracketPrefix.exec(s);
    if (b) {
      const code = (b[1] || b[2] || "").toUpperCase();
      if (country == null && ISO.has(code)) country = code;
      s = s.slice(b[0].length);
      continue;
    }
    const m = prefixSep.exec(s);
    if (!m) continue;
    const whole = m[1]!;
    const label = whole.toUpperCase().replace(/[ +-].*/, "");
    // Un préfixe n'est retiré que s'il ressemble à une étiquette (majuscules/chiffres), pas à un vrai titre.
    if (hasLower(whole) && whole.length > 3) continue;
    if (country == null) {
      const first = whole.toUpperCase().split(/[ +-]/).find((t) => ISO.has(t));
      if (first) country = first;
    }
    if (label.length > 0 || whole.length > 0) s = s.slice(m[0].length);
  }
  const cm = cleanMarkers(s);
  let display = trimDecor(cm.text).replace(/\s{2,}/g, " ");
  if (!display) display = n;
  return { isSeparator: false, displayName: display, country, quality: cm.q, flags: cm.flags };
}

export interface CategoryLabel { label: string; badge: string | null; quality: number }
const REGIONS: Record<string, string> = { AFRI: "AFR", ASIA: "ASIA", EURO: "EU", LATAM: "LATAM", MENA: "MENA" };

/** Nom de catégorie affichable : exposants normalisés, préfixe pays/région → badge, qualité retirée. */
export function parseCategoryName(raw: string): CategoryLabel {
  const n = norm(raw);
  const p = parseChannelName(n);
  let label = trimDecor(p.displayName);
  let badge = p.country;
  if (badge == null) {
    const first = label.split(" ")[0]!.toUpperCase();
    const reg = REGIONS[first];
    if (reg) {
      const rest = label.slice(first.length).trim();
      if ([...rest].filter((c) => /\p{L}/u.test(c)).length >= 2) { badge = reg; label = rest; }
    }
  }
  label = label.replace(/\s*[²³¹]\s*$/, "").replace(/\s{2,}/g, " ").trim();
  return { label: label || n, badge, quality: p.quality };
}

const QUALITY_LABELS = ["", "SD", "HD", "FHD", "4K", "8K"];
export const qualityLabel = (q: number) => QUALITY_LABELS[q] ?? "";
