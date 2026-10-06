/** Clé de recherche : minuscules, sans accents ni ponctuation, espaces réduits. */
export function normText(s: string): string {
  return s
    .normalize("NFKD")
    .replace(/\p{M}+/gu, "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim();
}

export function toNum(v: unknown, fallback = 0): number {
  const n = typeof v === "number" ? v : parseFloat(String(v ?? ""));
  return Number.isFinite(n) ? n : fallback;
}

/** Note sur 10 à partir de `rating` (déjà sur 10) ou `rating_5based`. */
export function rating10(rating: unknown, rating5: unknown): number {
  const r = toNum(rating, NaN);
  if (Number.isFinite(r) && r > 0) return Math.min(10, r);
  const r5 = toNum(rating5, 0);
  return Math.min(10, r5 * 2);
}

export function firstString(v: string[] | string | undefined | null): string | null {
  if (Array.isArray(v)) return v.find((x) => !!x) ?? null;
  return v || null;
}

/** Durée localisée (« 1 h 05 », « 12 min », « ١٢ د » en arabe) : les unités viennent d'Intl, pas du code. */
export function fmtDuration(totalSec: number, lang: string = (typeof document !== "undefined" && document.documentElement.lang) || "fr"): string {
  if (!Number.isFinite(totalSec) || totalSec <= 0) return "";
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const unit = (n: number, u: "hour" | "minute", digits = 1) => new Intl.NumberFormat(lang, { style: "unit", unit: u, unitDisplay: "short", minimumIntegerDigits: digits }).format(n);
  return h > 0 ? `${unit(h, "hour")} ${unit(m, "minute", 2)}` : unit(m, "minute");
}

export function fmtClock(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) sec = 0;
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = Math.floor(sec % 60);
  const mm = String(m).padStart(h > 0 ? 2 : 1, "0");
  return h > 0 ? `${h}:${mm}:${String(s).padStart(2, "0")}` : `${mm}:${String(s).padStart(2, "0")}`;
}

/** Mots d'une saisie, après normalisation (accents, casse, ponctuation). */
export function searchTokens(q: string): string[] {
  return normText(q).split(" ").filter(Boolean);
}

/**
 * Clés de l'index multiEntry `words` : « génération|mot » pour chaque mot distinct de `norm`.
 * Dexie ne permet pas le multiEntry dans un index composé : la génération est donc portée par la clé elle-même,
 * ce qui borne la recherche à la source active sans balayer les autres catalogues. Plafonné pour garder l'index maigre.
 */
export function wordKeys(sourceId: number, norm: string, max = 12): string[] {
  const out = new Set<string>();
  for (const w of norm.split(" ")) {
    if (!w) continue;
    out.add(`${sourceId}|${w}`);
    if (out.size >= max) break;
  }
  return [...out];
}
