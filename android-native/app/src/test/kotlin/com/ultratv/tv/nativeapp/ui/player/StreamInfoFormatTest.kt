package com.ultratv.tv.nativeapp.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamInfoFormatTest {
    @Test fun resolution_with_height_label() = assertEquals("1280×720 · 720p", StreamInfoFormat.resolution(1280, 720))
    @Test fun resolution_unknown() { assertNull(StreamInfoFormat.resolution(0, 720)); assertNull(StreamInfoFormat.resolution(1280, -1)) }
    @Test fun resolution_from_label() {
        assertEquals("1920×1080 · 1080p", StreamInfoFormat.resolutionFromLabel("1920×1080"))
        assertEquals("abc", StreamInfoFormat.resolutionFromLabel("abc"))
        assertNull(StreamInfoFormat.resolutionFromLabel(null))
    }
    @Test fun bitrate_mbps() { assertEquals("4.2 Mb/s", StreamInfoFormat.bitrate(4200)); assertEquals("0.8 Mb/s", StreamInfoFormat.bitrate(800)) }
    @Test fun bitrate_unknown() { assertNull(StreamInfoFormat.bitrate(null)); assertNull(StreamInfoFormat.bitrate(0)) }
    @Test fun reload_offer_threshold() { assertFalse(StreamInfoFormat.shouldOfferReload(2_999)); assertTrue(StreamInfoFormat.shouldOfferReload(3_000)) }
}
