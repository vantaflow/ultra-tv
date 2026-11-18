// Bureau : conversion audio à la volée (AC-3 / E-AC-3 / DTS → AAC) par le proxy Electron et ffmpeg (route /tx).
// Le flux converti n'est pas « cherchable » : un saut relance la conversion à la nouvelle position (`ss`), et la
// position affichée est `offset + currentTime`. Fonctions pures, testées.

export const TX_LABEL = "AAC";

/** Conversion en cours : URL d'origine, position de départ (s) et durée connue du média (s, 0 = direct / inconnue). */
export interface TxState { orig: string; offset: number; duration: number; /** Piste audio source convertie (0 = première). */ audio?: number }

/** Pistes lisibles dans le fichier (route /txi du proxy) : `i` = index dans son type pour ffmpeg. */
export interface TxTrack { i: number; lang: string; title: string; codec: string }
export interface TxInfo { audio: TxTrack[]; subs: TxTrack[] }

/** Valide la réponse JSON de /txi (jamais de confiance aveugle) ; null si inutilisable. */
export function parseTxInfo(raw: unknown): TxInfo | null {
  if (!raw || typeof raw !== "object") return null;
  const tracks = (v: unknown): TxTrack[] => (Array.isArray(v) ? v : []).flatMap((x) => {
    const o = x as Partial<TxTrack> | null;
    return o && Number.isInteger(o.i) && o.i! >= 0 && o.i! < 100
      ? [{ i: o.i!, lang: String(o.lang ?? "").slice(0, 12), title: String(o.title ?? "").slice(0, 80), codec: String(o.codec ?? "").slice(0, 20) }] : [];
  });
  const r = raw as { audio?: unknown; subs?: unknown };
  const info = { audio: tracks(r.audio), subs: tracks(r.subs) };
  return info.audio.length || info.subs.length ? info : null;
}

const b64url = (s: string) => btoa(String.fromCharCode(...new TextEncoder().encode(s))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

/** `proxyBase` = « http://127.0.0.1:PORT/TOKEN/ ». */
export function txUrl(proxyBase: string, orig: string, ss: number, live = false, audio = 0): string {
  const s = Math.max(0, Math.round(ss * 1000) / 1000);
  const q = [s > 0 ? `ss=${s}` : "", audio > 0 ? `a=${Math.floor(audio)}` : "", live ? "live=1" : ""].filter(Boolean).join("&");
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

/** Adresse de la liste des pistes d'un média (le proxy lit l'en-tête avec ffmpeg). */
export const txInfoUrl = (proxyBase: string, orig: string): string => `${proxyBase}txi/${b64url(orig)}`;
/** Adresse d'un sous-titre texte (index dans les sous-titres du fichier), servi en WebVTT.
 * `ss` : extraction qui part de là (lecture en cours) — sans lui, l'extraction relit le fichier depuis le début. */
export const txSubUrl = (proxyBase: string, orig: string, s: number, ss = 0): string =>
  `${proxyBase}txs/${b64url(orig)}?s=${Math.max(0, Math.floor(s))}${ss > 0 ? `&ss=${Math.round(ss)}` : ""}`;
