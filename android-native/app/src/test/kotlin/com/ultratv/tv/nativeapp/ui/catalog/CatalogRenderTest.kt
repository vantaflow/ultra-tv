package com.ultratv.tv.nativeapp.ui.catalog

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Ux
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
 * Rendu du catalogue Films / Séries (disposition TV v2) avec des données fictives, écrit en PNG sous `build/outputs/ui-v2/`.
 * Fenêtre 960 x 540 dp à 2,0 (= 1920 x 1080 px).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class CatalogRenderTest {
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    private fun posters(prefix: String, n: Int) = (1..n).map { i ->
        PosterItem(
            id = (prefix.hashCode() + i).toLong(), title = "$prefix $i", poster = null, year = 2015 + i % 10, rating = 6.0 + (i % 4) * 0.7,
            lang = if (i % 3 == 0) "FR" else "", watched = i == 4, progress = if (i == 2) 0.25f else if (i == 3) 0.6f else 0f,
        )
    }

    private fun render(lang: AppLang, light: Boolean, series: Boolean, file: String) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val chips = listOf(CategoryChip("action", "Action", 120), CategoryChip("comedie", "Comédie", 90), CategoryChip("familial", "Familial", 60))
        val data = chips.associate { it.remoteId to posters(if (series) "Série ${it.name}" else "Film ${it.name}", 8) }
        val focus = FocusRequester()
        val previous = Ux.themeLight
        Ux.themeLight = light
        try {
            rule.setContent {
                CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false) {
                    val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                    androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                    val D = LocalDs.current
                    Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                        val hero = remember { heroOf(data.getValue("action").first()).copy(synopsis = "Une ingénieure coincée dans une station en perdition a six heures pour ramener son équipage sur Terre.") }
                        CatalogRowsTv(
                            tabs = listOf(CatalogTabUi(null, D.allChip)) + chips.map { CatalogTabUi(it.remoteId, it.name) },
                            selectedKey = null, onTab = {}, filters = {},
                            hero = hero, featuredLabel = D.featured, watchLabel = D.watch, onWatch = {},
                            heroExtra = { Box(Modifier.size(30.dp).clip(CircleShape).background(Ux.Surface), contentAlignment = Alignment.Center) { DIcon(Icons.Heart, 12.dp, Ux.Text) } },
                            chips = chips, labelOf = { it.name }, rowItems = { id -> data[id] }, seeAllLabel = D.seeAll,
                            onOpen = {}, onSeeAll = {}, firstPoster = focus, onFirstPosterFocus = {},
                        )
                    }
                }
            }
            rule.waitForIdle()
            // Mode clavier (télécommande), puis focus sur la première affiche comme à l'ouverture de l'écran.
            rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
            rule.waitForIdle()
            rule.runOnIdle { focus.requestFocus() }
            rule.waitForIdle()
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("Catalogue capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
        } finally {
            Ux.themeLight = previous
        }
    }

    @Test fun capture_movies_tv_fr_dark() { render(AppLang.French, light = false, series = false, file = "movies-tv-fr-dark.png") }
    @Test fun capture_movies_tv_fr_light() { render(AppLang.French, light = true, series = false, file = "movies-tv-fr-light.png") }
    @Test fun capture_series_tv_fr_dark() { render(AppLang.French, light = false, series = true, file = "series-tv-fr-dark.png") }

    @Test fun capture_dimensions() {
        render(AppLang.French, light = false, series = false, file = "movies-tv-fr-dark.png")
        val bmp = rule.onRoot().captureToImage()
        assertEquals(1920, bmp.width); assertEquals(1080, bmp.height)
        assertTrue(File(outDir, "movies-tv-fr-dark.png").length() > 10_000)
    }
}
