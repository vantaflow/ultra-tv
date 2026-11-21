package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Décision « la carte quittée n'existe plus » de [HomeFocusRestore] : elle ne se prend que d'après les clés d'une rangée
 * DÉCLARÉE, composée, non vide et à jour ; dans le doute on attend (le délai de dernier recours borne l'attente).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RowFocusMemoryTest {
    @get:Rule val rule = createComposeRule()

    private val saved = "ROW\u0000m3"

    @Test fun carteAbsenteDUneRangeeDeclaree_relache() {
        val restore = HomeFocusRestore(saved)
        rule.setContent { CompositionLocalProvider(LocalHomeFocusRestore provides restore) { RegisterRowKeys(RowFocusMemory("ROW"), 1) { listOf("m1", "m2") } } }
        rule.waitForIdle()
        assertFalse(restore.isPending())
    }

    @Test fun carteDansLesDonnees_resteAttendue() {
        val restore = HomeFocusRestore(saved)
        rule.setContent { CompositionLocalProvider(LocalHomeFocusRestore provides restore) { RegisterRowKeys(RowFocusMemory("ROW"), 1) { listOf("m1", "m3") } } }
        rule.waitForIdle()
        assertTrue(restore.isPending())
    }

    @Test fun rangeeComposeeAvecUneListeVide_resteAttendue_lesDonneesPeuventArriver() {
        val restore = HomeFocusRestore(saved)
        rule.setContent { CompositionLocalProvider(LocalHomeFocusRestore provides restore) { RegisterRowKeys(RowFocusMemory("ROW"), 1) { emptyList() } } }
        rule.waitForIdle()
        assertTrue("liste vide = rien de décidé", restore.isPending())
    }

    @Test fun rangeeSortieDeLaComposition_sesClesPerimeesNeDecidentPlus() {
        val restore = HomeFocusRestore(saved)
        val shown = mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalHomeFocusRestore provides restore) {
                if (shown.value) { val memory = remember { RowFocusMemory("ROW") }; RegisterRowKeys(memory, 1) { listOf("m1", "m2") } }
            }
        }
        rule.waitForIdle()
        // La rangée est sortie de la composition avant la première recherche de focus : ses clés ne valent plus rien.
        rule.runOnUiThread { shown.value = false }
        rule.waitForIdle()
        assertTrue("clés périmées ignorées", restore.isPending())
    }
}
