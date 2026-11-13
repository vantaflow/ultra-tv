// Classement et tri des résultats de recherche, clé de tri alphabétique des titres. Même logique que l'app Android
// (data/repo/SearchRank.kt) : pertinence (identique > commence par > mot entier > autre, puis le plus récent),
// A–Z sans article initial ni accents (« The Office » à O, « Les Nuits Courtes » à N), année, note.
import { normText } from "./text";

export type SearchSort = "relevance" | "title" | "year" | "rating";
export const SEARCH_SORTS: SearchSort[] = ["relevance", "title", "year", "rating"];

const ARTICLES = new Set(["le", "la", "les", "l", "un", "une", "des", "the", "a", "an", "el", "los", "las", "der", "die", "das", "il", "lo", "gli", "i"]);

export function titleKey(title: string): string {
  const words = normText(title).split(" ").filter(Boolean);
  const first = title.trimStart()[0];
  const drop = words.length > 1 && ARTICLES.has(words[0]!) && first !== "À" && first !== "à";
  return (drop ? words.slice(1) : words).join(" ");
}

/** Lettre du rail A–Z : initiale de titleKey en majuscule, « # » pour un chiffre ou un titre vide. */
export function initial(title: string): string {
  const c = titleKey(title)[0];
  if (!c) return "#";
  return /\p{L}/u.test(c) ? c.toUpperCase() : "#";
}

/** 4 = identique, 3 = commence par la saisie, 2 = contient le mot entier, 1 = autre correspondance, 0 = saisie vide. */
export function relevance(query: string, title: string): number {
  const q = normText(query);
  if (!q) return 0;
  const t = normText(title);
  const k = titleKey(title);
  if (t === q || k === q) return 4;
  if (t.startsWith(q) || k.startsWith(q)) return 3;
  if (` ${t} `.includes(` ${q} `)) return 2;
  return 1;
}

export const validRating = (r: number | null | undefined): number | null => (r != null && r > 0 && r <= 10 ? r : null);

interface Sortable { title: string; year: number | null; rating?: number | null }

/** Tri stable selon `sort` (copie : la liste d'origine n'est pas modifiée). */
export function orderResults<T extends Sortable>(items: T[], query: string, sort: SearchSort): T[] {
  const keyed = items.map((it, i) => ({ it, i, k: titleKey(it.title), r: sort === "relevance" ? relevance(query, it.title) : 0 }));
  const cmpKey = (a: { k: string }, b: { k: string }) => (a.k < b.k ? -1 : a.k > b.k ? 1 : 0);
  keyed.sort((a, b) => {
    switch (sort) {
      case "relevance": return b.r - a.r || (b.it.year ?? 0) - (a.it.year ?? 0) || a.i - b.i;
      case "title": return cmpKey(a, b) || (b.it.year ?? 0) - (a.it.year ?? 0) || a.i - b.i;
      case "year": return (b.it.year ?? -Infinity) - (a.it.year ?? -Infinity) || cmpKey(a, b) || a.i - b.i;
      case "rating": return (validRating(b.it.rating) ?? -1) - (validRating(a.it.rating) ?? -1) || cmpKey(a, b) || a.i - b.i;
    }
  });
  return keyed.map((x) => x.it);
}

/** Lettres présentes dans une liste triée A–Z et indice du premier titre de chacune. */
export function letterIndex<T extends { title: string }>(sorted: T[]): Map<string, number> {
  const out = new Map<string, number>();
  sorted.forEach((it, i) => { const c = initial(it.title); if (!out.has(c)) out.set(c, i); });
  return out;
}
