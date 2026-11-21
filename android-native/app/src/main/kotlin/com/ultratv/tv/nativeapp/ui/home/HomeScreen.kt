package com.ultratv.tv.nativeapp.ui.home

import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.common.RowBleed
import com.ultratv.tv.nativeapp.ui.common.rowBleedStart
import com.ultratv.tv.nativeapp.ui.common.RowEdgeMargin
import com.ultratv.tv.nativeapp.ui.common.edgeMarginFor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import kotlinx.coroutines.launch
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.ultratv.tv.nativeapp.ui.design.imageScrims
import com.ultratv.tv.nativeapp.ui.design.BOTTOM_SPAN
import com.ultratv.tv.nativeapp.ui.design.OnImageButton
import com.ultratv.tv.nativeapp.ui.design.OnImageIconButton
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.cardPhone
import com.ultratv.tv.nativeapp.i18n.homeChannelsCount
import com.ultratv.tv.nativeapp.i18n.heroMyList
import com.ultratv.tv.nativeapp.i18n.homeFavoritesCount
import com.ultratv.tv.nativeapp.i18n.homeGuideSubtitle
import com.ultratv.tv.nativeapp.i18n.homeLiveNow
import com.ultratv.tv.nativeapp.i18n.homeMoreInfo
import com.ultratv.tv.nativeapp.i18n.homeMoviesCount
import com.ultratv.tv.nativeapp.i18n.homeSeriesCount
import com.ultratv.tv.nativeapp.i18n.homeTop10
import com.ultratv.tv.nativeapp.i18n.extNewEpisodes
import com.ultratv.tv.nativeapp.i18n.locale
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.traktPopular
import com.ultratv.tv.nativeapp.i18n.traktListRow
import com.ultratv.tv.nativeapp.i18n.traktUpNext
import com.ultratv.tv.nativeapp.i18n.traktRecommended
import com.ultratv.tv.nativeapp.i18n.traktTrending
import com.ultratv.tv.nativeapp.i18n.traktAvailable
import com.ultratv.tv.nativeapp.i18n.traktWatchlist
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.BackdropImage
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LiveBadge
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.SectionTitle
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.ThumbImage
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.i18n.featuredBadge
import com.ultratv.tv.nativeapp.i18n.greetAfternoon
import com.ultratv.tv.nativeapp.i18n.greetEvening
import com.ultratv.tv.nativeapp.i18n.greetMorning
import com.ultratv.tv.nativeapp.i18n.sourcesCount
import com.ultratv.tv.nativeapp.i18n.sportLiveCount
import com.ultratv.tv.nativeapp.i18n.sportLiveNow
import com.ultratv.tv.nativeapp.i18n.sportNav
import com.ultratv.tv.nativeapp.ui.common.rememberEpgClock

