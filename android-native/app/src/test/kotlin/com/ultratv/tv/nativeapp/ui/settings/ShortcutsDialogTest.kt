package com.ultratv.tv.nativeapp.ui.settings

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.mobile.DEFAULT_BOTTOM_TABS
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Réglage « Raccourcis » (barre du bas du téléphone) : déplacer, rétablir ; rendu écrit en PNG. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ShortcutsDialogTest {
    @get:Rule val rule = createComposeRule()

    @Test fun deplacer_retablir() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val saved = mutableListOf<List<String>>()
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French), LocalTouch provides true) {
                ShortcutsDialog(DEFAULT_BOTTOM_TABS, sportOn = false, onSave = { saved += it }, onDismiss = {})
            }
        }
        rule.waitForIdle()
        val moveDown = I18n.string(AppLang.French, com.ultratv.tv.nativeapp.R.string.perso_move_down)
        val reset = I18n.string(AppLang.French, com.ultratv.tv.nativeapp.R.string.perso_reset_default)
        // ▼ du 1er raccourci (Direct) : Direct passe en 2e.
        rule.onAllNodesWithContentDescription(moveDown)[0].performClick()
        rule.waitForIdle()
        assertEquals(listOf(Routes.GUIDE, Routes.LIVE, Routes.MOVIES), saved.last())
        File("build/outputs/ui-v2").mkdirs()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/outputs/ui-v2/shortcuts-phone.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Rétablir.
        rule.onNodeWithText(reset, useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(DEFAULT_BOTTOM_TABS, saved.last())
    }
}
