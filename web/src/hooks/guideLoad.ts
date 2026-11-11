// Chargement du guide TV en arrière-plan à l'ouverture de l'écran Guide (plus de page intermédiaire « Charger le guide »).

import { useCallback, useEffect, useRef, useState } from "react";
import { db, getSetting, setSetting } from "@/db/db";
import type { Source, SyncProgress } from "@/db/types";
import { syncSourceEpg } from "@/sync/client";

/** Au-delà de cet âge, le guide est rechargé à l'ouverture. */
export const EPG_STALE_MS = 6 * 3600_000;
/** Date (ms) du dernier chargement réussi du guide d'une génération de catalogue. */
export const epgAtKey = (cid: number) => `epg.at.${cid}`;

export interface GuideLoadState {
  phase: "idle" | "loading" | "error";
  /** Octets reçus (0 tant qu'inconnu) et programmes écrits. */
  bytes: number;
  programs: number;
}
export const GUIDE_IDLE: GuideLoadState = { phase: "idle", bytes: 0, programs: 0 };

/**
 * Faut-il lancer la synchro du guide ? Oui s'il n'y a aucun programme à venir (jamais chargé, ou entièrement passé)
 * ou si le dernier chargement date de plus de [EPG_STALE_MS].
 */
export function guideNeedsLoad(o: { upcoming: number; lastAt: number; now: number }): boolean {
  if (o.upcoming <= 0) return true;
  return o.now - o.lastAt > EPG_STALE_MS;
}

export interface GuideLoadDeps {
  sync: (source: Source, onProgress: (p: SyncProgress) => void) => Promise<number>;
  upcoming: (cid: number) => Promise<number>;
  lastAt: (source: Source) => Promise<number>;
  markLoaded: (source: Source, at: number) => Promise<void>;
  now: () => number;
}

export const realDeps: GuideLoadDeps = {
  sync: (s, onProgress) => syncSourceEpg(s, onProgress),
  upcoming: (cid) => db.programs.where("[sourceId+end]").between([cid, Date.now()], [cid, Infinity]).count(),
  lastAt: async (s) => Math.max(await getSetting<number>(epgAtKey(s.cid), 0), s.lastSyncAt || 0),
  markLoaded: (s, at) => setSetting(epgAtKey(s.cid), at).then(() => undefined),
  now: () => Date.now(),
};

/** Chargements en cours par génération de catalogue : rouvrir l'écran se rattache au chargement existant au lieu d'en relancer un. */
const inflight = new Map<number, { state: GuideLoadState; listeners: Set<(s: GuideLoadState) => void> }>();

/**
 * Démarre la synchro du guide si elle est nécessaire. Ne bloque jamais l'affichage : l'appelant affiche la grille
 * pendant que [onState] suit la progression. Retourne false si rien n'a été lancé.
 */
export async function startGuideLoad(source: Source, onState: (s: GuideLoadState) => void, deps: GuideLoadDeps = realDeps, force = false): Promise<boolean> {
  if (!source.cid) return false;
  const running = inflight.get(source.cid);
  if (running) { running.listeners.add(onState); onState(running.state); return true; }
  if (!force && !guideNeedsLoad({ upcoming: await deps.upcoming(source.cid), lastAt: await deps.lastAt(source), now: deps.now() })) return false;
  const entry = { state: { phase: "loading", bytes: 0, programs: 0 } as GuideLoadState, listeners: new Set([onState]) };
  inflight.set(source.cid, entry);
  const emit = (s: GuideLoadState) => { entry.state = s; for (const l of entry.listeners) l(s); };
  emit(entry.state);
  try {
    await deps.sync(source, (p) => emit({ phase: "loading", bytes: p.bytes ?? 0, programs: p.programs ?? 0 }));
    await deps.markLoaded(source, deps.now());
    inflight.delete(source.cid);
    emit(GUIDE_IDLE);
  } catch (e) {
    inflight.delete(source.cid);
    if (e instanceof DOMException && e.name === "AbortError") emit(GUIDE_IDLE);
    else emit({ phase: "error", bytes: 0, programs: 0 });
  }
  return true;
}

/** État du chargement du guide ; la synchro part toute seule une fois par source à l'ouverture. */
export function useGuideLoad(source: Source): { state: GuideLoadState; reload: () => void } {
  const [state, setState] = useState<GuideLoadState>(GUIDE_IDLE);
  const started = useRef<number | null>(null);
  const live = useRef(true);
  useEffect(() => { live.current = true; return () => { live.current = false; }; }, []);
  const set = useCallback((s: GuideLoadState) => { if (live.current) setState(s); }, []);
  useEffect(() => {
    if (started.current === source.cid) return;
    started.current = source.cid;
    void startGuideLoad(source, set);
  }, [source, set]);
  const reload = useCallback(() => { void startGuideLoad(source, set, realDeps, true); }, [source, set]);
  return { state, reload };
}
