package com.ultratv.tv.nativeapp.ui.common

/**
 * Fenêtre ouverte par un APPUI LONG sur OK (menu d'actions d'une chaîne, d'un épisode…) : la touche est encore enfoncée
 * quand la fenêtre prend le focus. Ses répétitions puis son relâchement arrivaient à la première ligne, qui se
 * validait toute seule (« Ajouter aux favoris » puis fermeture, retour Reddit). Tant qu'un NOUVEL appui (répétition 0)
 * n'a pas été vu dans la fenêtre, les événements OK sont ignorés. Logique pure, testée.
 */
class HeldKeyGuard {
    private var armed = false

    /** Vrai : l'événement est transmis à la fenêtre ; faux : il est avalé. [isOk] = OK / Entrée / A de manette. */
    fun allow(isOk: Boolean, down: Boolean, repeat: Int): Boolean {
        if (!isOk) return true
        if (down && repeat == 0) { armed = true; return true }
        return armed
    }
}
