package com.ultratv.tv.nativeapp.ui.home

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
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.traktPopular
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
    val hero by vm.hero.collectAsStateWithLifecycle()
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

    if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
        val profileVm: com.ultratv.tv.nativeapp.ui.profile.ProfileViewModel = hiltViewModel()
        val prof by profileVm.current.collectAsStateWithLifecycle()
        com.ultratv.tv.nativeapp.ui.mobile.MobileHomeScreen(
            state = com.ultratv.tv.nativeapp.ui.mobile.MobileHomeState(loaded, providers.isNotEmpty(), hero, rows, prof?.initial ?: "K", prof?.color ?: 0xFFD91E2B.toInt()),
            syncPercent = { syncPct.value },
            onOpenMovie = onOpenMovie, onOpenSeries = onOpenSeries,
            onSearch = onGoSearch, onProfile = { profileVm.requestSwitch() }, onGoLive = onGoLive, onGoSettings = onGoSettings, onGoGuide = onGoGuide, onGoFavorites = onGoFavorites,
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
    CompositionLocalProvider(LocalBringIntoViewSpec provides HomePivotSpec) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            // Marge gauche en contentPadding (et non en padding extérieur) : les rangées débordent de RowBleed dans
            // cette marge sans sortir de la zone de rognage de la liste — la première carte agrandie n'est pas coupée.
            contentPadding = PaddingValues(start = 72.design, top = 54.design, bottom = 54.design),
            verticalArrangement = Arrangement.spacedBy(48.design),
        ) {
            if (slot != HomeTopSlot.NONE) item(key = "top", contentType = slot.name) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides inheritedSpec) {
                    when (slot) {
                        HomeTopSlot.HERO -> if (h != null) Hero(h, onOpen = openHero, onGuide = onGoGuide, takeFocus = !heroFocusDone, onFocused = onHeroFocused)
                        HomeTopSlot.NO_SOURCE -> EmptyCard(D.homeNoSource, D.syncCloudHint, D.addSource, onGoSettings, D.cardCloud, onGoCloud)
                        // Sources héritées non prises en charge seulement (ex. Stalker, retiré en 1.1.1) : rien ne se chargera, on le dit.
                        HomeTopSlot.UNSUPPORTED -> EmptyCard(D.unsupportedSource, D.unsupportedSourceHint, D.openSourceSettings, onGoSettings, takeFocus = false)
                        // Source en erreur : message lisible, bouton explicite, aucun focus d'office (l'utilisateur reste sur le rail : « Accueil » n'ouvre pas les Réglages).
                        HomeTopSlot.FAILURE -> if (f != null) EmptyCard(
                            LocalStrings.current.sync.messageFor(f.kind), D.sourceErrorBody(f.provider), D.openSourceSettings, onGoSettings,
                            action2 = D.retry, onAction2 = { dataState.retry(f.providerId) }, takeFocus = false,
                        )
                        HomeTopSlot.LOADING -> LoadingCard(D.homeEmpty) { syncPct.value }
                        HomeTopSlot.NONE -> Unit
                    }
                }
            }
            items(rows.order, key = { it.name }, contentType = { it.contentType }) { id ->
                CompositionLocalProvider(LocalBringIntoViewSpec provides inheritedSpec) {
                    HomeSection(id, rows, playHistory, openPoster, playChannel)
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
) {
    val D = LocalDs.current
    when (id) {
        HomeSectionId.RESUME -> Section(D.continueWatching) { ResumeRow(rows.resume, playHistory) }
        // Trakt : seulement ce qui est disponible dans la playlist (rien si le compte n'est pas lié).
        HomeSectionId.TRAKT_WATCHLIST -> Section(D.traktWatchlist) { PosterRow(rows.watchlist, openPoster) }
        HomeSectionId.TRAKT_RECOMMENDED -> Section(D.traktRecommended) { PosterRow(rows.recommended, openPoster) }
        HomeSectionId.TRAKT_TRENDING -> Section(D.traktTrending) { PosterRow(rows.trending, openPoster) }
        HomeSectionId.TRAKT_POPULAR -> Section(D.traktPopular) { PosterRow(rows.popular, openPoster) }
        HomeSectionId.RECENT_CHANNELS -> Section(D.recentlyWatched) { ResumeRow(rows.recentChannels, playHistory) }
        HomeSectionId.LATEST_MOVIES -> Section(D.latestMovies) { PosterRow(rows.latestMovies, openPoster) }
        HomeSectionId.LATEST_SERIES -> Section(D.latestSeries) { PosterRow(rows.latestSeries, openPoster) }
        HomeSectionId.CHANNELS -> Section(if (rows.showingFavorites) D.favoriteChannels else LocalStrings.current.navLive) { ChannelRow(rows.channels, playChannel) }
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
private fun ChannelRow(cards: List<ChannelCardUi>, onPlay: (ChannelEntity) -> Unit) {
    LazyRow(Modifier.homeRow(), horizontalArrangement = Arrangement.spacedBy(24.design), contentPadding = PaddingValues(start = RowBleed, end = 96.design)) {
        items(cards, key = { it.channel.id }, contentType = { "channel" }) { c -> ChannelCard(c) { onPlay(c.channel) } }
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

@Composable
private fun Hero(h: HeroItem, onOpen: (HeroItem) -> Unit, onGuide: () -> Unit, takeFocus: Boolean, onFocused: () -> Unit) {
    val D = LocalDs.current
    val S = LocalStrings.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    if (takeFocus) RequestInitialFocus(requester, hasFocus = { focused })
    Box(
        Modifier.fillMaxWidth().padding(end = 96.design).height(440.design)
            .clip(RoundedCornerShape(32.design)).background(Ux.SurfaceDeep),
    ) {
        // Visuel : zone fixe de 760×440 à droite. Paysage → Crop plein cadre ; sinon l'affiche 2:3
        // dans sa propre zone fixe (jamais étirée, jamais sous le texte), sur la couleur du cadre.
        Box(Modifier.align(Alignment.CenterEnd).width(760.design).fillMaxHeight().background(Ux.Tone)) {
            if (h.backdrop != null) {
                BackdropImage(h.backdrop, Modifier.fillMaxSize(), decodeSize = DpSize(760.design, 440.design))
            } else {
                PosterImage(h.poster, h.title, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(293.design), radius = 0, kind = if (h.kind == HeroItem.Kind.SERIES) com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV else com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE, decodeSize = DpSize(293.design, 440.design))
            }
            // Voile de lisibilité : fondu vers le fond du cadre côté texte.
            Box(Modifier.fillMaxSize().background(com.ultratv.tv.nativeapp.ui.design.startToEndBrush(0f to Ux.SurfaceDeep, 0.45f to Ux.SurfaceDeep.copy(alpha = 0.6f), 1f to Color.Transparent)))
        }
        Column(
            Modifier.fillMaxHeight().width(880.design).padding(56.design),
            verticalArrangement = Arrangement.spacedBy(24.design, Alignment.Bottom),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
                val badge = remember(h.kind, S) { (if (h.kind == HeroItem.Kind.SERIES) S.seriesTitle else S.moviesTitle).uppercase() }
                LiveBadge(badge)
                // Méta formatée dans le ViewModel (HeroItem.meta), plus de String.format à la composition.
                if (h.meta.isNotEmpty()) Text(h.meta, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                h.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 64.spx, lineHeight = 67.spx,
                letterSpacing = (-1).spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 820.design),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                PillButton(
                    D.watch, onClick = { onOpen(h) }, heightPx = 76, hPadPx = 40, fontPx = 28, weight = FontWeight.Bold,
                    iconPath = Icons.Play, iconFill = true, bg = Ux.Cta,
                    modifier = Modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() },
                )
                PillButton(D.tvGuide, onClick = onGuide)
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, hint: String, action: String, onAction: () -> Unit, action2: String? = null, onAction2: () -> Unit = {}, takeFocus: Boolean = true) {
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

/** Carte chaîne 220×132 (maquette) : nom 24 + logo en boîte fixe, programme en cours 18. */
@Composable
private fun ChannelCard(card: ChannelCardUi, onClick: () -> Unit) {
    val c = card.channel
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(20.design), bg = Ux.Surface, modifier = Modifier.width(220.design).height(132.design)) { f ->
        Column(Modifier.fillMaxSize().padding(20.design), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.design))
                LogoBox(c.logo, c.title, Modifier.width(48.design).height(32.design), radius = 6, pad = 3, bg = if (f) Color(0xFFE4E4E7) else Ux.Surface2, decodeSize = DpSize(48.design, 32.design))
            }
            Text(
                card.nowTitle ?: "", color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
