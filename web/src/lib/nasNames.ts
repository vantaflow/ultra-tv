// Reconnaissance des films et des épisodes d'après le NOM DE FICHIER (et ses dossiers) : fonctions pures, sans disque ni réseau.
// Portage exact de android-native/.../data/nas/NasNames.kt : toute évolution se fait des deux côtés (mêmes cas de test).
//
// Exemples : « Titre (2019).mkv », « Titre.2019.1080p.x264-GRP.mkv », « Série S01E02 », « Série 1x02 », « Série/Saison 1/02 - Titre.mkv ».

export interface NasParsed {
  kind: "movie" | "episode";
  /** Titre du film, ou de l'épisode (peut être vide). */
  title: string;
  /** Année de sortie (ou de la série) si elle figure dans le nom ou le dossier. */
  year: number | null;
  seriesTitle?: string;
  season?: number;
  episode?: number;
}

export const NAS_VIDEO_EXTENSIONS = new Set(["mkv", "mp4", "m4v", "avi", "mov", "wmv", "ts", "m2ts", "mpg", "mpeg", "webm", "flv", "divx", "ogv"]);

export const extensionOf = (name: string): string => {
  const i = name.lastIndexOf(".");
  return i < 0 ? "" : name.slice(i + 1).toLowerCase();
};

export const isVideoName = (name: string): boolean => NAS_VIDEO_EXTENSIONS.has(extensionOf(name));

const SAMPLE = /(^|[\s._\-[(])(sample|trailer|teaser)([\s._\-\])]|$)/u;

/** Fichier à ignorer : échantillon, bande-annonce, fichier caché ou métadonnée de NAS. */
export function isIgnoredFile(name: string): boolean {
  const n = name.toLowerCase();
  if (n.startsWith(".")) return true;
  const dot = n.lastIndexOf(".");
  return SAMPLE.test(dot < 0 ? n : n.slice(0, dot));
}

const IGNORED_FOLDERS = new Set(["lost+found", "system volume information", "extras", "extra", "featurettes", "behind the scenes", "sample", "samples", "trailers", "subs", "subtitles"]);

/** Dossiers que le parcours ne descend jamais (corbeilles, métadonnées de NAS, extras). */
export function isIgnoredFolder(name: string): boolean {
  const n = name.toLowerCase();
  return n.startsWith(".") || n.startsWith("@") || n.startsWith("#") || n.startsWith("$") || IGNORED_FOLDERS.has(n);
}

