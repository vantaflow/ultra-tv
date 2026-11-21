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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.ui.catalog.PosterItem
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.TimeFormatPref
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.components.RAIL_COLLAPSED_PX
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.theme.isLightTheme
import com.ultratv.tv.nativeapp.ui.theme.isOledTheme
import org.junit.After
import org.junit.Before
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
// Application neutre : UltraTvApp recopie les réglages (format de l'heure, langue…) dans EpgClock depuis une coroutine, ce qui écrasait
// le format 24 h forcé ici un test sur deux (heure « 8:36 PM » dans les captures) ; ce test n'a pas besoin de l'application.
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi", application = android.app.Application::class)
class HomeRenderTest {
    /** Langue de l'interface du rendu (variantes anglaises pour le README et le site en anglais). */
    private var lang: AppLang = AppLang.French
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    private var savedTimeFormat = ""

    /** Heure en 24 h (état global de l'horloge, qu'un autre test peut avoir laissé en 12 h) : les captures du README montrent « 20:36 ». */
    @Before fun fakeImages() {
        installFakeImageLoader()
        savedTimeFormat = EpgClock.timeFormat
        EpgClock.timeFormat = TimeFormatPref.H24.key
    }

    /** Le thème est un état global : toujours remis au sombre, même si un rendu échoue. */
    @After fun resetTheme() {
        EpgClock.timeFormat = savedTimeFormat
        Ux.themeLight = false; Ux.themeOled = false; Ux.themeKind = AppTheme.DARK; resetImageLoader() }

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
        fun poster(i: Int, title: String) = PosterCardUi(
            key = "t$i", item = PosterItem(id = 100L + i, title = title, poster = null, year = 2020 + i % 5, rating = 6.5 + i % 3 * 0.8),
            isShow = false, kind = com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE,
        )
        val titles = listOf("Orbite basse", "Le Dernier Train", "Marée haute", "Le Phare", "Horizon noir", "Court-circuit", "Les Gardiens", "Nuit blanche", "Sous la glace", "Dernier salon")
        return HomeRows(
            top10 = titles.mapIndexed { i, t -> poster(i + 1, t) },
            channels = live,
            resume = listOf(
                resume(1, "Orbite basse", 0.25f, 95), resume(2, "Les Gardiens du phare · S2 É4", 0.45f, 32), resume(3, "Marée haute", 0.80f, 18),
                resume(4, "Le Dernier Train", 0.45f, 60), resume(5, "Le Phare", 0.70f, 25),
            ),
            shortcuts = shortcutsOf(HomeCounts(channels = 1240, movies = 30435, series = 4028, favorites = 12), emptyList(), null, null),
            // « Nouveaux épisodes » (TVmaze) : séries fictives, un épisode sorti hier, un autre demain.
            newEpisodes = listOf(
                NewEpisodeCardUi(21, "Les Gardiens du phare", null, 2023, 8.1, 2, 7, System.currentTimeMillis() - 86_400_000L, aired = true),
                NewEpisodeCardUi(22, "Marée haute", null, 2024, 7.4, 1, 3, System.currentTimeMillis() + 86_400_000L, aired = false),
            ),
        )
    }

    private fun render(light: Boolean, file: String, sport: Boolean, layout: HomeLayout = HomeLayout.defaultFor(true), focusTag: String = "hero-watch") {
        Ux.themeLight = light
        mountHome(sport, layout)
        focusAndCapture(focusTag, file)
    }

    /** Monte l'accueil TV une fois (un test ne peut appeler `setContent` qu'une fois) ; le thème se change ensuite par les jetons [Ux]. */
    private fun mountHome(sport: Boolean, layout: HomeLayout = HomeLayout.defaultFor(true)) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val data = rows().copy(layout = layout)
        // 20:36 : l'heure de la maquette (salutation du soir, horloge).
        val evening = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 36); set(Calendar.SECOND, 0) }.timeInMillis
        val hero = HeroItem(
            kind = HeroItem.Kind.MOVIE, id = 1, title = "Orbite basse", year = 2024, genre = "Science-fiction", rating = 7.8, backdrop = null, poster = null,
            plot = "Six heures pour ramener tout l'équipage sur Terre.", runtimeMin = 126,
        )
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (lang.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
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
                            heroInList = remember { mutableStateOf(emptySet()) }, onToggleHeroList = {},
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
    }

    private fun focusAndCapture(focusTag: String, file: String) {
        // Le bouton visé porte le focus dans la capture (le focus initial se fait après la première image).
        // Passage par l'autre bouton : le focus initial pris avant le mode clavier n'affiche pas l'anneau.
        rule.onNodeWithTag("hero-info").requestFocus()
        rule.waitForIdle()
        // Une affiche porte son `testTag` sur sa cellule : on focalise la surface cliquable qu'elle contient.
        val target = if (focusTag.startsWith("home-poster-")) rule.onNode(isFocusable() and hasAnyAncestor(hasTestTag(focusTag))) else rule.onNodeWithTag(focusTag)
        target.requestFocus()
        rule.waitForIdle()
        target.assertIsFocused()
        // Garde : une heure en 12 h dans une capture de documentation serait un réglage global écrasé en douce.
        assertTrue("horloge en 24 h", EpgClock.use24())
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

    /** Raccourcis réaffichés par l'utilisateur (masqués par défaut sur TV) : la rangée sous l'image. */
    @Test fun capture_tv_fr_raccourcis() { render(light = false, file = "home-tv-fr-raccourcis.png", sport = true, layout = HomeLayout.defaultFor(true).toggle(HomeBlock.SHORTCUTS)) }

    /** Focus sur « Plus d'infos » dans les deux thèmes : le seul bouton à porter l'anneau doit se lire. */
    @Test fun capture_tv_fr_info_sombre() { render(light = false, file = "home-tv-fr-info.png", sport = false, focusTag = "hero-info") }
    @Test fun capture_tv_fr_info_clair() { render(light = true, file = "home-tv-fr-light-info.png", sport = false, focusTag = "hero-info") }

    /** Focus sur une vignette de la rangée « En direct » dans les deux thèmes : anneau du thème, vignette agrandie. */
    @Test fun capture_tv_fr_direct_focus_sombre() { render(light = false, file = "home-tv-fr-direct-focus.png", sport = false, focusTag = "home-channel-4") }
    @Test fun capture_tv_fr_direct_focus_clair() { render(light = true, file = "home-tv-fr-light-direct-focus.png", sport = false, focusTag = "home-channel-4") }

    @Test fun capture_tv_en_sombre() { lang = AppLang.English; render(light = false, file = "home-tv-en.png", sport = true) }
    @Test fun capture_tv_ar_sombre() { lang = AppLang.Arabic; render(light = false, file = "home-tv-ar.png", sport = true) }

    /** Top 10 : grand chiffre au contour derrière chaque affiche, affiche focalisée à 10 % avec l'anneau du thème. */
    @Test fun capture_tv_fr_top10_focus_sombre() { render(light = false, file = "home-tv-fr-top10-focus.png", sport = false, focusTag = "home-poster-t3") }
    @Test fun capture_tv_fr_top10_focus_clair() { render(light = true, file = "home-tv-fr-light-top10-focus.png", sport = false, focusTag = "home-poster-t3") }
    @Test fun capture_tv_ar_top10_focus() { lang = AppLang.Arabic; render(light = false, file = "home-tv-ar-top10-focus.png", sport = false, focusTag = "home-poster-t3") }

    /**
     * Les dix thèmes (tout thème ajouté plus tard est couvert par `AppTheme.entries`) : l'accueil doit se composer sans erreur
     * dans chacun, avec le focus sur « Regarder », sur une vignette du Direct et sur une affiche. Les captures
     * `home-tv-theme-<thème>-<état>.png` servent à la relecture visuelle. Les thèmes sont appliqués comme le fait l'appli
     * (`ApplyUxTheme` : clair ? OLED ? palette), sur une box (pas de thème système sombre).
     */
    @Test fun capture_tv_tous_les_themes() {
        mountHome(sport = false)
        val etats = listOf("regarder" to "hero-watch", "direct" to "home-channel-4", "affiche" to "home-poster-t3")
        for (theme in AppTheme.entries) {
            rule.runOnUiThread {
                Ux.themeLight = isLightTheme(theme, systemDark = true, isTv = true)
                Ux.themeOled = isOledTheme(theme)
                Ux.themeKind = theme
            }
            rule.waitForIdle()
            // Retour en haut (la capture précédente a fait défiler la liste jusqu'aux affiches) : ▲ jusqu'à revoir « Regarder ».
            var haut = 0
            while (rule.onAllNodes(hasTestTag("hero-info")).fetchSemanticsNodes().isEmpty() && haut++ < 12) {
                rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                rule.waitForIdle()
            }
            for ((etat, tag) in etats) focusAndCapture(tag, "home-tv-theme-${theme.name.lowercase()}-$etat.png")
        }
    }
}
