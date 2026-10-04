package com.ultratv.tv.nativeapp.data.repo

import java.text.Normalizer

/**
 * Analyse un nom de chaîne IPTV (appliquée à la SYNCHRO, résultat stocké en colonnes indexées) :
 *  - SEPARATOR : « ##### 4K ᵁᴴᴰ ³⁸⁴⁰ᴾ ##### », « ===== », « ▬▬ » → en-tête de section, jamais une chaîne ;
 *  - displayName : sans préfixe de pays/étiquette, sans marqueurs de qualité (normalisé NFKC : exposants → lettres) ;
 *  - country : code ISO uniquement (liste blanche) — « TV », « GOLD », « VIP », « 4K: » ne sont pas des pays ;
 *  - quality : SD / HD / FHD / 4K ; « 8K » n'est PAS retenu comme définition (étiquette fournisseur : 8 766 noms
 *    « 8K » sur 54 562, impossible en réalité) sauf résolution explicite 4320P / 7680 ;
 *  - flags : HEVC, HDR, 50/60 FPS, RAW, BACKUP, LQ, VIP.
 */
object ChannelNameParser {
    const val Q_NONE = 0
    const val Q_SD = 1
    const val Q_HD = 2
    const val Q_FHD = 3
    const val Q_4K = 4
    const val Q_8K = 5

    const val F_HEVC = 1
    const val F_HDR = 2
    const val F_50FPS = 4
    const val F_60FPS = 8
    const val F_RAW = 16
    const val F_BACKUP = 32
    const val F_LQ = 64
    const val F_VIP = 128

    data class Parsed(
        val isSeparator: Boolean,
        val displayName: String,
        val country: String?,
        val quality: Int,
        val flags: Int,
    )

    private val iso = ("AD AE AF AG AI AL AM AO AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BM BN BO BR BS BT BW BY BZ CA CD CF CG CH CI CK CL CM CN CO CR CU CV CW CY CZ " +
        "DE DJ DK DM DO DZ EC EE EG ER ES ET FI FJ FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GT GU GW GY HK HN HR HT HU ID IE IL IM IN IQ IR IS IT JE JM JO JP " +
        "KE KG KH KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MG MK ML MM MN MO MQ MR MT MU MV MW MX MY MZ NA NC NE NG NI NL NO NP NZ OM PA PE PF PG PH PK PL PR PS PT PW PY QA " +
        "RE RO RS RU RW SA SB SC SD SE SG SI SK SL SM SN SO SR SS ST SV SY SZ TD TG TH TJ TL TM TN TO TR TT TW TZ UA UG US UY UZ VA VC VE VG VI VN VU WS YE ZA ZM ZW UK").split(' ').toSet()

