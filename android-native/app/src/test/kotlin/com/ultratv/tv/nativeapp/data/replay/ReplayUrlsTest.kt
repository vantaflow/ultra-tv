package com.ultratv.tv.nativeapp.data.replay

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

/** Hôte, identifiants et identifiants de flux sont fictifs (example.test). */
class ReplayUrlsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 24L * 3_600_000L
    private val now = 1_800_000_000_000L   // arbitraire
    private fun ch(days: Int = 3, url: String = "http://example.test:8080/live/usr/pwd/42.ts", src: String? = null) =
        ChannelEntity(id = 1, providerId = 1, remoteId = "42", name = "Chaine", logo = null, categoryId = "c", streamUrl = url, catchupDays = days, catchupSource = src)
    private fun prog(start: Long = now - 3_600_000, end: Long = start + 45 * 60_000) = EpgEntity(channelId = 1, title = "P", description = null, startMs = start, endMs = end)

    @Test fun forme_chemin() {
        val p = prog(start = 1_700_000_040_000L) // 2023-11-14 22:14:00 UTC
        val u = ReplayUrls.build(ch(days = 99999), p, now, ReplayStyle.PATH, utc)
        assertEquals("http://example.test:8080/timeshift/usr/pwd/45/2023-11-14:22-14/42.ts", u)
    }

    @Test fun forme_php() {
        val p = prog(start = 1_700_000_040_000L)
        val u = ReplayUrls.build(ch(days = 99999), p, now, ReplayStyle.PHP, utc)
        assertEquals("http://example.test:8080/streaming/timeshift.php?username=usr&password=pwd&stream=42&start=2023-11-14:22-14&duration=45", u)
    }

    @Test fun chaine_sans_archive_refusee() {
        assertEquals(ReplayAvailability.NOT_ARCHIVED, ReplayUrls.availability(ch(days = 0), prog(), now))
        assertNull(ReplayUrls.build(ch(days = 0), prog(), now, ReplayStyle.PATH, utc))
    }

    @Test fun limite_de_tv_archive_duration_en_jours() {
        assertEquals(ReplayAvailability.AVAILABLE, ReplayUrls.availability(ch(days = 3), prog(start = now - 2 * day), now))
        assertEquals(ReplayAvailability.OUT_OF_WINDOW, ReplayUrls.availability(ch(days = 3), prog(start = now - 4 * day), now))
    }

    @Test fun programme_futur_refuse_programme_en_cours_accepte() {
        assertEquals(ReplayAvailability.NOT_STARTED, ReplayUrls.availability(ch(), prog(start = now + 60_000), now))
        assertEquals(ReplayAvailability.AVAILABLE, ReplayUrls.availability(ch(), prog(start = now - 10 * 60_000, end = now + 30 * 60_000), now))
    }

    @Test fun duree_arrondie_au_dessus_minimum_une_minute() {
        assertEquals(1, ReplayUrls.durationMinutes(prog(start = 0, end = 10_000)))
        assertEquals(2, ReplayUrls.durationMinutes(prog(start = 0, end = 60_001)))
    }

    @Test fun source_non_xtream_sans_modele_non_supportee() {
        assertEquals(ReplayAvailability.UNSUPPORTED_SOURCE, ReplayUrls.availability(ch(url = "http://example.test/a.m3u8"), prog(), now))
    }
}
