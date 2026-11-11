package com.ultratv.tv.nativeapp.ui.player.osd

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.stringsOf
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.player.PlayerExtraStrings
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Rendu de l'OSD du Direct avec des données fictives neutres, écrit en PNG sous `build/outputs/osd/` (validation visuelle),
 * et parcours D-pad de la barre (aucun bouton inaccessible).
 *  - TV : fenêtre 960 × 540 dp à 2,0 (= 1920 × 1080 px, la densité normalisée de l'appli) ;
 *  - tactile paysage : 800 × 360 dp à 3,0 (= 2400 × 1080 px).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LiveOsdRenderTest {
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/osd").also { it.mkdirs() }

    companion object {
    private fun recents() = listOf(
        OsdRecent("p:1", null, 1, "TF1", "Météo"),
        OsdRecent("p:2", null, 7, "Arte", "Court-circuit"),
        OsdRecent("p:3", null, 6, "M6", "Capital"),
        OsdRecent("p:4", null, 12, "beIN Sports 1", "Ligue 1 · Direct"),
        OsdRecent("p:5", null, 4, "Canal+", "Le Grand Journal"),
        OsdRecent("p:6", null, 15, "BFMTV", "Édition spéciale"),
    )

    fun state(lang: AppLang, expanded: Boolean = true): LiveOsdState {
        val D = DesignStrings(lang); val X = PlayerExtraStrings(lang); val S = stringsOf(lang); val O = LiveOsdStrings(lang)
        val actions = OsdActions.build(
            OsdActionInputs(
                playing = true, tsActive = false, isFavorite = true, aspect = AspectMode.FIT, replayLabel = X.fromStart, replayActive = false,
                sleepActive = false, pipSupported = true,
            ),
            O.labels(D, X, S),
        )
        return LiveOsdState(
            channel = OsdChannelInfo(logo = null, number = 101, name = "France Info"),
            programme = OsdProgrammeInfo(
                title = "Journal de 20h",
                description = "Les titres de l'actualité nationale et internationale, suivis d'un grand reportage en direct depuis la capitale et des invités du jour.",
                timeRange = "20:00 – 21:30",
                progress = OsdProgress("20:00", "21:30", 0.4f, remainingLabel = O.remaining("54 min")),
                thenLine = "${X.thenWord} 21:30 · Grand débat",
            ),
            badge = OsdBadge(D.live, false), clock = "20:36", actions = actions, recents = recents(), recentsTitle = O.recentChannels,
            expanded = expanded, hint = O.bannerHint,
        )
    }
    }

    /**
     * À la télécommande, Compose est en mode « clavier » (après une première touche) : seul ce mode rend les éléments
     * cliquables focalisables. On simule donc une touche, puis on place le focus comme le fait l'OSD à l'ouverture.
     */
    private fun remoteFocus(target: androidx.compose.ui.focus.FocusRequester) {
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        rule.runOnIdle { target.requestFocus() }
        rule.waitForIdle()
    }

    private fun render(lang: AppLang, touch: Boolean, stage: OsdStage, file: String, expanded: Boolean = true) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val focus = LiveOsdFocus()
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides touch,
            ) {
                // Mode clavier (télécommande) : sans lui, les éléments cliquables ne prennent pas le focus.
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                // Faux plan vidéo : dégradé neutre, pour juger la lisibilité de la surcouche.
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2B5876), Color(0xFF4E4376), Color(0xFF1B2735))))) {
                    LiveOsd(state(lang, expanded), focus, onAction = {}, onRecent = {}, initialStage = stage, onBack = if (touch) ({}) else null, backDescription = "Retour")
                }
            }
        }
        rule.waitForIdle()
        if (!touch && expanded) remoteFocus(if (stage == OsdStage.RECENTS) focus.recents else focus.bar)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("OSD capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
    }

    @Test fun capture_tv_fr_focusBarre() { render(AppLang.French, false, OsdStage.CONTROLS, "live-osd-tv-fr.png") }
    @Test fun capture_tv_en_focusBarre() { render(AppLang.English, false, OsdStage.CONTROLS, "live-osd-tv-en.png") }
    @Test fun capture_tv_fr_focusRecents() { render(AppLang.French, false, OsdStage.RECENTS, "live-osd-tv-fr-recents.png") }
    @Test fun capture_tv_fr_bandeauSeul() { render(AppLang.French, false, OsdStage.CONTROLS, "live-osd-tv-fr-bandeau.png", expanded = false) }

    @Test @Config(qualifiers = "w800dp-h360dp-xxhdpi")
    fun capture_tactile_fr_paysage() { render(AppLang.French, true, OsdStage.CONTROLS, "live-osd-touch-fr.png") }

    @Test fun capture_dimensions() {
        render(AppLang.French, false, OsdStage.CONTROLS, "live-osd-tv-fr.png")
        val bmp = rule.onRoot().captureToImage()
        assertEquals(1920, bmp.width); assertEquals(1080, bmp.height)
        assertTrue(File(outDir, "live-osd-tv-fr.png").length() > 10_000)
    }

    @Test @Config(qualifiers = "w800dp-h360dp-xxhdpi")
    fun capture_tactile_dimensions() {
        render(AppLang.French, true, OsdStage.CONTROLS, "live-osd-touch-fr.png")
        val bmp = rule.onRoot().captureToImage()
        assertEquals(2400, bmp.width); assertEquals(1080, bmp.height)
    }

}
