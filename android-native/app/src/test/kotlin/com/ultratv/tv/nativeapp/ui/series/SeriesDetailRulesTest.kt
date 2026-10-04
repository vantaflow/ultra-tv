package com.ultratv.tv.nativeapp.ui.series

import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesDetailRulesTest {
    private fun ep(id: String, s: Int, e: Int) = EpisodeEntity(seriesId = 1, remoteId = id, season = s, episode = e, title = "t", streamUrl = "x", container = null, plot = null)
    private fun hist(id: String, pos: Long, dur: Long) = WatchHistoryEntity(1, "EPISODE", id, "t", null, "x", pos, dur, parentRemoteId = "s")

    @Test fun resumeTarget_sansEpisode_retourneNull() = assertNull(resumeTarget(emptyList(), emptyList()))

    @Test fun resumeTarget_jamaisVu_prendLePremier() {
        val eps = listOf(ep("b", 1, 2), ep("a", 1, 1))
        assertEquals("a", resumeTarget(eps, emptyList())?.remoteId)
    }

    @Test fun resumeTarget_episodeInacheve_estRepris() {
        val eps = listOf(ep("a", 1, 1), ep("b", 1, 2))
        assertEquals("b", resumeTarget(eps, listOf(hist("b", 600_000, 3_000_000)))?.remoteId)
    }

    @Test fun resumeTarget_episodeTermine_passeAuSuivantNonVu() {
        val eps = listOf(ep("a", 1, 1), ep("b", 1, 2))
        assertEquals("b", resumeTarget(eps, listOf(hist("a", 2_990_000, 3_000_000)))?.remoteId)
    }

    @Test fun cleanEpisodeTitle_retireSerieEtMarqueur() {
        assertEquals("", cleanEpisodeTitle("AF-FR - SUGAR DADDY - S01E01", "AF-FR - SUGAR DADDY", "Sugar Daddy"))
        assertEquals("Le départ", cleanEpisodeTitle("Sugar Daddy - S02E03 - Le départ", "x", "Sugar Daddy").trim('-', ' '))
    }
}
