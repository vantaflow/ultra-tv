// Ordre personnel des chaînes (Direct et Favoris), stocké localement par profil et par source.
// Comme sur Android, il n'est pas envoyé au cloud : seuls les favoris eux-mêmes sont synchronisés.
import { FAVORITES_SCOPE, mergeOrder } from "@/lib/channelOrder";
import { db } from "./db";
import { channelsCol, favoritesOf } from "./queries";
import type { ChannelOrderRow, ChannelRow } from "./types";

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
}

/** « Rétablir l'ordre » : la liste retrouve l'ordre de la source. */
export const resetOrder = (profile: string, sourceId: number, scope: string) => db.channelOrder.delete(orderKey(profile, sourceId, scope));

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
