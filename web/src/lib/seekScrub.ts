/**
 * Défilement accéléré aux flèches (lecteur film / épisode) : module pur.
 * Spécification partagée avec la version Android (Kotlin) : les deux doivent se comporter pareil.
 *
 * Un appui = ±10 s ; touche maintenue : le pas grandit avec la durée d'appui. La position visée (« cible »)
 * bouge sans sauter ; le saut réel a lieu quand plus rien n'arrive depuis COMMIT_MS (ou tout de suite sur Entrée).
 */

/** Appuis plus rapprochés que ça : cumul sur la même cible. */
export const ACCUM_MS = 600;
/** Silence après le dernier appui / relâchement avant de sauter. */
export const COMMIT_MS = 700;
/** Cadence maximale des pas pendant la répétition automatique de la touche (le clavier en émet ~30/s). */
export const REPEAT_TICK_MS = 250;

export type ScrubState = {
  /** Position au début du défilement (s) : retour d'Échap. */
  origin: number;
  /** Position visée (s). */
  target: number;
  /** Instant du dernier appui / de la dernière répétition (ms). */
  lastPressAt: number;
  /** Début de l'appui en cours (ms) : sert à la durée d'appui. */
  pressStartAt: number;
  /** Instant du dernier pas appliqué (ms) : cadence de la répétition. */
  lastStepAt: number;
};

/** Pas (s) selon la durée d'appui, plafonné à ~2 % de la durée (au moins 10 s) si elle est connue. */
export function stepSeconds(heldMs: number, durationSec: number): number {
  const base = heldMs < 1000 ? 10 : heldMs < 2500 ? 30 : heldMs < 4000 ? 60 : 120;
  return durationSec > 0 ? Math.min(base, Math.max(10, Math.round(durationSec * 0.02))) : base;
}

/** Borne la cible à [0, durée − 1] (durée inconnue : seulement ≥ 0). */
export function clampTarget(t: number, durationSec: number): number {
  const lo = Math.max(0, t);
  return durationSec > 0 ? Math.min(lo, Math.max(0, durationSec - 1)) : lo;
}

/**
 * Appui sur une flèche (dir = +1 avance, −1 recul ; `repeat` = répétition automatique de la touche).
 * `state` null ou périmé : nouveau défilement depuis `positionSec`.
 */
export function press(state: ScrubState | null, dir: 1 | -1, now: number, positionSec: number, durationSec: number, repeat: boolean): ScrubState {
  if (state === null || !(repeat || now - state.lastPressAt < ACCUM_MS)) {
    return {
      origin: positionSec,
      target: clampTarget(positionSec + dir * stepSeconds(0, durationSec), durationSec),
      lastPressAt: now, pressStartAt: now, lastStepAt: now,
    };
  }
  if (repeat) {
    // Répétition : la durée d'appui court depuis le premier appui ; on ne va pas plus vite que REPEAT_TICK_MS.
    if (now - state.lastStepAt < REPEAT_TICK_MS) return { ...state, lastPressAt: now };
    const step = stepSeconds(now - state.pressStartAt, durationSec);
    return { ...state, target: clampTarget(state.target + dir * step, durationSec), lastPressAt: now, lastStepAt: now };
  }
  // Nouvel appui rapproché : cumul de 10 s, la durée d'appui repart de zéro.
  return { ...state, target: clampTarget(state.target + dir * stepSeconds(0, durationSec), durationSec), lastPressAt: now, pressStartAt: now, lastStepAt: now };
}

/** Relâchement de la touche : le délai de validation repart de là. */
export function release(state: ScrubState, now: number): ScrubState {
  return { ...state, lastPressAt: now };
}

/** Faut-il valider (sauter) maintenant ? */
export function shouldCommit(state: ScrubState, now: number): boolean {
  return now - state.lastPressAt >= COMMIT_MS;
}

/** Écart cible − origine, signé : « +2:30 », « −0:40 » (h:mm:ss au-delà d'une heure). */
export function fmtDelta(deltaSec: number): string {
  const r = Math.round(Math.abs(deltaSec));
  const h = Math.floor(r / 3600);
  const m = Math.floor((r % 3600) / 60);
  const s = r % 60;
  const body = h > 0 ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}` : `${m}:${String(s).padStart(2, "0")}`;
  return `${deltaSec < 0 && r > 0 ? "−" : "+"}${body}`;
}
