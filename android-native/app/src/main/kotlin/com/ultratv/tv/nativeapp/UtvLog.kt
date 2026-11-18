package com.ultratv.tv.nativeapp

/**
 * Journaux de diagnostic à tags stables, filtrables avec adb :
 *   adb logcat -s UtvCloud:V UtvMedia:V UtvSync:V UtvSport:V UtvPlay:V
 *
 * Règles : résumés seulement (jamais une ligne par image ni par élément) ; jamais de jeton, de mot de passe ni d'URL avec
 * identifiants ([mask], [maskUrl], [LogSanitizer]). Les niveaux warn et error sont aussi envoyés au journal de l'appareil
 * ([RemoteLog], lisible dans /admin › Journaux) quand la télémétrie est activée.
 */
object UtvLog {
    const val CLOUD = "UtvCloud"
    const val MEDIA = "UtvMedia"
    const val SYNC = "UtvSync"
    const val SPORT = "UtvSport"
    const val PLAY = "UtvPlay"

    /** Texte sans identifiant : jetons, mots de passe, `user:pass@`, segments d'identifiants Xtream. */
    fun mask(text: String): String = LogSanitizer.sanitize(text, 600)

    /** URL réduite à `schéma://hôte[:port]/chemin` : requête, fragment et identifiants retirés ; segments de type Xtream masqués. */
    fun maskUrl(url: String?): String {
        if (url.isNullOrBlank()) return "-"
        val noFrag = url.substringBefore('#')
        val base = noFrag.substringBefore('?')
        val scheme = base.substringBefore("://", "")
        val rest = if (scheme.isEmpty()) base else base.substringAfter("://")
        val host = rest.substringBefore('/').substringAfterLast('@')
        val path = if (rest.contains('/')) "/" + rest.substringAfter('/') else ""
        val q = if (noFrag.contains('?')) "?…" else ""
        return mask((if (scheme.isEmpty()) "" else "$scheme://") + host + path + q)
    }

    /** Cause d'échec lisible et sans secret : classe de l'exception + message masqué. */
    fun why(t: Throwable): String = t.javaClass.simpleName + (t.message?.let { ": " + mask(it) } ?: "")

    fun i(tag: String, msg: String) { runCatching { android.util.Log.i(tag, mask(msg)) } }
    fun w(tag: String, msg: String) { runCatching { android.util.Log.w(tag, mask(msg)) }; remote { RemoteLog.warn(tag, msg) } }
    fun e(tag: String, msg: String) { runCatching { android.util.Log.e(tag, mask(msg)) }; remote { RemoteLog.error(tag, msg) } }

    private inline fun remote(f: () -> Unit) { runCatching { f() } }

    /** Durée écoulée en ms depuis [startNs] ([System.nanoTime]). */
    fun ms(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000
}
