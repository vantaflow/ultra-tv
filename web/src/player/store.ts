import { create } from "zustand";
import { orderedList } from "@/db/channelOrder";
import { channelsFrom } from "@/db/queries";
import { FAVORITES_SCOPE, neighborOf } from "@/lib/channelOrder";
import { mixedNeighbor, type MixedChan } from "@/lib/favoritesMix";
import { usePrefs } from "@/state/prefs";
import { viewOf } from "@/state/channelView";
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
  /** Favoris de toutes les sources (écran Favoris, mode « Toutes les sources ») : le zapping suit cette liste mélangée ; null = file de la source de la chaîne. */
  zapMix: MixedChan[] | null;
  /** Incrémenté pour relancer la lecture de la même cible. */
  nonce: number;
  /** Valeur de `nonce` posée par un zap : le lecteur y retarde la lecture (voir ZAP_DEBOUNCE_MS). */
  zapNonce: number;
  /** Chaînes du Direct regardées pendant la session, la plus récente en tête (rangée « Chaînes récentes » de l'OSD). */
  recents: RecentChannel[];
  open: (t: PlayTarget, mode?: "inline" | "full", zapCat?: string | null, zapMix?: MixedChan[] | null) => void;
  setMode: (m: PlayerMode) => void;
  setSlot: (r: Rect | null) => void;
  close: () => void;
  zap: (dir: 1 | -1) => Promise<void>;
  reload: () => void;
}

/**
 * Porte du code parental (branchée par l'application, voir player/gate.ts ; absente = aucune vérification) : `allow` décide
 * d'une ouverture (et peut demander le code) ; `blockedLive` donne les catégories du Direct que le zapping saute.
 */
export interface OpenGate {
  allow: (t: PlayTarget) => Promise<boolean>;
  blockedLive: (sourceId: number, cid: number) => Promise<ReadonlySet<string>>;
}
let gate: OpenGate | null = null;
export const setOpenGate = (g: OpenGate | null) => { gate = g; };
/** Ouvertures successives : seule la dernière demandée aboutit (une réponse de la porte arrivée trop tard est ignorée). */
let openSeq = 0;

/** Deux emplacements identiques (à moins d'un demi-pixel). */
export const sameRect = (a: Rect | null, b: Rect | null): boolean =>
  a === b || (!!a && !!b && Math.abs(a.x - b.x) < 0.5 && Math.abs(a.y - b.y) < 0.5 && Math.abs(a.w - b.w) < 0.5 && Math.abs(a.h - b.h) < 0.5);

export const usePlayer = create<PlayerStore>((set, get) => ({
  target: null, mode: "closed", slot: null, zapCat: null, zapMix: null, nonce: 0, zapNonce: -1, recents: [],
  // La liste mélangée ne vaut que pour l'ouverture qui la fournit : toute autre ouverture la remet à zéro.
  // Catégorie verrouillée (ou adulte pour un profil Enfants) : rien ne se lance avant le code parental, quelle que soit la voie
  // (liste, accueil, reprise, favoris, recherche, guide).
  open: (target, mode = "full", zapCat, zapMix) => {
    const seq = ++openSeq;
    const commit = () => set((s) => ({ target, mode, zapCat: zapCat === undefined ? s.zapCat : zapCat, zapMix: zapMix ?? null, nonce: s.nonce + 1 }));
    if (!gate) { commit(); return; }
    void gate.allow(target).then((ok) => { if (ok && seq === openSeq) commit(); }, () => undefined);
  },
  setMode: (mode) => set({ mode }),
  // Même rectangle : on garde l'état (pas de re-rendu du lecteur pour une mesure identique).
  setSlot: (slot) => set((s) => (sameRect(s.slot, slot) ? s : { slot })),
  close: () => set({ target: null, mode: "closed" }),
  reload: () => set((s) => ({ nonce: s.nonce + 1 })),
  async zap(dir) {
    const { target, zapCat, mode } = get();
    if (!target || target.kind !== "live" || !target.channel) return;
    // Favoris de toutes les sources : voisin dans la liste mélangée, même s'il vient d'une autre source (flux et génération de catalogue changent avec lui).
    const mix = get().zapMix;
    if (mix) {
      const m = mixedNeighbor(mix, { sourceId: target.sourceId, streamId: target.refId }, dir);
      if (!m) return;
      set((s) => ({
        nonce: s.nonce + 1,
        zapNonce: s.nonce + 1,
        mode,
        target: {
          ...target, sourceId: m.sourceId, cid: m.cid, refId: m.c.streamId, title: m.c.display, subtitle: undefined, image: m.c.logo, url: m.c.url, replay: undefined,
          protectedStream: !!m.c.drm,
          channel: { ord: m.c.ord, catExt: m.c.catExt, num: viewOf(m.sourceId).num(m.c), epg: m.c.epg, archive: !!m.c.archive, q: m.c.q, logo: m.c.logo },
        },
      }));
      return;
    }
    const ord = target.channel.ord;
    // Chaînes masquées par le profil courant : jamais atteintes par le zapping (même Set en mémoire que les listes). Catégories
    // verrouillées (code non saisi) ou adultes pour un profil Enfants : sautées aussi.
    const view = viewOf(target.sourceId);
    const blocked = gate ? await gate.blockedLive(target.sourceId, target.cid) : null;
    const visible = (c: { sep: 0 | 1; streamId: number; catExt: string }) => !c.sep && (!view.keep || view.keep(c)) && !blocked?.has(c.catExt);
    // Zapping dans la catégorie de la chaîne en cours (même lancée depuis l'accueil ou la recherche).
    const cat = zapCat === FAVORITES_SCOPE ? target.channel.catExt ?? null : zapCat ?? target.channel.catExt ?? null;
    // Liste réordonnée par l'utilisateur (favoris, ou catégorie avec un ordre personnel) : la file suit l'ordre affiché.
    const queue = zapCat ? (await orderedList(usePrefs.getState().profileId, target.sourceId, target.cid, zapCat))?.filter((c) => c.streamId === target.refId || !blocked?.has(c.catExt)) ?? null : null;
    const inQueue = !!queue && queue.some((c) => c.streamId === target.refId);
    // Bornes d'index à partir de la chaîne courante : avant, chaque zap lisait toutes les chaînes situées avant elle.
    const next = inQueue
      ? neighborOf(queue!, target.refId, dir, (c) => c.streamId)
      : dir > 0
        ? await channelsFrom(target.cid, cat, ord + 1, Infinity).filter(visible).first()
        : await channelsFrom(target.cid, cat, -1, ord - 1).reverse().filter(visible).first();
    if (!next) return;
    set((s) => ({
      nonce: s.nonce + 1,
      zapNonce: s.nonce + 1,
      mode,
      target: {
        ...target, refId: next.streamId, title: next.display, subtitle: undefined, image: next.logo, url: next.url, replay: undefined,
        channel: { ord: next.ord, catExt: next.catExt, num: view.num(next), epg: next.epg, archive: !!next.archive, q: next.q, logo: next.logo },
      },
      zapCat: s.zapCat,
    }));
  },
}));
