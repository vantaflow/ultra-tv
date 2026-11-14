package com.ultratv.tv.nativeapp.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.SimpleStrings
import com.ultratv.tv.nativeapp.ui.catalog.CatalogFilter
import com.ultratv.tv.nativeapp.ui.catalog.CatalogGridViewModel
import com.ultratv.tv.nativeapp.ui.catalog.CatalogKind
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.getOrNull
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LogoBox
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch

/*
 * Écrans du mode simple (maquette « Mode simple ») : chaque écran habituel a ici son équivalent — gros textes, une
 * action principale, rappel des seules touches utiles en bas. Les données et les actions viennent des MÊMES ViewModels
 * que les écrans habituels (lecture, reprise, favoris identiques) ; seule la présentation change.
 */

/** Cadre commun : marges, en-tête (retour + titre + action à droite), contenu, pied (touches). */
@Composable
internal fun SimpleFrame(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    hints: List<Pair<String, String>> = emptyList(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val touch = LocalTouch.current
    Column(
        Modifier.fillMaxSize().background(Ux.Bg).then(if (touch) Modifier.padding(16.design) else Modifier.padding(start = 96.design, end = 96.design, top = 56.design, bottom = 44.design)),
        verticalArrangement = Arrangement.spacedBy(28.design),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
            FocusSurface(onClick = onBack, shape = CircleShape, bg = Ux.Surface2, ringWidth = 6.design, focusedScale = 1f, modifier = Modifier.size(72.design)) { f ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DIcon("M15 6l-6 6 6 6", 34.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.6f) }
            }
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 60.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            action?.invoke()
        }
        content()
        if (hints.isNotEmpty() && !touch) Row(horizontalArrangement = Arrangement.spacedBy(40.design)) { hints.forEach { (k, l) -> KeyHintBig(k, l) } }
    }
}

/** Grand bouton-pilule (primaire : clair ; secondaire : sombre). */
@Composable
internal fun SimpleButton(label: String, iconPath: String?, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, iconFill: Boolean = false) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(999.design), bg = if (primary) Ux.Text else Ux.Surface2, ringWidth = 8.design, focusedScale = 1.02f, modifier = modifier.height(104.designT)) { f ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 44.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            val ink = if (primary || f) Ux.TextOnLight else Ux.Text
            if (iconPath != null) DIcon(iconPath, 42.design, ink, fill = iconFill, strokeWidth = 2.2f)
            Text(label, color = ink, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 38.spx, maxLines = 1)
        }
    }
}

@Composable
internal fun SimplePill(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(999.design), bg = if (selected) Ux.Text else Ux.Surface2, ringWidth = 6.design, focusedScale = 1f, modifier = Modifier.height(72.designT)) { f ->
        Box(Modifier.fillMaxHeight().padding(horizontal = 30.design), contentAlignment = Alignment.Center) {
            Text(label, color = if (f || selected) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 30.spx, maxLines = 1)
        }
    }
}

// ── Films / Séries ─────────────────────────────────────────────────────────────────────────────────

/** Onglets du catalogue simple : tri seulement (ni catégories, ni filtres). */
enum class SimpleCatalogTab(val sort: CatalogFilter.Sort) { NEW(CatalogFilter.Sort.RECENT), TOP(CatalogFilter.Sort.RATING), AZ(CatalogFilter.Sort.TITLE) }

