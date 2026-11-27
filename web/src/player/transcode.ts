// Bureau : conversion audio à la volée (AC-3 / E-AC-3 / DTS → AAC) par le proxy Electron et ffmpeg (route /tx).
// Le flux converti n'est pas « cherchable » : un saut relance la conversion à la nouvelle position (`ss`), et la
// position affichée est `offset + currentTime`. Fonctions pures, testées.

export const TX_LABEL = "AAC";

/** Conversion en cours : URL d'origine, position de départ (s) et durée connue du média (s, 0 = direct / inconnue). */
export interface TxState { orig: string; offset: number; duration: number; /** Vidéo convertie en H.264 (Chromium ne sait pas décoder l'originale). */ video?: boolean; /** Piste audio source convertie (0 = première). */ audio?: number }

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
export function txUrl(proxyBase: string, orig: string, ss: number, live = false, audio = 0, video = false): string {
  const s = Math.max(0, Math.round(ss * 1000) / 1000);
  const q = [s > 0 ? `ss=${s}` : "", audio > 0 ? `a=${Math.floor(audio)}` : "", live ? "live=1" : "", video ? "v=h264" : ""].filter(Boolean).join("&");
  return `${proxyBase}tx/${b64url(orig)}${q ? `?${q}` : ""}`;
}

/** Position / durée à afficher. */
export function txClock(tx: TxState | null, currentTime: number, duration: number): { t: number; d: number } {
  if (!tx) return { t: currentTime, d: Number.isFinite(duration) ? duration : 0 };
  return { t: tx.offset + (Number.isFinite(currentTime) ? currentTime : 0), d: tx.duration };
}

/** Marge (s) sous laquelle une fin de conversion est une vraie fin d'épisode. */
export const TX_END_MARGIN = 30;
/** Conversion arrêtée loin de la fin connue : le fournisseur a coupé le flux (p. ex. limite d'une connexion quand
 * l'extraction d'un sous-titre en ouvre une seconde), ce n'est pas la fin de l'épisode. */
export function txCutShort(tx: TxState | null, currentTime: number): boolean {
  if (!tx || !(tx.duration > 0)) return false;
  return txClock(tx, currentTime, NaN).t < tx.duration - TX_END_MARGIN;
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

/** Base de la conversion en cours pour le départ demandé (`ss`, normalisé comme dans txUrl : la base n'est
 * valable que pour cette génération de conversion). Le proxy répond dès qu'il a vu son premier paquet. */
export const txBaseUrl = (proxyBase: string, orig: string, ss = 0): string => {
  const s = Math.max(0, Math.round(ss * 1000) / 1000);
  return `${proxyBase}txb/${b64url(orig)}${s > 0 ? `?ss=${s}` : ""}`;
};

/** Diagnostic d'un flux qui ne s'affiche pas (le proxy sonde avec ffmpeg) ; `hevc` : Chromium sait décoder le HEVC ici. */
export const txDiagUrl = (proxyBase: string, orig: string, hevc: boolean): string => `${proxyBase}txd/${b64url(orig)}?hevc=${hevc ? 1 : 0}`;

export interface TxDiag {
  reason: "ok" | "http" | "network" | "other";
  status: number | null;
  codec: string;
  /** Vidéo illisible par Chromium : à convertir en H.264. */
  convert: boolean;
  /** ffmpeg avec encodeur H.264 disponible. */
  canEncode: boolean;
  /** Durée du média (s), 0 si inconnue (direct). */
  duration: number;
  /** Conteneur (matroska,webm, mpegts…) et codec audio vus par ffmpeg (journal). */
  container: string;
  audio: string;
}

/** Valide la réponse JSON de /txd ; null si inutilisable. */
export function parseTxDiag(raw: unknown): TxDiag | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as { reason?: unknown; status?: unknown; video?: { codec?: unknown } | null; convert?: unknown; canEncode?: unknown; duration?: unknown; container?: unknown; audio?: unknown };
  if (r.reason !== "ok" && r.reason !== "http" && r.reason !== "network" && r.reason !== "other") return null;
  const status = typeof r.status === "number" && r.status >= 100 && r.status < 600 ? r.status : null;
  return { reason: r.reason, status, codec: typeof r.video?.codec === "string" ? r.video.codec.slice(0, 20) : "", convert: r.convert === true, canEncode: r.canEncode === true, duration: typeof r.duration === "number" && r.duration > 0 && r.duration < 1e6 ? r.duration : 0, container: typeof r.container === "string" ? r.container.slice(0, 40) : "", audio: typeof r.audio === "string" ? r.audio.slice(0, 20) : "" };
}

/** Le décodeur HEVC de la plateforme est-il présent ? (Chromium : MSE hvc1.) */
export function canDecodeHevc(): boolean {
  try { return typeof MediaSource !== "undefined" && MediaSource.isTypeSupported('video/mp4; codecs="hvc1.1.6.L93.B0"'); } catch { return false; }
}
