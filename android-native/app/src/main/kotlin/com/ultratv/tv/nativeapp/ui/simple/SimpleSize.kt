package com.ultratv.tv.nativeapp.ui.simple

/** Taille de l'affichage du mode simple : l'échelle s'applique à tout l'écran simple (textes, tuiles, boutons). */
enum class SimpleSize(val key: String, val scale: Float) {
    SMALL("small", 0.82f), NORMAL("normal", 1f), LARGE("large", 1.15f);
    companion object { fun parse(raw: String?) = entries.firstOrNull { it.key == raw } ?: NORMAL }
}
