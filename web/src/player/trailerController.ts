// Lecteur de bande-annonce (bureau) : logique de commandes, sans DOM. Le lecteur officiel YouTube (iframe + postMessage,
// « IFrame Player API ») joue la vidéo ; nos commandes le pilotent. Ce module décode ses messages, tient l'état
// (lecture, position, durée, erreur) et calcule les sauts bornés. Le lecteur réel est injecté : les tests utilisent un faux.

/** Pas des boutons −10 s / +10 s et des flèches du clavier. */
export const TRAILER_SKIP_S = 10;

/** Hôtes d'où l'on accepte les messages du lecteur intégré. */
export const YT_ORIGINS = ["https://www.youtube-nocookie.com", "https://www.youtube.com"] as const;

/** Adresse du lecteur intégré : commandes à nous (controls=0), aucune vidéo liée en fin de lecture (rel=0). */
export function trailerEmbedUrl(key: string, origin?: string): string {
  const p = new URLSearchParams({
    enablejsapi: "1", controls: "0", rel: "0", playsinline: "1", modestbranding: "1", iv_load_policy: "3",
    disablekb: "1", fs: "0", autoplay: "1",
  });
  // `origin` n'a de sens que pour une page http(s) ; sous le protocole app:// du bureau on l'omet.
  if (origin && /^https?:\/\//i.test(origin)) p.set("origin", origin);
  return `https://www.youtube-nocookie.com/embed/${encodeURIComponent(key)}?${p}`;
}

export type TrailerErrorKind = "notFound" | "embedBlocked" | "config" | "unknown";

/** Codes d'erreur de l'API : 100/105 introuvable ou privée, 101/150 intégration interdite, 152/153 configuration. */
export function mapYoutubeError(code: number): TrailerErrorKind {
  if (code === 100 || code === 105) return "notFound";
  if (code === 101 || code === 150) return "embedBlocked";
  if (code === 152 || code === 153) return "config";
  return "unknown";
}

export type TrailerPhase = "loading" | "playing" | "paused" | "buffering" | "ended" | "error";

export interface TrailerSnapshot {
  phase: TrailerPhase;
  position: number;
  duration: number;
  error: TrailerErrorKind | null;
}

export type YtEvent =
  | { type: "ready" }
  | { type: "state"; state: number }
  | { type: "time"; position?: number; duration?: number; state?: number }
  | { type: "error"; code: number };

/** Décode un message du lecteur (chaîne JSON ou objet) ; renvoie [] pour tout ce qui n'est pas exploitable. */
export function parseYoutubeMessage(raw: unknown): YtEvent[] {
  let o: unknown = raw;
  if (typeof raw === "string") {
    try { o = JSON.parse(raw); } catch { return []; }
  }
  if (!o || typeof o !== "object") return [];
  const m = o as { event?: unknown; info?: unknown };
  if (typeof m.event !== "string") return [];
  switch (m.event) {
    case "onReady": return [{ type: "ready" }];
    case "onStateChange": return typeof m.info === "number" ? [{ type: "state", state: m.info }] : [];
    case "onError": return typeof m.info === "number" ? [{ type: "error", code: m.info }] : [];
    case "initialDelivery":
    case "infoDelivery": {
      const i = m.info as { currentTime?: unknown; duration?: unknown; playerState?: unknown } | undefined;
      if (!i || typeof i !== "object") return [];
      const out: YtEvent = { type: "time" };
      if (typeof i.currentTime === "number" && Number.isFinite(i.currentTime)) out.position = i.currentTime;
      if (typeof i.duration === "number" && Number.isFinite(i.duration)) out.duration = i.duration;
      if (typeof i.playerState === "number") out.state = i.playerState;
      return out.position === undefined && out.duration === undefined && out.state === undefined ? [] : [out];
    }
    default: return [];
  }
}

/** Nouvelle position après un saut : bornée à 0 et, si la durée est connue, à la durée. */
export function clampSeek(position: number, delta: number, duration: number): number {
  const p = (Number.isFinite(position) ? position : 0) + delta;
  const max = duration > 0 ? duration : Infinity;
  return Math.min(max, Math.max(0, p));
}

/** Position (secondes) pour une fraction de la barre (0..1) ; 0 tant que la durée est inconnue. */
export function seekFromRatio(ratio: number, duration: number): number {
  if (!(duration > 0) || !Number.isFinite(ratio)) return 0;
  return Math.min(1, Math.max(0, ratio)) * duration;
}

/** Fraction lue (0..1) pour la barre de progression. */
export function progressRatio(position: number, duration: number): number {
  return duration > 0 ? Math.min(1, Math.max(0, position / duration)) : 0;
}

/** Ce que le contrôleur sait demander au lecteur. */
export interface TrailerPlayerLike {
  play(): void;
  pause(): void;
  seekTo(seconds: number): void;
}

export interface TrailerController {
  snapshot(): TrailerSnapshot;
  subscribe(fn: (s: TrailerSnapshot) => void): () => void;
  /** Applique un message décodé du lecteur. */
  handle(ev: YtEvent): void;
  toggle(): void;
  skip(deltaSeconds: number): void;
  seekRatio(ratio: number): void;
}

export function createTrailerController(player: TrailerPlayerLike): TrailerController {
  let s: TrailerSnapshot = { phase: "loading", position: 0, duration: 0, error: null };
  const subs = new Set<(x: TrailerSnapshot) => void>();
  const set = (patch: Partial<TrailerSnapshot>) => {
    const n = { ...s, ...patch };
    if (n.phase === s.phase && n.position === s.position && n.duration === s.duration && n.error === s.error) return;
    s = n;
    for (const f of subs) f(s);
  };
  const phaseOf = (state: number): TrailerPhase | null =>
    state === 1 ? "playing" : state === 2 ? "paused" : state === 3 ? "buffering" : state === 0 ? "ended" : null;
  const ok = () => s.phase !== "error";
  return {
    snapshot: () => s,
    subscribe(fn) { subs.add(fn); return () => { subs.delete(fn); }; },
    handle(ev) {
      if (!ok()) return;
      if (ev.type === "ready") return;
      if (ev.type === "error") { set({ phase: "error", error: mapYoutubeError(ev.code) }); return; }
      if (ev.type === "state") { const p = phaseOf(ev.state); if (p) set({ phase: p }); return; }
      const p = ev.state !== undefined ? phaseOf(ev.state) : null;
      set({ ...(p ? { phase: p } : {}), ...(ev.position !== undefined ? { position: ev.position } : {}), ...(ev.duration !== undefined && ev.duration > 0 ? { duration: ev.duration } : {}) });
    },
    toggle() {
      if (!ok()) return;
      if (s.phase === "playing" || s.phase === "buffering") { player.pause(); return; }
      if (s.phase === "ended") player.seekTo(0);
      player.play();
    },
    skip(delta) {
      if (!ok()) return;
      const to = clampSeek(s.position, delta, s.duration);
      player.seekTo(to);
      set({ position: to });   // le lecteur confirmera ; la barre ne reste pas figée en pause
    },
    seekRatio(ratio) {
      if (!ok() || !(s.duration > 0)) return;
      const to = seekFromRatio(ratio, s.duration);
      player.seekTo(to);
      set({ position: to });
    },
  };
}

/**
 * Commandes de l'iframe officielle par postMessage (même protocole que la bibliothèque `iframe_api`, sans charger son
 * script : aucune extension de `script-src` nécessaire). `win` est le `contentWindow` de l'iframe.
 */
export function createYoutubePostMessagePlayer(win: { postMessage(msg: string, target: string): void }, target: string = YT_ORIGINS[0]): TrailerPlayerLike & { listen(): void; poll(): void } {
  const send = (func: string, args: unknown[] = []) => win.postMessage(JSON.stringify({ event: "command", func, args }), target);
  return {
    play: () => send("playVideo"),
    pause: () => send("pauseVideo"),
    seekTo: (sec) => send("seekTo", [sec, true]),
    /** Demande au lecteur de nous envoyer ses événements (à répéter jusqu'au premier message reçu). */
    listen: () => win.postMessage(JSON.stringify({ event: "listening", id: 1, channel: "widget" }), target),
    /** Abonnement explicite aux changements d'état et d'erreur. */
    poll() {
      send("addEventListener", ["onStateChange"]);
      send("addEventListener", ["onError"]);
    },
  };
}
