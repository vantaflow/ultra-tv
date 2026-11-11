package com.ultratv.tv.nativeapp.data.repo

import java.text.Normalizer

/**
 * Genres automatiques : regroupement des genres bruts (texte du fournisseur Xtream, identifiants TMDB) en une
 * vingtaine de genres communs, identiques quelle que soit la langue de la source. Logique pure, testée à part.
 */
enum class Genre {
    ACTION, ADVENTURE, ANIMATION, ANIME, COMEDY, CRIME, DOCUMENTARY, DRAMA, FAMILY, FANTASY, HISTORY, HORROR, MUSIC, MYSTERY,
    ROMANCE, SCIFI, THRILLER, WAR, WESTERN, OTHER,
}

object GenreGrouping {
    /** Code stocké pour les titres sans genre exploitable. */
    val OTHER_CODE = Genre.OTHER.name

    /** Au plus ce nombre de genres par titre (l'anime passe en premier) : borne la taille de la table de jointure. */
    const val MAX_PER_TITLE = 3

    private val MARKS = Regex("\\p{Mn}+")
    private val SPLIT = Regex("[,;/|&+\\n]")

    /** Identifiants de genres TMDB (films et séries) → genres communs. */
    private val TMDB: Map<Int, List<Genre>> = mapOf(
        28 to listOf(Genre.ACTION), 12 to listOf(Genre.ADVENTURE), 16 to listOf(Genre.ANIMATION), 35 to listOf(Genre.COMEDY),
        80 to listOf(Genre.CRIME), 99 to listOf(Genre.DOCUMENTARY), 18 to listOf(Genre.DRAMA), 10751 to listOf(Genre.FAMILY),
        14 to listOf(Genre.FANTASY), 36 to listOf(Genre.HISTORY), 27 to listOf(Genre.HORROR), 10402 to listOf(Genre.MUSIC),
        9648 to listOf(Genre.MYSTERY), 10749 to listOf(Genre.ROMANCE), 878 to listOf(Genre.SCIFI), 53 to listOf(Genre.THRILLER),
        10752 to listOf(Genre.WAR), 37 to listOf(Genre.WESTERN),
        // Séries
        10759 to listOf(Genre.ACTION, Genre.ADVENTURE), 10762 to listOf(Genre.FAMILY), 10765 to listOf(Genre.SCIFI, Genre.FANTASY),
        10766 to listOf(Genre.DRAMA), 10768 to listOf(Genre.WAR),
    )

    /** Mots-clés (déjà normalisés : minuscules, sans accents, ponctuation → espace) par genre. L'ordre compte : la science-fiction passe avant « fantasy ». */
    private val KEYWORDS: List<Pair<Genre, List<String>>> = listOf(
        Genre.SCIFI to listOf("science fiction", "sci fi", "scifi", "sf", "ciencia ficcion", "خيال علمي"),
        Genre.ANIME to listOf("anime", "animes", "انمي"),
        Genre.ANIMATION to listOf("animation", "animacion", "animated", "cartoon", "cartoons", "dessin", "dessins", "رسوم متحركة", "كرتون"),
        Genre.ACTION to listOf("action", "accion", "اكشن", "حركة"),
        Genre.ADVENTURE to listOf("adventure", "adventures", "aventure", "aventures", "aventura", "aventuras", "مغامرة", "مغامرات"),
        Genre.COMEDY to listOf("comedy", "comedie", "comedia", "humour", "humor", "كوميديا", "كوميدي"),
        Genre.CRIME to listOf("crime", "crimen", "policier", "polar", "جريمة"),
        Genre.DOCUMENTARY to listOf("documentary", "documentaire", "documental", "وثائقي", "وثائقية"),
        Genre.DRAMA to listOf("drama", "drame", "dramatique", "دراما"),
        Genre.FAMILY to listOf("family", "famille", "familia", "familial", "kids", "enfants", "infantil", "عائلي", "عائلة", "اطفال"),
        Genre.FANTASY to listOf("fantasy", "fantastique", "fantasia", "فانتازيا", "خيال"),
        Genre.HISTORY to listOf("history", "histoire", "historique", "historia", "historical", "تاريخ", "تاريخي"),
        Genre.HORROR to listOf("horror", "horreur", "terror", "epouvante", "رعب"),
        Genre.MUSIC to listOf("music", "musique", "musica", "musical", "موسيقى"),
        Genre.MYSTERY to listOf("mystery", "mystere", "misterio", "غموض", "لغز"),
        Genre.ROMANCE to listOf("romance", "romantique", "romantic", "romantico", "رومانسي", "رومانسية"),
        Genre.THRILLER to listOf("thriller", "suspense", "suspenso", "اثارة", "تشويق"),
        Genre.WAR to listOf("war", "guerre", "guerra", "حرب"),
        Genre.WESTERN to listOf("western", "وسترن", "غربي"),
    )

