// Service de suivi choisi par le client (Trakt, Simkl ou aucun). Les appareils parlent à une seule interface
// (/api/device/tracking/*, ou l'ancien /api/device/trakt/*) ; le Worker aiguille vers le service actif du compte.
import { simklEnabled } from "./simkl.js";

export const TRACKING_CHOICES = ["trakt", "simkl", "none"];

/** Services reliés ET utilisables du compte. */
export function linkedServices(env, acct) {
  return {
    trakt: !!(env.TRAKT_CLIENT_ID && acct.traktEnc),
    simkl: !!(simklEnabled(env) && acct.simklEnc),
  };
}

/**
 * Service actif : "trakt" | "simkl" | null. Sans choix explicite (comptes existants), Trakt s'il est relié, sinon
 * Simkl ; un choix explicite non relié (ou « none ») donne null.
 */
export function activeTracker(env, acct) {
  const l = linkedServices(env, acct);
  const pref = acct.tracking;
  if (pref === "none") return null;
  if (pref === "simkl") return l.simkl ? "simkl" : null;
  if (pref === "trakt") return l.trakt ? "trakt" : null;
  return l.trakt ? "trakt" : l.simkl ? "simkl" : null;
}

/** État pour l'appareil : service choisi, services proposés (Simkl masqué sans identifiants) et reliés. */
export function trackingStatus(env, acct) {
  const l = linkedServices(env, acct);
  const available = [...(env.TRAKT_CLIENT_ID ? ["trakt"] : []), ...(simklEnabled(env) ? ["simkl"] : [])];
  const active = activeTracker(env, acct);
  const choice = acct.tracking === "none" || acct.tracking === "simkl" || acct.tracking === "trakt" ? acct.tracking : (active || "none");
  return { service: active, choice, available, linked: l };
}
