package com.ultratv.tv.nativeapp.ui.components

import com.ultratv.tv.nativeapp.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Test

/** La rubrique Sport n'existe dans le rail que si le réglage est activé, juste après le Guide. */
class SportRailTest {
    @Test fun rail_sansSport_estLeRailHabituel() = assertEquals(railItems.map { it.route }, railItemsFor(false).map { it.route })

    @Test fun rail_avecSport_ajouteUneSeuleEntree_apresLeGuide() {
        val routes = railItemsFor(true).map { it.route }
        assertEquals(railItems.size + 1, routes.size)
        assertEquals(1, routes.count { it == Routes.SPORT })
        assertEquals(routes.indexOf(Routes.GUIDE) + 1, routes.indexOf(Routes.SPORT))
    }

    @Test fun rail_laRubriqueSportEstSelectionneeSurSaRoute() {
        assertEquals(listOf(Routes.SPORT), railItemsFor(true).filter { isSelected(Routes.SPORT, it.route) }.map { it.route })
    }
}
