package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.ui.common.LocalOverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayLayer
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
    @get:Rule val rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, androidx.activity.ComponentActivity> = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @After fun reset() = resetDetailHost()

    private fun press(k: Key) {
        rule.onRoot().performKeyInput { pressKey(k) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
    }

    /** Comme à la télécommande : focus sur « Bande-annonce » puis OK (l'élément activé se signale comme ouvreur). */
    private fun openTrailerWithOk() {
        rule.onNodeWithTag("dt-trailer").requestFocus()
        rule.waitForIdle()
        press(Key.Enter)
        rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    /** Touche Retour : l'appli la reçoit par `BackHandler` (OnBackPressedDispatcher), pas comme un événement clavier. */
    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(600); rule.waitForIdle()
    }

    private fun focusedTag(): String? = rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)

    /** Fiche film posée comme dans `MovieDetailScreen` : focus initial sur le principal, titres proches en dessous. */
    private fun show(
        m: MovieDetailTvModel, similar: List<Long> = emptyList(), onPrimary: () -> Unit = {}, onFromStart: () -> Unit = {},
        onTrailer: (() -> Unit)? = {}, onBack: () -> Unit = {}, similarOffsetDp: Int = 0,
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
                    Row(Modifier.padding(start = similarOffsetDp.dp)) {
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

    private fun hautDepuisLesSimilaires(offsetDp: Int) {
        show(DetailFixtures.movie(), similar = listOf(1L, 2L), similarOffsetDp = offsetDp)
        // ▼ jusqu'aux titres proches (au plus 12 pressions : boutons, distribution, rangée).
        var n = 0
        while (n++ < 12 && focusedTag() != "dt-similar-1") press(Key.DirectionDown)
        assertEquals("dt-similar-1", focusedTag())
        press(Key.DirectionUp)
        // La distribution, juste au-dessus : jamais « Lecture » (l'en-tête, groupe de focus, y renvoyait avant le correctif).
        val tag = focusedTag()
        assertTrue("un avatar de la distribution doit porter le focus, trouvé : $tag", tag != null && tag.startsWith("dt-cast-"))
    }

    @Test fun filmTv_hautDepuisLesSimilaires_vaSurLaDistribution() = hautDepuisLesSimilaires(0)

    /**
     * Rangée décalée loin à droite (sous aucun avatar). NON GARDÉ : ni cette fixture ni la précédente ne reproduisent le défaut
     * d'origine ; supprimer `up = castReq` de `MovieDetailTv` laisse les DEUX tests verts (la recherche géométrique mène déjà
     * à la distribution). Ils figent le comportement observable (▲ -> distribution, jamais « Lecture »), pas la règle ; le cas
     * fautif (écran réel complet, hauteur de fiche défilée) reste à vérifier sur appareil (tâche 9).
     */
    @Test fun filmTv_hautDepuisLesSimilairesDecalees_vaSurLaDistribution() = hautDepuisLesSimilaires(700)

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

    /**
     * Bande-annonce ouverte : Retour la ferme SEULE (la fiche reste, `onBack` n'est pas appelé) et le focus revient sur le
     * bouton qui l'a ouverte. Ici la surcouche `TrailerPlayer` est recomposée par le test, comme `MovieDetailScreen` le fait
     * (la vraie surcouche vit dans l'écran, qui exige un ViewModel) ; la version série ci-dessous utilise le vrai contenu.
     * Retour sur la fiche seule n'appelle pas `onBack` : c'est la navigation qui le gère (RailBack), pas la fiche.
     */
    @Test fun bandeAnnonce_retourFermeSeulement_film() {
        var backs = 0
        rule.setDetail(tv = true, width = 1920, height = 1080) {
            val req = remember { FocusRequester() }
            var focused by remember { mutableStateOf(false) }
            var open by remember { mutableStateOf(false) }
            RequestInitialFocus(req, hasFocus = { focused })
            val overlays = remember { OverlayHost() }
            // Comme MainActivity : surcouches rendues par l'OverlayHost, qui rend le focus à l'ouvreur à la fermeture.
            androidx.compose.runtime.CompositionLocalProvider(LocalOverlayHost provides overlays) { Box {
                MovieDetailTv(
                    m = DetailFixtures.movie(), onPrimary = {}, onFromStart = {}, onTrailer = { open = true }, onBack = { backs++ }, onOpenPerson = {},
                    playRequester = req, onPlayFocusChanged = { focused = it }, favorite = {},
                )
                if (open) TrailerPlayer("dQw4w9WgXcQ", "Orbite basse", onClose = { open = false })
                OverlayLayer(overlays)
            } }
        }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        openTrailerWithOk()
        assertTrue("bande-annonce ouverte, focus piégé (focus : ${focusedTag()})", focusedTag() != "dt-trailer")
        pressBack()
        assertEquals("Retour ferme la bande-annonce sans quitter la fiche", 0, backs)
        rule.onNodeWithTag("dt-primary").assertExists()
        assertEquals("focus après Retour", "dt-trailer", focusedTag())
        // La bande-annonce ne se rouvre pas toute seule.
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        assertEquals("focus après Retour", "dt-trailer", focusedTag())
        assertEquals(0, backs)
    }

    @Test fun bandeAnnonce_retourFermeSeulement_serie() {
        var backs = 0
        rule.setDetail(tv = true, width = 1920, height = 1080) {
            val overlays = remember { OverlayHost() }
            androidx.compose.runtime.CompositionLocalProvider(LocalOverlayHost provides overlays) { Box {
                SeriesDetailTvContent(
                    data = DetailFixtures.series(2, 4, activeSeason = 1), onSeason = {}, onPlayTarget = {}, onPlayEpisode = {}, onOpenPerson = {}, onBack = { backs++ },
                    onLongPressEpisode = {}, downloadLabel = "Télécharger la saison", onDownloadSeason = {}, favorite = {}, trakt = {},
                )
                OverlayLayer(overlays)
            } }
        }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        openTrailerWithOk()
        assertTrue("bande-annonce ouverte, focus piégé (focus : ${focusedTag()})", focusedTag() != "dt-trailer")
        pressBack()
        assertEquals("Retour ferme la bande-annonce sans quitter la fiche", 0, backs)
        rule.onNodeWithTag("dt-episode-1-1").assertExists()
        assertEquals("focus après Retour", "dt-trailer", focusedTag())
    }
}