/**
 * Accueil (maquette Accueil.dc.html) : hero « À la une » de 440 px, rails « Reprendre la lecture »
 * (cartes 16:9 avec progression) et « Chaînes favorites ». Aucune donnée factice : tout vient de la
 * source ; une métadonnée absente est masquée. Chaque image occupe un cadre fixe.
 *
 * Colonne PARESSEUSE (LazyColumn, une entrée par rangée avec clé et contentType) : seules les rangées visibles
 * sont composées. Avant, une Column + verticalScroll composait et mesurait d'un coup le hero et les huit rangées
 * (≈ 60 cartes avec image) à la première image puis à chaque émission de données — images de plus d'une seconde
 * sur les box modestes. Les rangées arrivent toutes prêtes du ViewModel ([HomeRows], immuable, construite hors du
 * fil principal) : la composition ne fabrique plus de listes ni ne nettoie de titres.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onGoLive: () -> Unit,
    onGoMovies: () -> Unit,
    onGoSeries: () -> Unit,
    onGoSettings: () -> Unit,
    onGoCloud: () -> Unit = {},
    onGoPhone: () -> Unit = {},
    onGoGuide: () -> Unit = {},
    onGoSport: () -> Unit = {},
    onGoSearch: () -> Unit = {},
    onGoFavorites: () -> Unit = {},
    onPlay: (url: String, title: String) -> Unit = { _, _ -> },
    onOpenMovie: (Long) -> Unit = {},
    onOpenSeries: (Long) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val D = LocalDs.current
    val providers by vm.providers.collectAsStateWithLifecycle()
    val loaded by vm.providersLoaded.collectAsStateWithLifecycle()
    // Défilement de « À la une » seulement au niveau d'animation complet sur un appareil non modeste : sinon image fixe
    // et le ViewModel ne prépare qu'un titre.
    val heroRotates = rememberHeroMayRotate()
    vm.setHeroRotating(heroRotates)
    val heroes by vm.heroes.collectAsStateWithLifecycle()
    // « À la une » qui défile toutes les 9 s (sans focus volé : seul le contenu change, OK ouvre le titre affiché).
    val freshStart = rememberHomeFreshStart()
    val heroFocusDoneState = rememberHeroFocusDone(freshStart)
    var heroIdx by rememberSaveable { mutableStateOf(0) }
    remember(freshStart) { if (freshStart) heroIdx = 0; freshStart }
    LaunchedEffect(heroes.size, heroRotates) {
        if (heroes.size < 2 || !heroRotates) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(HERO_ROTATE_MS); heroIdx = (heroIdx + 1) % heroes.size }
    }
    val hero = if (heroes.isEmpty()) null else heroes[heroIdx % heroes.size]
    // Notes IMDb / RT et synopsis de repli : State NON déballé ici, lu seulement par les pastilles et le synopsis du hero.
    val heroExtras = vm.heroExtras.collectAsStateWithLifecycle()
    // State non déballé : seul le bouton rond de l'image « À la une » le lit.
    val heroInList = vm.heroInList.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    // Lecture DIFFÉRÉE : seul l'encart de chargement lit le pourcentage (State non déballé ici), l'écran entier
    // n'est plus recomposé à chaque pour-cent de synchro.
    val syncPct = vm.syncPercent.collectAsStateWithLifecycle()
    // Retour au premier plan : relecture de la bibliothèque Trakt si elle a plus de 15 min (sinon sans effet).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_START) vm.refreshTrakt() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val dataState: com.ultratv.tv.nativeapp.ui.common.DataStateViewModel = hiltViewModel()
    val failure by dataState.failure.collectAsStateWithLifecycle()

    // Actions hissées : une seule instance par écran, pas une nouvelle lambda par carte à chaque recomposition.
    val playHistory: (WatchHistoryEntity) -> Unit = remember(vm, onPlay) { { e -> vm.playFromHistory(e); onPlay(e.streamUrl, e.title) } }
    val openHero: (HeroItem) -> Unit = remember(onOpenMovie, onOpenSeries) { { h -> if (h.kind == HeroItem.Kind.SERIES) onOpenSeries(h.id) else onOpenMovie(h.id) } }
    val openPoster: (PosterCardUi) -> Unit = remember(onOpenMovie, onOpenSeries) { { p -> if (p.isShow) onOpenSeries(p.item.id) else onOpenMovie(p.item.id) } }
    val playChannel: (ChannelEntity) -> Unit = remember(vm, onPlay) { { c -> vm.playChannel(c) { onPlay(c.streamUrl, c.title) } } }
    // « Regarder » : film → lecture directe ; série → fiche (l'épisode à reprendre y est choisi).
    val watchHero: (HeroItem) -> Unit = remember(vm, onPlay, openHero) { { h -> val u = h.streamUrl; if (u != null) { vm.playHero(h); onPlay(u, h.title) } else openHero(h) } }
    val toggleHeroList: (HeroItem) -> Unit = remember(vm) { { h -> vm.toggleHeroList(h) } }
    val onShortcut: (ShortcutId) -> Unit = remember(onGoLive, onGoMovies, onGoSeries, onGoFavorites, onGoGuide) {
        { id -> when (id) { ShortcutId.LIVE -> onGoLive(); ShortcutId.MOVIES -> onGoMovies(); ShortcutId.SERIES -> onGoSeries(); ShortcutId.FAVORITES -> onGoFavorites(); ShortcutId.GUIDE -> onGoGuide() } }
    }

    if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
        val profileVm: com.ultratv.tv.nativeapp.ui.profile.ProfileViewModel = hiltViewModel()
        val prof by profileVm.current.collectAsStateWithLifecycle()
        com.ultratv.tv.nativeapp.ui.mobile.MobileHomeScreen(
            state = com.ultratv.tv.nativeapp.ui.mobile.MobileHomeState(loaded, providers.isNotEmpty(), hero, rows, prof?.initial ?: "K", prof?.color ?: 0xFFD91E2B.toInt()),
            syncPercent = { syncPct.value },
            onOpenMovie = onOpenMovie, onOpenSeries = onOpenSeries,
            onSearch = onGoSearch, onProfile = { profileVm.requestSwitch() }, onGoLive = onGoLive, onGoSettings = onGoSettings, onGoGuide = onGoGuide, onGoFavorites = onGoFavorites, onShortcut = onShortcut,
            onPlay = onPlay, onPlayHistory = playHistory, onOpenHero = openHero,
        )
        return
    }

    val h = hero
    val f = failure
    val slot = remember(h != null, loaded, providers, f != null) {
        homeTopSlot(h != null, loaded, providers.map { it.kind }, com.ultratv.tv.nativeapp.ui.settings.SUPPORTED_KINDS, f != null)
    }
    // Focus initial sur « Regarder » UNE fois par visite de l'accueil (état sauvegardé dans l'entrée de navigation) :
    // au retour d'une fiche ou du lecteur, le hero recomposé ne prend plus le focus. Qui le rend : l'accueil est sorti de la
    // composition, le focusRestorer de ScreenFocusHost est perdu ; c'est [HomeFocusRestore] (élément quitté, sauvegardé avec
    // l'entrée) qui rend seul candidat l'élément quitté, et la demande de focus initiale de ScreenFocusHost tombe dessus.
    // Les retours qui ne recomposent pas l'accueil (modale fermée, menu latéral) restent à ScreenFocusHost / ContentFocusMemory.
    var heroFocusDone by heroFocusDoneState
    val onHeroFocused = remember { { heroFocusDone = true } }
    val clock = rememberEpgClock()
    val sportOn = com.ultratv.tv.nativeapp.ui.sport.LocalSportEnabled.current
    // Nombre de directs de sport (tuile « Sport » des raccourcis) : même ViewModel que la rangée « Sport en direct ».
    val sportLive: Int? = if (sportOn) {
        val sportVm: com.ultratv.tv.nativeapp.ui.sport.SportViewModel = hiltViewModel()
        val sportState by sportVm.state.collectAsStateWithLifecycle()
        sportState.live.size
    } else null

    HomeTvContent(
        slot = slot, hero = h, heroIndex = heroIdx % heroes.size.coerceAtLeast(1), heroCount = heroes.size, heroExtras = heroExtras,
        rows = rows, providerCount = providers.size, clock = clock, sportOn = sportOn, sportLive = sportLive,
        heroFocusDone = heroFocusDone, onHeroFocused = onHeroFocused, freshStart = freshStart,
        playHistory = playHistory, openPoster = openPoster, playChannel = playChannel, onShortcut = onShortcut, onGoSport = onGoSport,
        onWatchHero = watchHero, onInfoHero = openHero, heroInList = heroInList, onToggleHeroList = toggleHeroList,
        heroRotates = heroRotates,
        emptyTop = {
            when (slot) {
                HomeTopSlot.NO_SOURCE -> EmptyCard(D.homeNoSource, D.syncCloudHint, D.addSource, onGoSettings, D.cardCloud, onGoCloud, action3 = D.cardPhone, onAction3 = onGoPhone)
                // Sources héritées non prises en charge seulement (ex. Stalker, retiré en 1.1.1) : rien ne se chargera, on le dit.
                HomeTopSlot.UNSUPPORTED -> EmptyCard(D.unsupportedSource, D.unsupportedSourceHint, D.openSourceSettings, onGoSettings, takeFocus = false)
                // Source en erreur : message lisible, bouton explicite, aucun focus d'office (l'utilisateur reste sur le rail : « Accueil » n'ouvre pas les Réglages).
                HomeTopSlot.FAILURE -> if (f != null) EmptyCard(
                    LocalStrings.current.sync.messageFor(f.kind), D.sourceErrorBody(f.provider), D.openSourceSettings, onGoSettings,
                    action2 = D.retry, onAction2 = { dataState.retry(f.providerId) }, takeFocus = false,
                )
                HomeTopSlot.LOADING -> LoadingCard(D.homeEmpty) { syncPct.value }
                else -> Unit
            }
        },
        sportRow = { rowModifier, padding -> com.ultratv.tv.nativeapp.ui.sport.SportHomeSection(rowModifier, padding, playChannel, onGoSport) },
    )
}

/**
 * Accueil TV sans ViewModel (rendu testable) : « À la une » en image plein écran (titre et boutons dessinés dessus, heure en haut à droite) ou, sans
 * lui, l'en-tête (salutation, sources, heure) ; puis les rangées (raccourcis compris, quand l'utilisateur les affiche). Les rangées arrivent toutes prêtes ([HomeRows]) ; [emptyTop] dessine l'encart des états sans « À la une »
 * (aucune source, erreur, chargement) et [sportRow] la rangée « Sport en direct » quand la rubrique est activée.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun HomeTvContent(
    slot: HomeTopSlot, hero: HeroItem?, heroIndex: Int, heroCount: Int, heroExtras: State<Map<String, HeroExtras>>,
    rows: HomeRows, providerCount: Int, clock: State<Long>, sportOn: Boolean, sportLive: Int?,
    heroFocusDone: Boolean, onHeroFocused: () -> Unit,
    // Visite neuve (retour par le menu latéral) : liste en haut, rien à restaurer, rangées au début.
    freshStart: Boolean = false,
    playHistory: (WatchHistoryEntity) -> Unit, openPoster: (PosterCardUi) -> Unit, playChannel: (ChannelEntity) -> Unit,
    onShortcut: (ShortcutId) -> Unit, onGoSport: () -> Unit,
    onWatchHero: (HeroItem) -> Unit, onInfoHero: (HeroItem) -> Unit,
    heroInList: State<Set<String>>, onToggleHeroList: (HeroItem) -> Unit,
    emptyTop: @Composable () -> Unit,
    sportRow: @Composable (Modifier, PaddingValues) -> Unit,
    // Calculé UNE fois par l'appelant (l'écran le passe aussi au ViewModel) ; à défaut, lu ici.
    heroRotates: Boolean = rememberHeroMayRotate(),
    // Porte de sortie de rangée propre à cet écran ; injectable pour les tests.
    edgeGate: HomeEdgeGate = remember { HomeEdgeGate() },
) {
    val D = LocalDs.current
    // Pivot VERTICAL seulement (haut de l'élément focalisé à 30 % de l'écran, comme le pivot TV de Compose) ; les
    // rangées horizontales gardent le comportement hérité, rétabli dans chaque entrée.
    val inheritedSpec = LocalBringIntoViewSpec.current
    val listState = rememberLazyListState()
    // Retour sur « Regarder » / « Plus d'infos » depuis une rangée : la liste remonte en haut (le hero est le premier élément).
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val animsOff = com.ultratv.tv.nativeapp.ui.design.LocalAnimations.current.off
    val scrollTop: () -> Unit = remember(listState, scope, animsOff) {
        {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) scope.launch {
                if (animsOff) listState.scrollToItem(0) else listState.animateScrollToItem(0)
            }
        }
    }
    // Focus dans le bloc du haut (« À la une », raccourcis) : la liste doit montrer l'en-tête (salutation, heure). Le
    // défilement automatique vers l'élément focalisé s'arrête dès que le bouton est visible et annulait le retour en
    // haut lancé au même instant : on remonte donc APRÈS lui, dès que la liste est au repos et pas tout en haut.
    var topHasFocus by remember { mutableStateOf(false) }
    // Lu par le pivot hors composition (pendant le défilement) : drapeau simple, posé avant la demande de défilement.
    val topFocusFlag = remember { booleanArrayOf(false) }
    val pivotSpec = remember { HomePivotSpec { topFocusFlag[0] } }
    // Dernière carte focalisée, sauvegardée avec l'entrée de navigation : de retour d'une fiche, le focus y revient.
    val restore = rememberSaveable(saver = HomeFocusRestore.Saver) { HomeFocusRestore() }
    remember(restore, freshStart) { if (freshStart) { restore.reset(); listState.requestScrollToItem(0) }; freshStart }
    val homeVisit = rememberHomeVisit(freshStart)
    // Dernier recours de la restauration : après [HomeFocusRestore.RESTORE_MS] tout redevient candidat, bien avant la fin des essais de la
    // demande de focus initiale (3 s). Une carte disparue des données d'une rangée déjà là est vue plus tôt (voir RegisterRowKeys). Aucune demande de focus ici.
    LaunchedEffect(restore) {
        if (restore.pending) { kotlinx.coroutines.delay(HomeFocusRestore.RESTORE_MS); restore.pending = false }
    }
    LaunchedEffect(topHasFocus, animsOff) {
        if (!topHasFocus) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow {
            !listState.isScrollInProgress && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        }.collect { off -> if (off) { if (animsOff) listState.scrollToItem(0) else listState.animateScrollToItem(0) } }
    }
    // Raccourcis : une rangée comme les autres, seulement si l'utilisateur les affiche (masqués par défaut sur TV).
    val order = rows.order
    // Bouton « Regarder » du bandeau : cible de ▲ depuis la rangée « En direct » quand elle est juste sous l'image.
    val heroWatch = remember { HeroWatchTarget() }
    val sportAnchor = if (HomeSectionId.CHANNELS in order) HomeSectionId.CHANNELS else order.lastOrNull()
    val sportTile = if (sportOn) SportTile(sportLive, onGoSport) else null
    val stats = remember(providerCount, rows.shortcuts, D) {
        val count = { id: ShortcutId -> rows.shortcuts.firstOrNull { it.id == id }?.count }
        buildList {
            if (providerCount > 0) add(D.sourcesCount(providerCount))
            count(ShortcutId.LIVE)?.let { add(D.homeChannelsCount(it, formatCount(it, D.locale))) }
            count(ShortcutId.MOVIES)?.let { add(D.homeMoviesCount(it, formatCount(it, D.locale))) }
            count(ShortcutId.SERIES)?.let { add(D.homeSeriesCount(it, formatCount(it, D.locale))) }
        }.joinToString(" · ")
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec, LocalHomeFocusRestore provides restore, LocalHomeEdgeGate provides edgeGate, LocalHomeVisit provides homeVisit) {
        LazyColumn(
            // Touches reçues par l'accueil : une sortie de rangée différée dont la touche est périmée est abandonnée (voir [HomeEdgeGate]).
            Modifier.fillMaxSize().onPreviewKeyEvent { if (it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) { edgeGate.keys++; if (!heroFocusDone) onHeroFocused() }; false }
                // Le focus initial est FAIT dès que l'utilisateur a pressé une touche (ici ou sur un élément qui le porte) : un bandeau
                // qui arrive après les rangées ne déplace plus son focus et ne fait pas défiler la liste. Sans aucune touche (lancement : le
                // focus initial est tombé sur la première carte faute de bandeau), le bandeau qui arrive prend « Regarder » et la liste remonte.
                .onFocusChanged { if (it.hasFocus && restore.userActed()) onHeroFocused() },
            state = listState,
            // Les rangées portent elles-mêmes leur marge gauche (elles débordent de RowBleed dans cette marge sans être rognées par la liste).
            contentPadding = PaddingValues(bottom = 54.design),
            verticalArrangement = Arrangement.spacedBy(30.design),
        ) {
            item(key = "top", contentType = slot.name) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides inheritedSpec) {
                    Column(Modifier.onFocusChanged { topFocusFlag[0] = it.hasFocus; topHasFocus = it.hasFocus }) {
                        if (slot == HomeTopSlot.HERO && hero != null) {
                            // Image pleine largeur de la zone de contenu : aucune marge horizontale sur cet élément.
                            Hero(
                                hero, heroExtras, onWatch = onWatchHero, onInfo = onInfoHero, inList = heroInList, onToggleList = onToggleHeroList, takeFocus = !heroFocusDone && !restore.userActed(), onFocused = onHeroFocused, onFocusTop = scrollTop,
                                index = heroIndex, count = heroCount, mayRotate = heroRotates, clock = clock, watchTarget = heroWatch, modifier = Modifier.fillMaxWidth().height(HeroHeight).testTag("home-hero"),
                            )
                        } else Column(Modifier.padding(start = HomeStart, top = 44.design, end = 72.design), verticalArrangement = Arrangement.spacedBy(30.design)) {
                            // Salutation seulement sans image « À la une » (avec elle, l'heure se lit sur l'image).
                            HomeGreeting(clock, stats)
                            if (slot != HomeTopSlot.NONE && slot != HomeTopSlot.HERO) emptyTop()
                        }
                    }
                }
            }
            items(order, key = { it.name }, contentType = { it.contentType }) { id ->
                CompositionLocalProvider(LocalBringIntoViewSpec provides inheritedSpec, LocalHomeRowId provides id.name) {
                    val upTarget = if (slot == HomeTopSlot.HERO && hero != null && id == order.firstOrNull()) heroWatch else null
                    Box(Modifier.padding(start = HomeStart)) {
                        // Rubrique Sport activée : « Sport en direct » juste sous la rangée « Direct » (à défaut, en dernier).
                        if (sportOn && id == sportAnchor) Column(verticalArrangement = Arrangement.spacedBy(30.design)) {
                            HomeSection(id, rows, sportTile, playHistory, openPoster, playChannel, onShortcut, upTarget)
                            CompositionLocalProvider(LocalHomeRowId provides "SPORT") { sportRow(Modifier.homeRow(), PaddingValues(start = RowBleed, end = 96.design)) }
                        } else HomeSection(id, rows, sportTile, playHistory, openPoster, playChannel, onShortcut, upTarget)
                    }
                }
            }
        }
    }
}

/**
 * Cible de ▲ depuis la rangée sous le bandeau : le bouton « Regarder ». [attached] : le bandeau est composé. La liste paresseuse
 * peut l'avoir retiré (liste défilée, touche ▲ maintenue) ; viser alors [requester] ne ferait rien (avertissement, touche ignorée).
 */
