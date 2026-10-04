package com.ultratv.tv.nativeapp.data.repo

import java.text.Normalizer

/**
 * Détection de la langue d'un élément (appliquée à la synchro, stockée dans une colonne indexée `lang`).
 * Ordre : tag explicite du titre (VOSTFR, FRENCH, MULTI…) → préfixe du titre → langue de la catégorie →
 * alphabet du titre → plateforme (Netflix, Amazon… → MULTI) → « Non déterminé » (toujours VISIBLE : en cas de doute on affiche).
 * Attention : en préfixe, « AR » signifie ARABE dans ces sources (pas Argentine) ; « 4K », « TV », « GOLD », « VIP »… ne sont pas des langues.
 */
object LanguageDetector {
    const val MULTI = "MULTI"
    const val UNDETERMINED = ""

    private val codeToLang: Map<String, String> = buildMap {
        for (c in "UK US CA AU EN IE NZ GB".split(' ')) put(c, "en")
        for (c in "FR QC VF VFF TRUEFRENCH FRENCH".split(' ')) put(c, "fr")
        for (c in "AR MA DZ TN EG SA AE QA KW IQ SY LB JO LY SD YE OM BH ARA".split(' ')) put(c, "ar")
        for (c in "DE AT DEU GER".split(' ')) put(c, "de")
        for (c in "ES MX LAT CO CL PE VE".split(' ')) put(c, "es")
        for (c in "PT BR POR".split(' ')) put(c, "pt")
        put("IT", "it"); put("TR", "tr"); put("TUR", "tr"); put("NL", "nl"); put("GR", "el"); put("IN", "hi"); put("IR", "fa"); put("AL", "sq")
        put("SE", "sv"); put("DK", "da"); put("NO", "no"); put("FI", "fi"); put("PL", "pl"); put("IL", "he"); put("KU", "ku")
        put("RU", "ru"); put("UA", "uk"); put("RO", "ro"); put("BG", "bg"); put("RS", "sh"); put("HR", "sh"); put("BA", "sh"); put("CZ", "cs"); put("SK", "sk"); put("HU", "hu")
        put("PK", "ur"); put("BD", "bn"); put("KR", "ko"); put("JP", "ja"); put("CN", "zh"); put("TH", "th"); put("VN", "vi"); put("ID", "id"); put("PH", "tl")
    }

    private val platform = Regex("""(?i)\b(NETFLIX|AMAZON|PRIME\s*VIDEO|DISNEY\+?|APPLE\s*TV?\+?|APPLE\+|HBO|MAX|PARAMOUNT\+?|OSN\+?|SHAHID|HULU|PEACOCK|STARZ|MULTI[- ]?SUBS?|MULTI[- ]?AUDIO|MULTI|DUAL)\b""")
    private val multiTag = Regex("""(?i)\b(MULTI[- ]?SUBS?|MULTI[- ]?AUDIO|MULTI|DUAL)\b""")
    private val titleTags: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)\b(VOSTFR|TRUEFRENCH|FRENCH|VFF|VFQ|VF)\b""") to "fr",
        Regex("""(?i)\b(VOSTA|ARABIC|ARA)\b""") to "ar",
        Regex("""(?i)\b(LATINO|LAT)\b""") to "es",
        Regex("""(?i)\b(TURKISH|TUR)\b""") to "tr",
        Regex("""(?i)\b(GERMAN|DEU)\b""") to "de",
        Regex("""(?i)\b(PORTUGUESE|POR)\b""") to "pt",
    )
    private val prefix = Regex("""^\s*\[?\(?([A-Za-z]{2,4}(?:[-\s][A-Za-z+]{1,6})?)[\])]?\s*(?:[:|]|\s-\s)""")
    private val keywordLang: List<Pair<String, String>> = listOf(
        "تركية" to "tr", "تركي" to "tr", "هندية" to "hi", "كورية" to "ko", "فارسي" to "fa", "إيرانية" to "fa", "مسلسلات عربية" to "ar", "عربية" to "ar", "عربي" to "ar",
    )

    /** Langue d'un nom d'après son seul alphabet / mots-clés ; null si rien de net. */
    fun byScript(text: String): String? {
        var ar = 0; var cy = 0; var el = 0; var he = 0; var dv = 0; var th = 0; var kana = 0; var hangul = 0; var han = 0
        var faSpecific = false; var ukSpecific = false
        for (ch in text) {
            when (ch.code) {
                in 0x0600..0x06FF, in 0x0750..0x077F -> { ar++; if (ch in "پچژگ") faSpecific = true }
                in 0x0400..0x04FF -> { cy++; if (ch in "їєіґЇЄІҐ") ukSpecific = true }
                in 0x0370..0x03FF, in 0x1F00..0x1FFF -> el++
                in 0x0590..0x05FF -> he++
                in 0x0900..0x097F -> dv++
                in 0x0E00..0x0E7F -> th++
                in 0x3040..0x30FF -> kana++
                in 0xAC00..0xD7AF -> hangul++
                in 0x4E00..0x9FFF -> han++
            }
        }
        for ((k, l) in keywordLang) if (k in text) return l
        return when {
            ar > 0 && ar >= maxOf(cy, el, he, dv) -> if (faSpecific) "fa" else "ar"
            cy > 0 && cy >= maxOf(el, he, dv) -> if (ukSpecific) "uk" else "ru"
            el > 0 -> "el"; he > 0 -> "he"; dv > 0 -> "hi"; th > 0 -> "th"; kana > 0 -> "ja"; hangul > 0 -> "ko"; han > 0 -> "zh"
            else -> null
        }
    }

    /** Langue annoncée par un préfixe « XX: », « XX | », « XX - » ; null s'il n'est pas un code langue/pays connu. */
    fun byPrefix(name: String): String? {
        val m = prefix.find(Normalizer.normalize(name, Normalizer.Form.NFKC)) ?: return null
        // « IN-EN » : pays puis langue → la langue (dernier code connu) l'emporte ; « AR-SUBS » → AR.
        val tokens = m.groupValues[1].uppercase().split('-', ' ').filter { it.isNotEmpty() }
        return tokens.lastOrNull { it in codeToLang }?.let { codeToLang[it] }
    }

    fun byTag(name: String): String? {
        val n = Normalizer.normalize(name, Normalizer.Form.NFKC)
        if (multiTag.containsMatchIn(n)) return MULTI
        for ((re, l) in titleTags) if (re.containsMatchIn(n)) return l
        return null
    }

    fun isPlatform(name: String) = platform.containsMatchIn(Normalizer.normalize(name, Normalizer.Form.NFKC))

    /**
     * Langue d'une CATÉGORIE (préfixe, alphabet, mots-clés, plateforme → MULTI). Renvoie [UNDETERMINED] si rien de net.
     */
    fun forCategory(name: String): String =
        byPrefix(name) ?: byScript(name) ?: if (isPlatform(name)) MULTI else UNDETERMINED

    /** Langue d'un élément, avec héritage de [categoryLang] (« » = catégorie indéterminée). */
    fun forItem(title: String, categoryLang: String): String {
        byTag(title)?.let { return it }
        byPrefix(title)?.let { return it }
        if (categoryLang.isNotEmpty() && categoryLang != MULTI) return categoryLang
        byScript(title)?.let { return it }
        if (categoryLang == MULTI) return MULTI
        return UNDETERMINED
    }
}
