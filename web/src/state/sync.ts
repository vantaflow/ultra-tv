import { create } from "zustand";
import type { Source, SyncProgress } from "@/db/types";
import { cancelWork, syncSource } from "@/sync/client";
import { getSource, saveSource } from "@/db/sources";
import { convertToXtream, nonStandardHttpStatus } from "@/lib/xtreamUrl";
import { translate } from "@/i18n";
import { usePrefs } from "./prefs";
import { useUi } from "./ui";

interface SyncStore {
  sourceId: number | null;
  running: boolean;
  progress: SyncProgress | null;
  error: string | null;
  /** Catégories Stalker sautées lors de la dernière synchro réussie (0 = aucune) : message « Synchronisation terminée · X catégories ignorées ». */
  skipped: number;
  start: (source: Source, opts?: { preserveFlags?: boolean; silent?: boolean }) => Promise<boolean>;
  cancel: () => void;
}

export const useSync = create<SyncStore>((set, get) => ({
  sourceId: null, running: false, progress: null, error: null, skipped: 0,
  async start(source, opts = {}) {
    if (get().running) return false;
    set({ sourceId: source.id!, running: true, progress: null, error: null, skipped: 0 });
    let type = source.type;
    try {
      // L'objet de la liste peut dater d'avant une bascule de génération : on repart de la ligne courante.
      let fresh = (await getSource(source.id!)) ?? source;
      // Source M3U enregistrée avec une adresse Xtream (get.php…) : conversion une fois, puis synchro normale.
      const converted = convertToXtream(fresh);
      if (converted) { await saveSource(converted); fresh = converted; }
      type = fresh.type;
      await syncSource({ ...fresh }, (progress) => set({ progress, skipped: progress.skipped ?? get().skipped }), { preserveFlags: opts.preserveFlags });
      set({ running: false });
      if (!opts.silent) {
        const lang = usePrefs.getState().lang, skipped = get().skipped;
        useUi.getState().toast(skipped > 0 ? translate(lang, "sync.doneSkipped", { count: skipped }) : translate(lang, "toast.synced"));
      }
      return true;
    } catch (e) {
      const err = e as Error;
      const odd = nonStandardHttpStatus(err.message);
      const message = odd != null && type === "m3u" ? translate(usePrefs.getState().lang, "src.err.m3uBlocked") : err.message;
      set({ running: false, error: err.name === "AbortError" ? null : message });
      return false;
    }
  },
  cancel: () => { cancelWork(); },
}));
