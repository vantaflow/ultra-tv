// Bureau : conversion audio à la volée (AC-3 / E-AC-3 / DTS → AAC) par le proxy Electron et ffmpeg (route /tx).
// Le flux converti n'est pas « cherchable » : un saut relance la conversion à la nouvelle position (`ss`), et la
// position affichée est `offset + currentTime`. Fonctions pures, testées.

export const TX_LABEL = "AAC";

/** Conversion en cours : URL d'origine, position de départ (s) et durée connue du média (s, 0 = direct / inconnue). */
export interface TxState { orig: string; offset: number; duration: number }

const b64url = (s: string) => btoa(String.fromCharCode(...new TextEncoder().encode(s))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

/** `proxyBase` = « http://127.0.0.1:PORT/TOKEN/ ». */
export function txUrl(proxyBase: string, orig: string, ss: number, live = false): string {
  const s = Math.max(0, Math.round(ss * 1000) / 1000);
  const q = [s > 0 ? `ss=${s}` : "", live ? "live=1" : ""].filter(Boolean).join("&");
  return `${proxyBase}tx/${b64url(orig)}${q ? `?${q}` : ""}`;
}

/** Position / durée à afficher. */
export function txClock(tx: TxState | null, currentTime: number, duration: number): { t: number; d: number } {
  if (!tx) return { t: currentTime, d: Number.isFinite(duration) ? duration : 0 };
  return { t: tx.offset + (Number.isFinite(currentTime) ? currentTime : 0), d: tx.duration };
}

/** Nouvelle position bornée à la durée connue (0 = pas de borne). */
export function clampSeek(target: number, duration: number): number {
  const t = Math.max(0, target);
  return duration > 0 ? Math.min(t, Math.max(0, duration - 1)) : t;
}
