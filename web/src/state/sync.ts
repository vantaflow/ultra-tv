import { create } from "zustand";
import type { Source, SyncProgress } from "@/db/types";
import { cancelWork, syncSource } from "@/sync/client";
import { getSource } from "@/db/sources";
import { useUi } from "./ui";

interface SyncStore {
  sourceId: number | null;
  running: boolean;
  progress: SyncProgress | null;
  error: string | null;
  start: (source: Source, opts?: { preserveFlags?: boolean; silent?: boolean }) => Promise<boolean>;
  cancel: () => void;
}

export const useSync = create<SyncStore>((set, get) => ({
  sourceId: null, running: false, progress: null, error: null,
  async start(source, opts = {}) {
    if (get().running) return false;
    set({ sourceId: source.id!, running: true, progress: null, error: null });
    try {
      // L'objet de la liste peut dater d'avant une bascule de génération : on repart de la ligne courante.
      const fresh = (await getSource(source.id!)) ?? source;
      await syncSource({ ...fresh }, (progress) => set({ progress }), { preserveFlags: opts.preserveFlags });
      set({ running: false });
      if (!opts.silent) useUi.getState().toast("Catalogue mis à jour");
      return true;
    } catch (e) {
      const err = e as Error;
      set({ running: false, error: err.name === "AbortError" ? null : err.message });
      return false;
    }
  },
  cancel: () => { cancelWork(); },
}));
