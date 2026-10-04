package com.ultratv.tv.nativeapp.ui.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La coque tactile selon la taille de fenêtre : barre d'onglets (téléphone), rail (tablette), rien sur le lecteur. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class MobileNavTest {
    @get:Rule val rule = createComposeRule()

    private var nav: NavHostController? = null

    private fun host(layout: NavLayout, fullscreen: Boolean = false, start: String = "home", onProfile: () -> Unit = {}) {
        rule.setContent {
            val s = stringsFor(AppLang.French)
            CompositionLocalProvider(
                LocalStrings provides s,
                com.ultratv.tv.nativeapp.i18n.LocalDs provides com.ultratv.tv.nativeapp.i18n.DesignStrings(AppLang.French),
                LocalMobileStrings provides MobileStrings.FR,
                LocalTouch provides true,
            ) {
                val c = rememberNavController()
                nav = c
                MobileScaffold(layout, c, fullscreen, ProfileChip("K", 0xFFD91E2B.toInt()), onProfile) {
                    NavHost(c, startDestination = start) {
                        for (r in listOf("home", "live", "guide", "movies", "series", "search", "favorites", "recordings", "settings"))
                            composable(r) { Box(Modifier.fillMaxSize().testTag("screen-$r")) { Text(r) } }
                        composable("player?url={url}&title={title}") { Box(Modifier.fillMaxSize().testTag("screen-player")) }
                    }
                }
            }
        }
    }

    @Test fun compact_afficheLesCinqOnglets_etNavigue() {
        host(NavLayout.BOTTOM_TABS)
        for (label in listOf("Accueil", "Direct", "Guide", "Films", "Plus")) rule.onNodeWithText(label).assertIsDisplayed()
        rule.onNodeWithText("Accueil").assertIsSelected()
        rule.onNodeWithText("Direct").performClick()
        rule.waitForIdle()
        assertEquals("live", nav!!.currentDestination?.route)
        rule.onNodeWithText("Direct").assertIsSelected()
    }

    @Test fun compact_plusOuvreLaFeuille_aveclesSixDestinations() {
        host(NavLayout.BOTTOM_TABS)
        rule.onNodeWithText("Plus").performClick()
        for (label in listOf("Séries", "Recherche", "Favoris", "Enregistrements", "Réglages", "Profils")) rule.onNodeWithText(label).assertIsDisplayed()
        rule.onNodeWithText("Réglages").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("settings", nav!!.currentDestination?.route)
        rule.onNodeWithText("Plus").assertIsSelected()
    }

    @Test fun compact_profilsDeLaFeuille_appelleLeChangementDeProfil() {
        var asked = false
        host(NavLayout.BOTTOM_TABS, onProfile = { asked = true })
        rule.onNodeWithText("Plus").performClick()
        rule.onNodeWithText("Profils").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertTrue(asked)
    }

    @Test fun compact_surLeLecteur_aucuneBarre() {
        host(NavLayout.BOTTOM_TABS, fullscreen = true)
        assertTrue(rule.onAllNodesWithTextCount("Accueil") == 0)
    }

    @Test fun tablette_railLateral_aToutesLesEntrees_etPasDeBarreDuBas() {
        host(NavLayout.SIDE_RAIL)
        for (label in listOf("Accueil", "Direct", "Guide", "Films", "Séries", "Recherche", "Favoris", "Enregistrements", "Réglages")) rule.onNodeWithText(label).assertExists()
        assertFalse(rule.onAllNodesWithTextCount("Plus") > 0)
    }

    @Test fun focusSurface_tactile_cibleDAuMoins48dp() {
        rule.setContent {
            CompositionLocalProvider(LocalTouch provides true) {
                FocusSurface(onClick = {}, modifier = Modifier.testTag("petit").size(20.dp)) { }
            }
        }
        rule.onNodeWithTag("petit").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(t: String): Int =
    this.onAllNodesWithText(t).fetchSemanticsNodes().size
