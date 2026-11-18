// Sources déchiffrées, tenues à jour par une requête vivante Dexie : les composants les lisent de façon synchrone.
import { liveQuery } from "dexie";
import { create } from "zustand";
import type { Source } from "@/db/types";
import { purgeOrphanGenerations } from "@/db/db";
import { listSources, migrateCategoryLabels, migrateM3uToXtream } from "@/db/sources";
import { activeToSet } from "@/lib/profileSources";
import { usePrefs } from "./prefs";
import { registerPlexTokens } from "@/net/plex";
import { inheritHiddenAll, projectCategoryOrder, reconcileAllSources, seedProfileCatOrder } from "@/db/profileChannels";
import { startChannelViewWatcher } from "./channelView";

interface SourcesStore { ready: boolean; list: Source[] }
export const useSources = create<SourcesStore>(() => ({ ready: false, list: [] }));

/** La source active suit le profil courant : si elle n'est pas visible pour lui, on prend la première qui l'est. */
export function enforceProfileSources() {
  const { list } = useSources.getState();
  if (!list.length) return;
  const { profiles, profileId, activeSourceId, set } = usePrefs.getState();
  const names = (profiles.find((p) => p.id === profileId) ?? profiles[0])?.sources;
  const id = activeToSet(list, names, activeSourceId);
  if (id != null) set({ activeSourceId: id });
}

/** Réglages par profil à tenir à jour quand la liste des profils change : un profil créé hérite des chaînes masquées par tous et de l'ordre affiché ; un profil supprimé peut rendre d'autres chaînes unanimes. */
let profileWork: Promise<unknown> = Promise.resolve();
function profilesChanged(before: { id: string }[], after: { id: string }[]) {
  const had = new Set(before.map((p) => p.id));
  const has = new Set(after.map((p) => p.id));
  const added = after.filter((p) => !had.has(p.id)).map((p) => p.id);
  const removed = before.some((p) => !has.has(p.id));
  if (!added.length && !removed) return;
  profileWork = profileWork.then(async () => {
    for (const id of added) { await inheritHiddenAll(id); await seedProfileCatOrder(id); }
    await reconcileAllSources(after.map((p) => p.id));
  }).catch(() => undefined);
}

let started = false;
export function startSourcesWatcher() {
  if (started) return;
  started = true;
  startChannelViewWatcher();
  // Changement de profil ou de ses sources visibles : le catalogue affiché change avec lui.
  usePrefs.subscribe((s, prev) => {
    const cur = (s.profiles.find((p) => p.id === s.profileId) ?? s.profiles[0])?.sources;
    const old = (prev.profiles.find((p) => p.id === prev.profileId) ?? prev.profiles[0])?.sources;
    if (s.profileId !== prev.profileId || JSON.stringify(cur) !== JSON.stringify(old)) enforceProfileSources();
    // L'ordre des catégories est propre à chaque profil : celui du nouveau profil est recopié dans `ord`.
    if (s.profileId !== prev.profileId) profileWork = profileWork.then(() => projectCategoryOrder(s.profileId)).catch(() => undefined);
    if (s.profiles !== prev.profiles) profilesChanged(prev.profiles, s.profiles);
  });
  // Réparation des sources M3U « get.php » avant le premier affichage ; en cas d'échec on continue normalement.
  // Puis purge des générations de catalogue orphelines (synchro interrompue) : avant « ready », donc avant toute synchro,
  // car au démarrage aucune génération n'est légitimement « en cours ».
  void migrateM3uToXtream().catch(() => 0).then(() => migrateCategoryLabels().catch(() => 0))
    .then(() => purgeOrphanGenerations({ startup: true }).catch(() => []))
    .then(() => liveQuery(() => listSources()).subscribe({
    next: (list) => {
      registerPlexTokens(list);   // jetons Plex en mémoire seulement (affiches) : voir net/plex.ts
      useSources.setState({ ready: true, list });
      const { activeSourceId, set } = usePrefs.getState();
      if (list.length && !list.some((s) => s.id === activeSourceId)) set({ activeSourceId: list[0]!.id! });
      if (!list.length && activeSourceId != null) set({ activeSourceId: null });
      enforceProfileSources();
    },
    error: () => useSources.setState({ ready: true }),
  }));
}

export function useActiveSource(): Source | undefined {
  const list = useSources((s) => s.list);
  const id = usePrefs((s) => s.activeSourceId);
  return list.find((s) => s.id === id) ?? list[0];
}
