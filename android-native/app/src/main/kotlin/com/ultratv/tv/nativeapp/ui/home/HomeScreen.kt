package com.ultratv.tv.nativeapp.ui.home

import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.common.RowBleed
import com.ultratv.tv.nativeapp.ui.common.rowBleedStart
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.cardPhone
import com.ultratv.tv.nativeapp.i18n.homeChannelsCount
import com.ultratv.tv.nativeapp.i18n.homeFavoritesCount
import com.ultratv.tv.nativeapp.i18n.homeGuideSubtitle
import com.ultratv.tv.nativeapp.i18n.homeLiveNow
import com.ultratv.tv.nativeapp.i18n.homeMoreInfo
import com.ultratv.tv.nativeapp.i18n.homeMoviesCount
import com.ultratv.tv.nativeapp.i18n.homeSeriesCount
import com.ultratv.tv.nativeapp.i18n.homeTop10
import com.ultratv.tv.nativeapp.i18n.locale
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.traktPopular
import com.ultratv.tv.nativeapp.i18n.traktListRow
import com.ultratv.tv.nativeapp.i18n.traktUpNext
import com.ultratv.tv.nativeapp.i18n.traktRecommended
import com.ultratv.tv.nativeapp.i18n.traktTrending
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
    val heroes by vm.heroes.collectAsStateWithLifecycle()
    // « À la une » qui défile toutes les 9 s (sans focus volé : seul le contenu change, OK ouvre le titre affiché).
    var heroIdx by rememberSaveable { mutableStateOf(0) }
    val heroAnim = com.ultratv.tv.nativeapp.ui.design.LocalAnimations.current
    LaunchedEffect(heroes.size, heroAnim) {
        if (heroes.size < 2 || !heroAnim.rotateHero) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(HERO_ROTATE_MS); heroIdx = (heroIdx + 1) % heroes.size }
    }
    val hero = if (heroes.isEmpty()) null else heroes[heroIdx % heroes.size]
    // Notes IMDb / RT et synopsis de repli : State NON déballé ici, lu seulement par les pastilles et le synopsis du hero.
    val heroExtras = vm.heroExtras.collectAsStateWithLifecycle()
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
    val playChannel: (ChannelEntity) -> Unit = remember(onPlay) { { c -> onPlay(c.streamUrl, c.title) } }
    // « Regarder » : film → lecture directe ; série → fiche (l'épisode à reprendre y est choisi).
    val watchHero: (HeroItem) -> Unit = remember(vm, onPlay, openHero) { { h -> val u = h.streamUrl; if (u != null) { vm.playHero(h); onPlay(u, h.title) } else openHero(h) } }
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
    // au retour d'une fiche ou du lecteur, le hero recomposé ne vole plus le focus que ScreenFocusHost (focusRestorer)
    // rend à la carte quittée.
    var heroFocusDone by rememberSaveable { mutableStateOf(false) }
    val onHeroFocused = remember { { heroFocusDone = true } }

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
    CompositionLocalProvider(LocalBringIntoViewSpec provides HomePivotSpec) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            // Pas de marge en haut ni à gauche : l'image « À la une » occupe toute la largeur. Les autres éléments portent
            // eux-mêmes leur marge gauche (les rangées débordent de RowBleed dans cette marge sans être rognées par la liste).
            contentPadding = PaddingValues(bottom = 54.design),
            verticalArrangement = Arrangement.spacedBy(44.design),
        ) {
            if (slot != HomeTopSlot.NONE) item(key = "top", contentType = slot.name) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides inheritedSpec) {
                    if (slot == HomeTopSlot.HERO) {
                        if (h != null) Hero(h, heroExtras, onWatch = watchHero, onInfo = openHero, takeFocus = !heroFocusDone, onFocused = onHeroFocused, onFocusTop = scrollTop, index = heroIdx % heroes.size.coerceAtLeast(1), count = heroes.size)
                    } else Box(Modifier.padding(start = 72.design, top = 54.design)) {
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
                    }
                }
            }
            items(rows.order, key = { it.name }, contentType = { it.contentType }) { id ->
                CompositionLocalProvider(LocalBringIntoViewSpec provides inheritedSpec) {
                    Box(Modifier.padding(start = 72.design)) { HomeSection(id, rows, playHistory, openPoster, playChannel, onShortcut) }
                }
            }
        }
    }
}

