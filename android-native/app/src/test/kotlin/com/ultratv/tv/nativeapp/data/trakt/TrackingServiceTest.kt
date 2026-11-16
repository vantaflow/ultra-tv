package com.ultratv.tv.nativeapp.data.trakt

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Service de suivi (Trakt / Simkl) : libellés, état, bibliothèque, écritures non supportées. */
class TrackingServiceTest {
    @After fun reset() { TrackingService.active = TrackingService.TRAKT }

    // ── Service de suivi ──

    @Test fun forTracker_traktInchange_simklRemplaceLeNom() {
        assertEquals("Ajouter à ma watchlist Trakt", "Ajouter à ma watchlist Trakt".forTracker())
        TrackingService.active = TrackingService.SIMKL
        assertEquals("Ajouter à ma watchlist Simkl", "Ajouter à ma watchlist Trakt".forTracker())
        assertEquals("Marquer comme vu", "Marquer comme vu".forTracker())
    }

    @Test fun trackingStatus_parse() {
        val st = TrackingStatus.parse("""{"service":"simkl","choice":"simkl","available":["trakt","simkl","x"],"linked":{"trakt":false,"simkl":true}}""")!!
        assertEquals("simkl", st.service)
        assertEquals(listOf("trakt", "simkl"), st.available)
        assertTrue(st.isLinked("simkl")); assertFalse(st.isLinked("trakt")); assertTrue(st.isLinked("none"))
        val none = TrackingStatus.parse("""{"service":null,"choice":"none","available":["trakt"],"linked":{}}""")!!
        assertNull(none.service); assertEquals("none", none.choice)
        assertNull(TrackingStatus.parse("<html>"))
        assertNull(TrackingStatus.parse(null))
    }

    @Test fun bibliotheque_serviceLuDansLaReponse() {
        assertEquals("simkl", TraktLibrary.parse("""{"linked":true,"service":"simkl"}""").service)
        assertEquals("trakt", TraktLibrary.parse("""{"linked":true}""").service)
        assertEquals("trakt", TraktLibrary.parse("""{"linked":true,"service":"autre"}""").service)
    }

    @Test fun ecritureNonSupportee_distincteDeLEchec() {
        val p = { body: String -> WorkerTraktWriteTransport.parseWriteResponse(200, body) }
        assertEquals(TraktWriteResult.UNSUPPORTED, p("""{"linked":true,"matched":true,"ok":false,"error":"unsupported"}"""))
        assertEquals(TraktWriteResult.OK, p("""{"linked":true,"matched":true,"ok":true}"""))
    }
}
