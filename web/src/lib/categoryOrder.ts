// Ordre des catégories : logique PURE (sans base ni interface).

/**
 * Fusionne l'ordre local et l'ordre reçu [received] (identifiants de catégorie du fournisseur).
 * Les catégories connues suivent l'ordre reçu ; les inconnues de l'envoyeur gardent leur place relative, à la suite.
 * [local] est déjà trié comme affiché. Un identifiant reçu mais absent ici est ignoré.
 */
export function mergeCategoryOrder(local: string[], received: string[] | undefined | null): string[] {
  if (!received || received.length === 0) return local;
  const have = new Set(local);
  const seen = new Set<string>();
  const placed: string[] = [];
  for (const id of received) if (have.has(id) && !seen.has(id)) { seen.add(id); placed.push(id); }
  return [...placed, ...local.filter((id) => !seen.has(id))];
}

/** Déplace l'élément à l'indice [from] de [delta] rangs (borné) ; liste inchangée si rien ne bouge. */
export function moveIndex<T>(list: T[], from: number, delta: number): T[] {
  if (from < 0 || from >= list.length || delta === 0) return list;
  const to = Math.min(Math.max(from + delta, 0), list.length - 1);
  if (to === from) return list;
  const out = list.slice();
  const [x] = out.splice(from, 1);
  out.splice(to, 0, x!);
  return out;
}

/** Tri alphabétique (accents et casse ignorés, chiffres comparés comme des nombres). */
export const sortAZ = <T>(list: T[], label: (t: T) => string): T[] =>
  list.slice().sort((a, b) => label(a).localeCompare(label(b), undefined, { sensitivity: "base", numeric: true }));
