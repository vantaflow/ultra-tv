package com.ultratv.tv.nativeapp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Tâches de fond des portées globales : un échec est journalisé, ne remonte jamais au fil (plantage au démarrage, ou test
 * suivant en échec sous Robolectric) et n'empêche pas les tâches suivantes ; l'annulation n'est pas avalée.
 */
class BackgroundTasksTest {
    /** Exception non rattrapée vue par le fil (ce qu'aurait vu le processus ou kotlinx-coroutines-test). */
    private fun <T> catchingUncaught(block: () -> T): Pair<T, Throwable?> {
        val seen = AtomicReference<Throwable?>(null)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> seen.set(e) }
        try { return block() to seen.get() } finally { Thread.setDefaultUncaughtExceptionHandler(prev) }
    }

    @Test fun echecDUneTache_journalise_neRemontePas_etLaSuivanteTourne() {
        val (ran, uncaught) = catchingUncaught {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + BackgroundTasks.handler("test"))
            val second = CompletableDeferred<Boolean>()
            BackgroundTasks.launch(scope, "t1") { error("échec au démarrage") }
            BackgroundTasks.launch(scope, "t2") { second.complete(true) }
            runBlocking { withTimeout(5_000) { second.await() } }.also { Thread.sleep(100); scope.cancel() }
        }
        assertTrue(ran)
        assertEquals("aucune exception ne doit remonter au fil", null, uncaught)
    }

    @Test fun echecApresAnnulation_commeUneTacheQuiSurvitASonTest_neRemontePas() {
        // Tâche bloquante (graphe Hilt) dont la portée est annulée pendant qu'elle tourne, puis qui échoue : c'est la trace de
        // LiveOsdFocusTest (StandaloneCoroutine{Cancelled} + NullPointerException dans getSharedPreferences).
        val (_, uncaught) = catchingUncaught {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + BackgroundTasks.handler("test"))
            val started = CompletableDeferred<Unit>()
            val job = BackgroundTasks.launch(scope, "tardive") { started.complete(Unit); Thread.sleep(200); throw NullPointerException("sSharedPrefsCache is null") }
            runBlocking { started.await(); scope.cancel(); job.join() }
        }
        assertEquals(null, uncaught)
    }

    @Test fun gestionnaireDeLaPortee_rattrapeUneTacheNonProtegee() {
        val (_, uncaught) = catchingUncaught {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + BackgroundTasks.handler("test"))
            val job = scope.launch { error("tâche lancée sans protection") }
            runBlocking { job.join() }
        }
        assertEquals(null, uncaught)
    }

    @Test fun annulation_nEstPasAvalee() {
        var after = false
        val thrown = runCatching { runBlocking { BackgroundTasks.guard("x") { throw CancellationException("stop") }; after = true } }.exceptionOrNull()
        assertTrue("CancellationException doit remonter : $thrown", thrown is CancellationException)
        assertFalse(after)
    }

    @Test fun ligneDeJournal_sansDonneeSensible() {
        val line = BackgroundTasks.describe("app.prefs", java.io.IOException("échec http://user:secret@h.example/live/u/p/1.ts?password=abc\n suite"))
        assertTrue(line, line.startsWith("app.prefs IOException: "))
        assertFalse(line, line.contains("secret") || line.contains("abc") || line.contains("\n"))
        assertTrue(line.length < 220)
    }
}
