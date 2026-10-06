// Reconnexion automatique du direct (même logique que l'appli Android) : un serveur Xtream faible qui ferme la
// session, une coupure réseau ou un flux figé ne laissent plus l'image arrêtée avec un bouton « Réessayer ».
// Seulement pour un direct qui a déjà joué : une chaîne qui ne démarre jamais garde le repli de format habituel.

import type { PlayState } from "./engine";

/** Délais entre tentatives ; au-delà, on abandonne (écran d'erreur). */
export const LIVE_RECONNECT_MS = [500, 1_000, 2_000, 3_000, 5_000, 8_000, 10_000, 10_000];
/** Chargement qui ne repart pas au bout de ce délai : flux figé. */
export const STALL_MS = 12_000;
/** Lecture stable pendant ce délai : le compteur de tentatives repart de zéro. */
export const STABLE_MS = 30_000;

export interface ReconnectHooks {
  /** Rouvre la même adresse. */
  reload(): void;
  /** Plus de tentative : afficher l'erreur. */
  giveUp(): void;
  /** Une tentative est programmée (affichage « Reconnexion… »). */
  onReconnecting(attempt: number): void;
}

type Timer = ReturnType<typeof setTimeout>;

export class LiveReconnect {
  private attempts = 0;
  private everPlayed = false;
  private pending: Timer | null = null;
  private stall: Timer | null = null;
  private stable: Timer | null = null;

  constructor(private hooks: ReconnectHooks) {}

  /** Nouvelle chaîne / nouvelle lecture : tout repart de zéro. */
  reset(): void {
    this.clear();
    this.attempts = 0;
    this.everPlayed = false;
  }

  /** Transitions du moteur. [live] : direct (pas une VOD ni un replay). */
  onState(s: PlayState, live: boolean): void {
    if (!live) return;
    if (s === "playing") {
      this.everPlayed = true;
      this.cancel("stall");
      this.cancel("stable");
      // Le lecteur s'est rétabli seul pendant l'attente : la reconnexion programmée ne sert plus.
      if (this.pending !== null) { clearTimeout(this.pending); this.pending = null; }
      this.stable = setTimeout(() => { this.stable = null; this.attempts = 0; }, STABLE_MS);
    } else if ((s === "buffering" || s === "loading") && this.everPlayed && this.stall === null && this.pending === null) {
      // « loading » aussi : après un reload le moteur l'émet, et un flux muet restait sur « Reconnexion… » sans fin.
      this.stall = setTimeout(() => { this.stall = null; this.trigger(); }, STALL_MS);
    } else if (s === "ended") {
      // Direct : une « fin » est une session fermée par le serveur.
      this.trigger();
    }
  }

  /** Erreur du moteur. Vrai si la reconnexion la prend en charge (sinon, repli de format habituel). */
  onError(live: boolean): boolean {
    if (!live || !this.everPlayed) return false;
    this.trigger();
    return true;
  }

  dispose(): void { this.clear(); }

  private trigger(): void {
    this.cancel("stall");
    this.cancel("stable");
    if (this.pending !== null) return;
    const wait = LIVE_RECONNECT_MS[this.attempts];
    if (wait === undefined) { this.hooks.giveUp(); return; }
    this.attempts += 1;
    this.hooks.onReconnecting(this.attempts);
    this.pending = setTimeout(() => { this.pending = null; this.hooks.reload(); }, wait);
  }

  private cancel(k: "stall" | "stable"): void {
    const t = this[k];
    if (t !== null) { clearTimeout(t); this[k] = null; }
  }

  private clear(): void {
    this.cancel("stall");
    this.cancel("stable");
    if (this.pending !== null) { clearTimeout(this.pending); this.pending = null; }
  }
}
