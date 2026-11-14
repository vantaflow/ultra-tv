package com.ultratv.tv.nativeapp.ui.common

/**
 * Touche Retour : un appui court ne ferme JAMAIS l'appli (il revient en arrière, ou affiche « Maintenez Retour pour
 * quitter » quand il n'y a plus rien derrière) ; un appui long (≥ [HOLD_MS]) la ferme, depuis n'importe quel écran.
 */
object BackHold {
    const val HOLD_MS = 800L

    /** Appui assez long pour quitter : touche encore enfoncée (répétitions) ou relâchée après [HOLD_MS]. */
    fun isLong(eventTime: Long, downTime: Long): Boolean = eventTime - downTime >= HOLD_MS

    /** Téléphone / tablette (Retour = geste, pas d'appui long) : deux Retour en moins de [AGAIN_MS] à l'accueil ferment l'appli. */
    const val AGAIN_MS = 2_000L
    fun isSecondPress(now: Long, lastPress: Long): Boolean = lastPress > 0 && now - lastPress <= AGAIN_MS
}
