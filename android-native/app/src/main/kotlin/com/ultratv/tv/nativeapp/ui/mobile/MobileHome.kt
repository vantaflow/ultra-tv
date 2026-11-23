package com.ultratv.tv.nativeapp.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.ultratv.tv.nativeapp.ui.home.block
import androidx.compose.ui.input.pointer.pointerInput
import com.ultratv.tv.nativeapp.i18n.heroPageOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import com.ultratv.tv.nativeapp.i18n.homeChannelsCount
import com.ultratv.tv.nativeapp.i18n.homeFavoritesCount
import com.ultratv.tv.nativeapp.i18n.homeGuideSubtitle
import com.ultratv.tv.nativeapp.i18n.homeMoviesCount
import com.ultratv.tv.nativeapp.i18n.homeSeriesCount
import com.ultratv.tv.nativeapp.i18n.locale
import com.ultratv.tv.nativeapp.ui.home.ShortcutId
import com.ultratv.tv.nativeapp.ui.home.ShortcutUi
import com.ultratv.tv.nativeapp.ui.home.formatCount
import com.ultratv.tv.nativeapp.ui.home.withUniqueKeys
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.traktPopular
import com.ultratv.tv.nativeapp.i18n.homeTop10
import com.ultratv.tv.nativeapp.i18n.traktListRow
import com.ultratv.tv.nativeapp.i18n.traktUpNext
import com.ultratv.tv.nativeapp.i18n.traktRecommended
import com.ultratv.tv.nativeapp.i18n.traktTrending
import com.ultratv.tv.nativeapp.i18n.traktAvailable
import com.ultratv.tv.nativeapp.i18n.traktWatchlist
import com.ultratv.tv.nativeapp.ui.design.BackdropImage
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.LogoMark
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.ThumbImage
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.home.ChannelCardUi
import com.ultratv.tv.nativeapp.ui.home.HeroItem
import com.ultratv.tv.nativeapp.ui.home.HomeRows
import com.ultratv.tv.nativeapp.ui.home.HomeSectionId
import com.ultratv.tv.nativeapp.ui.home.traktListIndex
import com.ultratv.tv.nativeapp.ui.home.libraryIndex
import com.ultratv.tv.nativeapp.ui.home.PosterCardUi
import com.ultratv.tv.nativeapp.ui.home.ResumeCardUi
import com.ultratv.tv.nativeapp.ui.home.mobileSectionOrder
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.remember

/**
 * Données de l'accueil tactile (extraites du ViewModel pour garder l'écran sans état). [rows] : les mêmes rangées
 * immuables que la TV (construites hors fil principal) ; le pourcentage de synchro est passé à part, en lecture différée.
 */
@androidx.compose.runtime.Immutable
data class MobileHomeState(
    val providersLoaded: Boolean,
    val hasProviders: Boolean,
    val hero: HeroItem?,
    val rows: HomeRows,
    val profileInitial: String,
    val profileColor: Int,
    /** Carrousel « À la une » : page affichée et nombre de pages (balayage et pastilles touchables). */
    val heroIndex: Int = 0,
    val heroCount: Int = 1,
)

/** En-tête commun aux écrans principaux tactiles : logo, recherche, profil (maquette MobileAccueil). */
@Composable
fun MobileTopBar(profileInitial: String, profileColor: Int, onSearch: () -> Unit, onProfile: () -> Unit) {
    val M = LocalMobileStrings.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoMark(68)
            Row {
                Text("ULTRA ", fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = 0.8.sp, color = Ux.Text)
                Text("TV", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = 0.8.sp, color = Ux.Text3)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconCircle(Icons.Search, M.a11ySearch, Ux.Surface, Ux.Text, onSearch)
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = M.a11yProfile, onClick = onProfile).semantics { contentDescription = M.a11yProfile },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(profileColor)), contentAlignment = Alignment.Center) {
                    Text(profileInitial, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

/** Bouton rond de 48 dp (pastille de 40) avec description pour TalkBack. */
@Composable
fun IconCircle(icon: String, description: String, bg: Color, ink: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = description, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) { DIcon(icon, 20.dp, ink) }
    }
}

