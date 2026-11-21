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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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

    @Before fun fakeImages() { installFakeImageLoader(); InputClock.lastKeyMs = 0L; InputClock.lastKeyCode = 0 }

    @After fun resetTheme() { Ux.themeLight = false; resetImageLoader(); InputClock.lastKeyMs = 0L; InputClock.lastKeyCode = 0 }

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
    private var edgeGate = HomeEdgeGate()
    private var clockState = mutableLongStateOf(System.currentTimeMillis())
    /** Boîte de dialogue posée par-dessus l'écran (comme OverlayHost) ; à sa fermeture le focus est rendu à l'écran. */
    private val modalOpen = mutableStateOf(false)

    private val rowTagPrefixes = listOf("hero-watch", "hero-info", "hero-list", "home-channel-", "home-resume-", "home-poster-", "home-sport-", "rail-", "detail-button", "modal-")

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
        edgeGate = HomeEdgeGate(); clockState = mutableLongStateOf(System.currentTimeMillis())
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
                    ModalHost()
                }
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun ModalHost() {
        var wasOpen by remember { mutableStateOf(false) }
        if (modalOpen.value) {
            val r = remember { FocusRequester() }
            FocusSurface(onClick = {}, modifier = Modifier.size(100.dp, 40.dp).testTag("modal-button").focusRequester(r)) { _ -> }
            androidx.compose.runtime.LaunchedEffect(Unit) { r.requestFocus() }
        }
        androidx.compose.runtime.LaunchedEffect(modalOpen.value) {
            if (!modalOpen.value && wasOpen) CurrentScreenFocus.requester?.requestFocus()
            wasOpen = modalOpen.value
        }
    }

    @Composable
    private fun HomeScene(heroCount: Int, sportList: List<SportEntry>) {
        // Comme HomeScreen : « Regarder » ne prend le focus qu'une fois par visite (état sauvegardé dans l'entrée de navigation).
        val fresh = rememberHomeFreshStart()
        var done by rememberHeroFocusDone(fresh)
        val clock = clockState
        val extras = remember { mutableStateOf(emptyMap<String, HeroExtras>()) }
        val current = heroSource.value
        HomeTvContent(
            slot = if (current != null) HomeTopSlot.HERO else HomeTopSlot.NONE, hero = current, heroIndex = 0, heroCount = heroCount, heroExtras = extras,
            rows = rowsState.value, providerCount = 3, clock = clock, sportOn = sportList.isNotEmpty(), sportLive = sportList.size.takeIf { it > 0 },
            heroFocusDone = done, onHeroFocused = { done = true }, freshStart = fresh,
            playHistory = {}, openPoster = {}, playChannel = {}, onShortcut = {}, onGoSport = {},
            onWatchHero = {}, onInfoHero = {},
            heroInList = inList, onToggleHeroList = {},
            emptyTop = {}, edgeGate = edgeGate,
            sportRow = { m, p -> if (sportList.isNotEmpty()) SportHomeRow(sportList, clock, m, p, {}, {}) },
        )
    }

    // ───────────── Touches et constats ─────────────

    /** Focalisés à chaque image pendant [frames] images (autoAdvance coupé) : tout élément de passage y apparaît. */
    private fun focusTrace(frames: Int, action: () -> Unit): List<String> {
        rule.mainClock.autoAdvance = false
        val seen = mutableListOf<String>()
        rule.runOnUiThread { action() }
        repeat(frames) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodes(isFocused()).fetchSemanticsNodes().forEach { n -> tagOf(n)?.let { t -> if (seen.lastOrNull() != t) seen += t } }
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        return seen
    }

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

    @Test fun bandeauQuiArriveApresLesRangees_neVoleJamaisLeFocus_etNeFaitPasDefilerLaListe() {
        mount(withHero = false)
        // Les rangées arrivent à l'image suivante : la demande de focus de l'écran réessaie toutes les 100 ms.
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        // Pas de bandeau : l'écran a posé le focus sur un élément des rangées et l'utilisateur a déjà bougé.
        val first = focusNow("départ")
        assertTrue("focus initial sur une carte, pas sur le bandeau ($first)", !first.startsWith("hero-"))
        val moved = step(forward, "déjà ailleurs")
        // Les fonds sont résolus en retard : le bandeau apparaît au-dessus (600 de haut).
        val seen = focusTrace(120) { heroSource.value = hero }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        assertEquals("le bandeau est bien affiché", 1, rule.onAllNodes(hasTestTag("hero-watch")).fetchSemanticsNodes().size)
        assertTrue("le focus n'a jamais bougé : $seen", seen.all { it == moved })
        assertEquals("après l'arrivée du bandeau", moved, focusNow("après l'arrivée du bandeau"))
        // La liste n'a pas défilé d'elle-même : le haut du bandeau est toujours en haut de l'écran.
        assertEquals("la liste est restée en haut", 0f, rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot().top.value, 1f)
    }

    @Test fun lancementAFroid_rangeesPuisBandeau_aucuneTouche_leBandeauPrendLeFocus_etLaListeEstEnHaut() {
        mount(withHero = false)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        val first = focusNow("départ sans bandeau")
        assertTrue("focus initial sur une carte tant que le bandeau manque ($first)", !first.startsWith("hero-"))
        // Les fonds sont résolus en retard : le bandeau arrive au-dessus, l'utilisateur n'a rien pressé.
        rule.runOnIdle { heroSource.value = hero }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        assertEquals("sans aucune touche, le focus va sur « Regarder »", "hero-watch", focusNow("après l'arrivée du bandeau"))
        assertTrue("bandeau entier visible (bord haut non au-dessus de l'écran)", rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot().top.value >= -1f)
        rule.onNodeWithTag("hero-watch").assertIsDisplayed()
    }

    @Test fun lancementAFroid_uneToucheAvantLeBandeau_leFocusNeBougePas_unAppuiReel() {
        mount(withHero = false)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        // Le temps avance entre l'ouverture de l'accueil et l'appui : la touche est postérieure à l'ouverture.
        android.os.SystemClock.sleep(50)
        val moved = step(forward, "appui")
        rule.runOnIdle { heroSource.value = hero }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        assertEquals("le bandeau est affiché", 1, rule.onAllNodes(hasTestTag("hero-watch")).fetchSemanticsNodes().size)
        assertEquals("le focus n'a pas bougé", moved, focusNow("après le bandeau"))
        assertEquals("liste en haut", 0f, rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot().top.value, 1f)
    }

    @Test fun lancementAFroid_touchePressesSurUnAutreEcranAvantLAccueil_neCompteJamaisCommeUneInteraction() {
        InputClock.lastKeyCode = Key.DirectionDown.nativeKeyCode; InputClock.lastKeyMs = android.os.SystemClock.uptimeMillis()
        android.os.SystemClock.sleep(50)
        mount(withHero = false)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        rule.runOnIdle { heroSource.value = hero }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        assertEquals("touche d'un autre écran ignorée", "hero-watch", focusNow("après le bandeau"))
    }

    @Test fun lancementAFroid_sansAucunBandeau_leFocusResteSurLaPremiereRangee() {
        mount(withHero = false)
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        val first = focusNow("sans bandeau")
        assertTrue("premier élément de la première rangée ($first)", first.startsWith("home-channel-") || first.startsWith("home-resume-"))
    }

    @Test fun lancementAFroid_rangeesPuisBandeau_enArabe_leBandeauPrendLeFocus() {
        mount(lang = AppLang.Arabic, withHero = false)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        rule.runOnIdle { heroSource.value = hero }
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        assertEquals("hero-watch", focusNow("après le bandeau"))
        assertTrue(rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot().top.value >= -1f)
    }

    @Test fun accueilQuiSOuvreAvecLeBandeau_lePremierFocusEstSurRegarder_etLaListeResteEnHaut() {
        mount()
        rule.mainClock.advanceTimeBy(4_000); rule.waitForIdle()
        assertEquals("hero-watch", focusNow("départ"))
        rule.onNodeWithTag("hero-watch").assertIsDisplayed()
    }

    @Test fun hautDepuisLaPremiereRangee_quandLeBandeauNEstPasCompose_laToucheGardeUnComportementDefini() {
        mount(long = true)
        focusNow("départ")
        step(Key.DirectionDown, "première rangée")
        val firstRow = focusNow("première rangée")
        assertTrue("première rangée sous le bandeau ($firstRow)", !firstRow.startsWith("hero-"))
        // Le bandeau sort de la composition (liste défilée au doigt, focus resté dans la rangée).
        rule.onRoot().performTouchInput { swipeUp(); swipeUp() }
        rule.waitForIdle()
        assertTrue("le bandeau n'est plus composé", rule.onAllNodes(hasTestTag("hero-watch")).fetchSemanticsNodes().isEmpty())
        assertEquals("le focus est resté sur la carte", firstRow, rule.onAllNodes(isFocused()).fetchSemanticsNodes().mapNotNull { tagOf(it) }.single())
        // ▲ : le bouton « Regarder » n'est pas attaché, la demande ne lui est pas faite ; la recherche habituelle fait venir le bandeau.
        val out = java.io.ByteArrayOutputStream()
        val realOut = System.out
        System.setOut(java.io.PrintStream(out, true))
        try { press(Key.DirectionUp); rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle() } finally { System.setOut(realOut) }
        assertTrue("aucune demande de focus vers un FocusRequester non attaché : $out", !out.toString().contains("FocusRequester is not initialized"))
        // Comportement défini : la touche n'est pas avalée, la recherche habituelle de Compose décide, un seul élément reste focalisé.
        val after = focusNow("après ▲")
        assertTrue("▲ n'est pas avalée : le focus a quitté la carte ($after)", after != firstRow)
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

    // ───────────── Retour à l'accueil par le menu latéral : comme à neuf ─────────────

    /** Autre écran puis retour à l'accueil CHOISI dans le menu latéral ([HomeFreshStart], posé par le menu) ; accueil recomposé. */
    private fun leaveAndReturnByMenu() {
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.onNodeWithTagExists("detail-button")
        rule.runOnIdle { HomeFreshStart.mark(); screen.value = "home" }; rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
    }

    @Test fun retourParLeMenu_listeEnHaut_bandeauEntier_focusSurRegarder() {
        mount(long = true)
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        leaveAndReturnByMenu()
        assertEquals("accueil comme à neuf : « Regarder »", "hero-watch", focusNow("après retour par le menu"))
        rule.onNodeWithTag("home-hero").assertIsDisplayed()
        assertEquals("liste en haut : le bandeau commence en haut de l'écran", 0f, rule.onNodeWithTag("home-hero").getUnclippedBoundsInRoot().top.value, 1f)
    }

    @Test fun retourParLeMenu_memoireDesRangeesRemiseAZero_premiereCarte() {
        mount()
        downTo("home-poster-m")
        repeat(4) { step(forward, "carte") }
        leaveAndReturnByMenu()
        assertEquals("hero-watch", focusNow("après retour"))
        assertEquals("première carte de la rangée, pas la carte quittée", "home-poster-m1", downTo("home-poster-m"))
    }

    /** Visite ouverte par le menu : la remise à neuf des rangées a lieu UNE fois, puis la mémoire de rangée joue comme d'habitude. */
    @Test fun visiteNeuve_rangeeSortieDeLaCompositionPuisRevenue_gardeLaCarteQuittee() {
        mount(long = true)
        leaveAndReturnByMenu()
        assertEquals("hero-watch", focusNow("visite neuve"))
        downTo("home-poster-t")
        repeat(8) { step(forward, "carte") }
        val left = focusNow("carte lointaine")
        assertEquals("home-poster-t9", left)
        repeat(6) { step(Key.DirectionDown, "descente") }
        var tag = ""
        repeat(6) { tag = step(Key.DirectionUp, "remontée") }
        assertEquals("la rangée garde sa carte quittée", left, tag)
    }

    private fun entreeDansLeContenuApresRetourParLeMenu(lang: AppLang) {
        mount(lang)
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        leaveAndReturnByMenu()
        // Le focus est dans le menu au moment du choix : l'entrée dans le contenu (▶, ◀ en arabe) arrive sur « Regarder ».
        focusByTag("rail-0")
        assertEquals("hero-watch", step(forward, "menu vers le contenu"))
        rule.onNodeWithTag("home-hero").assertIsDisplayed()
    }

    @Test fun retourParLeMenu_focusDansLeMenu_entrerDansLeContenu_arriveSurRegarder() = entreeDansLeContenuApresRetourParLeMenu(AppLang.French)
    @Test fun retourParLeMenu_focusDansLeMenu_entrerDansLeContenu_enArabe_arriveSurRegarder() = entreeDansLeContenuApresRetourParLeMenu(AppLang.Arabic)

    @Test fun retourDUneFiche_sansPasserParLeMenu_gardeLaCarteQuittee() {
        mount()
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        leaveAndReturn(); rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
        assertEquals("home-poster-m3", focusNow("après retour de fiche"))
    }

    private fun leaveAndReturn(frames: Int = 90, whileAway: () -> Unit = {}): List<String> {
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.onNodeWithTagExists("detail-button")
        rule.runOnUiThread { whileAway() }
        return focusTrace(frames) { screen.value = "home" }.filter { it != "detail-button" }
    }

    @Test fun retourSurLAccueil_aucunFocusDePassage_memeDefilement_memeCarte() {
        mount()
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        assertEquals("home-poster-m3", focusNow("avant"))
        val before = rule.onNodeWithTag("home-poster-m3").getUnclippedBoundsInRoot()
        val seen = leaveAndReturn()
        // Entre la recomposition et la stabilisation, un seul élément a reçu le focus : la carte quittée.
        assertEquals("aucun focus de passage", listOf("home-poster-m3"), seen)
        assertEquals("home-poster-m3", focusNow("après"))
        val after = rule.onNodeWithTag("home-poster-m3").getUnclippedBoundsInRoot()
        assertEquals("même défilement de la liste (haut de la carte)", before.top.value, after.top.value, 1f)
        assertEquals("même défilement de la rangée (gauche de la carte)", before.left.value, after.left.value, 1f)
    }

    private fun retourSurUnBouton(button: String) {
        mount()
        repeat(listOf("hero-watch", "hero-info", "hero-list").indexOf(button)) { step(forward, "bouton") }
        assertEquals(button, focusNow("avant"))
        val seen = leaveAndReturn()
        assertEquals("aucun focus de passage ($button)", listOf(button), seen)
        assertEquals(button, focusNow("après retour"))
    }

    @Test fun retourSurLAccueil_heroWatch_resteHeroWatch() = retourSurUnBouton("hero-watch")
    @Test fun retourSurLAccueil_heroInfo_resteHeroInfo() = retourSurUnBouton("hero-info")
    @Test fun retourSurLAccueil_heroList_resteHeroList() = retourSurUnBouton("hero-list")

    @Test fun retourSurLAccueil_carteDisparue_restaurationAbandonnee_unElementVisibleFocalise() {
        mount()
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        // Pendant l'absence, la rangée est remplacée : la carte quittée n'existe plus.
        val seen = leaveAndReturn(frames = 150) { rowsState.value = rowsState.value.copy(latestMovies = (1..4).map { HomeTvFixtures.posterUi("n", it) }) }
        assertTrue("un élément a reçu le focus : $seen", seen.isNotEmpty())
        val tag = focusNow("après abandon")
        assertTrue("jamais nulle part, jamais sur la carte disparue ($tag)", tag != "home-poster-m3")
    }

    /** Images (de 16 ms) écoulées après le retour sur l'accueil avant qu'un de ses éléments soit focalisé ; [max] si jamais. */
    private fun imagesAvantFocus(max: Int): Int {
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { screen.value = "home" }
        var n = 0
        while (n < max) {
            rule.mainClock.advanceTimeByFrame(); n++
            // Le menu latéral peut garder le focus de passage : seul un élément de l'accueil compte.
            if (rule.onAllNodes(isFocused()).fetchSemanticsNodes().any { val t = tagOf(it); t != null && t != "detail-button" && !t.startsWith("rail-") }) break
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        return n
    }

    @Test fun retourSurLAccueil_carteDisparueDesDonnees_laRestaurationSeTermineAussitot_pasDeTrouDeFocus() {
        mount()
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.onNodeWithTagExists("detail-button")
        // Pendant l'absence, la rangée est remplacée : la carte quittée n'existe plus dans les données.
        rule.runOnUiThread { rowsState.value = rowsState.value.copy(latestMovies = (1..4).map { HomeTvFixtures.posterUi("n", it) }) }
        val frames = imagesAvantFocus(max = 60)
        // 60 images = 960 ms : bien avant le délai de dernier recours (1 000 ms). Un trou de plusieurs centaines de ms se voit comme un écran figé.
        assertTrue("un élément doit être focalisé dans les 250 premières ms, il l'a été après $frames images", frames <= 15)
        assertTrue("jamais sur la carte disparue", focusNow("après abandon") != "home-poster-m3")
    }

    @Test fun retourSurLAccueil_carteExistanteMaisPasEncoreComposee_laRangeeDefileJusquaElle_aucuneAutreCarteNePrendLeFocus() {
        mount()
        rowsState.value = rowsState.value.copy(latestMovies = (1..30).map { HomeTvFixtures.posterUi("m", it) })
        rule.waitForIdle()
        downTo("home-poster-m")
        repeat(19) { step(forward, "carte") }
        assertEquals("home-poster-m20", focusNow("avant"))
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.onNodeWithTagExists("detail-button")
        // Pendant l'absence : quarante nouvelles affiches avant la carte quittée, qui existe toujours mais hors de la fenêtre composée.
        rule.runOnUiThread { rowsState.value = rowsState.value.copy(latestMovies = (1..40).map { HomeTvFixtures.posterUi("n", it) } + HomeTvFixtures.posterUi("m", 20)) }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { screen.value = "home" }
        val passage = mutableListOf<String>()
        var n = 0
        // Avant, ce cas attendait le délai de dernier recours sans aucun focus (640 ms et plus) : la rangée défile maintenant jusqu'à la carte.
        while (n < 40 && passage.isEmpty()) {
            rule.mainClock.advanceTimeByFrame(); n++
            rule.onAllNodes(isFocused()).fetchSemanticsNodes().forEach { nd -> tagOf(nd)?.let { t -> if (t != "detail-button" && !t.startsWith("rail-")) passage += t } }
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue("le focus doit arriver sans attendre le dernier recours, il est arrivé après $n images", n <= 15)
        assertEquals("aucune autre carte ne prend le focus : la carte quittée", listOf("home-poster-m20"), passage.distinct())
        assertEquals("home-poster-m20", focusNow("après le retour"))
    }

    @Test fun retourSurLAccueil_septiemeCarteDeReprendre_remonteeEnTeteParLHistorique_leFocusArriveSansAttente() {
        mount()
        rowsState.value = rowsState.value.copy(resume = (1..12).map { HomeTvFixtures.resume(it) })
        rule.waitForIdle()
        downTo("home-resume-")
        repeat(6) { step(forward, "carte") }
        assertEquals("home-resume-r7", focusNow("avant"))
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.onNodeWithTagExists("detail-button")
        // La lecture met la carte en tête de l'historique (tri par date décroissante) : même clé, index 0, la rangée garde son défilement.
        rule.runOnUiThread { rowsState.value = rowsState.value.copy(resume = (listOf(7) + (1..12).filter { it != 7 }).map { HomeTvFixtures.resume(it) }) }
        val frames = imagesAvantFocus(max = 60)
        // Pas d'attente du délai de dernier recours (1 000 ms = 62 images) : le focus arrive en quelques images.
        assertTrue("le focus doit arriver sans attente, il est arrivé après $frames images", frames <= 15)
        assertEquals("la carte quittée, en tête de rangée", "home-resume-r7", focusNow("après le retour"))
    }

    @Test fun retourSurLAccueil_rangeeQuiArriveApresLeRetour_laCarteQuitteeEstRestauree() {
        mount()
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        assertEquals("home-poster-m3", focusNow("avant"))
        val rangeeDAvant = rowsState.value.latestMovies
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.onNodeWithTagExists("detail-button")
        // Données partielles au retour : les autres rangées sont là, « Derniers films » n'est pas encore arrivée.
        rule.runOnUiThread { rowsState.value = rowsState.value.copy(latestMovies = emptyList()) }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { screen.value = "home" }
        val passage = mutableListOf<String>()
        repeat(30) {
            rule.mainClock.advanceTimeByFrame()
            rule.onAllNodes(isFocused()).fetchSemanticsNodes().forEach { n -> tagOf(n)?.let { t -> if (t != "detail-button" && !t.startsWith("rail-")) passage += t } }
        }
        assertEquals("tant que la rangée n'est pas là, aucune autre carte ne prend le focus", emptyList<String>(), passage.distinct())
        // La rangée arrive 500 ms plus tard (bien avant le délai de dernier recours) : la carte quittée doit encore être restaurée.
        rule.runOnUiThread { rowsState.value = rowsState.value.copy(latestMovies = rangeeDAvant) }
        rule.mainClock.advanceTimeBy(600)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals("la carte quittée, pas un focus de remplacement", "home-poster-m3", focusNow("après l'arrivée de la rangée"))
    }

    @Test fun retourSurLAccueil_donneesJamaisArrivees_restaurationAbandonnee_leBandeauReprendLeFocus() {
        mount()
        downTo("home-poster-m")
        val seen = leaveAndReturn(frames = 150) { rowsState.value = HomeRows.EMPTY }
        assertTrue("un élément a reçu le focus : $seen", seen.isNotEmpty())
        assertTrue(focusNow("après abandon").startsWith("hero-"))
    }

    @Test fun retourSurLAccueil_touchePendantLAttente_gagneToujours_restaurationAbandonnee() {
        mount()
        downTo("home-poster-m")
        rule.runOnIdle { screen.value = "detail" }; rule.waitForIdle()
        rule.runOnUiThread { rowsState.value = rowsState.value.copy(latestMovies = (1..4).map { HomeTvFixtures.posterUi("n", it) }) }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { screen.value = "home" }
        repeat(3) { rule.mainClock.advanceTimeByFrame() }
        assertEquals("en attente : aucun focus dans l'accueil", 0, rule.onAllNodes(isFocused()).fetchSemanticsNodes().count { tagOf(it) != "detail-button" })
        // Une touche pendant l'attente (bien avant les 1000 ms d'abandon) : tout redevient candidat à la prochaine tentative.
        android.os.SystemClock.sleep(20)
        InputClock.lastKeyCode = Key.DirectionDown.nativeKeyCode; InputClock.lastKeyMs = android.os.SystemClock.uptimeMillis()
        rule.mainClock.advanceTimeBy(250)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        focusNow("après la touche")
    }

    @Test fun retourSurLAccueil_rafraichissementDesRangees_etFermetureDUneModale_neDeclenchentAucuneRestauration() {
        mount()
        downTo("home-poster-m")
        repeat(2) { step(forward, "carte") }
        leaveAndReturn()
        assertEquals("home-poster-m3", focusNow("restauré"))
        // Rafraîchissement : aucune restauration, le focus ne bouge pas.
        val refreshed = focusTrace(30) { rowsState.value = HomeTvFixtures.rows(layout) }
        assertTrue("rafraîchissement : $refreshed", refreshed.all { it == "home-poster-m3" })
        assertEquals("home-poster-m3", focusNow("après rafraîchissement"))
        // Modale : le focus va sur elle puis revient (rendu par l'écran), sans passer par une autre carte.
        val modal = focusTrace(30) { modalOpen.value = true }
        assertEquals(listOf("modal-button"), modal.filter { it != "home-poster-m3" })
        val closed = focusTrace(60) { modalOpen.value = false }
        assertTrue("fermeture de la modale : $closed", closed.all { it == "home-poster-m3" || it == "modal-button" })
        assertEquals("home-poster-m3", focusNow("après la modale"))
        // Et ◀ ▶ restent normaux ensuite.
        assertEquals("home-poster-m2", step(backward, "après")); assertEquals("home-poster-m3", step(forward, "après"))
    }

    // ───────────── Porte de sortie de rangée ─────────────

    @Test fun sansMenuLateral_gaucheAuDebutEtDroiteALaFin_leFocusResteSurLaMemeCarte_uneSeuleRelancePourChaqueTouche() {
        mount(withRail = false)
        downTo("home-poster-t")
        val first = focusNow("début")
        val r0 = edgeGate.relaunches
        assertEquals(first, step(backward, "début de rangée"))
        assertEquals("une seule relance pour la touche", r0 + 1, edgeGate.relaunches)
        for (k in 1..16) { val t = step(forward, "avance"); if (t == focusNow("lecture")) { /* continue */ } }
        val last = focusNow("fin")
        val r1 = edgeGate.relaunches
        assertEquals(last, step(forward, "fin de rangée"))
        assertTrue("au plus une relance pour la touche", edgeGate.relaunches - r1 <= 1)
        assertEquals("la porte est rouverte", false, edgeGate.closed)
        // Rien n'est perdu : ▼ / ▲ fonctionnent.
        step(Key.DirectionDown, "après"); step(Key.DirectionUp, "après")
    }

    @Test fun gauchePuisBasSansAttendre_lIntentionPerimeeEstAbandonnee_aucunMouvementFantome() {
        mount()
        downTo("home-resume-")
        val before = focusNow("départ")
        // ◀ puis ▼ dans le même instant : la relance différée de ◀ est périmée quand elle s'exécute.
        InputClock.lastKeyCode = Key.DirectionDown.nativeKeyCode; InputClock.lastKeyMs = android.os.SystemClock.uptimeMillis()
        val r0 = edgeGate.relaunches
        // Horloge de test arrêtée : la relance différée de ◀ ne s'exécute pas entre les deux touches.
        rule.mainClock.autoAdvance = false
        rule.onRoot().performKeyInput { keyDown(backward); keyUp(backward) }
        rule.onRoot().performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val after = focusNow("après ◀ ▼ simultanés")
        assertTrue("le focus a suivi ▼, pas le menu ($after)", !after.startsWith("rail-") && after != before)
        assertEquals("aucune relance fantôme", r0, edgeGate.relaunches)
        assertEquals(false, edgeGate.closed)
    }

    @Test fun relanceDeLaSortie_auPlusUneFoisParTouche_surToutLeParcoursDuMenu() {
        mount(long = true)
        var horizontalPresses = 0
        var last = focusNow("départ")
        for (i in 1..14) {
            val t = step(Key.DirectionDown, "descente")
            if (t == last) break
            last = t
            step(backward, "vers le menu"); horizontalPresses++
            step(forward, "retour"); horizontalPresses++
            assertEquals("la porte est rouverte", false, edgeGate.closed)
        }
        // Chaque touche horizontale provoque au plus une relance : une boucle donnerait bien plus.
        assertTrue("relances ${edgeGate.relaunches} pour $horizontalPresses touches", edgeGate.relaunches <= horizontalPresses)
    }

    // ───────────── Le focus ne bouge jamais sans touche, avec le temps ─────────────

    @Test fun avecLeTemps_rotationDuBandeauEtChangementDeMinute_leFocusNeBougePas() {
        mount(heroCount = 3)
        fun stable(label: String) {
            val tag = focusNow("$label (départ)")
            for (period in 1..4) {
                rule.runOnIdle { heroSource.value = hero.copy(id = 10L + period, title = "Titre $period", backdrop = "https://exemple.invalid/f$period.jpg") }
                rule.mainClock.advanceTimeBy(9_000)
                rule.waitForIdle()
                assertEquals("$label : période $period", tag, focusNow("$label période $period"))
            }
            rule.runOnIdle { clockState.longValue += 61_000L }
            rule.mainClock.advanceTimeBy(2_000); rule.waitForIdle()
            assertEquals("$label : l'heure a changé de minute", tag, focusNow("$label minute"))
        }
        downTo("home-poster-m")
        stable("carte")
        for (i in 1..14) { if (focusNow("haut").startsWith("hero-")) break; step(Key.DirectionUp, "remontée") }
        assertEquals("hero-watch", focusNow("haut"))
        step(forward, "bouton")
        stable("bouton")
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
