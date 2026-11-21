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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertHasClickAction
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
import com.ultratv.tv.nativeapp.ui.catalog.PosterItem
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.i18n.extNewEpisodes
import com.ultratv.tv.nativeapp.i18n.homeTop10
import com.ultratv.tv.nativeapp.i18n.sportLiveNow
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
            // Top 10 (dix affiches) et « Derniers films » (six affiches) : de quoi tester les rangées d'affiches.
            top10 = (1..10).map { posterUi("t", it) }, latestMovies = (1..6).map { posterUi("m", it) },
            layout = layout,
        )
    }

    private fun posterUi(prefix: String, i: Int) = PosterCardUi(
        key = "$prefix$i", item = PosterItem(id = i + if (prefix == "t") 100L else 0L, title = "Titre $prefix$i", poster = null, year = 2024, rating = 7.5),
        isShow = false, kind = com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE,
    )

    /** Titres des rangées, lus dans la composition (les libellés sont des propriétés composables). */
    private var titles = emptyMap<HomeSectionId, String>()

    /** Rangées montrées : modifiables depuis un test (remplacement des données d'une rangée). */
    private val rowsState = mutableStateOf(HomeRows.EMPTY)

    /** Titre « à la une » courant : modifiable depuis un test pour simuler la rotation automatique. */
    private val heroSource = mutableStateOf<HeroItem?>(null)

    /** « Ma liste » : titres à la une déjà dans les favoris, et les bascules demandées par le bouton rond. */
    private val inList = mutableStateOf<Set<String>>(emptySet())
    private val toggled = mutableListOf<HeroItem>()

    private fun setHome(
        hero: HeroItem?, layout: HomeLayout = HomeLayout.defaultFor(true), lang: AppLang = AppLang.French, heroFocusDone: Boolean = false,
        lowRam: Boolean = false, heroCount: Int = 1, animations: com.ultratv.tv.nativeapp.ui.design.Animations = com.ultratv.tv.nativeapp.ui.design.Animations.NORMAL,
    ) {
        heroSource.value = hero
        I18n.init(ApplicationProvider.getApplicationContext())
        rowsState.value = rows(layout)
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false,
                com.ultratv.tv.nativeapp.ui.common.LocalLowRam provides lowRam, com.ultratv.tv.nativeapp.ui.design.LocalAnimations provides animations,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                // Mode clavier (télécommande) : sans lui, les éléments cliquables ne prennent pas le focus.
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                // Comme l'écran réel : le bandeau ne reprend plus le focus une fois qu'il l'a eu.
                val done = remember { mutableStateOf(heroFocusDone) }
                val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
                val extras = remember { mutableStateOf(emptyMap<String, HeroExtras>()) }
                val D = LocalDs.current
                titles = mapOf(
                    HomeSectionId.CHANNELS to D.sportLiveNow, HomeSectionId.TOP10 to D.homeTop10, HomeSectionId.RESUME to D.continueWatching,
                    HomeSectionId.NEW_EPISODES to D.extNewEpisodes, HomeSectionId.RECENT_CHANNELS to D.recentlyWatched, HomeSectionId.LATEST_MOVIES to D.latestMovies,
                )
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    val current = heroSource.value
                    HomeTvContent(
                        slot = if (current != null) HomeTopSlot.HERO else HomeTopSlot.NONE, hero = current, heroIndex = 0, heroCount = heroCount, heroExtras = extras,
                        rows = rowsState.value, providerCount = 3, clock = clock, sportOn = false, sportLive = null,
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

    @Test fun pointsDePagination_seulementSiLeBandeauPeutDefiler() {
        setHome(hero = heroWithBackdrop, heroCount = 3)
        rule.onNodeWithTag("hero-dots").assertExists()
    }

    @Test fun pointsDePagination_absentsSurAppareilModeste_memeAvecPlusieursTitres() {
        setHome(hero = heroWithBackdrop, heroCount = 3, lowRam = true)
        rule.onNodeWithTag("hero-dots").assertDoesNotExist()
    }

    @Test fun pointsDePagination_absentsQuandUnSeulTitreEstPrepare() {
        setHome(hero = heroWithBackdrop, heroCount = 1)
        rule.onNodeWithTag("hero-dots").assertDoesNotExist()
    }

    @Test fun pointsDePagination_absentsQuandLesAnimationsSontReduites() {
        setHome(hero = heroWithBackdrop, heroCount = 3, animations = com.ultratv.tv.nativeapp.ui.design.Animations(com.ultratv.tv.nativeapp.ui.design.AnimationLevel.SUBTLE))
        rule.onNodeWithTag("hero-dots").assertDoesNotExist()
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

    // ───────────── Rangées d'affiches et Top 10 : mémoire de rangée, grand chiffre, focus à 10 % ─────────────

    /** Accueil avec « Derniers films » juste sous « En direct » : les deux rangées d'affiches sont composées dès le départ. */
    private fun setHomePosters(lang: AppLang = AppLang.French) {
        val base = HomeLayout.defaultFor(true)
        val layout = base.move(HomeBlock.LATEST_MOVIES, -(base.order.indexOf(HomeBlock.LATEST_MOVIES) - base.order.indexOf(HomeBlock.CHANNELS) - 1))
        // Sans image « À la une » : les trois premières rangées tiennent à l'écran, donc sont toutes composées.
        setHome(hero = null, layout = layout, lang = lang)
    }

    /** Élément focalisable d'une affiche d'accueil (le `testTag` est posé sur la cellule, pas sur la surface cliquable). */
    private fun poster(key: String) = rule.onNode(isFocusable() and hasAnyAncestor(hasTestTag("home-poster-$key")))

    private fun focusPoster(key: String) { poster(key).requestFocus(); rule.waitForIdle(); poster(key).assertIsFocused() }

    private fun tagStartsWith(prefix: String) = SemanticsMatcher("testTag commence par $prefix") {
        (SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].startsWith(prefix))
    }

    @Test fun affiches_memoireDeRangee_basPuisHaut_etHautPuisBas() {
        setHomePosters()
        focusPoster("m3")
        press(Key.DirectionDown); poster("m3").assertIsNotFocused()
        press(Key.DirectionUp); poster("m3").assertIsFocused()
        press(Key.DirectionUp); poster("m3").assertIsNotFocused()
        press(Key.DirectionDown); poster("m3").assertIsFocused()
    }

    @Test fun top10_memoireDeRangee_basPuisHaut_etHautPuisBas() {
        setHomePosters()
        focusPoster("t3")
        press(Key.DirectionDown); poster("t3").assertIsNotFocused()
        press(Key.DirectionUp); poster("t3").assertIsFocused()
        press(Key.DirectionUp); poster("t3").assertIsNotFocused()
        press(Key.DirectionDown); poster("t3").assertIsFocused()
    }

    @Test fun affiches_droite_uneALaFois_rienApresLaDerniere_gaucheRevient() {
        setHomePosters()
        focusPoster("m1")
        for (i in 2..6) { press(Key.DirectionRight); poster("m$i").assertIsFocused() }
        press(Key.DirectionRight); poster("m6").assertIsFocused()
        press(Key.DirectionLeft); poster("m5").assertIsFocused()
    }

    @Test fun top10_droite_uneALaFois_rienApresLaDerniere() {
        setHomePosters()
        focusPoster("t1")
        for (i in 2..10) { press(Key.DirectionRight); poster("t$i").assertIsFocused() }
        press(Key.DirectionRight); poster("t10").assertIsFocused()
    }

    @Test fun affiches_enArabe_suiventLOrdreDeLecture() {
        setHomePosters(AppLang.Arabic)
        val b1 = poster("m1").getUnclippedBoundsInRoot(); val b2 = poster("m2").getUnclippedBoundsInRoot()
        assertTrue("en RTL l'affiche 2 est à gauche de l'affiche 1", b2.left.value < b1.left.value)
        focusPoster("m1")
        press(Key.DirectionLeft); poster("m2").assertIsFocused()
        press(Key.DirectionRight); poster("m1").assertIsFocused()
        press(Key.DirectionRight); poster("m1").assertIsFocused()
    }

    @Test fun top10_rang_dixAuPlus_dansLOrdre_etJamaisFocalisable() {
        setHomePosters()
        val lefts = (1..4).map { rule.onNodeWithTag("top10-rank-$it").assert(!isFocusable()).getUnclippedBoundsInRoot().left.value }
        assertEquals("rangs dans l'ordre", lefts.sorted(), lefts)
        // Parcours de la rangée (les dernières cellules sont composées au défilement) : le focus ne se pose jamais sur un chiffre.
        focusPoster("t1")
        repeat(10) {
            rule.onNode(isFocused() and hasAnyAncestor(tagStartsWith("top10-rank-"))).assertDoesNotExist()
            press(Key.DirectionRight)
        }
        poster("t10").assertIsFocused()
        rule.onNodeWithTag("top10-rank-10").assertExists().assert(!isFocusable())
        rule.onNodeWithTag("top10-rank-11").assertDoesNotExist()
    }

    @Test fun top10_rang_estDecoratif_pourLesLecteursDEcran_etLAfficheResteActivable() {
        setHomePosters()
        val rank = rule.onNodeWithTag("top10-rank-1", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("le chiffre ne porte aucun texte lu", rank.children.all { SemanticsProperties.Text !in it.config })
        // L'affiche, elle, reste un élément d'accessibilité normal (activable).
        poster("t1").assertHasClickAction()
    }

    private fun assertRankSide(rtl: Boolean) {
        val rank = rule.onNodeWithTag("top10-rank-1").getUnclippedBoundsInRoot()
        val p = poster("t1").getUnclippedBoundsInRoot()
        if (rtl) assertTrue("en RTL le rang est à droite de l'affiche", rank.right.value > p.right.value && rank.left.value > p.left.value - 1f)
        else assertTrue("en LTR le rang est à gauche de l'affiche", rank.left.value < p.left.value && rank.right.value < p.right.value + 1f)
        // Affiche de 200 (en unités design).
        assertEquals(200.design.value, p.width.value, 1f)
    }

    @Test fun top10_rang_aGaucheDeLAffiche_enLTR() { setHomePosters(AppLang.French); assertRankSide(rtl = false) }

    @Test fun top10_rang_aDroiteDeLAffiche_enArabe() { setHomePosters(AppLang.Arabic); assertRankSide(rtl = true) }

    /** Bornes de l'élément focalisé agrandies de [scale] puis de l'anneau (10 de décalage + épaisseur, agrandis aussi). */
    private fun enlarged(scale: Float): List<Float> {
        val f = rule.onNode(isFocused()).getUnclippedBoundsInRoot()
        val mx = f.width.value * (scale - 1f) / 2f + 10.design.value * scale; val my = f.height.value * (scale - 1f) / 2f + 10.design.value * scale
        return listOf(f.left.value - mx, f.top.value - my, f.right.value + mx, f.bottom.value + my)
    }

    @Test fun affichesFocalisees_agrandiesDe10Pourcent_neRecouvrentPasLeTitre_neSontPasRognees() {
        setHomePosters()
        val screen = rule.onRoot().getUnclippedBoundsInRoot()
        for ((titleText, first, count, prefix) in listOf(Quad(titles.getValue(HomeSectionId.LATEST_MOVIES), "m1", 6, "m"), Quad(titles.getValue(HomeSectionId.TOP10), "t1", 10, "t"))) {
            focusPoster(first)
            for (i in 1..count) {
                poster("$prefix$i").assertIsFocused()
                val title = rule.onNode(hasText(titleText)).getUnclippedBoundsInRoot()
                val (l, t, r, b) = enlarged(1.10f)
                assertTrue("$prefix$i : recouvre le titre de la rangée (haut $t, bas du titre ${title.bottom})", t >= title.bottom.value)
                assertTrue("$prefix$i : coupée à gauche ($l)", l >= screen.left.value)
                assertTrue("$prefix$i : coupée à droite ($r / ${screen.right})", r <= screen.right.value)
                assertTrue("$prefix$i : coupée en bas ($b / ${screen.bottom})", b <= screen.bottom.value)
                if (i < count) press(Key.DirectionRight)
            }
        }
    }

    // ───────────── Rangées jamais rognées : marge de bord, des deux côtés, en LTR et en RTL ─────────────

    private fun cardOf(tag: String) = rule.onNode(isFocusable() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    /**
     * Parcourt une rangée de bout en bout (▶ puis ◀ en LTR, inversé en RTL) : la carte focalisée, agrandie de [scale] et de
     * son anneau, reste dans l'écran aux deux bords ; vers la fin de la rangée la carte ne recule jamais (pas de saut ni
     * d'oscillation du défilement) ; chaque touche déplace d'UNE carte.
     */
    private fun assertRowNeverClipped(block: HomeBlock, lang: AppLang, count: Int, scale: Float, tag: (Int) -> String, data: HomeRows.() -> HomeRows, rowTag: String? = null) {
        val base = HomeLayout.defaultFor(true)
        setHome(hero = null, layout = base.move(block, -base.order.indexOf(block)), lang = lang)
        rowsState.value = rowsState.value.data()
        rule.waitForIdle()
        val screen = rule.onRoot().getUnclippedBoundsInRoot()
        val rtl = lang.rtl
        val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight
        val backward = if (rtl) Key.DirectionRight else Key.DirectionLeft
        cardOf(tag(1)).requestFocus(); rule.waitForIdle(); cardOf(tag(1)).assertIsFocused()
        fun check(i: Int) {
            cardOf(tag(i)).assertIsFocused()
            val (l, t, r, b) = enlarged(scale)
            assertTrue("carte $i coupée en haut ($t)", t >= screen.top.value)
            assertTrue("carte $i coupée en bas ($b / ${screen.bottom})", b <= screen.bottom.value)
            if (rowTag != null) {
                val row = rule.onNodeWithTag(rowTag).getUnclippedBoundsInRoot()
                assertTrue("carte $i rognée en haut par la rangée ($t / ${row.top})", t >= row.top.value)
                assertTrue("carte $i rognée en bas par la rangée ($b / ${row.bottom})", b <= row.bottom.value)
            }
            assertTrue("carte $i coupée à gauche ($l)", l >= screen.left.value)
            assertTrue("carte $i coupée à droite ($r / ${screen.right})", r <= screen.right.value)
        }
        check(1)
        // Position de la carte dans le sens de la lecture : ne doit jamais reculer quand on avance.
        var last = Float.NEGATIVE_INFINITY
        for (i in 2..count) {
            press(forward); check(i)
            val f = rule.onNode(isFocused()).getUnclippedBoundsInRoot()
            val pos = if (rtl) -f.left.value else f.left.value
            assertTrue("carte $i : le défilement a reculé ($pos après $last)", pos >= last - 1f)
            last = pos
        }
        for (i in count - 1 downTo 1) { press(backward); check(i) }
    }

    private fun resumeRows(n: Int): HomeRows.() -> HomeRows = {
        copy(resume = (1..n).map { i ->
            ResumeCardUi(key = "r$i", entry = WatchHistoryEntity(providerId = 1, kind = "MOVIE", remoteId = "$i", title = "Film $i", poster = null, streamUrl = "u"), title = "Film $i", progress = 0.4f, minutesLeft = 30)
        })
    }

    private fun channelRows(n: Int): HomeRows.() -> HomeRows = {
        copy(channels = (1..n).map { i -> ChannelCardUi(ChannelEntity(id = i.toLong(), providerId = 1, remoteId = "$i", name = "Nova $i", logo = null, categoryId = null, streamUrl = "u", num = i), null) })
    }

    @Test fun rangeReprendre_jamaisRognee_enFrancais() = assertRowNeverClipped(HomeBlock.RESUME, AppLang.French, 8, 1.04f, { "home-resume-r$it" }, resumeRows(8), "home-resume-row")
    @Test fun rangeReprendre_jamaisRognee_enArabe() = assertRowNeverClipped(HomeBlock.RESUME, AppLang.Arabic, 8, 1.04f, { "home-resume-r$it" }, resumeRows(8), "home-resume-row")
    @Test fun rangeDirect_jamaisRognee_enFrancais() = assertRowNeverClipped(HomeBlock.CHANNELS, AppLang.French, 8, 1.06f, { "home-channel-$it" }, channelRows(8), "home-channel-row")
    @Test fun rangeDirect_jamaisRognee_enArabe() = assertRowNeverClipped(HomeBlock.CHANNELS, AppLang.Arabic, 8, 1.06f, { "home-channel-$it" }, channelRows(8), "home-channel-row")
    @Test fun rangeTop10_jamaisRognee_enFrancais() = assertRowNeverClipped(HomeBlock.TOP10, AppLang.French, 10, 1.10f, { "home-poster-t$it" }, { this })
    @Test fun rangeTop10_jamaisRognee_enArabe() = assertRowNeverClipped(HomeBlock.TOP10, AppLang.Arabic, 10, 1.10f, { "home-poster-t$it" }, { this })
    @Test fun rangeAffiches_jamaisRognee_enArabe() = assertRowNeverClipped(HomeBlock.LATEST_MOVIES, AppLang.Arabic, 6, 1.10f, { "home-poster-m$it" }, { this })

    private data class Quad(val a: String, val b: String, val c: Int, val d: String)

    @Test fun affiches_memoire_garde_uneCarteDisparue_neLaisseJamaisLaRangeeInatteignable() {
        setHomePosters()
        focusPoster("m3")
        press(Key.DirectionDown)
        poster("m3").assertIsNotFocused()
        // La rangée reçoit de nouvelles données : la carte quittée n'existe plus.
        rule.runOnIdle { rowsState.value = rowsState.value.copy(latestMovies = (1..4).map { posterUi("n", it) }) }
        rule.waitForIdle()
        poster("m3").assertDoesNotExist()
        press(Key.DirectionUp)
        rule.onNode(isFocused() and hasAnyAncestor(tagStartsWith("home-poster-n"))).assertExists()
        // Et la rangée reste atteignable dans les deux sens.
        press(Key.DirectionDown); press(Key.DirectionUp)
        rule.onNode(isFocused() and hasAnyAncestor(tagStartsWith("home-poster-n"))).assertExists()
    }

    @Test fun descente_complete_chaqueRangeeUneFois_unSeulFocus_toujoursVisible_puisRemontee() {
        setHome(hero = heroWithBackdrop)
        val expected = rowsState.value.order.map { id -> titles[id] ?: error("rangée imprévue $id") }
        val screen = rule.onRoot().getUnclippedBoundsInRoot()
        // Titre de la rangée d'un élément : le dernier titre connu dont le bas est au-dessus de l'élément.
        fun rowOfFocus(): String {
            val f = rule.onNode(isFocused()).getUnclippedBoundsInRoot()
            return expected.filter { t -> rule.onAllNodes(hasText(t)).fetchSemanticsNodes().isNotEmpty() }
                .map { t -> t to rule.onAllNodes(hasText(t))[0].getUnclippedBoundsInRoot() }
                .filter { (_, b) -> b.bottom.value <= f.top.value + 0.5f }.maxByOrNull { (_, b) -> b.bottom.value }!!.first
        }
        val seen = mutableListOf<String>()
        repeat(expected.size) {
            press(Key.DirectionDown)
            assertEquals("un seul élément focalisé", 1, rule.onAllNodes(isFocused()).fetchSemanticsNodes().size)
            val f = rule.onNode(isFocused()).getUnclippedBoundsInRoot()
            assertTrue("élément focalisé hors écran : $f", f.left.value >= screen.left.value && f.right.value <= screen.right.value && f.top.value >= screen.top.value && f.bottom.value <= screen.bottom.value + 0.5f)
            seen += rowOfFocus()
        }
        assertEquals("rangées visitées dans l'ordre d'affichage", expected, seen)
        val last = rule.onNode(isFocused()).fetchSemanticsNode().id
        press(Key.DirectionDown)
        assertEquals("▼ sur la dernière rangée ne change rien", last, rule.onNode(isFocused()).fetchSemanticsNode().id)
        repeat(expected.size) { press(Key.DirectionUp) }
        rule.onNodeWithTag("hero-watch").assertIsFocused()
    }
}