/**
 * Accueil tactile en colonne PARESSEUSE : seules les rangées visibles sont composées (avant : Column + verticalScroll,
 * tout composé d'un coup). Clés et contentType par rangée ; ordre identique à la TV ([mobileSectionOrder]).
 */
@Composable
fun MobileHomeScreen(
    state: MobileHomeState,
    syncPercent: () -> Int?,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onGoLive: () -> Unit,
    onGoSettings: () -> Unit,
    onGoGuide: () -> Unit,
    onGoFavorites: () -> Unit,
    onShortcut: (ShortcutId) -> Unit = {},
    onPlay: (url: String, title: String) -> Unit,
    onPlayHistory: (WatchHistoryEntity) -> Unit,
    onOpenHero: (HeroItem) -> Unit,
    onOpenMovie: (Long) -> Unit = {},
    onOpenSeries: (Long) -> Unit = {},
    onHeroStep: (Int) -> Unit = {},
    /** Disposition des rangées (même préférence que Réglages › Affichage) ; null = pas de menu de rangée. */
    homeLayout: com.ultratv.tv.nativeapp.ui.home.HomeLayout? = null,
    onHomeLayout: (com.ultratv.tv.nativeapp.ui.home.HomeLayout) -> Unit = {},
) {
    val D = LocalDs.current
    val M = LocalMobileStrings.current
    // Appui long sur le TITRE d'une rangée : menu de la rangée (monter, descendre, masquer, réglages des rangées).
    var menuFor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.ultratv.tv.nativeapp.ui.home.HomeBlock?>(null) }
    val rowMenu: (HomeSectionId) -> (() -> Unit)? = { id -> if (homeLayout == null) null else ({ menuFor = id.block }) }
    val wide = rememberWindowClass() != WindowClass.COMPACT
    val rows = state.rows
    val sections = remember(rows.order) { mobileSectionOrder(rows.order) }
    val openPoster: (PosterCardUi) -> Unit = remember(onOpenMovie, onOpenSeries) { { p -> if (p.isShow) onOpenSeries(p.item.id) else onOpenMovie(p.item.id) } }
    val playChannel: (ChannelEntity) -> Unit = remember(onPlay) { { c -> onPlay(c.streamUrl, c.title) } }
    val startPairing = remember(onGoSettings) { { com.ultratv.tv.nativeapp.StartupNav.startPairing.value = true; onGoSettings() } }
    TouchRefresh(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item(key = "topbar", contentType = "topbar") { MobileTopBar(state.profileInitial, state.profileColor, onSearch, onProfile) }
            val h = state.hero
            when {
                h != null -> item(key = "top", contentType = "hero") { MobileHero(h, D, wide, onOpenHero, state.heroIndex, state.heroCount, onHeroStep) }
                state.providersLoaded && !state.hasProviders -> item(key = "top", contentType = "notice") {
                    MobileNotice(D.homeNoSource, D.syncCloudHint, M.fromCloud, startPairing, secondary = D.addSource to onGoSettings)
                }
                state.providersLoaded -> item(key = "top", contentType = "notice") { MobileNotice(D.homeEmpty, null, null, {}, syncPercent) }
            }
            items(sections, key = { it.name }, contentType = { it.contentType }) { id ->
                when (id) {
                    HomeSectionId.SHORTCUTS -> MobileShortcuts(rows.shortcuts, onShortcut)
                    HomeSectionId.RESUME -> Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        SectionHeader(D.continueWatching, null, {}, onTitleLongPress = rowMenu(id))
                        val resume = androidx.compose.runtime.remember(rows.resume) { rows.resume.withUniqueKeys() }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(resume, key = { it.key }, contentType = { "resume" }) { c -> ResumeCard(c, D) { onPlayHistory(c.entry) } }
                        }
                    }
                    HomeSectionId.TRAKT_AVAILABLE -> TraktPosterRow(D.traktAvailable, rows.availableNow, wide, openPoster, rowMenu(id))
                    HomeSectionId.TRAKT_WATCHLIST -> TraktPosterRow(D.traktWatchlist, rows.watchlist, wide, openPoster, rowMenu(id))
                    HomeSectionId.TRAKT_RECOMMENDED -> TraktPosterRow(D.traktRecommended, rows.recommended, wide, openPoster, rowMenu(id))
                    HomeSectionId.TRAKT_TRENDING -> TraktPosterRow(D.traktTrending, rows.trending, wide, openPoster, rowMenu(id))
                    HomeSectionId.TRAKT_POPULAR -> TraktPosterRow(D.traktPopular, rows.popular, wide, openPoster, rowMenu(id))
                    HomeSectionId.TRAKT_UP_NEXT -> TraktPosterRow(D.traktUpNext, rows.upNext, wide, openPoster, rowMenu(id))
                    HomeSectionId.BECAUSE_WATCHED -> TraktPosterRow(D.becauseWatched(rows.becauseTitle), rows.because, wide, openPoster, rowMenu(id))
                    HomeSectionId.TOP_MOVIES -> TraktPosterRow(D.topRatedMovies, rows.topMovies, wide, openPoster, rowMenu(id))
                    HomeSectionId.LATEST_MOVIES -> TraktPosterRow(D.latestMovies, rows.latestMovies, wide, openPoster, rowMenu(id))
                    HomeSectionId.LATEST_SERIES -> TraktPosterRow(D.latestSeries, rows.latestSeries, wide, openPoster, rowMenu(id))
                    HomeSectionId.TOP10 -> TraktPosterRow(D.homeTop10, rows.top10, wide, openPoster, rowMenu(id))
                    HomeSectionId.LIBRARY_1, HomeSectionId.LIBRARY_2, HomeSectionId.LIBRARY_3, HomeSectionId.LIBRARY_4, HomeSectionId.LIBRARY_5, HomeSectionId.LIBRARY_6 ->
                        rows.libraries.getOrNull(id.libraryIndex)?.let { l -> TraktPosterRow(D.catalogSortName(com.ultratv.tv.nativeapp.ui.catalog.CatalogFilter.Sort.RECENT) + " · " + l.name, l.cards, wide, openPoster, rowMenu(id)) }
                    HomeSectionId.RECENT_CHANNELS -> Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        SectionHeader(D.recentlyWatched, null, {}, onTitleLongPress = rowMenu(id))
                        val recent = androidx.compose.runtime.remember(rows.recentChannels) { rows.recentChannels.withUniqueKeys() }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(recent, key = { it.key }, contentType = { "resume" }) { c -> ResumeCard(c, D) { onPlayHistory(c.entry) } }
                        }
                    }
                    HomeSectionId.TOP_SERIES -> TraktPosterRow(D.topRatedSeries, rows.topSeries, wide, openPoster, rowMenu(id))
                    HomeSectionId.TRAKT_LIST_1, HomeSectionId.TRAKT_LIST_2, HomeSectionId.TRAKT_LIST_3, HomeSectionId.TRAKT_LIST_4, HomeSectionId.TRAKT_LIST_5 ->
                        rows.traktLists.getOrNull(id.traktListIndex)?.let { l -> TraktPosterRow(D.traktListRow(l.name), l.cards, wide, openPoster, rowMenu(id)) }
                    HomeSectionId.CHANNELS -> Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        SectionHeader(if (rows.showingFavorites) D.favoriteChannels else D.directTitle, M.seeAll, if (rows.showingFavorites) onGoFavorites else onGoLive, onTitleLongPress = rowMenu(id))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(rows.channels.distinctBy { it.channel.id }, key = { it.channel.id }, contentType = { "channel" }) { c -> FavChannelCard(c) { playChannel(c.channel) } }
                        }
                    }
                    // Rangées propres à la TV (filtrées par mobileSectionOrder).
                    else -> Unit
                }
            }
        }
    }
    val target = menuFor
    if (target != null && homeLayout != null) com.ultratv.tv.nativeapp.ui.home.HomeRowMenuHost(
        target, homeLayout, com.ultratv.tv.nativeapp.ui.home.movableShown(sections), onSave = onHomeLayout, onDone = { menuFor = null },
    )
}

