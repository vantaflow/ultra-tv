package com.ultratv.tv.nativeapp.ui.player.engine

import com.ultratv.tv.nativeapp.ui.player.engine.TrackPolicy.VideoCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackPolicyTest {
    private fun c(w: Int, h: Int, kbps: Int, ok: Boolean = true) = VideoCandidate(w, h, kbps * 1000, ok)

    @Test fun highest_prendLaPlusGrandeResolution() {
        val l = listOf(c(426, 240, 400), c(1920, 1080, 5000), c(1280, 720, 2800), c(640, 360, 800))
        assertEquals(1, TrackPolicy.highestIndex(l))
    }

    @Test fun highest_memeResolution_departageParLeDebit() {
        val l = listOf(c(1920, 1080, 4000), c(1920, 1080, 6000), c(1920, 1080, 5000))
        assertEquals(1, TrackPolicy.highestIndex(l))
    }

    @Test fun highest_ignoreLesPistesNonPriseEnCharge() {
        val l = listOf(c(1280, 720, 2800), c(3840, 2160, 15000, ok = false), c(1920, 1080, 5000))
        assertEquals(2, TrackPolicy.highestIndex(l))
    }

    @Test fun highest_resolutionInconnue_seFondeSurLeDebit() {
        val l = listOf(c(0, 0, 800), c(0, 0, 3000), c(0, 0, 1500))
        assertEquals(1, TrackPolicy.highestIndex(l))
    }

    @Test fun highest_exAequo_garleLaPremiere() = assertEquals(0, TrackPolicy.highestIndex(listOf(c(1280, 720, 2000), c(1280, 720, 2000))))

    @Test fun highest_aucunePiste_ouAucunePriseEnCharge_null() {
        assertNull(TrackPolicy.highestIndex(emptyList()))
        assertNull(TrackPolicy.highestIndex(listOf(c(1280, 720, 2000, ok = false))))
    }

    @Test fun debitInitial_defautEtMaximum() {
        assertEquals(TrackPolicy.DEFAULT_INITIAL_BPS, TrackPolicy.initialBitrateEstimate(null, maxQuality = false))
        assertEquals(TrackPolicy.MAX_QUALITY_INITIAL_BPS, TrackPolicy.initialBitrateEstimate(2_000_000, maxQuality = true))
    }

    @Test fun debitInitial_suitLePlafondSansLeDepasser() {
        assertEquals(3_000_000L, TrackPolicy.initialBitrateEstimate(2_000_000, false))
        assertEquals(2_000_000L, TrackPolicy.initialBitrateEstimate(800_000, false))   // plancher
        assertEquals(TrackPolicy.DEFAULT_INITIAL_BPS, TrackPolicy.initialBitrateEstimate(12_000_000, false))
    }

    @Test fun reglage_max() {
        assertTrue(TrackPolicy.isMax("max"))
        assertFalse(TrackPolicy.isMax("auto")); assertFalse(TrackPolicy.isMax(null)); assertFalse(TrackPolicy.isMax("autre"))
    }

    @Test fun tunneling_desactiveParDefaut() = assertFalse(TrackPolicy.tunnelingEffective(false, textureView = false, decoder = DecoderMode.AUTO))

    @Test fun tunneling_actifSurSurfaceViewEtDecodeurMateriel() {
        assertTrue(TrackPolicy.tunnelingEffective(true, textureView = false, decoder = DecoderMode.AUTO))
        assertTrue(TrackPolicy.tunnelingEffective(true, textureView = false, decoder = DecoderMode.HARDWARE))
    }

    @Test fun tunneling_ignoreAvecTextureViewOuDecodeurLogiciel() {
        assertFalse(TrackPolicy.tunnelingEffective(true, textureView = true, decoder = DecoderMode.AUTO))
        assertFalse(TrackPolicy.tunnelingEffective(true, textureView = false, decoder = DecoderMode.SOFTWARE))
    }
}
