// Récupération au démarrage : une source déjà synchronisée dont le catalogue local est vide (catégories disparues après
// une mise à jour) est resynchronisée en entier, une seule fois par démarrage.

import { db } from "@/db/db";
import type { Source } from "@/db/types";

/** Sources déjà tentées depuis le lancement : une source réellement vide ne boucle jamais. */
const tried = new Set<number>();

const R = (cid: number): [[number, number], [number, number]] => [[cid, -1], [cid, Infinity]];

/**
 * Vrai si la source a déjà été synchronisée (date de dernière synchro) mais n'a plus aucune catégorie,
 * ou n'a plus aucun élément pour une partie (direct, films, séries) qui en avait.
 */
export async function catalogNeedsRebuild(s: Pick<Source, "lastSyncAt" | "state" | "cid" | "counts">): Promise<boolean> {
  if (!s.lastSyncAt || (s.state !== "ready" && s.state !== "error")) return false;
  if (!s.cid) return true;
  const cats = await db.categories.where("[sourceId+kind]").between([s.cid, ""], [s.cid, "￿"]).limit(1).count();
  if (cats === 0) return true;
  const tables = { live: db.channels, movie: db.movies, series: db.series } as const;
  for (const kind of ["live", "movie", "series"] as const) {
    if ((s.counts?.[kind] ?? 0) <= 0) continue;
    if ((await tables[kind].where("[sourceId+ord]").between(...R(s.cid)).limit(1).count()) === 0) return true;
  }
  return false;
}

/** Réserve la tentative de ce démarrage pour la source : faux si elle a déjà été tentée. */
export function claimRebuild(sourceId: number): boolean {
  if (tried.has(sourceId)) return false;
  tried.add(sourceId);
  return true;
}

/** Tests : oublie les tentatives. */
export const resetRebuildAttempts = () => tried.clear();
