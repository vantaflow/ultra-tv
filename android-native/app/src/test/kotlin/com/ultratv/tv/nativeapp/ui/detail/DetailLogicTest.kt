package com.ultratv.tv.nativeapp.ui.detail

import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings
import com.ultratv.tv.nativeapp.ui.series.resumeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailLogicTest {
    private fun ep(s: Int, e: Int) = EpisodeEntity(seriesId = 1, remoteId = "r$s-$e", season = s, episode = e, title = "t", streamUrl = "u", container = null, plot = null)
    private fun hist(rid: String, pos: Long, dur: Long) = WatchHistoryEntity(providerId = 1, kind = "EPISODE", remoteId = rid, title = "x", poster = null, streamUrl = "u", positionMs = pos, durationMs = dur)
    private val eps = listOf(ep(1, 1), ep(1, 2), ep(1, 3), ep(2, 1), ep(2, 2), ep(3, 1))

    @Test fun ligneInfosComplete() {
        val l = buildInfoLine("2001", "Comedy, Drama, Romance", "9 saisons · 182 épisodes", ExternalRatings(imdb = 8.4, rt = 87), 7.0)
        assertEquals(listOf("2001", "Comedy, Drama", "9 saisons · 182 épisodes"), l.texts)
        assertEquals("IMDb 8.4", l.imdb)
        assertEquals("87%", l.rt)
    }

    @Test fun ligneInfosSansNotesExternesUtiliseLaNoteSource() {
        val l = buildInfoLine("2020", null, "1 h 52", null, 7.4)
        assertEquals(listOf("2020", "1 h 52", "★ 7.4"), l.texts)
        assertNull(l.imdb); assertNull(l.rt)
    }

    @Test fun ligneInfosIgnoreLesChampsVides() {
        val l = buildInfoLine(" ", "", null, null, 0.0)
        assertTrue(l.texts.isEmpty())
    }

    @Test fun reprisePremierEpisodeSansHistorique() {
        assertEquals("r1-1", resumeTarget(eps, emptyList())?.remoteId)
    }

    @Test fun repriseEpisodeCommence() {
        assertEquals("r1-3", resumeTarget(eps, listOf(hist("r1-3", 120_000, 1_500_000)))?.remoteId)
        assertTrue(isResuming(120_000)); assertTrue(!isResuming(0)); assertTrue(!isResuming(null))
    }

    @Test fun repriseSuivantQuandLeDernierEstTermine() {
        val h = listOf(hist("r1-1", 1_490_000, 1_500_000), hist("r1-2", 1_495_000, 1_500_000))
        assertEquals("r1-3", resumeTarget(eps, h)?.remoteId)
    }

    @Test fun choixDeSaison() {
        val seasons = seasonsOf(eps)
        assertEquals(listOf(1, 2, 3), seasons)
        assertEquals(2, activeSeasonOf(seasons, null, 2))
        assertEquals(3, activeSeasonOf(seasons, 3, 2))
        assertEquals(1, activeSeasonOf(seasons, 9, 7))
        assertNull(activeSeasonOf(emptyList(), null, null))
    }

    @Test fun saisonSuivantePrecedente() {
        val s = listOf(1, 2, 3)
        assertEquals(2, stepSeason(s, 1, +1))
        assertEquals(2, stepSeason(s, 3, -1))
        assertNull(stepSeason(s, 3, +1))
        assertNull(stepSeason(s, 1, -1))
        assertNull(stepSeason(s, 8, +1))
    }

    @Test fun sautALEpisode() {
        val shown = eps.filter { it.season == 1 }
        assertEquals(2, jumpIndex(shown, 3))
        assertNull(jumpIndex(shown, 24))
        assertEquals(1, entryIndexOf(shown, ep(1, 2)))
        assertEquals(0, entryIndexOf(shown, ep(2, 2)))
        assertEquals(0, entryIndexOf(shown, null))
    }

    @Test fun progression() {
        assertEquals(0f, progressFraction(0, 0), 0f)
        assertEquals(0.5f, progressFraction(1_000_000, 500_000), 0.001f)
        assertEquals(1f, progressFraction(1_000_000, 990_000), 0f)
        assertTrue(isDone(1_000_000, 990_000)); assertTrue(!isDone(0, 5))
    }
}
