// Clé YouTube de la bande-annonce d'une fiche : celle du fournisseur (`youtube_trailer`), sinon, sur le bureau appairé,
// la bande-annonce TMDB du titre (via le proxy du Worker). Sans l'un ni l'autre : null, donc pas de bouton.
// Aucune recherche YouTube.
import { useEffect, useState } from "react";
import { cloudAvailable, tmdbGet } from "@/cloud/client";
import { getToken, useCloud } from "@/cloud/service";
import type { Lang } from "@/i18n/lang";
import { findTmdbTrailer, youtubeKey } from "@/lib/youtube";
import { usePrefs } from "@/state/prefs";

/** Langue demandée à TMDB (le proxy n'accepte que `xx` ou `xx-YY`). */
const TMDB_LANG: Record<Lang, string> = { fr: "fr-FR", en: "en-US", es: "es-ES", ar: "ar", pl: "pl-PL" };

const cache = new Map<string, string | null>();

export function useTrailerKey(
  kind: "movie" | "tv", provided: unknown, tmdb: number | undefined, title: string | undefined, year: number | null | undefined,
): string | null {
  const fromSource = youtubeKey(provided);
  const paired = useCloud((s) => s.paired);
  const worker = useCloud((s) => s.worker);
  const lang = usePrefs((s) => s.lang);
  const cacheKey = `${kind}:${tmdb ?? ""}:${tmdb ? "" : `${title ?? ""}:${year ?? ""}`}:${lang}`;
  const [found, setFound] = useState<string | null>(() => cache.get(cacheKey) ?? null);
  useEffect(() => {
    if (fromSource) return;
    if (cache.has(cacheKey)) { setFound(cache.get(cacheKey) ?? null); return; }
    setFound(null);
    if (!paired || !cloudAvailable() || (!tmdb && !title)) return;
    let dead = false;
    void (async () => {
      const token = await getToken().catch(() => "");
      if (!token) return;
      const key = await findTmdbTrailer((p) => tmdbGet(worker, token, p), { kind, tmdb, title, year, langs: [TMDB_LANG[lang]] });
      cache.set(cacheKey, key);
      if (!dead) setFound(key);
    })();
    return () => { dead = true; };
  }, [fromSource, cacheKey, paired, worker, kind, tmdb, title, year, lang]);
  return fromSource ?? found;
}
