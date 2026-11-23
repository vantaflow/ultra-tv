package com.ultratv.tv.nativeapp.ui.replay

import com.ultratv.tv.nativeapp.nav.DeepLink
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.nav.rememberableScreen
import com.ultratv.tv.nativeapp.ui.components.RailBackAction
import com.ultratv.tv.nativeapp.ui.components.isSelected
import com.ultratv.tv.nativeapp.ui.components.railBackAction
import com.ultratv.tv.nativeapp.ui.components.railItems
import com.ultratv.tv.nativeapp.ui.components.railItemsFor
import com.ultratv.tv.nativeapp.ui.mobile.moreRoutesFor
import com.ultratv.tv.nativeapp.ui.mobile.railRouteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rubrique Replay : entrée du menu (TV, rail et « Plus » tactiles), Retour, lien profond, dernier écran. */
class ReplayNavTest {
    // ───────────── Menu latéral TV ─────────────

    @Test fun rail_sansReplay_inchange() {
        assertEquals(railItems.map { it.route }, railItemsFor(sport = false, libraries = false, replay = false).map { it.route })
    }

    @Test fun rail_avecReplay_uneEntree_apresLeGuide() {
        val routes = railItemsFor(sport = false, libraries = false, replay = true).map { it.route }
        assertEquals(railItems.size + 1, routes.size)
        assertEquals(1, routes.count { it == Routes.REPLAY })
        assertEquals(routes.indexOf(Routes.GUIDE) + 1, routes.indexOf(Routes.REPLAY))
    }

    @Test fun rail_avecSportEtReplay_sportPuisReplay() {
        val routes = railItemsFor(sport = true, libraries = false, replay = true).map { it.route }
        assertEquals(listOf(Routes.GUIDE, Routes.SPORT, Routes.REPLAY), routes.subList(routes.indexOf(Routes.GUIDE), routes.indexOf(Routes.GUIDE) + 3))
    }

    @Test fun rail_replaySelectionneSurSaRoute() {
        assertEquals(listOf(Routes.REPLAY), railItemsFor(sport = true, libraries = true, replay = true).filter { isSelected(Routes.REPLAY, it.route) }.map { it.route })
    }

    @Test fun retour_ouvreLeMenu_commeLesAutresRubriques() {
        assertEquals(RailBackAction.OPEN_MENU, railBackAction(Routes.REPLAY, menuOpen = false, railShown = true, simpleHome = false, modalOpen = false))
        assertEquals(RailBackAction.MENU_TO_HOME, railBackAction(Routes.REPLAY, menuOpen = true, railShown = true, simpleHome = false, modalOpen = false))
        assertEquals(RailBackAction.PASS, railBackAction(Routes.REPLAY, menuOpen = false, railShown = true, simpleHome = false, modalOpen = true))
    }

    // ───────────── Téléphone / tablette ─────────────

    @Test fun plus_contientReplay_seulementSiDisponible() {
        val tabs = listOf(Routes.LIVE, Routes.GUIDE, Routes.MOVIES)
        assertFalse(Routes.REPLAY in moreRoutesFor(tabs, sportOn = false))
        val more = moreRoutesFor(tabs, sportOn = false, replayOn = true)
        assertEquals(1, more.count { it == Routes.REPLAY })
        assertEquals(Routes.REPLAY, more.first())
    }

    @Test fun railTactile_routeReplay() = assertEquals(Routes.REPLAY, railRouteFor(Routes.REPLAY))

    // ───────────── Lien profond et dernier écran ─────────────

    @Test fun lienProfond_replay() {
        assertEquals(DeepLink.OpenSection(Routes.REPLAY), DeepLink.parse("ultratv://replay"))
        assertEquals(DeepLink.OpenSection(Routes.REPLAY), DeepLink.resolve("ultratv://replay/"))
        assertEquals(DeepLink.OpenSection(Routes.REPLAY), DeepLink.parse(DeepLink.section(Routes.REPLAY)))
        assertTrue(DeepLink.isSectionRoute(Routes.REPLAY))
        // Inconnu : toujours l'accueil.
        assertEquals(DeepLink.OpenSection(Routes.HOME), DeepLink.resolve("ultratv://replays"))
        assertEquals(DeepLink.OpenSection(Routes.HOME), DeepLink.resolve("ultratv://replay/1"))
    }

    @Test fun dernierEcran_memoriseLeReplay() = assertEquals(Routes.REPLAY, rememberableScreen(Routes.REPLAY))
}
