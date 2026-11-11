package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.engine.EngineStats
import java.util.TimeZone
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class VodOverlayLogicTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun qualiteSelonResolution() {
        assertEquals("4K", VodOverlayLogic.quality("3840×2160"))
        assertEquals("FHD", VodOverlayLogic.quality("1920×1080"))
        assertEquals("FHD", VodOverlayLogic.quality("1920×800"))
        assertEquals("HD", VodOverlayLogic.quality("1280×720"))
        assertEquals("SD", VodOverlayLogic.quality("720×576"))
        assertNull(VodOverlayLogic.quality(null))
        assertNull(VodOverlayLogic.quality("0×0"))
        assertNull(VodOverlayLogic.quality("abc"))
    }

    @Test fun codecsMedia3EtVlc() {
        assertEquals("H.264", VodOverlayLogic.codec("avc"))
        assertEquals("H.264", VodOverlayLogic.codec("h264"))
        assertEquals("HEVC", VodOverlayLogic.codec("hevc"))
        assertEquals("AV1", VodOverlayLogic.codec("av01"))
        assertEquals("VP9", VodOverlayLogic.codec("x-vnd.on2.vp9"))
        assertEquals("MPEG-2", VodOverlayLogic.codec("mpeg2"))
        assertNull(VodOverlayLogic.codec("zzz"))
        assertNull(VodOverlayLogic.codec(null))
    }

    @Test fun dispositionAudio() {
        assertEquals("2.0", VodOverlayLogic.audio(2))
        assertEquals("5.1", VodOverlayLogic.audio(6))
        assertEquals("7.1", VodOverlayLogic.audio(8))
        assertNull(VodOverlayLogic.audio(null))
        assertNull(VodOverlayLogic.audio(12))
    }

    @Test fun badgesDepuisStatsEtInconnus() {
        val b = VodOverlayLogic.badges(EngineStats(resolution = "1920×1080", videoCodec = "hevc", audioChannels = 6))
        assertEquals(TechBadges("FHD", "HEVC", "5.1"), b)
        assertEquals(TechBadges.EMPTY, VodOverlayLogic.badges(EngineStats()))
        assertEquals(TechBadges.EMPTY, VodOverlayLogic.badges(null))
        assertEquals(TechBadges("HD", null, null), VodOverlayLogic.badges(EngineStats(resolution = "1280×720")))
    }

    @Test fun tempsRestant() {
        assertEquals("−2:48:58", VodOverlayLogic.remaining(4_000, (2 * 3600 + 48 * 60 + 58) * 1000L + 4_000))
        assertEquals("−0:30", VodOverlayLogic.remaining(90_000, 120_000))
        assertEquals("", VodOverlayLogic.remaining(0, -1))
        assertEquals("−0:00", VodOverlayLogic.remaining(130_000, 120_000))
    }

    @Test fun tempsEcoule() {
        assertEquals("0:04", VodOverlayLogic.clock(4_000))
        assertEquals("1:02:03", VodOverlayLogic.clock(3_723_000))
    }

    @Test fun heureDeFin() {
        val noon = 12 * 3600_000L
        val end = VodOverlayLogic.endsAtMs(noon, 0, 3600_000L + 6 * 60_000L)
        assertEquals(noon + 66 * 60_000L, end)
        assertEquals("13:06", VodOverlayLogic.endClock(end!!, true, utc))
        assertEquals("1:06 PM", VodOverlayLogic.endClock(end, false, utc))
        assertEquals("12:00 AM", VodOverlayLogic.endClock(0L, false, utc))
        assertEquals(noon + 30 * 60_000L, VodOverlayLogic.endsAtMs(noon, 0, 60 * 60_000L, 2f))
        assertNull(VodOverlayLogic.endsAtMs(noon, 0, -1))
    }
}
