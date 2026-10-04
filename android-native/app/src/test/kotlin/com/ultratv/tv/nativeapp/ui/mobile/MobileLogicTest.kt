package com.ultratv.tv.nativeapp.ui.mobile

import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileLogicTest {
    // ── Bascule de navigation selon la taille et le type d'appareil ──

    @Test fun navLayoutFor_tv_gardeToujoursLeRailDpad() {
        assertEquals(NavLayout.TV_RAIL, navLayoutFor(tv = true, widthDp = 960f, heightDp = 540f))
        // Même une « TV » à fenêtre étroite (multi-fenêtre) ne reçoit jamais la barre tactile.
        assertEquals(NavLayout.TV_RAIL, navLayoutFor(tv = true, widthDp = 360f, heightDp = 540f))
    }

    @Test fun navLayoutFor_telephonePortrait_barreDOnglets() {
        assertEquals(NavLayout.BOTTOM_TABS, navLayoutFor(tv = false, widthDp = 390f, heightDp = 844f))
    }

    @Test fun navLayoutFor_telephonePaysage_barreDOnglets_carLaHauteurEstCompacte() {
        assertEquals(NavLayout.BOTTOM_TABS, navLayoutFor(tv = false, widthDp = 844f, heightDp = 390f))
    }

    @Test fun navLayoutFor_tablette_railLateral() {
        assertEquals(NavLayout.SIDE_RAIL, navLayoutFor(tv = false, widthDp = 1194f, heightDp = 834f))
        assertEquals(NavLayout.SIDE_RAIL, navLayoutFor(tv = false, widthDp = 800f, heightDp = 1280f))
    }

    @Test fun windowClassFor_seuilsMaterial() {
        assertEquals(WindowClass.COMPACT, windowClassFor(599f))
        assertEquals(WindowClass.MEDIUM, windowClassFor(600f))
        assertEquals(WindowClass.MEDIUM, windowClassFor(839f))
        assertEquals(WindowClass.EXPANDED, windowClassFor(840f))
    }

    @Test fun tabForRoute_regroupeLesEcransSecondairesSousPlus() {
        assertEquals(MobileTab.HOME, tabForRoute(Routes.HOME))
        assertEquals(MobileTab.LIVE, tabForRoute(Routes.LIVE))
        assertEquals(MobileTab.LIVE, tabForRoute(Routes.PLAYER))
        assertEquals(MobileTab.GUIDE, tabForRoute(Routes.GUIDE))
        assertEquals(MobileTab.MOVIES, tabForRoute(Routes.MOVIES))
        assertEquals(MobileTab.MOVIES, tabForRoute(Routes.MOVIE_DETAIL))
        for (r in MORE_ROUTES) if (r != "profiles") assertEquals(r, MobileTab.MORE, tabForRoute(r))
        assertEquals(MobileTab.MORE, tabForRoute(Routes.SERIES_DETAIL))
    }

    @Test fun isFullscreenRoute_seulementLeLecteur() {
        assertTrue(isFullscreenRoute(Routes.PLAYER))
        assertFalse(isFullscreenRoute(Routes.LIVE))
        assertFalse(isFullscreenRoute(null))
    }

    @Test fun railRouteFor_fichesEtSousEcransPortentLEntreeParente() {
        assertEquals(Routes.MOVIES, railRouteFor(Routes.MOVIE_DETAIL))
        assertEquals(Routes.SERIES, railRouteFor(Routes.SERIES_DETAIL))
        assertEquals(Routes.SETTINGS, railRouteFor("categories"))
        assertEquals(Routes.LIVE, railRouteFor(Routes.PLAYER))
    }

    @Test fun gridColumns_adapteLeNombreDeColonnesALaLargeur() {
        assertEquals(2, gridColumns(300f))
        assertEquals(3, gridColumns(390f))
        assertTrue(gridColumns(1194f) > gridColumns(390f))
        assertEquals(2, gridColumns(0f))
    }

    @Test fun usesTwoPane_seulementEnLarge() {
        assertFalse(usesTwoPane(700f))
        assertTrue(usesTwoPane(1194f))
    }

    // ── Gestes du lecteur ──

    @Test fun tapZone_tiers() {
        assertEquals(TapZone.LEFT, tapZone(10f, 900f))
        assertEquals(TapZone.CENTER, tapZone(450f, 900f))
        assertEquals(TapZone.RIGHT, tapZone(890f, 900f))
        assertEquals(TapZone.CENTER, tapZone(5f, 0f))
    }

    @Test fun doubleTapSeekMs_vodSeulement() {
        assertEquals(-10_000L, doubleTapSeekMs(TapZone.LEFT, isLive = false))
        assertEquals(10_000L, doubleTapSeekMs(TapZone.RIGHT, isLive = false))
        assertNull(doubleTapSeekMs(TapZone.CENTER, isLive = false))
        assertNull(doubleTapSeekMs(TapZone.LEFT, isLive = true))
        assertNull(doubleTapSeekMs(TapZone.RIGHT, isLive = true))
    }

    @Test fun seekTarget_borneAZeroEtALaDuree() {
        assertEquals(0L, seekTarget(4_000, -10_000, 60_000))
        assertEquals(60_000L, seekTarget(55_000, 10_000, 60_000))
        assertEquals(25_000L, seekTarget(15_000, 10_000, 60_000))
        assertEquals(15_000L, seekTarget(5_000, 10_000, -1))   // durée inconnue : pas de plafond
    }

    @Test fun dragRole_gaucheLuminosite_droiteVolume_centreZapEnDirect() {
        assertEquals(DragRole.BRIGHTNESS, dragRole(50f, 900f, isLive = true))
        assertEquals(DragRole.VOLUME, dragRole(850f, 900f, isLive = false))
        assertEquals(DragRole.ZAP, dragRole(450f, 900f, isLive = true))
        assertEquals(DragRole.NONE, dragRole(450f, 900f, isLive = false))
    }

    @Test fun levelAfterDrag_versLeHautAugmente_borne() {
        assertEquals(0.75f, levelAfterDrag(0.5f, -250f, 1000f), 0.0001f)
        assertEquals(0.25f, levelAfterDrag(0.5f, 250f, 1000f), 0.0001f)
        assertEquals(1f, levelAfterDrag(0.9f, -900f, 1000f), 0.0001f)
        assertEquals(0f, levelAfterDrag(0.1f, 900f, 1000f), 0.0001f)
        assertEquals(0.4f, levelAfterDrag(0.4f, 100f, 0f), 0.0001f)
    }

    @Test fun zapFor_seuil() {
        assertEquals(1, zapFor(-200f, 120f))
        assertEquals(-1, zapFor(200f, 120f))
        assertNull(zapFor(60f, 120f))
    }

    @Test fun aspectAfterPinch_ecarterAgrandit_pincerReduit() {
        assertEquals(AspectMode.FILL, aspectAfterPinch(AspectMode.FIT, 1.3f))
        assertEquals(AspectMode.ZOOM, aspectAfterPinch(AspectMode.FILL, 1.3f))
        assertEquals(AspectMode.ZOOM, aspectAfterPinch(AspectMode.ZOOM, 1.5f))
        assertEquals(AspectMode.FIT, aspectAfterPinch(AspectMode.FILL, 0.7f))
        assertEquals(AspectMode.FIT, aspectAfterPinch(AspectMode.FIT, 0.5f))
        assertEquals(AspectMode.FILL, aspectAfterPinch(AspectMode.FILL, 1.05f))   // geste trop faible : inchangé
        assertEquals(AspectMode.FILL, aspectAfterPinch(AspectMode.R16_9, 1.3f))   // format forcé : repart de FIT
    }
}
