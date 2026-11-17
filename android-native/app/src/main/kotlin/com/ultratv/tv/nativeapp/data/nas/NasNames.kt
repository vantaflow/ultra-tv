package com.ultratv.tv.nativeapp.data.nas

/**
 * Reconnaissance des films et des épisodes d'après le NOM DE FICHIER (et les dossiers qui le contiennent) : fonction pure,
 * sans accès disque ni réseau. Même logique que `web/src/lib/nasNames.ts` (bureau) : toute évolution se fait des deux côtés.
 *
 * Exemples : « Titre (2019).mkv », « Titre.2019.1080p.x264-GRP.mkv », « Série S01E02 », « Série 1x02 », « Série/Saison 1/02 - Titre.mkv ».
 */
object NasNames {
    enum class Kind { MOVIE, EPISODE }

    /**
     * Résultat : [title] = titre du film, ou de l'épisode (peut être vide) ; [seriesTitle], [season] et [episode] pour un épisode.
     * [year] = année de sortie (ou de la série) si elle figure dans le nom ou le dossier.
     */
    data class Parsed(
        val kind: Kind, val title: String, val year: Int?,
        val seriesTitle: String? = null, val season: Int? = null, val episode: Int? = null,
    )

    /** Extensions vidéo prises en compte par le parcours. */
    val VIDEO_EXTENSIONS: Set<String> = setOf("mkv", "mp4", "m4v", "avi", "mov", "wmv", "ts", "m2ts", "mpg", "mpeg", "webm", "flv", "divx", "ogv")

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isVideo(name: String): Boolean = extensionOf(name) in VIDEO_EXTENSIONS

    /** Fichier à ignorer : échantillon, bande-annonce, fichier caché ou métadonnée de NAS. */
    fun isIgnored(name: String): Boolean {
        val n = name.lowercase()
        if (n.startsWith(".")) return true
        return SAMPLE.containsMatchIn(n.substringBeforeLast('.'))
    }

    private val IGNORED_FOLDERS = setOf(
        "lost+found", "system volume information", "extras", "extra", "featurettes", "behind the scenes", "sample", "samples", "trailers", "subs", "subtitles",
    )

    /** Dossiers que le parcours ne descend jamais (corbeilles, métadonnées de NAS, extras). */
    fun isIgnoredFolder(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith(".") || n.startsWith("@") || n.startsWith("#") || n.startsWith("$") || n in IGNORED_FOLDERS
    }

    private val SAMPLE = Regex("""(^|[\s._\-\[(])(sample|trailer|teaser)([\s._\-\])]|$)""")
    private val YEAR = Regex("""(?<![\p{L}\p{N}])(19\d{2}|20\d{2})(?![\p{L}\p{N}])""")
    private val SXXEXX = Regex("""(?i)(?<![\p{L}\p{N}])s(\d{1,2})[ ._\-]?e(\d{1,3})(?:[ ._\-]?(?:e|-e?)\d{1,3})*(?![\p{L}\p{N}])""")
    private val NXNN = Regex("""(?i)(?<![\p{L}\p{N}])(\d{1,2})x(\d{2,3})(?![\p{L}\p{N}])""")
    private val LONG_FORM = Regex("""(?i)(?<![\p{L}\p{N}])(?:season|saison|staffel|temporada|stagione)[ ._\-]*(\d{1,2})[ ._\-]*(?:episode|épisode|ep|e|folge|capitulo)[ ._\-]*(\d{1,3})""")
    private val SEASON_FOLDER = Regex("""(?i)^\s*(?:season|saison|staffel|temporada|stagione|s)[ ._\-]*(\d{1,2})\s*$""")
    private val LEADING_EPISODE = Regex("""(?i)^\s*(?:episode|épisode|ep|e)?[ ._\-]*(\d{1,3})(?![\p{L}\p{N}])[ ._\-]*""")
    private val TRAILING_PART = Regex("""(?i)[ ._\-]*\b(?:cd|disc|disk|part|pt)[ ._\-]?\d\s*$""")
    private val BRACKETS = Regex("""\[[^\]]*]|\{[^}]*}""")
    private val MULTI_SPACE = Regex("""\s{2,}""")
    private val EDGE_JUNK = Regex("""^[\s\-–.]+|[\s\-–.(\[]+$""")

    /** Mots de « release » sans ambiguïté : le titre s'arrête au premier d'entre eux. Comparés en minuscules, lettres et chiffres seulement. */
    private val STRONG = setOf(
        "480p", "576p", "720p", "1080p", "1080i", "2160p", "4320p", "4k", "uhd", "fhd", "8k",
        "x264", "x265", "h264", "h265", "hevc", "xvid", "divx", "vp9", "av1", "10bit", "hi10p",
        "bluray", "bdrip", "brrip", "bdremux", "remux", "dvdrip", "dvdscr", "webrip", "webdl", "hdtv", "hdrip", "hdcam", "pdtv",
        "amzn", "dsnp", "hmax", "atvp", "pcok",
        "aac", "ac3", "eac3", "dts", "dtshd", "ddp5", "dd5", "dd51", "atmos", "truehd", "flac",
        "multi", "vff", "vfq", "vf2", "vfi", "vostfr", "truefrench", "subfrench",
        "proper", "repack", "rerip", "extended", "unrated", "uncut", "remastered", "imax", "hdr", "hdr10", "hdr10plus",
    )

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Vrai si le mot [tok] (suivi de [next]) marque le début des mentions techniques de la release. */
    private fun isJunk(tok: String, next: String?): Boolean {
        val k = squash(tok)
        if (k.isEmpty()) return false
        if (k in STRONG) return true
        val nk = next?.let(::squash).orEmpty()
        return when (k) {
            "web" -> nk == "dl" || nk == "rip"
            "blu" -> nk == "ray"
            "h" -> nk == "264" || nk == "265"
            "dd" -> nk == "5" || nk == "2" || nk == "7"
            "vf" -> true
            else -> false
        }
    }

