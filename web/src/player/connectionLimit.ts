// Détection de l'erreur « limite de connexions simultanées atteinte » d'un fournisseur IPTV.

/** Statuts HTTP employés par les panneaux IPTV pour refuser une connexion de trop (403 : compte déjà utilisé, 458/509 : variantes Xtream). */
export const LIMIT_STATUSES: ReadonlySet<number> = new Set([403, 429, 458, 509]);

const LIMIT_TEXT = /max(?:imum)?[\s_-]*connections?|connections?[\s_-]*(?:limit|exceeded|reached)|too[\s_-]*many[\s_-]*(?:connections?|streams?|users?)|simultaneous|concurrent[\s_-]*(?:connections?|streams?)|already[\s_-]*(?:in[\s_-]*use|connected)/i;

export interface FailureInfo {
  /** Statut HTTP de la réponse du fournisseur, si connu. */
  status?: number | null;
  /** Début du corps de réponse / message du fournisseur, si connu. */
  message?: string | null;
}

/** Vrai si l'échec ressemble à une limite de connexions (statut dédié ou message explicite). */
export function isConnectionLimit(f: FailureInfo): boolean {
  if (f.message && LIMIT_TEXT.test(f.message)) return true;
  return f.status != null && LIMIT_STATUSES.has(f.status);
}

const toNum = (v: unknown): number | null => (v == null || v === "" || v === "null" || !Number.isFinite(Number(v)) ? null : Number(v));

export interface ConnCounts { active: number | null; max: number | null }

/** Connexions actives / maximales d'après `user_info` (chaînes ou nombres). */
export function connCounts(info: { active_cons?: unknown; max_connections?: unknown } | null | undefined): ConnCounts {
  return { active: toNum(info?.active_cons), max: toNum(info?.max_connections) };
}

/** Variables du message « Limite de connexions atteinte (x/y) » : null si le nombre maximal est inconnu. */
export function limitCounts(c: ConnCounts): { a: number; b: number } | null {
  return c.max === null ? null : { a: c.active ?? c.max, b: c.max };
}

/** Sonde d'un flux après échec (la connexion précédente est déjà fermée) : statut et début du corps. */
export async function probeFailure(
  url: string, headers: Record<string, string>, fetchImpl: typeof fetch = fetch, timeoutMs = 6000,
): Promise<FailureInfo> {
  const ac = new AbortController();
  const to = setTimeout(() => ac.abort(), timeoutMs);
  try {
    const res = await fetchImpl(url, { headers: { ...headers, Range: "bytes=0-0" }, signal: ac.signal });
    const status = res.status;
    let message: string | null = null;
    if (status >= 400) {
      try { message = (await res.text()).slice(0, 512); } catch { /* corps illisible */ }
    }
    // Ne jamais lire le flux : on coupe tout de suite la connexion.
    ac.abort();
    return { status, message };
  } catch {
    return {};
  } finally {
    clearTimeout(to);
  }
}
