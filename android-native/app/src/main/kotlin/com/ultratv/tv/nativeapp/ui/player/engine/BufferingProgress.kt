package com.ultratv.tv.nativeapp.ui.player.engine

/**
 * Relevé de progression réelle de la lecture : images sorties du décodeur ([frames], -1 si inconnu) et position ([positionMs]).
 */
data class ProgressMark(val frames: Long, val positionMs: Long)

/**
 * Le moteur dit « chargement » mais la lecture avance-t-elle vraiment ? (logique pure, testée)
 *
 * Constat box (1.3.73-rc9, direct dont les horodatages reculent) : après un recul d'horodatage ou un changement de surface,
 * le moteur peut signaler un chargement sans jamais repasser « prêt » alors que les images sont rendues et que le son
 * continue — « Recharger » restait affiché sur une vidéo qui joue. Un vrai chargement ne sort aucune image et n'avance pas
 * la position ; des images rendues ([MIN_FRAMES] ou plus) ou une position qui bouge (dans un sens ou dans l'autre : un recul
 * d'horodatage fait repartir la position en arrière, la lecture continue) prouvent la lecture.
 */
object BufferingProgress {
    /** Images minimales entre deux relevés pour conclure que la vidéo défile (une image isolée peut sortir au rechargement). */
    const val MIN_FRAMES = 2L
    /** Écart de position minimal entre deux relevés pour conclure que la lecture (son compris) avance. */
    const val MIN_POSITION_MS = 250L

    fun moved(prev: ProgressMark?, now: ProgressMark?): Boolean {
        if (prev == null || now == null) return false
        if (prev.frames >= 0 && now.frames >= 0 && now.frames - prev.frames >= MIN_FRAMES) return true
        return kotlin.math.abs(now.positionMs - prev.positionMs) >= MIN_POSITION_MS
    }
}
