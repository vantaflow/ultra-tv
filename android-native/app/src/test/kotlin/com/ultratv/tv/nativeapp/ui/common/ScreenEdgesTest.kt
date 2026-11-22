package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ultratv.tv.nativeapp.ui.design.ScreenEdges
import com.ultratv.tv.nativeapp.ui.design.Ux
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * « La barre latérale est tronquée » (Samsung 1080p qui coupe les bords) : réglage Bords de l'écran (TV). Toute
 * l'interface tient dans la zone intérieure, à la même maquette de 540 dp (rien ne se tasse, même à 7,5 %) ; la vidéo
 * plein écran ressort de la marge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ScreenEdgesTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @After fun reset() { Ux.screenEdges = ScreenEdges.DEFAULT }

    @Test fun reglage_defautPetit_valeursConnues() {
        assertEquals(ScreenEdges.SMALL, ScreenEdges.parse(null))
        assertEquals(ScreenEdges.SMALL, ScreenEdges.parse("enorme"))
        assertEquals(listOf(0f, 0.025f, 0.05f, 0.075f), ScreenEdges.entries.map { it.fraction })
        assertEquals(IntOffset.Zero, screenInsetPx(1920, 1080, 0f))
        assertEquals(IntOffset(144, 81), screenInsetPx(1920, 1080, 0.075f))
    }

    @Test fun maquetteIdentique_aToutesLesMarges() {
        for (e in ScreenEdges.entries) {
            val inset = screenInsetPx(1920, 1080, e.fraction)
            val w = 1920 - 2 * inset.x; val h = 1080 - 2 * inset.y
            val d = scaledDensity(tv = true, heightPx = h, base = Density(2f), textScale = 1f, ui = 1f)
            assertEquals("hauteur ${e.key}", 540f, h / d.density, 0.01f)
            assertEquals("largeur ${e.key}", 960f, w / d.density, 2f)
        }
    }

    @Test fun grandeMarge_interfaceDansLaZoneInterieure_videoBordABord() {
        Ux.screenEdges = ScreenEdges.LARGE
        org.robolectric.Shadows.shadowOf(rule.activity.packageManager).setSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK, true)
        var root = IntSize.Zero; var content = IntSize.Zero; var video = IntSize.Zero
        var contentX = 0f; var videoX = 0f; var widthDp = 0f
        rule.setContent {
            Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.size }) {
                ProvideUiScale {
                    widthDp = LocalUiWidthDp.current
                    Box(Modifier.fillMaxSize().onGloballyPositioned { content = it.size; contentX = it.positionInRoot().x }) {
                        Box(Modifier.fillMaxSize().bleedScreenEdges().onGloballyPositioned { video = it.size; videoX = it.positionInRoot().x })
                    }
                }
            }
        }
        rule.waitForIdle()
        val inset = screenInsetPx(root.width, root.height, 0.075f)
        assertEquals(root.width - 2 * inset.x, content.width)
        assertEquals(root.height - 2 * inset.y, content.height)
        assertEquals(inset.x.toFloat(), contentX, 0.5f)
        assertEquals(root, video)
        assertEquals(0f, videoX, 0.5f)
        assertTrue("largeur d'interface $widthDp dp", widthDp in 955f..965f)
    }
}
