package com.ultratv.tv.nativeapp.ui.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Premier écran : jamais de vide éternel (chargement puis erreur) et jamais de null éternel sur une exception de flux. */
class StartupGateTest {
    @Test fun `phases - vide puis chargement puis erreur`() {
        assertEquals(StartupWatchdog.Phase.BLANK, StartupWatchdog.phase(0))
        assertEquals(StartupWatchdog.Phase.BLANK, StartupWatchdog.phase(StartupWatchdog.LOADING_AFTER_MS - 1))
        assertEquals(StartupWatchdog.Phase.LOADING, StartupWatchdog.phase(StartupWatchdog.LOADING_AFTER_MS))
        assertEquals(StartupWatchdog.Phase.LOADING, StartupWatchdog.phase(StartupWatchdog.ERROR_AFTER_MS - 1))
        assertEquals(StartupWatchdog.Phase.ERROR, StartupWatchdog.phase(StartupWatchdog.ERROR_AFTER_MS))
        assertEquals(StartupWatchdog.Phase.ERROR, StartupWatchdog.phase(10 * 60_000L))
        assertTrue(StartupWatchdog.LOADING_AFTER_MS < StartupWatchdog.ERROR_AFTER_MS)
    }

    @Test fun `textes d'erreur - boutons Reessayer et Reinitialiser en francais, anglais par defaut`() {
        val fr = StartupWatchdog.text("fr")
        assertEquals("Réessayer", fr.retry)
        assertEquals("Réinitialiser les données locales", fr.reset)
        assertEquals("Retry", StartupWatchdog.text("de").retry)
        assertEquals("Reset local data", StartupWatchdog.text("EN").reset)
    }

    @Test fun `une exception de flux est journalisee et donne la valeur de repli`() = runBlocking {
        val logs = mutableListOf<String>()
        val src = flow<Boolean> { throw IllegalStateException("base fermee") }
        assertEquals(false, src.catchWithFallback("needsSelection", false) { logs += it }.first())
        assertEquals(1, logs.size)
        assertTrue(logs[0].contains("needsSelection") && logs[0].contains("IllegalStateException"))
    }

    @Test fun `un flux sain passe tel quel et sans journal`() = runBlocking {
        val logs = mutableListOf<String>()
        val src = flow { emit(true); emit(false) }
        assertEquals(listOf(true, false), src.catchWithFallback("x", false) { logs += it }.toList())
        assertTrue(logs.isEmpty())
    }
}
