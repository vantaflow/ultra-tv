package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.stringsOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** États de fiche au toucher : téléphone 320 / 390 dp, tablette 800 × 1280, taille de texte 1,3. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1300dp-h1300dp-xhdpi")
class DetailStatesTouchTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() = resetDetailHost()

    private fun checkInside(ctx: String, width: Int, height: Int, tags: List<String>) {
        for (t in tags) {
            val b = rule.onNodeWithTag(t).fetchSemanticsNode().boundsInRoot
            val dens = 2f // xhdpi
            assertTrue("$ctx : $t dans la largeur ($b)", b.left >= -0.5f && b.right <= width * dens + 0.5f)
            assertTrue("$ctx : $t dans la hauteur ($b)", b.top >= -0.5f && b.bottom <= height * dens + 0.5f)
            assertTrue("$ctx : $t cible d'au moins 48 dp (${b.height})", b.height >= 48 * dens - 1f)
        }
    }

    private fun checkErreur(w: Int, h: Int) {
        val msg = stringsOf(AppLang.French).sync.messageFor(SyncErrorKind.INVALID_RESPONSE)
        rule.setDetail(tv = false, width = w, height = h, fontScale = 1.3f) { DetailStatePanel(DetailLoad.Failed(SyncErrorKind.INVALID_RESPONSE), {}, {}) }
        rule.onNode(hasText(msg)).assertExists()
        val m = rule.onNode(hasText(msg)).fetchSemanticsNode().boundsInRoot
        assertTrue("${w}x$h : message dans la largeur ($m)", m.left >= -0.5f && m.right <= w * 2f + 0.5f)
        checkInside("${w}x$h erreur", w, h, listOf("dt-state-retry", "dt-state-back"))
    }

    @Test fun etats_tactile_police13_telephone320() = checkErreur(320, 568)
    @Test fun etats_tactile_police13_telephone390() = checkErreur(390, 844)
    @Test fun etats_tactile_police13_tabletteVerticale() = checkErreur(800, 1280)
    @Test fun etats_tactile_police13_tabletteHorizontale() = checkErreur(1280, 800)
    @Test fun etats_tactile_police13_telephonePaysage() = checkErreur(844, 390)

    @Test fun introuvable_tactile_retourFonctionne() {
        var back = 0
        rule.setDetail(tv = false, width = 390, height = 844, fontScale = 1.3f) { DetailStatePanel(DetailLoad.NotFound, {}, { back++ }) }
        rule.onNode(hasText("Ce titre n'est plus disponible")).assertExists()
        rule.onAllNodes(hasTestTag("dt-state-retry")).assertCountEquals(0)
        rule.onNodeWithTag("dt-state-back").performClick()
        assertEquals(1, back)
    }

    @Test fun erreur_tactile_reessayerEtRetour() {
        var retry = 0; var back = 0
        rule.setDetail(tv = false, width = 390, height = 844) { DetailStatePanel(DetailLoad.Failed(SyncErrorKind.TIMEOUT), { retry++ }, { back++ }) }
        rule.onNodeWithTag("dt-state-retry").performClick()
        rule.onNodeWithTag("dt-state-back").performClick()
        assertEquals(1, retry); assertEquals(1, back)
    }

    @Test fun chargement_tactile_silhouetteSansTexte() {
        rule.setDetail(tv = false, width = 390, height = 844, fontScale = 1.3f) { DetailStatePanel(DetailLoad.Loading, {}, {}) }
        rule.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Text)).assertCountEquals(0)
    }

    @Test fun arabe_tactile_messageTraduit() {
        rule.setDetail(tv = false, width = 390, height = 844, lang = AppLang.Arabic) { DetailStatePanel(DetailLoad.NotFound, {}, {}) }
        rule.onNode(hasText("هذا العنوان لم يعد متاحًا")).assertExists()
    }

    @Test fun themeClair_etatsLisibles() {
        rule.setDetail(tv = false, width = 390, height = 844, light = true) { DetailStatePanel(DetailLoad.Failed(SyncErrorKind.TIMEOUT), {}, {}) }
        rule.onNodeWithTag("dt-state").assertExists()
    }
}
