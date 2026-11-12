package com.ultratv.tv.nativeapp.ui.player

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Défilement accéléré de la position (film / épisode, télécommande), logique PURE : aucune horloge ni lecteur ici, l'heure est
 * passée en paramètre. Même comportement que le module du bureau (mêmes seuils, mêmes bornes).
 *
 * Un appui = ±10 s ; touche maintenue : le pas grandit avec la durée d'appui (10 s, 30 s après 1 s, 60 s après 2,5 s, 120 s
 * après 4 s), plafonné à 2 % de la durée. Appuis rapides (< 600 ms) = cumul. Seule la cible bouge ; le saut réel a lieu à la
 * validation (700 ms après le dernier appui / relâchement, ou tout de suite sur OK) ; Retour annule et revient à l'origine.
 */
object SeekScrubLogic {
    const val CUMUL_MS = 600L
    const val VALIDATE_MS = 700L
    /** Touche toujours enfoncée : validation retardée (filet), le délai de première répétition de certaines télécommandes dépasse 700 ms. */
    const val HELD_NET_MS = 1_500L
    /** Cadence d'une touche maintenue : un pas par 250 ms (sinon la répétition clavier, ~30 Hz, multiplierait le pas par 30). */
    const val REPEAT_TICK_MS = 250L
    /** Durée d'affichage des commandes après le saut avant leur masquage automatique. */
    const val HIDE_AFTER_JUMP_MS = 3_000L

    /** État du défilement en cours (positions en secondes). */
    data class State(
        val origin: Double,
        val target: Double,
        val lastPressAt: Long,
        val pressStartAt: Long,
        /** Dernier pas appliqué (cadence des répétitions). */
        val lastStepAt: Long = pressStartAt,
    ) {
        /** Écart visé par rapport à la position d'avant le défilement. */
        val deltaSec: Double get() = target - origin
    }

    /** Pas (secondes) pour une touche tenue [heldMs] ; plafonné à max(10, 2 % de la durée) si la durée est connue (> 0). */
    fun stepSeconds(heldMs: Long, durationSec: Double): Int {
        val base = when {
            heldMs < 1_000 -> 10
            heldMs < 2_500 -> 30
            heldMs < 4_000 -> 60
            else -> 120
        }
        if (durationSec <= 0) return base
        return min(base.toLong(), max(10L, (durationSec * 0.02).roundToLong())).toInt()
    }

    /** Borne la cible à [0, durée − 1] (durée inconnue : seulement ≥ 0). */
    fun clampTarget(sec: Double, durationSec: Double): Double {
        val hi = if (durationSec > 0) max(0.0, durationSec - 1) else Double.MAX_VALUE
        return sec.coerceIn(0.0, hi)
    }

    /**
     * Un appui. [state] null ou plus ancien que 600 ms (et pas une répétition) : nouveau défilement depuis [currentSec].
     * Sinon cumul sur la cible. Une répétition de touche plus proche que [REPEAT_TICK_MS] du dernier pas n'ajoute rien
     * (mais prolonge l'attente de validation).
     */
    fun press(state: State?, currentSec: Double, durationSec: Double, forward: Boolean, repeat: Boolean, nowMs: Long): State {
        val cont = state != null && (repeat || nowMs - state.lastPressAt < CUMUL_MS)
        val sign = if (forward) 1 else -1
        if (!cont || state == null) {
            val o = currentSec
            return State(o, clampTarget(o + sign * stepSeconds(0, durationSec), durationSec), nowMs, nowMs, nowMs)
        }
        if (repeat && nowMs - state.lastStepAt < REPEAT_TICK_MS) return state.copy(lastPressAt = nowMs)
        val start = if (repeat) state.pressStartAt else nowMs
        val step = stepSeconds(if (repeat) nowMs - start else 0, durationSec)
        return state.copy(target = clampTarget(state.target + sign * step, durationSec), lastPressAt = nowMs, pressStartAt = start, lastStepAt = nowMs)
    }

    /** Relâchement de la touche : l'attente de validation repart de cet instant. */
    fun release(state: State, nowMs: Long): State = state.copy(lastPressAt = nowMs)

    /** Faut-il valider (faire le saut réel) maintenant ? */
    fun shouldCommit(state: State, nowMs: Long, keyDown: Boolean = false): Boolean =
        nowMs - state.lastPressAt >= if (keyDown) HELD_NET_MS else VALIDATE_MS

    /** Annulation (Retour) : position à retrouver, sans aucun saut. */
    fun cancelPosition(state: State): Double = state.origin

    /** Écart affiché : « +2:30 », « −0:40 » (signe moins typographique), « +1:05:00 » au-delà d'une heure. */
    fun fmtDelta(deltaSec: Double): String {
        val a = kotlin.math.abs(deltaSec).roundToLong()
        val sign = if (deltaSec < 0 && a > 0) "−" else "+"
        val h = a / 3600; val m = a % 3600 / 60; val s = a % 60
        fun two(n: Long) = if (n < 10) "0$n" else "$n"
        return if (h > 0) "$sign$h:${two(m)}:${two(s)}" else "$sign$m:${two(s)}"
    }

    // ── Décision de touche (VOD seulement) ────────────────────────────────────────────────────────────────────────────

    enum class Key { BACK, FORWARD, OK, OTHER }

    sealed interface Action {
        /** La touche suit son cours normal (déplacement du focus de Compose). */
        data object Pass : Action
        /** Défilement ; [focusBar] : afficher les commandes avec le focus sur la barre de progression. */
        data class Scrub(val forward: Boolean, val focusBar: Boolean) : Action
        /** OK pendant un défilement : saut immédiat. */
        data object CommitNow : Action
    }

    /**
     * Que faire d'une touche en lecture film / épisode. Gauche / Droite (déjà inversées en RTL par l'appelant) et touches
     * média : commandes masquées → on les affiche, focus sur la barre, et on défile ; focus sur la barre → on défile sans
     * bouger le focus ; focus sur un bouton → la touche circule entre les boutons (Pass), sauf les touches média.
     */
    fun decide(key: Key, mediaKey: Boolean, hidden: Boolean, barFocused: Boolean, scrubbing: Boolean): Action = when (key) {
        Key.BACK, Key.FORWARD -> when {
            hidden -> Action.Scrub(key == Key.FORWARD, focusBar = true)
            mediaKey || barFocused -> Action.Scrub(key == Key.FORWARD, focusBar = false)
            else -> Action.Pass
        }
        Key.OK -> if (scrubbing) Action.CommitNow else Action.Pass
        Key.OTHER -> Action.Pass
    }
}
