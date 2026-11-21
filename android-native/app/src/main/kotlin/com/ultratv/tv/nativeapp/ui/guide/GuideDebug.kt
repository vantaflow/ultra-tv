package com.ultratv.tv.nativeapp.ui.guide

/**
 * Réglages CACHÉS de mesure du Guide (aucun texte ni entrée dans l'interface, désactivés par défaut). Lus au lancement de
 * l'activité depuis un extra d'intent, par exemple :
 * `adb shell am start -S -n com.ultratv.tv.nativeapp/.MainActivity --ez guide_instant_scroll true`.
 * Un lancement normal (icône, Accueil) les remet à faux.
 */
object GuideDebug {
    const val EXTRA_INSTANT_SCROLL = "guide_instant_scroll"

    /** Défilement vertical de la grille immédiat (sans animation) : comparaison de fluidité sur la box. */
    @Volatile var instantScroll: Boolean = false

    fun readFrom(intent: android.content.Intent?) {
        instantScroll = runCatching { intent?.getBooleanExtra(EXTRA_INSTANT_SCROLL, false) == true }.getOrDefault(false)
    }
}
