// Index de disponibilité : matchKey des titres du catalogue de la source active. Construit hors du rendu,
// par tranches (≈ 50 000 lignes) avec cession du fil entre chaque tranche, mémorisé par source + génération de catalogue (cid).

import { useEffect, useState } from "react";
import { db } from "@/db/db";
import type { Source } from "@/db/types";
import type { TraktItem } from "@/cloud/client";
import { addToIndex, firstTokens, type CatIndex } from "./availability";

export interface CatalogIndex { movies: CatIndex; series: CatIndex }
const CHUNK = 4000;
const cache = new Map<string, Promise<CatalogIndex>>();
const yieldUi = () => new Promise<void>((r) => setTimeout(r, 0));

/** Pré-filtre : premiers mots des clés voulues (null = pas de filtre, indexation complète). */
export function wantedTokens(lists: TraktItem[][]): Set<string> { return firstTokens(lists); }
const sig = (w: Set<string> | null) => (w ? `${w.size}:${[...w].sort().join(",")}` : "*");

export function buildCatalogIndex(cid: number, wanted: Set<string> | null = null): Promise<CatalogIndex> {
  const key = `${cid}|${sig(wanted)}`;
  const hit = cache.get(key);
  if (hit) return hit;
  const p = (async () => {
    const movies: CatIndex = new Map();
    const series: CatIndex = new Map();
    for (const [table, idx, ref] of [[db.movies, movies, "streamId"], [db.series, series, "seriesId"]] as const) {
      let from = -1;
      for (;;) {
        // `ord` est unique par source : borne basse exclusive = reprise sans doublon ni saut.
        const rows = await (table as typeof db.movies).where("[sourceId+ord]").between([cid, from], [cid, Infinity], false, true).limit(CHUNK).toArray();
        for (const r of rows) addToIndex(idx, [r.title, r.name], (r as unknown as Record<string, number>)[ref]!, r.year, wanted);
        if (rows.length < CHUNK) break;
        from = rows[rows.length - 1]!.ord;
        await yieldUi();
      }
    }
    return { movies, series };
  })();
  p.catch(() => cache.delete(key));
  cache.set(key, p);
  // Une seule génération en mémoire par appel : on oublie les anciennes.
  for (const k of [...cache.keys()]) if (k !== key) cache.delete(k);
  return p;
}

/** null tant que l'index se construit (ou sans catalogue). */
export function useCatalogIndex(source: Source | undefined, wanted: Set<string> | null): CatalogIndex | null {
  const cid = source?.cid ?? 0;
  const [st, setSt] = useState<{ cid: number; idx: CatalogIndex; w: Set<string> | null } | null>(null);
  useEffect(() => {
    if (!cid || !wanted) { setSt(null); return; }
    let live = true;
    void buildCatalogIndex(cid, wanted).then((idx) => { if (live) setSt({ cid, idx, w: wanted }); }, () => {});
    return () => { live = false; };
  }, [cid, wanted]);
  return st && st.cid === cid && st.w === wanted ? st.idx : null;
}