/** Pivot vertical de l'accueil : voir [pivotScrollDistance] ; animation courte (125 ms) comme le pivot TV de Compose. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private object HomePivotSpec : BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> =
        androidx.compose.animation.core.tween(durationMillis = 125, easing = androidx.compose.animation.core.CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = pivotScrollDistance(offset, size, containerSize)
}

/**
 * Rangée horizontale de l'accueil : débordement à gauche (première carte agrandie non rognée) + groupe de focus avec
 * restauration — revenir dans la rangée (Haut/Bas, retour du menu latéral, rangée recomposée après être sortie de
 * l'écran) rend le focus à la DERNIÈRE carte visée de cette rangée, jamais à la première par défaut.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.homeRow(): Modifier = this.rowBleedStart(RowBleed).focusRestorer().focusGroup()

@Composable
private fun HomeSection(
    id: HomeSectionId,
    rows: HomeRows,
    playHistory: (WatchHistoryEntity) -> Unit,
    openPoster: (PosterCardUi) -> Unit,
    playChannel: (ChannelEntity) -> Unit,
    onShortcut: (ShortcutId) -> Unit,
) {
    val D = LocalDs.current
    when (id) {
        HomeSectionId.SHORTCUTS -> ShortcutsRow(rows.shortcuts, onShortcut)
        HomeSectionId.TOP10 -> Section(D.homeTop10) { Top10Row(rows.top10, openPoster) }
        HomeSectionId.RESUME -> Section(D.continueWatching) { ResumeRow(rows.resume, playHistory) }
        // Trakt : seulement ce qui est disponible dans la playlist (rien si le compte n'est pas lié).
        HomeSectionId.TRAKT_WATCHLIST -> Section(D.traktWatchlist) { PosterRow(rows.watchlist, openPoster) }
        HomeSectionId.TRAKT_RECOMMENDED -> Section(D.traktRecommended) { PosterRow(rows.recommended, openPoster) }
        HomeSectionId.TRAKT_TRENDING -> Section(D.traktTrending) { PosterRow(rows.trending, openPoster) }
        HomeSectionId.TRAKT_POPULAR -> Section(D.traktPopular) { PosterRow(rows.popular, openPoster) }
        HomeSectionId.TRAKT_UP_NEXT -> Section(D.traktUpNext) { PosterRow(rows.upNext, openPoster) }
        HomeSectionId.TRAKT_LIST_1, HomeSectionId.TRAKT_LIST_2, HomeSectionId.TRAKT_LIST_3, HomeSectionId.TRAKT_LIST_4, HomeSectionId.TRAKT_LIST_5 ->
            rows.traktLists.getOrNull(id.traktListIndex)?.let { l -> Section(D.traktListRow(l.name)) { PosterRow(l.cards, openPoster) } }
        HomeSectionId.RECENT_CHANNELS -> Section(D.recentlyWatched) { ResumeRow(rows.recentChannels, playHistory) }
        HomeSectionId.LATEST_MOVIES -> Section(D.latestMovies) { PosterRow(rows.latestMovies, openPoster) }
        HomeSectionId.LATEST_SERIES -> Section(D.latestSeries) { PosterRow(rows.latestSeries, openPoster) }
        HomeSectionId.BECAUSE_WATCHED -> Section(D.becauseWatched(rows.becauseTitle)) { PosterRow(rows.because, openPoster) }
        HomeSectionId.TOP_MOVIES -> Section(D.topRatedMovies) { PosterRow(rows.topMovies, openPoster) }
        HomeSectionId.TOP_SERIES -> Section(D.topRatedSeries) { PosterRow(rows.topSeries, openPoster) }
        HomeSectionId.CHANNELS -> Section(if (rows.showingFavorites) D.favoriteChannels else D.directTitle) {
            ChannelRow(rows.channels, playChannel, D.seeAll) { onShortcut(if (rows.showingFavorites) ShortcutId.FAVORITES else ShortcutId.LIVE) }
        }
    }
}

@Composable
private fun ResumeRow(cards: List<ResumeCardUi>, onPlay: (WatchHistoryEntity) -> Unit) {
    LazyRow(Modifier.homeRow(), horizontalArrangement = Arrangement.spacedBy(28.design), contentPadding = PaddingValues(start = RowBleed, end = 96.design)) {
        items(cards, key = { it.key }, contentType = { "resume" }) { c -> ResumeCard(c) { onPlay(c.entry) } }
    }
}

/** Rangée d'affiches (catalogue ou Trakt, films et séries mêlés) ; sélectionner ouvre la fiche existante. */
@Composable
private fun PosterRow(cards: List<PosterCardUi>, onOpen: (PosterCardUi) -> Unit) {
    LazyRow(Modifier.homeRow(), horizontalArrangement = Arrangement.spacedBy(28.design), contentPadding = PaddingValues(start = RowBleed, end = 96.design)) {
        items(cards, key = { it.key }, contentType = { "poster" }) { p ->
            com.ultratv.tv.nativeapp.ui.catalog.PosterCell(p.item, Modifier.width(PosterWidth), kind = p.kind, imageWidth = PosterWidth) { onOpen(p) }
        }
    }
}

