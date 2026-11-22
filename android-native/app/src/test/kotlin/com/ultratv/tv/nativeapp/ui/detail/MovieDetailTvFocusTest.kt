package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.design.VodCard
import com.ultratv.tv.nativeapp.ui.trailer.TrailerPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Filet de non-régression de la fiche film à la télécommande (TV 1920 × 1080) : focus initial, cheminement ▲ ▼, boutons,
 * bande-annonce. `MovieDetailTv` est rendu comme le fait `MovieDetailScreen` (même focus initial, même surcouche de
 * bande-annonce), sans ViewModel.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class MovieDetailTvFocusTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() = resetDetailHost()

    private fun press(k: Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
    }

    /** Fiche film posée comme dans `MovieDetailScreen` : focus initial sur le principal, titres proches en dessous. */
    private fun show(
        m: MovieDetailTvModel, similar: List<Long> = emptyList(), onPrimary: () -> Unit = {}, onFromStart: () -> Unit = {},
        onTrailer: (() -> Unit)? = {}, onBack: () -> Unit = {},
    ) {
        rule.setDetail(tv = true, width = 1920, height = 1080) {
            val req = remember { FocusRequester() }
            var focused by remember { mutableStateOf(false) }
            RequestInitialFocus(req, hasFocus = { focused }, key = m.title)
            MovieDetailTv(
                m = m, onPrimary = onPrimary, onFromStart = onFromStart, onTrailer = onTrailer, onBack = onBack, onOpenPerson = {},
                playRequester = req, onPlayFocusChanged = { focused = it },
                favorite = { V2IconButton("M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z", "Favori", {}) },
                below = if (similar.isEmpty()) null else ({
                    Row {
                        for (id in similar) VodCard("Similaire $id", null, Modifier.width(85.dp).testTag("dt-similar-$id")) {}
                    }
                }),
            )
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
    }

    @Test fun filmTv_focusInitialSurLePrincipal_filmCommence() {
        show(DetailFixtures.movie(resumeAtMs = 1_872_000))
        rule.onNodeWithTag("dt-primary").assertIsFocused()
        rule.onNode(hasTestTag("dt-primary") and hasText("Reprendre à 31:12", substring = true)).assertExists()
        rule.onNodeWithTag("dt-from-start").assertExists()
    }

    @Test fun filmTv_focusInitialSurLePrincipal_filmPasCommence() {
        show(DetailFixtures.movie())
        rule.onNodeWithTag("dt-primary").assertIsFocused()
        rule.onNode(hasTestTag("dt-primary") and hasText("Regarder", substring = true)).assertExists()
        // Pas commencé : aucun second bouton « Depuis le début ».
        assertTrue(rule.onAllNodes(hasTestTag("dt-from-start")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun filmTv_hautDepuisLesSimilaires_vaSurLaDistribution() {
        show(DetailFixtures.movie(), similar = listOf(1L, 2L))
        // ▼ jusqu'aux titres proches (au plus 12 pressions : boutons, distribution, rangée).
        var n = 0
        while (n++ < 12 && runCatching { rule.onNodeWithTag("dt-similar-1").assertIsFocused() }.isFailure) press(Key.DirectionDown)
        rule.onNodeWithTag("dt-similar-1").assertIsFocused()
        press(Key.DirectionUp)
        // La distribution, juste au-dessus : jamais « Lecture » (l'en-tête, groupe de focus, y renvoyait avant le correctif).
        rule.onNodeWithTag("dt-primary").assertIsNotFocused()
        val tag = rule.onNode(androidx.compose.ui.test.isFocused()).fetchSemanticsNode().config
            .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
        assertTrue("un avatar de la distribution doit porter le focus, trouvé : $tag", tag != null && tag.startsWith("dt-cast-"))
    }

    @Test fun filmTv_principalEtDepuisLeDebut_declenchentLeursActions() {
        // Le choix « principal = reprise (vm.play), depuis le début = restart » est fait par l'écran (lambda branchée sur le
        // ViewModel) : ici on garde le câblage des deux boutons de la fiche.
        var primary = 0; var fromStart = 0
        show(DetailFixtures.movie(resumeAtMs = 1_872_000), onPrimary = { primary++ }, onFromStart = { fromStart++ })
        rule.onNodeWithTag("dt-primary").performClick()
        assertEquals(1 to 0, primary to fromStart)
        rule.onNodeWithTag("dt-from-start").performClick()
        assertEquals(1 to 1, primary to fromStart)
    }

    @Test fun bandeAnnonce_retourFermeSeulement_film() {
        // Comme MovieDetailScreen : la surcouche de bande-annonce est posée par-dessus la fiche.
        rule.setDetail(tv = true, width = 1920, height = 1080) {
            val req = remember { FocusRequester() }
            var focused by remember { mutableStateOf(false) }
            var open by remember { mutableStateOf(false) }
            RequestInitialFocus(req, hasFocus = { focused })
            Box {
                MovieDetailTv(
                    m = DetailFixtures.movie(), onPrimary = {}, onFromStart = {}, onTrailer = { open = true }, onBack = {}, onOpenPerson = {},
                    playRequester = req, onPlayFocusChanged = { focused = it }, favorite = {},
                )
                if (open) TrailerPlayer("dQw4w9WgXcQ", "Orbite basse", onClose = { open = false })
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.onNodeWithTag("dt-trailer").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        // Bande-annonce ouverte : le focus est piégé dedans, pas sur la fiche.
        rule.onNodeWithTag("dt-trailer").assertIsNotFocused()
        press(Key.Back)
        // Retour a fermé la bande-annonce SEULEMENT : la fiche est toujours là et a repris le focus.
        rule.onNodeWithTag("dt-primary").assertExists()
        rule.onNodeWithTag("dt-trailer").assertExists()
        assertTrue("la fiche doit reprendre le focus", rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty())
        // Un second Retour n'est plus à la charge de la bande-annonce : elle ne revient pas toute seule.
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        rule.onNodeWithTag("dt-trailer").assertExists()
    }

    @Test fun bandeAnnonce_retourFermeSeulement_serie() {
        rule.setDetail(tv = true, width = 1920, height = 1080) {
            SeriesDetailTvContent(
                data = DetailFixtures.series(2, 4, activeSeason = 1), onSeason = {}, onPlayTarget = {}, onPlayEpisode = {}, onOpenPerson = {}, onBack = {},
                onLongPressEpisode = {}, downloadLabel = "Télécharger la saison", onDownloadSeason = {}, favorite = {}, trakt = {},
            )
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        rule.onNodeWithTag("dt-trailer").performClick()
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
        rule.onNodeWithTag("dt-trailer").assertIsNotFocused()
        press(Key.Back)
        rule.onNodeWithTag("dt-trailer").assertExists()
        rule.onNodeWithTag("dt-episode-1-1").assertExists()
        assertTrue(rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isNotEmpty())
    }
}
