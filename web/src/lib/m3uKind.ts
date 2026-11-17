// Classement des entrées d'une playlist M3U : direct, film ou épisode de série (fonctions pures, testées).
// Une playlist 100 % VOD (extensions .mp4 / .mkv…, chemins Xtream /movie/ et /series/) était rangée dans le Direct et lue comme un flux continu.

export type M3uKind = "live" | "movie" | "series";

const VOD_EXT = new Set(["mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg", "mts", "m2ts", "divx", "vob", "3gp"]);
/** Épisode : S01E02, S1 E2, 1x02, « Saison 1 Épisode 2 ». */
const EPISODE_RE = /\bS(\d{1,2})\s*[._ -]?\s*E(\d{1,3})\b|\b(\d{1,2})x(\d{2,3})\b|\b(?:saison|season)\s*(\d{1,2})\W+(?:[ée]pisode|episode|ep)\s*(\d{1,3})\b/i;

export interface M3uKindInput {
  name: string;
  url: string;
  groupTitle?: string | null;
  rawAttributes?: Record<string, string>;
}

/** Chemin de l'adresse, sans requête ni fragment, en minuscules. */
function pathOf(url: string): string {
  try { return new URL(url).pathname.toLowerCase(); } catch { return url.split(/[?#]/)[0]!.toLowerCase(); }
}

/** Extension du fichier de l'adresse (sans point), ou "". */
export function urlExtension(url: string): string {
  const m = /\.([a-z0-9]{2,5})$/.exec(pathOf(url));
  return m ? m[1]! : "";
}

/** Saison et numéro d'épisode lus dans un titre ; null s'il n'y en a pas. */
export function episodeOf(name: string): { season: number; episode: number } | null {
  const m = EPISODE_RE.exec(name);
  if (!m) return null;
  return { season: Number(m[1] ?? m[3] ?? m[5]), episode: Number(m[2] ?? m[4] ?? m[6]) };
}

/** Classe une entrée : attribut `tvg-type`, chemin Xtream, extension du fichier, puis nom du groupe. */
export function classifyM3uEntry(e: M3uKindInput): M3uKind {
  const type = (e.rawAttributes?.["tvg-type"] ?? e.rawAttributes?.["type"] ?? "").trim().toLowerCase();
  if (type === "live" || type === "tv" || type === "channel") return "live";
  if (type === "series" || type === "serie" || type === "episode" || type === "show") return "series";
  if (type === "movie" || type === "movies" || type === "vod" || type === "film") return episodeOf(e.name) ? "series" : "movie";
  const path = pathOf(e.url);
  if (/\/series\/[^/]+\/[^/]+\/[^/]+$/.test(path)) return "series";
  if (/\/movie\/[^/]+\/[^/]+\/[^/]+$/.test(path)) return "movie";
  if (/\/live\/[^/]+\/[^/]+\/[^/]+$/.test(path)) return "live";
  const ext = urlExtension(e.url);
  if (ext === "m3u8" || ext === "ts") {
    // Flux continu par défaut ; seul un groupe explicitement VOD le range en films.
    return /^(?:vod|movies?|films?)\b/i.test((e.groupTitle ?? "").trim()) && !/^(?:live|tv)\b/i.test(e.name) ? "movie" : "live";
  }
  if (VOD_EXT.has(ext)) return episodeOf(e.name) ? "series" : "movie";
  const g = (e.groupTitle ?? "").trim();
  if (/^(?:s[ée]ries?|tv[\s-]?shows?|shows?)\b/i.test(g)) return "series";
  if (/^(?:vod|movies?|films?)\b/i.test(g)) return episodeOf(e.name) ? "series" : "movie";
  return "live";
}

/** Titre de série d'un épisode : le nom sans le marqueur d'épisode ni le titre qui suit. */
export function seriesTitleOf(name: string): string {
  const m = EPISODE_RE.exec(name);
  const head = m ? name.slice(0, m.index) : name;
  return head.replace(/[\s._:–—-]+$/g, "").trim() || name.trim();
}
