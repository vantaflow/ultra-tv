// Logique pure de l'OSD du Direct (bureau) : décision des touches, étage focalisé, Retour, chaînes récentes.
// Miroir de LiveOsdLogic.kt (Android TV) ; aucune dépendance au DOM ni à React. Testé dans liveOsd.test.ts.

/** Étage de l'OSD qui porte le focus : la barre de contrôles (bas) ou la rangée des chaînes récentes (milieu). */
export type OsdStage = "controls" | "recents";

/** Touches du Direct que l'OSD distingue (le reste est « other »). */
export type OsdKey = "ok" | "up" | "down" | "left" | "right" | "menu" | "info" | "channelUp" | "channelDown" | "guide" | "lastChannel" | "other";

/** Ce que le lecteur doit faire d'une touche du Direct. */
export type OsdDecision =
  | { type: "openOsd" }
  | { type: "zap"; forward: boolean; closeOsd: boolean }
  | { type: "timeshiftJump"; seconds: number }
  | { type: "openChannels" }
  | { type: "recallPrevious" }
  /** Touche absorbée (relance seulement la minuterie de masquage). */
  | { type: "consume" }
  /** Touche laissée à la navigation du focus (OSD ouvert). */
  | { type: "pass" };

/** Saut du différé, en secondes (pas de différé sur ordinateur aujourd'hui : gardé pour la parité). */
export const TIMESHIFT_JUMP_SEC = 30;
/** Nombre maximal de chaînes récentes affichées. */
export const MAX_RECENTS = 10;
/** Chaînes retenues en mémoire (la chaîne en cours en fait partie, elle n'est pas affichée). */
export const KEEP_RECENTS = MAX_RECENTS + 1;
/** Masquage : OSD complet ouvert au clavier / simple bandeau après un zap (la souris garde les 3 s d'osdIdle). */
export const OSD_FULL_HIDE_MS = 8000;
export const OSD_BANNER_HIDE_MS = 5000;
/** Saisie d'un numéro de chaîne : délai avant de zapper, plafond de chiffres. */
export const NUMBER_ENTRY_MS = 1500;
export const NUMBER_MAX_DIGITS = 5;

/** Correspondance KeyboardEvent.key → touche de l'OSD. */
export function keyOf(key: string): OsdKey {
  switch (key) {
    case "Enter": return "ok";
    case "ArrowUp": return "up";
    case "ArrowDown": return "down";
    case "ArrowLeft": return "left";
    case "ArrowRight": return "right";
    case "ContextMenu": return "menu";
    case "PageUp": return "channelUp";
    case "PageDown": return "channelDown";
    case "g": case "G": return "guide";
    case "Backspace": return "lastChannel";
    default: return "other";
  }
}

/**
 * Décision d'une touche pendant le Direct (aucun panneau ouvert ; la saisie de numéro, Espace et les raccourcis lettres
 * sont traités avant).
 * @param osdOpen OSD complet affiché (les commandes ont la main)
 * @param controlsHidden surcouche entièrement masquée (ni OSD ni bandeau)
 * @param tsActive pause du direct (différé) active
 * @param errorShown panneau d'erreur affiché : prioritaire sur toute touche du Direct
 */
export function decide(key: OsdKey, osdOpen: boolean, controlsHidden: boolean, tsActive: boolean, errorShown = false): OsdDecision {
  if (errorShown) return { type: "pass" };
  // Valables dans tous les états.
  switch (key) {
    case "lastChannel": return { type: "recallPrevious" };
    case "guide": return { type: "openChannels" };
    case "channelUp": return { type: "zap", forward: false, closeOsd: true };
    case "channelDown": return { type: "zap", forward: true, closeOsd: true };
    default: break;
  }
  // OSD ouvert : le focus se déplace entre les étages et les boutons.
  if (osdOpen) return { type: "pass" };
  // OSD fermé (masqué ou simple bandeau après un zap).
  switch (key) {
    // OK : liste des chaînes ; Haut / Bas : OSD complet ; Gauche / Droite : chaîne précédente / suivante
    // (en différé masqué : ±30 s).
    case "ok": return { type: "openChannels" };
    case "up": case "down": case "menu": case "info": return { type: "openOsd" };
    case "left": case "right":
      if (tsActive && controlsHidden) return { type: "timeshiftJump", seconds: key === "left" ? -TIMESHIFT_JUMP_SEC : TIMESHIFT_JUMP_SEC };
      if (tsActive) return { type: "openOsd" };
      return { type: "zap", forward: key === "right", closeOsd: false };
    default: return { type: "consume" };
  }
}

