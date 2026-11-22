// Porte des contenus (voir lib/contentGate) : lecture des verrous, des catégories adultes et des catégories masquées.
// Les verrous sont locaux à l'appareil (comme le code parental), communs à tous les profils, jamais synchronisés.

import { blockedCategories, isAdultCategory, lockKey, needsCode, parseLocks, toggleLock, unlockKey, type GateContext, type GateInput } from "@/lib/contentGate";
import { db, getSetting, setSetting } from "./db";
import { hiddenCategoryIds } from "./profileCategories";
import type { Kind } from "./types";
import { usePrefs } from "@/state/prefs";
import { useUnlocks } from "@/state/unlocks";

export const LOCKS_KEY = "catLocks.v1";

interface SourceRef { id?: number; cid: number }

export const getLocks = async (): Promise<Record<string, string[]>> => parseLocks(await getSetting<unknown>(LOCKS_KEY, {}));

export async function lockedCategoryIds(sourceId: number, kind: Kind): Promise<Set<string>> {
  return new Set((await getLocks())[lockKey(sourceId, kind)] ?? []);
}

export async function setCategoryLock(sourceId: number, kind: Kind, extId: string, on: boolean): Promise<void> {
  await setSetting(LOCKS_KEY, toggleLock(await getLocks(), sourceId, kind, extId, on));
}

/** Catégories adultes d'une génération de catalogue (indicateur du fournisseur ou nom). */
export async function adultCategoryIds(cid: number, kind: Kind): Promise<Set<string>> {
  if (!cid) return new Set();
  const rows = await db.categories.where("[sourceId+kind]").equals([cid, kind]).toArray();
  return new Set(rows.filter(isAdultCategory).map((c) => c.extId));
}

const isKids = (profile: string) => !!usePrefs.getState().profiles.find((p) => p.id === profile)?.kids;

/** Session : identifiants de catégorie déverrouillés pour cette source et ce type. */
function unlockedIds(sourceId: number, kind: Kind): Set<string> {
  const pre = unlockKey(sourceId, kind, "");
  return new Set([...useUnlocks.getState().keys].filter((k) => k.startsWith(pre)).map((k) => k.slice(pre.length)));
}

export async function gateInput(profile: string, source: SourceRef, kind: Kind): Promise<GateInput> {
  if (source.id == null) return { hidden: new Set(), locked: new Set(), adult: new Set(), unlocked: new Set(), kids: false };
  const [hidden, locked, adult] = await Promise.all([hiddenCategoryIds(profile, source.id, kind), lockedCategoryIds(source.id, kind), adultCategoryIds(source.cid, kind)]);
  return { hidden, locked, adult, unlocked: unlockedIds(source.id, kind), kids: isKids(profile) };
}

/** Catégories à écarter pour ce profil (masquées, verrouillées, adultes selon le contexte). */
export async function blockedCategoryIds(profile: string, source: SourceRef, kind: Kind, ctx: GateContext = "list"): Promise<Set<string>> {
  return blockedCategories(await gateInput(profile, source, kind), ctx);
}

/** Catégories à retirer de la LISTE des catégories (une catégorie verrouillée y reste, avec un cadenas : on la déverrouille de là). */
export async function unlistedCategoryIds(profile: string, source: SourceRef, kind: Kind): Promise<Set<string>> {
  const g = await gateInput(profile, source, kind);
  return blockedCategories({ ...g, locked: new Set() }, "list");
}

/** Catégories affichées avec un cadenas (code exigé pour les ouvrir). */
export async function codeCategoryIds(profile: string, source: SourceRef, kind: Kind): Promise<Set<string>> {
  const g = await gateInput(profile, source, kind);
  return new Set([...g.locked].filter((id) => needsCode(id, g)));
}

/**
 * Accès à une catégorie (fiche, lecture, ouverture dans une liste). Libre : vrai tout de suite. Code exigé : la fenêtre du code est
 * ouverte ; code accepté = catégorie déverrouillée pour la session. [ask] : demande du code (injectée pour les tests).
 */
export async function ensureCategoryAccess(profile: string, source: SourceRef, kind: Kind, catExt: string | null | undefined, label: string, ask: (label: string) => Promise<boolean>): Promise<boolean> {
  if (catExt == null || source.id == null) return true;
  const g = await gateInput(profile, source, kind);
  if (!needsCode(catExt, g)) return true;
  if (!(await ask(label))) return false;
  useUnlocks.getState().unlock(unlockKey(source.id, kind, catExt));
  return true;
}
