// Bibliothèque Trakt en mémoire : chargée quand l'appareil est appairé, rafraîchie au plus toutes les 15 min
// (le Worker met aussi en cache 15 min) et au changement de source. Un échec garde la dernière bonne valeur ;
// « non lié » (ou appareil non appairé) vide tout : l'interface n'affiche alors rien de Trakt.

import { create } from "zustand";
import { traktLibrary, type TraktLibraryResult } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";
import type { Lang } from "@/i18n/lang";
import { usePrefs } from "@/state/prefs";
import { buildWatched, type WatchedIndex } from "./availability";

export const TRAKT_REFRESH_MS = 15 * 60_000;
type Linked = Extract<TraktLibraryResult, { linked: true }>;

interface TraktState { lib: Linked | null; watched: WatchedIndex; loadedAt: number; key: string }
const EMPTY: WatchedIndex = { movies: new Map(), shows: new Map() };

// Persistance : la dernière bibliothèque est gardée en localStorage et réhydratée de façon synchrone au démarrage,
// pour que les rangées Trakt s'affichent tout de suite ; le rafraîchissement réseau suit la règle des 15 min.
export const LIB_STORAGE_KEY = "utv.trakt.lib.v1";
interface Saved { lib: Linked; loadedAt: number; key: string }

export function loadSavedLibrary(): Saved | null {
  try {
    const raw = localStorage.getItem(LIB_STORAGE_KEY);
    if (!raw) return null;
    const o = JSON.parse(raw) as Partial<Saved>;
    const l = o.lib;
    if (!l || l.linked !== true || typeof o.key !== "string" || typeof o.loadedAt !== "number" || !Array.isArray(l.watchlist) || !Array.isArray(l.recommendations)) return null;
    const lib: Linked = { ...l, trending: Array.isArray(l.trending) ? l.trending : [], popular: Array.isArray(l.popular) ? l.popular : [], watched: l.watched ?? { movies: [], shows: [] } };
    return { lib, loadedAt: o.loadedAt, key: o.key };
  } catch { return null; }
}
function saveLibrary(s: Saved | null): void {
  try { if (s) localStorage.setItem(LIB_STORAGE_KEY, JSON.stringify(s)); else localStorage.removeItem(LIB_STORAGE_KEY); } catch { /* quota / stockage indisponible : sans effet */ }
}

/** Réhydrate le store depuis le stockage (appelé à l'import ; exporté pour les tests). */
export function hydrateTraktLibrary(): boolean {
  const s = loadSavedLibrary();
  if (!s) return false;
  useTraktLibrary.setState({ lib: s.lib, watched: buildWatched(s.lib.watched), loadedAt: s.loadedAt, key: s.key });
  return true;
}

export const useTraktLibrary = create<TraktState>(() => ({ lib: null, watched: EMPTY, loadedAt: 0, key: "" }));
hydrateTraktLibrary();

/** Langue envoyée au Worker : celle de l'interface, « en » par défaut. */
export const traktLang = (l: Lang | string | undefined): string => (["fr", "en", "es", "ar"].includes(l ?? "") ? (l as string) : "en");

let inflight: Promise<void> | null = null;

/** Recharge la bibliothèque. `force` ignore le délai de 15 min (changement de source). Ne lève jamais. */
export function refreshTraktLibrary(force = false): Promise<void> {
  const { paired, worker } = useCloud.getState();
  if (!paired) { saveLibrary(null); useTraktLibrary.setState({ lib: null, watched: EMPTY, loadedAt: 0, key: "" }); return Promise.resolve(); }
  const lang = traktLang(usePrefs.getState().lang);
  const key = `${worker}|${lang}`;
  const cur = useTraktLibrary.getState();
  if (!force && cur.key === key && Date.now() - cur.loadedAt < TRAKT_REFRESH_MS) return Promise.resolve();
  if (inflight) return inflight;
  inflight = (async () => {
    try {
      const token = await getToken();
      if (!token) return;
      const r = await traktLibrary(worker, token, lang);
      const now = Date.now();
      if (r.linked) { saveLibrary({ lib: r, loadedAt: now, key }); useTraktLibrary.setState({ lib: r, watched: buildWatched(r.watched), loadedAt: now, key }); }
      else { saveLibrary(null); useTraktLibrary.setState({ lib: null, watched: EMPTY, loadedAt: now, key }); }
    } catch {
      // 401 / 429 / réseau : on garde la dernière valeur ; on réessaiera au prochain cycle (pas de boucle serrée).
      useTraktLibrary.setState({ loadedAt: Date.now() - TRAKT_REFRESH_MS + 60_000, key });
    } finally { inflight = null; }
  })();
  return inflight;
}
