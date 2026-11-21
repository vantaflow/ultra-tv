package com.ultratv.tv.nativeapp.ui.live

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.components.RailBackHandler
import com.ultratv.tv.nativeapp.ui.components.RailMenuFocusLink
import com.ultratv.tv.nativeapp.ui.components.RailMenuState
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TV en direct, touche Retour (demande du propriétaire, « comme Films et Séries ») : focus dans la liste des chaînes ou
 * dans l'aperçu → Retour ramène le focus sur la catégorie EN COURS ; focus dans les catégories → Retour ouvre le menu
 * latéral, focus sur « TV en direct ». Les fenêtres gardent la priorité ; tactile inchangé.
 * Le montage reprend la structure de l'écran (catégories | chaînes | aperçu) avec le vrai [LiveBackToCategories] et le vrai
 * gestionnaire racine [RailBackHandler].
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class LiveBackToCategoriesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val menu = RailMenuState()
    private var exits = 0
    private val modalOpen = mutableStateOf(false)
    private var cats = listOf("all", "news", "sport", "kids")
    private var selected = "sport"
    private var catState: LazyListState? = null

    @Composable
    private fun Rail(modifier: Modifier) {
        val root = remember { FocusRequester() }
        val active = remember { FocusRequester() }
        RailMenuFocusLink(menu, root)
        Column(
            modifier.width(48.dp).fillMaxHeight().focusRequester(root).focusProperties { enter = { active } }.focusGroup()
                .onFocusChanged { if (!it.hasFocus) menu.expanded = false },
        ) {
            listOf(Routes.HOME, Routes.LIVE, Routes.GUIDE).forEach { r ->
                FocusSurface(onClick = {}, modifier = Modifier.size(48.dp).testTag("rail-$r").then(if (r == Routes.LIVE) Modifier.focusRequester(active) else Modifier)) { _ -> }
            }
        }
    }

    private fun mount(rtl: Boolean = false, touch: Boolean = false) {
        rule.activity.onBackPressedDispatcher.addCallback(rule.activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { exits++ }
        })
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, LocalTouch provides touch) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    // Comme l'appli : le Retour racine est enregistré au démarrage, AVANT l'écran (dont les retours locaux,
                    // enregistrés plus tard, passent donc en premier).
                    RailBackHandler(route = Routes.LIVE, menu = menu, modalOpen = false, simpleHome = false, railShown = true, onMenuToHome = {})
                    Row(Modifier.fillMaxSize().padding(start = 48.dp).focusGroup()) {
                        val catTarget = remember { FocusRequester() }
                        val catList = remember { FocusRequester() }
                        var contentFocused by remember { mutableStateOf(false) }
                        // Comme l'écran : vraie colonne paresseuse, état de défilement et retour fournis par LiveScreen.
                        val selectedIndex = cats.indexOf(selected)
                        val state = rememberLiveCategoryListState(selectedIndex).also { catState = it }
                        LiveBackToCategories(contentFocused = contentFocused, categoryState = state, selectedIndex = selectedIndex, selectedCategory = catTarget, categoryList = catList)
                        LazyColumn(Modifier.width(160.dp).fillMaxHeight().focusRequester(catList), state = state) {
                            items(cats, key = { it }) { c ->
                                FocusSurface(onClick = {}, modifier = Modifier.size(150.dp, 40.dp).testTag("cat-$c").then(if (c == selected) Modifier.focusRequester(catTarget) else Modifier)) { _ -> }
                            }
                        }
                        Row(Modifier.weight(1f).onFocusChanged { contentFocused = it.hasFocus }) {
                            Column(Modifier.width(300.dp)) {
                                listOf("tf1", "m6", "arte").forEach { ch ->
                                    FocusSurface(onClick = {}, modifier = Modifier.size(280.dp, 40.dp).testTag("ch-$ch")) { _ -> }
                                }
                            }
                            FocusSurface(onClick = {}, modifier = Modifier.size(200.dp, 60.dp).testTag("preview-watch")) { _ -> }
                        }
                    }
                    Rail(Modifier.align(Alignment.TopStart))
                    if (modalOpen.value) BackHandler { modalOpen.value = false }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun focus(tag: String) { rule.onNode(isFocusable() and hasTestTag(tag)).requestFocus(); rule.waitForIdle() }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun focusedTag(): String? {
        val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().singleOrNull() ?: return null
        return generateSequence(n) { it.parent }.firstNotNullOfOrNull { p -> if (SemanticsProperties.TestTag in p.config) p.config[SemanticsProperties.TestTag] else null }
    }

    private fun deuxEtapes(start: String, rtl: Boolean = false) {
        mount(rtl)
        focus(start)
        back()
        assertEquals("$start : Retour ramène sur la catégorie en cours", "cat-$selected", focusedTag())
        assertFalse("le menu reste fermé", menu.expanded)
        back()
        assertTrue("depuis les catégories : le menu s'ouvre", menu.expanded)
        assertEquals("focus sur « TV en direct »", "rail-${Routes.LIVE}", focusedTag())
        assertEquals(0, exits)
    }

    @Test fun retour_depuisUneChaine_revientALaCategorieEnCours_puisMenu() = deuxEtapes("ch-arte")
    @Test fun retour_depuisLaPremiereChaine() = deuxEtapes("ch-tf1")
    @Test fun retour_depuisLApercu_revientALaCategorieEnCours() = deuxEtapes("preview-watch")
    @Test fun retour_enArabe_memeComportement() = deuxEtapes("ch-m6", rtl = true)

    @Test fun retour_depuisUneAutreCategorie_ouvreLeMenu() {
        mount()
        focus("cat-news")
        back()
        assertTrue(menu.expanded)
        assertEquals("rail-${Routes.LIVE}", focusedTag())
    }

    @Test fun retour_fenetreOuverte_garde_la_priorite() {
        mount()
        focus("ch-m6")
        rule.runOnUiThread { modalOpen.value = true }
        rule.waitForIdle()
        back()
        assertFalse("la fenêtre se ferme d'abord", modalOpen.value)
        assertEquals("le focus n'a pas bougé", "ch-m6", focusedTag())
    }

    @Test fun tactile_inchange_retourNeDeplacePasLeFocus() {
        mount(touch = true)
        focus("ch-m6")
        back()
        assertTrue("tactile : la règle racine s'applique telle quelle", focusedTag() != "cat-$selected")
    }

    // ── Longue liste (cas courant) : la catégorie en cours est hors de l'écran du haut de la colonne. ──
    private fun longue() { cats = (1..60).map { "c$it" }; selected = "c45" }

    @Test fun ouverture_longueListe_laColonneMontreDejaLaCategorieEnCours() {
        longue()
        mount()
        focus("ch-tf1")
        rule.onNodeWithTag("cat-c45").assertIsDisplayed()
        assertTrue("la 45e catégorie est affichée", catState!!.isItemShown(44))
    }

    @Test fun retour_longueListe_focusSurLa45eCategorieAffichee_puisMenu() {
        longue()
        mount()
        focus("ch-arte")
        back()
        assertEquals("Retour : focus sur la catégorie en cours (45e)", "cat-c45", focusedTag())
        rule.onNodeWithTag("cat-c45").assertIsDisplayed()
        assertFalse(menu.expanded)
        back()
        assertTrue("depuis les catégories : le menu s'ouvre", menu.expanded)
        assertEquals("rail-${Routes.LIVE}", focusedTag())
        assertEquals(0, exits)
    }

    @Test fun retour_longueListe_colonneRemonteeEnHaut_defileJusquALaCategoriePuisFocus() {
        longue()
        mount()
        focus("ch-m6")
        // Colonne revenue en haut (état ancien, défilement manuel…) : la 45e n'est plus composée.
        rule.runOnIdle { kotlinx.coroutines.runBlocking { catState!!.scrollToItem(0) } }
        rule.waitForIdle()
        assertFalse("hors écran avant Retour", catState!!.isItemShown(44))
        assertEquals("ch-m6", focusedTag())
        back()
        assertEquals("cat-c45", focusedTag())
        rule.onNodeWithTag("cat-c45").assertIsDisplayed()
    }

    @Test fun retour_longueListe_enArabe() {
        longue()
        mount(rtl = true)
        focus("ch-tf1")
        rule.runOnIdle { kotlinx.coroutines.runBlocking { catState!!.scrollToItem(0) } }
        rule.waitForIdle()
        back()
        assertEquals("cat-c45", focusedTag())
        back()
        assertTrue(menu.expanded)
    }

    @Test fun retour_longueListe_tactile_inchange() {
        longue()
        mount(touch = true)
        focus("ch-m6")
        back()
        assertTrue(focusedTag() != "cat-c45")
    }
}
