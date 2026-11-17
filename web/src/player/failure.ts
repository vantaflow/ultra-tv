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
