package com.ultratv.tv.nativeapp.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.UiSize
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.detail.resetDetailHost
import com.ultratv.tv.nativeapp.ui.detail.setDetail
import com.ultratv.tv.nativeapp.ui.settings.AppearancePicker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Libellés de l'appli coupés par « … » dans l'arbre affiché (même méthode que TextOverflowTest). */
internal fun ComposeContentTestRule.ellipsizedTexts(): List<String> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { n ->
        val out = mutableListOf<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        val r = out.firstOrNull() ?: return@mapNotNull null
        if (r.lineCount > 0 && (r.isLineEllipsized(r.lineCount - 1) || r.hasVisualOverflow && r.didOverflowWidth))
            n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } else null
    }

internal fun ComposeContentTestRule.savePng(name: String) {
    val dir = File("build/outputs/ui-v2").also { it.mkdirs() }
    val bmp = onRoot().captureToImage().asAndroidBitmap()
    File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** Étape « Apparence » de l'assistant au toucher : téléphone et tablette (portrait, paysage), police 1,3, 4 langues. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1300dp-h1300dp-xhdpi")
class AppearanceStepTouchTest {
    @get:Rule val rule = createComposeRule()
    @After fun reset() = resetDetailHost()

    private var picked: AppTheme? = null
    private var accent: AccentChoice? = null
    private var size: UiSize? = null
    private var continued = false
    private var skipped = false

    private fun show(w: Int, h: Int, lang: AppLang = AppLang.French, font: Float = 1f) {
        val a = WizardAppearance(AppTheme.DARK, AccentChoice.RED, UiSize.NORMAL)
        rule.setDetail(tv = false, width = w, height = h, lang = lang, fontScale = font) {
            Box(Modifier.fillMaxSize().background(Ux.Bg).padding(horizontal = 20.dp)) {
                AppearanceStepContent(
                    stringsFor(lang).wiz, a,
                    onTheme = { picked = it }, onAccent = { accent = it }, onUiSize = { size = it },
                    onContinue = { continued = true }, onSkip = { skipped = true },
                )
            }
        }
    }

    private fun checkAll(w: Int, h: Int, lang: AppLang, font: Float) {
        show(w, h, lang, font)
        for (t in AppTheme.entries) rule.onNodeWithTag(AppearancePicker.THEME_TAG + t.name).assertExists()
        rule.onNodeWithTag(AppearancePicker.THEME_TAG + AppTheme.DARK.name).assertIsSelected()
        for (a in AccentChoice.entries) rule.onNodeWithTag(AppearancePicker.ACCENT_TAG + a.key).assertExists()
        for (s in UiSize.entries) rule.onNodeWithTag(AppearancePicker.SIZE_TAG + s.key).assertExists()
        val cut = rule.ellipsizedTexts()
        assertTrue("${w}x$h ${lang.code} police $font : textes coupés $cut", cut.isEmpty())
    }

    @Test fun telephone_choixAppliques() {
        show(360, 780)
        rule.onNodeWithTag(AppearancePicker.THEME_TAG + AppTheme.SAND.name).performScrollTo().performClick()
        rule.onNodeWithTag(AppearancePicker.ACCENT_TAG + AccentChoice.BLUE.key).performScrollTo().performClick()
        rule.onNodeWithTag(AppearancePicker.SIZE_TAG + UiSize.LARGE.key).performScrollTo().performClick()
        assertEquals(AppTheme.SAND, picked); assertEquals(AccentChoice.BLUE, accent); assertEquals(UiSize.LARGE, size)
    }

    @Test fun telephone_police13_sansTexteCoupe() = checkAll(360, 780, AppLang.French, 1.3f)
    @Test fun tabletteVerticale_police13_sansTexteCoupe() { checkAll(800, 1280, AppLang.French, 1.3f); rule.savePng("onboarding-appearance-tablet-portrait.png") }
    @Test fun tabletteHorizontale_police13_sansTexteCoupe() { checkAll(1280, 800, AppLang.French, 1.3f); rule.savePng("onboarding-appearance-tablet-landscape.png") }
    @Test fun tablette_anglais() = checkAll(800, 1280, AppLang.English, 1.3f)
    @Test fun tablette_espagnol() = checkAll(800, 1280, AppLang.Spanish, 1.3f)
    @Test fun tablette_arabe() = checkAll(800, 1280, AppLang.Arabic, 1.3f)
}

/** Étape « Apparence » sur TV (1920 × 1080) : aperçus, focus sur le thème actif, aucun libellé coupé. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class AppearanceStepTvTest {
    @get:Rule val rule = createComposeRule()
    @After fun reset() = resetDetailHost()

    private fun show(lang: AppLang, theme: AppTheme = AppTheme.FOREST) {
        rule.setDetail(tv = true, width = 960, height = 540, lang = lang) {
            Box(Modifier.fillMaxSize().background(Ux.Bg).padding(horizontal = 48.dp, vertical = 27.dp)) {
                AppearanceStepContent(stringsFor(lang).wiz, WizardAppearance(theme, AccentChoice.RED, UiSize.NORMAL), {}, {}, {}, {}, {})
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    @Test fun focusInitial_surLaTuileDuThemeActif() {
        show(AppLang.French)
        rule.onNodeWithTag(AppearancePicker.THEME_TAG + AppTheme.FOREST.name).assertIsSelected()
        val focused = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("un élément focalisé", focused.isNotEmpty())
        rule.savePng("onboarding-appearance-tv-fr.png")
    }

    // Une langue par test : setContent n'est appelé qu'une fois par règle.
    @Test fun francais_sansTexteCoupe() { show(AppLang.French); assertTrue(rule.ellipsizedTexts().toString(), rule.ellipsizedTexts().isEmpty()) }
    @Test fun anglais_sansTexteCoupe() { show(AppLang.English); assertTrue(rule.ellipsizedTexts().toString(), rule.ellipsizedTexts().isEmpty()) }
    @Test fun espagnol_sansTexteCoupe() { show(AppLang.Spanish); assertTrue(rule.ellipsizedTexts().toString(), rule.ellipsizedTexts().isEmpty()) }
    @Test fun arabe_sansTexteCoupe() { show(AppLang.Arabic); assertTrue(rule.ellipsizedTexts().toString(), rule.ellipsizedTexts().isEmpty()) }
}
