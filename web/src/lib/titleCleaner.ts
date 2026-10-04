// Nettoie les intitulés des fournisseurs IPTV pour l'AFFICHAGE (le nom brut reste stocké ; la recherche
// l'interroge). Port fidèle du TitleCleaner de l'appli Android TV.

export interface Cleaned { title: string; year: number | null; quality: string | null }

const bracketPrefix = /^\s*(?:\[[^\]]{1,12}\]|\|[\p{L}\p{N}+ \-]{1,10}\||\([A-Za-z]{2,4}\))\s*/u;
const pipePrefix = /^\s*[\p{L}\p{N}+]{1,8}(?:[- ][\p{L}\p{N}+]{1,8}){0,2}\s*\|\s*/u;
const colonPrefix = /^[A-Z0-9][A-Z0-9+/ -]{0,11}:\s+/;
const dashPrefix = /^[A-Z0-9+]{1,5}(?:-[A-Z0-9+]{1,5}){0,3}\s+-\s+/;
const yearRe = /\((19\d{2}|20\d{2})\)/g;
const countrySuffix = /\s*\([A-Z]{2,3}\)\s*$/;
const QUALITY_TOKENS = ["8K", "4K", "UHD", "FHD", "HD", "SD", "HEVC", "H265", "H264", "LQ", "RAW", "50FPS", "60FPS", "FULLHD"];
const trailingQuality = new RegExp(`\\s+(?:${QUALITY_TOKENS.join("|")})\\s*$`, "i");
const trailingSource = /\s*\((?:SAT|D|TV|IPTV)\)\s*$/;

function isDecoration(c: string): boolean {
  const k = c.codePointAt(0)!;
  return (k >= 0x02b0 && k <= 0x02ff) || (k >= 0x2070 && k <= 0x209f) || (k >= 0x1d2c && k <= 0x1dbf) || "◉★☆•●⚽".includes(c);
}
const stripDeco = (s: string) => [...s].filter((c) => !isDecoration(c)).join("");

function trimChars(s: string, chars: string): string {
  let i = 0;
  let j = s.length;
  while (i < j && chars.includes(s[i]!)) i++;
  while (j > i && chars.includes(s[j - 1]!)) j--;
  return s.slice(i, j);
}

const absentTail = /[.\s_]+(?:None|null|undefined|N\/A)\s*$/i;
const dottedYear = /[.\s_]+(19\d{2}|20\d{2})\s*$/;
const SMALL = new Set(["de", "la", "le", "les", "du", "des", "et", "of", "the", "and", "a", "an", "in", "on", "el", "los", "las", "y", "un", "une", "au", "aux", "en"]);
const roman = /^[IVXLC]+$/;
const titleCase = (w: string) => { const l = w.toLowerCase(); return l.charAt(0).toUpperCase() + l.slice(1); };

/** Rend lisible un titre venu d'une source : « PREDICTION.None » → « Prediction ». Idempotent. */
export function tidyTitle(title: string): string {
  let s = title.trim().replace(absentTail, "").trim();
  if (!s.includes(" ") && (s.includes(".") || s.includes("_"))) s = s.replace(/[._]+/g, " ").trim();
  s = s.replace(dottedYear, "").trim();
  const letters = [...s].filter((c) => /\p{L}/u.test(c));
  if (letters.length >= 4 && !letters.some((c) => /\p{Ll}/u.test(c))) {
    s = s.split(" ").filter(Boolean).map((w, i) => {
      if (/\d/.test(w) || roman.test(w)) return w;
      if (i > 0 && SMALL.has(w.toLowerCase())) return w.toLowerCase();
      return titleCase(w);
    }).join(" ");
  }
  return s || title.trim();
}

/** Champ facultatif : null, vide, « None », « null », « 0 » → absent (champ masqué). */
export function presentable(v: string | null | undefined): string | null {
  const t = (v ?? "").trim();
  if (!t) return null;
  return ["none", "null", "undefined", "n/a", "0", "0000-00-00", "-"].includes(t.toLowerCase()) ? null : t;
}

export function cleanTitle(raw: string, live = false): Cleaned {
  let s = raw.trim();
  if (!s) return { title: raw, year: null, quality: null };
  if (live && s.includes(" | ")) {
    const last = s.slice(s.lastIndexOf(" | ") + 3).trim();
    if (last.length >= 3) s = last;
  }
  s = stripDeco(s).trim();
  s = trimChars(s, "# -_").trim();
  for (let i = 0; i < 3; i++) {
    const before = s;
    s = s.replace(bracketPrefix, "");
    s = s.replace(pipePrefix, "");
    if (live || dashPrefix.test(s)) s = s.replace(colonPrefix, "");
    s = s.replace(dashPrefix, "").trim();
    if (s === before) break;
  }
  let year: number | null = null;
  const all = [...s.matchAll(yearRe)];
  const m = all[all.length - 1];
  if (m && m.index! > 0) {
    year = parseInt(m[1]!, 10);
    s = s.slice(0, m.index).trim();
  }
  s = s.replace(countrySuffix, "").replace(trailingSource, "").trim();
  let quality: string | null = null;
  if (live || year == null) {
    for (;;) {
      const q = trailingQuality.exec(s);
      if (!q) break;
      const tok = q[0].trim().toUpperCase();
      if (quality == null && ["8K", "4K", "UHD", "FHD", "HD", "SD"].includes(tok)) quality = tok;
      s = s.slice(0, q.index).trim();
    }
  }
  s = trimChars(s, "# -_|:").replace(/\s{2,}/g, " ");
  if (!live) {
    const d = dottedYear.exec(s);
    if (year == null && d) year = parseInt(d[1]!, 10);
    s = tidyTitle(s);
  }
  if (!s) s = raw.trim();
  return { title: s, year, quality };
}

/** Nom de catégorie : retire le séparateur final « / » et les décorations. */
export function prettyCategoryName(raw: string): string {
  // Nom du FOURNISSEUR tel quel : tout nettoyage finissait par rendre des catégories indiscernables (« FR| SPORT » / « AR| SPORT »).
  return raw.trim().replace(/\s{2,}/g, " ") || raw;
}

/** Titre d'épisode : retire « Série - S01E01 - » (les fournisseurs répètent le nom de la série et le code). */
export function cleanEpisodeTitle(raw: string): string {
  const m = /\bS\d{1,3}\s?E\d{1,3}\s*[-–:.]?\s*/i.exec(raw);
  const t = m ? raw.slice(m.index + m[0].length) : raw;
  const c = cleanTitle(t.trim() || raw).title;
  return c || raw;
}
