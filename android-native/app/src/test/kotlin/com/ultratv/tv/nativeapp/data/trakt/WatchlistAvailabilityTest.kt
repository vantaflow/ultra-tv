package com.ultratv.tv.nativeapp.data.trakt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Détection locale des titres de la watchlist devenus disponibles dans le catalogue (« Disponible de ta liste »). */
class WatchlistAvailabilityTest {
    // ── « Disponible de ta liste » ──

    private val t0 = 1_000_000_000L

    @Test fun premierCalcul_poseLaReferenceSansRienSignaler() {
        val r = TraktWatchlistTracker.update(null, listOf("m:a", "m:b"), setOf("m:a"), t0)
        assertTrue(r.newly.isEmpty())
        assertEquals(setOf("m:b"), r.state.unavailable)
        assertTrue(r.state.recent.isEmpty())
    }

    @Test fun titreDevenuDisponible_signaleUneFois_puisResteDansLaRangee() {
        val ids = listOf("m:a", "m:b")
        val base = TraktWatchlistTracker.update(null, ids, setOf("m:a"), t0).state
        val r1 = TraktWatchlistTracker.update(base, ids, setOf("m:a", "m:b"), t0 + 1000)
        assertEquals(listOf("m:b"), r1.newly)
        assertEquals(mapOf("m:b" to t0 + 1000), r1.state.recent)
        // Synchro suivante : plus de notification, le titre reste dans la rangée.
        val r2 = TraktWatchlistTracker.update(r1.state, ids, setOf("m:a", "m:b"), t0 + 2000)
        assertTrue(r2.newly.isEmpty())
        assertEquals(setOf("m:b"), r2.state.recent.keys)
        // Au-delà de 14 jours : il quitte la rangée.
        val r3 = TraktWatchlistTracker.update(r2.state, ids, setOf("m:a", "m:b"), t0 + 1000 + TraktWatchlistTracker.RECENT_MS + 1)
        assertTrue(r3.state.recent.isEmpty())
    }

    @Test fun titreDejaDisponibleQuandIlEntreDansLaListe_nEstPasNouveau() {
        val base = TraktWatchlistTracker.update(null, listOf("m:a"), setOf("m:a"), t0).state
        val r = TraktWatchlistTracker.update(base, listOf("m:a", "m:z"), setOf("m:a", "m:z"), t0 + 5)
        assertTrue(r.newly.isEmpty())
    }

    @Test fun titreRetireDeLaListeOuDeLaSource_disparait() {
        val ids = listOf("m:a", "m:b")
        val base = TraktWatchlistTracker.update(null, ids, emptySet(), t0).state
        val r1 = TraktWatchlistTracker.update(base, ids, setOf("m:a", "m:b"), t0 + 1)
        assertEquals(setOf("m:a", "m:b"), r1.state.recent.keys)
        val r2 = TraktWatchlistTracker.update(r1.state, listOf("m:a"), setOf("m:a"), t0 + 2)
        assertEquals(setOf("m:a"), r2.state.recent.keys)
        // Disparu du catalogue puis de retour : redevient une nouveauté.
        val gone = TraktWatchlistTracker.update(r2.state, listOf("m:a"), emptySet(), t0 + 3)
        assertEquals(setOf("m:a"), gone.state.unavailable)
        val back = TraktWatchlistTracker.update(gone.state, listOf("m:a"), setOf("m:a"), t0 + 4)
        assertEquals(listOf("m:a"), back.newly)
    }

    @Test fun identifiant_typeEtPremiereCle() {
        assertEquals("m:alien", TraktWatchlistTracker.id(TraktItem(false, 348, 1979, "Alien", listOf("alien", "alien fr"))))
        assertEquals("s:lost", TraktWatchlistTracker.id(TraktItem(true, null, null, "Lost", listOf("lost"))))
        assertEquals("m:sans cle", TraktWatchlistTracker.id(TraktItem(false, null, null, "Sans Cle", emptyList())))
    }

    @Test fun etat_encodeDecode_etFichierIllisible() {
        val m = mapOf(7L to WatchlistState(setOf("m:a"), mapOf("m:b" to 42L)))
        assertEquals(m, TraktWatchlistTracker.decode(TraktWatchlistTracker.encode(m)))
        assertTrue(TraktWatchlistTracker.decode("{pas du json").isEmpty())
        assertTrue(TraktWatchlistTracker.decode(null).isEmpty())
    }

    @Test fun resume_troisTitresPuisPointsDeSuspension() {
        assertEquals("A, B", TraktWatchlistTracker.summary(listOf("A", "B")))
        assertEquals("A, B, C…", TraktWatchlistTracker.summary(listOf("A", "B", "C", "D")))
    }

    @Test fun rangeesCodec_garde_lesTitresDisponibles() {
        val card = TraktCard(false, 1L, "Alien", null, 1979, null)
        val rows = TraktRows(emptyList(), emptyList(), recent = listOf(card))
        assertFalse(rows.isEmpty)
        val back = TraktRowsCodec.decode(TraktRowsCodec.encode(mapOf(3L to rows)))[3L]!!
        assertEquals(listOf(card), back.recent)
    }
}
