// Chaînes masquées et ordre des catégories PAR PROFIL (même modèle qu'Android : ProfileChannelHiding / ProfileSwitcher).
//
//  - `profileHiddenChannels` : ce que masque chaque profil. `hiddenChannels` (db/hidden.ts) = masquées par TOUS : seules celles-là
//    sont supprimées du catalogue (à la synchro et à l'application). Les autres sont filtrées à la lecture pour le profil courant.
//  - `profileCatOrder` : ordre des catégories de chaque profil ; la colonne `ord` reflète toujours le profil COURANT (projection
//    recalculée au changement de profil), ce qui garde les requêtes indexées sans paramètre de profil.

import { hiddenByAll, buildShift, shownNum, NO_SHIFT, profileHiddenChKey, type NumShift } from "@/lib/channelHiding";
import { mergeCategoryOrder } from "@/lib/categoryOrder";
import { db } from "./db";
import { addHidden, listHidden, removeHidden, removeHiddenFromCatalog } from "./hidden";
import { categoriesOf } from "./queries";
import type { Kind, ProfileHiddenChannelRow } from "./types";

export type HiddenItem = { streamId: number; catExt: string | null; name: string };
const KINDS: Kind[] = ["live", "movie", "series"];

// ---------------- Lecture ----------------

/** Identifiants de flux masqués par ce profil pour cette source (ensemble vide si aucun). */
export async function hiddenChannelIds(profile: string, sourceId: number): Promise<Set<number>> {
  const rows = await db.profileHiddenChannels.where("[profile+sourceId]").equals([profile, sourceId]).toArray();
  return new Set(rows.map((r) => r.streamId));
}

export const listProfileHiddenChannels = (sourceId: number): Promise<ProfileHiddenChannelRow[]> => db.profileHiddenChannels.where("sourceId").equals(sourceId).toArray();

/** Ce que le profil courant voit des chaînes d'une source : filtre de lecture et numéros affichés. */
export interface ChannelView {
  hid: ReadonlySet<number>;
  /** Vrai pour les chaînes à garder (séparateurs de section toujours gardés) ; null = rien à filtrer. */
  keep: ((c: { streamId: number; sep?: 0 | 1 }) => boolean) | null;
  /** Numéro affiché : `num` moins les chaînes masquées par ce profil dans la même catégorie qui le précèdent. */
  num: (c: { catExt: string; num: number }) => number;
}
const EMPTY_SET: ReadonlySet<number> = new Set();
export const NO_VIEW: ChannelView = { hid: EMPTY_SET, keep: null, num: (c) => c.num };

export function makeView(hid: ReadonlySet<number>, shift: NumShift): ChannelView {
  if (!hid.size) return NO_VIEW;
  return { hid, keep: (c) => !!c.sep || !hid.has(c.streamId), num: shift === NO_SHIFT ? (c) => c.num : (c) => shownNum(shift, c) };
}

/**
 * Vue du profil [profile] sur la source [source]. Une lecture des chaînes masquées du profil, plus une lecture indexée par chaîne
 * encore présente au catalogue (les masquées par tous n'y sont plus : pas de lecture pour elles) pour connaître leur numéro.
 */
export async function loadChannelView(profile: string, source: { id?: number; cid: number }): Promise<ChannelView> {
  if (source.id == null) return NO_VIEW;
  const rows = await db.profileHiddenChannels.where("[profile+sourceId]").equals([profile, source.id]).toArray();
  if (!rows.length) return NO_VIEW;
  const hid = new Set(rows.map((r) => r.streamId));
  if (!source.cid) return makeView(hid, NO_SHIFT);
  const all = new Set((await listHidden(source.id)).map((h) => h.streamId));
  const found = await Promise.all(rows.filter((r) => !all.has(r.streamId)).map((r) => db.channels.where("[sourceId+streamId]").equals([source.cid, r.streamId]).first()));
  return makeView(hid, buildShift(found.filter((c): c is NonNullable<typeof c> => !!c && !c.sep)));
}

