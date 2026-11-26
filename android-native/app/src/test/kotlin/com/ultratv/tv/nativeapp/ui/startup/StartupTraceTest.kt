package com.ultratv.tv.nativeapp.ui.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupTraceTest {
    @Test fun `seuil d'erreur a 30 s et chargement a 3 s`() {
        assertEquals(30_000L, StartupWatchdog.ERROR_AFTER_MS)
        assertEquals(StartupWatchdog.Phase.LOADING, StartupWatchdog.phase(29_999))
        assertEquals(StartupWatchdog.Phase.ERROR, StartupWatchdog.phase(30_000))
    }

    @Test fun `la trace dit quels flux manquent et quand les autres ont emis`() {
        var now = 1_000L
        val t = StartupTrace { now }
        now += 200; t.mark("providers")
        now += 100; t.mark("providers") // seule la première émission compte
        now += 5_000
        assertEquals(listOf("prefs", "needsSelection", "onboarding.show"), t.missing())
        assertEquals(5_300L, t.elapsedMs())
        val s = t.summary()
        assertTrue(s, s.contains("manque prefs, needsSelection, onboarding.show"))
        assertTrue(s, s.contains("providers@200ms"))
    }

    @Test fun `trace complete - rien ne manque`() {
        val t = StartupTrace { 0L }
        t.expected.forEach { t.mark(it) }
        assertTrue(t.missing().isEmpty())
        assertTrue(t.summary().startsWith("manque rien"))
    }

    @Test fun `la synchro attend le premier ecran`() = runBlocking {
        val shown = MutableStateFlow(false)
        val job = launch { shown.value = true }
        assertTrue(StartupSyncGate.awaitFirstScreen(shown, capMs = 5_000))
        job.join()
    }

    @Test fun `la synchro demarre quand meme au plafond`() = runBlocking {
        assertFalse(StartupSyncGate.awaitFirstScreen(MutableStateFlow(false), capMs = 50))
        assertTrue(StartupSyncGate.awaitFirstScreen(MutableStateFlow(true), capMs = 50))
        assertEquals(20_000L, StartupSyncGate.CAP_MS)
    }

    @Test fun `reinitialiser demande une confirmation, annuler revient`() {
        var step = ResetConfirmation.Step.IDLE
        assertFalse(ResetConfirmation.shouldReset(step))
        step = ResetConfirmation.onResetPressed(step)
        assertEquals(ResetConfirmation.Step.CONFIRMING, step)
        assertTrue(ResetConfirmation.shouldReset(step))
        assertEquals(ResetConfirmation.Step.IDLE, ResetConfirmation.onCancel(step))
    }

    @Test fun `le texte de confirmation previent de la perte si non appaire, dans les 3 langues`() {
        for (lang in listOf("fr", "es", "en")) {
            val w = StartupWatchdog.text(lang)
            assertNotEquals(lang, ResetConfirmation.body(w, true), ResetConfirmation.body(w, false))
            assertTrue(w.confirmYes.isNotBlank() && w.cancel.isNotBlank())
        }
        assertTrue(ResetConfirmation.body(StartupWatchdog.text("fr"), false).contains("perdus"))
    }
}
