// Service de suivi choisi (Trakt, Simkl ou aucun) côté bureau. Le Worker aiguille les routes /api/device/trakt/* vers le
// service actif du compte : le reste de l'appli ne connaît qu'une bibliothèque « de suivi », au format de Trakt.
// Les libellés existants nomment Trakt : avec Simkl actif, le nom est remplacé (même phrase, dans toutes les langues).

import { useCallback } from "react";
import { useT, type TFn } from "@/i18n";
import { useTraktLibrary } from "./library";

export type TrackerId = "trakt" | "simkl";

export const trackerName = (svc?: string): string => (svc === "simkl" ? "Simkl" : "Trakt");

export const forTracker = (s: string, svc?: string): string => (svc === "simkl" ? s.replace(/Trakt/g, "Simkl") : s);

/** `t` dont les textes nomment le service de suivi actif (Trakt par défaut). */
export function useTrackerT(): TFn {
  const t = useT();
  const svc = useTraktLibrary((s) => s.lib?.service);
  return useCallback((key, vars) => forTracker(t(key, vars), svc), [t, svc]);
}
