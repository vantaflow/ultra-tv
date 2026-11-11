// Décision d'affichage / masquage de l'OSD du lecteur (bureau). Module pur : aucune dépendance au DOM ni aux minuteries.
// Chromium et Windows émettent des `mousemove` sans déplacement réel (rafraîchissement du calque vidéo, changement de
// focus, curseur caché réaffiché) : on ne réagit donc qu'à un vrai mouvement, mesuré depuis la dernière position retenue.

export const OSD_IDLE_MS = 3000;
export const OSD_MOVE_THRESHOLD_PX = 3;

export interface OsdIdle {
  /** Mouvement de souris : renvoie true si l'OSD doit être (ré)affiché. Les événements sans déplacement réel sont ignorés. */
  move(x: number, y: number, movementX?: number, movementY?: number, now?: number): boolean;
  /** Touche ou clic : toujours une activité. */
  activity(now?: number): void;
  /** Le pointeur survole la barre de contrôles, ou le focus clavier y est. */
  setHold(on: boolean): void;
  /** Un menu ou un panneau (pistes, sous-titres, réglages) est ouvert. */
  setMenuOpen(on: boolean): void;
  /** Vrai si l'OSD peut être masqué à cet instant. */
  shouldHide(now?: number): boolean;
  /** Millisecondes avant le masquage possible (0 si dû). */
  remaining(now?: number): number;
}

export function createOsdIdle(opts: { delayMs?: number; thresholdPx?: number } = {}): OsdIdle {
  const delay = opts.delayMs ?? OSD_IDLE_MS;
  const thr = opts.thresholdPx ?? OSD_MOVE_THRESHOLD_PX;
  let anchor: { x: number; y: number } | null = null;
  let last = 0;
  let hold = false;
  let menu = false;
  const t = (n?: number) => n ?? Date.now();
  return {
    move(x, y, mx = 0, my = 0, now) {
      if (!anchor) { anchor = { x, y }; return false; } // première position : simple référence
      const dist = Math.max(Math.abs(x - anchor.x), Math.abs(y - anchor.y));
      const mov = Math.max(Math.abs(mx), Math.abs(my));
      if (dist < thr && mov < thr) return false;
      anchor = { x, y };
      last = t(now);
      return true;
    },
    activity(now) { last = t(now); },
    setHold(on) { hold = on; },
    setMenuOpen(on) { menu = on; },
    shouldHide(now) { return !hold && !menu && t(now) - last >= delay; },
    remaining(now) { return Math.max(0, delay - (t(now) - last)); },
  };
}
