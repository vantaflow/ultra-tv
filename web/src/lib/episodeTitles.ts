// Titres d'épisodes TMDB : les sources IPTV donnent souvent « Episode 3 », « E03 », « S01E03 », rien, ou le nom de la
// série. Logique pure (sans réseau) : détection d'un titre générique, lecture d'une saison TMDB et fusion avec les épisodes
// de la source. Le titre TMDB ne remplace que les titres génériques ; résumé et vignette ne complètent que les absents.

/** Épisode TMDB d'une saison (`tv/{id}/season/{n}`), revalidé. */
export interface TmdbEpisode { number: number; name: string | null; overview: string | null; still: string | null }

const isObj = (v: unknown): v is Record<string, unknown> => !!v && typeof v === "object" && !Array.isArray(v);
const str = (v: unknown): string | null => (typeof v === "string" && v.trim() ? v.trim() : null);

/** Base des vignettes TMDB (la largeur 300 suffit aux miniatures d'épisodes). */
export const TMDB_STILL = "https://image.tmdb.org/t/p/w300";

/** Minuscules, sans accents, chiffres arabes-indiens convertis, espaces normalisés. */
function norm(s: string): string {
  return s
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660))
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0))
    .normalize("NFD").replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[_\s]+/g, " ")
    .trim();
}

/** Ponctuation et séparateurs autour d'un titre (« - », « : », « | », « . », crochets, « # »). */
const trimSep = (s: string) => s.replace(/^[\s\-–—:|.,·•#()[\]]+|[\s\-–—:|.,·•#()[\]]+$/g, "");

// Mots « épisode » (fr/en/es/pl/de/it/pt, abrégés) et « saison » ; arabe à part (pas de \b avec l'alphabet arabe).
const EP_WORD = "(?:episode|episodio|epizod|ep|e|capitulo|cap|chapitre|odcinek|odc|folge|puntata|n|no|nr|#)";
const SEASON_WORD = "(?:season|saison|temporada|sezon|staffel|stagione|s)";
const NUM = "\\d{1,4}";
const LATIN = new RegExp(
  `^(?:${SEASON_WORD}\\.?\\s*${NUM}\\s*[-–:.,x]?\\s*)?(?:${EP_WORD}\\.?\\s*)?${NUM}$|^${NUM}\\s*x\\s*${NUM}$`,
);
const ARABIC = new RegExp(`^(?:(?:ال)?موسم\\s*${NUM}\\s*[-–:،]?\\s*)?(?:ال)?حلق[ةه]\\s*(?:رقم\\s*)?${NUM}$|^${NUM}\\s*(?:ال)?حلق[ةه]$`);

/**
 * Vrai si le titre d'épisode ne dit rien de plus que son numéro : vide, chiffres seuls, « Episode 3 », « Épisode 03 »,
 * « Ep. 3 », « E03 », « S01E03 », « 1x03 », « Capítulo 3 », « حلقة 3 », « Odcinek 3 », le nom de la série, ou le nom de la
 * série suivi d'un numéro / code.
 */
export function isGenericEpisodeTitle(title: string | null | undefined, seriesName?: string | null, number?: number | null): boolean {
  let t = trimSep(norm(title ?? ""));
  if (!t) return true;
  const series = trimSep(norm(seriesName ?? ""));
  if (series) {
    if (t === series) return true;
    if (t.startsWith(series)) {
      const rest = trimSep(t.slice(series.length));
      if (!rest) return true;
      t = rest;
    }
  }
  if (number != null && t === String(number)) return true;
  const compact = t.replace(/\s+/g, " ");
  return LATIN.test(compact) || ARABIC.test(compact);
}

/** Épisodes d'une réponse TMDB `tv/{id}/season/{n}`, indexés par numéro (entrées invalides ignorées). */
export function parseTmdbSeason(body: unknown): TmdbEpisode[] {
  const list = isObj(body) && Array.isArray(body.episodes) ? body.episodes : [];
  const out: TmdbEpisode[] = [];
  for (const e of list) {
    if (!isObj(e)) continue;
    const n = e.episode_number;
    if (typeof n !== "number" || !Number.isInteger(n) || n < 0) continue;
    const still = str(e.still_path);
    out.push({
      number: n, name: str(e.name), overview: str(e.overview),
      still: still && /^\/[\w.-]+$/.test(still) ? TMDB_STILL + still : null,
    });
  }
  return out;
}

/** Champs d'un épisode de la source concernés par la fusion. */
export interface EpisodeFields { num: number; title: string; plot: string | null; img: string | null }

/**
 * Fusionne un épisode de la source avec l'épisode TMDB de même numéro : titre TMDB seulement si celui de la source est
 * générique (et que celui de TMDB ne l'est pas : TMDB renvoie « Épisode 3 » sans traduction) ; résumé et vignette seulement
 * s'ils manquent. Renvoie l'objet d'origine s'il n'y a rien à changer.
 */
export function mergeEpisode<E extends EpisodeFields>(ep: E, tmdb: TmdbEpisode | undefined, seriesName?: string | null): E {
  if (!tmdb) return ep;
  const title = tmdb.name && isGenericEpisodeTitle(ep.title, seriesName, ep.num) && !isGenericEpisodeTitle(tmdb.name, seriesName, ep.num) ? tmdb.name : ep.title;
  const plot = ep.plot ?? tmdb.overview;
  const img = ep.img ?? tmdb.still;
  if (title === ep.title && plot === ep.plot && img === ep.img) return ep;
  return { ...ep, title, plot, img };
}

/** Fusion d'une liste d'épisodes avec une saison TMDB (même liste si rien ne change). */
export function mergeEpisodes<E extends EpisodeFields>(eps: E[], tmdb: TmdbEpisode[] | null | undefined, seriesName?: string | null): E[] {
  if (!tmdb?.length) return eps;
  const by = new Map(tmdb.map((e) => [e.number, e]));
  let changed = false;
  const out = eps.map((e) => { const m = mergeEpisode(e, by.get(e.num), seriesName); if (m !== e) changed = true; return m; });
  return changed ? out : eps;
}
