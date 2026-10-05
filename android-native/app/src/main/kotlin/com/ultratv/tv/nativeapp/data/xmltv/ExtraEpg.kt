package com.ultratv.tv.nativeapp.data.xmltv

import com.ultratv.tv.nativeapp.data.db.EpgTitle
import com.ultratv.tv.nativeapp.data.repo.TimeshiftChannels
import java.text.Normalizer

/**
 * Guide complémentaire gratuit, utilisé SEULEMENT pour les chaînes restées sans programme après le guide de la source.
 * Choix par défaut : XMLTV France (xmltvfr.fr) — projet communautaire maintenu, ~400 chaînes françaises, 7 à 10 jours,
 * identifiants au même format que la plupart des sources (« TF1.fr », « France2.fr »).
 */
object ExtraEpg {
    const val DEFAULT = "https://xmltvfr.fr/xmltv/xmltv_fr.xml.gz"

    /** (URL, libellé) proposés dans les réglages ; "" = désactivé. */
    val OPTIONS = listOf(
        DEFAULT to "XMLTV France",
        "https://xmltvfr.fr/xmltv/xmltv_tnt.xml.gz" to "XMLTV France · TNT (léger)",
        "https://epgshare01.online/epgshare01/epg_ripper_BEIN1.xml.gz" to "epgshare01 · beIN Sports",
        "https://epgshare01.online/epgshare01/epg_ripper_SA1.xml.gz" to "epgshare01 · Arabie saoudite (MBC, Rotana…)",
        "https://epgshare01.online/epgshare01/epg_ripper_AE1.xml.gz" to "epgshare01 · Émirats",
    )

    fun labelOf(url: String): String? = OPTIONS.firstOrNull { it.first == url }?.second

    /** Nom comparable : minuscules, sans accents ni ponctuation, sans mention de qualité. */
    fun norm(name: String): String {
        val s = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase()
        return s.split(Regex("[^a-z0-9+]+")).filter { it.isNotEmpty() && it !in QUALITY }.joinToString("")
    }
    private val QUALITY = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "raw", "vip", "tv")

    /**
     * Résolveur des chaînes du guide complémentaire vers les chaînes locales SANS programme :
     * identifiant EPG identique, sinon même nom (pays compatible), sinon chaîne décalée « X +N » d'une chaîne X du guide.
     */
    class Resolver(targets: List<EpgTitle>) {
        private val byId = targets.filter { !it.epgChannelId.isNullOrBlank() }.groupBy { it.epgChannelId!!.lowercase() }
        private val byName = HashMap<String, MutableList<Pair<EpgTitle, Int>>>()
        init {
            for (t in targets) {
                val (base, h) = TimeshiftChannels.parse(t.title) ?: (t.title to 0)
                val k = norm(base)
                if (k.length >= 2) byName.getOrPut(k) { mutableListOf() }.add(t to h)
            }
        }
        /** (canal local, décalage en heures) pour une chaîne du guide. */
        fun resolve(feedId: String, displayNames: List<String>): List<Pair<Long, Int>> {
            byId[feedId.lowercase()]?.let { l -> return l.map { it.id to 0 } }
            val feedCountry = feedId.substringAfterLast('.', "").uppercase().takeIf { it.length == 2 }
            val out = LinkedHashMap<Long, Int>()
            for (n in displayNames + feedId.substringBeforeLast('.')) {
                byName[norm(n)]?.forEach { (t, h) ->
                    val c = t.country?.uppercase()
                    if (c == null || feedCountry == null || c == feedCountry) out.putIfAbsent(t.id, h)
                }
            }
            return out.map { it.key to it.value }
        }
    }
}
