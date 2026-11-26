package com.ultratv.tv.nativeapp.ui.startup

import com.ultratv.tv.nativeapp.RemoteLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Trace du démarrage : quels flux ont déjà émis (et à quel instant), pour savoir lequel retarde le premier écran.
 * Logique pure (horloge injectable) ; le journal distant est branché par [StartupTraceLog].
 */
class StartupTrace(private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val t0 = clockMs()
    private val firstEmit = LinkedHashMap<String, Long>()

    /** Flux attendus avant le premier écran : DataStore et Room (providers) séparés, puis les deux flux composés. */
    val expected = listOf("prefs", "providers", "needsSelection", "onboarding.show")

    @Synchronized fun mark(name: String) { firstEmit.getOrPut(name) { clockMs() - t0 } }

    @Synchronized fun elapsedMs(): Long = clockMs() - t0

    @Synchronized fun missing(): List<String> = expected.filter { it !in firstEmit }

    /** « manque prefs, onboarding.show ; reçu providers@4200ms » : court, pour le journal. */
    @Synchronized fun summary(): String {
        val got = firstEmit.entries.joinToString(", ") { "${it.key}@${it.value}ms" }
        val miss = missing().joinToString(", ")
        return buildString {
            append("manque ").append(miss.ifEmpty { "rien" })
            if (got.isNotEmpty()) append(" ; reçu ").append(got)
        }
    }

    companion object {
        /** Au-delà de ce délai, l'apparition du premier écran est journalisée (même seuil que le chargement). */
        const val SLOW_FIRST_SCREEN_MS = StartupWatchdog.LOADING_AFTER_MS
    }
}

/** Instance de l'appli + journal distant (tag « startup »). */
object StartupTraceLog {
    val trace = StartupTrace()
    @Volatile private var firstScreenLogged = false

    fun mark(name: String) = trace.mark(name)

    /** Écran d'erreur affiché : qui n'a pas répondu, au bout de combien de temps. */
    fun logError() = RemoteLog.error("startup", "bloqué après ${trace.elapsedMs()} ms : ${trace.summary()}")

    /** Premier écran affiché ; journalisé seulement s'il a été lent. */
    fun firstScreen() {
        if (firstScreenLogged) return
        firstScreenLogged = true
        val ms = trace.elapsedMs()
        if (ms > StartupTrace.SLOW_FIRST_SCREEN_MS) RemoteLog.warn("startup", "premier écran après $ms ms : ${trace.summary()}")
    }
}

/** Marque la première émission du flux sans le modifier. */
fun <T> Flow<T>.markStartup(name: String): Flow<T> = onEach { StartupTraceLog.mark(name) }

/**
 * Synchro de démarrage : attend que le premier écran soit affiché pour ne pas concurrencer ses lectures Room par des
 * écritures longues, mais jamais plus de [capMs] (la synchro doit partir même si le premier écran tarde).
 * Rend vrai si le premier écran est apparu, faux si le plafond a été atteint.
 */
object StartupSyncGate {
    const val CAP_MS = 20_000L

    suspend fun awaitFirstScreen(shown: StateFlow<Boolean>, capMs: Long = CAP_MS): Boolean =
        withTimeoutOrNull(capMs) { shown.first { it } } == true
}

/** Confirmation en deux temps de « Réinitialiser les données locales » (logique pure, testée). */
object ResetConfirmation {
    enum class Step { IDLE, CONFIRMING }

    /** Premier appui : demande confirmation ; « Annuler » : retour ; seul « Oui, tout effacer » (étape CONFIRMING) réinitialise. */
    fun onResetPressed(step: Step): Step = Step.CONFIRMING
    fun onCancel(step: Step): Step = Step.IDLE
    fun shouldReset(step: Step): Boolean = step == Step.CONFIRMING

    /** Texte de confirmation : prévient de la perte des sources et réglages si l'appareil n'est pas appairé au cloud. */
    fun body(w: StartupWatchdog.Words, paired: Boolean): String = if (paired) w.confirmPaired else w.confirmUnpaired
}
