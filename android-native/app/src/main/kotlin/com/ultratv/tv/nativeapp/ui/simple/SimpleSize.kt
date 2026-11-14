package com.ultratv.tv.nativeapp.ui.simple

/** Taille de l'affichage du mode simple : l'échelle s'applique à tout l'écran simple (textes, tuiles, boutons). */
enum class SimpleSize(val key: String, val scale: Float) {
    // « Grande » = affichage d'origine (déjà conçu pour remplir la TV : plus grand, les textes débordaient).
    SMALL("small", 0.8f), NORMAL("normal", 0.9f), LARGE("large", 1f);
    companion object { fun parse(raw: String?) = entries.firstOrNull { it.key == raw } ?: LARGE }
}
