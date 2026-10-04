package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import java.text.Normalizer
import java.util.Locale

/**
 * Une seule entrée par œuvre dans la recherche : les fournisseurs listent le même film sous
 * « FR - X », « |FR| X », « [EN] X », « X 4K », « X MULTI »… La clé est le titre nettoyé
 * (préfixes, qualité, langue, année entre parenthèses retirés, accents/casse/ponctuation ignorés) + l'année.
 */
object SearchDedup {
    private val tagTokens = setOf(
        "multi", "vostfr", "vost", "vf", "vff", "vfq", "vfi", "vo", "truefrench", "french", "subbed", "dubbed",
        "4k", "8k", "uhd", "fhd", "hd", "sd", "hevc", "x265", "x264", "h265", "h264", "bluray", "bdrip", "webrip", "hdrip", "dvdrip", "hdtv", "1080p", "720p", "2160p",
    )
    private val combining = Regex("\\p{Mn}+")

    /** Minuscules, sans accents, ponctuation → espaces. */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(combining, "").lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Titre canonique + année (nulle si inconnue). */
    fun key(rawName: String, knownYear: Int?): String {
        val c = TitleCleaner.clean(rawName)
        var words = fold(c.title).split(' ').filter { it.isNotEmpty() }
        while (words.size > 1 && words.last() in tagTokens) words = words.dropLast(1)
        while (words.size > 1 && words.first() in tagTokens) words = words.drop(1)
        return words.joinToString(" ") + "|" + (knownYear ?: c.year ?: 0)
    }

    /** Garde la première occurrence de chaque œuvre, en préférant celle qui a une affiche ; l'ordre d'origine est conservé. */
    fun <T> dedupe(items: List<T>, key: (T) -> String, hasPoster: (T) -> Boolean): List<T> {
        val best = LinkedHashMap<String, T>()
        for (it in items) {
            val k = key(it)
            val cur = best[k]
            if (cur == null || (!hasPoster(cur) && hasPoster(it))) best[k] = it
        }
        return best.values.toList()
    }

    fun movies(list: List<MovieEntity>) = dedupe(list, { key(it.name, it.year) }, { !it.poster.isNullOrBlank() })
    fun series(list: List<SeriesEntity>) = dedupe(list, { key(it.name, it.year) }, { !it.poster.isNullOrBlank() })
}
