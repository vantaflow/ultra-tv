package com.ultratv.tv.nativeapp.data.repo

/** Tri des résultats de recherche (puces « Trier »). */
enum class SearchSort { RELEVANCE, TITLE, YEAR, RATING }

/**
 * Classement et tri des résultats de recherche, et clé de tri alphabétique des titres. Logique pure, testée à part.
 *
 * - Pertinence : titre identique à la saisie > commence par la saisie > contient le mot entier > le reste ; à égalité,
 *   le plus récent d'abord, puis l'ordre d'origine (tri stable).
 * - A–Z : [titleKey] (minuscules, sans accents ni ponctuation, sans article initial : « The Office » se range à O,
 *   « L'Odyssée » à O, « Les Nuits Courtes » à N) ; chiffres avant lettres.
 */
object SearchRank {
    /** Articles ignorés en tête de titre (fr, en, es, de, it). « À » n'en est pas un (« À bout de souffle » reste à A). */
    private val ARTICLES = setOf("le", "la", "les", "l", "un", "une", "des", "the", "a", "an", "el", "los", "las", "der", "die", "das", "il", "lo", "gli", "i")

    fun titleKey(title: String): String {
        val words = FuzzySearch.tokens(title)
        val first = title.trimStart().firstOrNull()
        val dropArticle = words.size > 1 && words[0] in ARTICLES && first != 'À' && first != 'à'
        return (if (dropArticle) words.drop(1) else words).joinToString(" ")
    }

    /** Lettre du rail A–Z : initiale de [titleKey] en majuscule, « # » pour un chiffre ou un titre vide. */
    fun initial(title: String): Char {
        val c = titleKey(title).firstOrNull() ?: return '#'
        return if (c.isLetter()) c.uppercaseChar() else '#'
    }

    /** 4 = titre identique, 3 = commence par la saisie, 2 = contient le mot entier, 1 = autre correspondance. */
    fun relevance(query: String, title: String): Int {
        val q = FuzzySearch.tokens(query).joinToString(" ")
        if (q.isEmpty()) return 0
        val t = FuzzySearch.tokens(title).joinToString(" ")
        val k = titleKey(title)
        return when {
            t == q || k == q -> 4
            t.startsWith(q) || k.startsWith(q) -> 3
            " $t ".contains(" $q ") -> 2
            else -> 1
        }
    }

    fun <T> order(items: List<T>, query: String, sort: SearchSort, title: (T) -> String, year: (T) -> Int?, rating: (T) -> Double?): List<T> = when (sort) {
        SearchSort.RELEVANCE -> items.sortedWith(compareByDescending<T> { relevance(query, title(it)) }.thenByDescending { year(it) ?: 0 })
        SearchSort.TITLE -> items.sortedWith(compareBy<T> { titleKey(title(it)) }.thenByDescending { year(it) ?: 0 })
        SearchSort.YEAR -> items.sortedWith(compareByDescending<T> { year(it) ?: Int.MIN_VALUE }.thenBy { titleKey(title(it)) })
        SearchSort.RATING -> items.sortedWith(compareByDescending<T> { validRating(rating(it)) ?: -1.0 }.thenBy { titleKey(title(it)) })
    }

    /** Note exploitable (0 < note ≤ 10), sinon null : certaines sources envoient 0 ou des valeurs hors échelle. */
    fun validRating(r: Double?): Double? = r?.takeIf { it > 0.0 && it <= 10.0 }

    /** Lettres présentes dans une liste déjà triée A–Z, avec l'indice du premier titre de chacune (rail de lettres). */
    fun <T> letterIndex(sortedByTitle: List<T>, title: (T) -> String): Map<Char, Int> {
        val out = LinkedHashMap<Char, Int>()
        sortedByTitle.forEachIndexed { i, it -> out.putIfAbsent(initial(title(it)), i) }
        return out
    }

    /** « 9–16 sur 18 » : bornes affichées (1-indexées) d'après les éléments visibles ; null s'il n'y a rien. */
    fun visibleRange(firstVisible: Int, lastVisible: Int, total: Int): Pair<Int, Int>? {
        if (total <= 0 || lastVisible < 0) return null
        val a = (firstVisible.coerceIn(0, total - 1)) + 1
        val b = (lastVisible.coerceIn(0, total - 1)) + 1
        return a to maxOf(a, b)
    }
}
