// Catégories masquées PAR PROFIL (stockage local, par source et par type). Le téléchargement d'une catégorie (champ `enabled`,
// global à la source) suit : voir lib/profileCategories.ts pour la règle.

import { planVisibility, type HiddenMap } from "@/lib/profileCategories";
import { db } from "./db";
import type { CategoryRow, Kind, ProfileHiddenRow } from "./types";

export const profileHiddenKey = (profile: string, sourceId: number, kind: Kind) => `${profile}:${sourceId}:${kind}`;

/** Callback appelé après un changement fait par l'utilisateur (le service cloud y branche la publication) ; jamais appelé pour une réception. */
let onChanged: ((sourceId: number) => void) | null = null;
export const setProfileHiddenListener = (fn: ((sourceId: number) => void) | null) => { onChanged = fn; };

/** Identifiants de catégorie masqués par ce profil pour ce type (ensemble vide si aucun). */
export async function hiddenCategoryIds(profile: string, sourceId: number, kind: Kind): Promise<Set<string>> {
  const r = await db.profileHidden.get(profileHiddenKey(profile, sourceId, kind));
  return new Set(r?.ids ?? []);
}

/** Toutes les lignes d'une source (tous profils, tous types). */
export const listProfileHidden = (sourceId: number): Promise<ProfileHiddenRow[]> => db.profileHidden.where("sourceId").equals(sourceId).toArray();

async function putIds(profile: string, sourceId: number, kind: Kind, ids: Set<string>): Promise<void> {
  const key = profileHiddenKey(profile, sourceId, kind);
  if (ids.size) await db.profileHidden.put({ key, profile, sourceId, kind, ids: [...ids] });
  else await db.profileHidden.delete(key);
}

/**
 * Affiche ou masque [rows] (catégories d'un même type de la source [sourceId]) pour le profil [me].
 * [profiles] : tous les profils locaux. Écrit les listes masquées et le champ `enabled` en une transaction.
 * Retourne le nombre de catégories dont le téléchargement change (catalogue à resynchroniser).
 */
export async function setCategoriesVisible(sourceId: number, me: string, profiles: string[], kind: Kind, rows: Pick<CategoryRow, "id" | "extId" | "enabled">[], visible: boolean): Promise<number> {
  if (!rows.length) return 0;
  const all = profiles.includes(me) ? profiles : [...profiles, me];
  return db.transaction("rw", db.profileHidden, db.categories, async () => {
    const hidden: HiddenMap = new Map();
    for (const p of all) hidden.set(p, await hiddenCategoryIds(p, sourceId, kind));
    const enabledNow = new Set(rows.filter((r) => r.enabled).map((r) => r.extId));
    const plan = planVisibility(me, rows.map((r) => r.extId), visible, enabledNow, hidden, all);
    const touched = new Set<string>();
    for (const [p, ids] of plan.unhide) { const s = hidden.get(p)!; ids.forEach((i) => s.delete(i)); touched.add(p); }
    for (const [p, ids] of plan.hide) { const s = hidden.get(p)!; ids.forEach((i) => s.add(i)); touched.add(p); }
    for (const p of touched) await putIds(p, sourceId, kind, hidden.get(p)!);
    const flips = new Map<string, 0 | 1>([...plan.enable.map((e) => [e, 1 as const] as const), ...plan.disable.map((e) => [e, 0 as const] as const)]);
    const ups = rows.filter((r) => r.id != null && flips.has(r.extId) && flips.get(r.extId) !== r.enabled).map((r) => ({ key: r.id!, changes: { enabled: flips.get(r.extId)! } }));
    if (ups.length) await db.categories.bulkUpdate(ups);
    return ups.length;
  }).then((n) => { onChanged?.(sourceId); return n; });
}

/** Application d'un état reçu : remplace les catégories masquées de chaque profil pour cette source (sans notifier). */
export async function applyProfileHidden(sourceId: number, byProfile: Map<string, Record<Kind, string[]>>): Promise<void> {
  const kinds: Kind[] = ["live", "movie", "series"];
  await db.transaction("rw", db.profileHidden, async () => {
    for (const [profile, per] of byProfile) for (const k of kinds) await putIds(profile, sourceId, k, new Set(per[k]));
  });
}
