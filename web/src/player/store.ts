import { create } from "zustand";
import { channelsCol } from "@/db/queries";
import type { PlayTarget } from "./resolve";

export type PlayerMode = "closed" | "inline" | "full";
export interface Rect { x: number; y: number; w: number; h: number }

interface PlayerStore {
  target: PlayTarget | null;
  mode: PlayerMode;
  slot: Rect | null;
  /** Contexte de zapping : catégorie courante (null = toutes). */
  zapCat: string | null;
  /** Incrémenté pour relancer la lecture de la même cible. */
  nonce: number;
  open: (t: PlayTarget, mode?: "inline" | "full", zapCat?: string | null) => void;
  setMode: (m: PlayerMode) => void;
  setSlot: (r: Rect | null) => void;
  close: () => void;
  zap: (dir: 1 | -1) => Promise<void>;
  reload: () => void;
}

export const usePlayer = create<PlayerStore>((set, get) => ({
  target: null, mode: "closed", slot: null, zapCat: null, nonce: 0,
  open: (target, mode = "full", zapCat) => set((s) => ({ target, mode, zapCat: zapCat === undefined ? s.zapCat : zapCat, nonce: s.nonce + 1 })),
  setMode: (mode) => set({ mode }),
  setSlot: (slot) => set({ slot }),
  close: () => set({ target: null, mode: "closed" }),
  reload: () => set((s) => ({ nonce: s.nonce + 1 })),
  async zap(dir) {
    const { target, zapCat, mode } = get();
    if (!target || target.kind !== "live" || !target.channel) return;
    const ord = target.channel.ord;
    // Zapping dans la catégorie de la chaîne en cours (même lancée depuis l'accueil ou la recherche).
    const col = channelsCol(target.cid, zapCat ?? target.channel.catExt ?? null);
    const next = dir > 0
      ? await col.clone().filter((c) => c.ord > ord && !c.sep).first()
      : await col.clone().reverse().filter((c) => c.ord < ord && !c.sep).first();
    if (!next) return;
    set((s) => ({
      nonce: s.nonce + 1,
      mode,
      target: {
        ...target, refId: next.streamId, title: next.display, subtitle: undefined, image: next.logo, url: next.url, replay: undefined,
        channel: { ord: next.ord, catExt: next.catExt, num: next.num, epg: next.epg, archive: !!next.archive, q: next.q, logo: next.logo },
      },
      zapCat: s.zapCat,
    }));
  },
}));
