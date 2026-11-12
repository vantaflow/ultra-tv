package com.ultratv.tv.nativeapp.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
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
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.i18n.stringsOf
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
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
 * Rendu de la surcouche film / épisode (nouvelle interface) avec des données fictives neutres, écrit en PNG sous
 * `build/outputs/ui-v2/` (validation visuelle). Fenêtre 960 × 540 dp à 2,0 (= 1920 × 1080 px, densité normalisée de l'appli).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class VodOverlayRenderTest {
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    private fun remoteFocus(target: FocusRequester) {
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        rule.runOnIdle { target.requestFocus() }
        rule.waitForIdle()
    }

    private fun save(file: String): Bitmap {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("VOD capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
        return bmp
    }

    private fun render(episode: Boolean, file: String, steps: Int = 0) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = AppLang.French
        val labels = vodLabels(PlayerExtraStrings(lang), stringsOf(lang), DesignStrings(lang))
        val pauseFocus = FocusRequester()
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                // Faux plan vidéo : dégradé neutre, pour juger la lisibilité de la surcouche.
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1E2A38), Color(0xFF2E1E26), Color(0xFF1B2735))))) {
                    val pos = if (episode) 3_046_000L else 1_872_000L
                    val dur = if (episode) 3_240_000L else 7_560_000L
                    VodOverlay(
                        title = if (episode) "La lanterne" else "Orbite basse",
                        kicker = if (episode) "LES GARDIENS DU PHARE · S02E04" else "FILM",
                        episode = episode,
                        badges = if (episode) TechBadges("FHD", null, "2.0") else TechBadges("4K", "HEVC", "5.1", "HDR10"),
                        pos = { pos }, dur = { dur }, buffered = { if (episode) 3_100_000L else 2_900_000L }, speed = 1f,
                        marks = if (episode) listOf(0.03f to 0.07f, 0.94f to 1f) else emptyList(),
                        playing = true, loading = false, hasPrev = episode, hasNext = episode, sleepActive = false,
                        pauseFocus = pauseFocus, labels = remember { labels },
                        onPrev = {}, onSeek = {}, onToggle = {}, onNext = {}, onSubs = {}, onSleep = {}, onOptions = {}, onAudio = {},
                    )
                    if (episode) NextEpisodeCard(
                        tag = "É5", label = "ÉPISODE SUIVANT · 12 s", title = "5. Marée basse", summary = "Un bateau s'échoue sans équipage", image = null, fraction = 0.2f,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 72.design, bottom = 330.design),
                    )
                }
            }
        }
        rule.waitForIdle()
        remoteFocus(pauseFocus)
        // Série : le focus de la maquette est sur « Épisode suivant » (Pause, −10 s, +10 s, Précédent, Suivant).
        repeat(steps) { rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }; rule.waitForIdle() }
        save(file)
    }

    @Test fun capture_movie_player_tv_fr() {
        render(episode = false, file = "movie-player-tv-fr.png")
        rule.onNodeWithText("Pause").assertExists()
    }

    @Test fun capture_series_player_tv_fr() {
        render(episode = true, file = "series-player-tv-fr.png", steps = 4)
    }

    @Test fun capture_player_dimensions() {
        render(episode = false, file = "movie-player-tv-fr.png")
        val bmp = rule.onRoot().captureToImage()
        assertEquals(1920, bmp.width); assertEquals(1080, bmp.height)
        assertTrue(File(outDir, "movie-player-tv-fr.png").length() > 10_000)
    }
}
