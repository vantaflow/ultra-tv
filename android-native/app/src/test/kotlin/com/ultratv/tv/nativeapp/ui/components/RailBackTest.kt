package com.ultratv.tv.nativeapp.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Ux
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Retour sur TV : sur un écran principal, quand rien d'autre n'est à fermer, il OUVRE le menu latéral (focus sur l'entrée de
 * l'écran) ; menu ouvert, il suit la règle de sortie de l'appli. Les retours locaux (fenêtre, sous-niveau) gardent la priorité.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class RailBackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val principaux = listOf(
        Routes.HOME, Routes.LIVE, Routes.GUIDE, Routes.SPORT, Routes.MOVIES, Routes.SERIES,
        Routes.FAVORITES, Routes.RECORDINGS, Routes.DOWNLOADS, "account", Routes.SETTINGS, Routes.LIBRARIES,
    )

    // ───────────── Décision (logique pure) ─────────────

    @Test fun decision_ecranPrincipalMenuFerme_ouvreLeMenu() {
        for (r in principaux) assertEquals(r, RailBackAction.OPEN_MENU, railBackAction(r, menuOpen = false, railShown = true, simpleHome = false, modalOpen = false))
    }

    @Test fun decision_menuOuvertSurLAccueil_laissePasserLaRegleDeSortie() {
        assertEquals(RailBackAction.PASS, railBackAction(Routes.HOME, menuOpen = true, railShown = true, simpleHome = false, modalOpen = false))
    }

    @Test fun decision_menuOuvertAilleurs_revientAAccueil() {
        for (r in principaux - Routes.HOME) assertEquals(r, RailBackAction.MENU_TO_HOME, railBackAction(r, menuOpen = true, railShown = true, simpleHome = false, modalOpen = false))
    }

    @Test fun decision_sousNiveauxEtEcransHorsMenu_restentAuxRetoursLocaux() {
        for (r in listOf("movies/12", "series/3", "player?url=x&title=y", Routes.SEARCH, Routes.SETTINGS_ALL, "person/Bob", Routes.SIMPLE_CHANNELS, null))
            assertEquals("$r", RailBackAction.PASS, railBackAction(r, menuOpen = false, railShown = true, simpleHome = false, modalOpen = false))
    }

    @Test fun decision_fenetreOuverte_modeSimple_ouSansMenu_inchange() {
        assertEquals(RailBackAction.PASS, railBackAction(Routes.GUIDE, menuOpen = false, railShown = true, simpleHome = false, modalOpen = true))
        assertEquals(RailBackAction.PASS, railBackAction(Routes.MOVIES, menuOpen = false, railShown = true, simpleHome = true, modalOpen = false))
        assertEquals(RailBackAction.PASS, railBackAction(Routes.HOME, menuOpen = false, railShown = false, simpleHome = false, modalOpen = false))
    }

    // ───────────── Montage : règle de sortie de l'appli, menu, écran ─────────────

    private var exits = 0
    private var toHome = 0
    private val route = mutableStateOf(Routes.HOME)
    private val modalOpen = mutableStateOf(false)
    private val subLevel = mutableStateOf(false)
    private val menu = RailMenuState()

    @Before fun resetState() { exits = 0; toHome = 0; route.value = Routes.HOME; modalOpen.value = false; subLevel.value = false }

    private val railRoutes = listOf(Routes.HOME, Routes.LIVE, Routes.GUIDE, Routes.MOVIES, Routes.SERIES)

    /** Menu latéral : même câblage que SidebarNav (entrée sur la page active, état hissé, lien de focus vers [RailMenuState]). */
    @Composable
    private fun Rail(modifier: Modifier) {
        val root = remember { FocusRequester() }
        val active = remember { FocusRequester() }
        RailMenuFocusLink(menu, root)
        Column(
            modifier.width(48.dp).fillMaxHeight()
                .focusRequester(root)
                .focusProperties { enter = { active } }
                .focusGroup()
                .onFocusChanged { st -> if (!st.hasFocus) menu.expanded = false },
        ) {
            railRoutes.forEach { r ->
                FocusSurface(onClick = {}, modifier = Modifier.width(48.dp).size(48.dp).testTag("rail-$r").then(if (r == route.value) Modifier.focusRequester(active) else Modifier)) { _ -> }
            }
        }
    }

    private fun mount(rtl: Boolean = false) {
        // Dernier recours de l'activité (enregistré AVANT la composition) : la règle de sortie actuelle.
        rule.activity.onBackPressedDispatcher.addCallback(rule.activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { exits++ }
        })
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    Box(Modifier.fillMaxSize().padding(start = 48.dp).focusGroup()) {
                        FocusSurface(onClick = {}, modifier = Modifier.size(200.dp, 60.dp).testTag("content-a")) { _ -> }
                        // Retour local d'un écran (sous-dossier, catégorie) : composé APRÈS le handler racine ci-dessous ? Non : avant, comme dans l'appli
                        // (écrans dans le NavHost, handler racine posé après lui) ; sa priorité vient de son enregistrement plus tardif quand il s'ouvre.
                        if (subLevel.value) BackHandler { subLevel.value = false }
                    }
                    Rail(Modifier.align(Alignment.TopStart))
                    if (modalOpen.value) BackHandler { modalOpen.value = false }
                    RailBackHandler(
                        route = route.value, menu = menu, modalOpen = false, simpleHome = false, railShown = true,
                        onMenuToHome = { toHome++ },
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNode(isFocusable() and hasTestTag("content-a")).requestFocus()
        rule.waitForIdle()
    }

    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    private fun focusedTag(): String? {
        val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().singleOrNull() ?: return null
        return generateSequence(n) { it.parent }.firstNotNullOfOrNull { p -> if (androidx.compose.ui.semantics.SemanticsProperties.TestTag in p.config) p.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] else null }
    }

    private fun ecranPrincipal(r: String, rtl: Boolean = false) {
        route.value = r
        mount(rtl)
        assertEquals("content-a", focusedTag())
        back()
        assertTrue("$r : le menu est ouvert", menu.expanded)
        assertEquals("$r : focus sur l'entrée de l'écran", "rail-$r", focusedTag())
        assertEquals("$r : pas de sortie", 0, exits)
        assertEquals(0, toHome)
    }

    @Test fun retour_depuisLAccueil_ouvreLeMenu_focusSurAccueil_sansSortir() = ecranPrincipal(Routes.HOME)
    @Test fun retour_depuisLeGuide_ouvreLeMenu_focusSurGuide() = ecranPrincipal(Routes.GUIDE)
    @Test fun retour_depuisLesFilms_ouvreLeMenu_focusSurFilms() = ecranPrincipal(Routes.MOVIES)
    @Test fun retour_depuisLesSeries_ouvreLeMenu_focusSurSeries() = ecranPrincipal(Routes.SERIES)
    @Test fun retour_depuisLeDirect_ouvreLeMenu_focusSurDirect() = ecranPrincipal(Routes.LIVE)
    @Test fun retour_enArabe_ouvreLeMenu_focusSurAccueil() = ecranPrincipal(Routes.HOME, rtl = true)

    @Test fun retour_menuOuvertSurLAccueil_suitLaRegleDeSortieActuelle() {
        ecranPrincipal(Routes.HOME)
        back()
        assertEquals("règle de sortie : le dernier recours de l'activité reçoit Retour", 1, exits)
        assertTrue("le menu reste ouvert", menu.expanded)
        assertEquals(0, toHome)
    }

    @Test fun retour_menuOuvertSurUnAutreEcran_reviensAAccueil_sansSortie() {
        ecranPrincipal(Routes.GUIDE)
        back()
        assertEquals("retour à l'accueil (comportement actuel du menu ouvert)", 1, toHome)
        assertEquals(0, exits)
    }

    @Test fun retour_avecUneFenetreOuverte_lafenetreSeFerme_lemenuNOuvrePas() {
        route.value = Routes.GUIDE
        mount()
        rule.runOnUiThread { modalOpen.value = true }
        rule.waitForIdle()
        back()
        assertFalse("la fenêtre est fermée", modalOpen.value)
        assertFalse("le menu ne s'ouvre pas", menu.expanded)
        assertEquals("content-a", focusedTag())
        assertEquals(0, exits)
        back()
        assertTrue("Retour suivant : plus rien à fermer, le menu s'ouvre", menu.expanded)
    }

    @Test fun retour_avecUnSousNiveauOuvert_leSousNiveauSeFerme_lemenuNOuvrePas() {
        route.value = Routes.MOVIES
        mount()
        rule.runOnUiThread { subLevel.value = true }
        rule.waitForIdle()
        back()
        assertFalse(subLevel.value)
        assertFalse(menu.expanded)
        assertEquals(0, exits)
    }
}
