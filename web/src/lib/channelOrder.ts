// Ordre personnel des chaînes : logique PURE (sans base ni interface), même règle que sur Android :
// les chaînes qui ont une place passent d'abord (dans l'ordre choisi), les autres suivent dans l'ordre de la source.

/** Liste « favoris » : valeur de `scope` (ne peut pas être l'identifiant d'une catégorie du fournisseur). */
export const FAVORITES_SCOPE = "__fav__";

/**
 * Fusionne l'ordre de la source et l'ordre enregistré [saved] (identifiants de flux, dans l'ordre choisi).
 * Chaîne disparue : ignorée. Chaîne nouvelle (absente de [saved]) : après les chaînes placées, ordre de la source conservé.
 * Aucun ordre enregistré : liste de la source inchangée.
 */
export function mergeOrder<T>(natural: T[], saved: number[] | undefined | null, key: (t: T) => number): T[] {
  if (!saved || saved.length === 0) return natural;
  const place = new Map<number, number>();
  saved.forEach((k, i) => { if (!place.has(k)) place.set(k, i); });
  const placed: Array<{ t: T; p: number }> = [];
  const rest: T[] = [];
  for (const t of natural) { const p = place.get(key(t)); if (p === undefined) rest.push(t); else placed.push({ t, p }); }
  placed.sort((a, b) => a.p - b.p);
  return [...placed.map((x) => x.t), ...rest];
}

/** Déplace [id] de [delta] rangs (négatif = vers le haut), borné aux extrémités. Liste inchangée si l'id est absent ou le déplacement nul. */
export function moveBy(ids: number[], id: number, delta: number): number[] {
  const from = ids.indexOf(id);
  if (from < 0 || delta === 0) return ids;
  const to = Math.min(Math.max(from + delta, 0), ids.length - 1);
  if (to === from) return ids;
  const out = ids.slice();
  out.splice(from, 1);
  out.splice(to, 0, id);
  return out;
}

/**
 * Glisser-déposer : place [id] AVANT la chaîne [target] (ou à la fin si [target] est null / inconnue).
 * Déposer une chaîne sur elle-même, ou juste devant sa voisine suivante, ne change rien.
 */
export function insertBefore(ids: number[], id: number, target: number | null): number[] {
  const from = ids.indexOf(id);
  if (from < 0 || target === id) return ids;
  const out = ids.slice();
  out.splice(from, 1);
  const at = target === null ? -1 : out.indexOf(target);
  if (at < 0) out.push(id); else out.splice(at, 0, id);
  return out.every((v, i) => v === ids[i]) ? ids : out;
}

/** Chaîne qui suit [id] dans [ids] (null = dernière) : cible d'un dépôt « après » une ligne. */
export function nextOf(ids: number[], id: number): number | null {
  const i = ids.indexOf(id);
  return i < 0 || i + 1 >= ids.length ? null : ids[i + 1]!;
}

/** Chaîne voisine de [cur] dans une file ordonnée ([dir] = 1 suivante, -1 précédente) ; null aux extrémités ou si [cur] n'y est pas. */
export function neighborOf<T>(list: T[], cur: number, dir: 1 | -1, key: (t: T) => number): T | null {
  const i = list.findIndex((t) => key(t) === cur);
  return i < 0 ? null : list[i + dir] ?? null;
}

/** Même suite d'identifiants ? */
export const sameOrder = (a: number[] | null | undefined, b: number[] | null | undefined): boolean =>
  (a?.length ?? 0) === (b?.length ?? 0) && (a ?? []).every((v, i) => v === b![i]);

/** Déplacement en cours (clavier) : [before] = ordre enregistré au départ (null = ordre de la source), pour pouvoir annuler. */
export interface MoveSession { scope: string; id: number; ids: number[]; before: number[] | null }

export const startMove = (scope: string, id: number, ids: number[], before: number[] | null): MoveSession => ({ scope, id, ids, before });
export const stepMove = (m: MoveSession, delta: number): MoveSession => { const ids = moveBy(m.ids, m.id, delta); return ids === m.ids ? m : { ...m, ids }; };
/** Annulation : l'ordre enregistré d'avant (ou aucun) est conservé tel quel — rien n'a été écrit pendant le déplacement. */
export const cancelMove = (m: MoveSession): number[] | null => m.before;
/** Validation : ordre à enregistrer, ou null si rien n'a bougé par rapport à ce qui s'affichait au départ. */
export const commitMove = (m: MoveSession, natural: number[]): number[] | null => sameOrder(m.ids, mergeOrder(natural, m.before, (x) => x)) ? null : m.ids;