@Composable
fun SimpleCatalogScreen(kind: CatalogKind, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val vm: CatalogGridViewModel = hiltViewModel(key = "simple-${kind.name}")
    val S = SimpleStrings(LocalDs.current.lang)
    val D = LocalDs.current
    LaunchedEffect(kind) { vm.bind(kind) }
    var tab by remember { mutableStateOf(SimpleCatalogTab.NEW) }
    val ready by vm.ready.collectAsState()
    // Tri de la visite (jamais enregistré : l'écran habituel garde le sien). Posé une fois les préférences lues.
    LaunchedEffect(tab, ready) { if (ready) vm.setFilterTransient(CatalogFilter(sort = tab.sort)) }
    val items = vm.items.collectAsLazyPagingItems()
    val openSearch = com.ultratv.tv.nativeapp.ui.catalog.rememberScopedSearch(kind)
    val touch = LocalTouch.current
    SimpleFrame(
        title = if (kind == CatalogKind.MOVIES) D.moviesTitle else D.seriesTitle, onBack = onBack,
        action = { SimpleButton(LocalStrings.current.navSearch, Icons.Search, primary = false, onClick = openSearch) },
        hints = listOf("OK" to S.keyOpen, "↩" to S.keyBack),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
            SimpleCatalogTab.entries.forEach { t -> SimplePill(when (t) { SimpleCatalogTab.NEW -> S.tabNew; SimpleCatalogTab.TOP -> S.tabTop; SimpleCatalogTab.AZ -> S.tabAz }, tab == t) { tab = t } }
        }
        val first = remember { FocusRequester() }
        var hasFocus by remember { mutableStateOf(false) }
        RequestInitialFocus(first, hasFocus = { hasFocus }, key = items.itemCount > 0)
        LazyVerticalGrid(
            // TV : colonnes adaptatives (5 en taille Grande, 6 en Moyenne, 7 en Petite) : le zoom du mode simple montre plus d'affiches.
            if (touch) GridCells.Fixed(3) else GridCells.Adaptive(140.dp),
            Modifier.weight(1f).fillMaxWidth().onFocusChanged { hasFocus = it.hasFocus }.focusRestorer().focusGroup(),
            contentPadding = PaddingValues(top = 12.design, bottom = 24.design),
            horizontalArrangement = Arrangement.spacedBy(36.design), verticalArrangement = Arrangement.spacedBy(36.design),
        ) {
            items(items.itemCount, key = { i -> items.getOrNull(i)?.id ?: "p$i" }) { i ->
                val it = items.getOrNull(i) ?: return@items
                FocusSurface(onClick = { onOpen(it.id) }, shape = RoundedCornerShape(28.design), bg = androidx.compose.ui.graphics.Color.Transparent, focusedBg = androidx.compose.ui.graphics.Color.Transparent, ringWidth = 8.design, focusedScale = 1.03f, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { _ ->
                    // Marge sous l'affiche : l'anneau de sélection (8) et l'arrondi ne rognent plus le titre ni l'année.
                    Column(Modifier.padding(bottom = 14.design), verticalArrangement = Arrangement.spacedBy(14.design)) {
                        PosterImage(it.poster, it.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), radius = 28, year = it.year)
                        Text(it.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 32.spx, lineHeight = 38.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.design))
                        it.year?.let { y -> Text("$y", color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, maxLines = 1, modifier = Modifier.padding(horizontal = 16.design)) }
                    }
                }
            }
        }
    }
}

// ── Fiche d'un film ───────────────────────────────────────────────────────────────────────────────

