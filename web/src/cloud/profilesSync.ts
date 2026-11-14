// Synchro des profils entre appareils (orchestration) : journal local, échange avec le Worker, application du résultat.
// Règles pures dans profilesLogic.ts. Le renommage garde l'id local, donc favoris, historique et ordre des chaînes.

import { deleteProfileData } from "@/db/profileData";
import { setProfilesHook, usePrefs } from "@/state/prefs";
import { syncProfilesRemote } from "./client";
import { applyActions, diffLog, localEntries, parseRemote, plan, type ProfileLog } from "./profilesLogic";

const LOG_KEY = "ultratv.profileSync.v1";
let cache: ProfileLog | null = null;
let applying = false;

export function loadProfileLog(): ProfileLog {
  if (cache) return cache;
  try { const raw = localStorage.getItem(LOG_KEY); cache = raw ? (JSON.parse(raw) as ProfileLog) : {}; } catch { cache = {}; }
  return cache!;
}
export function saveProfileLog(log: ProfileLog): void {
  cache = log;
  try { localStorage.setItem(LOG_KEY, JSON.stringify(log)); } catch { /* stockage indisponible : journal gardé en mémoire */ }
}
/** Tests : repart d'un journal vide. */
export function resetProfileLog(): void { cache = {}; try { localStorage.removeItem(LOG_KEY); } catch { /* ignoré */ } }

let onLocalChange: (() => void) | null = null;
/** Branché par le service cloud : appelé après un changement LOCAL de profils. */
export const onLocalProfilesChanged = (cb: (() => void) | null) => { onLocalChange = cb; };

// Chaque changement local (création, renommage, couleur, langue, suppression) alimente le journal ; pas les changements appliqués par la synchro.
setProfilesHook((before, after) => {
  if (applying) return;
  saveProfileLog(diffLog(before, after, loadProfileLog(), Date.now()));
  onLocalChange?.();
});

let running: Promise<boolean> | null = null;
/** Échange les profils avec le Worker et applique le résultat. false = route inconnue du Worker. */
export function syncProfiles(worker: string, token: string): Promise<boolean> {
  running ??= doSync(worker, token).finally(() => { running = null; });
  return running;
}

async function doSync(worker: string, token: string): Promise<boolean> {
  const s0 = usePrefs.getState();
  const res = await syncProfilesRemote(worker, token, localEntries(s0.profiles, loadProfileLog()));
  if (!res) return false;
  // L'état est relu après la réponse : une modification faite pendant l'échange (date plus récente) n'est pas écrasée.
  const s = usePrefs.getState();
  const actions = plan(s.profiles, loadProfileLog(), parseRemote(res));
  if (!actions.length) return true;
  const r = applyActions(s.profiles, s.profileId, loadProfileLog(), actions, Date.now());
  applying = true;
  try { usePrefs.getState().set({ profiles: r.profiles, profileId: r.profileId }); } finally { applying = false; }
  saveProfileLog(r.log);
  for (const id of r.deletedIds) await deleteProfileData(id);
  return true;
}