    /** Mots d'un fragment : points et tirets bas deviennent des espaces ; les tirets internes (« Spider-Man ») sont gardés. */
    private fun tokens(s: String): List<String> = s.replace('_', ' ').replace('.', ' ').split(' ').filter { it.isNotEmpty() }

    /**
     * Nettoie un fragment en titre lisible : retire les crochets, coupe à la dernière année puis au premier mot de release,
     * retire un numéro de disque final. Rend (titre, année trouvée). [allowFirstJunk] : le premier mot peut être un mot de release
     * (fragment qui suit une marque d'épisode).
     */
    fun cleanTitle(raw: String, yearHint: Boolean = true, allowFirstJunk: Boolean = false): Pair<String, Int?> {
        val flat = BRACKETS.replace(raw, " ").replace('_', ' ').replace('.', ' ')
        // L'année : la DERNIÈRE occurrence qui n'est pas au tout début (« 2012.2009.mkv » : titre 2012, année 2009).
        var year: Int? = null
        var cut = flat.length
        if (yearHint) {
            val pick = YEAR.findAll(flat).lastOrNull { flat.substring(0, it.range.first).any { c -> c.isLetterOrDigit() } }
            if (pick != null) { year = pick.groupValues[1].toInt(); cut = pick.range.first }
        }
        val toks = tokens(flat.substring(0, cut))
        var end = toks.size
        for (i in toks.indices) {
            if ((i > 0 || allowFirstJunk) && isJunk(toks[i], toks.getOrNull(i + 1))) { end = i; break }
        }
        var title = toks.subList(0, end).joinToString(" ")
        title = TRAILING_PART.replace(title, "")
        title = MULTI_SPACE.replace(EDGE_JUNK.replace(title, ""), " ").trim()
        return title to year
    }

    private val GENERIC = setOf("movie", "video", "film", "main", "title", "cd1", "cd2", "disc1", "disc2", "videots", "bdmv", "stream", "index")

    /**
     * Analyse un fichier. [fileName] = nom avec extension ; [folders] = dossiers parents, du plus haut au plus proche du fichier,
     * relatifs à la racine du partage (jamais le nom du partage lui-même).
     */
    fun parse(fileName: String, folders: List<String> = emptyList()): Parsed {
        val base = if (extensionOf(fileName) in VIDEO_EXTENSIONS) fileName.substringBeforeLast('.') else fileName
        val parent = folders.lastOrNull().orEmpty()
        val seasonFromFolder = SEASON_FOLDER.matchEntire(parent)?.groupValues?.get(1)?.toIntOrNull()

        // 1) Marque d'épisode dans le nom : S01E02, 1x02, « Season 1 Episode 2 ».
        val m = SXXEXX.find(base) ?: LONG_FORM.find(base) ?: NXNN.find(base)
        if (m != null) {
            val season = m.groupValues[1].toInt()
            val episode = m.groupValues[2].toInt()
            val (before, beforeYear) = cleanTitle(base.substring(0, m.range.first), yearHint = true)
            val series = before.ifBlank { seriesFromFolders(folders) }
            val epTitle = cleanTitle(base.substring(m.range.last + 1), yearHint = false, allowFirstJunk = true).first
            return Parsed(Kind.EPISODE, epTitle, beforeYear ?: yearFromFolders(folders), series, season, episode)
        }

        // 2) Dossier « Saison N » : « 02 - Titre.mkv », « E02.mkv ».
        if (seasonFromFolder != null) {
            LEADING_EPISODE.find(base)?.let { lm ->
                val ep = lm.groupValues[1].toInt()
                val rest = cleanTitle(base.substring(lm.range.last + 1), yearHint = false, allowFirstJunk = true).first
                return Parsed(Kind.EPISODE, rest, yearFromFolders(folders), seriesFromFolders(folders), seasonFromFolder, ep)
            }
        }

        // 3) Film.
        var (title, year) = cleanTitle(base)
        if (title.isBlank() || squash(title) in GENERIC) {
            // Nom vide ou générique : le dossier porte le titre (« Titre (2019)/movie.mkv »).
            val f = cleanTitle(parent)
            if (f.first.isNotBlank()) { title = f.first; if (year == null) year = f.second }
        } else if (year == null) {
            // Année seulement dans le dossier (« Titre (2019)/Titre.mkv »).
            val f = cleanTitle(parent)
            if (f.second != null && squash(f.first) == squash(title)) year = f.second
        }
        if (title.isBlank()) title = base.replace('.', ' ').replace('_', ' ').trim()
        return Parsed(Kind.MOVIE, title, year)
    }

    /** Titre de la série d'après les dossiers : le plus proche qui n'est pas un dossier de saison. */
    private fun seriesFromFolders(folders: List<String>): String {
        val f = folders.lastOrNull { SEASON_FOLDER.matchEntire(it) == null } ?: return ""
        return cleanTitle(f).first
    }

    private fun yearFromFolders(folders: List<String>): Int? =
        folders.lastOrNull { SEASON_FOLDER.matchEntire(it) == null }?.let { cleanTitle(it).second }

    /** Clé de regroupement d'une série : titre sans accents ni ponctuation (« La Casa de Papel » = « la.casa.de.papel »). */
    fun seriesKey(title: String): String {
        val n = java.text.Normalizer.normalize(title.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")
        return n.filter { it.isLetterOrDigit() }
    }
}
