package com.ultratv.tv.nativeapp.data.tmdb

/**
 * Clé YouTube (11 caractères) d'une valeur fournie par la source ou TMDB : `watch?v=`, `youtu.be/`, `embed/`, `shorts/`,
 * ou la clé brute. Fonction pure, sans Android : même règle que `youtubeKey` du bureau (web/src/lib/youtube.ts), à garder alignée.
 */
object YoutubeKey {
    private val KEY = Regex("[A-Za-z0-9_-]{11}")
    private val ABSENT = setOf("", "null", "none", "n/a", "undefined", "0", "false")
    private val HOSTS = setOf("youtube.com", "youtube-nocookie.com", "youtu.be")
    private val PATH_KEY = Regex("^/(?:embed|shorts|v|live)/([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])")
    private val SHORT_PATH = Regex("^/([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])")
    private val SCHEME = Regex("^(?:https?:)?//", RegexOption.IGNORE_CASE)
    private val HOST_AND_REST = Regex("^([^/?#:]+)(?::\\d+)?([^#]*)")
    private val SUBDOMAIN = Regex("^(?:www|m|music)\\.")

    /** Clé extraite, ou null (valeur vide ou « null », adresse qui n'est pas YouTube, clé mal formée). */
    fun of(value: String?): String? {
        val s = value?.trim().orEmpty()
        if (s.lowercase() in ABSENT) return null
        if (KEY.matches(s)) return s
        val m = HOST_AND_REST.find(s.replace(SCHEME, "")) ?: return null
        val host = m.groupValues[1].lowercase().replace(SUBDOMAIN, "")
        if (host !in HOSTS) return null
        val tail = m.groupValues[2]
        val q = tail.indexOf('?')
        val path = if (q >= 0) tail.substring(0, q) else tail
        val query = if (q >= 0) tail.substring(q + 1) else ""
        if (host == "youtu.be") return SHORT_PATH.find(path)?.groupValues?.get(1)
        PATH_KEY.find(path)?.let { return it.groupValues[1] }
        val v = query.split('&').firstNotNullOfOrNull { p -> p.takeIf { it.startsWith("v=") }?.substring(2) }
        return v?.takeIf { KEY.matches(it) }
    }
}