// ---------------- Masquer / réafficher ----------------

export interface HideOutcome {
  /** Chaînes retirées du catalogue (devenues masquées par tous). */
  removed: number;
  /** Des chaînes ne sont plus masquées par tous : absentes du catalogue jusqu'à la prochaine synchro de la source. */
  reshow: boolean;
}

/** Retire du catalogue ce qui est masqué par tous, et corrige le total du Direct de la source. */
async function dropFromCatalog(sourceId: number, cid: number, only?: ReadonlySet<number>): Promise<number> {
  const removed = await removeHiddenFromCatalog(cid, sourceId, only);
  if (removed) {
    const cur = await db.sources.get(sourceId);
    if (cur) await db.sources.update(sourceId, { counts: { ...cur.counts, live: Math.max(0, cur.counts.live - removed) } });
  }
  return removed;
}

/**
 * Aligne `hiddenChannels` (masquées par TOUS les profils, lue par la synchro) sur les choix des profils : les chaînes devenues unanimes
 * sont supprimées du catalogue ; celles qui ne le sont plus devront être rechargées par une synchro (reshow).
 * [profileIds] : tous les profils locaux. [cid] : génération courante (0 = pas encore de catalogue).
 */
export async function reconcileHidden(sourceId: number, cid: number, profileIds: readonly string[]): Promise<HideOutcome> {
  const rows = await listProfileHiddenChannels(sourceId);
  const per = new Map<string, Set<number>>(profileIds.map((p) => [p, new Set<number>()]));
  for (const r of rows) per.get(r.profile)?.add(r.streamId);
  const unanimous = hiddenByAll(per);
  const stored = await listHidden(sourceId);
  const storedIds = new Set(stored.map((h) => h.streamId));
  const added = [...unanimous].filter((id) => !storedIds.has(id));
  const gone = [...storedIds].filter((id) => !unanimous.has(id));
  if (added.length) {
    const info = new Map(rows.map((r) => [r.streamId, r]));
    await addHidden(sourceId, added.map((id) => ({ streamId: id, catExt: info.get(id)?.catExt ?? null, name: info.get(id)?.name ?? "" })));
  }
  if (gone.length) await removeHidden(sourceId, gone);
  const removed = added.length && cid ? await dropFromCatalog(sourceId, cid, new Set(added)) : 0;
  return { removed, reshow: gone.length > 0 };
}

const rowOf = (profile: string, sourceId: number, r: HiddenItem): ProfileHiddenChannelRow =>
  ({ key: profileHiddenChKey(profile, sourceId, r.streamId), profile, sourceId, streamId: r.streamId, catExt: r.catExt, name: r.name.slice(0, 120) });

/** Masque [items] pour le profil [profile] ; supprimées du catalogue seulement si tous les profils les masquent. */
export async function hideChannels(sourceId: number, cid: number, profile: string, profileIds: readonly string[], items: HiddenItem[]): Promise<HideOutcome> {
  if (items.length) await db.profileHiddenChannels.bulkPut(items.map((r) => rowOf(profile, sourceId, r)));
  return reconcileHidden(sourceId, cid, profileIds);
}

/** Réaffiche [streamIds] pour le profil [profile] ; rechargées par la synchro si elles avaient été supprimées (masquées par tous). */
export async function showChannels(sourceId: number, cid: number, profile: string, profileIds: readonly string[], streamIds: number[]): Promise<HideOutcome> {
  if (streamIds.length) await db.profileHiddenChannels.bulkDelete(streamIds.map((i) => profileHiddenChKey(profile, sourceId, i)));
  return reconcileHidden(sourceId, cid, profileIds);
}

