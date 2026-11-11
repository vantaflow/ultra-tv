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

    /**
     * Nom comparable : minuscules, sans accents ni ponctuation, sans mention de qualité (HD, FHD, 4K…), sans préfixe de
     * pays (« PL: », « PL | », « [PL] ») ni suffixe de pays (« … PL »). Les espaces sont supprimés : « TVP 1 » = « TVP1 ».
     * Un chiffre reste significatif : « TVN » ≠ « TVN 7 ». Même règle côté bureau (web/src/sync/epgNames.ts).
     */
    fun norm(name: String): String {
        var s = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").lowercase()
        s = COUNTRY_PREFIX.replace(s) { m -> if ((m.groupValues[1].ifEmpty { m.groupValues[2] }) in COUNTRIES) "" else m.value }
        val tokens = s.split(Regex("[^a-z0-9+]+")).filter { it.isNotEmpty() && it !in QUALITY }.toMutableList()
        // Suffixe de pays : seulement s'il reste un nom devant (« TVP 1 PL » → « tvp1 », mais « PL » seul reste).
        while (tokens.size >= 2 && tokens.last() in COUNTRIES) tokens.removeAt(tokens.lastIndex)
        return tokens.joinToString("")
    }
    private val QUALITY = setOf("hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "raw", "vip", "tv")
    /** Codes pays des préfixes et suffixes des listes IPTV (liste volontairement courte : seuls les bords du nom sont touchés). */
    private val COUNTRIES = setOf("pl", "fr", "uk", "gb", "us", "de", "es", "pt", "it", "nl", "tr", "ro", "ru", "gr")
    /** Début du nom : « [PL] », « (PL) », ou « PL » suivi de `:`, `|`, `/` ou `-`. */
    private val COUNTRY_PREFIX = Regex("^\\s*(?:[\\[(]\\s*([a-z]{2})\\s*[\\])]|([a-z]{2})\\s*[:|/-])\\s*(?=\\S)")

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
                // Identifiant EPG de la playlist (« TVP1.pl ») différent de celui du guide (« TVP 1 ») : son radical sert
                // aussi de nom. Jamais pour une chaîne décalée : son identifiant est celui de la chaîne de base, pas le sien.
                if (h == 0 && !t.epgChannelId.isNullOrBlank()) {
                    val ik = norm(t.epgChannelId!!.substringBeforeLast('.'))
                    if (ik.length >= 2 && ik != k) byName.getOrPut(ik) { mutableListOf() }.add(t to 0)
                }
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
