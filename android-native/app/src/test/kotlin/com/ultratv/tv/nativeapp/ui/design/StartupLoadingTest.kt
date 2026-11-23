package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Points de chargement : trois points allumés tour à tour, immobiles quand les animations sont réduites. Pur. */
class LoadingDotsTest {
    @Test fun troisPoints_cycleDe1200ms() {
        assertEquals(3, LoadingDots.COUNT)
        assertEquals(1200, LoadingDots.CYCLE_MS)
    }

    @Test fun chaquePointAtteintSonSommetAUnTiersDeCycleDuPrecedent() {
        for (i in 0 until LoadingDots.COUNT) {
            val peak = (i * LoadingDots.CYCLE_MS / LoadingDots.COUNT).toLong()
            assertEquals("point $i au sommet", 1f, LoadingDots.alpha(i, peak, animate = true), 0.001f)
            for (j in 0 until LoadingDots.COUNT) if (j != i) assertTrue("point $j éteint quand $i brille", LoadingDots.alpha(j, peak, animate = true) < 0.7f)
        }
    }

    @Test fun periodique_etBorne() {
        for (t in 0L..2400L step 50) for (i in 0 until 3) {
            val a = LoadingDots.alpha(i, t, true)
            assertTrue(a in LoadingDots.MIN_ALPHA..1f)
            assertEquals(a, LoadingDots.alpha(i, t + LoadingDots.CYCLE_MS, true), 0.001f)
        }
    }

    @Test fun animationsReduites_pointsImmobiles() {
        val a = (0L..2400L step 100).map { LoadingDots.alpha(1, it, animate = false) }.toSet()
        assertEquals(1, a.size)
    }

    @Test fun animation_seulementSiNiReduitesNiCoupees() {
        assertTrue(LoadingDots.animate(Animations(AnimationLevel.NORMAL), systemAnimatorScale = 1f))
        assertFalse(LoadingDots.animate(Animations(AnimationLevel.SUBTLE), systemAnimatorScale = 1f))
        assertFalse(LoadingDots.animate(Animations(AnimationLevel.OFF), systemAnimatorScale = 1f))
        assertFalse(LoadingDots.animate(Animations(AnimationLevel.NORMAL), systemAnimatorScale = 0f))
    }
}

/** Écran de chargement : le nom « Ultra TV » et les points, aucun logo (le logo est déjà sur l'écran système). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class StartupLoadingScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun nomDeLaMarque_etPointsDeChargement() {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(AppLang.French), LocalAnimations provides Animations(AnimationLevel.OFF)) {
                Box(Modifier.fillMaxSize()) { StartupLoadingScreen() }
            }
        }
        rule.onNodeWithTag(STARTUP_LOADING_TAG).assertIsDisplayed()
        rule.onNodeWithText("ULTRA", substring = true).assertIsDisplayed()
        rule.onNodeWithText("TV", substring = true).assertIsDisplayed()
        // Annoncé « Chargement… » aux lecteurs d'écran.
        val desc = rule.onNodeWithTag(STARTUP_LOADING_TAG).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(listOf(I18n.string(AppLang.French, com.ultratv.tv.nativeapp.R.string.ds_loading_label)), desc)
    }
}

/**
 * Démarrage (garde-fou sur la source) : un seul écran de chargement après l'écran système, sans second logo ni animation
 * d'ouverture posée par-dessus l'application (elle retardait l'accès à l'accueil de 700 ms).
 */
class StartupFlowSourceTest {
    private val main = File("src/main/kotlin/com/ultratv/tv/nativeapp/MainActivity.kt").readText()

    @Test fun ecranDeChargementUnique_sansSecondLogo() {
        val root = main.substringAfter("private fun Root(").substringBefore("private fun UltraTvAppRoot(")
        assertTrue("Root affiche StartupLoadingScreen", root.contains("StartupLoadingScreen("))
        assertFalse("pas d'animation d'ouverture par-dessus l'appli", root.contains("StartupAnimation("))
        assertFalse("pas de logo pendant le chargement", root.contains("LogoMark("))
        assertFalse("pas de fond vide pendant le chargement", root.contains("Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))"))
    }

    @Test fun ecranSystemeNonRetenu() {
        // L'écran système disparaît à la première image : aucune condition ne le garde affiché.
        assertFalse(main.contains("setKeepOnScreenCondition"))
    }

    @Test fun fondDeFenetre_identiqueAuFondDuChargement() {
        val colors = File("src/main/res/values/colors.xml").readText()
        val bg = Regex("""name="bg">#([0-9A-Fa-f]{6})<""").find(colors)!!.groupValues[1].uppercase()
        assertEquals(String.format("%06X", DarkPalette.bg.toArgb() and 0xFFFFFF), bg)
    }
}
