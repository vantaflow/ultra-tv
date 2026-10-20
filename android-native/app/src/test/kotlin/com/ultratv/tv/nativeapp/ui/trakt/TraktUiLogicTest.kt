package com.ultratv.tv.nativeapp.ui.trakt

import com.ultratv.tv.nativeapp.data.trakt.TraktCard
import com.ultratv.tv.nativeapp.data.trakt.TraktItem
import com.ultratv.tv.nativeapp.data.trakt.TraktLibrary
import com.ultratv.tv.nativeapp.data.trakt.TraktRated
import com.ultratv.tv.nativeapp.data.trakt.TraktUpNextCard
import com.ultratv.tv.nativeapp.data.trakt.TraktWatchedShow
import com.ultratv.tv.nativeapp.ui.home.HomeRows
import com.ultratv.tv.nativeapp.ui.home.HomeSectionId
import com.ultratv.tv.nativeapp.ui.home.TraktListUi
import com.ultratv.tv.nativeapp.ui.home.homeSectionOrder
import com.ultratv.tv.nativeapp.ui.home.traktListIndex
import com.ultratv.tv.nativeapp.ui.home.traktPosterOf
import com.ultratv.tv.nativeapp.ui.home.traktUpNextPosterOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraktUiLogicTest {
    private val lost = TraktItem(true, 4607, 2004, "Lost", listOf("lost"))
    private val alien = TraktItem(false, 348, 1979, "Alien", listOf("alien"))
    private val lib = TraktLibrary(
        linked = true, updatedAt = 1, watchlist = listOf(lost), recommendations = emptyList(),
        watchedMovies = listOf(alien), watchedShows = listOf(TraktWatchedShow(lost, setOf("1x1"))),
        ratedMovies = listOf(TraktRated(alien, 8)),
    )

    @Test fun etatFiche_film() {
        val st = traktDetailState(TraktTarget(false, 1, "9", "FR - Alien (1979)", 1979), lib)
        assertTrue(st.linked && st.watched && !st.inWatchlist)
        assertEquals(8, st.rating)
    }

    @Test fun etatFiche_serie() {
        val st = traktDetailState(TraktTarget(true, 1, "7", "Lost", 2004), lib)
        assertTrue(st.inWatchlist)
        assertFalse("une série n'est jamais « film vu »", st.watched)
        assertEquals(setOf("1x1"), st.episodes)
    }

    @Test fun etatFiche_nonLie_rien() {
        assertEquals(TraktDetailState(), traktDetailState(TraktTarget(false, 1, "9", "Alien", 1979), TraktLibrary.EMPTY))
        assertEquals(TraktDetailState(), traktDetailState(null, lib))
    }

    @Test fun accueil_aSuivreEtListes_ordre() {
        val card = TraktCard(true, 3, "Lost", null, 2004, null)
        val up = traktUpNextPosterOf(TraktUpNextCard(card, 2, 5))
        assertEquals("Lost · S02E05", up.item.title)
        assertTrue(up.isShow)
        val lists = (1..6).map { TraktListUi("$it", "L$it", if (it == 2) emptyList() else listOf(traktPosterOf(card.copy(id = it.toLong())))) }
        val order = homeSectionOrder(HomeRows(resume = emptyList(), upNext = listOf(up), traktLists = lists))
        assertEquals(
            listOf(HomeSectionId.TRAKT_UP_NEXT, HomeSectionId.TRAKT_LIST_1, HomeSectionId.TRAKT_LIST_3, HomeSectionId.TRAKT_LIST_4, HomeSectionId.TRAKT_LIST_5),
            order,
        )
        assertEquals(2, HomeSectionId.TRAKT_LIST_3.traktListIndex)
        assertEquals(-1, HomeSectionId.RESUME.traktListIndex)
    }
}
