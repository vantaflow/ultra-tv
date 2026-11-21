package com.ultratv.tv.nativeapp.ui.design

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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Direct › appui long › « Autres qualités » : deux variantes de même qualité et de même nom (« HD · HBO » deux fois chez
 * le fournisseur) donnaient deux options au même libellé, et la liste plantait à l'ouverture
 * (IllegalArgumentException: Key "HD · HBO" was already used).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ChoiceDialogDuplicateLabelsTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun `deux options au meme libelle s affichent sans planter et restent distinctes`() {
        val picked = mutableListOf<Long>()
        rule.setContent {
            val overlays = remember { OverlayHost() }
            CompositionLocalProvider(LocalOverlayHost provides overlays) {
                Box(Modifier.fillMaxSize()) {
                    ChoiceDialog("Autres qualités", listOf(1L to "HD · HBO", 2L to "FHD · HBO", 3L to "HD · HBO"), 1L, { picked += it }, {})
                    OverlayLayer(overlays)
                }
            }
        }
        rule.waitForIdle()
        assertEquals(2, rule.onAllNodesWithText("HD · HBO").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("FHD · HBO").fetchSemanticsNodes().size)
    }
}
