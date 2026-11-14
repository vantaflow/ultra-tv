// Ordre personnel des chaînes (Direct et Favoris), stocké localement par profil et par source.
// Partagé entre appareils avec les réglages d'affichage de la source (profils rapprochés par nom).
import { FAVORITES_SCOPE, mergeOrder } from "@/lib/channelOrder";
import { db } from "./db";
import { channelsCol, favoritesOf } from "./queries";
import type { ChannelOrderRow, ChannelRow } from "./types";

/** Abonné aux changements faits par l'utilisateur (le service cloud y branche la publication) ; jamais appelé par l'application d'un ordre reçu. */
let onChanged: ((sourceId: number) => void) | null = null;
export const setOrderListener = (fn: ((sourceId: number) => void) | null) => { onChanged = fn; };

export const orderKey = (profile: string, sourceId: number, scope: string) => `${profile}:${sourceId}:${scope}`;

/** Ordre enregistré d'une liste (null = ordre de la source). */
export async function getOrder(profile: string, sourceId: number, scope: string): Promise<number[] | null> {
  const r = await db.channelOrder.get(orderKey(profile, sourceId, scope));
  return r && r.ids.length ? r.ids : null;
}

/** Enregistre l'ordre d'une liste ; une liste vide revient à l'ordre de la source. */
export async function saveOrder(profile: string, sourceId: number, scope: string, ids: number[]): Promise<void> {
  if (!ids.length) return resetOrder(profile, sourceId, scope);
  await db.channelOrder.put({ key: orderKey(profile, sourceId, scope), profile, sourceId, scope, ids, updatedAt: Date.now() } satisfies ChannelOrderRow);
  onChanged?.(sourceId);
}

/** « Rétablir l'ordre » : la liste retrouve l'ordre de la source. */
export async function resetOrder(profile: string, sourceId: number, scope: string): Promise<void> {
  await db.channelOrder.delete(orderKey(profile, sourceId, scope));
  onChanged?.(sourceId);
}

/** Tous les ordres personnels d'une source (tous profils). */
export const listOrders = (sourceId: number): Promise<ChannelOrderRow[]> => db.channelOrder.filter((r) => r.sourceId === sourceId).toArray();

/** Application d'un ordre reçu : écrit et efface sans notifier (on ne republie pas ce qu'on vient de recevoir). */
export async function applyOrders(sourceId: number, put: { profile: string; scope: string; ids: number[] }[], remove: { profile: string; scope: string }[]): Promise<void> {
  const now = Date.now();
  await db.transaction("rw", db.channelOrder, async () => {
    await db.channelOrder.bulkDelete(remove.map((r) => orderKey(r.profile, sourceId, r.scope)));
    await db.channelOrder.bulkPut(put.map((r) => ({ key: orderKey(r.profile, sourceId, r.scope), profile: r.profile, sourceId, scope: r.scope, ids: r.ids, updatedAt: now } satisfies ChannelOrderRow)));
  });
}

/**
 * File de zapping / liste du lecteur d'une liste (catégorie ou favoris) dans l'ordre AFFICHÉ, séparateurs exclus.
 * Catégorie sans ordre personnel : null (le lecteur garde la requête par index, sans charger la catégorie).
 */
export async function orderedList(profile: string, sourceId: number, cid: number, scope: string): Promise<ChannelRow[] | null> {
  if (scope === FAVORITES_SCOPE) {
    const favs = await favoritesOf(profile, sourceId, "live");
    const rows = (await Promise.all(favs.map((f) => db.channels.where("[sourceId+streamId]").equals([cid, f.refId]).first()))).filter((r): r is ChannelRow => !!r && !r.sep);
    return mergeOrder(rows, await getOrder(profile, sourceId, scope), (c) => c.streamId);
  }
  const order = await getOrder(profile, sourceId, scope);
  if (!order) return null;
  return mergeOrder(await channelsCol(cid, scope).filter((c) => !c.sep).limit(20000).toArray(), order, (c) => c.streamId);
}
