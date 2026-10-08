// Index de disponibilité : matchKey des titres du catalogue de la source active. Construit hors du rendu,
// par tranches (≈ 50 000 lignes) avec cession du fil entre chaque tranche, mémorisé par source + génération de catalogue (cid).

import { useEffect, useState } from "react";
import { db } from "@/db/db";
import type { Source } from "@/db/types";
import { addToIndex, type CatIndex } from "./availability";

export interface CatalogIndex { movies: CatIndex; series: CatIndex }
const CHUNK = 4000;
const cache = new Map<string, Promise<CatalogIndex>>();
const yieldUi = () => new Promise<void>((r) => setTimeout(r, 0));

export function buildCatalogIndex(cid: number): Promise<CatalogIndex> {
  const key = String(cid);
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
        for (const r of rows) addToIndex(idx, [r.title, r.name], (r as unknown as Record<string, number>)[ref]!, r.year);
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
export function useCatalogIndex(source: Source | undefined): CatalogIndex | null {
  const cid = source?.cid ?? 0;
  const [st, setSt] = useState<{ cid: number; idx: CatalogIndex } | null>(null);
  useEffect(() => {
    if (!cid) { setSt(null); return; }
    let live = true;
    void buildCatalogIndex(cid).then((idx) => { if (live) setSt({ cid, idx }); }, () => {});
    return () => { live = false; };
  }, [cid]);
  return st && st.cid === cid ? st.idx : null;
}
