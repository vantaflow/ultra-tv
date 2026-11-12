package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.i18n.stringsOf
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parcours D-pad du lecteur film : Gauche / Droite avec les commandes masquées les affichent avec le focus sur la barre de
 * progression ; sur la barre ils font défiler la position sans bouger le focus ; Bas va aux boutons, où Gauche / Droite
 * circulent ; Haut revient sur la barre. Les décisions sont celles de SeekScrubLogic.decide, comme dans PlayerScreen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class VodScrubFocusTest {
    @get:Rule val rule = createComposeRule()

    private var shown by mutableStateOf(false)
    private var barFocused = false
    private var scrub: SeekScrubLogic.State? = null
    private var clock = 0L

    private fun setUp() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = AppLang.English
        val labels = vodLabels(PlayerExtraStrings(lang), stringsOf(lang), DesignStrings(lang))
        val rootFocus = FocusRequester(); val pauseFocus = FocusRequester(); val barFocus = FocusRequester()
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                var focusBarOnShow by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { rootFocus.requestFocus() }
                LaunchedEffect(shown) { if (shown) runCatching { if (focusBarOnShow) barFocus.requestFocus() else pauseFocus.requestFocus() } }
                Box(
                    Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFocus).focusable().onPreviewKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val k = when (ev.key) { Key.DirectionLeft -> SeekScrubLogic.Key.BACK; Key.DirectionRight -> SeekScrubLogic.Key.FORWARD; else -> SeekScrubLogic.Key.OTHER }
                        when (val a = SeekScrubLogic.decide(k, false, hidden = !shown, barFocused = barFocused, scrubbing = scrub != null)) {
                            is SeekScrubLogic.Action.Scrub -> {
                                if (a.focusBar) focusBarOnShow = true
                                clock += 100
                                scrub = SeekScrubLogic.press(scrub, 1872.0, 7560.0, a.forward, false, clock)
                                shown = true
                                true
                            }
                            else -> false
                        }
                    },
                ) {
                    if (shown) VodOverlay(
                        title = "Orbite basse", badges = TechBadges(), pos = { 1_872_000L }, dur = { 7_560_000L }, speed = 1f,
                        playing = true, loading = false, hasPrev = false, hasNext = false, sleepActive = false,
                        pauseFocus = pauseFocus, labels = labels,
                        onPrev = {}, onSeek = {}, onToggle = {}, onNext = {}, onSubs = {}, onSleep = {}, onOptions = {}, onAudio = {},
                        barFocus = barFocus, onBarFocus = { barFocused = it },
                        scrubTarget = { scrub?.let { (it.target * 1000).toLong() } ?: -1L },
                        scrubDelta = { scrub?.let { SeekScrubLogic.fmtDelta(it.deltaSec) }.orEmpty() },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun gauche_commandesMasquees_afficheLesCommandesAvecLeFocusSurLaBarre() {
        setUp()
        press(Key.DirectionLeft)
        assertTrue(shown)
        assertTrue("le focus doit être sur la barre", barFocused)
        assertEquals(1862.0, scrub!!.target, 0.0)
        rule.onNodeWithContentDescription("Progress bar").assertExists()
    }

    @Test fun surLaBarre_gaucheDroiteDefilent_sansBougerLeFocus_basVaAuxBoutons_hautRevient() {
        setUp()
        press(Key.DirectionLeft)
        press(Key.DirectionRight); press(Key.DirectionRight)
        assertTrue(barFocused)
        assertEquals(1882.0, scrub!!.target, 0.0)
        press(Key.DirectionDown)
        assertFalse("Bas va vers les boutons", barFocused)
        val before = scrub
        press(Key.DirectionRight)                        // sur les boutons : circule, ne défile pas
        assertEquals(before, scrub)
        assertFalse(barFocused)
        press(Key.DirectionUp)
        assertTrue("Haut revient sur la barre", barFocused)
    }
}
