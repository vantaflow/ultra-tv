package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.common.InputClock
import com.ultratv.tv.nativeapp.ui.common.LocalOverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayHost
import com.ultratv.tv.nativeapp.ui.common.OverlayLayer
import com.ultratv.tv.nativeapp.ui.common.ScreenFocusHost
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Réglages › Sources › Ajouter une source › « Portail Stalker (MAC) » à la télécommande (box Google TV) :
 * le formulaire se refermait aussitôt (clavier affiché puis masqué) et, ailleurs dans les Réglages, « la sélection sautait ».
 *
 * Cause : quand l'élément focalisé d'une modale disparaît (choix fait dans la liste, modale fermée), Compose rend le focus
 * au PREMIER élément focalisable de l'écran (en haut à gauche) = le menu latéral, qui, sans touche de déplacement récente,
 * renvoie aussitôt le focus vers la droite : il tombe sur une rubrique quelconque, la rubrique suit le focus, le volet
 * « Sources » quitte la composition et emporte la modale (son état `dialog` y est mémorisé).
 *
 * L'hôte reproduit la structure réelle : menu latéral (même logique de focus que SidebarNav), ScreenFocusHost, rubriques
 * qui suivent le focus, volet Sources avec son `when (dialog)`, OverlayHost + OverlayLayer à la racine, mise à l'échelle TV.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class StalkerDialogFlowTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private enum class Dlg { NONE, CHOOSER, XTREAM, STALKER, M3U }

    private var rub by mutableStateOf(0)
    private val windowH = mutableStateOf(1080.dp)
    private val dialogs = mutableListOf<Dlg>()

    /** Comme ProvideUiScale sur TV : densité = hauteur_px / 540 (le clavier qui raccourcit la fenêtre change la densité). */
    @Composable
    private fun TvScale(content: @Composable () -> Unit) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = androidx.compose.ui.unit.Density(com.ultratv.tv.nativeapp.ui.common.uiDensityFor(constraints.maxHeight, 2f), 1f)
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides d,
                com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp provides constraints.maxWidth / d.density,
                com.ultratv.tv.nativeapp.ui.mobile.LocalTouch provides false,
            ) { content() }
        }
    }

    /** Menu latéral : même logique de focus que SidebarNav (entrée sur la page active, sortie à droite sans touche de déplacement). */
    @Composable
    private fun Rail() {
        val fm = LocalFocusManager.current
        val active = remember { FocusRequester() }
        Column(
            Modifier.width(48.dp).fillMaxHeight()
                .focusProperties { enter = { active } }
                .focusGroup()
                .onFocusChanged { st -> if (st.hasFocus) railVisits++; if (st.hasFocus && !InputClock.recentNavKey()) fm.moveFocus(FocusDirection.Right) },
        ) {
            repeat(7) { i ->
                FocusSurface(onClick = {}, modifier = Modifier.width(48.dp).height(40.dp).testTag("rail-$i").then(if (i == 6) Modifier.focusRequester(active) else Modifier)) { _ -> }
            }
        }
    }

    private var withRail = true
    /** Passages du focus par le menu latéral (focus « rendu » par Compose au premier élément en haut à gauche). */
    private var railVisits = 0

    @Composable
    private fun Host() {
        val overlays = remember { OverlayHost() }
        CompositionLocalProvider(LocalOverlayHost provides overlays) {
            Box(Modifier.fillMaxSize()) {
                // Comme UltraTvAppRoot (TV_RAIL) : contenu décalé de la largeur du rail replié, rail dessiné PAR-DESSUS.
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(start = 48.dp)) {
                    ScreenFocusHost(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxSize()) {
                            Column(Modifier.width(160.dp)) {
                                Spacer(Modifier.height(24.dp))
                                repeat(9) { i ->
                                    FocusSurface(onClick = { rub = i }, modifier = Modifier.fillMaxWidth().height(36.dp).testTag("rub-$i")
                                        .onFocusChanged { if (it.isFocused && rubFollowsFocus(overlays.hasModal)) rub = i }) { _ -> Text("Rubrique $i") }
                                }
                            }
                            Column(Modifier.weight(1f)) { when (rub) { 0 -> SourcesLike(); 3 -> DisplayLike(); else -> Text("Rubrique $rub") } }
                        }
                    }
                    }
                    if (withRail) Rail()
                }
                OverlayLayer(overlays)
            }
        }
    }

    @Composable
    private fun SourcesLike() {
        var dialog by remember { mutableStateOf(Dlg.NONE) }
        androidx.compose.runtime.SideEffect { if (dialogs.lastOrNull() != dialog) dialogs += dialog }
        FocusSurface(onClick = { dialog = Dlg.CHOOSER }, modifier = Modifier.width(300.dp).height(48.dp).testTag("add-source")) { _ -> Text("Ajouter une source") }
        when (dialog) {
            Dlg.CHOOSER -> ChoiceDialog("Ajouter une source", listOf(Dlg.XTREAM to "Xtream Codes", Dlg.STALKER to "Portail Stalker (MAC)", Dlg.M3U to "M3U"), null,
                onPick = { dialog = it }, onDismiss = { dialog = Dlg.NONE })
            Dlg.XTREAM -> XtreamDialog({ dialog = Dlg.NONE }) { _, _, _, _ -> dialog = Dlg.NONE }
            Dlg.M3U -> M3uDialog({ dialog = Dlg.NONE }) { _, _ -> dialog = Dlg.NONE }
            Dlg.STALKER -> {
                var checking by remember { mutableStateOf(false) }
                StalkerDialog({ dialog = Dlg.NONE }, busy = checking) { _, _, _, _, _ -> checking = true }
            }
            Dlg.NONE -> Unit
        }
    }

    /** Volet « Affichage » : une ligne de préférence qui ouvre une boîte de choix (ex. Taille du texte). */
    @Composable
    private fun DisplayLike() {
        var open by remember { mutableStateOf(false) }
        Text("Affichage")
        Spacer(Modifier.height(120.dp))
        FocusSurface(onClick = { open = true }, modifier = Modifier.width(300.dp).height(48.dp).testTag("pref-text")) { _ -> Text("Taille du texte") }
        if (open) ChoiceDialog("Taille du texte", listOf("n" to "Normal", "g" to "Grand", "tg" to "Très grand"), "n", onPick = { open = false }, onDismiss = { open = false })
    }

    /** Appui télécommande complet (comme MainActivity.dispatchKeyEvent, qui horodate la touche dans InputClock). */
    private fun press(key: Key) {
        InputClock.lastKeyCode = key.nativeKeyCode
        InputClock.lastKeyMs = android.os.SystemClock.uptimeMillis()
        // Retour : MainActivity le transmet au OnBackPressedDispatcher (BackHandler des modales).
        if (key == Key.Back) rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        else rule.onRoot().performKeyInput { keyDown(key); keyUp(key) }
        rule.waitForIdle()
    }

    private fun settle() { rule.mainClock.advanceTimeBy(1_500); rule.waitForIdle() }

    private fun openSettings() {
        rule.setContent { Box(Modifier.fillMaxWidth().height(windowH.value)) { TvScale { Host() } } }
        settle()
    }

    private fun openSettingsOnAddSource() {
        rule.setContent { Box(Modifier.fillMaxWidth().height(windowH.value)) { TvScale { Host() } } }
        rule.onNodeWithTag("add-source").requestFocus()
        settle()
    }

    private fun pickInChooser(downs: Int, ok: Key = Key.DirectionCenter) {
        press(ok); settle()
        repeat(downs) { press(Key.DirectionDown) }
        press(ok); settle()
    }

    @Test fun stalker_choisiAuD_padAvecOk_resteOuvert() {
        openSettingsOnAddSource()
        pickInChooser(1)
        assertEquals("la rubrique ne doit pas changer toute seule", 0, rub)
        assertEquals("le focus ne transite pas par le menu latéral", 0, railVisits)
        rule.onNodeWithTag("field-mac").assertExists()
        rule.onNodeWithTag("field-url").assertIsFocused()
        assertEquals(listOf(Dlg.NONE, Dlg.CHOOSER, Dlg.STALKER), dialogs)
    }

    @Test fun stalker_choisiAvecEntree_resteOuvert() {
        openSettingsOnAddSource()
        pickInChooser(1, Key.Enter)
        assertEquals(0, rub)
        rule.onNodeWithTag("field-mac").assertExists()
        rule.onNodeWithTag("field-url").assertIsFocused()
    }

    @Test fun xtream_choisiAuD_pad_resteOuvert() {
        openSettingsOnAddSource()
        pickInChooser(0)
        assertEquals(0, rub)
        rule.onNodeWithTag("field-user").assertExists()
        rule.onNodeWithTag("field-url").assertIsFocused()
    }

    @Test fun stalker_resteOuvert_quandLeClavierApparaitPuisDisparait() {
        openSettingsOnAddSource()
        pickInChooser(1)
        // Clavier à l'écran : la fenêtre (adjustResize) raccourcit puis retrouve sa taille ; la densité TV change avec.
        windowH.value = 560.dp; settle()
        rule.onNodeWithTag("field-mac").assertExists()
        rule.onNodeWithTag("field-url").assertIsFocused()
        windowH.value = 1080.dp; settle()
        rule.onNodeWithTag("field-mac").assertExists()
        rule.onNodeWithTag("field-url").assertIsFocused()
        assertEquals(0, rub)
    }

    @Test fun stalker_resteOuvert_quandLaVuePerdPuisRetrouveLeFocus() {
        openSettingsOnAddSource()
        pickInChooser(1)
        rule.runOnUiThread {
            val v = rule.activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            v.clearFocus(); v.requestFocus()
        }
        settle()
        rule.onNodeWithTag("field-mac").assertExists()
        assertEquals(0, rub)
        // Le focus revient DANS le formulaire, sur le champ quitté (avant : il restait derrière la modale, clavier refermé).
        rule.onNodeWithTag("field-url").assertIsFocused()
    }

    /**
     * Focus « parasite » posé sur une autre rubrique pendant que le formulaire est ouvert (focus rendu par Compose qui
     * retombe, via le menu latéral, sur la rubrique à la hauteur de l'entrée « Réglages ») : avant, la rubrique suivait ce
     * focus, le volet Sources disparaissait et le formulaire avec ; le clavier, ouvert un instant, se refermait.
     */
    @Test fun focusParasiteSurUneRubrique_neFermePasLeFormulaire() {
        openSettingsOnAddSource()
        pickInChooser(1)
        // Focus perdu (clavier à l'écran : la vue Compose perd le focus), puis rendu à une rubrique, sans touche de déplacement.
        rule.runOnUiThread { rule.activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0).clearFocus() }
        rule.onNodeWithTag("rub-6").requestFocus()
        settle()
        assertEquals(0, rub)
        rule.onNodeWithTag("field-mac").assertExists()
        rule.onNodeWithTag("field-url").assertIsFocused()
    }

    @Test fun choixDansUneModale_sansMenuLateral_laSelectionNeSautePas() {
        withRail = false
        openSettings()
        rule.onNodeWithTag("rub-0").requestFocus()
        settle()
        repeat(3) { press(Key.DirectionDown) }
        settle()
        assertEquals(3, rub)
        press(Key.DirectionRight); settle()
        rule.onNodeWithTag("pref-text").assertIsFocused()
        press(Key.DirectionCenter); settle()
        press(Key.DirectionDown)
        press(Key.DirectionCenter); settle()
        // Avant : le focus « rendu » tombait sur la première rubrique (en haut à gauche) et le volet changeait.
        assertEquals(3, rub)
        rule.onNodeWithTag("pref-text").assertIsFocused()
    }

    @Test fun fermerUneModaleAuRetour_rendLeFocusALElementQuiLAOuverte() {
        openSettingsOnAddSource()
        press(Key.DirectionCenter); settle()
        val before = railVisits
        press(Key.Back); settle()
        assertEquals(0, rub)
        rule.onNodeWithTag("add-source").assertIsFocused()
        assertEquals("le focus revient directement, sans passer par le menu latéral", before, railVisits)
    }

    @Test fun annulerLeFormulaire_rendLeFocusALElementQuiLAOuvert() {
        openSettingsOnAddSource()
        pickInChooser(1)
        press(Key.Back); settle()
        assertEquals(0, rub)
        rule.onNodeWithTag("field-mac").assertDoesNotExist()
        rule.onNodeWithTag("add-source").assertIsFocused()
    }
}
