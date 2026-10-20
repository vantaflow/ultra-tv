package com.ultratv.tv.nativeapp.data.downloads

/**
 * Machine d'états d'un téléchargement (fonction pure, testée) :
 *
 * ```
 *   QUEUED ──START──▶ RUNNING ──COMPLETE──▶ COMPLETED
 *     ▲  │              │  │
 *     │  └──PAUSE──┐    │  └──FAIL──▶ FAILED ──RETRY──▶ QUEUED
 *     │            ▼    │
 *     └──RESUME── PAUSED ◀──PAUSE
 *     ▲                 │
 *     └───INTERRUPT─────┘   (réseau perdu, Wi-Fi exigé, processus arrêté : le partiel est gardé)
 * ```
 * Annuler / supprimer est permis depuis n'importe quel état : la ligne et les fichiers disparaissent (pas d'état).
 */
object DownloadState {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val PAUSED = "PAUSED"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"

    enum class Event { START, COMPLETE, FAIL, PAUSE, RESUME, RETRY, INTERRUPT }

    /** État suivant, ou null si l'événement n'a pas de sens dans cet état (il est alors ignoré). */
    fun next(from: String, event: Event): String? = when (event) {
        Event.START -> if (from == QUEUED) RUNNING else null
        Event.COMPLETE -> if (from == RUNNING) COMPLETED else null
        Event.FAIL -> if (from == RUNNING || from == QUEUED) FAILED else null
        Event.PAUSE -> if (from == QUEUED || from == RUNNING) PAUSED else null
        Event.RESUME -> if (from == PAUSED) QUEUED else null
        Event.RETRY -> if (from == FAILED) QUEUED else null
        Event.INTERRUPT -> if (from == RUNNING) QUEUED else null
    }

    /** En file ou en cours : compte comme « actif » (barre de progression, connexion occupée). */
    fun isActive(state: String) = state == QUEUED || state == RUNNING
}
