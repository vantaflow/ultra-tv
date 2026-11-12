package com.ultratv.tv.nativeapp.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.components.RAIL_COLLAPSED_PX
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
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
import java.util.Calendar

/**
 * Rendu de l'accueil TV v2 (maquette Accueil.dc.html) avec des données fictives neutres, écrit en PNG sous
 * `build/outputs/ui-v2/` pour la validation visuelle. Fenêtre 960 × 540 dp à 2,0 (= 1920 × 1080 px, densité normalisée de l'appli).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeRenderTest {
    /** Langue de l'interface du rendu (variantes anglaises pour le README et le site en anglais). */
    private var lang: AppLang = AppLang.French
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    /** Le thème est un état global : toujours remis au sombre, même si un rendu échoue. */
    @After fun resetTheme() { Ux.themeLight = false }

    private fun channel(id: Long, num: Int, name: String) =
        ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = name, logo = null, categoryId = null, streamUrl = "u", num = num)

    private fun rows(): HomeRows {
        val live = listOf(
            ChannelCardUi(channel(1, 1, "Nova 1"), "Esprits criminels", "20:00 – 21:00", 0.60f),
            ChannelCardUi(channel(4, 4, "Écran+"), "Le Grand Journal", "20:15 – 21:00", 0.45f),
            ChannelCardUi(channel(12, 12, "Stade 1"), "Ligue 1 · PSG – OM", "20:45 – 22:45", 0.20f),
            ChannelCardUi(channel(7, 7, "Horizon"), "Court-circuit", "20:30 – 21:30", 0.10f),
            ChannelCardUi(channel(101, 101, "Le Fil Info"), "Journal de 20h", "20:00 – 21:30", 0.40f),
        )
        fun resume(i: Int, title: String, p: Float, left: Int) = ResumeCardUi(
            key = "r$i", entry = WatchHistoryEntity(providerId = 1, kind = "MOVIE", remoteId = "$i", title = title, poster = null, streamUrl = "u"),
            title = title, progress = p, minutesLeft = left,
        )
        return HomeRows(
            channels = live,
            resume = listOf(
                resume(1, "Orbite basse", 0.25f, 95), resume(2, "Les Gardiens du phare · S2 É4", 0.45f, 32), resume(3, "Marée haute", 0.80f, 18),
                resume(4, "Le Dernier Train", 0.45f, 60), resume(5, "Le Phare", 0.70f, 25),
            ),
            shortcuts = shortcutsOf(HomeCounts(channels = 1240, movies = 30435, series = 4028, favorites = 12), emptyList(), null, null),
        )
    }

    private fun render(light: Boolean, file: String, sport: Boolean) {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.themeLight = light
        val data = rows()
        // 20:36 : l'heure de la maquette (salutation du soir, horloge).
        val evening = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 36); set(Calendar.SECOND, 0) }.timeInMillis
        val hero = HeroItem(
            kind = HeroItem.Kind.MOVIE, id = 1, title = "Orbite basse", year = 2024, genre = "Science-fiction", rating = 7.8, backdrop = null, poster = null,
            plot = "Six heures pour ramener tout l'équipage sur Terre.", runtimeMin = 126,
        )
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false,
            ) {
                // Mode clavier (télécommande) : sans lui, les éléments cliquables ne prennent pas le focus.
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                val clock = remember { mutableLongStateOf(evening) }
                val extras = remember { mutableStateOf(emptyMap<String, HeroExtras>()) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    // Rail replié simplifié : seule sa largeur et sa couleur comptent pour juger la mise en page.
                    Box(Modifier.fillMaxHeight().width(RAIL_COLLAPSED_PX.design).background(Ux.Rail))
                    Box(Modifier.padding(start = RAIL_COLLAPSED_PX.design)) {
                        HomeTvContent(
                            slot = HomeTopSlot.HERO, hero = hero, heroIndex = 0, heroCount = 1, heroExtras = extras,
                            rows = data, providerCount = 3, clock = clock, sportOn = sport, sportLive = if (sport) 4 else null,
                            heroFocusDone = false, onHeroFocused = {},
                            playHistory = {}, openPoster = {}, playChannel = {}, onShortcut = {}, onGoSport = {},
                            onWatchHero = {}, onInfoHero = {},
                            emptyTop = {}, sportRow = { _, _ -> },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Accueil capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
    }

    @Test fun capture_tv_fr_sombre() { render(light = false, file = "home-tv-fr.png", sport = true) }

    @Test fun capture_tv_fr_clair() { render(light = true, file = "home-tv-fr-light.png", sport = false) }

    @Test fun capture_dimensions() {
        render(light = false, file = "home-tv-fr.png", sport = true)
        val bmp = rule.onRoot().captureToImage()
        assertEquals(1920, bmp.width); assertEquals(1080, bmp.height)
        assertTrue(File(outDir, "home-tv-fr.png").length() > 10_000)
    }

    @Test fun capture_tv_en_sombre() { lang = AppLang.English; render(light = false, file = "home-tv-en.png", sport = true) }
}
