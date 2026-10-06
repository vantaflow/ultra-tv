package com.ultratv.tv.nativeapp.ui.common

/**
 * Instant (uptime) de la dernière touche de télécommande. Distingue un focus DÉPLACÉ par l'utilisateur d'un focus
 * récupéré tout seul (élément focalisé disparu pendant une mise à jour de la liste en arrière-plan).
 */
object InputClock {
    @Volatile var lastKeyMs: Long = 0L

    /** Une touche a été pressée il y a moins de [windowMs]. */
    fun recentKey(windowMs: Long = 600): Boolean = android.os.SystemClock.uptimeMillis() - lastKeyMs < windowMs
}

/** Application affichée à l'écran (MainActivity onStart / onStop) : les tâches périodiques « confort » s'y limitent. */
object AppForeground {
    @Volatile var visible: Boolean = false
}
