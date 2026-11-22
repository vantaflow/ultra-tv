// Groupes de chaînes personnalisés, par profil et par source : rangés avec les préférences locales (table `settings`).
// Pas de synchro cloud : le Worker n'accepte que les champs de préférences qu'il connaît (un champ nouveau serait perdu).

import { addToGroup, createGroup, deleteGroup, groupIdOf, parseGroups, removeFromGroup, renameGroup, type ChannelGroup } from "@/lib/channelGroups";
import { db, getSetting, setSetting } from "./db";
import type { ChannelRow } from "./types";

export const GROUPS_KEY = "chGroups.v1";
type Store = Record<string, ChannelGroup[]>;
const slot = (profile: string, sourceId: number) => `${profile}:${sourceId}`;

async function readAll(): Promise<Store> {
  const v = await getSetting<unknown>(GROUPS_KEY, {});
  if (!v || typeof v !== "object" || Array.isArray(v)) return {};
  const out: Store = {};
  for (const [k, l] of Object.entries(v as Record<string, unknown>)) { const g = parseGroups(l); if (g.length) out[k] = g; }
  return out;
}

export async function listGroups(profile: string, sourceId: number): Promise<ChannelGroup[]> {
  return (await readAll())[slot(profile, sourceId)] ?? [];
}

async function update(profile: string, sourceId: number, fn: (l: ChannelGroup[]) => ChannelGroup[]): Promise<ChannelGroup[]> {
  return db.transaction("rw", db.settings, async () => {
    const all = await readAll();
    const k = slot(profile, sourceId);
    const next = fn(all[k] ?? []);
    if (next.length) all[k] = next; else delete all[k];
    await setSetting(GROUPS_KEY, all);
    return next;
  });
}

const newId = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;

/** Crée un groupe (avec, facultatif, une première chaîne) ; rend son identifiant, ou null si refusé (nom vide, trop de groupes). */
export async function addGroup(profile: string, sourceId: number, name: string, streamIds: number[] = []): Promise<string | null> {
  const id = newId();
  const next = await update(profile, sourceId, (l) => createGroup(l, name, id, streamIds));
  return next.some((g) => g.id === id) ? id : null;
}
export const renameChannelGroup = (profile: string, sourceId: number, id: string, name: string) => update(profile, sourceId, (l) => renameGroup(l, id, name));
export const deleteChannelGroup = (profile: string, sourceId: number, id: string) => update(profile, sourceId, (l) => deleteGroup(l, id));
export const addToChannelGroup = (profile: string, sourceId: number, id: string, streamIds: number[]) => update(profile, sourceId, (l) => addToGroup(l, id, streamIds));
export const removeFromChannelGroup = (profile: string, sourceId: number, id: string, streamIds: number[]) => update(profile, sourceId, (l) => removeFromGroup(l, id, streamIds));

/** Chaînes d'un groupe (portée `__grp:…`) présentes dans la génération [cid], dans l'ordre du groupe. */
export async function groupChannels(profile: string, sourceId: number, cid: number, scope: string): Promise<ChannelRow[]> {
  const g = (await listGroups(profile, sourceId)).find((x) => x.id === groupIdOf(scope));
  if (!g || !cid) return [];
  const rows = await Promise.all(g.ids.map((s) => db.channels.where("[sourceId+streamId]").equals([cid, s]).first()));
  return rows.filter((r): r is ChannelRow => !!r && !r.sep);
}

/** Profil supprimé : ses groupes partent avec lui. */
export async function deleteProfileGroups(profile: string): Promise<void> {
  await db.transaction("rw", db.settings, async () => {
    const all = await readAll();
    let changed = false;
    for (const k of Object.keys(all)) if (k.startsWith(`${profile}:`)) { delete all[k]; changed = true; }
    if (changed) await setSetting(GROUPS_KEY, all);
  });
}
