package com.ultratv.tv.nativeapp.ui.settings

/**
 * Télémétrie du parcours « Portail Stalker (MAC) » (tag « stalker », niveau info), limitée en débit.
 * Jamais d'adresse MAC, d'identifiant, de mot de passe ni de chemin d'URL : seulement l'hôte du portail et des classes d'erreur.
 */
internal object StalkerTelemetry {
    /** Au plus un événement par clé toutes les [perKeyMs] ms, et [maxPerSession] événements par processus. */
    internal class Limiter(private val perKeyMs: Long, private val maxPerSession: Int, private val now: () -> Long) {
        private val last = HashMap<String, Long>()
        private var count = 0

        @Synchronized fun allow(key: String): Boolean {
            if (count >= maxPerSession) return false
            val t = now()
            val prev = last[key]
            if (prev != null && t - prev < perKeyMs) return false
            last[key] = t
            count++
            return true
        }
    }

    private val limiter = Limiter(perKeyMs = 5_000, maxPerSession = 40, now = { android.os.SystemClock.elapsedRealtime() })

    /** Remplaçable en test. */
    @Volatile internal var sink: (String) -> Unit = { com.ultratv.tv.nativeapp.RemoteLog.info("stalker", it) }

    private fun log(key: String, message: String) { if (limiter.allow(key)) sink(message) }

    fun dialogOpened() = log("open", "dialog opened")

    /** [reason] : back, cancel, submit, other (« other » = disparu sans action de l'utilisateur). */
    fun dialogClosed(reason: String) = log("close:$reason", "dialog dismissed reason=$reason")

    fun addAttempt(portal: String) = log("add", "add attempt host=${portalHost(portal)}")

    fun probeOk() = log("probe", "probe ok endpoint found")
    fun probeFailed(t: Throwable) = log("probe", "probe failed error=${errorClass(t)}")

    fun syncStarted() = log("sync-start", "sync start")
    fun syncEnded(errorKind: String?) = log("sync-end", if (errorKind == null) "sync end ok" else "sync error kind=$errorKind")
    fun syncUnknown() = log("sync-end", "sync end unknown (no status observed)")

    /** Hôte seul, en minuscules (jamais le port, le chemin ni la requête) ; « invalid » si l'adresse n'en a pas. */
    internal fun portalHost(portal: String): String {
        val s = portal.trim()
        val withScheme = if (s.contains("://")) s else "http://$s"
        return runCatching { java.net.URI(withScheme).host?.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "invalid"
    }

    /** Classe de l'erreur seulement (le message peut contenir l'adresse ou la MAC). */
    internal fun errorClass(t: Throwable?): String = t?.javaClass?.simpleName?.takeIf { it.isNotBlank() } ?: "unknown"
}
