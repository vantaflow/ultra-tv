// Détection « image mais pas de son » : Chromium (donc Electron) démuxe certains MKV mais ne décode pas
// l'AC-3 / E-AC-3 / DTS (codecs sous licence, absents du ffmpeg d'Electron). Le <video> joue alors en silence,
// SANS événement « error » : le repli de candidats ne se déclenchait jamais.
// Fonctions pures (testables) ; la lecture de l'élément <video> est faite par l'appelant.

export interface AudioSample {
  /** video.currentTime. */
  time: number;
  paused: boolean;
  /** webkitAudioDecodedByteCount (Chromium/WebKit) ; undefined si non exposé. */
  decodedBytes?: number;
  /** mozHasAudio (Firefox) ; undefined si non exposé. */
  mozHasAudio?: boolean;
  /** video.audioTracks.length quand l'API existe (absente par défaut dans Chromium). */
  audioTrackCount?: number;
}

export type AudioVerdict = "wait" | "ok" | "silent";

/** Secondes de lecture réellement écoulées avant de conclure (le décodage audio démarre dès les premières secondes). */
export const AUDIO_PROBE_SECONDS = 3;
/** Au-delà, on abandonne la sonde (flux sans piste audio légitime, saut de position répétés…). */
export const AUDIO_PROBE_MAX_TICKS = 20;

export function readAudioSample(v: HTMLVideoElement): AudioSample {
  const x = v as HTMLVideoElement & { webkitAudioDecodedByteCount?: number; mozHasAudio?: boolean; audioTracks?: { length: number } };
  return {
    time: v.currentTime, paused: v.paused || v.ended,
    decodedBytes: typeof x.webkitAudioDecodedByteCount === "number" ? x.webkitAudioDecodedByteCount : undefined,
    mozHasAudio: typeof x.mozHasAudio === "boolean" ? x.mozHasAudio : undefined,
    audioTrackCount: x.audioTracks && typeof x.audioTracks.length === "number" ? x.audioTracks.length : undefined,
  };
}

/** Cumule le temps de lecture effectif ; conclut « silent » si aucun octet audio n'a été décodé après AUDIO_PROBE_SECONDS. */
export class AudioWatcher {
  private played = 0;
  private ticks = 0;
  private last: number | null = null;

  tick(s: AudioSample): AudioVerdict {
    this.ticks++;
    if (s.decodedBytes !== undefined && s.decodedBytes > 0) return "ok";
    if (s.mozHasAudio === true) return "ok";
    // Aucun signal exploitable (API absente) : on ne peut rien conclure, on n'interfère pas.
    if (s.decodedBytes === undefined && s.mozHasAudio === undefined && s.audioTrackCount === undefined) return "ok";
    if (this.last !== null && !s.paused) {
      const d = s.time - this.last;
      // Les sauts (seek, reprise) ne comptent pas comme du temps joué.
      if (d > 0.2 && d < 2.5) this.played += d;
    }
    this.last = s.time;
    if (this.played >= AUDIO_PROBE_SECONDS) return "silent";
    if (this.ticks >= AUDIO_PROBE_MAX_TICKS) return "ok";
    return "wait";
  }
}

/**
 * Prochain candidat à essayer quand le candidat `i` joue sans son : le suivant dans la liste.
 * -1 s'il n'y en a plus.
 */
export function nextAudioFallback(total: number, i: number): number {
  return i + 1 < total ? i + 1 : -1;
}

/**
 * Quand le repli sur un candidat échoue (404, manifeste absent) après un candidat « image sans son » :
 * mieux vaut revenir à ce dernier (avec avertissement) qu'afficher une erreur. Renvoie l'index à rejouer, ou -1.
 */
export function revertToSilent(silentIdx: number | null, total: number): number {
  return silentIdx !== null && silentIdx >= 0 && silentIdx < total ? silentIdx : -1;
}
