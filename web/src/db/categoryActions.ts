// Actions groupées sur les catégories d'une source : une seule écriture IndexedDB par action.

import { moveIndex, sortAZ } from "@/lib/categoryOrder";
import { db } from "./db";
import type { CategoryRow } from "./types";

/** Active/désactive d'un coup un ensemble de catégories (seules celles qui changent sont écrites). Retourne le nombre modifié. */
export async function setCategoriesEnabled(rows: Pick<CategoryRow, "id" | "enabled">[], v: 0 | 1): Promise<number> {
  const todo = rows.filter((r) => r.id != null && r.enabled !== v);
  if (!todo.length) return 0;
  await db.categories.bulkUpdate(todo.map((r) => ({ key: r.id!, changes: { enabled: v } })));
  return todo.length;
}

/** Réécrit la position (`ord`) de ces catégories selon l'ordre donné ; seules celles qui changent sont écrites. */
async function renumber(ordered: CategoryRow[]): Promise<number> {
  const ups = ordered.map((c, i) => ({ c, i })).filter((x) => x.c.id != null && x.c.ord !== x.i).map((x) => ({ key: x.c.id!, changes: { ord: x.i } }));
  if (ups.length) await db.categories.bulkUpdate(ups);
  return ups.length;
}

/** Déplace une catégorie de [delta] rangs dans la liste de son type ([sameKind] trié comme affiché). Retourne true si quelque chose a bougé. */
export async function moveCategory(sameKind: CategoryRow[], id: number, delta: number): Promise<boolean> {
  const next = moveIndex(sameKind, sameKind.findIndex((c) => c.id === id), delta);
  return next !== sameKind && (await renumber(next)) > 0;
}

/** Trie les catégories d'un type de A à Z. */
export async function sortCategoriesAZ(sameKind: CategoryRow[]): Promise<boolean> {
  return (await renumber(sortAZ(sameKind, (c) => c.label || c.name))) > 0;
}
