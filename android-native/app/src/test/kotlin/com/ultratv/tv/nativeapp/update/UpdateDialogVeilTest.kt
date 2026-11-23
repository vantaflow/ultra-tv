package com.ultratv.tv.nativeapp.update

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.LocalOverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayLayer
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.paletteFor
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Couleurs de l'invite : voile et surface opaques dans les 10 thèmes, en clair comme en sombre effectif. */
class UpdateDialogColorsTest {
    @Test fun `voile et surface opaques dans tous les themes`() {
        assertEquals(10, AppTheme.entries.size)
        for (theme in AppTheme.entries) for (light in listOf(false, true)) {
            val p = paletteFor(light, theme)
            assertEquals("voile $theme light=$light", 1f, updateVeil(p).alpha)
            assertEquals("surface $theme light=$light", 1f, updateSurface(p).alpha)
        }
    }
}

/** Invite ouverte par-dessus un écran : rien ne transparaît, le focus reste dedans, Retour ferme. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class UpdateDialogVeilTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Suppress("UNCHECKED_CAST")
    private val flow: MutableStateFlow<UpdateChecker.UpdateInfo?>
        get() = UpdateChecker::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(null) as MutableStateFlow<UpdateChecker.UpdateInfo?>

    @After fun tearDown() { flow.value = null; Ux.themeKind = AppTheme.DARK; Ux.themeLight = false }

    private var closed = 0

    private fun show(theme: AppTheme, light: Boolean) {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.themeKind = theme; Ux.themeLight = light
        flow.value = UpdateChecker.UpdateInfo(tag = "v9.9.9", versionName = "9.9.9", versionCode = 999, apkUrl = "https://example.invalid/a.apk", notes = "")
        rule.setContent {
            val overlays = remember { OverlayHost() }
            CompositionLocalProvider(
                LocalOverlayHost provides overlays, LocalDs provides designStringsFor(AppLang.French),
                LocalStrings provides stringsFor(AppLang.French), LocalTouch provides false,
            ) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                Box(Modifier.fillMaxSize()) {
                    // « Menus » de l'écran derrière l'invite.
                    Column { PillButton("Menu derrière", onClick = {}); PillButton("Autre menu", onClick = {}) }
                    UpdateDialog(onClose = { closed++ })
                    OverlayLayer(overlays)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun `voile opaque - le coin de l'ecran a la couleur du voile (theme Sable, clair)`() {
        show(AppTheme.SAND, true)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        // Coin haut-gauche : là où est dessiné « Menu derrière ». Les 10 thèmes : UpdateDialogColorsTest.
        assertEquals(updateVeil(paletteFor(true, AppTheme.SAND)).toArgb(), bmp.getPixel(20, 20))
        rule.onNodeWithTag(UPDATE_VEIL_TAG).assertExists()
    }

    @Test fun `focus piege dans l'invite puis Retour ferme`() {
        show(AppTheme.MIDNIGHT, false)
        repeat(4) { rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }; rule.waitForIdle() }
        repeat(4) { rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }; rule.waitForIdle() }
        rule.onNodeWithText("Menu derrière").assertIsNotFocused()
        rule.onNodeWithText("Autre menu").assertIsNotFocused()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(1, closed)
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.hasTestTag(UPDATE_VEIL_TAG)).fetchSemanticsNodes().isEmpty())
    }
}
