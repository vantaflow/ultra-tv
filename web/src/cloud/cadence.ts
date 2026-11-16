// Cadence adaptative de la synchro cloud et empreintes de ce qui a déjà été envoyé (quota Worker : 100 000 requêtes par jour).
// Fonctions pures (l'heure est un paramètre) : testées sans minuterie. Même logique que SyncCadence.kt côté Android.

const MIN = 60_000;

export interface Cadence {
  /** Un changement a eu lieu (fait sur cet appareil, ou reçu d'un autre) : cadence rapide pendant 10 min. */
  noteChange(now?: number): void;
  /** Délai entre deux synchros périodiques à l'instant [now] : 2 min pendant 10 min après un changement, sinon 10 min. */
  intervalMs(now?: number): number;
  /** Une synchro périodique est-elle due ? [lastRun] : instant de la dernière (0 = jamais). */
  periodicDue(now: number, lastRun: number): boolean;
  /** Retour sur la fenêtre : synchro immédiate, au plus une par minute. */
  foregroundDue(now: number, lastRun: number): boolean;
}

export interface CadenceOptions { activeMs?: number; idleMs?: number; burstMs?: number; foregroundMinMs?: number; start?: number }

export function createCadence(o: CadenceOptions = {}): Cadence {
  const activeMs = o.activeMs ?? 2 * MIN, idleMs = o.idleMs ?? 10 * MIN, burstMs = o.burstMs ?? 10 * MIN, foregroundMinMs = o.foregroundMinMs ?? MIN;
  // Le démarrage compte comme un changement : cadence rapide pendant les 10 premières minutes.
  let lastChange = o.start ?? Date.now();
  const intervalMs = (now = Date.now()) => (now - lastChange < burstMs ? activeMs : idleMs);
  return {
    noteChange: (now = Date.now()) => { lastChange = now; },
    intervalMs,
    periodicDue: (now, lastRun) => now - lastRun >= intervalMs(now),
    foregroundDue: (now, lastRun) => now - lastRun >= foregroundMinMs,
  };
}

/** Empreinte courte (cyrb53, 53 bits) d'un contenu sérialisé : sert à détecter « rien n'a changé depuis le dernier envoi ». */
export function fingerprint(content: string): string {
  let h1 = 0xdeadbeef, h2 = 0x41c6ce57;
  for (let i = 0; i < content.length; i++) {
    const c = content.charCodeAt(i);
    h1 = Math.imul(h1 ^ c, 2654435761);
    h2 = Math.imul(h2 ^ c, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return (4294967296 * (2097151 & h2) + (h1 >>> 0)).toString(16);
}

export interface PushGate {
  /** Faut-il échanger [key] maintenant ? Oui si le contenu [print] a changé, ou si le dernier échange date de plus de 10 min. */
  shouldExchange(key: string, print: string, now?: number): boolean;
  /** À appeler après un échange réussi. */
  markSent(key: string, print: string, now?: number): void;
  /** Oublie tout (dé-appairage, révocation). */
  reset(): void;
}

/**
 * Un contenu inchangé depuis le dernier envoi réussi n'est pas renvoyé. Les échanges (profils, favoris et reprises)
 * renvoient aussi ce que les autres appareils ont changé : un contenu inchangé est donc échangé quand même au plus
 * toutes les [refreshMs] (10 min) pour recevoir ces changements.
 */
export function createPushGate(refreshMs = 10 * MIN): PushGate {
  const sent = new Map<string, { print: string; at: number }>();
  return {
    shouldExchange(key, print, now = Date.now()) {
      const s = sent.get(key);
      return !s || s.print !== print || now - s.at >= refreshMs;
    },
    markSent(key, print, now = Date.now()) { sent.set(key, { print, at: now }); },
    reset() { sent.clear(); },
  };
}

/** Instances partagées par le service cloud, les profils et l'état partagé. */
export const cadence = createCadence();
export const pushGate = createPushGate();
