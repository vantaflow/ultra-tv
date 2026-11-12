package com.ultratv.tv.nativeapp.ui.common

/**
 * Instant (uptime) de la dernière touche de télécommande. Distingue un focus DÉPLACÉ par l'utilisateur d'un focus
 * récupéré tout seul (élément focalisé disparu pendant une mise à jour de la liste en arrière-plan).
 */
object InputClock {
    @Volatile var lastKeyMs: Long = 0L
    @Volatile var lastKeyCode: Int = 0

    /** Une touche a été pressée il y a moins de [windowMs]. */
    fun recentKey(windowMs: Long = 600): Boolean = android.os.SystemClock.uptimeMillis() - lastKeyMs < windowMs

    /**
     * La dernière touche, récente, était une touche de DÉPLACEMENT (flèches, Menu) : l'utilisateur a lui-même amené le
     * focus. Un OK qui fait disparaître le bouton focalisé (« Définir par défaut », « Synchroniser ») envoie aussi le focus
     * ailleurs, mais ce n'est pas une navigation.
     */
    fun recentNavKey(windowMs: Long = 600): Boolean = recentKey(windowMs) && lastKeyCode in NAV_KEYS

    /** Millisecondes à attendre encore pour que [quietMs] se soient écoulées depuis la dernière touche (0 = silence déjà établi). */
    fun quietRemaining(nowMs: Long, lastKeyMs: Long, quietMs: Long): Long = (quietMs - (nowMs - lastKeyMs)).coerceAtLeast(0L)

    /**
     * Suspend jusqu'à ce que la télécommande soit restée silencieuse [quietMs] (au plus [maxWaitMs] au total). Sert aux
     * chargements réseau de confort : décoder du JSON et négocier du TLS pendant que l'utilisateur fait défiler les
     * rangées prend du temps de processeur au fil principal et au rendu sur une box à quatre cœurs.
     */
    suspend fun awaitQuiet(quietMs: Long, maxWaitMs: Long) {
        val start = android.os.SystemClock.uptimeMillis()
        while (true) {
            val now = android.os.SystemClock.uptimeMillis()
            val wait = quietRemaining(now, lastKeyMs, quietMs)
            if (wait == 0L || now - start >= maxWaitMs) return
            kotlinx.coroutines.delay(wait.coerceAtMost(maxWaitMs - (now - start)).coerceAtLeast(1L))
        }
    }

    private val NAV_KEYS = setOf(
        android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_MENU,
    )
}

/** Application affichée à l'écran (MainActivity onStart / onStop) : les tâches périodiques « confort » s'y limitent. */
object AppForeground {
    @Volatile var visible: Boolean = false
}
