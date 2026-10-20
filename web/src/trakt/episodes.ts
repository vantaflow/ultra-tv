// Épisodes d'une série du catalogue pour « À suivre » et l'import des reprises : fiche en cache (db.details) ;
// à défaut, demandée à la source Xtream au plus une fois par série et par session, dans un budget borné.

import { db } from "@/db/db";
import type { Source } from "@/db/types";

export interface CatEpisode { id: number; season: number; num: number; ext: string; secs: number; title: string }

const requested = new Set<string>();

/** Remet à zéro la mémoire de session (tests). */
export function resetEpisodeRequests(): void { requested.clear(); }

/**
 * Épisodes connus de la série (fiche en cache), sinon demandés à la source si `budget.left > 0` (décrémenté).
 * Liste vide si inconnus (source M3U, échec réseau, budget épuisé).
 */
export async function episodesOf(source: Source, seriesId: number, budget: { left: number }): Promise<CatEpisode[]> {
  const key = `series:${source.id}:${seriesId}`;
  let det = await db.details.get(key);
  if (!det && source.type === "xtream" && budget.left > 0 && !requested.has(key)) {
    requested.add(key);
    budget.left--;
    try {
      const [{ currentTransport }, { seriesInfo }, { credsOf }] = await Promise.all([import("@/net/transport"), import("@/net/xtream"), import("@/sync/core")]);
      const json = await seriesInfo(await currentTransport(), credsOf(source), seriesId);
      det = { key, json, fetchedAt: Date.now() };
      await db.details.put(det);
    } catch { return []; }
  }
  if (!det) return [];
  // Chargé à la demande : la fiche importe beaucoup (lecteur, routeur) et importe elle-même ce module-ci.
  const { parseEpisodes } = await import("@/screens/Detail");
  return parseEpisodes(det.json as Parameters<typeof parseEpisodes>[0]).seasons.flatMap((s) => s.eps)
    .map((e) => ({ id: e.id, season: e.season, num: e.num, ext: e.ext, secs: e.secs, title: e.title }));
}
