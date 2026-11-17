package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Navigation à la télécommande de la fiche série TV : lignes de la colonne gauche, entrée dans les épisodes, retour. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SeriesDetailFocusTest {
    @get:Rule val rule = createComposeRule()

    private fun data(withTarget: Boolean): SeriesDetailData {
        val series = SeriesEntity(
            providerId = 1, remoteId = "s1", name = "Les Gardiens du phare", poster = null, categoryId = null, year = 2023, rating = 8.1,
            plot = "Un phare.", genre = "Drame", cast = "Mara Aubin, Yann Kerel, Léa Tanguy",
        )
        val eps = (1..6).map { n -> EpisodeEntity(seriesId = 1, remoteId = "e$n", season = 1, episode = n, title = "Titre$n", streamUrl = "", container = null, plot = null) }
        val prog = WatchHistoryEntity(providerId = 1, kind = "EPISODE", remoteId = "e4", title = "Titre4", poster = null, streamUrl = "", positionMs = 1_000_000, durationMs = 2_900_000)
        return SeriesDetailData(
            series = series, episodes = eps, seasons = listOf(1), activeSeason = 1,
            target = if (withTarget) eps[3] else null, resuming = withTarget,
            progress = mapOf("e4" to prog), traktSeen = emptySet(), ratings = null, trailerUrl = "dQw4w9WgXcQ", downloads = emptyMap(), loading = false,
        )
    }

    private fun show(withTarget: Boolean) {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French)) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                SeriesDetailTvContent(
                    data = data(withTarget), onSeason = {}, onPlayTarget = {}, onPlayEpisode = {}, onOpenPerson = {}, onBack = {},
                    onLongPressEpisode = {}, downloadLabel = "Télécharger", onDownloadSeason = {},
                    favorite = { V2IconButton("M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z", "Favori", {}) }, trakt = {},
                )
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
    }

    private fun press(k: Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
    }

    /** L'élément focalisé contient le texte (ou la description) donné : comparaison des cadres, le focus portant sur la surface. */
    private fun focused(text: String) {
        val f = rule.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val hits = rule.onAllNodes(hasText(text, substring = true, ignoreCase = true) or hasContentDescription(text, substring = true, ignoreCase = true))
            .fetchSemanticsNodes().map { it.boundsInRoot }
        val ok = hits.any { b -> b.left >= f.left - 1 && b.right <= f.right + 1 && b.top >= f.top - 1 && b.bottom <= f.bottom + 1 }
        check(ok) { "Focus attendu sur « $text » ; focus en $f, textes en $hits" }
    }

    @Test fun droiteDepuisReprendreVaALEpisodeAReprendre() {
        show(true)
        focused("Reprendre")
        press(Key.DirectionRight)
        focused("Titre4")
    }

    @Test fun gaucheDepuisUnEpisodeRevientSurReprendre() {
        show(true)
        press(Key.DirectionRight); focused("Titre4")
        press(Key.DirectionDown); focused("Titre5")
        press(Key.DirectionLeft)
        focused("Reprendre")
    }

    @Test fun ligneBoutonsGardeDroiteDansLaLignePuisEntreDansLesEpisodes() {
        show(true)
        press(Key.DirectionDown)
        focused("bande")           // bande-annonce
        press(Key.DirectionRight)
        focused("Favori")
        press(Key.DirectionRight)  // dernier bouton : jamais l'acteur du dessous
        focused("Titre4")
    }

    @Test fun distributionEtDerniereEntreeVersLEpisode() {
        show(true)
        press(Key.DirectionDown); press(Key.DirectionDown)
        focused("Mara")
        press(Key.DirectionRight); focused("Yann")
        press(Key.DirectionRight); focused("Léa")
        press(Key.DirectionRight)
        focused("Titre4")
    }

    @Test fun sansReprendreEntreSurLePremierEpisodeEtGaucheSurLePremierBouton() {
        show(false)
        // Premier focus : bande-annonce (premier bouton).
        focused("bande")
        press(Key.DirectionRight); focused("Favori")
        press(Key.DirectionRight); focused("Titre1")
        press(Key.DirectionLeft)
        focused("bande")
    }
}
