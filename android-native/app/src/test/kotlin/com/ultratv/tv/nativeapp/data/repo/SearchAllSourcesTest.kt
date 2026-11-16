package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.profile.SourceBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchAllSourcesTest {
    private fun movie(id: Long, pid: Long, name: String) =
        MovieEntity(id = id, providerId = pid, remoteId = "r$id", name = name, poster = null, categoryId = null, streamUrl = "u", container = null, year = null, rating = null, plot = null)
    private fun channel(id: Long, pid: Long, name: String) =
        ChannelEntity(id = id, providerId = pid, remoteId = "c$id", name = name, logo = null, categoryId = null, streamUrl = "u")

    @Test fun fusion_garde_chaque_source_et_son_identifiant() {
        val a = SearchResults(movies = listOf(movie(1, 10, "Alien")), channels = listOf(channel(1, 10, "Ciné A")))
        val b = SearchResults(movies = listOf(movie(2, 20, "Aliens")), channels = listOf(channel(2, 20, "Ciné B")))
        val m = SearchResults.merge(listOf(a, b))
        assertEquals(listOf(10L, 20L), m.movies.map { it.providerId })
        assertEquals(listOf("Ciné A", "Ciné B"), m.channels.map { it.name })
        assertFalse(m.fuzzy)
    }

    @Test fun titres_proches_seulement_si_aucune_source_n_a_de_resultat_exact() {
        val exact = SearchResults(movies = listOf(movie(1, 10, "Alien")))
        val near = SearchResults(movies = listOf(movie(2, 20, "Alian")), fuzzy = true)
        assertFalse(SearchResults.merge(listOf(exact, near)).fuzzy)
        assertTrue(SearchResults.merge(listOf(SearchResults(), near)).fuzzy)
        assertFalse(SearchResults.merge(emptyList()).fuzzy)
    }

    @Test fun pastille_courte() {
        assertEquals("IPTV", SourceBadge.of(" IPTV "))
        assertEquals("Ma list\u2026", SourceBadge.of("Ma liste de chaînes"))
        assertEquals(8, SourceBadge.of("Une source très longue").length)
        assertEquals("?", SourceBadge.of("  "))
    }
}
