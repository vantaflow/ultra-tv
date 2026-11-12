package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HDR terne sous TextureView : détection du contenu, de l'écran et décision de bascule en SurfaceView. */
class HdrOutputTest {
    @Test fun contenu_hdr10_hlg_dolbyVision() {
        assertEquals("hdr10", HdrOutput.contentKind(HdrOutput.TRANSFER_ST2084, "video/hevc"))
        assertEquals("hlg", HdrOutput.contentKind(HdrOutput.TRANSFER_HLG, "video/hevc"))
        assertEquals("dv", HdrOutput.contentKind(null, "video/dolby-vision"))
        assertEquals("dv", HdrOutput.contentKind(HdrOutput.TRANSFER_ST2084, "VIDEO/DOLBY-VISION"))
    }

    @Test fun contenu_sdr_ou_inconnu() {
        assertNull(HdrOutput.contentKind(3, "video/avc"))
        assertNull(HdrOutput.contentKind(null, null))
        assertFalse(HdrOutput.isHdrContent(1, "video/hevc"))
        assertTrue(HdrOutput.isHdrContent(6, "video/hevc"))
    }

    @Test fun ecran_hdr() {
        assertTrue(HdrOutput.displayHdrCapable(intArrayOf(2, 3), null))
        assertTrue(HdrOutput.displayHdrCapable(null, true))
        assertTrue(HdrOutput.displayHdrCapable(intArrayOf(), true))
        assertFalse(HdrOutput.displayHdrCapable(intArrayOf(), false))
        assertFalse(HdrOutput.displayHdrCapable(null, null))
    }

    @Test fun bascule_surface_cas_nominal() {
        assertTrue(sw())
    }

    @Test fun bascule_refusee() {
        assertFalse(sw(contentHdr = false))
        assertFalse(sw(displayHdr = false))
        assertFalse(sw(currentlyTexture = false))
        assertFalse(sw(userPref = "texture"))
        assertFalse(sw(userPref = "surface"))
        assertFalse(sw(alreadySwitched = true))
        assertFalse(sw(surfaceKnownBad = true))
    }

    @Test fun journal_sans_adresse() {
        assertEquals("hdr content=hdr10 display=hdr surface=texture engine=EXO", HdrOutput.describe("hdr10", true, true, EngineKind.EXO))
        assertEquals("hdr content=dv display=sdr surface=surface engine=VLC", HdrOutput.describe("dv", false, false, EngineKind.VLC))
    }

    @Test fun repli_hdr_garde_le_materiel_d_abord() {
        val tried = setOf(Combo(EngineKind.EXO, DecoderMode.AUTO))
        assertEquals(Combo(EngineKind.VLC, DecoderMode.AUTO), PlaybackPlanner.next(EngineChoice.AUTO, tried, hdr = true))
        assertEquals(Combo(EngineKind.EXO, DecoderMode.SOFTWARE), PlaybackPlanner.next(EngineChoice.AUTO, tried, hdr = false))
        val tried2 = tried + Combo(EngineKind.VLC, DecoderMode.AUTO)
        assertEquals(Combo(EngineKind.EXO, DecoderMode.SOFTWARE), PlaybackPlanner.next(EngineChoice.AUTO, tried2, hdr = true))
        assertEquals(Combo(EngineKind.EXO, DecoderMode.SOFTWARE), PlaybackPlanner.next(EngineChoice.EXO, tried, hdr = true))
        val all = tried2 + Combo(EngineKind.EXO, DecoderMode.SOFTWARE) + Combo(EngineKind.VLC, DecoderMode.SOFTWARE)
        assertNull(PlaybackPlanner.next(EngineChoice.AUTO, all, hdr = true))
    }

    private fun sw(
        contentHdr: Boolean = true, displayHdr: Boolean = true, currentlyTexture: Boolean = true,
        userPref: String = "auto", alreadySwitched: Boolean = false, surfaceKnownBad: Boolean = false,
    ) = HdrOutput.shouldSwitchToSurface(contentHdr, displayHdr, currentlyTexture, userPref, alreadySwitched, surfaceKnownBad)
}