@Composable
fun SimpleMovieDetailScreen(movieId: Long, onPlay: (url: String, title: String) -> Unit, onBack: () -> Unit,
    vm: com.ultratv.tv.nativeapp.ui.movies.MovieDetailViewModel = hiltViewModel()) {
    val S = SimpleStrings(LocalDs.current.lang)
    LaunchedEffect(movieId) { vm.load(movieId) }
    val m by vm.movie.collectAsState()
    val resume by vm.resumeMs.collectAsState()
    val info by vm.info.collectAsState()
    val movie = m
    val touch = LocalTouch.current
    Row(
        Modifier.fillMaxSize().background(Ux.Bg).then(if (touch) Modifier.padding(16.design) else Modifier.padding(start = 96.design, end = 96.design, top = 64.design, bottom = 44.design)),
        horizontalArrangement = Arrangement.spacedBy(72.design),
    ) {
        Column(Modifier.width(if (touch) 280.design else 520.design), verticalArrangement = Arrangement.spacedBy(24.design)) {
            FocusSurface(onClick = onBack, shape = CircleShape, bg = Ux.Surface2, ringWidth = 6.design, focusedScale = 1f, modifier = Modifier.size(72.design)) { f ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DIcon("M15 6l-6 6 6 6", 34.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.6f) }
            }
            if (movie != null) PosterImage(movie.poster, movie.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), radius = 32, year = movie.year)
        }
        Column(Modifier.weight(1f).padding(top = if (touch) 0.design else 96.design), verticalArrangement = Arrangement.spacedBy(30.design)) {
            if (movie == null) { Text(S.loading, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx); return@Column }
            Text(movie.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 72.spx, lineHeight = 78.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(movie.year?.toString(), SimpleText.duration(info?.duration ?: movie.duration), (info?.genre ?: movie.genre)?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() },
                movie.rating?.takeIf { it > 0 }?.let { "★ ${"%.1f".format(it)}" })
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 34.spx, maxLines = 1)
            (info?.plot ?: movie.plot)?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx, lineHeight = 46.spx, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            val first = remember { FocusRequester() }
            var hasFocus by remember { mutableStateOf(false) }
            RequestInitialFocus(first, hasFocus = { hasFocus })
            Row(Modifier.padding(top = 12.design).onFocusChanged { hasFocus = it.hasFocus }, horizontalArrangement = Arrangement.spacedBy(28.design), verticalAlignment = Alignment.CenterVertically) {
                SimpleButton(if (resume > 0) S.tileResume else S.keyWatch, Icons.Play, primary = true, onClick = { vm.play(movie, onPlay) }, modifier = Modifier.focusRequester(first), iconFill = true)
                com.ultratv.tv.nativeapp.ui.detail.FavoriteIconButton("MOVIE", movie.remoteId, sizePx = 104)
            }
            Spacer(Modifier.weight(1f))
            if (!touch) Row(horizontalArrangement = Arrangement.spacedBy(40.design)) { KeyHintBig("OK", S.keyWatch); KeyHintBig("↩", S.keyBack) }
        }
    }
}