private class HeroWatchTarget {
    val requester = FocusRequester()
    @JvmField var attached = false
}

/** Marge gauche de l'accueil (maquette : 56 px après le rail). */
private val HomeStart get() = 56.design

/** Tuile « Sport » des raccourcis (rubrique activée) : [live] directs en cours (null = inconnu), [onClick] ouvre l'écran Sport. */
private class SportTile(val live: Int?, val onClick: () -> Unit)

/** Hauteur de l'image « À la une » (maquette). */
private val HeroHeight get() = 600.design

/** Texte posé sur l'image « À la une » : clair dans les deux thèmes (l'image est toujours voilée de sombre). */
private val HeroInk = Color(0xFFF4F5F7)
private val HeroInk2 = Color(0xFFD5D8DE)

/** Heure : l'horloge est lue ICI (State non déballé plus haut) ; seule cette feuille se recompose chaque minute. */
@Composable
private fun HomeClock(clock: State<Long>, color: Color, modifier: Modifier = Modifier) {
    Text(EpgClock.wall(clock.value), color = color, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1, softWrap = false, modifier = modifier.testTag("home-clock"))
}

/** Salutation selon l'heure, sources et compteurs, puis l'heure à droite (l'horloge est lue ICI : seul ce bandeau se recompose). */
@Composable
private fun HomeGreeting(clock: State<Long>, stats: String) {
    val D = LocalDs.current
    val now = clock.value
    val hour = remember(now / 60_000L) { java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY) }
    val word = when { hour in 5..11 -> D.greetMorning; hour in 12..17 -> D.greetAfternoon; else -> D.greetEvening }
    Row(Modifier.fillMaxWidth().testTag("home-greeting"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
        Text(word, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 1, softWrap = false)
        if (stats.isNotEmpty()) Text("· $stats", color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        HomeClock(clock, Ux.Text)
    }
}

/** Pivot vertical de l'accueil : voir [pivotScrollDistance] ; animation courte (125 ms) comme le pivot TV de Compose. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
/**
 * [topFocused] : focus dans le bloc du haut (« À la une », raccourcis). Le pivot ne déplace alors PAS la page : il
 * voulait amener le bouton à 30 % de l'écran, donc descendre la page et cacher l'en-tête ; le retour en haut la
 * remontait ensuite, d'où un va-et-vient à chaque Gauche / Droite entre les boutons.
 */
private class HomePivotSpec(private val topFocused: () -> Boolean) : BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        androidx.compose.animation.core.tween(durationMillis = 125, easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        if (topFocused()) 0f else pivotScrollDistance(offset, size, containerSize)
}

/**
 * Rangée horizontale de l'accueil : débordement à gauche (première carte agrandie non rognée) + groupe de focus avec
 * restauration — revenir dans la rangée (Haut/Bas, retour du menu latéral, rangée recomposée après être sortie de
 * l'écran) rend le focus à la DERNIÈRE carte visée de cette rangée, jamais à la première par défaut.
 * Sorties : [up] reçoit ▲ (sinon recherche habituelle) ; ◀ / ▶ ne tombent jamais sur la carte d'une autre rangée, voir [HomeEdgeGate].
 * Le déplacement refait est DIFFÉRÉ d'une image (le refaire dans `exit` rentrerait dans la recherche en cours) : si une autre
 * touche est arrivée entre-temps, l'intention est périmée et rien n'est refait.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.homeRow(up: HeroWatchTarget? = null): Modifier {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val gate = LocalHomeEdgeGate.current
    val bleed = RowBleed
    val chain = remember(up, gate, focusManager, scope, bleed) {
        Modifier.focusProperties {
            exit = { d ->
                when {
                    gate == null || gate.closed -> FocusRequester.Default
                    // Bandeau pas (ou plus) composé : aucune demande vers un FocusRequester non attaché, la recherche habituelle fait composer l'élément précédent.
                    d == androidx.compose.ui.focus.FocusDirection.Up && up != null -> if (up.attached) up.requester else FocusRequester.Default
                    d == androidx.compose.ui.focus.FocusDirection.Left || d == androidx.compose.ui.focus.FocusDirection.Right -> {
                        val keys = gate.keys
                        scope.launch {
                            // Toujours à l'image suivante (jamais dans la recherche de focus en cours, même si le dispatcher est immédiat).
                            androidx.compose.runtime.withFrameNanos { }
                            if (gate.keys != keys) return@launch
                            gate.relaunches++
                            gate.closed = true
                            try { focusManager.moveFocus(d) } finally { gate.closed = false }
                        }
                        FocusRequester.Cancel
                    }
                    else -> FocusRequester.Default
                }
            }
        }.rowBleedStart(bleed).focusRestorer().focusGroup()
    }
    return this.then(chain)
}

/*
 * Marges de bord au défilement : calculées par [edgeMarginFor] (2 g + anneau) à partir des constantes mêmes de chaque
 * rangée (zoom, largeur de carte), jamais écrites en chiffres. Portée de l'anneau : 10 (design), soit contour 6 + 4 pour
 * les affiches ; pour les cartes Reprendre / En direct l'anneau est dessiné dans la carte, 10 est une réserve prudente.
 */
internal val PosterRingReach get() = 10.design
internal const val ResumeScale = 1.04f
internal val ResumeCardWidth get() = 320.design
internal const val ChannelScale = 1.06f
internal val ChannelCardWidth get() = 392.design

/** Rangées d'affiches et Top 10 : 31. */
private val PosterEdgeMargin get() = edgeMarginFor(HomePosterScale, PosterWidth, PosterRingReach)

/** Rangée « Reprendre » : 24. */
private val ResumeEdgeMargin get() = edgeMarginFor(ResumeScale, ResumeCardWidth, PosterRingReach)

/** Rangée « En direct » : 35. */
private val ChannelEdgeMargin get() = edgeMarginFor(ChannelScale, ChannelCardWidth, PosterRingReach)

@Composable
private fun HomeSection(
    id: HomeSectionId,
    rows: HomeRows,
    sportTile: SportTile?,
    playHistory: (WatchHistoryEntity) -> Unit,
    openPoster: (PosterCardUi) -> Unit,
    playChannel: (ChannelEntity) -> Unit,
    onShortcut: (ShortcutId) -> Unit,
    upTarget: HeroWatchTarget? = null,
) {
    val D = LocalDs.current
    when (id) {
        HomeSectionId.SHORTCUTS -> ShortcutsRow(rows.shortcuts, sportTile, onShortcut)
        HomeSectionId.TOP10 -> Section(D.homeTop10) { Top10Row(rows.top10, openPoster) }
        HomeSectionId.RESUME -> Section(D.continueWatching) { ResumeRow(rows.resume, playHistory) }
        // Trakt : seulement ce qui est disponible dans la playlist (rien si le compte n'est pas lié).
        HomeSectionId.LIBRARY_1, HomeSectionId.LIBRARY_2, HomeSectionId.LIBRARY_3, HomeSectionId.LIBRARY_4, HomeSectionId.LIBRARY_5, HomeSectionId.LIBRARY_6 ->
            rows.libraries.getOrNull(id.libraryIndex)?.let { l ->
                Section(D.catalogSortName(com.ultratv.tv.nativeapp.ui.catalog.CatalogFilter.Sort.RECENT) + " \u00B7 " + l.name) { PosterRow(l.cards, openPoster) }
            }
        HomeSectionId.TRAKT_AVAILABLE -> Section(D.traktAvailable) { PosterRow(rows.availableNow, openPoster) }
        HomeSectionId.TRAKT_WATCHLIST -> Section(D.traktWatchlist) { PosterRow(rows.watchlist, openPoster) }
        HomeSectionId.TRAKT_RECOMMENDED -> Section(D.traktRecommended) { PosterRow(rows.recommended, openPoster) }
        HomeSectionId.TRAKT_TRENDING -> Section(D.traktTrending) { PosterRow(rows.trending, openPoster) }
        HomeSectionId.TRAKT_POPULAR -> Section(D.traktPopular) { PosterRow(rows.popular, openPoster) }
        HomeSectionId.TRAKT_UP_NEXT -> Section(D.traktUpNext) { PosterRow(rows.upNext, openPoster) }
        HomeSectionId.NEW_EPISODES -> Section(D.extNewEpisodes) { NewEpisodesRow(rows.newEpisodes, openPoster) }
        HomeSectionId.TRAKT_LIST_1, HomeSectionId.TRAKT_LIST_2, HomeSectionId.TRAKT_LIST_3, HomeSectionId.TRAKT_LIST_4, HomeSectionId.TRAKT_LIST_5 ->
            rows.traktLists.getOrNull(id.traktListIndex)?.let { l -> Section(D.traktListRow(l.name)) { PosterRow(l.cards, openPoster) } }
        HomeSectionId.RECENT_CHANNELS -> Section(D.recentlyWatched) { ResumeRow(rows.recentChannels, playHistory) }
        HomeSectionId.LATEST_MOVIES -> Section(D.latestMovies) { PosterRow(rows.latestMovies, openPoster) }
        HomeSectionId.LATEST_SERIES -> Section(D.latestSeries) { PosterRow(rows.latestSeries, openPoster) }
        HomeSectionId.BECAUSE_WATCHED -> Section(D.becauseWatched(rows.becauseTitle)) { PosterRow(rows.because, openPoster) }
        HomeSectionId.TOP_MOVIES -> Section(D.topRatedMovies) { PosterRow(rows.topMovies, openPoster) }
        HomeSectionId.TOP_SERIES -> Section(D.topRatedSeries) { PosterRow(rows.topSeries, openPoster) }
        // « En direct maintenant » (maquette) ; le libellé est celui de la rubrique Sport, même sens.
        HomeSectionId.CHANNELS -> Section(if (rows.showingFavorites) D.favoriteChannels else D.sportLiveNow) {
            ChannelRow(rows.channels, playChannel, D.seeAll, upTarget) { onShortcut(if (rows.showingFavorites) ShortcutId.FAVORITES else ShortcutId.LIVE) }
        }
    }
}

@Composable
private fun ResumeRow(cards: List<ResumeCardUi>, onPlay: (WatchHistoryEntity) -> Unit) {
    val memory = rememberRowFocusMemory()
    val listState = rememberRowListState()
    RegisterRowKeys(memory, cards, listState) { cards.map { it.key } }
    RowEdgeMargin(ResumeEdgeMargin) {
        LazyRow(Modifier.homeRow().then(memory.tracker).testTag("home-resume-row"), state = listState, horizontalArrangement = Arrangement.spacedBy(28.design), contentPadding = PaddingValues(start = RowBleed, end = 96.design, top = 18.design, bottom = 18.design)) {
            items(cards, key = { it.key }, contentType = { "resume" }) { c -> ResumeCard(c, rowMemory(memory, c.key).testTag("home-resume-${c.key}")) { onPlay(c.entry) } }
        }
    }
}

/** « Nouveaux épisodes » : affiches des séries, « Série · S2 É5 · Demain » (libellés localisés ici, une fois par langue). */
@Composable
private fun NewEpisodesRow(cards: List<NewEpisodeCardUi>, onOpen: (PosterCardUi) -> Unit) {
    val D = LocalDs.current
    val posters = remember(cards, D.lang) {
        val now = System.currentTimeMillis()
        cards.map { c ->
            val tag = if (c.season != null && c.number != null) D.episodeTag(c.season, c.number) else null
            newEpisodePosterOf(c, tag, com.ultratv.tv.nativeapp.ui.detail.airDayLabel(D, c.airAt, now))
        }
    }
    PosterRow(posters, onOpen)
}

/** Rangée d'affiches (catalogue ou Trakt, films et séries mêlés) ; sélectionner ouvre la fiche existante. */
@Composable
private fun PosterRow(cards: List<PosterCardUi>, onOpen: (PosterCardUi) -> Unit) {
    val memory = rememberRowFocusMemory()
    val listState = rememberRowListState()
    RegisterRowKeys(memory, cards, listState) { cards.map { it.key } }
    RowEdgeMargin(PosterEdgeMargin) {
        LazyRow(Modifier.homeRow().then(memory.tracker).testTag("home-poster-row"), state = listState, horizontalArrangement = Arrangement.spacedBy(28.design), contentPadding = PosterRowPadding) {
            items(cards, key = { it.key }, contentType = { "poster" }) { p ->
                com.ultratv.tv.nativeapp.ui.catalog.PosterCell(
                    p.item, Modifier.width(PosterWidth).then(rowMemory(memory, p.key)).testTag("home-poster-${p.key}"),
                    kind = p.kind, imageWidth = PosterWidth, focusedScale = HomePosterScale,
                ) { onOpen(p) }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ChannelRow(cards: List<ChannelCardUi>, onPlay: (ChannelEntity) -> Unit, seeAll: String, up: HeroWatchTarget?, onSeeAll: () -> Unit) {
    // ▲ depuis n'importe quelle carte, à la sortie du groupe : « Regarder » (sans cela, la recherche géométrique choisit le bouton le plus proche, à droite).
    val memory = rememberRowFocusMemory()
    val listState = rememberRowListState()
    RegisterRowKeys(memory, cards, listState) { cards.distinctBy { it.channel.id }.map { it.channel.id } + "see-all" }
    val rowModifier = Modifier.homeRow(up)
    RowEdgeMargin(ChannelEdgeMargin) {
        LazyRow(
            rowModifier.then(memory.tracker).testTag("home-channel-row"), state = listState, horizontalArrangement = Arrangement.spacedBy(20.design),
            // Marge verticale : la carte focalisée grandit de 6 % (13 de chaque côté) + anneau (≈ 11) et la rangée rogne ce qui dépasse.
            contentPadding = PaddingValues(start = RowBleed, end = 96.design, top = 24.design, bottom = 24.design)) {
            items(cards.distinctBy { it.channel.id }, key = { it.channel.id }, contentType = { "channel" }) { c -> ChannelCard(c, memory) { onPlay(c.channel) } }
            // « Tout voir » : dernière carte de la rangée (un lien en haut à droite du titre capterait le focus en descendant).
            item(key = "see-all", contentType = "see-all") { SeeAllCard(seeAll, memory, onSeeAll) }
        }
    }
}

/** Dernière carte d'une rangée : ouvre l'écran complet. Même hauteur, même focus que les vignettes de chaîne. */
@Composable
private fun SeeAllCard(label: String, memory: RowFocusMemory, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(16.design), bg = Ux.Card, focusedBg = Ux.Card, focusedScale = ChannelScale, ringWidth = 5.design,
        modifier = Modifier.width(220.design).height(CHANNEL_CARD_HEIGHT).then(rowMemory(memory, "see-all")).testTag("home-channel-see-all"),
    ) { _ ->
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.design))
            DIcon(Icons.Arrow, 26.design, Ux.Text, strokeWidth = 2.4f)
        }
    }
}

/** Cellule du Top 10 : 336 de large (chiffre derrière, affiche de 200 du côté opposé). */
private val Top10CellWidth get() = 336.design

/** Cellule du rang à deux chiffres : plus large d'un chiffre, sinon l'affiche cachait le « 0 » et « 10 » se lisait « 1 ». */
private val Top10WideCellWidth get() = 526.design

/** Agrandissement de l'affiche focalisée sur l'accueil (10 %). */
internal const val HomePosterScale = 1.10f

/**
 * « Top 10 aujourd'hui » : chaque affiche porte son rang en grand chiffre au contour, derrière elle, du côté du début de
 * lecture (gauche en LTR, droite en RTL). Le chiffre est décoratif : jamais focalisable, ignoré des lecteurs d'écran.
 */
@Composable
private fun Top10Row(cards: List<PosterCardUi>, onOpen: (PosterCardUi) -> Unit) {
    val memory = rememberRowFocusMemory()
    val listState = rememberRowListState()
    RegisterRowKeys(memory, cards, listState) { cards.map { it.key } }
    // Le défilement vers la carte focalisée vise la CELLULE entière (grand chiffre compris), pas la seule affiche : sinon, en revenant en arrière, le chiffre était rogné au bord.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cellPx = with(density) { Top10CellWidth.toPx() }
    val reservePx = with(density) { ((HomePosterScale - 1f) / 2f * PosterWidth.toPx()) }
    val cellModifier = remember(cellPx, reservePx) { Modifier.width(Top10CellWidth).bringIntoViewResponder(WholeCellResponder(cellPx, reservePx)) }
    val widePx = with(density) { Top10WideCellWidth.toPx() }
    val wideCellModifier = remember(widePx, reservePx) { Modifier.width(Top10WideCellWidth).bringIntoViewResponder(WholeCellResponder(widePx, reservePx)) }
    RowEdgeMargin(PosterEdgeMargin) {
        LazyRow(Modifier.homeRow().then(memory.tracker).testTag("home-top10-row"), state = listState, horizontalArrangement = Arrangement.spacedBy(28.design), contentPadding = PosterRowPadding) {
            itemsIndexed(cards, key = { _, p -> p.key }, contentType = { i, _ -> if (i >= 9) "top10-wide" else "top10" }) { i, p ->
                Box(if (i >= 9) wideCellModifier else cellModifier) {
                    Top10Rank(i + 1, Modifier.align(Alignment.TopStart))
                    com.ultratv.tv.nativeapp.ui.catalog.PosterCell(
                        p.item, Modifier.width(PosterWidth).align(Alignment.TopEnd).then(rowMemory(memory, p.key)).testTag("home-poster-${p.key}"),
                        kind = p.kind, imageWidth = PosterWidth, focusedScale = HomePosterScale,
                    ) { onOpen(p) }
                }
            }
        }
    }
}

/**
 * Rend visible la cellule entière (de 0 à [widthPx], sur la hauteur de l'enfant) quand un enfant demande à être amené à l'écran.
 * [reservePx] : débord de l'agrandissement de l'affiche (hors de la cellule de chaque côté), que la marge de bord de la rangée
 * ne couvrait que pour un rectangle déjà décalé par le zoom, comme celui de l'affiche seule.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private class WholeCellResponder(private val widthPx: Float, private val reservePx: Float) : androidx.compose.foundation.relocation.BringIntoViewResponder {
    override fun calculateRectForParent(localRect: androidx.compose.ui.geometry.Rect) =
        androidx.compose.ui.geometry.Rect(-reservePx, localRect.top, widthPx + reservePx, localRect.bottom)
    override suspend fun bringChildIntoView(localRect: () -> androidx.compose.ui.geometry.Rect?) = Unit
}

/** Grand chiffre au contour (couleur du thème) : dessiné une fois par cellule, aucune lecture d'état de focus. */
@Composable
private fun Top10Rank(rank: Int, modifier: Modifier) {
    // Taille NON soumise à « Taille du texte » : la cellule est de largeur fixe (336, ou 526 pour « 10 »), un chiffre à ×1,3 la déborderait. Style mémorisé (rien d'alloué à la recomposition).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val stroke = with(density) { 4.design.toPx() }
    val size = with(density) { (300 / 2f).dp.toSp() }
    val style = remember(stroke) { androidx.compose.ui.text.TextStyle(drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)) }
    Box(modifier.testTag("top10-rank-$rank")) {
        Text(
            "$rank", color = Ux.Text3, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size,
            maxLines = 1, softWrap = false,
            style = style,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

// ───────────────────────── Raccourcis ─────────────────────────

/** Ordre d'affichage de la maquette : Direct, Guide TV, Films, Séries, Favoris, puis Sport (null = tuile Sport). */
private fun shortcutDisplay(items: List<ShortcutUi>, withSport: Boolean): List<ShortcutUi?> {
    val byId = items.associateBy { it.id }
    return buildList<ShortcutUi?> {
        for (id in listOf(ShortcutId.LIVE, ShortcutId.GUIDE, ShortcutId.MOVIES, ShortcutId.SERIES, ShortcutId.FAVORITES)) byId[id]?.let { add(it) }
        if (withSport) add(null)
    }
}

private fun shortcutIcon(id: ShortcutId): String = when (id) {
    ShortcutId.LIVE -> Icons.Live
    ShortcutId.MOVIES -> Icons.Movies
    ShortcutId.SERIES -> Icons.Series
    ShortcutId.FAVORITES -> Icons.Heart
    ShortcutId.GUIDE -> Icons.Guide
}

/** Rangée de raccourcis (quand l'utilisateur les affiche) : une ligne, tuiles à largeurs égales. */
@Composable
private fun ShortcutsRow(items: List<ShortcutUi>, sport: SportTile?, onSelect: (ShortcutId) -> Unit) {
    val memory = rememberRowFocusMemory()
    Row(Modifier.homeRow().then(memory.tracker).padding(end = 96.design).testTag("home-shortcuts"), horizontalArrangement = Arrangement.spacedBy(16.design)) {
        shortcutDisplay(items, sport != null).forEach { sc -> ShortcutTile(sc, sport, Modifier.weight(1f).height(110.design).then(rowMemory(memory, sc?.id ?: "sport")), onSelect) }
    }
}

/** Tuile (maquette) : icône, titre en gras et compteur ; au focus le fond s'inverse (palette de focus). [sc] nul = tuile Sport. */
@Composable
private fun ShortcutTile(sc: ShortcutUi?, sport: SportTile?, modifier: Modifier, onSelect: (ShortcutId) -> Unit) {
    val D = LocalDs.current
    val S = LocalStrings.current
    val (title, subtitle) = remember(sc?.id, sc?.count, sport?.live, D, S) {
        if (sc == null) D.sportNav to (sport?.live?.takeIf { it > 0 }?.let { D.sportLiveCount(formatCount(it, D.locale)) } ?: "")
        else {
            val n = sc.count ?: 0
            val c = formatCount(n, D.locale)
            when (sc.id) {
                ShortcutId.LIVE -> D.directTitle to D.homeChannelsCount(n, c)
                ShortcutId.MOVIES -> S.moviesTitle to D.homeMoviesCount(n, c)
                ShortcutId.SERIES -> S.seriesTitle to D.homeSeriesCount(n, c)
                ShortcutId.FAVORITES -> S.navFavorites to D.homeFavoritesCount(n, c)
                ShortcutId.GUIDE -> D.tvGuide to D.homeGuideSubtitle
            }
        }
    }
    val icon = if (sc == null) com.ultratv.tv.nativeapp.ui.sport.SportIcons.Trophy else shortcutIcon(sc.id)
    FocusSurface(
        onClick = { if (sc == null) sport?.onClick?.invoke() else onSelect(sc.id) },
        shape = RoundedCornerShape(22.design), bg = Ux.Card, focusedBg = Ux.Cta, focusedScale = 1.03f, ringWidth = 0.dp, modifier = modifier,
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 18.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
            DIcon(icon, 30.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2f)
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotEmpty()) Text(subtitle, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal val PosterWidth get() = 200.design
/**
 * Marges des rangées d'affiches : l'affiche ciblée grandit de 10 % (15 en haut pour 300 de haut) plus son contour
 * (décalé de 6, épaisseur 4, agrandi lui aussi : 11) ; sans cette marge, elle recouvrait le titre de la rangée.
 */
private val PosterRowPadding get() = PaddingValues(start = RowBleed, end = 96.design, top = 28.design, bottom = 8.design)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
        SectionTitle(title, 26)
        content()
    }
}

// ───────────────────────── Hero « À la une » ─────────────────────────

@Composable
private fun Hero(
    h: HeroItem, extras: State<Map<String, HeroExtras>>, onWatch: (HeroItem) -> Unit, onInfo: (HeroItem) -> Unit,
    inList: State<Set<String>>, onToggleList: (HeroItem) -> Unit,
    takeFocus: Boolean, onFocused: () -> Unit, onFocusTop: () -> Unit, clock: State<Long>, index: Int = 0, count: Int = 1, mayRotate: Boolean = false, watchTarget: HeroWatchTarget? = null, modifier: Modifier = Modifier,
) = com.ultratv.tv.nativeapp.ui.design.NoTextWrap {
    // Bandeau de hauteur fixe : pas de ligne de plus (les boutons « Regarder / Plus d'infos » étaient écrasés).
    HeroBody(h, extras, onWatch, onInfo, inList, onToggleList, takeFocus, onFocused, onFocusTop, clock, index, count, mayRotate, watchTarget, modifier)
}

/** API de l'appareil, lue UNE fois (jamais dans la composition). */
private val DeviceSdk = android.os.Build.VERSION.SDK_INT

/** « À la une » peut défiler ici : niveau d'animation, mémoire de l'appareil et API réunis (voir [heroMayRotate]). */
@Composable
private fun rememberHeroMayRotate(): Boolean =
    heroRotatesOn(com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current, com.ultratv.tv.nativeapp.ui.design.LocalAnimations.current.rotateHero, com.ultratv.tv.nativeapp.ui.common.LocalLowRam.current, DeviceSdk)

@Composable
private fun HeroBody(
    h: HeroItem, extras: State<Map<String, HeroExtras>>, onWatch: (HeroItem) -> Unit, onInfo: (HeroItem) -> Unit,
    inList: State<Set<String>>, onToggleList: (HeroItem) -> Unit,
    takeFocus: Boolean, onFocused: () -> Unit, onFocusTop: () -> Unit, clock: State<Long>, index: Int, count: Int, mayRotate: Boolean, watchTarget: HeroWatchTarget?, modifier: Modifier,
) {
    val D = LocalDs.current
    val requester = remember(watchTarget) { watchTarget?.requester ?: FocusRequester() }
    // « Regarder » n'est ciblable qu'une fois le bandeau composé (voir [HeroWatchTarget]).
    androidx.compose.runtime.DisposableEffect(watchTarget) { watchTarget?.attached = true; onDispose { watchTarget?.attached = false } }
    var focused by remember { mutableStateOf(false) }
    val restoreState = LocalHomeFocusRestore.current
    // Une touche pressée pendant les essais arrête la demande : jamais de focus déplacé sous les doigts de l'utilisateur.
    if (takeFocus) RequestInitialFocus(requester, hasFocus = { focused || restoreState?.userActed() == true })
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    // Voile bas : sombre, il fond vers la page sur 40 % de la hauteur. Thème clair : le texte (clair) reste sur fond
    // sombre, le fondu vers la page claire se limite aux 8 % du bas, sous les boutons (bord adouci, texte lisible).
    val bottomFade = Ux.Bg
    val bottomSpan = if (Ux.themeLight) 0.08f else BOTTOM_SPAN
    // Image pleine largeur, titre et boutons dessinés dessus (aucun panneau opaque) ; voiles dessinés une fois (aucun flou).
    Box(modifier.background(HeroScrimBase)) {
        // UNE seule image de fond. Fondu léger entre deux titres (coupé net si les animations sont désactivées).
        androidx.compose.animation.Crossfade(targetState = h, animationSpec = if (mayRotate) com.ultratv.tv.nativeapp.ui.design.LocalAnimations.current.tween<Float>(500) else androidx.compose.animation.core.snap<Float>(), label = "hero") { cur ->
            Box(Modifier.fillMaxSize()) {
                if (cur.backdrop != null) {
                    BackdropImage(cur.backdrop, Modifier.fillMaxSize(), decodeSize = DpSize(1920.design, HeroHeight))
                    Box(Modifier.fillMaxSize().imageScrims(rtl = rtl, pageColor = bottomFade, bottomSpan = bottomSpan))
                } else {
                    // Sans image paysage : voiles puis l'affiche 2:3 à droite, dans une zone fixe de 300 × 450 (jamais étirée, jamais voilée).
                    Box(Modifier.fillMaxSize().imageScrims(rtl = rtl, pageColor = bottomFade, bottomSpan = bottomSpan))
                    PosterImage(cur.poster, cur.title, Modifier.align(Alignment.BottomEnd).padding(end = 120.design, bottom = 60.design).height(450.design).width(300.design), radius = 20, kind = if (cur.kind == HeroItem.Kind.SERIES) com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV else com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE, decodeSize = DpSize(300.design, 450.design), background = Color(0xFF1B1F27), initialsColor = Color(0xFFA0A6B2))
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = HomeStart, bottom = 52.design).widthIn(max = 820.design),
            verticalArrangement = Arrangement.spacedBy(14.design, Alignment.Bottom),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
                Text(
                    D.featuredBadge.uppercase(), color = Ux.OnAccent, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.spx, letterSpacing = 1.spx, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Ux.Accent).padding(horizontal = 12.design, vertical = 5.design),
                )
                HeroInfoRow(h, extras)
            }
            HeroTitle(h, extras)
            HeroPlot(h, extras)
            Row(Modifier.padding(top = 6.design), horizontalArrangement = Arrangement.spacedBy(14.design)) {
                OnImageButton(
                    D.watch, onClick = { onWatch(h) }, primary = true, iconPath = Icons.Play, iconFill = true,
                    modifier = Modifier.testTag("hero-watch").then(heroFocus("watch")).focusRequester(requester).onFocusChanged { focused = it.isFocused; if (it.isFocused) { onFocused(); onFocusTop() } },
                )
                OnImageButton(
                    D.homeMoreInfo, onClick = { onInfo(h) },
                    modifier = Modifier.testTag("hero-info").then(heroFocus("info")).onFocusChanged { if (it.isFocused) onFocusTop() },
                )
                HeroListButton(h, inList, onToggleList, Modifier.testTag("hero-list").then(heroFocus("list")).onFocusChanged { if (it.isFocused) onFocusTop() })
            }
        }
        // Heure en haut à droite de l'image (lue dans HomeClock : l'écran ne se recompose pas chaque minute).
        HomeClock(clock, HeroInk, Modifier.align(Alignment.TopEnd).padding(top = 44.design, end = 72.design))
        // Points de pagination (titre affiché / nombre de titres « à la une »), en bas à droite de l'image.
        if (count > 1 && mayRotate) Row(Modifier.testTag("hero-dots").align(Alignment.BottomEnd).padding(end = 28.design, bottom = 24.design), horizontalArrangement = Arrangement.spacedBy(10.design)) {
            repeat(count) { i ->
                Box(Modifier.height(8.design).width(if (i == index) 28.design else 8.design).clip(RoundedCornerShape(4.design)).background(if (i == index) Color.White else Color.White.copy(alpha = 0.4f)))
            }
        }
    }
}

/**
 * Bouton rond « Ma liste » : « + » (hors liste) ou « ✓ » (dans la liste). L'état est lu ICI seulement : basculer
 * recompose ce bouton, ni le bandeau ni la liste ; le focus reste sur le bouton (même nœud, mêmes clés).
 */
@Composable
private fun HeroListButton(h: HeroItem, inList: State<Set<String>>, onToggle: (HeroItem) -> Unit, modifier: Modifier) {
    val D = LocalDs.current
    val on = h.key in inList.value
    OnImageIconButton(
        iconPath = if (on) Icons.Check else Icons.Plus, description = D.heroMyList, onClick = { onToggle(h) }, modifier = modifier,
        state = if (on) D.removeFavorite else D.addFavorite,
    )
}

/** Fond de l'image « À la une » avant son chargement (et derrière l'affiche) : le voile sombre des deux thèmes. L'emplacement de l'affiche de repli (#1B1F27, initiales claires) reste sombre aussi. */
private val HeroScrimBase = Color(0xFF0A0C10)

/** « 2013 | Drame | 1 h 41 min » puis pastilles IMDb et Rotten Tomatoes ; les notes arrivent plus tard (State lu ICI seulement). */
@Composable
private fun HeroInfoRow(h: HeroItem, extras: State<Map<String, HeroExtras>>) {
    val D = LocalDs.current
    val line = remember(h.key, D) { heroInfoLine(h.year, h.genre, h.runtimeMin?.let { runtimeLabel(it, D.hourShort, D.minShort) }) }
    val badges = heroRatingBadges(extras.value[h.key]?.ratings, h.rating)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
        if (line.isNotEmpty()) Text(line, color = HeroInk2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        badges.imdb?.let { Text(it, color = Color(0xFF0A0A0C), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.spx, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(6.design)).background(Color(0xFFF5C518)).padding(horizontal = 8.design, vertical = 2.design)) }
        badges.fallback?.let { Text(it, color = HeroInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.spx, maxLines = 1) }
        badges.rt?.let { Text("\uD83C\uDF45 $it", color = HeroInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.spx, maxLines = 1) }
    }
}

/** Titre du bandeau : logo officiel (Fanart.tv) s'il existe et une fois chargé, sinon le titre texte (State lu ICI seulement). */
@Composable
private fun HeroTitle(h: HeroItem, extras: State<Map<String, HeroExtras>>) {
    com.ultratv.tv.nativeapp.ui.detail.TitleLogo(extras.value[h.key]?.logo, h.title, maxWidth = 820.design, maxHeight = 200.design) {
        Text(
            h.title, color = HeroInk, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 96.spx, lineHeight = 98.spx,
            letterSpacing = (-1.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Synopsis sur deux lignes (source, à défaut TMDB une fois résolu) ; rien si aucun n'existe. */
@Composable
private fun HeroPlot(h: HeroItem, extras: State<Map<String, HeroExtras>>) {
    val text = h.plot?.takeIf { it.isNotBlank() } ?: extras.value[h.key]?.overview
    if (text != null) Text(text, color = HeroInk2, fontFamily = Manrope, fontSize = 20.spx, lineHeight = 30.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 820.design))
}

@Composable
private fun EmptyCard(title: String, hint: String, action: String, onAction: () -> Unit, action2: String? = null, onAction2: () -> Unit = {}, takeFocus: Boolean = true, action3: String? = null, onAction3: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(end = 96.design).clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep).padding(56.design),
        verticalArrangement = Arrangement.spacedBy(20.design),
    ) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(hint, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, lineHeight = 36.spx, maxLines = 5, overflow = TextOverflow.Ellipsis)
        val requester = remember { FocusRequester() }
        var focused by remember { mutableStateOf(false) }
        if (takeFocus) RequestInitialFocus(requester, hasFocus = { focused })
        Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
            PillButton(action, onClick = onAction, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused })
            if (action2 != null) PillButton(action2, onClick = onAction2, weight = FontWeight.Bold)
            if (action3 != null) PillButton(action3, onClick = onAction3, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingCard(text: String, percent: () -> Int?) {
    Column(
        Modifier.fillMaxWidth().padding(end = 96.design).height(440.design).clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep).padding(56.design),
        verticalArrangement = Arrangement.spacedBy(24.design, Alignment.Bottom),
    ) {
        Text(text, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 48.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth().height(10.design).clip(RoundedCornerShape(5.design)).background(Ux.Surface2)) {
            // Pourcentage lu ICI (lecture différée) : seule cette carte se recompose pendant la synchro.
            ProgressLine((percent() ?: 0) / 100f, Modifier.fillMaxWidth(), heightPx = 10)
        }
    }
}

// ───────────────────────── Cartes ─────────────────────────

/** Dégradé simple (aucun flou) sous le titre d'une carte « Reprendre » : lisible sur n'importe quelle vignette. */
private val ResumeScrim = androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC000000))

