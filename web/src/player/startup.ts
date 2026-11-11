// Délai de démarrage du lecteur web : un flux qui ne produit ni lecture ni progrès de chargement (serveur qui garde
// la connexion ouverte sans rien envoyer, flux MPEG-TS ou natif qui ne se charge jamais) ne doit pas laisser le
// spinner tourner sans fin. hls.js a ses propres délais de manifeste/segment ; mpegts.js et le <video> natif n'en ont pas.
// Au déclenchement, le moteur signale une erreur : le lecteur passe au format suivant, puis affiche l'erreur.

/** Délai sans aucun progrès avant de juger le démarrage figé. */
export const STARTUP_MS = 20_000;
/** Plafond absolu, même si le chargement progresse (VOD très lente). */
export const STARTUP_MAX_MS = 90_000;

export interface StartupProbe {
  /** Assez de données pour lire (readyState >= HAVE_FUTURE_DATA) : le démarrage a réussi, même si la lecture automatique est refusée. */
  ready: boolean;
  /** Mesure croissante du chargement (état de préparation + secondes tamponnées). */
  progress: number;
}

type Timer = ReturnType<typeof setTimeout>;

export class StartupWatchdog {
  private timer: Timer | null = null;

  constructor(
    private probe: () => StartupProbe,
    private onStall: () => void,
    private stepMs = STARTUP_MS,
    private maxMs = STARTUP_MAX_MS,
  ) {}

  /** Arme le délai (à chaque chargement d'un flux). */
  start(): void {
    this.stop();
    let last = this.probe().progress;
    let waited = 0;
    const tick = () => {
      this.timer = null;
      waited += this.stepMs;
      const p = this.probe();
      if (p.ready) return;
      if (p.progress > last && waited < this.maxMs) { last = p.progress; this.timer = setTimeout(tick, this.stepMs); return; }
      this.onStall();
    };
    this.timer = setTimeout(tick, this.stepMs);
  }

  /** Lecture démarrée, erreur, arrêt ou changement de flux. */
  stop(): void {
    if (this.timer !== null) { clearTimeout(this.timer); this.timer = null; }
  }
}
