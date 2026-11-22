package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.activity.ComponentActivity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.mobile.EXPANDED_MIN_DP
import com.ultratv.tv.nativeapp.ui.mobile.ExpandableText
import com.ultratv.tv.nativeapp.ui.mobile.MobileDetailFrame
import com.ultratv.tv.nativeapp.ui.mobile.MobileStrings
import com.ultratv.tv.nativeapp.ui.mobile.usesTwoPane
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Filet de non-régression des fiches AU TOUCHER (téléphone, tablette). Les écrans tactiles (`MovieDetailScreen`,
 * `SeriesDetailScreen`) sont bâtis sur des ViewModel Hilt dans leur corps : on garde donc les briques sans ViewModel que ces
 * écrans assemblent (`ExpandableText`, `MobileDetailFrame`, `usesTwoPane`). Fenêtre de 1300 × 1300 dp : le contenu est imposé à
 * la taille testée (390×844, 320×568, 844×390, 800×1280, 1280×800).
 * Non couvert faute de ViewModel : saisons en pastilles, épisode → lecture, boutons de téléchargement (voir le rapport de la tâche 1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1300dp-h1300dp-xhdpi")
class DetailTouchRegressionTest {
    @get:Rule val rule: AndroidComposeTestRule<*, ComponentActivity> = createAndroidComposeRule<ComponentActivity>()

    @After fun reset() = resetDetailHost()

    private val M = MobileStrings.forLang(AppLang.French, "fr")
    private val longPlot = (1..40).joinToString(" ") { "Une phrase assez longue pour remplir plusieurs lignes du synopsis $it." }

    private fun synopsisLines(): Int {
        val node = rule.onNode(hasText("Une phrase", substring = true)).fetchSemanticsNode()
        val out = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.first().lineCount
    }

    private fun synopsisRepliable(w: Int, h: Int, scale: Float) {
        val ctx = "taille ${w}x$h, police $scale"
        rule.setDetail(tv = false, width = w, height = h, fontScale = scale) { ExpandableText(longPlot) }
        // Replié : exactement 4 lignes, et « Plus » proposé.
        assertEquals("$ctx : replié à 4 lignes", 4, synopsisLines())
        rule.onNodeWithText(M.moreInfo).assertIsDisplayed()
        // « Plus » déplie tout.
        rule.onNodeWithText(M.moreInfo).performClick()
        rule.waitForIdle()
        assertTrue("$ctx : déplié, plus de 4 lignes", synopsisLines() > 4)
        // « Moins » replie.
        rule.onNodeWithText(M.lessInfo).assertIsDisplayed()
        rule.onNodeWithText(M.lessInfo).performClick()
        rule.waitForIdle()
        assertEquals("$ctx : replié de nouveau", 4, synopsisLines())
        rule.onNodeWithText(M.moreInfo).assertIsDisplayed()
    }

    @Test fun tactile_synopsisRepliable_telephone() = synopsisRepliable(390, 844, 1f)
    @Test fun tactile_synopsisRepliable_petitTelephone_texte13() = synopsisRepliable(320, 568, 1.3f)
    @Test fun tactile_synopsisRepliable_telephonePaysage() = synopsisRepliable(844, 390, 1f)
    @Test fun tactile_synopsisRepliable_tabletteTexte13() = synopsisRepliable(800, 1280, 1.3f)

    @Test fun tactile_synopsisCourt_sansPlus() {
        rule.setDetail(tv = false, width = 390, height = 844) { ExpandableText("Un synopsis court.") }
        assertTrue(rule.onAllNodesWithText(M.moreInfo).fetchSemanticsNodes().isEmpty())
    }

    /**
     * Bouton Retour flottant des fiches tactiles : en haut à gauche (12 dp du bord, 8 dp du haut, hors barre d'état), cible de
     * 48 dp, JAMAIS recouvert par le visuel ni par le contenu qui défile. Le décalage sous une vraie barre d'état
     * (`windowInsetsPadding(WindowInsets.statusBars)`) ne peut pas être piloté ici : Robolectric n'injecte pas d'encarts dans
     * Compose (essais `dispatchApplyWindowInsets` sans effet) ; il reste à la liste de contrôle sur appareil réel (tâche 9).
     */
    private fun retourFlottant(w: Int, h: Int) {
        var back = 0
        rule.setDetail(tv = false, width = w, height = h) {
            MobileDetailFrame(null, null, "Orbite basse", onBack = { back++ }) { Text("Contenu", color = androidx.compose.ui.graphics.Color.White) }
        }
        val d = rule.density.density
        val b = rule.onNodeWithTag("dt-back").fetchSemanticsNode().boundsInRoot
        assertEquals("${w}x$h : bord gauche à 12 dp", 12f * d, b.left, 1f)
        assertEquals("${w}x$h : haut à 8 dp (sans barre d'état)", 8f * d, b.top, 1f)
        assertEquals("${w}x$h : cible de 48 dp", 48f * d, b.width, 1f)
        assertEquals("${w}x$h : cible de 48 dp", 48f * d, b.height, 1f)
        rule.onNodeWithTag("dt-back").performClick()
        assertEquals(1, back)
    }

    @Test fun tactile_retourFlottant_telephone() = retourFlottant(390, 844)
    @Test fun tactile_retourFlottant_tabletteDeuxVolets() = retourFlottant(1280, 800)

    @Test fun tactile_grandEcran_seuilDeuxVolets() {
        assertEquals(840f, EXPANDED_MIN_DP, 0f)
        assertFalse(usesTwoPane(839f)); assertTrue(usesTwoPane(840f))
        // Tailles du lot : portrait et téléphone à un volet ; tablette large et paysage large à deux volets.
        for ((w, expected) in listOf(320 to false, 390 to false, 800 to false, 844 to true, 1280 to true)) assertEquals("largeur $w", expected, usesTwoPane(w.toFloat()))
    }

    private fun contentLeftFraction(w: Int, h: Int): Float {
        rule.setDetail(tv = false, width = w, height = h) {
            MobileDetailFrame(null, null, "Orbite basse", onBack = {}) { Text("Contenu", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.testTag("probe").fillMaxWidth()) }
        }
        val b = rule.onNodeWithTag("probe").fetchSemanticsNode().boundsInRoot
        return b.left / (w * rule.density.density)
    }

    @Test fun tactile_grandEcran_deuxVolets() {
        // 1280 × 800 : visuel à gauche (42 %), contenu à droite.
        val f = contentLeftFraction(1280, 800)
        assertTrue("deux volets : le contenu commence après le visuel (à ${f * 100} % de la largeur)", f > 0.38f)
    }

    @Test fun tactile_tabletteAUnVolet_800x1280() {
        // 800 dp de large (< 840) : un seul volet, le contenu commence au bord (marge de 12 dp).
        val f = contentLeftFraction(800, 1280)
        assertTrue("un volet : le contenu part du bord gauche (à ${f * 100} %)", f < 0.1f)
    }
}
