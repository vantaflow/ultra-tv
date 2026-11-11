// Notes IMDb / Rotten Tomatoes des fiches (Worker → OMDb). Seulement sur le bureau appairé (le Worker n'émet pas
// d'en-têtes CORS) ; sinon, et en cas d'échec, la fiche garde la note fournie par la source.
import { useEffect, useState } from "react";
import { fetchRatings, type ExternalRatings } from "@/cloud/client";
import { cloudAvailable } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";

/** Libellés de la ligne méta : « IMDb 8.8 » (sinon « ★ 7.4 » de la source), puis « RT 87% ». */
export function ratingLabels(r: ExternalRatings | null | undefined, fallback?: number | null): string[] {
  const out: string[] = [];
  if (r?.imdb && r.imdb > 0 && r.imdb <= 10) out.push(`IMDb ${r.imdb.toFixed(1)}`);
  else if (fallback && fallback > 0 && fallback <= 10) out.push(`★ ${fallback.toFixed(1)}`);
  if (r?.rt != null && r.rt >= 0 && r.rt <= 100) out.push(`RT ${r.rt}%`);
  return out;
}

const cache = new Map<string, ExternalRatings | null>();

export function useRatings(kind: "movie" | "tv", tmdb: number | undefined, title: string | undefined, year: number | null | undefined): ExternalRatings | null {
  const paired = useCloud((s) => s.paired);
  const worker = useCloud((s) => s.worker);
  const key = `${kind}:${tmdb ?? ""}:${tmdb ? "" : `${title ?? ""}:${year ?? ""}`}`;
  const [r, setR] = useState<ExternalRatings | null>(() => cache.get(key) ?? null);
  useEffect(() => {
    if (cache.has(key)) { setR(cache.get(key) ?? null); return; }
    setR(null);
    if (!paired || !cloudAvailable() || (!tmdb && !title)) return;
    let dead = false;
    void (async () => {
      try {
        const token = await getToken();
        if (!token) return;
        const v = await fetchRatings(worker, token, { kind, tmdb, title, year: year ?? undefined });
        cache.set(key, v);
        if (!dead) setR(v);
      } catch { /* réseau, 401, 429 : on garde la note de la source */ }
    })();
    return () => { dead = true; };
  }, [key, paired, worker, kind, tmdb, title, year]);
  return r;
}
