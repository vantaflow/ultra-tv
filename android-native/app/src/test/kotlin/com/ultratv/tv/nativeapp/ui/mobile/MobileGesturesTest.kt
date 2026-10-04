package com.ultratv.tv.nativeapp.ui.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La couche de gestes traduit les événements tactiles en décisions (zones, rôles) ; la logique pure est testée à part. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w844dp-h390dp-xxhdpi")
class MobileGesturesTest {
    @get:Rule val rule = createComposeRule()

    private var taps = 0
    private var doubles = mutableListOf<TapZone>()
    private var started = mutableListOf<DragRole>()
    private var ended = mutableListOf<Pair<DragRole, Float>>()
    private var dragged = 0

    private fun layer(live: Boolean) = rule.setContent {
        GestureLayer(
            isLive = live, modifier = Modifier.fillMaxSize().testTag("layer"),
            onTap = { taps++ }, onDoubleTap = { doubles += it },
            onDragStart = { started += it }, onDrag = { _, _, _ -> dragged++ }, onDragEnd = { r, t -> ended += r to t },
            onPinch = {},
        )
    }

    @Test fun unAppui_basculeLesControles() {
        layer(live = false)
        rule.onNodeWithTag("layer").performTouchInput { click(center) }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        assertEquals(1, taps)
    }

    @Test fun doubleAppuiADroite_donneLaZoneDroite_etAGauche_laZoneGauche() {
        layer(live = false)
        rule.onNodeWithTag("layer").performTouchInput { doubleClick(Offset(width * 0.9f, height / 2f)) }
        rule.onNodeWithTag("layer").performTouchInput { doubleClick(Offset(width * 0.1f, height / 2f)) }
        assertEquals(listOf(TapZone.RIGHT, TapZone.LEFT), doubles)
    }

    @Test fun glisserVerticalAGauche_estUnReglageDeLuminosite() {
        layer(live = false)
        rule.onNodeWithTag("layer").performTouchInput { swipe(Offset(width * 0.1f, height * 0.8f), Offset(width * 0.1f, height * 0.2f), 200) }
        assertEquals(listOf(DragRole.BRIGHTNESS), started)
        assertTrue(dragged > 0)
        assertEquals(DragRole.BRIGHTNESS, ended.single().first)
        assertTrue("glisser vers le haut = delta négatif", ended.single().second < 0f)
    }

    @Test fun glisserVerticalADroite_estUnReglageDeVolume() {
        layer(live = false)
        rule.onNodeWithTag("layer").performTouchInput { swipe(Offset(width * 0.9f, height * 0.2f), Offset(width * 0.9f, height * 0.8f), 200) }
        assertEquals(listOf(DragRole.VOLUME), started)
    }

    @Test fun balayageAuCentre_zappeEnDirect_maisPasEnVod() {
        layer(live = true)
        rule.onNodeWithTag("layer").performTouchInput { swipe(Offset(width * 0.5f, height * 0.8f), Offset(width * 0.5f, height * 0.2f), 200) }
        assertEquals(listOf(DragRole.ZAP), started)
    }

    @Test fun balayageAuCentre_enVod_neFaitRien() {
        layer(live = false)
        rule.onNodeWithTag("layer").performTouchInput { swipe(Offset(width * 0.5f, height * 0.8f), Offset(width * 0.5f, height * 0.2f), 200) }
        assertTrue(started.isEmpty())
        assertNull(ended.firstOrNull())
    }
}
