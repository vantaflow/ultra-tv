package com.ultratv.tv.nativeapp.ui.home

import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.ui.catalog.PosterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLibrariesTest {
    private fun card(id: Long, show: Boolean) = PosterCardUi("${if (show) "s" else "m"}-$id", PosterItem(id, "T$id", null, null, null), show, if (show) TmdbKind.TV else TmdbKind.MOVIE)
    private fun h(p: Long, id: String, at: Long, kind: String = "MOVIE") = WatchHistoryEntity(providerId = p, kind = kind, remoteId = id, title = id, poster = null, streamUrl = "u", watchedAt = at)

    @Test fun reprendre_melangeIptvEtServeurs_parDate_sansDoublon() {
        val iptv = listOf(h(1, "a", 100), h(1, "b", 50))
        val lib = listOf(h(2, "x", 200), h(2, "y", 10), h(1, "a", 100))
        assertEquals(listOf("x", "a", "b", "y"), mergeResume(iptv, lib, 12).map { it.remoteId })
        assertEquals(2, mergeResume(iptv, lib, 2).size)
    }

    @Test fun rangeeServeur_alterneFilmsEtSeries_20Maximum() {
        val m = (1L..15L).map { card(it, false) }; val s = (1L..15L).map { card(it, true) }
        val out = interleaveCards(m, s, 20)
        assertEquals(20, out.size)
        assertEquals(listOf("m-1", "s-1", "m-2", "s-2"), out.take(4).map { it.key })
    }

    @Test fun aucuneRangee_sansServeurVisible_ouServeurVide() {
        assertTrue(composeLibraryShelves(emptyList()).isEmpty())
        assertTrue(composeLibraryShelves(listOf(LibraryShelfUi(1, "Plex", emptyList()))).isEmpty())
        val rows = HomeRows(libraries = emptyList())
        assertFalse(rows.order.any { it in LIBRARY_SECTIONS })
    }

    @Test fun rangeesDansLOrdreDesSources_surLeBlocLibraries_etMasquables() {
        val shelves = composeLibraryShelves(listOf(LibraryShelfUi(2, "Plex", listOf(card(1, false))), LibraryShelfUi(3, "JF", listOf(card(2, true)))))
        val rows = HomeRows(libraries = shelves)
        assertEquals(listOf(HomeSectionId.LIBRARY_1, HomeSectionId.LIBRARY_2), rows.order)
        assertEquals("Plex", rows.libraries[HomeSectionId.LIBRARY_1.libraryIndex].name)
        // Bloc masqué par l'utilisateur : plus aucune rangée de serveur.
        val hidden = HomeRows(libraries = shelves, layout = HomeLayout(HomeBlock.entries, setOf(HomeBlock.LIBRARIES)))
        assertTrue(hidden.order.isEmpty())
        // Plus de 6 serveurs : 6 rangées au plus.
        assertEquals(6, composeLibraryShelves((1L..9L).map { LibraryShelfUi(it, "S$it", listOf(card(it, false))) }).size)
    }
}
