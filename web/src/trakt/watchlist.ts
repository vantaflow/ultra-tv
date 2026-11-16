// « Disponible de ta liste » : titres de la watchlist (Trakt ou Simkl) devenus disponibles dans le catalogue de la source.
// Tout se passe sur l'appareil : la comparaison utilise l'index du catalogue local (IndexedDB) à chaque nouvelle génération
// du catalogue (fin d'une synchro) ; le catalogue n'est jamais envoyé au serveur.

import type { TraktItem } from "@/cloud/client";
import { translate } from "@/i18n";
import { usePrefs } from "@/state/prefs";

/** Titres vus indisponibles au dernier calcul, et titres devenus disponibles (identifiant → date en ms). */
export interface WatchState { unavailable: string[]; recent: Record<string, number> }

/** Durée pendant laquelle un titre reste dans « Disponible de ta liste ». */
export const RECENT_MS = 14 * 24 * 3600 * 1000;
export const WATCH_KEY = "utv.trakt.watch.v1";

/** Identifiant stable d'un titre de la watchlist : type + première clé de rapprochement (ou titre). */
export const watchId = (i: TraktItem): string => `${i.type === "movie" ? "m" : "s"}:${i.keys[0] ?? i.title.toLowerCase()}`;

/**
 * @param prev état précédent de cette source, null la toute première fois (référence posée sans rien signaler : une
 *   watchlist déjà disponible ne déclenche pas une avalanche de notifications)
 * @param ids identifiants de la watchlist actuelle, dans l'ordre du service
 * @param available ceux qui existent aujourd'hui dans le catalogue
 */
export function updateWatch(prev: WatchState | null, ids: string[], available: Set<string>, now: number): { state: WatchState; newly: string[] } {
  const unavailable: string[] = [];
  const recent: Record<string, number> = {};
  const newly: string[] = [];
  const was = new Set(prev?.unavailable ?? []);
  for (const id of ids) {
    if (available.has(id)) {
      const at = prev?.recent[id];
      if (at !== undefined) { if (now - at < RECENT_MS) recent[id] = at; }
      else if (prev && was.has(id)) { recent[id] = now; newly.push(id); }
    } else if (!unavailable.includes(id)) {
      unavailable.push(id);
    }
  }
  return { state: { unavailable, recent }, newly };
}

type Saved = Record<string, WatchState>;
function readAll(): Saved {
  try {
    const o = JSON.parse(localStorage.getItem(WATCH_KEY) ?? "null") as Saved | null;
    return o && typeof o === "object" ? o : {};
  } catch { return {}; }
}

export function loadWatch(sourceKey: string): WatchState | null {
  const s = readAll()[sourceKey];
  if (!s || !Array.isArray(s.unavailable) || typeof s.recent !== "object" || s.recent === null) return null;
  return { unavailable: s.unavailable.filter((x) => typeof x === "string"), recent: Object.fromEntries(Object.entries(s.recent).filter(([, v]) => typeof v === "number")) };
}

export function saveWatch(sourceKey: string, st: WatchState): void {
  try { localStorage.setItem(WATCH_KEY, JSON.stringify({ ...readAll(), [sourceKey]: st })); } catch { /* stockage indisponible : sans effet */ }
}

/** Texte de la notification : au plus trois titres, puis « … » s'il y en a d'autres (aucun mot à traduire). */
export function summarize(titles: string[]): string {
  const shown = titles.slice(0, 3).join(", ");
  return titles.length > 3 ? `${shown}…` : shown;
}

/** Notification locale (API Notification du navigateur / d'Electron) ; sans effet si elle n'est pas autorisée. */
export function notifyAvailable(titles: string[]): void {
  try {
    if (!titles.length || typeof Notification === "undefined" || Notification.permission !== "granted") return;
    new Notification(translate(usePrefs.getState().lang, "home.traktAvailable"), { body: summarize(titles) });
  } catch { /* notifications indisponibles : sans effet */ }
}
