package com.ultratv.tv.nativeapp.data.repo

/**
 * Normalisation de l'arabe pour la recherche, appliquée À L'IDENTIQUE à la saisie et aux titres :
 * minuscules, voyelles et signes (tashkeel) et tatweel retirés, أ إ آ ٱ → ا, ة → ه, ى → ي.
 *
 * L'index plein texte (FTS4 unicode61) ne plie rien de tout cela (« احمد » ne trouvait pas « أحمد », « مسلسل » ne
 * trouvait pas « مُسَلْسَل ») et Android ne permet pas de tokenizer maison : pour une saisie en arabe, un balayage LIKE
 * ([likePattern], puis [loosePattern] si rien) donne des candidats, et [matches] (même pliage des deux côtés) tranche.
 * Logique pure, testée à part.
 */
object ArabicFold {
    private const val ESC = '\\'
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    fun hasArabic(s: String): Boolean = s.any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' }

    /** Pliage arabe + minuscules (le latin n'est que mis en minuscules). */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) when (ch) {
            in 'ً'..'ٟ', 'ٰ', 'ـ' -> Unit          // tashkeel, hamza combinante, alef suscrit, tatweel
            'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا') // أ إ آ ٱ → ا
            'ة' -> sb.append('ه')                              // ة → ه
            'ى' -> sb.append('ي')                              // ى → ي
            else -> sb.append(ch.lowercaseChar())
        }
        return sb.toString()
    }

    /** Mots de la saisie, pliés. */
    fun tokens(s: String): List<String> = fold(s).split(SEPARATORS).filter { it.isNotEmpty() }

    /** Tous les mots de [query] figurent dans [title] (même pliage des deux côtés). */
    fun matches(query: String, title: String): Boolean {
        val q = tokens(query)
        if (q.isEmpty()) return false
        val t = fold(title)
        return q.all { t.contains(it) }
    }

    /**
     * Motif LIKE (ESCAPE '\') des candidats : mots dans l'ordre, et `_` à la place des lettres qui ont plusieurs
     * graphies dans les titres (ا ه ي après pliage). Null si moins de 2 lettres (balayage inutile).
     */
    fun likePattern(query: String): String? {
        val t = tokens(query)
        if (t.sumOf { it.length } < 2) return null
        return t.joinToString("%", prefix = "%", postfix = "%") { w -> buildString { w.forEach { appendLike(it) } } }
    }

    /** Repli pour les titres voyellés : chaque lettre séparée par `%` (le tashkeel du titre peut s'intercaler). */
    fun loosePattern(query: String): String? {
        val t = tokens(query)
        if (t.sumOf { it.length } < 2) return null
        return buildString {
            append('%')
            for (w in t) for (c in w) { appendLike(c); append('%') }
        }
    }

    /**
     * Titres correspondant à une saisie en arabe : candidats par [likePattern] (puis [loosePattern] si aucun, titres
     * voyellés), filtrés par [matches]. [fetch] (motif, plafond) interroge la base. Vide si la saisie n'a pas d'arabe.
     */
    suspend fun <T> search(query: String, cap: Int, fetch: suspend (String, Int) -> List<T>, name: (T) -> String): List<T> {
        if (!hasArabic(query)) return emptyList()
        val strict = likePattern(query) ?: return emptyList()
        val hits = fetch(strict, cap).filter { matches(query, name(it)) }
        if (hits.isNotEmpty()) return hits
        val loose = loosePattern(query) ?: return emptyList()
        return fetch(loose, cap).filter { matches(query, name(it)) }
    }

    private fun StringBuilder.appendLike(c: Char) {
        when (c) {
            'ا', 'ه', 'ي' -> append('_')
            '%', '_', ESC -> append(ESC).append(c)
            else -> append(c)
        }
    }
}
