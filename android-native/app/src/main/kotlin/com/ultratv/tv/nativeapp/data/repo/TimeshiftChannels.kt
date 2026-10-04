package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.EpgTitle

/**
 * Chaînes décalées (« TF1 +1 », « M6 +2 ») : même programme que la chaîne de base, N heures plus tard.
 * Les fournisseurs ne leur donnent presque jamais d'identifiant EPG.
 */
object TimeshiftChannels {
    private val RE = Regex("""^(.*\S)\s*\+\s*([1-9])\s*[hH]?$""")

    /** « TF1 +1 » → (« TF1 », 1) ; null si le titre n'est pas celui d'une chaîne décalée. */
    fun parse(title: String): Pair<String, Int>? {
        val m = RE.matchEntire(title.trim()) ?: return null
        val base = m.groupValues[1].trim()
        if (base.count { it.isLetterOrDigit() } < 2) return null
        return base to m.groupValues[2].toInt()
    }

    /**
     * Chaînes SANS identifiant EPG rattachées au programme d'une chaîne qui en a un :
     * même nom (« TF1 » de la catégorie HEVC ↔ « TF1 » de la catégorie VIP RAW), ou chaîne décalée « TF1 +1 » (+N heures).
     * Même pays d'abord, puis nom seul. [primaryOf] : identifiant EPG → canal qui reçoit les programmes.
     * Résultat : canal porteur → (canal rattaché, décalage en heures).
     */
    fun plan(channels: List<EpgTitle>, primaryOf: Map<String, Long>): Map<Long, List<Pair<Long, Int>>> {
        val withEpg = channels.filter { !it.epgChannelId.isNullOrBlank() }
        fun key(country: String?, title: String) = (country?.uppercase() ?: "") + "|" + title.trim().uppercase()
        val byCountryTitle = withEpg.groupBy { key(it.country, it.title) }.mapValues { (_, l) -> primaryOf[l.first().epgChannelId!!] }
        val byTitle = withEpg.groupBy { it.title.trim().uppercase() }.mapValues { (_, l) -> primaryOf[l.first().epgChannelId!!] }
        return channels.filter { it.epgChannelId.isNullOrBlank() }
            .mapNotNull { c ->
                val (base, h) = parse(c.title) ?: (c.title.trim() to 0)
                if (base.count { it.isLetterOrDigit() } < 2) return@mapNotNull null
                val primary = (if (c.country != null) byCountryTitle[key(c.country, base)] else null) ?: byTitle[base.uppercase()]
                primary?.takeIf { it != c.id }?.let { it to (c.id to h) }
            }
            .groupBy({ it.first }, { it.second })
    }
}