/** Carte « Reprendre » (maquette) : vignette 16:9 plein cadre, titre et reste à voir en bas, progression en pied. */
@Composable
private fun ResumeCard(c: ResumeCardUi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val D = LocalDs.current
    val e = c.entry
    // Titre nettoyé et progression calculés dans le ViewModel (hors fil principal) ; seul le libellé localisé est formaté ici, une fois.
    val meta = remember(c.minutesLeft, D) {
        val mins = c.minutesLeft
        when {
            mins == null -> null
            mins >= 60 -> D.hourMinLeft.format(mins / 60, mins % 60)
            else -> D.minLeft.format(mins)
        }
    }
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Card, focusedBg = Ux.Card, focusedScale = ResumeScale, ringWidth = 5.design, modifier = modifier.width(ResumeCardWidth).height(180.design)) { _ ->
        Box(Modifier.fillMaxSize()) {
            ThumbImage(e.poster, e.title, Modifier.fillMaxSize(), radius = 20, decodeSize = DpSize(ResumeCardWidth, 180.design))
            Box(Modifier.fillMaxSize().background(ResumeScrim))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.design, end = 16.design, bottom = 18.design), verticalArrangement = Arrangement.spacedBy(4.design)) {
                Text(c.title, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, lineHeight = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta != null) Text(meta, color = Color(0xCCFFFFFF), fontFamily = Manrope, fontSize = 15.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ProgressLine(c.progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), heightPx = 5, track = Color(0x33F4F5F7))
        }
    }
}

