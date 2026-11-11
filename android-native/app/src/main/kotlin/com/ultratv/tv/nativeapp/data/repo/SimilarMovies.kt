package com.ultratv.tv.nativeapp.data.repo

/**
 * « Parce que vous avez regardé… » : classement des films proches d'un film de départ, en mémoire (règles pures,
 * testées sans Android). Un candidat doit partager l'essentiel des genres du film de départ, ou son réalisateur, ou
 * un acteur : un film qui n'a en commun qu'un genre très large (« Action ») n'est plus proposé.
 */
object SimilarMovies {
    data class Facts(val genres: Set<String>, val people: List<String>, val director: String?, val year: Int?)

    /** Genres normalisés (« Science-Fiction, Aventure » → {science fiction, aventure}). */
    fun genres(raw: String?): Set<String> =
        raw.orEmpty().split(',', '/', '|', ';', '&').map { normalize(it) }.filter { it.length >= 3 }.toSet()

    /** Genres tels qu'écrits (pour les requêtes LIKE), sans les caractères joker de SQL. */
    fun rawGenres(raw: String?): List<String> =
        raw.orEmpty().split(',', '/', '|', ';', '&').map { it.trim().replace("%", "").replace("_", "") }.filter { it.length >= 3 }.distinct()

    /** Noms de la distribution, dans l'ordre (les premiers sont les rôles principaux). */
    fun people(raw: String?, max: Int): List<String> =
        raw.orEmpty().split(',', ';', '|').map { it.trim() }.filter { it.length >= 3 }.distinct().take(max)

    fun <T> rank(seedTitle: String, seed: Facts, candidates: List<Pair<T, Facts>>, limit: Int, titleOf: (T) -> String = { "" }): List<T> {
        val seedKey = normalize(seedTitle)
        val needGenres = if (seed.genres.size >= 2) 2 else seed.genres.size
        return candidates.asSequence()
            .mapNotNull { (item, f) ->
                val shared = f.genres.count { it in seed.genres }
                val sameDirector = seed.director != null && f.people.any { it.equals(seed.director, ignoreCase = true) }
                val sharedPeople = f.people.count { p -> seed.people.any { it.equals(p, ignoreCase = true) } }
                // Garde-fou du thème : assez de genres en commun, ou un lien de personnes fort.
                if (shared < needGenres && !sameDirector && sharedPeople == 0) return@mapNotNull null
                val union = (f.genres + seed.genres).size.coerceAtLeast(1)
                var score = 10.0 * shared / union + 2.0 * shared
                if (sameDirector) score += 4
                score += 2.5 * sharedPeople.coerceAtMost(2)
                val dy = if (seed.year != null && f.year != null) kotlin.math.abs(seed.year - f.year) else null
                if (dy != null) score += when { dy <= 5 -> 1.0; dy <= 12 -> 0.5; else -> 0.0 }
                item to score
            }
            .filter { (item, _) -> titleOf(item).let { it.isEmpty() || normalize(it) != seedKey } }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
    }

    internal fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('-', ' ').replace(Regex("\\s+"), " ")
}
