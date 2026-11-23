package com.ultratv.tv.nativeapp.ui.player

/**
 * Quand une chaîne du direct entre dans l'historique du profil (« Dernières chaînes regardées ») — logique pure, testée.
 *
 * Une chaîne compte dès qu'elle a montré [MIN_WATCH_MS] d'image sans changer de chaîne : un zapping rapide (on passe sans
 * s'arrêter) ne remplit pas la rangée, une chaîne regardée y entre quelle que soit la façon de l'avoir atteinte. La chaîne
 * affichée en quittant le lecteur est toujours enregistrée, comme avant.
 */
object LiveHistoryPolicy {
    const val MIN_WATCH_MS = 5_000L

    /** Armer le délai : une chaîne du direct ([liveKey] non nul) dont l'image joue. */
    fun shouldArm(liveKey: String?, playing: Boolean): Boolean = liveKey != null && playing
}
