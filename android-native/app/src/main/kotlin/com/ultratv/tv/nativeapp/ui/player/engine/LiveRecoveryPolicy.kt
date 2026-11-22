package com.ultratv.tv.nativeapp.ui.player.engine

/**
 * Reconnexions d'un direct qui jouait (logique pure, testée).
 *
 * Avant : le compteur repartait de zéro après 30 s de lecture, si bien qu'un flux qui se figeait toutes les 35 s se
 * reconnectait sans fin, toujours « #1 » en 500 ms (boucle gel / reconnexion / gel relevée sur la box en 1.3.73-rc7).
 * Maintenant :
 *  - le délai dépend du nombre de reconnexions des 2 dernières minutes (500 ms, 1 s, 2 s… 10 s) ;
 *  - plafond : 8 reconnexions en 2 min ou 16 en 10 min → abandon (écran d'erreur) ;
 *  - 3 pannes ExoPlayer (gel, chargement bloqué, erreur — pas une fin de session du serveur) en 2 min sur la même
 *    chaîne → bascule sur VLC, si le moteur VLC est permis pour cette lecture.
 */
class LiveRecoveryPolicy(
    private val shortWindowMs: Long = 120_000L,
    private val longWindowMs: Long = 600_000L,
    private val maxLong: Int = 16,
    private val exoFailuresForVlc: Int = 3,
) {
    enum class Cause { ENDED, FROZEN, STALL, ERROR }

    sealed interface Decision {
        data class Reconnect(val delayMs: Long, val attempt: Int) : Decision
        data object SwitchToVlc : Decision
        data object GiveUp : Decision
    }

    private val attempts = ArrayDeque<Long>()
    private val exoFailures = ArrayDeque<Long>()

    fun onFailure(nowMs: Long, engine: EngineKind, cause: Cause, vlcAllowed: Boolean): Decision {
        while (attempts.isNotEmpty() && nowMs - attempts.first() >= longWindowMs) attempts.removeFirst()
        while (exoFailures.isNotEmpty() && nowMs - exoFailures.first() >= shortWindowMs) exoFailures.removeFirst()
        val recent = attempts.count { nowMs - it < shortWindowMs }
        val delayMs = PlaybackPlanner.liveReconnectDelayMs(recent)
        if (delayMs == null || attempts.size >= maxLong) return Decision.GiveUp
        if (engine == EngineKind.EXO && cause != Cause.ENDED) {
            exoFailures.addLast(nowMs)
            if (vlcAllowed && exoFailures.size >= exoFailuresForVlc) {
                exoFailures.clear(); attempts.addLast(nowMs)
                return Decision.SwitchToVlc
            }
        }
        attempts.addLast(nowMs)
        return Decision.Reconnect(delayMs, recent + 1)
    }

    fun reset() { attempts.clear(); exoFailures.clear() }
}

/**
 * Chaînes basculées sur VLC après des pannes ExoPlayer répétées : retenu pour la durée de vie de l'appli seulement
 * (jamais écrit sur disque — le fournisseur peut avoir réparé son flux au prochain lancement).
 */
class LiveEngineMemo {
    private val keys = java.util.Collections.synchronizedSet(HashSet<String>())
    fun preferVlc(key: String) { keys += key }
    fun prefersVlc(key: String) = key in keys
    fun clear() = keys.clear()

    companion object { val session = LiveEngineMemo() }
}
