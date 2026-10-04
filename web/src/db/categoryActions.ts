// Actions groupées sur les catégories d'une source : une seule écriture IndexedDB par action.

import { db } from "./db";
import type { CategoryRow } from "./types";

/** Active/désactive d'un coup un ensemble de catégories (seules celles qui changent sont écrites). Retourne le nombre modifié. */
export async function setCategoriesEnabled(rows: Pick<CategoryRow, "id" | "enabled">[], v: 0 | 1): Promise<number> {
  const todo = rows.filter((r) => r.id != null && r.enabled !== v);
  if (!todo.length) return 0;
  await db.categories.bulkUpdate(todo.map((r) => ({ key: r.id!, changes: { enabled: v } })));
  return todo.length;
}
