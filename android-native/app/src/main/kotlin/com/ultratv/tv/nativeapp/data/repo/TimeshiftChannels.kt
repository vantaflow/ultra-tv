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
     * Chaînes décalées SANS identifiant EPG, rattachées au canal porteur du programme de leur chaîne de base.
     * [primaryOf] : identifiant EPG → canal qui reçoit les programmes. Résultat : canal porteur → (canal décalé, heures).
     */
    fun plan(channels: List<EpgTitle>, primaryOf: Map<String, Long>): Map<Long, List<Pair<Long, Int>>> {
        val baseByTitle = channels.filter { !it.epgChannelId.isNullOrBlank() }
            .groupBy { it.title.trim().uppercase() }
            .mapValues { (_, l) -> primaryOf[l.first().epgChannelId!!] }
        return channels.filter { it.epgChannelId.isNullOrBlank() }
            .mapNotNull { c -> parse(c.title)?.let { (base, h) -> baseByTitle[base.uppercase()]?.let { primary -> primary to (c.id to h) } } }
            .groupBy({ it.first }, { it.second })
    }
}