/** Rangée d'affiches Trakt (films et séries mêlés, ordre de Trakt) ; toucher ouvre la fiche existante. */
@Composable
private fun TraktPosterRow(title: String, cards: List<PosterCardUi>, wide: Boolean, onOpen: (PosterCardUi) -> Unit, onTitleLongPress: (() -> Unit)? = null) {
    val w = if (wide) 150.dp else 110.dp
    val unique = androidx.compose.runtime.remember(cards) { cards.withUniqueKeys() }
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        SectionHeader(title, null, {}, onTitleLongPress = onTitleLongPress)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            items(unique, key = { it.key }, contentType = { "poster" }) { p ->
                com.ultratv.tv.nativeapp.ui.catalog.PosterCell(p.item, Modifier.width(w), kind = p.kind, imageWidth = w) { onOpen(p) }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String?, onAction: () -> Unit, onTitleLongPress: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        val longPress = if (onTitleLongPress == null) Modifier else Modifier.pointerInput(onTitleLongPress) {
            detectTapGestures(onLongPress = { onTitleLongPress() })
        }
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).then(longPress).padding(vertical = 6.dp))
        if (action != null) Text(action, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 14.dp))
    }
}

/** Hero « À la une » : carte 216 dp (tablette : 280), visuel plein cadre, voile, titre et bouton Regarder. */
@Composable
private fun MobileHero(h: HeroItem, D: DesignStrings, wide: Boolean, onOpen: (HeroItem) -> Unit, index: Int = 0, count: Int = 1, onStep: (Int) -> Unit = {}) {
    val S = LocalStrings.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Balayage horizontal : page suivante / précédente (inversé en arabe) ; seuil de 48 dp pour ne pas gêner le défilement vertical.
    val swipe = if (count > 1) Modifier.pointerInput(count, rtl) {
        var total = 0f
        val threshold = with(density) { 48.dp.toPx() }
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { com.ultratv.tv.nativeapp.ui.home.heroSwipeStep(total, rtl, threshold)?.let(onStep) },
        ) { change, dx -> total += dx; change.consume() }
    } else Modifier
    val shape = RoundedCornerShape(22.dp)
    val heightDp = if (wide) 300.dp else 216.dp
    // Taille de décodage : largeur de la fenêtre moins les marges (le cadre n'est jamais plus large).
    val widthDp = (com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current - 40f).coerceAtLeast(1f).dp
    val size = androidx.compose.ui.unit.DpSize(widthDp, heightDp)
    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(heightDp).clip(shape).background(Ux.Tone).then(swipe).clickable(onClickLabel = D.watch) { onOpen(h) }) {
        if (h.backdrop != null) BackdropImage(h.backdrop, Modifier.fillMaxSize(), decodeSize = size)
        else PosterImage(h.poster, h.title, Modifier.align(Alignment.CenterEnd).fillMaxSize(), radius = 0, kind = if (h.kind == HeroItem.Kind.SERIES) com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV else com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE, decodeSize = size)
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xC70A0A0C)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    (if (h.kind == HeroItem.Kind.SERIES) S.seriesTitle else S.moviesTitle).uppercase(), color = Ux.OnAccent, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.1.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Ux.Accent).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                if (h.meta.isNotEmpty()) Text(h.meta, color = Color(0xFFC4C4CC), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(h.title, color = Color(0xFFF5F5F7), fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Pastilles touchables (cible de 32 dp chacune) : toucher une pastille affiche sa page.
        if (count > 1) Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            repeat(count) { i ->
                val label = D.heroPageOf(i + 1, count)
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable(onClickLabel = label) { if (i != index) onStep(i - index) }.semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.height(8.dp).width(if (i == index) 20.dp else 8.dp).clip(RoundedCornerShape(4.dp)).background(if (i == index) Color.White else Color.White.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

@Composable
private fun MobileNotice(title: String, hint: String?, action: String?, onAction: () -> Unit, percent: (() -> Int?)? = null, secondary: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ux.SurfaceDeep).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        if (hint != null) Text(hint, color = Ux.Text2, fontFamily = Manrope, fontSize = 14.sp)
        // Pourcentage lu ici seulement (lecture différée) : seule cette carte se recompose pendant la synchro.
        if (percent != null || action == null) ProgressLine((percent?.invoke() ?: 0) / 100f, Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)), heightPx = 8, track = Ux.Surface2)
        if (action != null) PillButton(action, onAction, bg = Ux.Cta, weight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        if (secondary != null) PillButton(secondary.first, secondary.second, modifier = Modifier.fillMaxWidth())
    }
}

/** Carte « Reprendre » : 200 dp de large, vignette 112 dp avec progression, titre et temps restant. */
@Composable
private fun ResumeCard(c: ResumeCardUi, D: DesignStrings, onClick: () -> Unit) {
    val e = c.entry
    val progress = c.progress
    // Titre nettoyé dans le ViewModel ; seul le libellé localisé est formaté ici, une fois.
    val meta = remember(c.minutesLeft, D) {
        val mins = c.minutesLeft
        mins?.let { com.ultratv.tv.nativeapp.ui.home.minutesLeftLabel(D, it) }
    }
    val title = c.title
    Column(Modifier.width(200.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = title, onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(112.dp)) {
            ThumbImage(e.poster, e.title, Modifier.fillMaxSize(), radius = 28, decodeSize = androidx.compose.ui.unit.DpSize(200.dp, 112.dp))
            ProgressLine(progress, Modifier.align(Alignment.BottomStart).fillMaxWidth(), heightPx = 8, track = Color(0x66000000))
        }
        Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) Text(meta, color = Ux.Text3, fontFamily = Manrope, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Carte chaîne 120×84 dp : logo en boîte fixe, nom sur une ligne ; le programme en cours sert de description TalkBack. */
@Composable
private fun FavChannelCard(card: ChannelCardUi, onClick: () -> Unit) {
    val c = card.channel
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(14.dp), bg = Ux.SurfaceDeep, modifier = Modifier.width(120.dp).height(84.dp).semantics { contentDescription = c.title + (card.nowTitle?.let { " — $it" } ?: "") }) { _ ->
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            LogoBox(c.logo, c.title, Modifier.width(44.dp).height(28.dp), radius = 12, pad = 4, decodeSize = androidx.compose.ui.unit.DpSize(44.dp, 28.dp))
            Text(c.title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bouton « Retour » rond des fiches (maquette MobileFiche) : pastille sombre translucide, chevron, 48 dp tactiles. */
@Composable
fun MobileBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val M = LocalMobileStrings.current
    Box(modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = M.a11yBack, onClick = onBack).semantics { contentDescription = M.a11yBack }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xB30A0A0C)), contentAlignment = Alignment.Center) {
            DIcon(MobileIcons.Back, 20.dp, Color(0xFFF5F5F7), strokeWidth = 2.4f)
        }
    }
}

