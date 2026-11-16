package com.ultratv.tv.nativeapp.ui.player

import android.view.KeyEvent

/** Action déduite d'une touche média (casque Bluetooth, télécommande, clavier). */
enum class MediaKeyAction { TOGGLE, PLAY, PAUSE, SEEK_FORWARD, SEEK_BACK, NEXT, PREVIOUS, NONE }

/** Ce que « suivant / précédent » fait selon le contenu. */
enum class SkipTarget { CHANNEL_NEXT, CHANNEL_PREVIOUS, EPISODE_NEXT, EPISODE_PREVIOUS, NONE }

/** Règles pures de routage des touches média : touche + état → action. Aucune dépendance au moteur. */
object MediaKeyRouter {
    /** Touche → action. Le volume n'en fait pas partie : il reste au système (flux média). */
    fun actionFor(keyCode: Int): MediaKeyAction = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> MediaKeyAction.TOGGLE
        KeyEvent.KEYCODE_MEDIA_PLAY -> MediaKeyAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaKeyAction.PAUSE
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> MediaKeyAction.SEEK_FORWARD
        KeyEvent.KEYCODE_MEDIA_REWIND -> MediaKeyAction.SEEK_BACK
        KeyEvent.KEYCODE_MEDIA_NEXT -> MediaKeyAction.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaKeyAction.PREVIOUS
        else -> MediaKeyAction.NONE
    }

    /** Vrai pour les touches que la session traite elle-même (suivant / précédent) ; le reste va au lecteur de la session. */
    fun isSkip(keyCode: Int): Boolean = actionFor(keyCode).let { it == MediaKeyAction.NEXT || it == MediaKeyAction.PREVIOUS }

    /** Lecture voulue après l'action ; null si l'action ne touche pas à la lecture. */
    fun wantPlaying(action: MediaKeyAction, enginePlaying: Boolean): Boolean? = when (action) {
        MediaKeyAction.TOGGLE -> !enginePlaying
        MediaKeyAction.PLAY -> true
        MediaKeyAction.PAUSE -> false
        else -> null
    }

    /** Suivant / précédent : chaîne en direct ; épisode suivant / précédent s'il existe en différé ; sinon rien. */
    fun skipTarget(action: MediaKeyAction, live: Boolean, hasNextEpisode: Boolean, hasPreviousEpisode: Boolean): SkipTarget {
        val forward = when (action) {
            MediaKeyAction.NEXT -> true
            MediaKeyAction.PREVIOUS -> false
            else -> return SkipTarget.NONE
        }
        return when {
            live -> if (forward) SkipTarget.CHANNEL_NEXT else SkipTarget.CHANNEL_PREVIOUS
            forward -> if (hasNextEpisode) SkipTarget.EPISODE_NEXT else SkipTarget.NONE
            else -> if (hasPreviousEpisode) SkipTarget.EPISODE_PREVIOUS else SkipTarget.NONE
        }
    }

    /** Seuls les appuis initiaux comptent (pas les répétitions ni le relâchement) pour suivant / précédent. */
    fun isInitialPress(down: Boolean, repeatCount: Int): Boolean = down && repeatCount == 0
}

/**
 * Anti-doublon : un même appui peut arriver par la fenêtre (touche) ET par la session média ; la même direction reçue
 * moins de [windowMs] après la précédente est ignorée.
 */
class SkipDebounce(private val windowMs: Long = 600L) {
    private var lastForward: Boolean? = null
    private var lastAt = Long.MIN_VALUE / 2

    fun accept(forward: Boolean, nowMs: Long): Boolean {
        if (lastForward == forward && nowMs - lastAt in 0 until windowMs) return false
        lastForward = forward; lastAt = nowMs
        return true
    }
}
