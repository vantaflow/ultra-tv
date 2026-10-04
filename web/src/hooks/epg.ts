import { useEffect, useState } from "react";
import { nowNext } from "@/db/queries";
import type { ProgramRow, Source } from "@/db/types";
import { currentTransport } from "@/net/transport";
import { b64text, shortEpg } from "@/net/xtream";
import { credsOf } from "@/sync/core";

export type NN = { now?: ProgramRow; next?: ProgramRow };
const shortCache = new Map<string, { at: number; nn: NN }>();

/** Repli : guide court du serveur Xtream (quand le XMLTV complet n'est pas chargé). */
async function fetchShort(source: Source, streamId: number): Promise<NN> {
  const key = `${source.id}:${streamId}`;
  const hit = shortCache.get(key);
  if (hit && Date.now() - hit.at < 5 * 60_000) return hit.nn;
  let nn: NN = {};
  try {
    const r = await shortEpg(await currentTransport(), credsOf(source), streamId, 4);
    const rows: ProgramRow[] = (r.epg_listings ?? []).map((e) => ({
      sourceId: source.cid, epg: "", start: Number(e.start_timestamp) * 1000, end: Number(e.stop_timestamp) * 1000,
      title: b64text(e.title), desc: b64text(e.description),
    })).filter((p) => p.end > p.start);
    const now = Date.now();
    const cur = rows.find((p) => p.start <= now && p.end > now);
    nn = { now: cur, next: rows.find((p) => p.start >= (cur?.end ?? now)) };
  } catch { /* guide indisponible */ }
  shortCache.set(key, { at: Date.now(), nn });
  return nn;
}

/** Programme en cours et suivant d'une chaîne, rafraîchis toutes les 30 s. */
export function useNowNext(source: Source | undefined, epg: string | null | undefined, streamId: number | undefined, allowShort = true): NN {
  const [nn, setNn] = useState<NN>({});
  useEffect(() => {
    let dead = false;
    setNn({});
    if (!source || !source.cid || streamId == null) return;
    const run = async () => {
      let r: NN = {};
      if (epg) r = (await nowNext(source.cid, [epg])).get(epg) ?? {};
      if (!r.now && allowShort && source.type === "xtream") r = await fetchShort(source, streamId);
      if (!dead) setNn(r);
    };
    void run();
    const t = setInterval(run, 30_000);
    return () => { dead = true; clearInterval(t); };
  }, [source?.id, source?.cid, epg, streamId, allowShort]);
  return nn;
}
