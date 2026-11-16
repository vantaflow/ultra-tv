package com.ultratv.tv.nativeapp.ui.simple

import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import com.ultratv.tv.nativeapp.nav.StartScreen
import com.ultratv.tv.nativeapp.nav.StartupTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mode simple sur appareil tactile : option masquée et mode jamais appliqué (retour à l'interface normale). */
class SimpleModeTouchTest {
    @Test fun optionProposeeSeulementSurTv() {
        assertTrue(simpleModeAvailable(touch = false))
        assertFalse(simpleModeAvailable(touch = true))
    }

    @Test fun modeSimpleActifSurTelephone_interfaceNormale() {
        val prefs = UserPrefs(simpleMode = true)
        assertEquals(HomeKind.SIMPLE, homeKindFor(prefs, touch = false))
        assertEquals(HomeKind.STANDARD, homeKindFor(prefs, touch = true))
    }

    @Test fun modeSimpleInactif_toujoursNormal() {
        assertEquals(HomeKind.STANDARD, homeKindFor(UserPrefs(simpleMode = false), touch = false))
        assertFalse(simpleModeApplies(false, touch = false))
        assertFalse(simpleModeApplies(true, touch = true))
        assertTrue(simpleModeApplies(true, touch = false))
    }

    @Test fun ouvertureSurTelephone_ignoreLeModeSimple() {
        // Mode simple non appliqué : la rubrique choisie s'ouvre comme en mode normal (guide compris).
        val applies = simpleModeApplies(true, touch = true)
        assertEquals(SimpleHome.startupTarget(false, StartScreen.GUIDE), SimpleHome.startupTarget(applies, StartScreen.GUIDE))
        assertEquals(StartupTarget.Route(com.ultratv.tv.nativeapp.nav.Routes.GUIDE), SimpleHome.startupTarget(applies, StartScreen.GUIDE))
    }
}
