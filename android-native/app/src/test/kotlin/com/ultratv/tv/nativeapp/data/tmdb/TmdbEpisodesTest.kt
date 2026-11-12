package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbEpisodesTest {
    private val series = "Serie Fictive"

    private fun ep(title: String, plot: String? = null, image: String? = null, n: Int = 3) =
        EpisodeEntity(seriesId = 1, remoteId = "r$n", season = 1, episode = n, title = title, streamUrl = "u", container = null, plot = plot, image = image)

    @Test fun `titres generiques reconnus dans plusieurs langues`() {
        for (t in listOf("Episode 3", "Épisode 03", "ÉPISODE 3", "Ep. 3", "E03", "S01E03", "s1 e3", "1x03", "Folge 3", "Capítulo 3", "Episodio 3",
            "حلقة 3", "الحلقة 3", "Odcinek 3", "3", "03", "", "   ", "Serie Fictive", "Serie Fictive 3", "Serie Fictive - S01E03", "serie fictive ep 3")) {
            assertTrue("« $t » devrait être générique", isGenericEpisodeTitle(t, series, 3))
        }
        assertTrue(isGenericEpisodeTitle(null, series, 3))
    }

    @Test fun `vrais titres conserves`() {
        for (t in listOf("Pilot", "Le Retour", "Serie Fictive - S01E03 - Le Retour", "Episode 3 Le Retour", "The Episode", "Chapitre Un")) {
            assertFalse("« $t » ne devrait pas être générique", isGenericEpisodeTitle(t, series, 3))
        }
    }

    @Test fun `nom brut de la source avec etiquettes tolere`() {
        assertTrue(isGenericEpisodeTitle("Serie Fictive - S01E03", "4K-NF - Serie Fictive (2023) (KR)", 3))
        assertTrue(isGenericEpisodeTitle("Fictive 3", "XX - Autre", 3, "Serie Fictive"))
    }

    @Test fun `titre TMDB seulement si titre source generique`() {
        val t = TmdbEpisode(3, "Le Retour", "Résumé TMDB", "/img.jpg", runtime = 42)
        val merged = mergeEpisode(ep("Episode 3"), t, series)
        assertEquals("Le Retour", merged.title)
        assertEquals("Résumé TMDB", merged.plot)
        assertEquals("https://image.tmdb.org/t/p/w300/img.jpg", merged.image)
        assertEquals("42", merged.duration)
        assertEquals("Vrai titre", mergeEpisode(ep("Vrai titre"), t, series).title)
    }

    @Test fun `resume et vignette de la source prioritaires`() {
        val t = TmdbEpisode(3, "Le Retour", "Résumé TMDB", "/img.jpg")
        val m = mergeEpisode(ep("E03", plot = "Résumé source", image = "http://src/img.png"), t, series)
        assertEquals("Le Retour", m.title)
        assertEquals("Résumé source", m.plot)
        assertEquals("http://src/img.png", m.image)
    }

    @Test fun `titre TMDB generique ignore`() {
        val e = ep("Episode 3")
        assertSame(e, mergeEpisode(e, TmdbEpisode(3, "Épisode 3", null, null), series))
        assertSame(e, mergeEpisode(e, null, series))
    }

    @Test fun `fusion par saison et numero`() {
        val eps = listOf(ep("E01", n = 1), ep("E02", n = 2))
        val out = mergeEpisodes(eps, mapOf(1 to listOf(TmdbEpisode(2, "Deux", null, null))), series)
        assertEquals(listOf("E01", "Deux"), out.map { it.title })
        assertSame(eps, mergeEpisodes(eps, emptyMap(), series))
    }

    @Test fun `lecture de la saison TMDB et cache`() {
        val json = """{"episodes":[{"episode_number":1,"name":"Un","overview":"","still_path":null,"runtime":40},{"episode_number":0,"name":"x"},{"episode_number":2,"name":"Deux","still_path":"/b.jpg"}]}"""
        val eps = TmdbSeasonCodec.parse(json)
        assertEquals(listOf(1, 2), eps.map { it.number })
        assertNull(eps[0].overview)
        assertNull(eps[0].stillPath)
        assertEquals(40, eps[0].runtime)
        val raw = TmdbSeasonCodec.encode(eps, 1_000L)
        assertEquals(eps, TmdbSeasonCodec.decode(raw, 2_000L))
        assertNull(TmdbSeasonCodec.decode(raw, 1_000L + TmdbSeasonCodec.TTL_MS))
        assertEquals(emptyList<TmdbEpisode>(), TmdbSeasonCodec.decode(TmdbSeasonCodec.encode(emptyList(), 5L), 6L))
        assertNull(TmdbSeasonCodec.decode("pas du json", 0L))
        assertTrue(TmdbSeasonCodec.parse("{}").isEmpty())
    }
}
