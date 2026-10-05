package com.ultratv.tv.nativeapp.ui.components

import com.ultratv.tv.nativeapp.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Le rail est piloté par des identifiants de route explicites, jamais par des index : chaque item vise SA route. */
class SidebarRoutesTest {
    private val expected = listOf(Routes.HOME, Routes.LIVE, Routes.GUIDE, Routes.MOVIES, Routes.SERIES, Routes.FAVORITES, "recordings", "account", Routes.SETTINGS)

    @Test fun rail_chaqueItemVisaSaRoute_dansLOrdreDeLaMaquette() = assertEquals(expected, railItems.map { it.route })

    @Test fun rail_laRechercheNEstPasUneDestination() = assertFalse(railItems.any { it.route == Routes.SEARCH })

    @Test fun itemActif_correspondALaRouteCourante_etUniquement() {
        for (route in expected) assertEquals("route $route", listOf(route), railItems.filter { isSelected(route, it.route) }.map { it.route })
    }

    @Test fun itemActif_sousRoutes() {
        assertEquals(listOf(Routes.MOVIES), railItems.filter { isSelected("movies/12", it.route) }.map { it.route })
        assertEquals(listOf(Routes.SERIES), railItems.filter { isSelected("series/3", it.route) }.map { it.route })
        assertEquals(listOf(Routes.LIVE), railItems.filter { isSelected("player?url=x&title=y", it.route) }.map { it.route })
        assertEquals(emptyList<String>(), railItems.filter { isSelected(Routes.SEARCH, it.route) }.map { it.route })
    }

    @Test fun accueil_n_est_jamais_actif_sur_les_reglages() {
        assertFalse(isSelected(Routes.SETTINGS, Routes.HOME))
        assertFalse(isSelected(Routes.HOME, Routes.SETTINGS))
    }
}