@Composable
private fun ChannelRow(cards: List<ChannelCardUi>, onPlay: (ChannelEntity) -> Unit, seeAll: String, onSeeAll: () -> Unit) {
    LazyRow(Modifier.homeRow(), horizontalArrangement = Arrangement.spacedBy(24.design), contentPadding = PaddingValues(start = RowBleed, end = 96.design)) {
        items(cards, key = { it.channel.id }, contentType = { "channel" }) { c -> ChannelCard(c) { onPlay(c.channel) } }
        // « Tout voir » : dernière carte de la rangée (un lien en haut à droite du titre capterait le focus en descendant).
        item(key = "see-all", contentType = "see-all") { SeeAllCard(seeAll, onSeeAll) }
    }
}

/** Dernière carte d'une rangée : ouvre l'écran complet. */
@Composable
private fun SeeAllCard(label: String, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Surface, ringWidth = 5.design, modifier = Modifier.width(220.design).height(CHANNEL_CARD_HEIGHT)) { f ->
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.design))
            DIcon(Icons.Arrow, 26.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.4f)
        }
    }
}

/** « Top 10 aujourd'hui » : affiches avec leur rang (1 à 10) en pastille sur l'affiche. */
@Composable
private fun Top10Row(cards: List<PosterCardUi>, onOpen: (PosterCardUi) -> Unit) {
    LazyRow(Modifier.homeRow(), horizontalArrangement = Arrangement.spacedBy(28.design), contentPadding = PaddingValues(start = RowBleed, end = 96.design)) {
        itemsIndexed(cards, key = { _, p -> p.key }, contentType = { _, _ -> "top10" }) { i, p ->
            Box(Modifier.width(PosterWidth)) {
                com.ultratv.tv.nativeapp.ui.catalog.PosterCell(p.item, Modifier.fillMaxWidth(), kind = p.kind, imageWidth = PosterWidth) { onOpen(p) }
                // Pastille de rang en bas à gauche de l'affiche (le titre et la méta occupent ~100 de plus sous l'affiche).
                Text(
                    "${i + 1}", color = Ux.OnAccent, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 34.spx, maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.design, bottom = 108.design)
                        .clip(RoundedCornerShape(10.design)).background(Ux.Accent).padding(horizontal = 14.design, vertical = 2.design),
                )
            }
        }
    }
}

