package com.ultratv.tv.nativeapp.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rangées de pastilles du catalogue montées dans les marges de page réelles ([catalogPagePadding]) : le débordement de
 * début (`rowBleedStart`) suit le sens de lecture, la première pastille est alignée sur le bord de début, et aucune
 * pastille focalisée (anneau compris) n'est rognée par l'écran. Fenêtre 960 x 540 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class CatalogRowsBleedTest {
    @get:Rule val rule = createComposeRule()

    private val chips = (1..14).map { CategoryChip("c$it", "Catégorie numéro $it", 10) }

    private fun mount(lang: AppLang, touch: Boolean, row: @androidx.compose.runtime.Composable () -> Unit) {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides touch,
                LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val input = LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { input.requestInputMode(InputMode.Keyboard) }
                Column(Modifier.fillMaxSize().background(Ux.Bg).then(catalogPagePadding(touch))) { row() }
            }
        }
        rule.waitForIdle()
    }

    private fun first() = rule.onAllNodes(isFocusable())[0]

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { keyDown(key); keyUp(key) }
        rule.waitForIdle()
    }

    /**
     * La première pastille touche le bord de début (marge de page [startDp]) ; on avance jusqu'au bout de la rangée puis on
     * revient : la pastille focalisée, agrandie de [scale] et de son anneau [ringDp], reste dans l'écran, et au retour la
     * première retrouve exactement sa place.
     */
    private fun assertRow(lang: AppLang, touch: Boolean, startDp: Float, backDp: Float, scale: Float, ringDp: Float, steps: Int, row: @androidx.compose.runtime.Composable () -> Unit) {
        mount(lang, touch, row)
        val rtl = lang.rtl
        val screen = rule.onRoot().getUnclippedBoundsInRoot()
        fun startEdge(): Float { val b = first().getUnclippedBoundsInRoot(); return if (rtl) screen.right.value - b.right.value else b.left.value }
        assertEquals("première pastille : marge de début", startDp, startEdge(), 0.5f)
        first().requestFocus(); rule.waitForIdle()
        fun inside() {
            val f = rule.onNode(isFocused()).getUnclippedBoundsInRoot()
            val mx = f.width.value * (scale - 1f) / 2f + ringDp * scale; val my = f.height.value * (scale - 1f) / 2f + ringDp * scale
            assertTrue("pastille coupée à gauche (${f.left.value - mx})", f.left.value - mx >= screen.left.value)
            assertTrue("pastille coupée à droite (${f.right.value + mx} / ${screen.right})", f.right.value + mx <= screen.right.value)
            assertTrue("pastille coupée en haut", f.top.value - my >= screen.top.value)
            assertTrue("pastille coupée en bas", f.bottom.value + my <= screen.bottom.value)
        }
        inside()
        val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight
        val backward = if (rtl) Key.DirectionRight else Key.DirectionLeft
        repeat(steps) { press(forward); inside() }
        repeat(steps) { press(backward); inside() }
        assertEquals("retour au début : la première pastille se cale sur la marge de défilement", backDp, startEdge(), 0.5f)
    }

    private val tvRow: @androidx.compose.runtime.Composable () -> Unit = { CatalogChipRows(chips, null, CatalogFilter(), {}, {}) }
    private val touchRow: @androidx.compose.runtime.Composable () -> Unit = { TouchFilterRow(chips, null, CatalogFilter(), {}, {}) }

    // TV : marge de page de début 72 (unités design) = 36 dp ; au retour la pastille se cale sur le bord de la zone de rognage
    // (36 - 12 de débordement = 24 dp, dans les deux sens de lecture) ; pastille sans zoom ni anneau.
    @Test fun rangeCategoriesTv_gaucheADroite_alignee_etNonRognee() = assertRow(AppLang.French, false, 72.design.value, 72.design.value - 12f, 1f, 0f, 14, tvRow)
    @Test fun rangeCategoriesTv_droiteAGauche_alignee_etNonRognee() = assertRow(AppLang.Arabic, false, 72.design.value, 72.design.value - 12f, 1f, 0f, 14, tvRow)

    // Téléphone : marge de page de début 12 dp ; au retour, marge de défilement de 10 dp ; pastille zoomée à 1,06 avec anneau de 6 (unités design).
    @Test fun rangeFiltresTactile_gaucheADroite_alignee_etNonRognee() = assertRow(AppLang.French, true, 12f, 10f, 1.06f, 6.design.value, 15, touchRow)
    @Test fun rangeFiltresTactile_droiteAGauche_alignee_etNonRognee() = assertRow(AppLang.Arabic, true, 12f, 10f, 1.06f, 6.design.value, 15, touchRow)
}
