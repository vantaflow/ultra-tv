package com.ultratv.tv.nativeapp.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class StartScreenTest {
    @Test fun startupTarget_chaqueEcran_actionAttendue() {
        assertEquals(StartupTarget.Stay, startupTarget(StartScreen.HOME))
        assertEquals(StartupTarget.Route(Routes.LIVE), startupTarget(StartScreen.LIVE))
        assertEquals(StartupTarget.Route(Routes.MOVIES), startupTarget(StartScreen.MOVIES))
        assertEquals(StartupTarget.Route(Routes.SERIES), startupTarget(StartScreen.SERIES))
        assertEquals(StartupTarget.Route(Routes.GUIDE), startupTarget(StartScreen.GUIDE))
        assertEquals(StartupTarget.LastChannel, startupTarget(StartScreen.LAST_CHANNEL))
    }

    @Test fun parse_cleDeChaqueEcran_allerRetour() {
        for (s in StartScreen.entries) assertEquals(s, StartScreen.parse(s.key, legacyAutoPlayLast = false))
    }

    @Test fun parse_reglageAbsent_ancienReprendreAuLancement_derniereChaine() {
        assertEquals(StartScreen.LAST_CHANNEL, StartScreen.parse("", legacyAutoPlayLast = true))
        assertEquals(StartScreen.HOME, StartScreen.parse("", legacyAutoPlayLast = false))
        assertEquals(StartScreen.HOME, StartScreen.parse("inconnu", legacyAutoPlayLast = false))
    }

    @Test fun parse_reglageExplicite_prioritaireSurLAncien() {
        assertEquals(StartScreen.HOME, StartScreen.parse("home", legacyAutoPlayLast = true))
        assertEquals(StartScreen.GUIDE, StartScreen.parse("GUIDE", legacyAutoPlayLast = true))
    }
}