/** Mouvement du focus dans l'OSD ouvert. */
export type OsdNav =
  | { type: "focusStage"; stage: OsdStage }
  | { type: "step"; delta: 1 | -1 }
  | { type: "none" };

/**
 * Navigation clavier dans l'OSD ouvert. Bas : étage des récents → barre ; Haut : barre → récents (s'il y en a).
 * Gauche / Droite : bouton précédent / suivant de l'étage (en RTL, Droite recule : `rtl`).
 * Sans focus dans l'OSD (`stage` null), toute flèche amène d'abord le focus sur la barre.
 */
export function navigate(key: OsdKey, stage: OsdStage | null, hasRecents: boolean, rtl = false): OsdNav {
  if (key !== "up" && key !== "down" && key !== "left" && key !== "right") return { type: "none" };
  if (stage === null) return { type: "focusStage", stage: "controls" };
  if (key === "up") return stage === "controls" && hasRecents ? { type: "focusStage", stage: "recents" } : { type: "none" };
  if (key === "down") return stage === "recents" ? { type: "focusStage", stage: "controls" } : { type: "none" };
  const forward = (key === "right") !== rtl;
  return { type: "step", delta: forward ? 1 : -1 };
}

/** Effet d'Échap sur l'OSD (le reste, quitter le lecteur compris, relève du lecteur). */
export type BackEffect = "focusControls" | "closeOsd" | "notOsd";

/** Échap : focus dans les récents → barre ; focus dans la barre → l'OSD se ferme ; OSD fermé → quitter. */
export function backEffect(osdOpen: boolean, stage: OsdStage, playing: boolean): BackEffect {
  if (!osdOpen || !playing) return "notOsd";
  return stage === "recents" ? "focusControls" : "closeOsd";
}

/** Chaîne mémorisée pour la rangée des récents. */
export interface RecentChannel {
  streamId: number;
  num: number;
  name: string;
  logo: string | null;
  ord: number;
  catExt: string | null;
  epg: string | null;
  archive: boolean;
  q: number;
  url?: string;
}

/** Ajoute (ou remonte) une chaîne en tête de l'historique ; sans doublon, bornée à [KEEP_RECENTS]. */
export function pushRecent(all: readonly RecentChannel[], c: RecentChannel, keep = KEEP_RECENTS): RecentChannel[] {
  return [c, ...all.filter((r) => r.streamId !== c.streamId)].slice(0, keep);
}

/** Chaînes récentes affichées : sans la chaîne en cours, au plus [MAX_RECENTS], dans l'ordre de l'historique. */
export function recentsToShow<T extends { streamId: number }>(all: readonly T[], currentId: number | null | undefined): T[] {
  return all.filter((r) => r.streamId !== currentId).slice(0, MAX_RECENTS);
}

/** Saisie d'un numéro : ajoute un chiffre (plafonné) ; null si la touche n'est pas un chiffre seul. */
export function appendDigit(buf: string, key: string): string | null {
  if (!/^[0-9]$/.test(key)) return null;
  return (buf + key).slice(-NUMBER_MAX_DIGITS);
}

/** Délai de masquage supplémentaire (au-delà des 3 s d'osdIdle) pour l'OSD complet ouvert au clavier. */
export const KEYBOARD_EXTRA_MS = OSD_FULL_HIDE_MS - 3000;
