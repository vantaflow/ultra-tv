// Favoris de TOUTES les sources du profil dans une seule liste : règles pures (testées à part), sans E/S.

import type { ChannelRow } from "@/db/types";

/** Une chaîne favorite avec la source dont elle vient (identifiant réel + génération de catalogue). */
export interface MixedChan { sourceId: number; cid: number; name: string; c: ChannelRow }

/** Le choix « toutes les sources » n'a de sens qu'avec au moins deux sources visibles pour le profil. */
export const canMixFavorites = (visibleCount: number): boolean => visibleCount >= 2;

/** Mode effectivement appliqué : choisi par le profil ET possible (une seule source visible = vue habituelle). */
export const mixActive = (chosen: boolean | undefined, visibleCount: number): boolean => !!chosen && canMixFavorites(visibleCount);

/** Clé d'une catégorie du Direct masquée par le profil : « sourceId:catExt ». */
export const hiddenKey = (sourceId: number, catExt: string) => `${sourceId}:${catExt}`;

/** Retire les chaînes dont la catégorie est masquée pour le profil (même règle que le Direct). */
export function dropHidden(list: MixedChan[], hidden: ReadonlySet<string>): MixedChan[] {
  return hidden.size ? list.filter((x) => !hidden.has(hiddenKey(x.sourceId, x.c.catExt))) : list;
}

/**
 * Liste mélangée : les sources dans l'ordre donné, chacune avec son propre ordre (déjà appliqué par l'appelant) ;
 * une même chaîne n'apparaît qu'une fois.
 */
export function concatBySource(groups: { sourceId: number; items: MixedChan[] }[]): MixedChan[] {
  const seen = new Set<string>();
  const out: MixedChan[] = [];
  for (const g of groups) for (const x of g.items) {
    const k = `${x.sourceId}:${x.c.streamId}`;
    if (!seen.has(k)) { seen.add(k); out.push(x); }
  }
  return out;
}

/** Chaîne voisine dans la liste mélangée (zapping haut/bas) ; null aux extrémités ou si la chaîne courante n'y est pas. */
export function mixedNeighbor(list: readonly MixedChan[], cur: { sourceId: number; streamId: number }, dir: 1 | -1): MixedChan | null {
  const i = list.findIndex((x) => x.sourceId === cur.sourceId && x.c.streamId === cur.streamId);
  return i < 0 ? null : list[i + dir] ?? null;
}
