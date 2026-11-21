package com.ultratv.tv.nativeapp.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.ContentFocusMemory
import com.ultratv.tv.nativeapp.ui.common.CurrentScreenFocus
import com.ultratv.tv.nativeapp.ui.common.InputClock
import com.ultratv.tv.nativeapp.ui.common.LocalContentFocusMemory
import com.ultratv.tv.nativeapp.ui.common.ScreenFocusHost
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.sport.SportHomeRow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Parcours complets de l'accueil TV à la télécommande : logiques (l'ordre du focus suit l'ordre visuel, chaque direction
 * mène à un élément visible ou ne fait rien, jamais à un élément décoratif) et stables (le focus ne bouge jamais sans
 * touche, revient à l'élément quitté, le défilement le suit sans saut).
 *
 * Le montage reproduit la racine réelle de la TV : contenu décalé de la largeur du rail replié dans un groupe de
 * focus dont l'entrée est la mémoire du contenu ([ContentFocusMemory]), [ScreenFocusHost], menu latéral posé par-dessus
 * avec la même logique de focus que `SidebarNav`, et état sauvegardé par écran (comme le NavHost).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class HomeTvNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun fakeImages() { installFakeImageLoader() }

    @After fun resetTheme() { Ux.themeLight = false; resetImageLoader() }

    private val hero = HomeTvFixtures.heroWithBackdrop
    private val layout = HomeLayout.defaultFor(true)

    private val rowsState = mutableStateOf(HomeRows.EMPTY)
    private val heroSource = mutableStateOf<HeroItem?>(null)
    private val inList = mutableStateOf<Set<String>>(emptySet())
    /** Écran affiché : « home » ou « detail » (une fiche, comme après un OK sur une affiche). */
    private val screen = mutableStateOf("home")
    /** Passages du focus par le menu latéral. */
    private var railVisits = 0
    private var rtl = false
    private var longHome = false

    private val rowTagPrefixes = listOf("hero-watch", "hero-info", "hero-list", "home-channel-", "home-resume-", "home-poster-", "home-sport-", "rail-", "detail-button")

    // ───────────── Montage ─────────────

    /** Menu latéral : même logique de focus que SidebarNav (entrée sur la page active ; focus sans touche renvoyé à l'écran). */
    @Composable
    private fun Rail(modifier: Modifier) {
        val fm = LocalFocusManager.current
        val active = remember { FocusRequester() }
        val scope = rememberCoroutineScope()
        var hasFocus by remember { mutableStateOf(false) }
        Column(
            modifier.width(48.dp).fillMaxHeight()
                .focusProperties { enter = { active } }
                .focusGroup()
                .onFocusChanged { st ->
                    hasFocus = st.hasFocus
                    if (st.hasFocus) railVisits++
                    if (st.hasFocus && !InputClock.recentNavKey()) scope.launch {
                        androidx.compose.runtime.withFrameNanos { }
                        if (!hasFocus) return@launch
                        val back = CurrentScreenFocus.requester
                        if (back == null || !runCatching { back.requestFocus() }.getOrDefault(false)) fm.moveFocus(if (rtl) FocusDirection.Left else FocusDirection.Right)
                    }
                },
        ) {
            repeat(7) { i ->
                FocusSurface(onClick = {}, modifier = Modifier.width(48.dp).weight(1f).testTag("rail-$i").then(if (i == 0) Modifier.focusRequester(active) else Modifier)) { _ -> }
            }
        }
    }

    private fun sportEntries(n: Int): List<SportEntry> = (1..n).map { i ->
        val ch = ChannelEntity(id = 500L + i, providerId = 1, remoteId = "s$i", name = "Stade $i", logo = null, categoryId = "sport", streamUrl = "u", num = i)
        SportEntry(ch, EpgEntity(id = 900L + i, channelId = ch.id, title = "Équipe A - Équipe B $i", description = null, startMs = 0L, endMs = 7_200_000L), sportChannel = true, strict = true)
    }

    private fun mount(
        lang: AppLang = AppLang.French, withHero: Boolean = true, long: Boolean = false, withRail: Boolean = true, sport: Int = 0, heroCount: Int = 1,
    ) {
        rtl = lang.rtl; longHome = long
        I18n.init(ApplicationProvider.getApplicationContext())
        heroSource.value = if (withHero) hero else null
        rowsState.value = if (long) HomeTvFixtures.longRows(layout) else HomeTvFixtures.rows(layout)
        val sportList = sportEntries(sport)
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false,
                LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                val holder = rememberSaveableStateHolder()
                val contentFocus = remember { ContentFocusMemory() }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    // Comme UltraTvAppRoot (TV_RAIL) : contenu décalé de la largeur du rail replié, entrée = élément quitté.
                    Box(
                        Modifier.fillMaxSize().padding(start = if (withRail) 48.dp else 0.dp)
                            .focusProperties { enter = { contentFocus.target() } }.focusGroup(),
                    ) {
                        CompositionLocalProvider(LocalContentFocusMemory provides contentFocus) {
                            if (screen.value == "home") holder.SaveableStateProvider("home") {
                                ScreenFocusHost(Modifier.fillMaxSize()) { HomeScene(heroCount, sportList) }
                            } else holder.SaveableStateProvider("detail") {
                                ScreenFocusHost(Modifier.fillMaxSize()) {
                                    FocusSurface(onClick = {}, modifier = Modifier.size(200.dp, 60.dp).testTag("detail-button")) { _ -> }
                                }
                            }
                        }
                    }
                    if (withRail) Rail(Modifier.align(Alignment.TopStart))
                }
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun HomeScene(heroCount: Int, sportList: List<SportEntry>) {
        // Comme HomeScreen : « Regarder » ne prend le focus qu'une fois par visite (état sauvegardé dans l'entrée de navigation).
        var done by rememberSaveable { mutableStateOf(false) }
        val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
        val extras = remember { mutableStateOf(emptyMap<String, HeroExtras>()) }
        val current = heroSource.value
        HomeTvContent(
            slot = if (current != null) HomeTopSlot.HERO else HomeTopSlot.NONE, hero = current, heroIndex = 0, heroCount = heroCount, heroExtras = extras,
            rows = rowsState.value, providerCount = 3, clock = clock, sportOn = sportList.isNotEmpty(), sportLive = sportList.size.takeIf { it > 0 },
            heroFocusDone = done, onHeroFocused = { done = true },
            playHistory = {}, openPoster = {}, playChannel = {}, onShortcut = {}, onGoSport = {},
            onWatchHero = {}, onInfoHero = {},
            heroInList = inList, onToggleHeroList = {},
            emptyTop = {},
            sportRow = { m, p -> if (sportList.isNotEmpty()) SportHomeRow(sportList, clock, m, p, {}, {}) },
        )
    }

    // ───────────── Touches et constats ─────────────

    /** Appui complet (comme MainActivity.dispatchKeyEvent, qui horodate la touche dans InputClock). */
    private fun press(key: Key) {
        InputClock.lastKeyCode = key.nativeKeyCode
        InputClock.lastKeyMs = android.os.SystemClock.uptimeMillis()
        rule.onRoot().performKeyInput { keyDown(key); keyUp(key) }
        rule.waitForIdle()
    }

    private fun tagOf(n: SemanticsNode): String? =
        generateSequence(n) { it.parent }.firstNotNullOfOrNull { if (SemanticsProperties.TestTag in it.config) it.config[SemanticsProperties.TestTag] else null }

    /**
     * Constat valable à chaque pas : exactement UN élément focalisé, sur un élément interactif (jamais l'image, l'heure, un
     * grand chiffre du Top 10, un titre de rangée ni un point de pagination), entièrement dans l'écran. Rend son repère.
     */
    private fun focusNow(step: String): String {
        val nodes = rule.onAllNodes(isFocused()).fetchSemanticsNodes()
        assertEquals("$step : un seul élément focalisé", 1, nodes.size)
        val tag = tagOf(nodes[0])
        assertTrue("$step : focus sur un élément décoratif ou sans repère ($tag)", tag != null && !tag.endsWith("-row") && rowTagPrefixes.any { tag.startsWith(it) })
        if (!tag!!.startsWith("rail-")) {
            val screenBounds = rule.onRoot().getUnclippedBoundsInRoot()
            val f = rule.onNode(isFocused()).getUnclippedBoundsInRoot()
            assertTrue("$step ($tag) : élément focalisé hors écran : $f", f.left.value >= screenBounds.left.value && f.right.value <= screenBounds.right.value && f.top.value >= screenBounds.top.value && f.bottom.value <= screenBounds.bottom.value + 0.5f)
        }
        return tag
    }

    /** Rangée de l'élément focalisé : identité du conteneur « …-row » (le bandeau n'en a pas : -1). */
    private fun focusRow(): Int {
        val n = rule.onNode(isFocused()).fetchSemanticsNode()
        return generateSequence(n) { it.parent }
            .firstOrNull { SemanticsProperties.TestTag in it.config && it.config[SemanticsProperties.TestTag].endsWith("-row") }?.id ?: -1
    }

    /**
     * Un pas de télécommande. ◀ / ▶ restent dans LA RANGÉE : le focus ne change jamais de rangée (hors menu latéral) ; un
     * saut vers une carte d'une autre rangée, proche ou non, visible ou non, est un défaut de navigation.
     */
    private fun step(key: Key, label: String): String {
        val horizontal = key == Key.DirectionLeft || key == Key.DirectionRight
        val beforeTag = if (horizontal) focusNow("$label (avant)") else ""
        val before = if (horizontal) focusRow() else 0
        press(key)
        val tag = focusNow("$label ($key)")
        if (horizontal && !tag.startsWith("rail-") && !beforeTag.startsWith("rail-")) {
            assertEquals("$label : $key depuis $beforeTag a sauté vers une autre rangée ($tag)", before, focusRow())
        }
        return tag
    }

    private val forward get() = if (rtl) Key.DirectionLeft else Key.DirectionRight
    private val backward get() = if (rtl) Key.DirectionRight else Key.DirectionLeft

    /** Descend d'une rangée à la fois jusqu'à ce que le focus soit sur un repère commençant par [prefix] (au plus [max] pas). */
    private fun downTo(prefix: String, max: Int = 14): String {
        var tag = focusNow("départ")
        var n = 0
        while (!tag.startsWith(prefix)) { check(n++ < max) { "jamais arrivé sur $prefix (dernier : $tag)" }; tag = step(Key.DirectionDown, "vers $prefix") }
        return tag
    }

    private fun focusByTag(tag: String) {
        rule.onNode(isFocusable() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)))).requestFocus()
        rule.waitForIdle()
        assertEquals(tag, focusNow("pose du focus"))
    }

    // ───────────── Descente et remontée sur un accueil long ─────────────

    @Test fun accueilLong_descenteJusquaLaFin_puisRemonteeRangeeParRange_finSurRegarder() {
        mount(long = true, heroCount = 3)
        assertEquals("hero-watch", focusNow("départ"))
        val down = mutableListOf("hero-watch")
        var disposedHero = false
        var last = "hero-watch"
        // Descend tant que le focus change ; la dernière rangée est atteinte quand ▼ ne change plus rien.
        for (i in 1..40) {
            val t = step(Key.DirectionDown, "descente $i")
            if (rule.onAllNodes(hasTestTag("hero-watch")).fetchSemanticsNodes().isEmpty()) disposedHero = true
            if (t == last) break
            down += t; last = t
        }
        assertTrue("l'accueil long doit faire sortir le bandeau de la composition", disposedHero)
        assertTrue("au moins huit rangées parcourues (${down.size - 1})", down.size - 1 >= 8)
        assertEquals("▼ sur la dernière rangée ne change rien", last, step(Key.DirectionDown, "fond"))
        // Remontée : chaque ▲ revient sur la carte quittée à la descente (mémoire de rangée), jusqu'à « Regarder ».
        for (expected in down.dropLast(1).reversed()) assertEquals("remontée", expected, step(Key.DirectionUp, "remontée"))
        assertEquals("hero-watch", focusNow("fin"))
        assertEquals("▲ depuis « Regarder » ne change rien", "hero-watch", step(Key.DirectionUp, "sommet"))
        // Le bandeau est recomposé et la liste est revenue en haut.
        rule.onNodeWithTagExists("home-hero")
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithTagExists(tag: String) {
        assertTrue("$tag doit être composé", onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty())
    }

    private fun toutesLesRangeesDeBoutEnBout(lang: AppLang) {
        mount(lang, long = true, heroCount = 3)
        var tag = focusNow("départ")
        // Boutons de l'image : dans l'ordre de lecture, rien de plus au bout.
        assertEquals("hero-info", step(forward, "boutons"))
        assertEquals("hero-list", step(forward, "boutons"))
        assertEquals("hero-list", step(forward, "fin des boutons"))
        repeat(2) { step(backward, "boutons") }
        assertEquals("hero-watch", focusNow("retour"))
        var rows = 0
        for (i in 1..40) {
            val before = tag
            tag = step(Key.DirectionDown, "rangée $i")
            if (tag == before) break
            rows++
            // Avance jusqu'au bout de la rangée (jamais sur un chiffre du Top 10) puis revient carte par carte au début.
            val trail = mutableListOf(tag)
            for (k in 1..16) { val t = step(forward, "avance"); if (t == trail.last()) break; trail += t }
            for (expected in trail.dropLast(1).reversed()) assertEquals("retour carte par carte", expected, step(backward, "recul"))
            assertEquals(tag, focusNow("début de rangée"))
        }
        assertTrue("rangées parcourues : $rows", rows >= 9)
    }

    @Test fun accueilLong_toutesLesRangees_deBoutEnBout_enFrancais_rienDeDecoratif() = toutesLesRangeesDeBoutEnBout(AppLang.French)

    @Test fun accueilLong_toutesLesRangees_deBoutEnBout_enArabe_rienDeDecoratif() = toutesLesRangeesDeBoutEnBout(AppLang.Arabic)

    // ───────────── Menu latéral ─────────────

    private fun railRoundTripOnEveryRow(lang: AppLang) {
        mount(lang, long = true, sport = 4)
        // Depuis le bandeau : ◀ (▶ en arabe) sort vers le menu, l'inverse revient sur « Regarder ».
        assertEquals("hero-watch", focusNow("départ"))
        val toRail = backward
        assertTrue("menu latéral", step(toRail, "bandeau vers le menu").startsWith("rail-"))
        assertEquals("hero-watch", step(forward, "menu vers le bandeau"))
        // Sur chaque rangée : première carte, ◀ jusqu'au menu, ▶ : retour sur la carte quittée (ni « Regarder », ni la première carte de l'écran).
        var tag = "hero-watch"
        var rows = 0
        for (i in 1..40) {
            val t = step(Key.DirectionDown, "descente $i")
            if (t == tag) break
            tag = t; rows++
            // Premier élément de la rangée : on recule jusqu'à la bordure.
            var first = tag
            for (k in 1..16) { val b = step(backward, "début de rangée"); if (b.startsWith("rail-")) break; first = b }
            assertEquals("la rangée de $tag doit mener au menu à son début", true, focusNow("menu").startsWith("rail-"))
            assertEquals("retour du menu sur la carte quittée", first, step(forward, "menu vers la rangée $first"))
            assertNotEquals("hero-watch", first);
            tag = first
        }
        assertTrue("rangées parcourues : $rows", rows >= 9)
    }

    @Test fun menuLateral_gaucheAuDebutDeChaqueRangee_vaSurLeMenu_droiteRevientSurLaCarteQuittee_enFrancais() = railRoundTripOnEveryRow(AppLang.French)

    @Test fun menuLateral_enArabe_directionsInversees_memeRetourSurLaCarteQuittee() = railRoundTripOnEveryRow(AppLang.Arabic)

    @Test fun menuLateral_apresUneCarteDuMilieu_retourSurLaMemeCarte_pasSurLaPremiere() {
        mount()
        downTo("home-poster-t")
        repeat(3) { step(Key.DirectionRight, "carte") }
        val left = focusNow("carte du milieu")
        repeat(3) { step(Key.DirectionLeft, "recul") }
        step(Key.DirectionLeft, "menu")
        assertTrue(focusNow("menu").startsWith("rail-"))
        // Le focus a quitté la rangée à sa première carte : c'est elle qui revient (ce n'est pas celle du milieu).
        val back = step(Key.DirectionRight, "retour")
        assertEquals("home-poster-t1", back)
        assertNotEquals(left, back)
    }

    // ───────────── Rangée Sport ─────────────

    @Test fun sport_basPuisHaut_etHautPuisBas_reviennentSurLaMemeCarte() {
        mount(sport = 5)
        downTo("home-channel-")
        val channelRowCard = step(forward, "carte du direct")
        val sport = step(Key.DirectionDown, "vers Sport")
        assertTrue("la rangée Sport vient juste sous le Direct ($sport)", sport.startsWith("home-sport-"))
        repeat(2) { step(forward, "carte Sport") }
        val chosen = focusNow("carte Sport choisie")
        assertNotEquals(sport, chosen)
        val below = step(Key.DirectionDown, "sous le Sport")
        assertTrue("sous le Sport : une autre rangée ($below)", !below.startsWith("home-sport-"))
        assertEquals("▲ : retour sur la carte Sport quittée", chosen, step(Key.DirectionUp, "retour Sport"))
        // ▲ encore : la rangée Direct, sur la carte quittée.
        assertEquals("retour au Direct sur la carte quittée", channelRowCard, step(Key.DirectionUp, "vers le Direct"))
        assertEquals("▼ : retour sur la carte Sport quittée", chosen, step(Key.DirectionDown, "retour au Sport"))
        // Bord de rangée : ▶ jusqu'à « Tout voir » puis rien de plus, ◀ revient carte par carte.
        for (i in 1..4) step(forward, "fin du Sport")
        assertEquals("home-sport-see-all", focusNow("tout voir"))
        assertEquals("home-sport-see-all", step(forward, "après tout voir"))
    }

    // ───────────── Données qui changent, retour sur l'accueil, Retour ─────────────

    @Test fun donneesQuiChangent_lAfficheFocaliseResteFocalisee_etLeFocusNeBougeJamaisSansTouche() {
        mount()
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        assertEquals("home-poster-m3", focusNow("avant"))
        // 1. Nouvelle instance de HomeRows, même contenu.
        rule.runOnIdle { rowsState.value = HomeTvFixtures.rows(layout) }; rule.waitForIdle()
        assertEquals("nouvelle instance de rangées", "home-poster-m3", focusNow("après remplacement"))
        // 2. Le titre à la une change.
        rule.runOnIdle { heroSource.value = hero.copy(id = 2, title = "Marée haute", backdrop = "https://exemple.invalid/autre.jpg") }; rule.waitForIdle()
        assertEquals("titre à la une changé", "home-poster-m3", focusNow("après rotation"))
        // 3. Les deux ensemble, avec une rangée AU-DESSUS qui change (une carte « Reprendre » de plus décale tout).
        rule.runOnIdle {
            heroSource.value = hero.copy(id = 3, title = "Cap au nord")
            rowsState.value = HomeTvFixtures.rows(layout).let { r -> r.copy(resume = r.resume + r.recentChannels) }
        }
        rule.waitForIdle()
        assertEquals("rangée du dessus changée", "home-poster-m3", focusNow("après changement du dessus"))
        // 4. Le titre à la une disparaît (liste vide) : les rangées restent, le focus aussi.
        rule.runOnIdle { heroSource.value = null }; rule.waitForIdle()
        assertEquals("sans titre à la une", "home-poster-m3", focusNow("sans bandeau"))
        // La navigation continue normalement ensuite.
        assertEquals("home-poster-m4", step(forward, "après les changements"))
        assertEquals("home-poster-m3", step(backward, "après les changements"))
    }

    @Test fun retourSurLAccueil_apresUneFiche_memeAfficheFocalisee_etRegarderNeLaReprendPas() {
        mount()
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        assertEquals("home-poster-m3", focusNow("avant"))
        // OK sur l'affiche : la fiche remplace l'accueil (l'accueil quitte la composition, son état est sauvegardé).
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.onNodeWithTagExists("detail-button")
        rule.runOnIdle { screen.value = "home" }; rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        assertEquals("retour de la fiche : la même affiche", "home-poster-m3", focusNow("après retour"))
        // Et la navigation reprend d'où on l'avait laissée.
        assertEquals("home-poster-m4", step(forward, "après retour"))
        var t = focusNow("avant remontée")
        for (i in 1..12) { if (t == "hero-watch") break; t = step(Key.DirectionUp, "remontée") }
        assertEquals("hero-watch", t)
    }

    @Test fun retourSurLAccueil_apresUnBoutonDeLImage_leFocusReste_surLImage_pasSurUneCarteOubliee() {
        mount()
        downTo("home-poster-m")
        // De retour en haut : la dernière carte visitée ne doit plus servir de cible (le focus a quitté les rangées).
        var t = focusNow("avant remontée")
        for (i in 1..12) { if (t == "hero-watch") break; t = step(Key.DirectionUp, "remontée") }
        assertEquals("hero-info", step(forward, "bouton"))
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.runOnIdle { screen.value = "home" }; rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        val back = focusNow("après retour")
        assertTrue("le focus revient sur l'image, pas sur une carte de rangée ($back)", back.startsWith("hero-"))
    }

    @Test fun retour_unSeulAppuiAuMilieuDesRangees_neBougeJamaisLeFocus_etArriveAuDernierRecours() {
        var backs = 0
        // Comme MainActivity : dernier recours enregistré AVANT l'interface (rappel d'arrière-plan « appui long pour quitter »).
        rule.activity.onBackPressedDispatcher.addCallback(rule.activity, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { backs++ } })
        mount()
        downTo("home-poster-t")
        repeat(2) { step(Key.DirectionRight, "carte") }
        val before = focusNow("avant Retour")
        val visits = railVisits
        InputClock.lastKeyCode = Key.Back.nativeKeyCode; InputClock.lastKeyMs = android.os.SystemClock.uptimeMillis()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        // Aucun écran d'accueil ni rangée n'intercepte Retour : il arrive tel quel au dernier recours de l'activité…
        assertEquals("Retour doit atteindre le dernier recours de l'activité, une seule fois", 1, backs)
        // … qui n'ouvre pas le menu, ne remonte pas en haut et ne quitte rien : le focus reste sur la carte.
        assertEquals("le focus ne bouge pas", before, focusNow("après Retour"))
        assertEquals("le menu latéral n'a pas pris le focus", visits, railVisits)
    }
}
