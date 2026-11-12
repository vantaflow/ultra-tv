package com.ultratv.tv.nativeapp.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES
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
 * Rendu de la liste des chaînes du lecteur (feuille flottante, onglets Chaînes / Favoris, catégories, chaînes), données
 * fictives neutres, écrit en PNG sous `build/outputs/drawer/` (validation visuelle).
 *  - TV : fenêtre 960 × 540 dp à 2,0 (= 1920 × 1080 px) ; tactile paysage : 800 × 360 dp à 3,0 (= 2400 × 1080 px).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LiveDrawerRenderTest {
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/drawer").also { it.mkdirs() }

    private val cats = listOf(
        DrawerCategory(CATEGORY_ALL, "Toutes les chaînes", 1240),
        DrawerCategory(CATEGORY_FAVORITES, "Favoris", 12),
        DrawerCategory("news", "Actualités", 86),
        DrawerCategory("sport", "Sport", 143),
        DrawerCategory("cine", "Cinéma", 61),
        DrawerCategory("kids", "Jeunesse", 38),
        DrawerCategory("docs", "Documentaires", 52),
        DrawerCategory("music", "Musique", 24),
    )

    private val rows = listOf(
        DrawerRowUi(1, 1, null, "France Info", "Journal de 20h · 20:00", 0.40f, false),
        DrawerRowUi(2, 2, null, "Chaîne Info", "Le grand débat · 20:00", 0.62f, true),
        DrawerRowUi(3, 3, null, "Info 24", "Édition spéciale · 19:45", 0.75f, false),
        DrawerRowUi(4, 4, null, "Actu Plus", "Reportage · 20:15", 0.30f, false),
        DrawerRowUi(5, 5, null, "Monde TV", "Magazine · 19:30", 0.85f, false),
        DrawerRowUi(6, 6, null, "Journal TV", "Les titres · 20:30", 0.10f, false),
        DrawerRowUi(7, 7, null, "Eco News", "Bourse · 20:00", 0.55f, false),
    )

    private fun render(lang: AppLang, touch: Boolean, favorites: Boolean, file: String) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val catFocus = FocusRequester()
        val shownRows = if (favorites) rows.take(3) else rows
        val highlight = if (favorites) CATEGORY_FAVORITES else "news"
        rule.setContent {
            // designStringsFor est composable : à appeler dans la composition.
            val D = designStringsFor(lang)
            CompositionLocalProvider(LocalDs provides D, LocalStrings provides stringsFor(lang), LocalTouch provides touch) {
                // Mode clavier (télécommande) : sans lui, les éléments cliquables ne prennent pas le focus.
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                // Faux plan vidéo : dégradé neutre, pour juger la lisibilité de la feuille.
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2B5876), Color(0xFF4E4376), Color(0xFF1B2735))))) {
                    DrawerSheet(
                        title = D.pChannels, channelsTab = D.pChannels, favoritesTab = D.catFavorites, favoritesSelected = favorites, onTab = {},
                        cats = cats, highlightId = highlight, rtl = false, catFocus = catFocus,
                        onCatFocused = {}, onCatEnter = {},
                        rows = shownRows, listState = rememberLazyListState(), rowModifier = { Modifier },
                        onRowFocus = {}, onRowClick = {}, onAirLabel = D.onAirPill, emptyText = null,
                    )
                }
            }
        }
        rule.waitForIdle()
        if (!touch) {
            // Focus sur la catégorie choisie, puis Bas : la catégorie suivante prend le focus (couleurs de focus).
            rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
            rule.waitForIdle()
            rule.runOnIdle { catFocus.requestFocus() }
            rule.waitForIdle()
            rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            rule.waitForIdle()
        }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Drawer capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
    }

    @Test fun capture_tv_fr_chaines() { render(AppLang.French, false, false, "live-drawer-tv-fr.png") }
    @Test fun capture_tv_fr_onglet_favoris() { render(AppLang.French, false, true, "live-drawer-tv-fr-favoris.png") }
    @Test fun capture_tv_en_chaines() { render(AppLang.English, false, false, "live-drawer-tv-en.png") }

    @Test @Config(qualifiers = "w800dp-h360dp-xxhdpi")
    fun capture_tactile_fr_paysage() { render(AppLang.French, true, false, "live-drawer-touch-fr.png") }

    @Test fun capture_dimensions() {
        render(AppLang.French, false, false, "live-drawer-tv-fr.png")
        val bmp = rule.onRoot().captureToImage()
        assertEquals(1920, bmp.width); assertEquals(1080, bmp.height)
        assertTrue(File(outDir, "live-drawer-tv-fr.png").length() > 10_000)
    }
}
