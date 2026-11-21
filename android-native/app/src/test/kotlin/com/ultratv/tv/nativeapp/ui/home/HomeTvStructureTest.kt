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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.ultratv.tv.nativeapp.ui.design.designT
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
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
import org.junit.Before
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

    @Before fun fakeImages() { installFakeImageLoader() }

    @After fun resetTheme() { Ux.themeLight = false; resetImageLoader() }

    private val heroWithBackdrop = HeroItem(
        kind = HeroItem.Kind.MOVIE, id = 1, title = "Orbite basse", year = 2024, genre = "Science-fiction", rating = 7.8,
        backdrop = "https://exemple.invalid/fond.jpg", poster = null, plot = "Six heures pour ramener tout l'équipage sur Terre.", runtimeMin = 126,
    )
    private val heroPosterOnly = heroWithBackdrop.copy(backdrop = null, poster = "https://exemple.invalid/affiche.jpg")

    private fun rows(layout: HomeLayout): HomeRows {
        fun ch(i: Int) = ChannelEntity(id = i.toLong(), providerId = 1, remoteId = "$i", name = "Nova $i", logo = null, categoryId = null, streamUrl = "u", num = i)
        fun resume(i: Int) = ResumeCardUi(
            key = "r$i", entry = WatchHistoryEntity(providerId = 1, kind = "MOVIE", remoteId = "$i", title = "Film $i", poster = null, streamUrl = "u"),
            title = "Film $i", progress = 0.4f, minutesLeft = 30,
        )
        return HomeRows(
            // Trois chaînes : deux avec programme en cours, une sans (la carte montre alors seulement la chaîne).
            channels = listOf(ChannelCardUi(ch(1), "Esprits criminels", "20:00 – 21:00", 0.6f), ChannelCardUi(ch(2), "Le grand journal", "20:30 – 21:30", 0.3f), ChannelCardUi(ch(3), null)),
            resume = listOf(resume(1), resume(2)), recentChannels = listOf(resume(3)),
            newEpisodes = listOf(NewEpisodeCardUi(21, "Les Gardiens du phare", null, 2023, 8.1, 2, 7, 0L, aired = true)),
            shortcuts = shortcutsOf(HomeCounts(channels = 1240, movies = 30435, series = 4028, favorites = 12), emptyList(), null, null),
            layout = layout,
        )
    }

    /** Titre « à la une » courant : modifiable depuis un test pour simuler la rotation automatique. */
    private val heroSource = mutableStateOf<HeroItem?>(null)

    /** « Ma liste » : titres à la une déjà dans les favoris, et les bascules demandées par le bouton rond. */
    private val inList = mutableStateOf<Set<String>>(emptySet())
    private val toggled = mutableListOf<HeroItem>()

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
                // Comme l'écran réel : le bandeau ne reprend plus le focus une fois qu'il l'a eu.
                val done = remember { mutableStateOf(heroFocusDone) }
                val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
                val extras = remember { mutableStateOf(emptyMap<String, HeroExtras>()) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    val current = heroSource.value
                    HomeTvContent(
                        slot = if (current != null) HomeTopSlot.HERO else HomeTopSlot.NONE, hero = current, heroIndex = 0, heroCount = 1, heroExtras = extras,
                        rows = data, providerCount = 3, clock = clock, sportOn = false, sportLive = null,
                        heroFocusDone = done.value, onHeroFocused = { done.value = true },
                        playHistory = {}, openPoster = {}, playChannel = {}, onShortcut = {}, onGoSport = {},
                        onWatchHero = {}, onInfoHero = {},
                        heroInList = inList, onToggleHeroList = { h -> toggled += h; inList.value = if (h.key in inList.value) inList.value - h.key else inList.value + h.key },
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

    // ───────────── « Ma liste » sur l'image ─────────────

    @Test fun maListe_droiteDepuisInfo_atteintLeBoutonRond_gaucheRevient() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionRight); press(Key.DirectionRight)
        rule.onNodeWithTag("hero-list").assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithTag("hero-info").assertIsFocused()
    }

    @Test fun maListe_droiteDepuisLeBoutonRond_neFaitRienPerdre() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionRight); press(Key.DirectionRight); press(Key.DirectionRight)
        rule.onNodeWithTag("hero-list").assertIsFocused()
    }

    @Test fun maListe_bas_vaSurPremiereRangee() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionRight); press(Key.DirectionRight); press(Key.DirectionDown)
        assertFocusInFirstRow()
    }

    @Test fun maListe_enArabe_suitLOrdreDeLecture() {
        setHome(hero = heroWithBackdrop, lang = AppLang.Arabic)
        val info = rule.onNodeWithTag("hero-info").getUnclippedBoundsInRoot()
        val list = rule.onNodeWithTag("hero-list").getUnclippedBoundsInRoot()
        assertTrue("en RTL le bouton rond est à gauche de « Plus d'infos »", list.left.value < info.left.value)
        press(Key.DirectionLeft); press(Key.DirectionLeft)
        rule.onNodeWithTag("hero-list").assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithTag("hero-list").assertIsFocused()
        press(Key.DirectionRight); rule.onNodeWithTag("hero-info").assertIsFocused()
    }

    @Test fun maListe_ok_appelleLaBascule_etGardeLeFocus() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionRight); press(Key.DirectionRight)
        press(Key.Enter)
        assertEquals(listOf(heroWithBackdrop), toggled)
        rule.onNodeWithTag("hero-list").assertIsFocused()
        press(Key.Enter)
        assertEquals(2, toggled.size)
        rule.onNodeWithTag("hero-list").assertIsFocused()
    }

    @Test fun maListe_rondDe76_memeHauteurQueLesAutresBoutons() {
        setHome(hero = heroWithBackdrop)
        val info = rule.onNodeWithTag("hero-info").getUnclippedBoundsInRoot()
        val list = rule.onNodeWithTag("hero-list").getUnclippedBoundsInRoot()
        assertEquals(info.height.value, list.height.value, 0.5f)
        assertEquals(list.height.value, list.width.value, 0.5f)
    }

    @Test fun maListe_descriptionLueParLesLecteursDEcran() {
        setHome(hero = heroWithBackdrop)
        val node = rule.onNodeWithTag("hero-list").fetchSemanticsNode()
        val desc = node.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
        assertEquals(listOf("Ma liste"), desc)
    }

    // ───────────── Rangée « En direct maintenant » en vignettes larges ─────────────

    @Test fun direct_premiereCarte_392Par220() {
        setHome(hero = heroWithBackdrop)
        val b = rule.onNodeWithTag("home-channel-1").getUnclippedBoundsInRoot()
        assertEquals(392.design.value, b.width.value, 1f)
        assertEquals(220.designT.value, b.height.value, 1f)
    }

    @Test fun direct_bas_depuisChaqueBouton_vaSurLaPremiereCarte() {
        setHome(hero = heroWithBackdrop)
        rule.onNodeWithTag("hero-watch").assertIsFocused()
        press(Key.DirectionDown); rule.onNodeWithTag("home-channel-1").assertIsFocused()
        press(Key.DirectionUp); rule.onNodeWithTag("hero-watch").assertIsFocused()
        press(Key.DirectionRight); rule.onNodeWithTag("hero-info").assertIsFocused()
        press(Key.DirectionDown); rule.onNodeWithTag("home-channel-1").assertIsFocused()
        press(Key.DirectionUp); press(Key.DirectionRight); press(Key.DirectionRight); rule.onNodeWithTag("hero-list").assertIsFocused()
        press(Key.DirectionDown); rule.onNodeWithTag("home-channel-1").assertIsFocused()
    }

    @Test fun direct_droite_uneCarteALaFois_jusqua_ToutVoir_puisRien_etGaucheRevient() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionDown)
        press(Key.DirectionRight); rule.onNodeWithTag("home-channel-2").assertIsFocused()
        press(Key.DirectionRight); rule.onNodeWithTag("home-channel-3").assertIsFocused()
        press(Key.DirectionRight); rule.onNodeWithTag("home-channel-see-all").assertIsFocused()
        press(Key.DirectionRight); rule.onNodeWithTag("home-channel-see-all").assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithTag("home-channel-3").assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithTag("home-channel-2").assertIsFocused()
        press(Key.DirectionLeft); rule.onNodeWithTag("home-channel-1").assertIsFocused()
    }

    @Test fun direct_haut_depuisNImporteQuelleCarte_revientSurRegarder() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionDown); press(Key.DirectionRight); press(Key.DirectionRight); press(Key.DirectionRight)
        rule.onNodeWithTag("home-channel-see-all").assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithTag("hero-watch").assertIsFocused()
    }

    @Test fun direct_carteFocalisee_entierementVisible_etToutVoirAMemeHauteur() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionDown)
        val first = rule.onNodeWithTag("home-channel-1").getUnclippedBoundsInRoot()
        val seeAllHeight = rule.onNodeWithTag("home-channel-see-all").getUnclippedBoundsInRoot().height.value
        assertEquals(first.height.value, seeAllHeight, 1f)
        val screen = rule.onRoot().getUnclippedBoundsInRoot()
        repeat(5) {
            val f = rule.onNode(isFocused()).getUnclippedBoundsInRoot()
            val row = rule.onNodeWithTag("home-channel-row").getUnclippedBoundsInRoot()
            // Agrandissement de 6 % : 3 % de la taille de chaque côté (les bornes non rognées l'ignorent).
            val mx = f.width.value * 0.03f; val my = f.height.value * 0.03f
            val l = f.left.value - mx; val r = f.right.value + mx; val t = f.top.value - my; val b = f.bottom.value + my
            assertTrue("carte agrandie coupée à gauche : $l", l >= screen.left.value && l >= row.left.value)
            assertTrue("carte agrandie coupée à droite : $r (écran ${screen.right})", r <= screen.right.value && r <= row.right.value)
            assertTrue("carte agrandie coupée en haut : $t", t >= row.top.value && t >= screen.top.value)
            assertTrue("carte agrandie coupée en bas : $b (écran ${screen.bottom})", b <= row.bottom.value && b <= screen.bottom.value)
            press(Key.DirectionRight)
        }
    }

    @Test fun direct_enArabe_suitLOrdreDeLecture() {
        setHome(hero = heroWithBackdrop, lang = AppLang.Arabic)
        press(Key.DirectionDown); rule.onNodeWithTag("home-channel-1").assertIsFocused()
        val c1 = rule.onNodeWithTag("home-channel-1").getUnclippedBoundsInRoot()
        val c2 = rule.onNodeWithTag("home-channel-2").getUnclippedBoundsInRoot()
        assertTrue("en RTL la carte 2 est à gauche de la carte 1", c2.left.value < c1.left.value)
        press(Key.DirectionLeft); rule.onNodeWithTag("home-channel-2").assertIsFocused()
        press(Key.DirectionRight); rule.onNodeWithTag("home-channel-1").assertIsFocused()
    }

    @Test fun direct_carteAvecProgramme_aSaProgression_carteSansProgramme_non() {
        setHome(hero = heroWithBackdrop)
        val fill = hasTestTag(com.ultratv.tv.nativeapp.ui.design.PROGRESS_FILL_TAG)
        rule.onNode(fill and hasAnyAncestor(hasTestTag("home-channel-1")), useUnmergedTree = true).assertExists()
        rule.onNode(fill and hasAnyAncestor(hasTestTag("home-channel-3")), useUnmergedTree = true).assertDoesNotExist()
        // La carte sans programme montre quand même la chaîne.
        rule.onNode(hasText("Nova 3") and hasAnyAncestor(hasTestTag("home-channel-3")), useUnmergedTree = true).assertExists()
    }

    @Test fun direct_carteAvecProgramme_montreBadge_chaine_titre_horaire() {
        setHome(hero = heroWithBackdrop)
        val inCard = hasAnyAncestor(hasTestTag("home-channel-1"))
        rule.onNode(hasText("EN DIRECT") and inCard, useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Nova 1") and inCard, useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Esprits criminels") and inCard, useUnmergedTree = true).assertExists()
        rule.onNode(hasText("20:00 – 21:00") and inCard, useUnmergedTree = true).assertExists()
    }

    // ───────────── Navigation : cas limites de la rangée ─────────────

    @Test fun direct_sansImageALaUne_haut_gardeLeFocusSurLaRangee() {
        setHome(hero = null)
        rule.onNodeWithTag("home-channel-2").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("home-channel-2").assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithTag("home-channel-2").assertIsFocused()
    }

    @Test fun direct_memoireDeRangee_basPuisHaut_revientSurLaMemeCarte() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionDown); press(Key.DirectionRight); press(Key.DirectionRight)
        rule.onNodeWithTag("home-channel-3").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithTag("home-channel-3").assertIsNotFocused()
        press(Key.DirectionUp)
        rule.onNodeWithTag("home-channel-3").assertIsFocused()
    }

    /** Le montage de test n'a pas de menu latéral : ◀ sur la première carte laisse le focus sur elle. */
    @Test fun direct_gaucheSurLaPremiereCarte_resteSurElle_sansMenuLateral() {
        setHome(hero = heroWithBackdrop)
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        rule.onNodeWithTag("home-channel-1").assertIsFocused()
    }

    @Test fun direct_haut_depuisUneRangeeBasse_remonteJusquaRegarder_sansBlocage() {
        setHome(hero = heroWithBackdrop)
        // Descend jusqu'à la dernière rangée : le bandeau n'est plus composé quand l'accueil a assez défilé.
        repeat(6) { press(Key.DirectionDown) }
        rule.onNodeWithTag("hero-watch").assertDoesNotExist()
        var last = rule.onNode(isFocused()).fetchSemanticsNode().id
        val trace = mutableListOf<Int>()
        for (i in 1..12) {
            press(Key.DirectionUp)
            if (rule.onAllNodes(hasTestTag("hero-watch")).fetchSemanticsNodes().isNotEmpty() && runCatching { rule.onNodeWithTag("hero-watch").assertIsFocused() }.isSuccess) break
            val now = rule.onNode(isFocused()).fetchSemanticsNode().id
            trace += now
            assertTrue("le focus est resté bloqué au pas $i (éléments : $trace)", now != last)
            last = now
        }
        rule.onNodeWithTag("hero-watch").assertIsFocused()
    }

    @Test fun direct_pisteDeProgression_estUnJetonDuTheme_pasLeBlancParDefaut() {
        for (light in listOf(false, true)) {
            Ux.themeLight = light
            val t = channelTrackColor()
            org.junit.Assert.assertNotEquals("piste = blanc par défaut (thème clair : $light)", androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f), t)
            assertEquals(Ux.Text.copy(alpha = 0.10f), t)
        }
    }
}
