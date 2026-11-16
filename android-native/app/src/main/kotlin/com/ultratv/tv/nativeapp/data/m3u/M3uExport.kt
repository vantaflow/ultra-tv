package com.ultratv.tv.nativeapp.data.m3u

/**
 * Export d'une liste de chaînes au format M3U standard (`#EXTM3U`, `#EXTINF` avec `tvg-id`, `tvg-logo`, `group-title`, puis
 * l'adresse du flux). Logique pure, testée en JVM.
 *
 * ATTENTION : les adresses Xtream contiennent l'identifiant et le mot de passe de l'abonnement ; l'interface prévient
 * l'utilisateur avant d'enregistrer ou de partager le fichier. Les propriétés de lecture (`#KODIPROP` / `#EXTVLCOPT` :
 * clés de licence, en-têtes) sont des secrets de lecture et ne sont JAMAIS exportées.
 */
object M3uExport {

    data class Entry(
        val name: String,
        val url: String,
        /** Identifiant XMLTV (`tvg-id`) ; vide = inconnu (attribut omis). */
        val tvgId: String? = null,
        val logo: String? = null,
        /** Nom de la catégorie (`group-title`). */
        val group: String? = null,
        /** Numéro de chaîne (`tvg-chno`) ; 0 ou moins = inconnu. */
        val number: Int = 0,
    )

    /**
     * Seules les adresses lisibles hors de l'appli sont exportées : les adresses internes (`stalker://`, dont le lien est
     * demandé à usage unique au lancement) ne fonctionneraient dans aucun autre lecteur.
     */
    fun isExportable(url: String): Boolean {
        val scheme = url.trim().substringBefore("://", "").lowercase()
        return scheme in setOf("http", "https", "rtmp", "rtsp", "udp", "rtp", "mms")
    }

    /** Valeur d'attribut : pas de guillemet ni de saut de ligne (le format n'a pas d'échappement). */
    internal fun attr(v: String): String =
        v.replace('"', '\'').replace(Regex("[\\r\\n\\t]+"), " ").trim()

    /** Titre après la virgule : une seule ligne. */
    internal fun title(v: String): String = v.replace(Regex("[\\r\\n\\t]+"), " ").trim()

    /** Nombre de chaînes réellement écrites par [build]. */
    fun count(entries: List<Entry>): Int = entries.count { isExportable(it.url) }

    /** Contenu du fichier. Les chaînes à adresse non exportable sont ignorées ; l'ordre est conservé. */
    fun build(entries: List<Entry>): String {
        val sb = StringBuilder("#EXTM3U\n")
        for (e in entries) {
            if (!isExportable(e.url)) continue
            val name = title(e.name).ifEmpty { "-" }
            sb.append("#EXTINF:-1")
            e.tvgId?.let(::attr)?.takeIf { it.isNotEmpty() }?.let { sb.append(" tvg-id=\"").append(it).append('"') }
            sb.append(" tvg-name=\"").append(attr(name)).append('"')
            e.logo?.let(::attr)?.takeIf { it.isNotEmpty() }?.let { sb.append(" tvg-logo=\"").append(it).append('"') }
            if (e.number > 0) sb.append(" tvg-chno=\"").append(e.number).append('"')
            e.group?.let(::attr)?.takeIf { it.isNotEmpty() }?.let { sb.append(" group-title=\"").append(it).append('"') }
            sb.append(',').append(name).append('\n')
            sb.append(title(e.url)).append('\n')
        }
        return sb.toString()
    }

    /** Nom de fichier proposé (`ultra-tv-favorites-AAAAMMJJ.m3u`). */
    fun fileName(year: Int, month: Int, day: Int): String =
        "ultra-tv-favorites-%04d%02d%02d.m3u".format(java.util.Locale.ROOT, year, month, day)
}