    private val decor = "#=-_*~<>|•●★☆▬▪■□◆◇─━═·.:;+  "
    private val leadingDecor = Regex("""^[#=\-_*~<>|•●★☆▬▪■□◆◇─━═]{3,}""")
    private val prefixSep = Regex("""^\s*([\p{L}\p{N}+ \-]{1,14}?)\s*(?:[:|]|\s-\s)\s*""")
    private val bracketPrefix = Regex("""^\s*(?:\[([^\]]{1,12})]|\(([A-Za-z]{2,4})\))\s*""")
    private val token = Regex("""(?<![\p{L}\p{N}])(8K|4K|UHD|FHD|FULLHD|HD|SD|LQ|HEVC|H265|H\.265|HDR|HDR10|RAW|BACKUP|VIP|2160P|3840P|4320P|7680P|1080P|1080I|720P|576P|480P|50FPS|60FPS|25FPS|30FPS)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Parsed {
        val n = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
        // ── Séparateur ──
        val alnum = n.count { it.isLetterOrDigit() }
        if (n.isNotEmpty() && (alnum == 0 || leadingDecor.containsMatchIn(n))) {
            val label = n.trim { it in decor }.let { cleanMarkers(it).first }.ifBlank { n.trim { it in decor } }
            return Parsed(true, label.uppercase(), null, Q_NONE, 0)
        }
        var s = n
        var country: String? = null
        // Préfixes « XX: », « XX | », « XX - », « [VIP] », « (AU) » (jusqu'à trois, ex. « [VIP] FR| TF1 »).
        repeat(3) {
            val b = bracketPrefix.find(s)
            if (b != null) {
                val code = (b.groupValues[1].ifEmpty { b.groupValues[2] }).uppercase()
                if (country == null && code in iso) country = code
                s = s.substring(b.range.last + 1); return@repeat
            }
            val m = prefixSep.find(s) ?: return@repeat
            val label = m.groupValues[1].uppercase().replace(Regex("[ +\\-].*"), "")
            // Un préfixe n'est retiré que s'il ressemble à une étiquette (majuscules/chiffres) — pas à un vrai titre.
            val whole = m.groupValues[1]
            if (whole.any { it.isLowerCase() } && whole.length > 3) return@repeat
            if (country == null) {
                val first = whole.uppercase().split(' ', '-', '+').firstOrNull { it in iso }
                if (first != null) country = first
            }
            if (label.isNotEmpty() || whole.isNotEmpty()) s = s.substring(m.range.last + 1)
        }
        val (name, q, flags) = cleanMarkers(s)
        var display = name.trim { it in decor }.replace(Regex("""\s{2,}"""), " ")
        if (display.isEmpty()) display = n
        return Parsed(false, display, country, q, flags)
    }

    data class CategoryLabel(val label: String, val badge: String?, val quality: Int)

    private val regions = mapOf("AFRI" to "AFR", "AFRICA" to "AFR", "ASIA" to "ASIA", "EURO" to "EU", "LATAM" to "LATAM", "MENA" to "MENA")
    private val trailingSuper = Regex("\\s*[\u00B2\u00B3\u00B9]\\s*$")

    /** Nom de catégorie affichable : exposants normalisés, préfixe pays/région -> badge, marqueurs de qualité retirés. */
    fun parseCategory(raw: String): CategoryLabel {
        val n = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
        val p = parse(n)
        var label = p.displayName.trim { it in decor }
        var badge = p.country
        if (badge == null) {
            val first = label.substringBefore(' ').uppercase()
            regions[first]?.let { badge = it; label = label.substringAfter(' ', label).trim().ifEmpty { label } }
        }
        label = label.replace(trailingSuper, "").replace(Regex("\\s{2,}"), " ").trim()
        return CategoryLabel(label.ifBlank { n }, badge, p.quality)
    }

    /** Retire les marqueurs de qualité / drapeaux du texte et les renvoie. */
    private fun cleanMarkers(text: String): Triple<String, Int, Int> {
        var q = Q_NONE
        var flags = 0
        val out = token.replace(text) { m ->
            when (m.value.uppercase()) {
                "4K", "UHD", "2160P", "3840P" -> q = maxOf(q, Q_4K)
                "4320P", "7680P" -> q = maxOf(q, Q_8K)
                "8K" -> Unit                       // étiquette fournisseur, pas une définition
                "FHD", "FULLHD", "1080P", "1080I" -> q = maxOf(q, Q_FHD)
                "HD", "720P" -> q = maxOf(q, Q_HD)
                "SD", "576P", "480P" -> q = maxOf(q, Q_SD)
                "LQ" -> { flags = flags or F_LQ; if (q == Q_NONE) q = Q_SD }
                "HEVC", "H265", "H.265" -> flags = flags or F_HEVC
                "HDR", "HDR10" -> flags = flags or F_HDR
                "50FPS" -> flags = flags or F_50FPS
                "60FPS" -> flags = flags or F_60FPS
                "RAW" -> flags = flags or F_RAW
                "BACKUP" -> flags = flags or F_BACKUP
                "VIP" -> flags = flags or F_VIP
            }
            " "
        }
        return Triple(out.replace(Regex("""\s{2,}"""), " ").replace(Regex("""\(\s*\)"""), "").trim(), q, flags)
    }
}
