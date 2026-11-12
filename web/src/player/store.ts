import { create } from "zustand";
import { orderedList } from "@/db/channelOrder";
import { channelsFrom } from "@/db/queries";
import { FAVORITES_SCOPE, neighborOf } from "@/lib/channelOrder";
import { usePrefs } from "@/state/prefs";
import type { PlayTarget } from "./resolve";
import type { RecentChannel } from "./liveOsd";

/** Zaps successifs : le nouveau flux ne part qu'après ce délai, pour que le fournisseur libère l'ancien (comptes à connexion unique). */
export const ZAP_DEBOUNCE_MS = 200;

export type PlayerMode = "closed" | "inline" | "full";
export interface Rect { x: number; y: number; w: number; h: number }

interface PlayerStore {
  target: PlayTarget | null;
  mode: PlayerMode;
  slot: Rect | null;
  /** Contexte de zapping : catégorie courante (null = toutes, FAVORITES_SCOPE = favoris). */
  zapCat: string | null;
  /** Incrémenté pour relancer la lecture de la même cible. */
  nonce: number;
  /** Valeur de `nonce` posée par un zap : le lecteur y retarde la lecture (voir ZAP_DEBOUNCE_MS). */
  zapNonce: number;
  /** Chaînes du Direct regardées pendant la session, la plus récente en tête (rangée « Chaînes récentes » de l'OSD). */
  recents: RecentChannel[];
  open: (t: PlayTarget, mode?: "inline" | "full", zapCat?: string | null) => void;
  setMode: (m: PlayerMode) => void;
  setSlot: (r: Rect | null) => void;
  close: () => void;
  zap: (dir: 1 | -1) => Promise<void>;
  reload: () => void;
}

/** Deux emplacements identiques (à moins d'un demi-pixel). */
export const sameRect = (a: Rect | null, b: Rect | null): boolean =>
  a === b || (!!a && !!b && Math.abs(a.x - b.x) < 0.5 && Math.abs(a.y - b.y) < 0.5 && Math.abs(a.w - b.w) < 0.5 && Math.abs(a.h - b.h) < 0.5);

export const usePlayer = create<PlayerStore>((set, get) => ({
  target: null, mode: "closed", slot: null, zapCat: null, nonce: 0, zapNonce: -1, recents: [],
  open: (target, mode = "full", zapCat) => set((s) => ({ target, mode, zapCat: zapCat === undefined ? s.zapCat : zapCat, nonce: s.nonce + 1 })),
  setMode: (mode) => set({ mode }),
  // Même rectangle : on garde l'état (pas de re-rendu du lecteur pour une mesure identique).
  setSlot: (slot) => set((s) => (sameRect(s.slot, slot) ? s : { slot })),
  close: () => set({ target: null, mode: "closed" }),
  reload: () => set((s) => ({ nonce: s.nonce + 1 })),
  async zap(dir) {
    const { target, zapCat, mode } = get();
    if (!target || target.kind !== "live" || !target.channel) return;
    const ord = target.channel.ord;
    // Zapping dans la catégorie de la chaîne en cours (même lancée depuis l'accueil ou la recherche).
    const cat = zapCat === FAVORITES_SCOPE ? target.channel.catExt ?? null : zapCat ?? target.channel.catExt ?? null;
    // Liste réordonnée par l'utilisateur (favoris, ou catégorie avec un ordre personnel) : la file suit l'ordre affiché.
    const queue = zapCat ? await orderedList(usePrefs.getState().profileId, target.sourceId, target.cid, zapCat) : null;
    const inQueue = !!queue && queue.some((c) => c.streamId === target.refId);
    // Bornes d'index à partir de la chaîne courante : avant, chaque zap lisait toutes les chaînes situées avant elle.
    const next = inQueue
      ? neighborOf(queue!, target.refId, dir, (c) => c.streamId)
      : dir > 0
        ? await channelsFrom(target.cid, cat, ord + 1, Infinity).filter((c) => !c.sep).first()
        : await channelsFrom(target.cid, cat, -1, ord - 1).reverse().filter((c) => !c.sep).first();
    if (!next) return;
    set((s) => ({
      nonce: s.nonce + 1,
      zapNonce: s.nonce + 1,
      mode,
      target: {
        ...target, refId: next.streamId, title: next.display, subtitle: undefined, image: next.logo, url: next.url, replay: undefined,
        channel: { ord: next.ord, catExt: next.catExt, num: next.num, epg: next.epg, archive: !!next.archive, q: next.q, logo: next.logo },
      },
      zapCat: s.zapCat,
    }));
  },
}));
