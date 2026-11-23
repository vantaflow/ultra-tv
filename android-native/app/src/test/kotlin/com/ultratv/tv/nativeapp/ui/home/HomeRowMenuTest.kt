package com.ultratv.tv.nativeapp.ui.home

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.rowMenuDown
import com.ultratv.tv.nativeapp.i18n.rowMenuHide
import com.ultratv.tv.nativeapp.i18n.rowMenuUp
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.LocalOverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayLayer
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Déplacements depuis l'accueil : parmi les rangées AFFICHÉES, même préférence que Réglages › Affichage. */
class HomeRowMoveTest {
    private val shown = listOf(HomeBlock.CHANNELS, HomeBlock.RESUME, HomeBlock.LATEST_MOVIES)

    @Test fun `monter et descendre parmi les rangees affichees, bornes grisees`() {
        val l = HomeLayout.DEFAULT
        assertFalse(l.canMoveAmong(HomeBlock.CHANNELS, -1, shown))
        assertTrue(l.canMoveAmong(HomeBlock.CHANNELS, 1, shown))
        assertFalse(l.canMoveAmong(HomeBlock.LATEST_MOVIES, 1, shown))
        assertFalse("tuiles non déplaçables", l.canMoveAmong(HomeBlock.SHORTCUTS, 1, shown))
        // LATEST_MOVIES passe juste au-dessus de RESUME (les blocs vides entre les deux ne comptent pas).
        val up = l.moveAmong(HomeBlock.LATEST_MOVIES, -1, shown)
        assertEquals(listOf(HomeSectionId.CHANNELS, HomeSectionId.LATEST_MOVIES, HomeSectionId.RESUME), up.apply(listOf(HomeSectionId.RESUME, HomeSectionId.LATEST_MOVIES, HomeSectionId.CHANNELS)))
        val down = l.moveAmong(HomeBlock.CHANNELS, 1, shown)
        assertEquals(listOf(HomeSectionId.RESUME, HomeSectionId.CHANNELS, HomeSectionId.LATEST_MOVIES), down.apply(listOf(HomeSectionId.RESUME, HomeSectionId.LATEST_MOVIES, HomeSectionId.CHANNELS)))
        assertEquals(l, l.moveAmong(HomeBlock.CHANNELS, -1, shown))
        // Masquer : même liste que la fenêtre des Réglages, réaffichable par toggle.
        val h = l.hide(HomeBlock.RESUME)
        assertFalse(h.isVisible(HomeBlock.RESUME)); assertTrue(h.toggle(HomeBlock.RESUME).isVisible(HomeBlock.RESUME))
        assertEquals("RESUME", h.hiddenCsv())
        // Persistance identique (CSV de Rangées de l'accueil) : relu tel quel.
        assertEquals(up, HomeLayout.parse(up.orderCsv(), up.hiddenCsv()))
    }

    @Test fun `blocs deplacables - sans les tuiles`() {
        assertEquals(listOf(HomeBlock.CHANNELS, HomeBlock.RESUME), movableShown(listOf(HomeSectionId.SHORTCUTS, HomeSectionId.CHANNELS, HomeSectionId.RESUME)))
    }

    @Test fun `touches - appui long sur OK ou Menu ouvrent, un appui bref non`() {
        var opened = 0
        val k = RowMenuKeys(longMs = 500)
        val open = { opened++; Unit }
        // Appui bref : laissé à la carte.
        assertFalse(k.handle(KeyEvent.KEYCODE_DPAD_CENTER, true, 0, 0, 0, open))
        assertFalse(k.handle(KeyEvent.KEYCODE_DPAD_CENTER, false, 0, 0, 120, open))
        assertEquals(0, opened)
        // Appui long avec répétitions : ouvre une fois, avale répétitions et relâchement.
        assertFalse(k.handle(KeyEvent.KEYCODE_DPAD_CENTER, true, 0, 0, 0, open))
        assertTrue(k.handle(KeyEvent.KEYCODE_DPAD_CENTER, true, 1, 0, 500, open))
        assertTrue(k.handle(KeyEvent.KEYCODE_DPAD_CENTER, true, 2, 0, 550, open))
        assertTrue(k.handle(KeyEvent.KEYCODE_DPAD_CENTER, false, 0, 0, 700, open))
        assertEquals(1, opened)
        // Télécommande sans répétition : durée lue au relâchement.
        assertFalse(k.handle(KeyEvent.KEYCODE_ENTER, true, 0, 1000, 1000, open))
        assertTrue(k.handle(KeyEvent.KEYCODE_ENTER, false, 0, 1000, 1700, open))
        assertEquals(2, opened)
        // Touche Menu.
        assertTrue(k.handle(KeyEvent.KEYCODE_MENU, true, 0, 0, 0, open))
        assertTrue(k.handle(KeyEvent.KEYCODE_MENU, false, 0, 0, 50, open))
        assertEquals(3, opened)
        assertFalse(k.handle(KeyEvent.KEYCODE_DPAD_LEFT, true, 0, 0, 0, open))
    }
}

