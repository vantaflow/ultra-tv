package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TraktScrobblerTest {
    private class FakeTransport(var paired: Boolean = true, var result: ScrobbleResult = ScrobbleResult.LINKED) : TraktTransport {
        val sent = mutableListOf<ScrobbleRequest>()
        override val isPaired get() = paired
        override suspend fun send(req: ScrobbleRequest): ScrobbleResult { sent += req; return result }
    }

    private class FakeResolver : TraktIdentityResolver {
        var calls = 0
        override suspend fun resolve(item: PlaybackContext.Item): TraktIdentity? { calls++; return TraktIdentity("movie", item.title, 2020) }
    }

    private fun item(kind: String = "MOVIE", rid: String = "1") =
        PlaybackContext.Item(providerId = 1, kind = kind, remoteId = rid, title = "Titre", poster = null, streamUrl = "u")

    private fun TestScope.build(t: FakeTransport = FakeTransport(), r: FakeResolver = FakeResolver()) =
        Triple(TraktScrobbler(t, r, StandardTestDispatcher(testScheduler), pauseDelayMs = 3_000), t, r)

    private fun actions(t: FakeTransport) = t.sent.map { it.action }

    @Test fun onTick_lectureContinue_envoieUnSeulStart() = runTest {
        val (s, t, _) = build()
        repeat(5) { s.onTick(item(), true, it * 500L, 100_000, it * 500L) }
        advanceUntilIdle()
        assertEquals(listOf("start"), actions(t))
    }

    @Test fun onTick_pauseUtilisateurPuisReprise_envoiePauseApresDelaiPuisStart() = runTest {
        val (s, t, _) = build()
        s.onTick(item(), true, 1_000, 100_000, 0)
        s.onTick(item(), false, 1_000, 100_000, 1_000)   // trop tôt : buffering possible
        s.onTick(item(), false, 1_000, 100_000, 4_500)
        s.onTick(item(), false, 1_000, 100_000, 5_000)   // doublon
        s.onTick(item(), true, 1_000, 100_000, 6_000)
        advanceUntilIdle()
        assertEquals(listOf("start", "pause", "start"), actions(t))
    }

    @Test fun onTick_bufferingCourt_nenvoiePasDePause() = runTest {
        val (s, t, _) = build()
        s.onTick(item(), true, 1_000, 100_000, 0)
        s.onTick(item(), false, 1_000, 100_000, 500)
        s.onTick(item(), true, 1_000, 100_000, 1_500)
        advanceUntilIdle()
        assertEquals(listOf("start"), actions(t))
    }

    @Test fun onPause_arrierePlan_envoiePauseImmediate() = runTest {
        val (s, t, _) = build()
        s.onTick(item(), true, 10_000, 100_000, 0)
        s.onPause(item(), 10_000, 100_000)
        s.onPause(item(), 10_000, 100_000)
        advanceUntilIdle()
        assertEquals(listOf("start", "pause"), actions(t))
    }

    @Test fun onStop_envoieStopAvecProgressionFinale() = runTest {
        val (s, t, _) = build()
        s.onTick(item(), true, 1_000, 200_000, 0)
        s.onStop(item(), 50_000, 200_000)
        s.onStop(item(), 50_000, 200_000)   // doublon ignoré
        advanceUntilIdle()
        assertEquals(listOf("start", "stop"), actions(t))
        assertEquals(25.0, t.sent.last().progress, 0.001)
    }

    @Test fun onStop_sansStartPrealable_neRienEnvoie() = runTest {
        val (s, t, _) = build()
        s.onStop(item(), 50_000, 200_000)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), actions(t))
    }

    @Test fun onTick_directEtRelecture_ignores() = runTest {
        val (s, t, r) = build()
        s.onTick(item("LIVE"), true, 1_000, 100_000, 0)
        s.onStop(item("LIVE"), 1_000, 100_000)
        advanceUntilIdle()
        assertEquals(0, t.sent.size)
        assertEquals(0, r.calls)
    }

    @Test fun onTick_dureeInconnue_attendLaDuree() = runTest {
        val (s, t, _) = build()
        s.onTick(item(), true, 0, -1, 0)
        s.onTick(item(), true, 500, 0, 500)
        s.onTick(item(), true, 1_000, 100_000, 1_000)
        advanceUntilIdle()
        assertEquals(listOf("start"), actions(t))
    }

    @Test fun onTick_nonAppaire_neRienEnvoie() = runTest {
        val (s, t, _) = build(FakeTransport(paired = false))
        s.onTick(item(), true, 1_000, 100_000, 0)
        advanceUntilIdle()
        assertEquals(0, t.sent.size)
    }

    @Test fun onTick_compteNonLie_arreteLesEnvoisPourCetElementPuisReprendAuSuivant() = runTest {
        val (s, t, _) = build(FakeTransport(result = ScrobbleResult.NOT_LINKED))
        s.onTick(item(), true, 1_000, 100_000, 0)
        advanceUntilIdle()
        s.onPause(item(), 1_000, 100_000)
        s.onStop(item(), 1_000, 100_000)
        advanceUntilIdle()
        assertEquals(listOf("start"), actions(t))
        s.onTick(item(rid = "2"), true, 1_000, 100_000, 10_000)   // nouvel élément : on retente
        advanceUntilIdle()
        assertEquals(listOf("start", "start"), actions(t))
    }

    @Test fun onTick_echecReseau_nestPasFatal() = runTest {
        val (s, t, _) = build(FakeTransport(result = ScrobbleResult.FAILED))
        s.onTick(item(), true, 1_000, 100_000, 0)
        s.onStop(item(), 2_000, 100_000)
        advanceUntilIdle()
        assertEquals(listOf("start", "stop"), actions(t))
    }

    @Test fun onTick_identiteResolueUneSeuleFoisParElement() = runTest {
        val (s, _, r) = build()
        s.onTick(item(), true, 1_000, 100_000, 0)
        s.onPause(item(), 1_000, 100_000)
        s.onStop(item(), 1_000, 100_000)
        advanceUntilIdle()
        assertEquals(1, r.calls)
    }
}
