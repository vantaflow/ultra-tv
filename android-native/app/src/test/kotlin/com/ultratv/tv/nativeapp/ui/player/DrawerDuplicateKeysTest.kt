package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Liste des chaînes du lecteur (zapping) : deux lignes ou deux catégories de même clé (même chaîne reçue deux fois du
 * fournisseur, catégorie répétée) faisaient planter la feuille (« Key … was already used »).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class DrawerDuplicateKeysTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `chaines et categories en double - aucune exception, toutes affichees et focalisables`() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val catFocus = FocusRequester()
        val cats = listOf(DrawerCategory("ent", "Divertissement", 2), DrawerCategory("ent", "Divertissement", 2))
        val rows = listOf(
            DrawerRowUi(6, 6, null, "M6", null, 0f, false),
            DrawerRowUi(6, 6, null, "M6", null, 0f, false),
        )
        rule.setContent {
            val D = designStringsFor(AppLang.French)
            CompositionLocalProvider(LocalDs provides D, LocalStrings provides stringsFor(AppLang.French)) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                Box(Modifier.fillMaxSize()) {
                    DrawerSheet(
                        title = D.pChannels, channelsTab = D.pChannels, favoritesTab = D.catFavorites, favoritesSelected = false, onTab = {},
                        cats = cats, highlightId = "ent", rtl = false, catFocus = catFocus, onCatFocused = {}, onCatEnter = {},
                        rows = rows, listState = rememberLazyListState(), rowModifier = { Modifier },
                        onRowFocus = {}, onRowClick = {}, onAirLabel = D.onAirPill, emptyText = null,
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals(2, rule.onAllNodesWithText("M6").fetchSemanticsNodes().size)
        assertEquals(2, rule.onAllNodesWithText("Divertissement", substring = true).fetchSemanticsNodes().size)
        // Les deux catégories prennent le focus l'une après l'autre (aucune n'est sautée).
        rule.runOnIdle { catFocus.requestFocus() }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()
        rule.onAllNodesWithText("Divertissement", substring = true)[1].assertIsFocused()
    }
}
