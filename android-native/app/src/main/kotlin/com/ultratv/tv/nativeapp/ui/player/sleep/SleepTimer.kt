package com.ultratv.tv.nativeapp.ui.player.sleep

/** Réglage de la minuterie de veille. */
sealed interface SleepChoice {
    data class Minutes(val minutes: Int) : SleepChoice
    /** Jusqu'à la fin du programme EPG en cours. */
    data class UntilMs(val endMs: Long) : SleepChoice
}

enum class SleepPhase { IDLE, RUNNING, WARNING, EXPIRED }

/**
 * Minuterie de veille : pure, l'horloge est fournie. À [WARNING_MS] de l'échéance passe en WARNING (« Toujours là ? »),
 * puis EXPIRED à l'échéance. Confirmer relance la même durée (pas pour « fin du programme » : on annule).
 */
class SleepTimer {
    var choice: SleepChoice? = null; private set
    var deadlineMs: Long = 0L; private set

    fun arm(c: SleepChoice, nowMs: Long) {
        choice = c
        deadlineMs = when (c) {
            is SleepChoice.Minutes -> nowMs + c.minutes * 60_000L
            is SleepChoice.UntilMs -> c.endMs
        }
    }

    fun cancel() { choice = null; deadlineMs = 0L }

    fun phase(nowMs: Long): SleepPhase = when {
        choice == null -> SleepPhase.IDLE
        nowMs >= deadlineMs -> SleepPhase.EXPIRED
        deadlineMs - nowMs <= WARNING_MS -> SleepPhase.WARNING
        else -> SleepPhase.RUNNING
    }

    fun secondsLeft(nowMs: Long): Int = if (choice == null) 0 else ((deadlineMs - nowMs).coerceAtLeast(0) / 1000).toInt()

    /** « Oui, je suis là » : repart pour la même durée ; « fin du programme » s'arrête là. */
    fun confirmPresence(nowMs: Long) {
        when (val c = choice) {
            is SleepChoice.Minutes -> arm(c, nowMs)
            else -> cancel()
        }
    }

    companion object { const val WARNING_MS = 60_000L }
}
