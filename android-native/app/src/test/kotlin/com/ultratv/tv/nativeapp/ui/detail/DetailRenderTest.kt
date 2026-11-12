package com.ultratv.tv.nativeapp.ui.detail

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.design.Ux
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

/**
 * Rendu des fiches film et série (interface v2) avec des données fictives neutres, écrit en PNG sous `build/outputs/ui-v2/`.
 * Fenêtre TV : 960 × 540 dp à 2,0 (= 1920 × 1080 px, la densité normalisée de l'appli).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DetailRenderTest {
    /** Langue de l'interface du rendu (variantes anglaises pour le README et le site en anglais). */
    private var lang: AppLang = AppLang.French
    private fun tr(fr: String, en: String) = if (lang == AppLang.English) en else fr
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    @After fun resetTheme() { Ux.themeLight = false }

    private fun capture(file: String, light: Boolean, content: @androidx.compose.runtime.Composable () -> Unit): Int {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.themeLight = light
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang)) {
                // Mode clavier (télécommande) : sans lui, les éléments cliquables ne prennent pas le focus.
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                content()
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Fiche capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
        return bmp.width
    }

    private fun movieModel() = MovieDetailTvModel(
        title = "Orbite basse", backdrop = null, poster = null,
        chips = listOf("2024", "Science-fiction", "Thriller", "4K", "VF"),
        ratings = listOf(RatingCell("7,6", "IMDb"), RatingCell("88 %", "Rotten Tomatoes"), RatingCell("2 h 06", tr("Durée", "Runtime"))),
        synopsis = "Une ingénieure coincée dans une station orbitale en perdition a six heures pour ramener son équipage sur Terre, alors que les communications avec le sol se coupent une à une.",
        skeleton = false, primaryLabel = tr("Reprendre à 31:12", "Resume at 31:12"), fromStartLabel = tr("Depuis le début", "From the start"), trailerLabel = tr("Bande-annonce", "Trailer"),
        progress = 0.25f, progressLine = tr("Reprendre à 31:12 · 94 min restantes", "Resume at 31:12 · 94 min left"),
        cast = listOf(
            CastUi("Nora Valin", "Elsa", null), CastUi("Tom Reyes", "Malik", null), CastUi("Ana Kiel", null, null),
            CastUi("Jo Bram", null, null), CastUi("Lou Marez", null, null), CastUi("Sam Doré", tr("Réalisation", "Director"), null),
        ),
        castTitle = tr("Distribution", "Cast"), backDescription = tr("Retour", "Back"),
    )

    private fun renderMovie(file: String, light: Boolean): Int = capture(file, light) {
        val req = remember { FocusRequester() }
        MovieDetailTv(
            m = movieModel(), onPrimary = {}, onFromStart = {}, onTrailer = {}, onBack = {}, onOpenPerson = {},
            playRequester = req, onPlayFocusChanged = {}, favorite = { V2IconButton("M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z", "Favori", {}) },
        )
    }

    private fun seriesData(): SeriesDetailData {
        val series = SeriesEntity(
            providerId = 1, remoteId = "s1", name = "Les Gardiens du phare", poster = null, categoryId = null, year = 2023, rating = 8.1,
            plot = "Sur une île coupée du monde, trois gardiens découvrent que le phare guide autre chose que des bateaux.",
            genre = "Drame", cast = "Mara Aubin, Yann Kerel, Léa Tanguy, Paul Guen, Sofia Ruiz, Noé Bastin",
        )
        val titles = listOf("Le signal", "Brouillard", "La lanterne", "Marée basse", "Le registre")
        val eps = titles.mapIndexed { i, t ->
            EpisodeEntity(
                seriesId = 1, remoteId = "e${i + 2}", season = 2, episode = i + 2, title = t, streamUrl = "", container = null,
                plot = "Une lumière répond à celle du phare, et personne ne sait d'où elle vient.", duration = "49",
            )
        } + (1..3).map { n -> EpisodeEntity(seriesId = 1, remoteId = "a$n", season = 1, episode = n, title = "Pilote $n", streamUrl = "", container = null, plot = null) } +
            (1..3).map { n -> EpisodeEntity(seriesId = 1, remoteId = "c$n", season = 3, episode = n, title = "Final $n", streamUrl = "", container = null, plot = null) }
        val progress = mapOf(
            "e2" to WatchHistoryEntity(providerId = 1, kind = "EPISODE", remoteId = "e2", title = "Le signal", poster = null, streamUrl = "", positionMs = 2_940_000, durationMs = 2_940_000),
            "e3" to WatchHistoryEntity(providerId = 1, kind = "EPISODE", remoteId = "e3", title = "Brouillard", poster = null, streamUrl = "", positionMs = 3_060_000, durationMs = 3_060_000),
            "e4" to WatchHistoryEntity(providerId = 1, kind = "EPISODE", remoteId = "e4", title = "La lanterne", poster = null, streamUrl = "", positionMs = 1_300_000, durationMs = 2_900_000),
        )
        return SeriesDetailData(
            series = series, episodes = eps, seasons = listOf(1, 2, 3), activeSeason = 2, target = eps.first { it.remoteId == "e4" }, resuming = true,
            progress = progress, traktSeen = emptySet(), ratings = null, trailerUrl = "dQw4w9WgXcQ", downloads = emptyMap(), loading = false,
            // Ligne TVmaze (fictive) ; logo Fanart absent : le titre texte reste affiché.
            episodeLine = tr("Prochain épisode : S2 É7 · La relève · Demain", "Next episode: S2 E7 · The relief · Tomorrow"),
        )
    }

    private fun renderSeries(file: String, light: Boolean): Int = capture(file, light) {
        SeriesDetailTvContent(
            data = seriesData(), onSeason = {}, onPlayTarget = {}, onPlayEpisode = {}, onOpenPerson = {}, onBack = {},
            onLongPressEpisode = {}, downloadLabel = tr("Télécharger la saison", "Download season"), onDownloadSeason = {},
            favorite = { V2IconButton("M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z", "Favori", {}) }, trakt = {},
        )
    }

    @Test fun capture_film_tv_sombre() {
        assertEquals(1920, renderMovie("movie-detail-tv-dark.png", light = false))
        assertTrue(File(outDir, "movie-detail-tv-dark.png").length() > 10_000)
    }

    @Test fun capture_film_tv_clair() {
        assertEquals(1920, renderMovie("movie-detail-tv-light.png", light = true))
    }

    @Test fun capture_serie_tv_sombre() {
        assertEquals(1920, renderSeries("series-detail-tv-dark.png", light = false))
        assertTrue(File(outDir, "series-detail-tv-dark.png").length() > 10_000)
    }

    @Test fun logoIntrouvable_titreTexteConserve() {
        // Logo Fanart illisible (fichier absent) : le titre texte reste affiché, sans plantage.
        capture("movie-detail-tv-logo-fallback.png", light = false) {
            val req = remember { FocusRequester() }
            MovieDetailTv(
                m = MovieDetailTvModel(
                    title = "Orbite basse", backdrop = null, poster = null, chips = emptyList(), ratings = emptyList(), synopsis = null,
                    skeleton = false, primaryLabel = "Lecture", fromStartLabel = null, trailerLabel = "Bande-annonce", progress = 0f, progressLine = null,
                    cast = emptyList(), castTitle = "Distribution", backDescription = "Retour", logo = "file:///introuvable/logo.png",
                ),
                onPrimary = {}, onFromStart = {}, onTrailer = null, onBack = {}, onOpenPerson = {},
                playRequester = req, onPlayFocusChanged = {}, favorite = {},
            )
        }
        assertTrue(rule.onAllNodesWithText("Orbite basse").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun capture_film_tv_en() { lang = AppLang.English; renderMovie("movie-detail-tv-en.png", light = false) }
    @Test fun capture_serie_tv_en() { lang = AppLang.English; renderSeries("series-detail-tv-en.png", light = false) }
}
