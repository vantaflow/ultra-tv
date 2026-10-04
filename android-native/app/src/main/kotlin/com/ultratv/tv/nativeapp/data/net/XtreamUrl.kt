package com.ultratv.tv.nativeapp.data.net

/**
 * Détecte une adresse de type Xtream Codes collée là où l'on attendait un lien M3U :
 * `http://hôte[:port]/get.php?username=U&password=P&type=m3u_plus&output=ts` (ou `player_api.php`, `xmltv.php`).
 * Beaucoup de fournisseurs bloquent `get.php` (HTTP 884) mais acceptent l'API : on crée alors une source
 * Xtream Codes (serveur, identifiant, mot de passe) à la place de la source M3U.
 */
object XtreamUrl {
    data class Credentials(val server: String, val username: String, val password: String)

    private val xtreamPaths = setOf("get.php", "player_api.php", "xmltv.php")
    private val urlRegex = Regex("""^(https?)://([^/?#]+)((?:/[^?#]*)?)(?:\?([^#]*))?(?:#.*)?$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Credentials? {
        val m = urlRegex.matchEntire(raw.trim()) ?: return null
        val (scheme, authority, path, query) = m.destructured
        val host = authority.substringAfterLast('@')
        if (host.isBlank() || host.startsWith(":")) return null
        val file = path.trimEnd('/').substringAfterLast('/').lowercase()
        if (file !in xtreamPaths) return null
        val params = query.split('&').filter { it.contains('=') }
            .associate { it.substringBefore('=').lowercase() to percentDecode(it.substringAfter('=')) }
        val user = params["username"].orEmpty()
        val pass = params["password"].orEmpty()
        if (user.isBlank() || pass.isBlank()) return null
        val prefix = path.trimEnd('/').substringBeforeLast('/', "")
        return Credentials("${scheme.lowercase()}://$host$prefix", user, pass)
    }

    /** Source M3U « get.php » → même source en Xtream Codes (dates de synchro remises à zéro) ; null si ce n'est pas le cas. */
    fun convert(p: com.ultratv.tv.nativeapp.data.db.ProviderEntity): com.ultratv.tv.nativeapp.data.db.ProviderEntity? {
        if (p.kind != "M3U") return null
        val c = parse(p.baseUrl) ?: return null
        return p.copy(kind = "XTREAM", baseUrl = c.server.trimEnd('/'), username = c.username, password = c.password,
            lastLiveSyncAt = 0, lastVodSyncAt = 0, lastSeriesSyncAt = 0, lastEpgSyncAt = 0, categoryFilter = -1)
    }

    /** Décodage %XX en UTF-8 ; le « + » reste un « + » (il peut faire partie d'un mot de passe). */
    internal fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length && s.substring(i + 1, i + 3).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                out.write(s.substring(i + 1, i + 3).toInt(16)); i += 3
            } else { out.write(c.toString().toByteArray(Charsets.UTF_8)); i++ }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
