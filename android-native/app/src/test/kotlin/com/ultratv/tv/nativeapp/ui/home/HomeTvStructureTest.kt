package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Structure de l'accueil TV : « À la une » en image plein écran (titre et boutons dessinés dessus), heure en haut à
 * droite, raccourcis en rangée seulement s'ils sont affichés. Même montage que [HomeRenderTest] (fenêtre 1920 × 1080 px).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeTvStructureTest {
    @get:Rule val rule = createComposeRule()

    @After fun resetTheme() { Ux.themeLight = false }

    private val heroWithBackdrop = HeroItem(
        kind = HeroItem.Kind.MOVIE, id = 1, title = "Orbite basse", year = 2024, genre = "Science-fiction", rating = 7.8,
        backdrop = "https://exemple.invalid/fond.jpg", poster = null, plot = "Six heures pour ramener tout l'équipage sur Terre.", runtimeMin = 126,
    )
    private val heroPosterOnly = heroWithBackdrop.copy(backdrop = null, poster = "https://exemple.invalid/affiche.jpg")

    private fun rows(layout: HomeLayout): HomeRows {
        val ch = ChannelEntity(id = 1, providerId = 1, remoteId = "1", name = "Nova 1", logo = null, categoryId = null, streamUrl = "u", num = 1)
        return HomeRows(
            channels = listOf(ChannelCardUi(ch, "Esprits criminels", "20:00 – 21:00", 0.6f)),
            shortcuts = shortcutsOf(HomeCounts(channels = 1240, movies = 30435, series = 4028, favorites = 12), emptyList(), null, null),
            layout = layout,
        )
    }

    /** Titre « à la une » courant : modifiable depuis un test pour simuler la rotation automatique. */
    private val heroSource = mutableStateOf<HeroItem?>(null)

    private fun setHome(
        hero: HeroItem?, layout: HomeLayout = HomeLayout.defaultFor(true), lang: AppLang = AppLang.French, heroFocusDone: Boolean = false,
    ) {
        heroSource.value = hero
        I18n.init(ApplicationProvider.getApplicationContext())
        val data = rows(layout)
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                // Mode clavier (télécommande) : sans lui, les éléments cliquables ne prennent pas le focus.
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
                val extras = remember { mutableStateOf(emptyMap<String, HeroExtras>()) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    val current = heroSource.value
                    HomeTvContent(
                        slot = if (current != null) HomeTopSlot.HERO else HomeTopSlot.NONE, hero = current, heroIndex = 0, heroCount = 1, heroExtras = extras,
                        rows = data, providerCount = 3, clock = clock, sportOn = false, sportLive = null,
                        heroFocusDone = heroFocusDone, onHeroFocused = {},
                        playHistory = {}, openPoster = {}, playChannel = {}, onShortcut = {}, onGoSport = {},
                        onWatchHero = {}, onInfoHero = {},
                        emptyTop = {}, sportRow = { _, _ -> },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test fun aLaUne_pleineLargeur_sansGrilleDeRaccourcis() {
        setHome(hero = heroWithBackdrop, layout = HomeLayout.defaultFor(true))
        val hero = rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot()
        // 600 unités « design » converties à l'échelle de la fenêtre de test (1 unité design = 0,5 dp).
        assertEquals(600.design.value, hero.height.value, 1f)
        // Image sur toute la largeur de la zone de contenu (960 dp de fenêtre).
        assertEquals(960f, hero.width.value, 1f)
        rule.onNodeWithTag("home-shortcuts").assertDoesNotExist()
        rule.onNodeWithTag("home-clock").assertExists()
        rule.onNodeWithTag("home-greeting").assertDoesNotExist()
        rule.onNodeWithTag("hero-watch").assertIsFocused()
    }

    @Test fun aLaUne_sansImagePaysage_afficheDansSaZone() {
        setHome(hero = heroPosterOnly)
        rule.onNodeWithTag("home-hero").assertExists()
        rule.onNodeWithTag("hero-watch").assertIsFocused()
    }

    @Test fun sansTitreALaUne_salutationEtRangees() {
        setHome(hero = null)
        rule.onNodeWithTag("home-hero").assertDoesNotExist()
        rule.onNodeWithTag("home-greeting").assertExists()
        rule.onNodeWithTag("home-clock").assertExists()
    }

    @Test fun titreTresLong_boutonsDansLImage() {
        setHome(hero = heroWithBackdrop.copy(title = "Titre ".repeat(30)))
        val hero = rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot()
        val watch = rule.onNodeWithTag("hero-watch").getUnclippedBoundsInRoot()
        assertTrue(watch.bottom <= hero.bottom && watch.top >= hero.top)
    }

    @Test fun raccourcisChoisis_affichesEnRangee() {
        setHome(hero = heroWithBackdrop, layout = HomeLayout.defaultFor(true).toggle(HomeBlock.SHORTCUTS))
        rule.onNodeWithTag("home-shortcuts").assertExists()
    }

    // ───────────── Navigation à la télécommande ─────────────

    private fun press(key: Key) {
        rule.onRoot().performKeyInput { keyDown(key); keyUp(key) }
        rule.waitForIdle()
    }

    /** Le focus est dans la première rangée : sous l'image, dans la fenêtre, jamais sur l'image ni sur l'heure. */
    private fun assertFocusInFirstRow() {
        val hero = rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot()
        val f = rule.onNode(isFocused()).getUnclippedBoundsInRoot()
        assertTrue("le focus doit être sous l'image (haut ${f.top} / bas image ${hero.bottom})", f.top.value >= hero.bottom.value - 8f)
        assertTrue("le focus doit rester à l'écran", f.top.value < 540f)
    }

    @Test fun boutons_droiteVaSurInfo_gaucheRevientSurRegarder() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionRight); rule.onNodeWithTag("hero-info").assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithTag("hero-watch").assertIsFocused()
    }

    @Test fun boutons_enArabe_ordreInverseCoherentAvecLaLecture() {
        setHome(hero = heroWithBackdrop, lang = AppLang.Arabic)
        val watch = rule.onNodeWithTag("hero-watch").getUnclippedBoundsInRoot()
        val info = rule.onNodeWithTag("hero-info").getUnclippedBoundsInRoot()
        assertTrue("en RTL « Regarder » est à droite de « Plus d'infos »", watch.left.value > info.left.value)
        press(Key.DirectionLeft); rule.onNodeWithTag("hero-info").assertIsFocused()
        press(Key.DirectionRight); rule.onNodeWithTag("hero-watch").assertIsFocused()
    }

    @Test fun bas_depuisRegarder_vaSurPremiereRangee() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionDown); assertFocusInFirstRow()
    }

    @Test fun bas_depuisInfo_vaSurPremiereRangee() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionRight); press(Key.DirectionDown); assertFocusInFirstRow()
    }

    @Test fun haut_depuisPremiereRangee_revientSurRegarder() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionDown); press(Key.DirectionUp)
        rule.onNodeWithTag("hero-watch").assertIsFocused()
    }

    @Test fun haut_depuisRegarder_neFaitRienPerdre() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionUp)
        rule.onNodeWithTag("hero-watch").assertIsFocused()
        // L'heure n'est pas focalisable : le focus ne peut pas s'y perdre.
        rule.onNodeWithTag("home-clock").assert(!isFocusable())
    }

    @Test fun rotation_duTitre_neVolePasLeFocus() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionRight)
        rule.runOnIdle { heroSource.value = heroWithBackdrop.copy(id = 2, title = "Marée haute") }
        rule.waitForIdle()
        rule.onNodeWithTag("hero-info").assertIsFocused()
    }

    @Test fun retourSurAccueil_leHeroNeVolePasLeFocus() {
        // heroFocusDone = vrai : l'accueil a déjà donné le focus à « Regarder » pendant cette visite.
        setHome(hero = heroWithBackdrop, heroFocusDone = true)
        rule.onNodeWithTag("hero-watch").assertIsNotFocused()
    }

    @Test fun elementsDecoratifs_pasFocalisables() {
        setHome(hero = heroWithBackdrop)
        rule.onNodeWithTag("home-clock").assert(!isFocusable())
        rule.onNodeWithTag("home-hero").assert(!isFocusable())
        assertFalse(rule.onNodeWithTag("home-clock").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Focused))
    }
}