// ───────────────────────── Raccourcis ─────────────────────────

private val ShortcutHeight get() = 150.design

/** Couleurs de départ / d'arrivée du dégradé de chaque raccourci (sombres : texte blanc lisible dans les deux thèmes). */
private fun shortcutColors(id: ShortcutId): Pair<Color, Color> = when (id) {
    ShortcutId.LIVE -> Color(0xFF7A4312) to Color(0xFF2E1A0A)
    ShortcutId.MOVIES -> Color(0xFF1E4A99) to Color(0xFF0E1D3D)
    ShortcutId.SERIES -> Color(0xFF5E2E99) to Color(0xFF241040)
    ShortcutId.FAVORITES -> Color(0xFF9E2433) to Color(0xFF3D0E15)
    ShortcutId.GUIDE -> Color(0xFF176B61) to Color(0xFF082B27)
}

private fun shortcutIcon(id: ShortcutId): String = when (id) {
    ShortcutId.LIVE -> Icons.Live
    ShortcutId.MOVIES -> Icons.Movies
    ShortcutId.SERIES -> Icons.Series
    ShortcutId.FAVORITES -> Icons.Heart
    ShortcutId.GUIDE -> Icons.Guide
}

/** Cinq grandes cartes sous l'image « À la une » ; une seule ligne (pas de liste paresseuse : cinq éléments fixes). */
@Composable
private fun ShortcutsRow(items: List<ShortcutUi>, onSelect: (ShortcutId) -> Unit) {
    Row(Modifier.homeRow().padding(end = 96.design), horizontalArrangement = Arrangement.spacedBy(24.design)) {
        items.forEach { sc -> key(sc.id) { ShortcutCard(sc, Modifier.weight(1f)) { onSelect(sc.id) } } }
    }
}

@Composable
private fun ShortcutCard(sc: ShortcutUi, modifier: Modifier, onClick: () -> Unit) {
    val D = LocalDs.current
    val S = LocalStrings.current
    val (title, subtitle) = remember(sc.id, sc.count, D, S) {
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
    val brush = remember(sc.id) { shortcutColors(sc.id).let { (a, b) -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(a, b)) } }
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(24.design), bg = Color.Transparent, focusedBg = Color.Transparent, focusedScale = 1.04f, ringWidth = 5.design, modifier = modifier.height(ShortcutHeight)) { f ->
        Box(Modifier.fillMaxSize().background(brush)) {
            // Décor à droite : vignette (Films, Séries) ou logos de chaînes (Direct), décodés à leur taille réelle.
            if (sc.thumb != null) {
                ThumbImage(sc.thumb, "", Modifier.align(Alignment.CenterEnd).width(110.design).fillMaxHeight(), radius = 0, decodeSize = DpSize(110.design, ShortcutHeight))
                Box(Modifier.align(Alignment.CenterEnd).width(150.design).fillMaxHeight().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color(0x66000000)))))
            }
            if (sc.logos.isNotEmpty()) Row(Modifier.align(Alignment.CenterEnd).padding(end = 22.design), horizontalArrangement = Arrangement.spacedBy((-14).design)) {
                sc.logos.take(2).forEach { u -> LogoBox(u, "", Modifier.size(56.design), radius = 28, pad = 6, bg = Color(0xFFF2F2F5), decodeSize = DpSize(56.design, 56.design)) }
            }
            if (f) Box(Modifier.fillMaxSize().background(Color(0x1FFFFFFF)))
            Row(Modifier.fillMaxSize().padding(horizontal = 24.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                DIcon(shortcutIcon(sc.id), 44.design, Color.White, strokeWidth = 2f)
                Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.design)) {
                    Text(title, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Color(0xCCFFFFFF), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val PosterWidth get() = 200.design

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.design)) {
        SectionTitle(title)
        content()
    }
}