const YEAR = /(?<![\p{L}\p{N}])(19\d{2}|20\d{2})(?![\p{L}\p{N}])/gu;
const SXXEXX = /(?<![\p{L}\p{N}])s(\d{1,2})[ ._-]?e(\d{1,3})(?:[ ._-]?(?:e|-e?)\d{1,3})*(?![\p{L}\p{N}])/iu;
const NXNN = /(?<![\p{L}\p{N}])(\d{1,2})x(\d{2,3})(?![\p{L}\p{N}])/iu;
const LONG_FORM = /(?<![\p{L}\p{N}])(?:season|saison|staffel|temporada|stagione)[ ._-]*(\d{1,2})[ ._-]*(?:episode|épisode|ep|e|folge|capitulo)[ ._-]*(\d{1,3})/iu;
const SEASON_FOLDER = /^\s*(?:season|saison|staffel|temporada|stagione|s)[ ._-]*(\d{1,2})\s*$/iu;
const LEADING_EPISODE = /^\s*(?:episode|épisode|ep|e)?[ ._-]*(\d{1,3})(?![\p{L}\p{N}])[ ._-]*/iu;
const TRAILING_PART = /[ ._-]*\b(?:cd|disc|disk|part|pt)[ ._-]?\d\s*$/iu;
const BRACKETS = /\[[^\]]*\]|\{[^}]*\}/gu;
const MULTI_SPACE = /\s{2,}/gu;
const EDGE_JUNK = /^[\s\-–.]+|[\s\-–.([]+$/gu;

/** Mots de « release » sans ambiguïté : le titre s'arrête au premier d'entre eux. */
const STRONG = new Set([
  "480p", "576p", "720p", "1080p", "1080i", "2160p", "4320p", "4k", "uhd", "fhd", "8k",
  "x264", "x265", "h264", "h265", "hevc", "xvid", "divx", "vp9", "av1", "10bit", "hi10p",
  "bluray", "bdrip", "brrip", "bdremux", "remux", "dvdrip", "dvdscr", "webrip", "webdl", "hdtv", "hdrip", "hdcam", "pdtv",
  "amzn", "dsnp", "hmax", "atvp", "pcok",
  "aac", "ac3", "eac3", "dts", "dtshd", "ddp5", "dd5", "dd51", "atmos", "truehd", "flac",
  "multi", "vff", "vfq", "vf2", "vfi", "vostfr", "truefrench", "subfrench",
  "proper", "repack", "rerip", "extended", "unrated", "uncut", "remastered", "imax", "hdr", "hdr10", "hdr10plus",
]);

const squash = (s: string) => s.toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");

/** Vrai si le mot [tok] (suivi de [next]) marque le début des mentions techniques de la release. */
function isJunk(tok: string, next: string | undefined): boolean {
  const k = squash(tok);
  if (!k) return false;
  if (STRONG.has(k)) return true;
  const nk = next ? squash(next) : "";
  switch (k) {
    case "web": return nk === "dl" || nk === "rip";
    case "blu": return nk === "ray";
    case "h": return nk === "264" || nk === "265";
    case "dd": return nk === "5" || nk === "2" || nk === "7";
    case "vf": return true;
    default: return false;
  }
}

/** Mots d'un fragment : points et tirets bas deviennent des espaces ; les tirets internes (« Spider-Man ») sont gardés. */
const tokens = (s: string): string[] => s.replace(/_/g, " ").replace(/\./g, " ").split(" ").filter(Boolean);

/** Dernière occurrence d'une année dans [s] qui n'est pas au tout début (« 2012.2009.mkv » : titre 2012, année 2009). */
function lastYear(s: string, notAtStart: boolean): { year: number; at: number } | null {
  let best: { year: number; at: number } | null = null;
  for (const m of s.matchAll(YEAR)) {
    const at = m.index ?? 0;
    if (notAtStart && !/[\p{L}\p{N}]/u.test(s.slice(0, at))) continue;
    best = { year: Number(m[1]), at };
  }
  return best;
}

/**
 * Nettoie un fragment en titre lisible : retire les crochets, coupe à la dernière année puis au premier mot de release, retire un
 * numéro de disque final. [allowFirstJunk] : le premier mot peut être un mot de release (fragment qui suit une marque d'épisode).
 */
export function cleanNasTitle(raw: string, yearHint = true, allowFirstJunk = false): { title: string; year: number | null } {
  const flat = raw.replace(BRACKETS, " ").replace(/_/g, " ").replace(/\./g, " ");
  let year: number | null = null;
  let cut = flat.length;
  if (yearHint) {
    const y = lastYear(flat, true);
    if (y) { year = y.year; cut = y.at; }
  }
  const toks = tokens(flat.slice(0, cut));
  let end = toks.length;
  for (let i = 0; i < toks.length; i++) {
    if ((i > 0 || allowFirstJunk) && isJunk(toks[i]!, toks[i + 1])) { end = i; break; }
  }
  let title = toks.slice(0, end).join(" ");
  title = title.replace(TRAILING_PART, "");
  title = title.replace(EDGE_JUNK, "").replace(MULTI_SPACE, " ").trim();
  return { title, year };
}

const GENERIC = new Set(["movie", "video", "film", "main", "title", "cd1", "cd2", "disc1", "disc2", "videots", "bdmv", "stream", "index"]);

const isSeasonFolder = (f: string) => SEASON_FOLDER.test(f);

function seriesFromFolders(folders: string[]): string {
  const f = [...folders].reverse().find((x) => !isSeasonFolder(x));
  return f ? cleanNasTitle(f).title : "";
}

function yearFromFolders(folders: string[]): number | null {
  const f = [...folders].reverse().find((x) => !isSeasonFolder(x));
  return f ? cleanNasTitle(f).year : null;
}

/**
 * Analyse un fichier. [fileName] = nom avec extension ; [folders] = dossiers parents, du plus haut au plus proche du fichier,
 * relatifs à la racine du partage (jamais le nom du partage lui-même).
 */
export function parseNasName(fileName: string, folders: string[] = []): NasParsed {
  const base = isVideoName(fileName) ? fileName.slice(0, fileName.lastIndexOf(".")) : fileName;
  const parent = folders[folders.length - 1] ?? "";
  const folderSeason = SEASON_FOLDER.exec(parent);
  const seasonFromFolder = folderSeason ? Number(folderSeason[1]) : null;

  // 1) Marque d'épisode dans le nom : S01E02, « Season 1 Episode 2 », 1x02.
  const m = SXXEXX.exec(base) ?? LONG_FORM.exec(base) ?? NXNN.exec(base);
  if (m) {
    const before = cleanNasTitle(base.slice(0, m.index), true);
    const series = before.title || seriesFromFolders(folders);
    const epTitle = cleanNasTitle(base.slice(m.index + m[0].length), false, true).title;
    return { kind: "episode", title: epTitle, year: before.year ?? yearFromFolders(folders), seriesTitle: series, season: Number(m[1]), episode: Number(m[2]) };
  }

  // 2) Dossier « Saison N » : « 02 - Titre.mkv », « E02.mkv ».
  if (seasonFromFolder !== null) {
    const lm = LEADING_EPISODE.exec(base);
    if (lm) {
      const rest = cleanNasTitle(base.slice(lm[0].length), false, true).title;
      return { kind: "episode", title: rest, year: yearFromFolders(folders), seriesTitle: seriesFromFolders(folders), season: seasonFromFolder, episode: Number(lm[1]) };
    }
  }

  // 3) Film.
  let { title, year } = cleanNasTitle(base);
  if (!title || GENERIC.has(squash(title))) {
    // Nom vide ou générique : le dossier porte le titre (« Titre (2019)/movie.mkv »).
    const f = cleanNasTitle(parent);
    if (f.title) { title = f.title; if (year === null) year = f.year; }
  } else if (year === null) {
    // Année seulement dans le dossier (« Titre (2019)/Titre.mkv »).
    const f = cleanNasTitle(parent);
    if (f.year !== null && squash(f.title) === squash(title)) year = f.year;
  }
  if (!title) title = base.replace(/\./g, " ").replace(/_/g, " ").trim();
  return { kind: "movie", title, year };
}

/** Clé de regroupement d'une série : titre sans accents ni ponctuation (« La Casa de Papel » = « la.casa.de.papel »). */
export const nasSeriesKey = (title: string): string => title.toLowerCase().normalize("NFD").replace(/\p{Mn}+/gu, "").replace(/[^\p{L}\p{N}]/gu, "");