/** Remplace TOUT ce que masque le profil [profile] pour la source (réglages reçus d'un autre appareil ; ne notifie pas). */
export async function replaceProfileHidden(profile: string, sourceId: number, items: HiddenItem[]): Promise<void> {
  await db.transaction("rw", db.profileHiddenChannels, async () => {
    await db.profileHiddenChannels.where("[profile+sourceId]").equals([profile, sourceId]).delete();
    if (items.length) await db.profileHiddenChannels.bulkPut(items.map((r) => rowOf(profile, sourceId, r)));
  });
}

/** Un profil créé hérite des chaînes déjà supprimées du catalogue (masquées par tous) : elles restent masquées pour lui, il peut les réafficher. */
export async function inheritHiddenAll(profile: string): Promise<void> {
  const rows = await db.hiddenChannels.toArray();
  if (rows.length) await db.profileHiddenChannels.bulkPut(rows.map((h) => rowOf(profile, h.sourceId, h)));
}

/** Un profil supprimé peut rendre unanimes des chaînes masquées par tous les autres : liste « par tous » à réaligner, pour chaque source. */
export async function reconcileAllSources(profileIds: readonly string[]): Promise<void> {
  for (const s of await db.sources.toArray()) if (s.id != null) await reconcileHidden(s.id, s.cid, profileIds);
}

// ---------------- Ordre des catégories par profil ----------------

export const catOrderKey = (profile: string, sourceId: number, kind: Kind) => `${profile}:${sourceId}:${kind}`;

export const listProfileCatOrders = (sourceId: number) => db.profileCatOrder.where("sourceId").equals(sourceId).toArray();

/** Enregistre l'ordre actuellement affiché (`ord`) des catégories de ce type comme ordre du profil. */
export async function saveProfileCatOrder(profile: string, sourceId: number, cid: number, kind: Kind): Promise<void> {
  if (!cid) return;
  const ids = (await categoriesOf(cid, kind)).map((c) => c.extId);
  if (ids.length) await db.profileCatOrder.put({ key: catOrderKey(profile, sourceId, kind), profile, sourceId, kind, ids });
}

/** Écrit des ordres reçus (profil -> type -> ids), sans notifier. */
export async function putProfileCatOrders(sourceId: number, rows: { profile: string; kind: Kind; ids: string[] }[]): Promise<void> {
  const ok = rows.filter((r) => r.ids.length);
  if (ok.length) await db.profileCatOrder.bulkPut(ok.map((r) => ({ key: catOrderKey(r.profile, sourceId, r.kind), profile: r.profile, sourceId, kind: r.kind, ids: r.ids })));
}

/** Recopie l'ordre du profil [profile] dans `ord` (seules les catégories qui changent de place sont écrites). Un type sans ordre enregistré reste tel quel. */
export async function projectCategoryOrder(profile: string): Promise<void> {
  for (const s of await db.sources.toArray()) {
    if (s.id == null || !s.cid) continue;
    for (const kind of KINDS) {
      const saved = await db.profileCatOrder.get(catOrderKey(profile, s.id, kind));
      if (!saved) continue;
      const cats = await categoriesOf(s.cid, kind);
      const byExt = new Map(cats.map((c) => [c.extId, c]));
      const ups: { key: number; changes: { ord: number } }[] = [];
      mergeCategoryOrder(cats.map((c) => c.extId), saved.ids).forEach((ext, i) => { const c = byExt.get(ext)!; if (c.id != null && c.ord !== i) ups.push({ key: c.id, changes: { ord: i } }); });
      if (ups.length) await db.categories.bulkUpdate(ups);
    }
  }
}

/** Un profil créé part de l'ordre affiché au moment de sa création (sans écraser un ordre déjà reçu). */
export async function seedProfileCatOrder(profile: string): Promise<void> {
  for (const s of await db.sources.toArray()) {
    if (s.id == null || !s.cid) continue;
    for (const kind of KINDS) {
      if (await db.profileCatOrder.get(catOrderKey(profile, s.id, kind))) continue;
      await saveProfileCatOrder(profile, s.id, s.cid, kind);
    }
  }
}