/** Tuile d'action d'une fiche (Ma liste, Enregistrer, Bande-annonce) : 64 dp de haut, icône + libellé 12 sp. */
@Composable
fun MobileActionTile(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, fill: Boolean = false, wrap: Boolean = false) {
    // [wrap] : la tuile grandit avec son libellé (5 lignes au plus, centré) au lieu de le couper ; sinon 64 dp et une ligne (inchangé).
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.dp), bg = Ux.SurfaceDeep, modifier = (if (wrap) modifier.heightIn(min = 64.dp) else modifier.height(64.dp)).semantics { contentDescription = label }) { _ ->
        Column(if (wrap) Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp) else Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            DIcon(icon, 20.dp, if (active) Ux.Accent else Ux.Text, fill = fill)
            Text(label, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = if (wrap) 5 else 1, overflow = TextOverflow.Ellipsis, textAlign = if (wrap) androidx.compose.ui.text.style.TextAlign.Center else null)
        }
    }
}

/** Pilule d'action principale (Lecture) : 52 dp, blanche (encre en thème clair), icône lecture. */
@Composable
fun MobilePrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(26.dp), bg = Ux.Cta, modifier = modifier.fillMaxWidth().height(52.dp)) { _ ->
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            DIcon(Icons.Play, 18.dp, Ux.TextOnLight, fill = true)
            Text(label, color = Ux.TextOnLight, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pilule secondaire pleine largeur (Reprendre à …). */
@Composable
fun MobileSecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(24.dp), bg = Ux.Surface2, modifier = modifier.fillMaxWidth().height(48.dp)) { _ ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Ligne de méta (année · durée · genre · note) suivie de pastilles cerclées (qualité, langue). */
@Composable
fun MetaRow(bits: List<String>, badges: List<String>) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        bits.forEachIndexed { i, b ->
            if (i > 0) Text("·", color = Ux.Text2, fontFamily = Manrope, fontSize = 13.sp)
            Text(b, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        }
        badges.forEach { badge ->
            Text(badge, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1,
                modifier = Modifier.border(1.5.dp, Ux.LineKey, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/** Synopsis sur 4 lignes puis « Plus » / « Moins ». */
@Composable
fun ExpandableText(text: String) {
    val M = LocalMobileStrings.current
    var open by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableStateOf(false) }
    var overflow by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableStateOf(false) }
    Column {
        Text(text, color = Ux.Text2, fontFamily = Manrope, fontSize = 14.sp, lineHeight = 22.sp, maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis, onTextLayout = { if (!open) overflow = it.hasVisualOverflow })
        if (overflow || open) Text(
            if (open) M.lessInfo else M.moreInfo, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 14.dp, horizontal = 4.dp),
        )
    }
}

/**
 * Cadre commun des fiches tactiles : visuel (haut de 300 dp en compact, panneau gauche sur tablette large),
 * bouton Retour flottant, contenu défilant. Le contenu est fourni par la fiche.
 */
@Composable
fun MobileDetailFrame(backdrop: String?, poster: String?, title: String, onBack: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val twoPane = usesTwoPane(com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp.current)
    Box(Modifier.fillMaxSize().background(Ux.Bg)) {
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.42f).fillMaxSize().background(Ux.Tone)) { com.ultratv.tv.nativeapp.ui.movies.DetailVisual(backdrop, poster, title) }
                Column(Modifier.weight(0.58f).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 56.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(300.dp).background(Ux.Tone)) {
                    com.ultratv.tv.nativeapp.ui.movies.DetailVisual(backdrop, poster, title)
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.5f to Color.Transparent, 1f to Ux.Bg)))
                }
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 24.dp).offset(y = (-56).dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
            }
        }
        MobileBackButton(onBack, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp).windowInsetsPadding(WindowInsets.statusBars).testTag("dt-back"))
    }
}


/** Raccourcis (Direct, Films, Séries, Favoris, Guide) en défilement horizontal : chips colorées avec titre et compteur. */
@Composable
private fun MobileShortcuts(items: List<ShortcutUi>, onSelect: (ShortcutId) -> Unit) {
    val D = LocalDs.current
    val S = LocalStrings.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(items, key = { it.id }, contentType = { "shortcut" }) { sc ->
            val (title, sub) = remember(sc.id, sc.count, D, S) {
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
            val color = when (sc.id) {
                ShortcutId.LIVE -> Color(0xFF5A3210); ShortcutId.MOVIES -> Color(0xFF173A78); ShortcutId.SERIES -> Color(0xFF47237A)
                ShortcutId.FAVORITES -> Color(0xFF7A1B28); ShortcutId.GUIDE -> Color(0xFF11524A)
            }
            Column(
                Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).background(color).clickable(onClickLabel = title) { onSelect(sc.id) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = Color(0xCCFFFFFF), fontFamily = Manrope, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