// ── Série et épisodes ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SimpleSeriesDetailScreen(seriesId: Long, onPlayEpisode: (url: String, title: String) -> Unit, onBack: () -> Unit,
    vm: com.ultratv.tv.nativeapp.ui.series.SeriesDetailViewModel = hiltViewModel()) {
    val S = SimpleStrings(LocalDs.current.lang)
    LaunchedEffect(seriesId) { vm.load(seriesId) }
    val s by vm.series.collectAsState()
    val eps by vm.episodes.collectAsState()
    val watched by vm.watched.collectAsState()
    val traktSeen by vm.traktWatchedEpisodes.collectAsState()
    val series = s
    SimpleFrame(title = series?.title ?: S.loading, onBack = onBack, hints = listOf("OK" to S.keyWatch, "↩" to S.keyBack)) {
        if (series == null) return@SimpleFrame
        val progress = remember(watched) { watched.associateBy { it.remoteId } }
        val target: EpisodeEntity? = remember(eps, watched, traktSeen) { com.ultratv.tv.nativeapp.ui.series.resumeTarget(eps, watched, traktSeen) }
        val seasons = remember(eps) { com.ultratv.tv.nativeapp.ui.detail.seasonsOf(eps) }
        var season by remember(series.id) { mutableStateOf<Int?>(null) }
        val active = com.ultratv.tv.nativeapp.ui.detail.activeSeasonOf(seasons, season, target?.season)
        val seasonEps = remember(eps, active) { eps.filter { it.season == active } }
        val play: (EpisodeEntity) -> Unit = { ep -> vm.playEpisode(series.name, series.remoteId, series.providerId, ep, onPlayEpisode) }
        val first = remember { FocusRequester() }
        var hasFocus by remember { mutableStateOf(false) }
        RequestInitialFocus(first, hasFocus = { hasFocus }, key = target?.id)
        Column(Modifier.weight(1f).onFocusChanged { hasFocus = it.hasFocus }.focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(24.design)) {
            // Épisode à reprendre / suivant : la grande action de l'écran.
            if (target != null) FocusSurface(onClick = { play(target) }, shape = RoundedCornerShape(36.design), bg = Ux.Surface2, ringWidth = 10.design, focusedScale = 1.01f,
                modifier = Modifier.fillMaxWidth().height(190.designT).focusRequester(first)) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 36.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.design)) {
                    Box(Modifier.size(116.design).clip(CircleShape).background(Ux.Accent), contentAlignment = Alignment.Center) { DIcon(Icons.Play, 50.design, Ux.OnAccent, fill = true) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.design)) {
                        Text((if (com.ultratv.tv.nativeapp.ui.detail.isResuming(progress[target.remoteId]?.positionMs)) S.tileResume else S.nextEpisode).uppercase(), color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 26.spx, maxLines = 1)
                        Text("${S.season} ${target.season} · ${target.episode}", color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 48.spx, maxLines = 1)
                        Text(com.ultratv.tv.nativeapp.ui.series.cleanEpisodeTitle(target.title, series.name, series.title), color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 28.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (seasons.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(14.design), verticalAlignment = Alignment.CenterVertically) {
                item { Text(S.season, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.spx, modifier = Modifier.padding(end = 8.design)) }
                items(seasons) { n -> SimplePill("$n", active == n) { season = n } }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.design), contentPadding = PaddingValues(bottom = 16.design)) {
                items(seasonEps, key = { it.id }) { ep ->
                    val p = progress[ep.remoteId]
                    val seen = com.ultratv.tv.nativeapp.data.trakt.TraktLibrary.episodeKey(ep.season, ep.episode) in traktSeen ||
                        (p != null && p.durationMs > 0 && p.positionMs >= p.durationMs * 9 / 10)
                    FocusSurface(onClick = { play(ep) }, shape = RoundedCornerShape(24.design), bg = Ux.Surface2, ringWidth = 8.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(100.designT)) { f ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 32.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.design)) {
                            Text("${ep.episode}", color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 42.spx, maxLines = 1, modifier = Modifier.width(80.design))
                            Text(com.ultratv.tv.nativeapp.ui.series.cleanEpisodeTitle(ep.title, series.name, series.title), color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (seen) Text(S.watched, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ── Mes favoris ───────────────────────────────────────────────────────────────────────────────────

@Composable
fun SimpleFavoritesScreen(onPlayChannel: (String, String) -> Unit, onOpenMovie: (Long) -> Unit, onOpenSeries: (Long) -> Unit, onBack: () -> Unit,
    vm: com.ultratv.tv.nativeapp.ui.favorites.FavoritesViewModel = hiltViewModel()) {
    val S = SimpleStrings(LocalDs.current.lang)
    val D = LocalDs.current
    val channels by vm.channels.collectAsState()
    val now by vm.nowPlaying.collectAsState()
    val movies by vm.movies.collectAsState()
    val series by vm.series.collectAsState()
    val touch = LocalTouch.current
    SimpleFrame(title = S.tileFavorites, onBack = onBack, hints = listOf("OK" to S.keyWatch, "OK ⏵" to S.holdRemove, "↩" to S.keyBack)) {
        if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) { Text(D.noFavorites, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx, maxLines = 3); return@SimpleFrame }
        val first = remember { FocusRequester() }
        var hasFocus by remember { mutableStateOf(false) }
        RequestInitialFocus(first, hasFocus = { hasFocus })
        LazyColumn(Modifier.weight(1f).onFocusChanged { hasFocus = it.hasFocus }.focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(32.design)) {
            if (channels.isNotEmpty()) {
                item { Text(S.myChannels, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 34.spx) }
                items(channels.chunked(if (touch) 1 else 3), key = { row -> "c" + row.first().id }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(28.design)) {
                        row.forEach { c ->
                            val isFirst = c.id == channels.first().id
                            FocusSurface(onClick = { onPlayChannel(c.streamUrl, c.title) }, onLongClick = { vm.remove("LIVE", c.providerId, c.remoteId) }, shape = RoundedCornerShape(28.design), bg = Ux.Surface2, ringWidth = 8.design, focusedScale = 1f,
                                modifier = Modifier.weight(1f).height(132.designT).then(if (isFirst) Modifier.focusRequester(first) else Modifier)) { f ->
                                Row(Modifier.fillMaxSize().padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
                                    LogoBox(c.logo, c.title, Modifier.width(112.design).height(72.design), radius = 16, pad = 8, bg = if (f) Ux.Line else Ux.Surface)
                                    Column(Modifier.weight(1f)) {
                                        Text(c.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        now[c.id]?.title?.let { Text(it, color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    }
                                }
                            }
                        }
                        repeat((if (touch) 1 else 3) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            val titles: List<Triple<String, String?, () -> Unit>> = movies.map { mv -> Triple(mv.title, mv.poster) { onOpenMovie(mv.id) } } +
                series.map { sr -> Triple(sr.title, sr.poster) { onOpenSeries(sr.id) } }
            val removeOf: List<() -> Unit> = movies.map { mv -> { vm.remove("MOVIE", mv.providerId, mv.remoteId) } } + series.map { sr -> { vm.remove("SERIES", sr.providerId, sr.remoteId) } }
            if (titles.isNotEmpty()) {
                item { Text(S.myTitles, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 34.spx) }
                val cols = if (touch) 3 else 6
                items(titles.indices.chunked(cols), key = { row -> "t" + row.first() }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(28.design)) {
                        row.forEach { i ->
                            val (t, poster, open) = titles[i]
                            FocusSurface(onClick = open, onLongClick = removeOf[i], shape = RoundedCornerShape(24.design), bg = androidx.compose.ui.graphics.Color.Transparent, focusedBg = androidx.compose.ui.graphics.Color.Transparent, ringWidth = 8.design, focusedScale = 1.03f,
                                modifier = Modifier.weight(1f).then(if (channels.isEmpty() && i == 0) Modifier.focusRequester(first) else Modifier)) { _ ->
                                Column(verticalArrangement = Arrangement.spacedBy(12.design)) {
                                    PosterImage(poster, t, Modifier.fillMaxWidth().aspectRatio(2f / 3f), radius = 24)
                                    Text(t, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ── Recherche ─────────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimpleSearchScreen(onOpenChannel: (String, String) -> Unit, onOpenMovie: (Long) -> Unit, onOpenSeries: (Long) -> Unit, onBack: () -> Unit,
    vm: com.ultratv.tv.nativeapp.ui.search.SearchViewModel = hiltViewModel()) {
    val S = SimpleStrings(LocalDs.current.lang)
    val D = LocalDs.current
    val q by vm.query.collectAsState()
    val view by vm.view.collectAsState()
    val searching by vm.searching.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val voiceIntent = remember {
        android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    val voiceAvailable = remember { runCatching { voiceIntent.resolveActivity(ctx.packageManager) != null }.getOrDefault(false) }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { res ->
        res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { vm.setQuery(it.trim()) }
    }
    val touch = LocalTouch.current
    // Résultats en une liste : séries, films, puis chaînes (12 au plus).
    data class Hit(val key: String, val title: String, val sub: String, val image: String?, val open: () -> Unit)
    val hits = remember(view) {
        (view.series.map { Hit("s${it.id}", it.title, listOfNotNull(D.seriesTitle, it.year?.toString()).joinToString(" · "), it.poster) { onOpenSeries(it.id) } } +
            view.movies.map { Hit("m${it.id}", it.title, listOfNotNull(D.moviesTitle, it.year?.toString()).joinToString(" · "), it.poster) { onOpenMovie(it.id) } } +
            view.channels.map { c -> Hit("c${c.id}", c.title, D.directTitle, c.logo) { vm.playChannel(c) { onOpenChannel(c.streamUrl, c.name) } } }).take(12)
    }
    SimpleFrame(title = S.tileSearch, onBack = onBack) {
        // Clavier branché / télécommande à clavier : les lettres tapées vont dans la recherche, où que soit le focus.
        val typing = Modifier.onPreviewKeyEvent { e ->
            if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val cp = e.utf16CodePoint
            when {
                e.key == androidx.compose.ui.input.key.Key.Backspace -> { vm.backspace(); true }
                cp > 0 && (Character.isLetterOrDigit(cp) || cp == ' '.code) -> { vm.append(cp.toChar().lowercaseChar()); true }
                else -> false
            }
        }
        Row(Modifier.weight(1f).then(typing), horizontalArrangement = Arrangement.spacedBy(64.design)) {
            Column(if (touch) Modifier.fillMaxWidth() else Modifier.width(860.design), verticalArrangement = Arrangement.spacedBy(22.design)) {
                if (voiceAvailable) FocusSurface(onClick = { runCatching { voice.launch(voiceIntent) } }, shape = RoundedCornerShape(999.design), bg = Ux.Accent, ringWidth = 8.design, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth().height(116.designT)) { f ->
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        DIcon("M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3zM5 11a7 7 0 0 0 14 0M12 18v3", 48.design, if (f) Ux.TextOnLight else Ux.OnAccent, strokeWidth = 2.2f)
                        Spacer(Modifier.width(20.design))
                        Text(S.say, color = if (f) Ux.TextOnLight else Ux.OnAccent, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 38.spx, maxLines = 1)
                    }
                }
                Text(S.orType, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.spx)
                Box(Modifier.fillMaxWidth().height(96.design).clip(RoundedCornerShape(26.design)).background(Ux.SurfaceDeep).border(4.design, Ux.Line, RoundedCornerShape(26.design)).padding(horizontal = 32.design), contentAlignment = Alignment.CenterStart) {
                    Text(q.ifEmpty { " " }, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 44.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val first = remember { FocusRequester() }
                var hasFocus by remember { mutableStateOf(false) }
                RequestInitialFocus(first, hasFocus = { hasFocus })
                Column(Modifier.onFocusChanged { hasFocus = it.hasFocus }.focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(12.design)) {
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".chunked(KEY_COLS).forEachIndexed { r, line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.design)) {
                            line.forEachIndexed { c, ch ->
                                FocusSurface(onClick = { vm.append(ch.lowercaseChar()) }, shape = RoundedCornerShape(20.design), bg = Ux.Surface2, ringWidth = 6.design, focusedScale = 1f,
                                    modifier = Modifier.weight(1f).height(76.designT).then(if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier)) { f ->
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("$ch", color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1) }
                                }
                            }
                            repeat(KEY_COLS - line.length) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.design)) {
                        for ((label, act) in listOf(D.keySpace to { vm.append(' ') }, D.keyDelete to { vm.backspace() }, LocalStrings.current.searchClear to { vm.clear() })) {
                            FocusSurface(onClick = act, shape = RoundedCornerShape(20.design), bg = Ux.Surface2, ringWidth = 6.design, focusedScale = 1f, modifier = Modifier.weight(1f).height(80.designT)) { f ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 30.spx, maxLines = 1) }
                            }
                        }
                    }
                }
            }
            if (!touch) Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.design)) {
                when {
                    q.isBlank() -> Unit
                    view.query != q || (searching && hits.isEmpty()) -> Text(D.searchingLabel, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx)
                    hits.isEmpty() -> Text(LocalStrings.current.searchNoMatches, color = Ux.Text2, fontFamily = Manrope, fontSize = 32.spx)
                    else -> {
                        Text(S.found, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 34.spx)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.design)) {
                            items(hits, key = { it.key }) { h ->
                                FocusSurface(onClick = h.open, shape = RoundedCornerShape(28.design), bg = Ux.Surface2, ringWidth = 8.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(150.designT)) { f ->
                                    Row(Modifier.fillMaxSize().padding(horizontal = 24.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.design)) {
                                        PosterImage(h.image, h.title, Modifier.width(84.design).height(116.design), radius = 14)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.design)) {
                                            Text(h.title, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(h.sub, color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val KEY_COLS = 9
private val MINUTES = Regex("""^(\d{1,5})\s*min""")

/** Mises en forme du mode simple (pures, testées). */
object SimpleText {
    /** « 01:33:00 », « 93 min », « 5580 » (s) → « 1 h 33 » ; texte inconnu laissé tel quel ; vide → null. */
    fun duration(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val parts = t.split(':')
        val minutes: Int? = when {
            parts.size == 3 -> parts.mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 3 }?.let { (h, m, _) -> h * 60 + m }
            parts.size == 2 -> parts.mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 2 }?.let { (h, m) -> h * 60 + m }
            MINUTES.find(t) != null -> MINUTES.find(t)?.groupValues?.get(1)?.toIntOrNull()
            t.all { it.isDigit() } -> t.toIntOrNull()?.let { if (it > 600) it / 60 else it }
            else -> null
        }
        if (minutes == null) return t
        if (minutes <= 0) return null
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
    }
}