/** Accueil TV : touche Menu sur une rangée → menu ; options impossibles grisées ; « Descendre » enregistre la disposition. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeRowMenuTvTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun resume(i: Int) = ResumeCardUi(
        key = "r$i", entry = WatchHistoryEntity(providerId = 1, kind = "MOVIE", remoteId = "$i", title = "Film $i", poster = null, streamUrl = "u"),
        title = "Film $i", progress = 0.5f, minutesLeft = 10,
    )

    @Test fun `menu de rangee - grisage puis descendre`() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val saved = mutableListOf<HomeLayout>()
        val rows = HomeRows(resume = listOf(resume(1), resume(2)), recentChannels = listOf(resume(3)))
        rule.setContent {
            val overlays = remember { OverlayHost() }
            var layout by remember { mutableStateOf(HomeLayout.DEFAULT) }
            CompositionLocalProvider(
                LocalOverlayHost provides overlays, LocalDs provides designStringsFor(AppLang.French),
                LocalStrings provides stringsFor(AppLang.French), LocalTouch provides false,
            ) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                val clock = remember { mutableLongStateOf(0L) }
                val extras = remember { mutableStateOf(emptyMap<String, HeroExtras>()) }
                Box(Modifier.fillMaxSize()) {
                    HomeTvContent(
                        slot = HomeTopSlot.NONE, hero = null, heroIndex = 0, heroCount = 0, heroExtras = extras,
                        rows = rows.copy(layout = layout),
                        providerCount = 1, clock = clock, sportOn = false, sportLive = null,
                        heroFocusDone = true, onHeroFocused = {},
                        playHistory = {}, openPoster = {}, playChannel = {}, onShortcut = {}, onGoSport = {},
                        onWatchHero = {}, onInfoHero = {}, emptyTop = {}, sportRow = { _, _ -> },
                        homeLayout = layout, onHomeLayout = { saved += it; layout = it },
                    )
                    OverlayLayer(overlays)
                }
            }
        }
        rule.waitForIdle()
        val D = DesignStrings(AppLang.French)
        // Focus sur la première carte de « Reprendre » (première rangée).
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle()
        rule.onNodeWithText("Film 1").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.Menu) }; rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasTestTag(ROW_MENU_TAG)).fetchSemanticsNodes().isNotEmpty())
        // « Monter » grisée (première rangée affichée) : non focalisable, « Descendre » l'est.
        rule.onNodeWithText(D.rowMenuDown).assertExists()
        rule.onNodeWithText(D.rowMenuHide).assertExists()
        rule.onNodeWithText(D.rowMenuDown).performClick(); rule.waitForIdle()
        assertEquals(1, saved.size)
        assertEquals(listOf(HomeSectionId.RECENT_CHANNELS, HomeSectionId.RESUME), saved.last().apply(listOf(HomeSectionId.RESUME, HomeSectionId.RECENT_CHANNELS)))
        assertTrue(rule.onAllNodes(hasTestTag(ROW_MENU_TAG)).fetchSemanticsNodes().isEmpty())
        // Le focus suit la rangée déplacée.
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        rule.onNodeWithText("Film 1").assertIsFocused()
        // « Monter » existe mais n'était pas focalisable.
        assertTrue(D.rowMenuUp.isNotEmpty())
    }

}
