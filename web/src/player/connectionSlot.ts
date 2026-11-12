// « Emplacement de connexion » : le bureau ne tient jamais deux connexions fournisseur en même temps.
// Beaucoup de comptes Xtream n'autorisent qu'une connexion (user_info.max_connections = 1) : ouvrir le flux
// suivant avant la fermeture du précédent donnait « too many connections ». Toute ouverture de flux du lecteur
// passe par acquire() ; l'ancien titulaire est évincé (libération) avant que le suivant ne parte.
// Logique pure (machine à états) ici, l'horloge et l'attente sont injectées par ConnectionSlot.

export interface SlotState {
  /** Titulaire actuel (jeton), null si libre. */
  holder: number | null;
  /** Horodatage de la dernière libération (ms), null si jamais. */
  releasedAt: number | null;
  /** Dernier jeton attribué. */
  seq: number;
}

export const initialSlot = (): SlotState => ({ holder: null, releasedAt: null, seq: 0 });

export interface AcquireResult {
  state: SlotState;
  /** Jeton attribué au nouveau titulaire. */
  token: number;
  /** Titulaire évincé (à fermer par l'appelant), s'il y en avait un. */
  evicted: number | null;
  /** Attente avant d'ouvrir la connexion : reste du délai de grâce depuis la dernière libération. */
  waitMs: number;
}

/** Prise de l'emplacement : évince un éventuel titulaire, calcule l'attente de grâce restante. */
export function acquire(s: SlotState, now: number, graceMs: number): AcquireResult {
  const token = s.seq + 1;
  const evicted = s.holder;
  // Évincer = libérer à l'instant : la grâce court à partir de maintenant ; sinon depuis la libération passée.
  const releasedAt = evicted !== null ? now : s.releasedAt;
  const waitMs = releasedAt === null ? 0 : Math.max(0, Math.min(graceMs, releasedAt + graceMs - now));
  return { state: { holder: token, releasedAt, seq: token }, token, evicted, waitMs };
}

/** Libération par le titulaire (idempotente ; un jeton périmé est ignoré). */
export function release(s: SlotState, token: number, now: number): SlotState {
  if (s.holder !== token) return s;
  return { ...s, holder: null, releasedAt: now };
}

export interface SlotConfig {
  /** Délai de grâce après fermeture avant d'ouvrir le flux suivant (certains serveurs comptent la connexion encore quelques secondes). */
  graceMs: number;
  /** Nouvelle tentative unique après une erreur de limite de connexions. */
  retryMs: number;
}
export const slotConfig: SlotConfig = { graceMs: 0, retryMs: 1500 };
export const setSlotConfig = (c: Partial<SlotConfig>) => { Object.assign(slotConfig, c); };

export interface Lease { token: number }

/** Version à horloge : acquire() rend la main quand l'ouverture est permise. */
export class ConnectionSlot {
  private s = initialSlot();
  constructor(
    private onEvict: (token: number) => void,
    private now: () => number = () => Date.now(),
    private sleep: (ms: number) => Promise<void> = (ms) => new Promise((r) => setTimeout(r, ms)),
    private cfg: SlotConfig = slotConfig,
  ) {}

  /** Libère l'ancien titulaire puis attend le délai de grâce. Null si une autre ouverture ou un arrêt est passé pendant l'attente. */
  async acquire(): Promise<Lease | null> {
    const r = acquire(this.s, this.now(), this.cfg.graceMs);
    this.s = r.state;
    if (r.evicted !== null) this.onEvict(r.evicted);
    if (r.waitMs > 0) {
      await this.sleep(r.waitMs);
      if (this.s.holder !== r.token) return null;
    }
    return { token: r.token };
  }

  release(lease: Lease | null): void {
    if (lease) this.s = release(this.s, lease.token, this.now());
  }

  /** Libère le titulaire courant, quel qu'il soit (arrêt du lecteur). */
  releaseAll(): void {
    if (this.s.holder !== null) this.s = release(this.s, this.s.holder, this.now());
  }

  get holder(): number | null { return this.s.holder; }
}
