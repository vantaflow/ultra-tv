package com.ultratv.tv.nativeapp.ui.mobile

import com.ultratv.tv.nativeapp.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomTabsTest {
    @Test fun parDefaut_barreDOrigine() {
        assertEquals(listOf(Routes.LIVE, Routes.GUIDE, Routes.MOVIES), bottomTabRoutes("", sportOn = false))
        assertEquals(listOf(Routes.LIVE, Routes.GUIDE, Routes.MOVIES), bottomTabRoutes(null, sportOn = true))
    }

    @Test fun seriesALaPlaceDuGuide() {
        val tabs = withBottomTab(DEFAULT_BOTTOM_TABS, 1, Routes.SERIES)
        assertEquals(listOf(Routes.LIVE, Routes.SERIES, Routes.MOVIES), tabs)
        assertEquals(tabs, bottomTabRoutes(tabs.joinToString(","), sportOn = false))
    }

    @Test fun rubriqueDejaPresente_echange() {
        assertEquals(listOf(Routes.MOVIES, Routes.GUIDE, Routes.LIVE), withBottomTab(DEFAULT_BOTTOM_TABS, 0, Routes.MOVIES))
    }

    @Test fun valeursInvalides_ignorees_etCompletees() {
        assertEquals(listOf(Routes.SERIES, Routes.LIVE, Routes.GUIDE), bottomTabRoutes("series,series,inconnu", sportOn = false))
        // Sport masqué : remplacé.
        assertEquals(listOf(Routes.LIVE, Routes.GUIDE, Routes.MOVIES), bottomTabRoutes("sport,live,guide", sportOn = false).sortedBy { DEFAULT_BOTTOM_TABS.indexOf(it) })
    }

    @Test fun ongletActif_etPlus() {
        val tabs = listOf(Routes.LIVE, Routes.SERIES, Routes.MOVIES)
        assertEquals(Routes.HOME, activeBottomTab(Routes.HOME, tabs))
        assertEquals(Routes.SERIES, activeBottomTab("series/{id}", tabs))
        assertEquals(Routes.LIVE, activeBottomTab("player/x", tabs))
        assertNull(activeBottomTab(Routes.GUIDE, tabs))
        assertEquals(listOf(Routes.GUIDE, Routes.FAVORITES, "recordings", Routes.DOWNLOADS, Routes.SETTINGS, "profiles"), moreRoutesFor(tabs, sportOn = false))
    }
}
