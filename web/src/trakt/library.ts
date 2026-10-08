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
export const useTraktLibrary = create<TraktState>(() => ({ lib: null, watched: EMPTY, loadedAt: 0, key: "" }));

/** Langue envoyée au Worker : celle de l'interface, « en » par défaut. */
export const traktLang = (l: Lang | string | undefined): string => (["fr", "en", "es", "ar"].includes(l ?? "") ? (l as string) : "en");

let inflight: Promise<void> | null = null;

/** Recharge la bibliothèque. `force` ignore le délai de 15 min (changement de source). Ne lève jamais. */
export function refreshTraktLibrary(force = false): Promise<void> {
  const { paired, worker } = useCloud.getState();
  if (!paired) { useTraktLibrary.setState({ lib: null, watched: EMPTY, loadedAt: 0, key: "" }); return Promise.resolve(); }
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
      if (r.linked) useTraktLibrary.setState({ lib: r, watched: buildWatched(r.watched), loadedAt: Date.now(), key });
      else useTraktLibrary.setState({ lib: null, watched: EMPTY, loadedAt: Date.now(), key });
    } catch {
      // 401 / 429 / réseau : on garde la dernière valeur ; on réessaiera au prochain cycle (pas de boucle serrée).
      useTraktLibrary.setState({ loadedAt: Date.now() - TRAKT_REFRESH_MS + 60_000, key });
    } finally { inflight = null; }
  })();
  return inflight;
}