// ───────────────────────── Hero 440 px ─────────────────────────

private val HeroHeight get() = 640.design

@Composable
private fun Hero(
    h: HeroItem, extras: State<Map<String, HeroExtras>>, onWatch: (HeroItem) -> Unit, onInfo: (HeroItem) -> Unit,
    takeFocus: Boolean, onFocused: () -> Unit, onFocusTop: () -> Unit, index: Int = 0, count: Int = 1,
) {
    val D = LocalDs.current
    val S = LocalStrings.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    if (takeFocus) RequestInitialFocus(requester, hasFocus = { focused })
    val bg = Ux.Bg
    // Dégradés SIMPLES (aucun flou) : vers la gauche pour le texte, vers le bas pour fondre dans la page.
    val side = com.ultratv.tv.nativeapp.ui.design.startToEndBrush(0f to bg.copy(alpha = 0.96f), 0.38f to bg.copy(alpha = 0.78f), 0.72f to bg.copy(alpha = 0.2f), 1f to Color.Transparent)
    val bottom = remember(bg) { androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to bg) }
    Box(Modifier.fillMaxWidth().height(HeroHeight).background(Ux.SurfaceDeep)) {
        // UNE seule image de fond, décodée à la taille de l'écran. Fondu léger entre deux titres (coupé net si les animations sont désactivées).
        androidx.compose.animation.Crossfade(targetState = h, animationSpec = com.ultratv.tv.nativeapp.ui.design.LocalAnimations.current.tween(500), label = "hero") { cur ->
            Box(Modifier.fillMaxSize()) {
                if (cur.backdrop != null) {
                    BackdropImage(cur.backdrop, Modifier.fillMaxSize(), decodeSize = DpSize(1920.design, 640.design))
                } else {
                    // Sans image paysage : l'affiche 2:3 à droite, dans une zone fixe (jamais étirée).
                    PosterImage(cur.poster, cur.title, Modifier.align(Alignment.CenterEnd).padding(end = 150.design).height(560.design).width(373.design), radius = 24, kind = if (cur.kind == HeroItem.Kind.SERIES) com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV else com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE, decodeSize = DpSize(373.design, 560.design))
                }
            }
        }
        Box(Modifier.fillMaxSize().background(side))
        Box(Modifier.fillMaxSize().background(bottom))
        Column(
            Modifier.align(Alignment.BottomStart).width(1040.design).padding(start = 72.design, bottom = 56.design),
            verticalArrangement = Arrangement.spacedBy(20.design),
        ) {
            val badge = remember(h.kind, S) { (if (h.kind == HeroItem.Kind.SERIES) S.seriesTitle else S.moviesTitle).uppercase() }
            LiveBadge(badge)
            Text(
                h.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 72.spx, lineHeight = 76.spx,
                letterSpacing = (-1).spx, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            HeroInfoRow(h, extras)
            HeroPlot(h, extras)
            Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                PillButton(
                    D.watch, onClick = { onWatch(h) }, heightPx = 76, hPadPx = 40, fontPx = 28, weight = FontWeight.Bold,
                    iconPath = Icons.Play, iconFill = true, bg = Ux.Cta,
                    modifier = Modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused; if (it.isFocused) { onFocused(); onFocusTop() } },
                )
                PillButton(
                    D.homeMoreInfo, onClick = { onInfo(h) }, iconPath = Icons.Info,
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocusTop() },
                )
            }
        }
        // Points de pagination (titre affiché / nombre de titres « à la une »).
        if (count > 1) Row(Modifier.align(Alignment.BottomEnd).padding(end = 48.design, bottom = 40.design), horizontalArrangement = Arrangement.spacedBy(10.design)) {
            repeat(count) { i ->
                Box(Modifier.height(8.design).width(if (i == index) 28.design else 8.design).clip(RoundedCornerShape(4.design)).background(if (i == index) Color.White else Color.White.copy(alpha = 0.4f)))
            }
        }
    }
}

