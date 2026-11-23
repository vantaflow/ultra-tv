package com.ultratv.tv.nativeapp.ui.home

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.heroNextPage
import com.ultratv.tv.nativeapp.i18n.heroPageOf
import com.ultratv.tv.nativeapp.i18n.heroPrevPage
import com.ultratv.tv.nativeapp.i18n.homeMoreInfo
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Logique pure du carrousel « À la une » (pas, touches média, balayage). */
class HeroCarouselLogicTest {
    @Test fun `pas en boucle dans les deux sens`() {
        assertEquals(1, heroStep(0, 3, 1))
        assertEquals(0, heroStep(2, 3, 1))
        assertEquals(2, heroStep(0, 3, -1))
        assertEquals(1, heroStep(2, 3, -1))
        assertEquals(0, heroStep(0, 0, 1))
        // Saut direct vers une pastille (pas = cible − page affichée).
        assertEquals(2, heroStep(0, 5, 2 - 0))
        assertEquals(0, heroStep(4, 5, 0 - 4))
    }

    @Test fun `touches media precedente et suivante, fleches ignorees`() {
        assertEquals(-1, heroKeyStep(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertEquals(-1, heroKeyStep(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(1, heroKeyStep(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertEquals(1, heroKeyStep(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertNull(heroKeyStep(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(heroKeyStep(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertNull(heroKeyStep(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test fun `balayage - gauche suivante, droite precedente, inverse en arabe`() {
        assertEquals(1, heroSwipeStep(-120f, rtl = false, threshold = 50f))
        assertEquals(-1, heroSwipeStep(120f, rtl = false, threshold = 50f))
        assertEquals(-1, heroSwipeStep(-120f, rtl = true, threshold = 50f))
        assertEquals(1, heroSwipeStep(120f, rtl = true, threshold = 50f))
        assertNull(heroSwipeStep(30f, rtl = false, threshold = 50f))
    }
}

/** Accueil TV : ordre du focus Regarder → Plus d'infos → ‹ → ›, OK et touches média, pause du défilement. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HeroCarouselFocusTest {
    @get:Rule val rule = createComposeRule()

    private val steps = mutableListOf<Int>()
    private val holds = mutableListOf<Boolean>()

    private fun hero(i: Int) = HeroItem(kind = HeroItem.Kind.MOVIE, id = i.toLong(), title = "Titre $i", year = 2024, genre = null, rating = null, backdrop = null, poster = null, plot = null)

    private fun show(lang: AppLang = AppLang.French, count: Int = 3) {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (lang.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                val clock = remember { mutableLongStateOf(0L) }
                val extras = remember { mutableStateOf(emptyMap<String, HeroExtras>()) }
                Box(Modifier.fillMaxSize()) {
                    HomeTvContent(
                        slot = HomeTopSlot.HERO, hero = hero(0), heroIndex = 0, heroCount = count, heroExtras = extras,
                        rows = HomeRows(), providerCount = 1, clock = clock, sportOn = false, sportLive = null,
                        heroFocusDone = false, onHeroFocused = {},
                        playHistory = {}, openPoster = {}, playChannel = {}, onShortcut = {}, onGoSport = {},
                        onWatchHero = {}, onInfoHero = {}, emptyTop = {}, sportRow = { _, _ -> },
                        onHeroStep = { steps += it }, onHeroBlockFocus = { holds += it },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test fun `ordre du focus puis OK sur les boutons de page`() {
        show()
        val D = DesignStrings(AppLang.French)
        rule.onNodeWithText(D.watch).assertIsFocused()
        assertEquals(true, holds.last())
        press(Key.DirectionRight); rule.onNodeWithText(D.homeMoreInfo).assertIsFocused()
        press(Key.DirectionRight); rule.onNodeWithContentDescription(D.heroPrevPage).assertIsFocused()
        press(Key.Enter); assertEquals(listOf(-1), steps)
        press(Key.DirectionRight); rule.onNodeWithContentDescription(D.heroNextPage).assertIsFocused()
        press(Key.Enter); assertEquals(listOf(-1, 1), steps)
        press(Key.DirectionLeft); rule.onNodeWithContentDescription(D.heroPrevPage).assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithText(D.homeMoreInfo).assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithText(D.watch).assertIsFocused()
    }

    @Test fun `touches media dans le bloc`() {
        show()
        press(Key.MediaFastForward); press(Key.MediaNext); press(Key.MediaRewind); press(Key.MediaPrevious)
        assertEquals(listOf(1, 1, -1, -1), steps)
    }

    @Test fun `arabe - meme ordre dans le sens de lecture (fleche gauche vers la fin)`() {
        show(AppLang.Arabic)
        val D = DesignStrings(AppLang.Arabic)
        rule.onNodeWithText(D.watch).assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithText(D.homeMoreInfo).assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithContentDescription(D.heroPrevPage).assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithContentDescription(D.heroNextPage).assertIsFocused()
    }

    @Test fun `un seul titre - ni boutons ni touches media captees`() {
        show(count = 1)
        press(Key.MediaNext)
        assertEquals(emptyList<Int>(), steps)
        rule.onNodeWithContentDescription(DesignStrings(AppLang.French).heroPrevPage).assertDoesNotExist()
    }

    @Test fun `libelles des pastilles en chiffres latins`() {
        assertEquals("الصفحة 2 من 5", DesignStrings(AppLang.Arabic).heroPageOf(2, 5))
        assertEquals("Page 2 sur 5", DesignStrings(AppLang.French).heroPageOf(2, 5))
    }
}
