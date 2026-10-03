package com.ultratv.tv.nativeapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {
    @Test fun resolutionsTv_donnentLaMemeHauteurEnDp() {
        // 720p, 1080p et 4K : toujours 540 dp de haut, quelle que soit la densité système.
        for (h in listOf(720, 1080, 2160)) {
            val d = uiDensityFor(h, systemDensity = 2f)
            assertEquals(540f, h / d, 0.001f)
        }
    }

    @Test fun largeur_16_9_donne_960dp() = assertEquals(960f, 1920 / uiDensityFor(1080, 2f), 0.001f)

    @Test fun hauteurInconnue_garde_la_densite_systeme() = assertEquals(2.5f, uiDensityFor(0, 2.5f), 0f)

    @Test fun conversionMaquette_1920x1080_vers_dp() {
        assertEquals(48f, 96.design.value, 0f)   // marge horizontale de la maquette
        assertEquals(42f, 84.design.value, 0f)   // titre 84 px
    }
}
