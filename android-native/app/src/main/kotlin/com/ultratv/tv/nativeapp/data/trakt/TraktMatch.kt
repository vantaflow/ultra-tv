package com.ultratv.tv.nativeapp.data.trakt

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Clé de rapprochement des titres Trakt ↔ playlist. PORT EXACT de `matchKey` du Worker (cloudflare-config) et du
 * bureau : les trois implémentations partagent les vecteurs `cloudflare-config/test/fixtures/trakt-match-vectors.json`
 * (TraktMatchTest). Toute modification de la règle se fait d'abord dans ce fichier de vecteurs.
 */
object TraktMatch {
    // `\s` JavaScript = espaces Unicode (dont l'insécable et le BOM) ; celui de Java est ASCII seulement.
    private const val WS = "[\\s\\p{Zs}\\uFEFF]"

    // Préfixe de pays / langue / source : volontairement sensible à la casse (« FR - », « |FR| », « [VOD] »).
    private val PREFIX = Regex("^$WS*(\\|[^|]{1,12}\\||\\[[^\\]]{1,12}\\]|[A-Z0-9]{2,4}$WS*[-:|]$WS*)")
    private val MARKS = Regex("\\p{M}+")
    private val QUALITY = Regex(
        "(?<![\\p{L}\\p{N}])(4k|uhd|fhd|hd|sd|hevc|h26[45]|x26[45]|multi|vostfr|vost|vff|vf|vo|truefrench|subfrench|french)(?![\\p{L}\\p{N}])",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val YEAR_PARENS = Regex("\\($WS*(19|20)\\d{2}$WS*\\)")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val SPACES = Regex("$WS+")
    private val TRAILING_YEAR = Regex("$WS(19|20)\\d{2}\\z")
    private val LEADING_ARTICLE = Regex("^(the|le|la|les|l|el|los|las|a|an)$WS+(?=\\S)")

    fun matchKey(raw: String?): String {
        var s = raw ?: ""
        s = s.replaceFirst(PREFIX, "")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)
        s = s.replace(QUALITY, " ")
        s = s.replace(YEAR_PARENS, " ")
        s = s.replace("&", " and ")
        s = s.replace(NON_ALNUM, " ").replace(SPACES, " ").trim(' ')
        val noYear = s.replaceFirst(TRAILING_YEAR, "")
        if (noYear.isNotEmpty()) s = noYear
        s = s.replaceFirst(LEADING_ARTICLE, "")
        return s
    }

    /** Années compatibles : l'une inconnue, ou écart d'au plus 1 (sorties de fin / début d'année, fuseaux). */
    fun yearsCompatible(a: Int?, b: Int?): Boolean = a == null || b == null || a == 0 || b == 0 || abs(a - b) <= 1

    /** Même règle que `titleMatches` du Worker (vecteurs « matches »). */
    fun titleMatches(catalogTitle: String, catalogYear: Int?, titles: List<String>, year: Int?): Boolean {
        val k = matchKey(catalogTitle)
        if (k.isEmpty()) return false
        if (!yearsCompatible(catalogYear, year)) return false
        return titles.any { matchKey(it) == k }
    }
}
