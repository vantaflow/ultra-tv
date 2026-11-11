// Ordre des chaînes du guide : numéro croissant (celui du fournisseur ou celui calculé par l'appli, tel qu'affiché dans le Direct).
// Les chaînes sans numéro vont à la fin, par nom. Même règle pour « Tout », les catégories et les Favoris.

interface Numbered { num?: number | null; display?: string | null; name?: string | null; ord?: number }

const label = (c: Numbered) => c.display || c.name || "";
const hasNum = (c: Numbered) => typeof c.num === "number" && Number.isFinite(c.num) && c.num > 0;

/** Comparateur : numéros croissants, puis (à numéro égal) ordre du catalogue ; sans numéro : à la fin, par nom. */
export function compareGuideChannels(a: Numbered, b: Numbered): number {
  const an = hasNum(a), bn = hasNum(b);
  if (an && bn) return a.num! - b.num! || (a.ord ?? 0) - (b.ord ?? 0) || label(a).localeCompare(label(b));
  if (an) return -1;
  if (bn) return 1;
  return label(a).localeCompare(label(b)) || (a.ord ?? 0) - (b.ord ?? 0);
}

/** Copie triée (le tableau d'entrée n'est pas modifié). */
export function sortGuideChannels<T extends Numbered>(chans: readonly T[]): T[] {
  return [...chans].sort(compareGuideChannels);
}
