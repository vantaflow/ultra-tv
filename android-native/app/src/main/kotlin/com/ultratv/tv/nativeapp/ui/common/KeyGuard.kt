package com.ultratv.tv.nativeapp.ui.common

/**
 * Filtre des touches de télécommande en double. Certaines télécommandes Bluetooth de box Google TV envoient deux
 * ACTION_DOWN (repeatCount 0) pour un seul appui, sans ACTION_UP entre les deux : le focus avançait alors de deux
 * éléments (« la sélection saute »). Un second DOWN de la même touche sans UP, moins de [DUP_WINDOW_MS] après le
 * premier, est ignoré. Les répétitions d'un appui long (repeatCount > 0) passent toujours.
 *
 * Compte aussi le retard de traitement (instant de l'appui → traitement) : une box surchargée accumule les appuis.
 * Les compteurs sont envoyés en télémétrie (tag « keys ») au plus une fois par [REPORT_EVERY_MS].
 */
class KeyGuard(private val report: (String) -> Unit = {}) {
    private val downAt = HashMap<Int, Long>()
    private var dups = 0
    private var lagged = 0
    private var maxLag = 0L
    private var repeats = 0
    private var lastReport = 0L

    /** Renvoie true si l'évènement doit être ignoré. [eventTime] et [now] en uptime (ms). */
    fun shouldDrop(keyCode: Int, down: Boolean, repeatCount: Int, eventTime: Long, now: Long): Boolean {
        if (keyCode !in GUARDED) return false
        if (!down) { downAt.remove(keyCode); return false }
        val lag = now - eventTime
        if (lag > LAG_MS) { lagged++; if (lag > maxLag) maxLag = lag }
        if (repeatCount > 0) { repeats++; flush(now); return false }
        val prev = downAt[keyCode]
        if (prev != null && eventTime - prev in 0 until DUP_WINDOW_MS) {
            dups++; flush(now); return true
        }
        downAt[keyCode] = eventTime
        flush(now)
        return false
    }

    private fun flush(now: Long) {
        if (dups + lagged == 0 || now - lastReport < REPORT_EVERY_MS) return
        lastReport = now
        report("dup=$dups lag=$lagged maxLagMs=$maxLag repeats=$repeats")
        dups = 0; lagged = 0; maxLag = 0; repeats = 0
    }

    companion object {
        const val DUP_WINDOW_MS = 250L
        const val LAG_MS = 250L
        const val REPORT_EVERY_MS = 30_000L
        private val GUARDED = setOf(
            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
        )
    }
}
