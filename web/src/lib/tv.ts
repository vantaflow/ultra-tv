// Plateformes TV : Tizen (Samsung) et webOS (LG).
// Détection de l'appareil, transposition des touches de la télécommande vers les touches clavier
// déjà comprises par l'interface (flèches, Entrée, Espace, Échap) et sortie d'application.
// Tout est inactif en dehors d'une TV : navigateur et bureau ne voient aucun changement.
// Les TV 2020+ embarquent Chromium 76+ (Tizen 5.5) / 79+ (webOS 6) : voir docs/tv.md.

/** Plateforme détectée, ou null hors TV. */
export type TvKind = "tizen" | "webos";

/** Détection pure, testable : Tizen annonce « Tizen » (ou SMART-TV sur les anciens), LG « Web0S » (zéro). */
export function tvKindOf(ua: string): TvKind | null {
  if (/Web0S|webOS/i.test(ua)) return "webos";
  if (/Tizen|SMART-TV|SmartTV|Maple/i.test(ua)) return "tizen";
  return null;
}

let kind: TvKind | null | undefined;

export const tvKind = (): TvKind | null => (kind ??= tvKindOf(typeof navigator === "undefined" ? "" : navigator.userAgent));
export const isTv = (): boolean => tvKind() !== null;

/** Éléments de saisie : les touches transposées ne doivent jamais casser la frappe au clavier virtuel. */
export function isTyping(el: EventTarget | null): boolean {
  const t = el as { tagName?: unknown; isContentEditable?: unknown } | null;
  if (!t || typeof t.tagName !== "string") return false;
  const tag = t.tagName.toUpperCase();
  return tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || t.isContentEditable === true;
}

/** Touches que Tizen ne livre qu'après tvinputdevice.registerKey (Retour et flèches le sont toujours). */
export const TIZEN_KEYS = ["MediaPlayPause", "MediaPlay", "MediaPause", "MediaStop", "MediaRewind", "MediaFastForward", "ChannelUp", "ChannelDown", "Exit"] as const;

/** Enregistre les touches média auprès de Tizen (privilège tv.inputdevice). Sans effet ailleurs ; une touche absente du modèle est ignorée. */
export function registerTizenKeys(g: unknown = globalThis): number {
  const dev = (g as { tizen?: { tvinputdevice?: { registerKey?: (k: string) => void } } }).tizen?.tvinputdevice;
  if (!dev?.registerKey) return 0;
  let n = 0;
  for (const k of TIZEN_KEYS) {
    try { dev.registerKey(k); n++; } catch { /* touche absente sur ce modèle */ }
  }
  return n;
}

/** Codes DVB partagés par les deux plateformes : rembobinage, stop, lecture, avance, chaîne +/−, lecture/pause (10252). */
const MEDIA: Record<number, string> = { 19: " ", 412: "ArrowLeft", 413: "Escape", 415: " ", 417: "ArrowRight", 427: "ArrowUp", 428: "ArrowDown", 10252: " " };
/** Retour : 10009 (Return, Tizen) et 461 (webOS) ; Exit : 10182 (Tizen). */
const BACK_CODES = new Set([10009, 461]);
const EXIT_CODES = new Set([10182]);
/** Certains moteurs livrent le Retour via `key` plutôt que via le code. */
const BACK_KEYS = new Set(["XF86Back", "GoBack", "BrowserBack"]);
/** Valeurs legacy pour les rares lecteurs d'événements qui lisent keyCode plutôt que key. */
const LEGACY: Record<string, number> = { " ": 32, ArrowDown: 40, ArrowLeft: 37, ArrowRight: 39, ArrowUp: 38, Escape: 27 };

/**
 * Transposition pure, testable. Renvoie la touche clavier à simuler, "exit" pour quitter
 * l'application, ou null pour ne rien faire (touche inconnue : les gestionnaires existants décident).
 */
export function tvKey(e: { keyCode?: number; key?: string }, typing: boolean): string | "exit" | null {
  const code = e.keyCode ?? 0;
  const key = e.key ?? "";
  if (BACK_CODES.has(code) || BACK_KEYS.has(key)) return "Escape";
  // Sur webOS, la touche Retour arrive aussi en Espace arrière (8) : jamais pendant une saisie.
  if (code === 8 && !typing) return "Escape";
  if (EXIT_CODES.has(code)) return "exit";
  return MEDIA[code] ?? null;
}

/** Sortie propre : API Tizen quand elle existe, sinon fermeture de la fenêtre (webOS). */
export function tvExit() {
  const tizen = (globalThis as unknown as { tizen?: { application?: { exit?: () => void } } }).tizen;
  try {
    if (tizen?.application?.exit) { tizen.application.exit(); return; }
  } catch { /* retombe sur window.close */ }
  window.close();
}

/** Rejoue l'événement comme une touche clavier classique sur la même cible (les handlers React l'entendent). */
function dispatchKey(orig: KeyboardEvent, key: string) {
  for (const type of ["keydown", "keyup"] as const) {
    const synth = new KeyboardEvent(type, { key, bubbles: true, cancelable: true, composed: true });
    Object.defineProperty(synth, "keyCode", { get: () => LEGACY[key] ?? 0 });
    orig.target?.dispatchEvent(synth);
  }
}

/**
 * Branche la transposition des touches (capture, avant tout le monde). No-op hors TV.
 * Le Retour est neutralisé côté navigateur (prévention du défilement / de l'historique) puis
 * rejoué en Échap : l'interface ferme le panneau ou le lecteur. L'app ne se ferme jamais
 * d'elle-même sur Retour (comme sur Android TV) : l'utilisateur passe par Accueil ou Exit.
 */
export function installTvKeys(): () => void {
  if (!isTv()) return () => undefined;
  registerTizenKeys();
  const onKey = (ev: KeyboardEvent) => {
    const mapped = tvKey(ev, isTyping(ev.target));
    if (!mapped) return;
    ev.preventDefault();
    if (mapped === "exit") tvExit();
    else dispatchKey(ev, mapped);
  };
  document.addEventListener("keydown", onKey, true);
  document.addEventListener("keyup", onKey, true);
  return () => {
    document.removeEventListener("keydown", onKey, true);
    document.removeEventListener("keyup", onKey, true);
  };
}

/**
 * Polyfills des API absentes de Chromium 76 (Tizen 2020) : appelé au démarrage, partout,
 * gardé volontairement minuscule. `crypto.randomUUID` n'existe qu'à partir de Chromium 92.
 */
export function ensureTvCompat() {
  const c = globalThis.crypto as (Crypto & { randomUUID?: () => string }) | undefined;
  if (c && !c.randomUUID) {
    c.randomUUID = () => {
      const b = c.getRandomValues(new Uint8Array(16));
      b[6] = (b[6]! & 0x0f) | 0x40; // version 4
      b[8] = (b[8]! & 0x3f) | 0x80; // variante RFC
      const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
      return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
    };
  }
}
