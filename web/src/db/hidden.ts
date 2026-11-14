// Chaînes masquées par l'utilisateur, par source. Comme sur Android, une chaîne masquée est SUPPRIMÉE de la table des chaînes
// (après chaque synchro du catalogue et à l'application) : elle disparaît ainsi du Direct, des favoris, du guide, de la recherche et du zapping.
// La réafficher = la retirer de la liste, puis relancer la synchro de la source.

import { db } from "./db";
import type { HiddenChannelRow } from "./types";

export const hiddenKey = (sourceId: number, streamId: number) => `${sourceId}:${streamId}`;

export const listHidden = (sourceId: number): Promise<HiddenChannelRow[]> => db.hiddenChannels.where("sourceId").equals(sourceId).toArray();

/** Masque des chaînes (déjà masquées : mises à jour). */
export async function addHidden(sourceId: number, rows: { streamId: number; catExt: string | null; name: string }[]): Promise<void> {
  if (!rows.length) return;
  await db.hiddenChannels.bulkPut(rows.map((r) => ({ key: hiddenKey(sourceId, r.streamId), sourceId, ...r } satisfies HiddenChannelRow)));
}

/** Réaffiche des chaînes : retirées de la liste ; elles reviennent à la prochaine synchro de la source. */
export const removeHidden = (sourceId: number, streamIds: number[]) => db.hiddenChannels.bulkDelete(streamIds.map((i) => hiddenKey(sourceId, i)));

/** Remplace toute la liste d'une source. Retourne le nombre de chaînes réaffichées (donc une synchro à relancer). */
export async function replaceHidden(sourceId: number, rows: { streamId: number; catExt: string | null; name: string }[]): Promise<number> {
  const cur = await listHidden(sourceId);
  const next = new Set(rows.map((r) => r.streamId));
  const gone = cur.filter((r) => !next.has(r.streamId));
  await db.transaction("rw", db.hiddenChannels, async () => {
    if (gone.length) await db.hiddenChannels.bulkDelete(gone.map((r) => r.key));
    await addHidden(sourceId, rows);
  });
  return gone.length;
}

/**
 * Supprime de la génération de catalogue [cid] les chaînes masquées de la source [sourceId], et corrige l'effectif des catégories.
 * Retourne le nombre de chaînes réellement retirées (à déduire du total du Direct).
 */
export async function removeHiddenFromCatalog(cid: number, sourceId: number): Promise<number> {
  if (!cid) return 0;
  const hidden = await listHidden(sourceId);
  if (!hidden.length) return 0;
  const perCat = new Map<string, number>();
  let removed = 0;
  await db.transaction("rw", db.channels, db.categories, async () => {
    for (const h of hidden) {
      const rows = await db.channels.where("[sourceId+streamId]").equals([cid, h.streamId]).filter((c) => !c.sep).toArray();
      if (!rows.length) continue;
      await db.channels.bulkDelete(rows.map((r) => r.id!));
      removed += rows.length;
      for (const r of rows) perCat.set(r.catExt, (perCat.get(r.catExt) ?? 0) + 1);
    }
    for (const [ext, n] of perCat) {
      const cat = await db.categories.where("[sourceId+kind+extId]").equals([cid, "live", ext]).first();
      if (cat) await db.categories.update(cat.id!, { count: Math.max(0, cat.count - n) });
    }
  });
  return removed;
}