/** « 2013 | Drame | 1 h 41 min » puis pastilles IMDb et Rotten Tomatoes ; les notes arrivent plus tard (State lu ICI seulement). */
@Composable
private fun HeroInfoRow(h: HeroItem, extras: State<Map<String, HeroExtras>>) {
    val D = LocalDs.current
    val line = remember(h.key, D) { heroInfoLine(h.year, h.genre, h.runtimeMin?.let { runtimeLabel(it, D.hourShort, D.minShort) }) }
    val badges = heroRatingBadges(extras.value[h.key]?.ratings, h.rating)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
        if (line.isNotEmpty()) Text(line, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        badges.imdb?.let { Text(it, color = Color(0xFF0A0A0C), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 24.spx, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(8.design)).background(Color(0xFFF5C518)).padding(horizontal = 12.design, vertical = 3.design)) }
        badges.fallback?.let { Text(it, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1) }
        badges.rt?.let { Text("\uD83C\uDF45 $it", color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1) }
    }
}

/** Synopsis sur deux lignes (source, à défaut TMDB une fois résolu) ; rien si aucun n'existe. */
@Composable
private fun HeroPlot(h: HeroItem, extras: State<Map<String, HeroExtras>>) {
    val text = h.plot?.takeIf { it.isNotBlank() } ?: extras.value[h.key]?.overview
    if (text != null) Text(text, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, lineHeight = 36.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 920.design))
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

/** Carte « Reprendre » : vignette 16:9 (360×202) avec progression, titre 24, reste à voir 20. */
@Composable
private fun ResumeCard(c: ResumeCardUi, onClick: () -> Unit) {
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
    val cleanTitle = c.title
    val progress = c.progress
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Color.Transparent, modifier = Modifier.width(360.design)) { f ->
        Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
            Box(Modifier.fillMaxWidth().height(202.design)) {
                ThumbImage(e.poster, e.title, Modifier.fillMaxSize(), radius = 20, decodeSize = DpSize(360.design, 202.design))
                ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), heightPx = 6, track = Color(0x66000000))
            }
            Column(Modifier.padding(horizontal = 12.design).padding(bottom = 16.design), verticalArrangement = Arrangement.spacedBy(6.design)) {
                // Titre sur deux lignes (hauteur fixe : les cartes de la rangée restent alignées) — un titre d'épisode
                // « Série : Épisode » ne tient presque jamais sur une seule.
                Text(cleanTitle, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 23.spx, lineHeight = 29.spx, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta != null) Text(meta, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val CHANNEL_CARD_HEIGHT get() = 190.designT

/** Carte chaîne : logo, nom, pastille « En direct » et horaire du programme en cours (guide) ; OK lance la chaîne. */
@Composable
private fun ChannelCard(card: ChannelCardUi, onClick: () -> Unit) {
    val D = LocalDs.current
    val c = card.channel
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Surface, ringWidth = 5.design, modifier = Modifier.width(340.design).height(CHANNEL_CARD_HEIGHT)) { f ->
        Column(Modifier.fillMaxSize().padding(horizontal = 20.design, vertical = 18.design), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                LogoBox(c.logo, c.title, Modifier.width(72.design).height(52.design), radius = 8, pad = 4, bg = if (f) Color(0xFFE4E4E7) else Ux.Surface2, decodeSize = DpSize(72.design, 52.design))
                Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, lineHeight = 28.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Text(card.nowTitle ?: "", color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
                Box(Modifier.size(12.design).clip(CircleShape).background(Color(0xFF3DDC84)))
                Text(D.homeLiveNow, color = if (f) Ux.TextOnLight else Color(0xFF3DDC84), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1)
                if (card.timeRange != null) Text(card.timeRange, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
