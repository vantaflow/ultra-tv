// Cause réelle d'un échec de lecture (pour le message affiché) : fonctions pures, testées.
import { LIMIT_STATUSES } from "./connectionLimit";
import type { TxDiag } from "./transcode";

export type Failure =
  | { kind: "http"; status: number }
  /** Le fournisseur refuse (401 / 403 / 429 / 458 / 509) : limite de connexions, autre appareil ou compte. */
  | { kind: "account"; status: number }
  | { kind: "codec"; codec: string; /** Commande d'installation de ffmpeg quand la conversion n'est pas disponible. */ install: string | null }
  | { kind: "network" }
  | { kind: "other" };

/** Statuts qui parlent du compte ou des connexions (en plus de LIMIT_STATUSES : identifiants refusés). */
export const ACCOUNT_STATUSES: ReadonlySet<number> = new Set([401, ...LIMIT_STATUSES]);

/** Commande d'installation d'un ffmpeg complet selon le système ; null si l'application le fournit. */
export function ffmpegInstallCommand(platform: string): string {
  if (platform === "darwin") return "brew install ffmpeg";
  if (platform === "linux") return "sudo apt install ffmpeg  /  sudo dnf install ffmpeg";
  return "winget install Gyan.FFmpeg";
}

/**
 * Choisit la cause à afficher. `status` : statut HTTP vu par le lecteur ou par une sonde ; `diag` : diagnostic ffmpeg.
 * Le statut HTTP prime (c'est la réponse du fournisseur) ; sans lui, un codec non pris en charge, puis le réseau.
 */
export function classifyFailure(i: { status?: number | null; diag?: TxDiag | null; platform?: string; canEncode?: boolean }): Failure {
  const status = i.status ?? (i.diag?.reason === "http" ? i.diag.status : null);
  if (status && status >= 400) return ACCOUNT_STATUSES.has(status) ? { kind: "account", status } : { kind: "http", status };
  const d = i.diag;
  if (d?.reason === "ok" && d.convert) return { kind: "codec", codec: d.codec, install: d.canEncode ? null : ffmpegInstallCommand(i.platform ?? "") };
  if (d?.reason === "http") return { kind: "http", status: d.status ?? 0 };
  if (d?.reason === "network") return { kind: "network" };
  return { kind: "other" };
}

/**
 * Que faire d'un flux que Chromium n'a pas pu lire, d'après le diagnostic ffmpeg :
 * - "convert" : vidéo illisible (MPEG-2, HEVC sans décodeur, H.264 10 bits…) → réencodage H.264 ;
 * - "remux" : codecs lisibles mais conteneur ou audio non pris en charge (MKV, AVI, AC-3 / E-AC-3 / DTS) → vidéo copiée
 *   telle quelle, audio en AAC, sortie MPEG-TS ;
 * - "needEncoder" : conversion nécessaire mais ffmpeg n'a pas d'encodeur H.264 ;
 * - null : rien à tenter (flux injoignable, rien de lisible). `vod` : fichier (film, épisode) ; le direct en HLS / TS dont les
 *   codecs sont lisibles garde ses autres formats avant tout remux.
 */
export function planFallback(d: TxDiag | null, { vod }: { vod: boolean }): "convert" | "remux" | "needEncoder" | null {
  if (!d || d.reason !== "ok") return null;
  if (d.convert) return d.canEncode ? "convert" : "needEncoder";
  return vod ? "remux" : null;
}

/** Conteneur d'une adresse de film / épisode d'après son extension (mkv, avi, ts… : pas lisibles tels quels avec leur audio). */
export const isMatroskaLike = (ext: string): boolean => /^(mkv|avi|ts|mov|wmv|flv|webm)$/i.test(ext);

/**
 * Réparation du son d'un film / épisode lu par Chromium, après un changement de format ou d'adresse (la sonde doit viser
 * l'adresse COURANTE, pas la première essayée). Comme planFallback, mais quand la sonde est impossible (statut 551, réseau…) :
 * - le lecteur a signalé « image sans son » (`silent`), ou le conteneur est MKV & co. → remux avec audio AAC quand même
 *   (`-c:v copy -c:a aac`), plutôt que de jouer sans son ; ffmpeg doit être disponible (`canRemux`).
 */
export function planAudioRepair(d: TxDiag | null, { vod, ext, silent, canRemux }: { vod: boolean; ext: string; silent: boolean; canRemux: boolean }): "convert" | "remux" | "needEncoder" | null {
  if (!canRemux) return null;
  const plan = planFallback(d, { vod });
  if (plan) return plan;
  if (d?.reason === "ok") return silent && vod ? "remux" : null;
  return vod && (silent || isMatroskaLike(ext)) ? "remux" : null;
}
