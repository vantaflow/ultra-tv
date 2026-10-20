package com.ultratv.tv.nativeapp.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    private val all = HomeSectionId.entries.toList()

    @Test fun parse_reglageVide_dispositionParDefaut() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.parse("", ""))
        assertEquals(HomeLayout.DEFAULT, HomeLayout.parse(null, null))
    }

    @Test fun apply_dispositionParDefaut_ordreDeLaMaquette() {
        assertEquals(all, HomeLayout.DEFAULT.apply(all.reversed()))
    }

    @Test fun csv_allerRetour_dispositionIdentique() {
        val l = HomeLayout.DEFAULT.move(HomeBlock.CHANNELS, -3).toggle(HomeBlock.TRAKT_POPULAR).toggle(HomeBlock.RESUME)
        assertEquals(l, HomeLayout.parse(l.orderCsv(), l.hiddenCsv()))
    }

    @Test fun orderCsv_ordreParDefaut_vide() {
        assertEquals("", HomeLayout.DEFAULT.orderCsv())
        assertEquals("", HomeLayout.DEFAULT.hiddenCsv())
    }

    @Test fun parse_nomsInconnusEtDoublons_ignores() {
        val l = HomeLayout.parse("CHANNELS,FOO,CHANNELS,RESUME", "BAR,LATEST_MOVIES")
        assertEquals(HomeBlock.CHANNELS, l.order[0])
        assertEquals(HomeBlock.entries.size, l.order.size)
        assertEquals(HomeBlock.entries.toSet(), l.order.toSet())
        assertEquals(setOf(HomeBlock.LATEST_MOVIES), l.hidden)
    }

    @Test fun parse_blocAjouteParUneMiseAJour_inseréApresSonPredecesseur() {
        // Ordre enregistré par une version qui ne connaissait pas « À suivre » ni les listes Trakt.
        val saved = HomeBlock.entries.filter { it != HomeBlock.TRAKT_UP_NEXT && it != HomeBlock.TRAKT_LISTS }.reversed().joinToString(",") { it.name }
        val l = HomeLayout.parse(saved, "")
        assertEquals(l.order.indexOf(HomeBlock.RESUME) + 1, l.order.indexOf(HomeBlock.TRAKT_UP_NEXT))
        assertEquals(l.order.indexOf(HomeBlock.TRAKT_POPULAR) + 1, l.order.indexOf(HomeBlock.TRAKT_LISTS))
        assertEquals(HomeBlock.entries.size, l.order.size)
    }

    @Test fun parse_premierBlocAbsent_inseréEnTete() {
        val l = HomeLayout.parse(HomeBlock.entries.drop(1).joinToString(",") { it.name }, "")
        assertEquals(HomeBlock.RESUME, l.order.first())
    }

    @Test fun apply_blocMasque_rangeesRetirees() {
        val l = HomeLayout.DEFAULT.toggle(HomeBlock.TRAKT_LISTS).toggle(HomeBlock.RESUME)
        val out = l.apply(all)
        assertFalse(HomeSectionId.RESUME in out)
        assertTrue(TRAKT_LIST_SECTIONS.none { it in out })
        assertTrue(HomeSectionId.CHANNELS in out)
    }

    @Test fun apply_ordrePersonnalise_rangeesSuivantLOrdre_videsAbsentes() {
        val l = HomeLayout.DEFAULT.move(HomeBlock.CHANNELS, -(HomeBlock.entries.size - 1))
        assertEquals(listOf(HomeSectionId.CHANNELS, HomeSectionId.RESUME, HomeSectionId.LATEST_MOVIES), l.apply(listOf(HomeSectionId.LATEST_MOVIES, HomeSectionId.RESUME, HomeSectionId.CHANNELS)))
    }

    @Test fun apply_listesTrakt_ordreInterneConserve() {
        val l = HomeLayout.DEFAULT.move(HomeBlock.TRAKT_LISTS, -6)
        assertEquals(listOf(HomeSectionId.TRAKT_LIST_1, HomeSectionId.TRAKT_LIST_3, HomeSectionId.RESUME), l.apply(listOf(HomeSectionId.RESUME, HomeSectionId.TRAKT_LIST_3, HomeSectionId.TRAKT_LIST_1)))
    }

    @Test fun move_enBoutDeListe_sansEffet() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.move(HomeBlock.RESUME, -1))
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.move(HomeBlock.CHANNELS, 1))
    }

    @Test fun toggle_deuxFois_retourALEtatInitial() {
        assertEquals(HomeLayout.DEFAULT, HomeLayout.DEFAULT.toggle(HomeBlock.LATEST_SERIES).toggle(HomeBlock.LATEST_SERIES))
    }

    @Test fun homeSectionOrder_dispositionDesRangees_appliquee() {
        val rows = HomeRows(
            resume = listOf(resumeCardOf(com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity(providerId = 1, kind = "MOVIE", remoteId = "1", title = "T", poster = null, streamUrl = "u", positionMs = 0, durationMs = 0, watchedAt = 0), "", { it })),
            channels = listOf(ChannelCardUi(com.ultratv.tv.nativeapp.data.db.ChannelEntity(id = 1, providerId = 1, remoteId = "1", name = "C", logo = null, categoryId = null, streamUrl = "u"), null)),
            layout = HomeLayout.DEFAULT.move(HomeBlock.CHANNELS, -(HomeBlock.entries.size - 1)),
        )
        assertEquals(listOf(HomeSectionId.CHANNELS, HomeSectionId.RESUME), rows.order)
        assertEquals(listOf(HomeSectionId.RESUME), rows.copy(layout = HomeLayout.DEFAULT.toggle(HomeBlock.CHANNELS)).order)
    }

    @Test fun block_chaqueRangee_appartientAUnSeulBloc() {
        for (s in HomeSectionId.entries) assertEquals(1, HomeBlock.entries.count { s in it.sections })
    }
}