    private val NORMALIZED_KEYWORDS: List<Pair<Genre, List<String>>> = KEYWORDS.map { (g, ks) -> g to ks.map(::normalize) }

    /** Minuscules, sans accents (ni marques arabes), toute ponctuation remplacée par un espace. */
    internal fun normalize(raw: String): String {
        val d = MARKS.replace(Normalizer.normalize(raw.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD), "")
        val sb = StringBuilder(d.length)
        for (c in d) sb.append(if (c.isLetterOrDigit()) c else ' ')
        return sb.toString().trim().replace(Regex(" +"), " ")
    }

    /** Genres d'un texte libre du fournisseur (« Action, Aventure », « Sci-Fi & Fantasy »…), dans l'ordre d'apparition. */
    fun fromText(text: String?): List<Genre> {
        if (text.isNullOrBlank()) return emptyList()
        val out = LinkedHashSet<Genre>()
        for (token in text.split(SPLIT)) {
            var t = " " + normalize(token) + " "
            if (t.isBlank()) continue
            for ((g, words) in NORMALIZED_KEYWORDS) {
                for (w in words) {
                    val needle = " $w "
                    if (t.contains(needle)) { out.add(g); t = t.replace(needle, " ") }
                }
            }
        }
        return out.toList()
    }

    /** Genres d'une liste d'identifiants TMDB séparés par des virgules (« 28,12 »). */
    fun fromTmdb(ids: String?): List<Genre> {
        if (ids.isNullOrBlank()) return emptyList()
        val out = LinkedHashSet<Genre>()
        for (p in ids.split(',')) TMDB[p.trim().toIntOrNull()]?.let { out.addAll(it) }
        return out.toList()
    }

    /**
     * Genres d'un titre : TMDB d'abord (fiable), puis les textes du fournisseur ; « anime » = animation d'origine
     * japonaise (langue d'origine TMDB `ja`) ou mention explicite. Jamais vide : [Genre.OTHER] si rien n'est reconnu.
     */
    fun classify(tmdbIds: String?, originalLanguage: String?, vararg texts: String?): List<Genre> {
        val set = LinkedHashSet<Genre>()
        set.addAll(fromTmdb(tmdbIds))
        for (t in texts) set.addAll(fromText(t))
        if (Genre.ANIMATION in set && originalLanguage.equals("ja", ignoreCase = true)) set.add(Genre.ANIME)
        if (set.isEmpty()) return listOf(Genre.OTHER)
        val ordered = (if (Genre.ANIME in set) listOf(Genre.ANIME) else emptyList()) + set.filter { it != Genre.ANIME }
        return ordered.take(MAX_PER_TITLE)
    }

    /** Ordre d'affichage : le plus fourni d'abord, « Autres » toujours en dernier, égalités par nom de code. */
    fun <T> sortedByCount(entries: List<T>, code: (T) -> String, count: (T) -> Int): List<T> =
        entries.sortedWith(compareBy<T>({ code(it) == OTHER_CODE }, { -count(it) }, { code(it) }))
}
