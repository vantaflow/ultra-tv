// Réglages Trakt de l'appareil (bureau) : « Marquer vu automatiquement à la fin » et listes personnelles masquées
// de l'accueil (par défaut toutes visibles, au plus MAX_LIST_ROWS rangées). Gardés en localStorage.

import { create } from "zustand";

export const MAX_LIST_ROWS = 5;
export const TRAKT_PREFS_KEY = "utv.trakt.prefs.v1";

export interface TraktPrefs { autoWatched: boolean; hiddenLists: string[] }
const DEFAULTS: TraktPrefs = { autoWatched: true, hiddenLists: [] };

export function loadTraktPrefs(): TraktPrefs {
  try {
    const o = JSON.parse(localStorage.getItem(TRAKT_PREFS_KEY) ?? "null") as Partial<TraktPrefs> | null;
    if (!o) return { ...DEFAULTS };
    return { autoWatched: o.autoWatched !== false, hiddenLists: Array.isArray(o.hiddenLists) ? o.hiddenLists.filter((x) => typeof x === "string") : [] };
  } catch { return { ...DEFAULTS }; }
}

interface Store extends TraktPrefs {
  setAutoWatched: (on: boolean) => void;
  setListVisible: (id: string, visible: boolean) => void;
}

const save = (p: TraktPrefs) => { try { localStorage.setItem(TRAKT_PREFS_KEY, JSON.stringify(p)); } catch { /* stockage indisponible */ } };

export const useTraktPrefs = create<Store>((set, get) => ({
  ...loadTraktPrefs(),
  setAutoWatched: (on) => { set({ autoWatched: on }); save({ autoWatched: on, hiddenLists: get().hiddenLists }); },
  setListVisible: (id, visible) => {
    const hidden = visible ? get().hiddenLists.filter((x) => x !== id) : [...new Set([...get().hiddenLists, id])];
    set({ hiddenLists: hidden });
    save({ autoWatched: get().autoWatched, hiddenLists: hidden });
  },
}));

/** Listes à afficher : masquées retirées, au plus MAX_LIST_ROWS. */
export function visibleLists<T extends { id: string }>(lists: T[], hidden: string[]): T[] {
  const h = new Set(hidden);
  return lists.filter((l) => !h.has(l.id)).slice(0, MAX_LIST_ROWS);
}