// Hauteur d'une vignette : cadre qui contient du texte (titre, horaire), donc en unité « designT » (grandit avec la taille de texte choisie).
private val CHANNEL_CARD_HEIGHT get() = 220.designT

/** Piste de la progression d'une vignette posée sur la page : jeton du thème (le blanc par défaut de ProgressLine est fait pour une image). */
internal fun channelTrackColor(): Color = Ux.Text.copy(alpha = 0.10f)

/**
 * Vignette chaîne « En direct maintenant » (392 × 220) : en haut pastille EN DIRECT, logo et nom de la chaîne ; en bas
 * titre du programme en cours et horaire ; progression collée au bord bas. Sans programme en cours : seulement la chaîne.
 * Le focus se lit par l'anneau et l'agrandissement (fond inchangé : les textes gardent leurs jetons).
 */
@Composable
private fun ChannelCard(card: ChannelCardUi, memory: RowFocusMemory, onClick: () -> Unit) {
    val c = card.channel
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(16.design), bg = Ux.Card, focusedBg = Ux.Card, focusedScale = ChannelScale, ringWidth = 5.design,
        modifier = Modifier.width(ChannelCardWidth).height(CHANNEL_CARD_HEIGHT).then(rowMemory(memory, c.id)).testTag("home-channel-${c.id}"),
    ) { _ ->
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 18.design, end = 18.design, top = 16.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
            LiveBadge()
            LogoBox(c.logo, c.title, Modifier.width(40.design).height(40.design), radius = 11, pad = 4, bg = Ux.Tile, decodeSize = DpSize(40.design, 40.design))
            // Sans numéro : celui de la source est souvent propre à une catégorie (« 2 » partout sur une rangée mélangée).
            Text(c.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        val now = card.nowTitle
        if (now != null) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 18.design, end = 18.design, bottom = 20.design), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(now, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (card.timeRange != null) Text(card.timeRange, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (card.progress != null) com.ultratv.tv.nativeapp.ui.design.StreamingProgressLine(card.progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), track = channelTrackColor())
        }
    }
}
