// Journal de la synchro Stalker (films, séries) : étape, type de contenu, code HTTP et classe d'erreur SEULEMENT.
// Jamais le message de l'erreur (il peut contenir l'hôte ou la MAC), ni l'adresse, ni le nom de la catégorie.
// Au plus MAX_LINES lignes détaillées par synchro, puis un bilan par partie. Le bureau n'a pas de télémétrie
// distante : les lignes vont à la console (visible dans les outils de développement).

import { HttpError } from "./transport";

export const MAX_LINES = 20;

export type SyncStep = "categories" | "list" | "page" | "part";
export type SyncType = "vod" | "series";

/** Réponse inutilisable du portail (vide, ni liste ni objet attendu). */
export class StalkerEmptyError extends Error { constructor() { super("empty"); this.name = "StalkerEmptyError"; } }

export const httpOf = (e: unknown): number | null => (e instanceof HttpError ? e.status : null);

/** Classe d'erreur lisible : délai, coupure, refus, réponse vide… (jamais le message). */
export function errorClass(e: unknown): string {
  if (e == null) return "unknown";
  const err = e as { name?: string; message?: string; cause?: { code?: string } };
  if (e instanceof HttpError) return e.status === 401 || e.status === 403 ? "denied" : "http";
  switch (err.name) {
    case "TimeoutError": return "timeout";
    case "StalkerAuthError": return "denied";
    case "StalkerEmptyError": return "empty";
    case "TruncatedError": case "SyntaxError": return "truncated";
    case "TypeError": {
      const code = String(err.cause?.code ?? "");
      if (/ENOTFOUND|EAI_AGAIN/.test(code)) return "dns";
      if (/ECONNREFUSED|ECONNRESET|EHOSTUNREACH|ENETUNREACH|ETIMEDOUT/.test(code)) return "connect";
      return "io";
    }
    default: return "io";
  }
}

const detail = (step: string, type: string, e: unknown, extra = "") =>
  `stalker sync ${extra}step=${step} type=${type} http=${httpOf(e) ?? "-"} error=${errorClass(e)}`;

/** Compteur de lignes détaillées d'une synchro + sortie (console par défaut, remplaçable dans les tests). */
export class StalkerSyncLog {
  private lines = 0;
  constructor(private out: (line: string) => void = (l) => console.info(`[stalker] ${l}`)) {}

  private emit(line: string) { if (++this.lines <= MAX_LINES) this.out(line); }

  /** Une catégorie (ou une partie) sautée. */
  skipped(step: SyncStep, type: SyncType, e: unknown) { this.emit(detail(step, type, e, "skip ")); }
  /** Nouvelle tentative d'une page (délai, coupure, 429, 5xx). */
  retry(type: string, attempt: number, e: unknown) { this.emit(`stalker sync retry step=page type=${type} attempt=${attempt} http=${httpOf(e) ?? "-"} error=${errorClass(e)}`); }
  /** Bilan d'une partie (toujours écrit, hors plafond). */
  summary(type: SyncType, read: number, skipped: number, items: number) { this.out(`stalker sync done type=${type} categories=${read} skipped=${skipped} items=${items}`); }
  /** Module « séries » (type=series) détecté ou absent. */
  seriesModule(present: boolean, categories: number) { this.out(`stalker sync series_module=${present ? "yes" : "no"} categories=${categories}`); }
}
