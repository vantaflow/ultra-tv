package com.ultratv.tv.nativeapp.data.config

/**
 * Serveur de secours (Vercel), utilisé UNIQUEMENT quand le Worker Cloudflare est en panne, pour lire la configuration et
 * les annonces. Décisions pures ici ; l'interceptor est dans WorkerFallback.kt. Voir docs/plan-secours-vercel.md.
 */
object BackupFailover {
    const val HOST = "utv-backup.vercel.app"
    /** Le secours a un délai plus court que le principal : en panne, on ne fait pas attendre l'utilisateur. */
    const val TIMEOUT_SECONDS = 10

    /** Chemins lisibles en secours (liste blanche identique à celle du serveur de secours). */
    private val PATHS = setOf("/api/config", "/api/announcements")

    /** Seules les lectures (GET) de la liste blanche peuvent aller au secours : jamais une écriture. */
    fun eligible(method: String, path: String): Boolean = method.equals("GET", ignoreCase = true) && path in PATHS

    /** Statut qui signale une panne du principal : 500, 502, 503, 504, 52x (Cloudflare). Jamais un 4xx, 304 ni 429. */
    fun isOutageStatus(code: Int): Boolean = code == 500 || code == 502 || code == 503 || code == 504 || code in 520..526

    /** Une réponse du secours n'est retenue que si 200 ou 304 : un 401 du secours ne doit jamais délier l'appareil. */
    fun accepted(code: Int): Boolean = code == 200 || code == 304

    /** Disjoncteur partagé par tous les appels du processus. */
    val breaker = Breaker()

    /**
     * 2 échecs consécutifs du principal ouvrent le secours pendant 60 s (lectures d'emblée au secours), puis une requête
     * d'essai repart vers le principal ; un nouvel échec double la pause (5 min au plus), un succès referme.
     */
    class Breaker(private val now: () -> Long = System::currentTimeMillis) {
        private var failures = 0
        private var openUntil = 0L
        private var cooldownMs = BASE_MS

        @Synchronized fun preferBackup(): Boolean = now() < openUntil

        @Synchronized fun success() { failures = 0; openUntil = 0L; cooldownMs = BASE_MS }

        @Synchronized fun failure() {
            failures++
            if (failures < THRESHOLD) return
            if (openUntil > 0L) cooldownMs = minOf(cooldownMs * 2, MAX_MS)
            openUntil = now() + cooldownMs
        }

        companion object {
            const val BASE_MS = 60_000L
            const val MAX_MS = 300_000L
            const val THRESHOLD = 2
        }
    }
}
