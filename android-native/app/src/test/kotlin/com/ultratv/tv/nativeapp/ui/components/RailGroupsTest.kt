package com.ultratv.tv.nativeapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Groupes du rail (maquette « Menu latéral ») : Regarder, Ma liste, puis compte et réglages en bas. */
class RailGroupsTest {
    private fun routes(sport: Boolean, g: RailGroup) = railItemsFor(sport).filter { it.group == g }.map { it.route }

    @Test fun groupes_sansSport() {
        assertEquals(listOf("home", "live", "guide", "movies", "series"), routes(false, RailGroup.WATCH))
        assertEquals(listOf("favorites", "recordings", "downloads"), routes(false, RailGroup.MINE))
        assertEquals(listOf("account", "settings"), routes(false, RailGroup.FOOT))
    }

    @Test fun sport_rejointRegarder_apresLeGuide() =
        assertEquals(listOf("home", "live", "guide", "sport", "movies", "series"), routes(true, RailGroup.WATCH))
}
