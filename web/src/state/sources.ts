// Sources déchiffrées, tenues à jour par une requête vivante Dexie : les composants les lisent de façon synchrone.
import { liveQuery } from "dexie";
import { create } from "zustand";
import type { Source } from "@/db/types";
import { listSources, migrateM3uToXtream } from "@/db/sources";
import { usePrefs } from "./prefs";

interface SourcesStore { ready: boolean; list: Source[] }
export const useSources = create<SourcesStore>(() => ({ ready: false, list: [] }));

let started = false;
export function startSourcesWatcher() {
  if (started) return;
  started = true;
  // Réparation des sources M3U « get.php » avant le premier affichage ; en cas d'échec on continue normalement.
  void migrateM3uToXtream().catch(() => 0).then(() => liveQuery(() => listSources()).subscribe({
    next: (list) => {
      useSources.setState({ ready: true, list });
      const { activeSourceId, set } = usePrefs.getState();
      if (list.length && !list.some((s) => s.id === activeSourceId)) set({ activeSourceId: list[0]!.id! });
      if (!list.length && activeSourceId != null) set({ activeSourceId: null });
    },
    error: () => useSources.setState({ ready: true }),
  }));
}

export function useActiveSource(): Source | undefined {
  const list = useSources((s) => s.list);
  const id = usePrefs((s) => s.activeSourceId);
  return list.find((s) => s.id === id) ?? list[0];
}
