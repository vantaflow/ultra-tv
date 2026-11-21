package com.ultratv.tv.nativeapp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Tâches de fond des portées GLOBALES (application, objets) : une exception y est journalisée (étiquette, classe, message
 * nettoyé) au lieu de remonter au fil et de tuer le processus — au démarrage, c'était un plantage de l'appli ; sous
 * Robolectric, une tâche qui survivait à son test plantait dans le SUIVANT (« UncaughtExceptionsBeforeTest »).
 * L'annulation ([CancellationException]) n'est jamais avalée.
 */
internal object BackgroundTasks {
    /** Dernier secours d'une portée globale : ce qu'une tâche n'a pas rattrapé elle-même est journalisé, jamais fatal. */
    fun handler(scope: String): CoroutineExceptionHandler = CoroutineExceptionHandler { _, e -> report(scope, e) }

    /** Ligne de journal sans donnée sensible : étiquette, classe d'exception, message nettoyé (adresses, identifiants) et court. */
    fun describe(label: String, e: Throwable): String {
        val msg = LogSanitizer.sanitize((e.message ?: "").replace(Regex("\\s+"), " ").trim(), max = 160)
        return "$label ${e.javaClass.simpleName}" + if (msg.isEmpty()) "" else ": $msg"
    }

    fun report(label: String, e: Throwable) {
        if (e is CancellationException) return
        val line = describe(label, e)
        // Jamais d'exception depuis le rapport lui-même (environnement de test détruit, journal pas encore prêt).
        runCatching { android.util.Log.w("UltraBg", line) }
        runCatching { RemoteLog.error("bg", line) }
    }

    /** Exécute [block] : son échec est journalisé sous [label] et n'interrompt rien d'autre ; l'annulation remonte. */
    suspend fun <T> guard(label: String, block: suspend () -> T): T? = try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        report(label, t); null
    }

    /** Lance une tâche protégée individuellement dans [scope] (l'échec de l'une n'empêche pas les suivantes). */
    fun launch(scope: CoroutineScope, label: String, block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch { guard(label) { block() } }
}
