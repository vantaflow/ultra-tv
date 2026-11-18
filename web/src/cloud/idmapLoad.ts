// Construit le traducteur d'identifiants d'une source (lecture du catalogue local). Voir idmap.ts pour les règles.

import { db, getSetting } from "@/db/db";
import type { Source } from "@/db/types";
import { identityMapper, m3uMapper, stalkerMapper, type IdMapper } from "./idmap";

/** Clé du réglage qui garde le tvg-id de CHAQUE entrée de la playlist (même celles retirées du catalogue : chaînes masquées). */
export const m3uTvgKey = (sourceId: number) => `m3u.tvg.${sourceId}`;
export interface M3uTvgStore { cid: number; tvg: string }

/** tvg-id de chaque entrée -> forme stockée (une ligne par entrée, vide = sans tvg-id). */
export const packTvgIds = (ids: readonly (string | null | undefined)[]): string => ids.map((t) => (t && t.trim() ? t : "")).join("\n");
export const unpackTvgIds = (s: string): string[] => (s === "" ? [] : s.split("\n"));

export async function mapperFor(s: Source): Promise<IdMapper> {
  if (s.type === "m3u" && s.id != null) {
    const st = await getSetting<M3uTvgStore | null>(m3uTvgKey(s.id), null);
    // Absent (catalogue chargé par une version précédente) ou d'une autre génération : pas de traduction, comportement historique.
    if (st && st.cid === s.cid && st.tvg) return m3uMapper(unpackTvgIds(st.tvg));
    return identityMapper;
  }
  if (s.type === "stalker" && s.cid) {
    const portalOf = new Map<number, string>();
    await db.series.where("[sourceId+ord]").between([s.cid, -1], [s.cid, Infinity]).each((r) => { if (r.portalId) portalOf.set(r.seriesId, r.portalId); });
    return stalkerMapper(portalOf);
  }
  return identityMapper;
}
