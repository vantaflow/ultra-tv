package com.ultratv.tv.nativeapp

/**
 * Retire les identifiants de tout texte destiné au Worker (journaux, crashs).
 * Les URL Xtream embarquent utilisateur et mot de passe (paramètres ou segments
 * de chemin) ; les envoyer tels quels fuiterait l'abonnement de l'utilisateur.
 *
 * Objet pur (sans dépendance Android) pour pouvoir le tester en JVM. Le Worker
 * applique les mêmes règles côté serveur : cette copie réduit ce qui quitte
 * l'appareil, celle du serveur couvre les clients anciens ou modifiés.
 */
object LogSanitizer {

    private const val PARAMS =
        "username|user|login|password|passwd|pass|pwd|token|apikey|api_key|secret|auth|authorization|key"

    private val USERINFO = Regex("""(\b[a-zA-Z][a-zA-Z0-9+.-]*://)[^\s/@]+@""")
    private val PARAM = Regex("""(^|[?&;\s,{"'])($PARAMS)(\s*[=:]\s*"?)[^&\s"',}]+""", RegexOption.IGNORE_CASE)
    private val XTREAM_PATH = Regex("""/(live|movie|series|vod|radio|timeshift)/[^/\s?]+/[^/\s?]+/""", RegexOption.IGNORE_CASE)
    private val DEVICE_TOKEN = Regex("""\butv_[A-Za-z0-9_-]{16,}""")
    private val BEARER = Regex("""\bBearer\s+[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE)
    private val CONTROL = Regex("""[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]""")

    fun sanitize(input: String, max: Int = 4000): String {
        var out = input.take(max * 2).replace(CONTROL, "")
        out = out.replace(USERINFO, "$1<redacted>@")
        out = out.replace(PARAM) { m -> "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}<redacted>" }
        out = out.replace(XTREAM_PATH) { m -> "/${m.groupValues[1]}/<redacted>/<redacted>/" }
        out = out.replace(DEVICE_TOKEN, "utv_<redacted>")
        out = out.replace(BEARER, "Bearer <redacted>")
        return out.take(max)
    }
}
