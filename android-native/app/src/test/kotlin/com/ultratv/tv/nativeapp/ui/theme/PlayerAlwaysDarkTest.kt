package com.ultratv.tv.nativeapp.ui.theme

import com.ultratv.tv.nativeapp.nav.Routes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAlwaysDarkTest {
    @Test fun isLightEffective_lecteurAffiche_resteSombre() {
        assertFalse(isLightEffective(themeLight = true, playerShown = true))
        assertTrue(isLightEffective(themeLight = true, playerShown = false))
        assertFalse(isLightEffective(themeLight = false, playerShown = false))
    }

    @Test fun isPlayerShown_lienProfondEmpileUnSecondLecteur_restePresentJusquAuDernier() {
        // Lien profond ultratv://live/… alors qu'un lecteur est déjà affiché : deux lecteurs coexistent.
        assertTrue(isPlayerShown(listOf(Routes.HOME, Routes.PLAYER, Routes.PLAYER)))
        // L'ancien sort : le nouveau reste, donc toujours sombre (un compteur se désynchronisait ici).
        assertTrue(isPlayerShown(listOf(Routes.HOME, Routes.PLAYER)))
        assertFalse(isPlayerShown(listOf(Routes.HOME)))
        assertFalse(isPlayerShown(emptyList()))
    }
}
