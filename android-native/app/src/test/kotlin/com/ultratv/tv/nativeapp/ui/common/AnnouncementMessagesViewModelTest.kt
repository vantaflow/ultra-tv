package com.ultratv.tv.nativeapp.ui.common

import com.ultratv.tv.nativeapp.data.announce.Announcement
import com.ultratv.tv.nativeapp.data.announce.AnnouncementFeed
import com.ultratv.tv.nativeapp.data.announce.AnnouncementPlan
import com.ultratv.tv.nativeapp.data.announce.FetchOutcome
import com.ultratv.tv.nativeapp.data.announce.ReceivedAnnouncement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Bouton « Rafraîchir » de Réglages › Messages : succès, échec (hors ligne / serveur injoignable), pas de double appel. */
@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementMessagesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val a1 = Announcement("a1", "Titre", "Nouveau message", "info")
    private val now = 1_763_800_000_000L

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(feed: AnnouncementFeed) = AnnouncementMessagesViewModel(feed, MutableStateFlow(emptyList<ReceivedAnnouncement>()))

    @Test fun succes_metAJourLesAlertesEtLaDerniereVerification() = runTest(dispatcher) {
        val audiences = mutableListOf<String>()
        val feed = AnnouncementFeed(fetcher = { audiences += it; FetchOutcome.Ok(listOf(a1)) }, clock = { now })
        val vm = vm(feed)
        vm.refreshNow("tv")
        assertTrue("Actualisation… pendant l'appel", vm.ui.value.refreshing)
        advanceUntilIdle()
        assertFalse(vm.ui.value.refreshing)
        assertNull(vm.ui.value.error)
        assertEquals(listOf(a1), feed.items.value)
        assertEquals(now, vm.ui.value.lastCheckAt)
        assertEquals(listOf("tv"), audiences)
    }

    @Test fun echecHorsLigne_messageClairEtReessaiPossible() = runTest(dispatcher) {
        var outcome: FetchOutcome = FetchOutcome.Offline
        val feed = AnnouncementFeed(fetcher = { outcome }, clock = { now })
        val vm = vm(feed)
        vm.refreshNow("tv")
        advanceUntilIdle()
        assertFalse(vm.ui.value.refreshing)
        assertEquals(RefreshError.OFFLINE, vm.ui.value.error)
        assertNull("aucune vérification réussie", vm.ui.value.lastCheckAt)
        // Réessayer : nouvel appel, l'erreur disparaît au succès.
        outcome = FetchOutcome.Ok(listOf(a1))
        vm.refreshNow("tv")
        advanceUntilIdle()
        assertNull(vm.ui.value.error)
        assertEquals(listOf(a1), feed.items.value)
    }

    @Test fun echecServeur_gardeLesAlertesConnues() = runTest(dispatcher) {
        var outcome: FetchOutcome = FetchOutcome.Ok(listOf(a1))
        val feed = AnnouncementFeed(fetcher = { outcome }, clock = { now })
        val vm = vm(feed)
        vm.refreshNow("mobile"); advanceUntilIdle()
        outcome = FetchOutcome.Unreachable
        vm.refreshNow("mobile"); advanceUntilIdle()
        assertEquals(RefreshError.UNREACHABLE, vm.ui.value.error)
        assertEquals(listOf(a1), feed.items.value)
        assertEquals(now, vm.ui.value.lastCheckAt)
    }

    @Test fun pasDeDoubleAppel_pendantUneActualisation() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val feed = AnnouncementFeed(fetcher = { calls++; gate.await(); FetchOutcome.Ok(listOf(a1)) }, clock = { now })
        val vm = vm(feed)
        vm.refreshNow("tv")
        advanceUntilIdle()
        vm.refreshNow("tv")
        vm.refreshNow("tv")
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(vm.ui.value.refreshing)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.ui.value.refreshing)
        assertEquals(1, calls)
    }

    @Test fun flux_appelleLeRappelDeNettoyageSeulementAuSucces() = runTest(dispatcher) {
        val fetched = mutableListOf<List<Announcement>>()
        var outcome: FetchOutcome = FetchOutcome.Unreachable
        val feed = AnnouncementFeed(fetcher = { outcome }, clock = { now }, onFetched = { fetched += it })
        feed.check("tv")
        assertTrue(fetched.isEmpty())
        outcome = FetchOutcome.Ok(listOf(a1))
        feed.check("tv")
        assertEquals(listOf(listOf(a1)), fetched)
    }

    /** Une alerte reçue par Rafraîchir suit les règles habituelles : déjà vue → pas réaffichée. */
    @Test fun alerteDejaVue_pasReaffichee() {
        assertNull(AnnouncementPlan.next(listOf(a1), seen = setOf("a1"), shownThisLaunch = emptySet()))
        assertEquals(a1, AnnouncementPlan.next(listOf(a1), seen = emptySet(), shownThisLaunch = emptySet()))
    }
}
