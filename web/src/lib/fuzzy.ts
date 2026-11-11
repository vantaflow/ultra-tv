// Recherche tolérante aux fautes de frappe (« brekin bad » → Breaking Bad), en repli quand la recherche exacte ne trouve
// aucun film ni série. Même règle que l'app Android (FuzzySearch.kt) : ressemblance mot à mot par distance d'édition.
import { searchTokens } from "./text";

/** Score minimal (0..1) pour retenir un titre. */
export const FUZZY_THRESHOLD = 0.72;

/** Débuts de mot (3 lettres) servant à lire les candidats dans l'index `words`. */
export function fuzzyPrefixes(q: string): string[] {
  return [...new Set(searchTokens(q).filter((w) => w.length >= 3).map((w) => w.slice(0, 3)))].slice(0, 6);
}

/** Damerau-Levenshtein (transposition d'une paire = 1). */
function distance(a: string, b: string): number {
  const n = a.length, m = b.length;
  if (!n) return m;
  if (!m) return n;
  const d: number[][] = Array.from({ length: n + 1 }, (_, i) => Array.from({ length: m + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)));
  for (let i = 1; i <= n; i++) for (let j = 1; j <= m; j++) {
    const cost = a[i - 1] === b[j - 1] ? 0 : 1;
    let v = Math.min(d[i - 1]![j]! + 1, d[i]![j - 1]! + 1, d[i - 1]![j - 1]! + cost);
    if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) v = Math.min(v, d[i - 2]![j - 2]! + 1);
    d[i]![j] = v;
  }
  return d[n]![m]!;
}

/** 1 − distance / longueur ; un mot saisi plus court compte comme préfixe (saisie en cours). */
export function similarity(a: string, b: string): number {
  if (a === b) return 1;
  const bb = b.length > a.length + 1 ? b.slice(0, a.length + 1) : b;
  return 1 - distance(a, bb) / Math.max(a.length, bb.length);
}

/** Ressemblance (0..1) saisie / titre : meilleur mot du titre pour chaque mot saisi, moyenne pondérée par la longueur. */
export function fuzzyScore(query: string, title: string): number {
  const q = searchTokens(query).filter((w) => w.length >= 2);
  const t = searchTokens(title);
  if (!q.length || !t.length) return 0;
  let sum = 0, weight = 0;
  for (const w of q) { sum += Math.max(...t.map((x) => similarity(w, x))) * w.length; weight += w.length; }
  return sum / weight;
}

/** Titres ressemblants, du plus proche au moins proche. */
export function fuzzyRank<T>(query: string, items: T[], title: (t: T) => string, limit: number): T[] {
  return items.map((it) => [it, fuzzyScore(query, title(it))] as const)
    .filter(([, s]) => s >= FUZZY_THRESHOLD)
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit)
    .map(([it]) => it);
}
