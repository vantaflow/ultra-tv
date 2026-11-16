package com.ultratv.tv.nativeapp.data.tmdb

import java.io.IOException

/**
 * Le proxy TMDB du Worker a répondu 429 : plus aucune requête TMDB avant [retryAfterSec] secondes.
 * Sous-classe d'[IOException] : les appelants existants la traitent comme une erreur réseau (rien n'est mémorisé).
 */
class TmdbRateLimitedException(val retryAfterSec: Long) : IOException("TMDB proxy HTTP 429 (retry-after ${retryAfterSec}s)")

/**
 * Pause des requêtes TMDB après un 429 : on respecte `Retry-After` au lieu de continuer la rafale. Pure (heure injectée).
 * Sans en-tête exploitable : [DEFAULT_SEC] ; la valeur est bornée entre [MIN_SEC] et [MAX_SEC].
 */
class TmdbRateLimit(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var until = 0L

    /** Millisecondes restantes de pause (0 = on peut interroger). */
    fun remainingMs(): Long = maxOf(0L, until - clock())

    /** Note un 429 ; [retryAfterHeader] = valeur brute de `Retry-After` (secondes), ou null. Renvoie la durée retenue en secondes. */
    fun onRateLimited(retryAfterHeader: String?): Long {
        val sec = parseRetryAfter(retryAfterHeader)
        until = maxOf(until, clock() + sec * 1000)
        return sec
    }

    companion object {
        const val DEFAULT_SEC = 60L
        const val MIN_SEC = 5L
        const val MAX_SEC = 900L

        fun parseRetryAfter(raw: String?): Long = (raw?.trim()?.toLongOrNull() ?: DEFAULT_SEC).coerceIn(MIN_SEC, MAX_SEC)
    }
}

/**
 * Filtre des titres à chercher sur TMDB : un titre qui ne ressemble pas à un film (moins de 3 lettres, que des chiffres,
 * une date) ne trouvera rien, on ne lance donc aucune requête. Pur, testé.
 */
object TmdbTitleFilter {
    private val numericDate = Regex("""^\d{1,4}\s*[-/._]\s*\d{1,2}\s*[-/._]\s*\d{1,4}$""")
    private val monthNames = "jan|janv|janvier|january|feb|fev|fév|févr|fevrier|février|february|mar|mars|march|apr|avr|avril|april|" +
        "may|mai|jun|juin|june|jul|juil|juillet|july|aug|aou|aoû|août|aout|august|sep|sept|septembre|september|oct|octobre|october|" +
        "nov|novembre|november|dec|déc|décembre|decembre|december|ene|enero|abr|abril|mayo|junio|julio|ago|agosto|septiembre|" +
        "octubre|noviembre|dic|diciembre|janeiro|fevereiro|março|marco|maio|junho|julho|setembro|outubro|dezembro"
    // « 15 novembre 2026 », « Nov 15 », « 3rd Mar, 2026 » : un mois, au moins un nombre, rien d'autre.
    private val monthDate = Regex("""^(\d{1,2}(st|nd|rd|th|er)?\s+)?($monthNames)\.?(\s+\d{1,4})?(,?\s+\d{2,4})?$""", RegexOption.IGNORE_CASE)

    /** Vrai si [title] peut être le titre d'un film ou d'une série. */
    fun looksLikeTitle(title: String): Boolean {
        val t = title.trim()
        if (t.count { it.isLetter() } < 3) return false              // « 12 », « 2024 », « A1 », « --- »
        if (numericDate.matches(t)) return false                      // 2026-11-15, 15/11/2026
        if (monthDate.matches(t) && t.any { it.isDigit() }) return false // 15 novembre 2026
        return true
    }
}
