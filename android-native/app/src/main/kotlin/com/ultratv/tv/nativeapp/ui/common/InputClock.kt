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

    private val NAV_KEYS = setOf(
        android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
        android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_MENU,
    )
}

/** Application affichée à l'écran (MainActivity onStart / onStop) : les tâches périodiques « confort » s'y limitent. */
object AppForeground {
    @Volatile var visible: Boolean = false
}
