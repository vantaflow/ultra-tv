package com.ultratv.tv.nativeapp.ui.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.ultratv.tv.nativeapp.ui.common.LocalOverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayLayer
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct › appui long › « Autres qualités » quand la qualité est inconnue (« — ») et que le fournisseur envoie deux
 * variantes de même nom : libellés « — · M6 » répétés (télémétrie 1.3.72, Freebox Player POP). Seul site de l'appli qui
 * construit ce libellé ; la boîte de choix doit l'afficher sans planter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class VariantPickerUnknownQualityTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun `deux variantes de qualite inconnue et de meme nom - pas de plantage, deux options`() {
        val labels = variantLabels(listOf(0 to "M6", 0 to "M6", 2 to "M6"))
        assertEquals(listOf("— · M6", "— · M6", "HD · M6"), labels)
        rule.setContent {
            val overlays = remember { OverlayHost() }
            CompositionLocalProvider(LocalOverlayHost provides overlays) {
                Box(Modifier.fillMaxSize()) {
                    ChoiceDialog("Autres qualités", labels.mapIndexed { i, l -> i.toLong() to l }, 0L, {}, {})
                    OverlayLayer(overlays)
                }
            }
        }
        rule.waitForIdle()
        assertEquals(2, rule.onAllNodesWithText("— · M6").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("HD · M6").fetchSemanticsNodes().size)
    }
}
