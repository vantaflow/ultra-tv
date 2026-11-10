package com.ultratv.tv.nativeapp.data.repo

import java.text.Normalizer

/**
 * Recherche tolérante aux fautes de frappe (« brekin bad » → Breaking Bad), en repli quand la recherche exacte
 * (FTS) ne trouve aucun film ni série. Deux temps : des candidats par FTS sur le DÉBUT de chaque mot (3 lettres,
 * mots reliés par OR), puis un classement par distance d'édition mot à mot. Logique pure, testée à part.
 */
object FuzzySearch {
    /** Score minimal (0..1) pour retenir un titre. */
    const val THRESHOLD = 0.72

    /** Requête FTS4 des candidats : préfixes de 3 lettres, au moins un mot de 3 lettres ; null sinon. */
    fun candidateMatch(raw: String): String? {
        val pre = tokens(raw).filter { it.length >= 3 }.map { it.take(3) }.distinct().take(6)
        if (pre.isEmpty()) return null
        return pre.joinToString(" OR ") { "$it*" }
    }

    /** Minuscules, sans accents ni ponctuation, mots séparés. */
    fun tokens(s: String): List<String> =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }

    /**
     * Ressemblance (0..1) entre la saisie et un titre : pour chaque mot saisi, le mot du titre le plus proche
     * (distance d'édition rapportée à la longueur), moyenne pondérée par la longueur des mots saisis.
     */
    fun score(query: String, title: String): Double {
        val q = tokens(query).filter { it.length >= 2 }
        val t = tokens(title)
        if (q.isEmpty() || t.isEmpty()) return 0.0
        var sum = 0.0; var weight = 0
        for (w in q) {
            val best = t.maxOf { x -> similarity(w, x) }
            sum += best * w.length; weight += w.length
        }
        return sum / weight
    }

    /** Titres ressemblants, du plus proche au moins proche. */
    fun <T> rank(query: String, items: List<T>, title: (T) -> String, limit: Int): List<T> =
        items.asSequence()
            .map { it to score(query, title(it)) }
            .filter { it.second >= THRESHOLD }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()

    /** 1 − distance de Damerau-Levenshtein / longueur du plus long ; un mot saisi plus court compte comme préfixe. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        // Saisie en cours : « break » contre « breaking » ne doit pas être pénalisé par la fin manquante.
        val bb = if (b.length > a.length + 1) b.take(a.length + 1) else b
        val d = distance(a, bb)
        return 1.0 - d.toDouble() / maxOf(a.length, bb.length)
    }

    private fun distance(a: String, b: String): Int {
        val n = a.length; val m = b.length
        if (n == 0) return m
        if (m == 0) return n
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
            d[i][j] = v
        }
        return d[n][m]
    }
}
