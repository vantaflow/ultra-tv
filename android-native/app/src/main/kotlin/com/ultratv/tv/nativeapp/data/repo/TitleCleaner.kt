package com.ultratv.tv.nativeapp.data.repo

/**
 * Nettoie les intitulés des fournisseurs IPTV pour l'AFFICHAGE (le nom brut reste stocké, c'est lui
 * que la recherche interroge). Retire les préfixes de langue / pays / qualité (« IN-EN - »,
 * « 4K-A+ - », « FR| », « |AR| », « [VIP] », « US: », « (AU) »), les décorations (« ## »,
 * lettres modificatrices « ᴿᴬᵂ », « ◉ »), les suffixes de qualité (HD, 4K, HEVC…) et les pays
 * entre parenthèses, et extrait l'année « (2021) » dans un champ séparé. Jamais de titre vide.
 */
object TitleCleaner {
    data class Cleaned(val title: String, val year: Int?, val quality: String?)

    private val bracketPrefix = Regex("""^\s*(?:\[[^\]]{1,12}]|\|[\p{L}\p{N}+ \-]{1,10}\||\([A-Za-z]{2,4}\))\s*""")
    private val pipePrefix = Regex("""^\s*[\p{L}\p{N}+]{1,8}(?:[- ][\p{L}\p{N}+]{1,8}){0,2}\s*\|\s*""")
    private val colonPrefix = Regex("""^[A-Z0-9][A-Z0-9+/ \-]{0,11}:\s+""")
    private val dashPrefix = Regex("""^[A-Z0-9+]{1,5}(?:-[A-Z0-9+]{1,5}){0,3}\s+-\s+""")
    private val yearRe = Regex("""\((19\d{2}|20\d{2})\)""")
    private val countrySuffix = Regex("""\s*\([A-Z]{2,3}\)\s*$""")
    private val qualityTokens = listOf("8K", "4K", "UHD", "FHD", "HD", "SD", "HEVC", "H265", "H264", "LQ", "RAW", "50FPS", "60FPS", "FULLHD")
    private val trailingQuality = Regex("""\s+(?:${qualityTokens.joinToString("|")})\s*$""", RegexOption.IGNORE_CASE)
    private val trailingSource = Regex("""\s*\((?:SAT|D|TV|IPTV)\)\s*$""")

    private fun isDecoration(c: Char): Boolean =
        c.code in 0x02B0..0x02FF || c.code in 0x2070..0x209F || c.code in 0x1D2C..0x1DBF || c == '◉' || c == '★' || c == '☆' || c == '•' || c == '●' || c == '⚽'

    /** Retire lettres modificatrices / symboles décoratifs (« ᴿᴬᵂ », « ◉ », « ³⁸⁴⁰ᴾ »). */
    fun stripDecorations(s: String): String = s.filterNot(::isDecoration).replace(Regex("""\s{2,}"""), " ").trim()

    fun clean(raw: String, live: Boolean = false): Cleaned {
        var s = raw.trim()
        if (s.isEmpty()) return Cleaned(raw, null, null)
        // Lignes d'événements « a | b | c | NL: CHAÎNE » : seul le dernier segment est le nom.
        if (live && " | " in s) s.substringAfterLast(" | ").trim().takeIf { it.length >= 3 }?.let { s = it }
        s = s.filterNot(::isDecoration).trim()
        s = s.trim('#', ' ', '-', '_').trim()
        // Préfixes (plusieurs passes : « [VIP] FR| TF1 », « (AU) US: X »).
        repeat(3) {
            val before = s
            s = s.replaceFirst(bracketPrefix, "")
            s = s.replaceFirst(pipePrefix, "")
            if (live || dashPrefix.containsMatchIn(s)) s = s.replaceFirst(colonPrefix, "")
            s = s.replaceFirst(dashPrefix, "")
            s = s.trim()
            if (s == before) return@repeat
        }
        // Année : le dernier « (YYYY) » ; ce qui suit (titre traduit, « دوبله », pays) est ignoré.
        var year: Int? = null
        val m = yearRe.findAll(s).lastOrNull()
        if (m != null && m.range.first > 0) {
            year = m.groupValues[1].toInt()
            s = s.substring(0, m.range.first).trim()
        }
        s = s.replace(countrySuffix, "").replace(trailingSource, "").trim()
        var quality: String? = null
        if (live || year == null) {
            while (true) {
                val q = trailingQuality.find(s) ?: break
                val tok = q.value.trim().uppercase()
                if (quality == null && tok in setOf("8K", "4K", "UHD", "FHD", "HD", "SD")) quality = tok
                s = s.substring(0, q.range.first).trim()
            }
        }
        s = s.trim('#', ' ', '-', '_', '|', ':').replace(Regex("""\s{2,}"""), " ")
        if (s.isEmpty()) s = raw.trim()
        return Cleaned(s, year, quality)
    }
}
