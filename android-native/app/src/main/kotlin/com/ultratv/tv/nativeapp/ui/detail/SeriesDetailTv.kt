package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.downloads.DownloadEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings
import com.ultratv.tv.nativeapp.data.tmdb.TmdbTrailer
import com.ultratv.tv.nativeapp.ui.trailer.TrailerPlayer
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.episodesCount
import com.ultratv.tv.nativeapp.i18n.jumpToEpisode
import com.ultratv.tv.nativeapp.i18n.seasonsHelp
import com.ultratv.tv.nativeapp.i18n.longPressDownload
import com.ultratv.tv.nativeapp.i18n.trailerLabel
import com.ultratv.tv.nativeapp.i18n.typeSeries
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.ThumbImage
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.downloads.DownloadActionsViewModel
import com.ultratv.tv.nativeapp.ui.downloads.DownloadStrings
import com.ultratv.tv.nativeapp.ui.movies.movieDuration
import com.ultratv.tv.nativeapp.ui.series.cleanEpisodeTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tout ce dont la fiche série (télécommande) a besoin, déjà calculé par l'écran parent. */
class SeriesDetailData(
    val series: SeriesEntity,
    val episodes: List<EpisodeEntity>,
    val seasons: List<Int>,
    val activeSeason: Int?,
    val target: EpisodeEntity?,
    val resuming: Boolean,
    val progress: Map<String, WatchHistoryEntity>,
    val traktSeen: Set<String>,
    val ratings: ExternalRatings?,
    val trailerUrl: String?,
    val downloads: Map<String, DownloadEntity>,
    val loading: Boolean,
)

/**
 * Fiche série pour télécommande : fond plein écran fixe, en-tête (type, titre, infos, synopsis, boutons, pastilles) puis, en
 * dessous, saisons à gauche et grandes cartes d'épisodes à droite. Le contenu défile d'un bloc entre l'en-tête et le volet
 * saisons / épisodes ; le fond ne bouge pas.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailTv(
    data: SeriesDetailData,
    onSeason: (Int) -> Unit,
    onPlayTarget: () -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onOpenPerson: (String) -> Unit,
    dlVm: DownloadActionsViewModel,
    dlStrings: DownloadStrings,
) {
    val series = data.series
    val S = LocalStrings.current
    val D = LocalDs.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val playRequester = remember { FocusRequester() }
    var playFocused by remember { mutableStateOf(false) }
    RequestInitialFocus(playRequester, hasFocus = { playFocused }, key = series.remoteId to (data.target != null))

    val shown = remember(data.episodes, data.activeSeason) { data.episodes.filter { it.season == data.activeSeason } }
    val T = TitleCleaner
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    // Touches : « vers les saisons » est du côté du début de ligne, « vers les épisodes » du côté opposé.
    val towardSeasons = if (ltr) Key.DirectionLeft else Key.DirectionRight
    val awayFromSeasons = if (ltr) Key.DirectionRight else Key.DirectionLeft

    // État du volet : rang d'entrée dans la liste (reprise, sinon premier, sinon saut demandé).
    var jumpIdx by remember(data.activeSeason) { mutableStateOf<Int?>(null) }
    val entryIdx = jumpIdx ?: entryIndexOf(shown, data.target)
    val entryNow by rememberUpdatedState(entryIdx)
    val shownNow by rememberUpdatedState(shown)
    val activeNow by rememberUpdatedState(data.activeSeason)
    val seasonsNow by rememberUpdatedState(data.seasons)
    val listState = rememberLazyListState()
    val entry = remember { FocusRequester() }
    val seasonEntry = remember { FocusRequester() }
    // Barre « Aller à l'épisode » / « Télécharger la saison » : Haut depuis le 1er épisode y mène (avant, il la sautait).
    val toolbarReq = remember { FocusRequester() }
    var jumpOpen by remember { mutableStateOf(false) }
    // Bande-annonce : clé YouTube (TMDB), lue dans l'appli par le lecteur intégré.
    val trailerKey = remember(data.trailerUrl) { data.trailerUrl?.let(TmdbTrailer::keyOf) }
    var trailerOpen by remember { mutableStateOf(false) }
    val hasTarget = data.target != null

    suspend fun focusEntry() {
        androidx.compose.runtime.withFrameNanos { }
        listState.scrollToItem(entryNow)
        repeat(10) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { entry.requestFocus() }.getOrDefault(false)) return
            delay(40)
        }
    }

    // Nouvelle saison : la liste repart de son point d'entrée (la reprise si elle y est, sinon le haut).
    LaunchedEffect(data.activeSeason) { listState.scrollToItem(entryNow) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ux.Bg)) {
        val paneHeight = maxHeight - 56.design
        DetailBackdrop(series.backdrop, series.poster, series.title)
        DetailScrim()
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 72.design, end = 72.design, top = 56.design),
            verticalArrangement = Arrangement.spacedBy(28.design),
        ) {
            // ─── En-tête : le focus qui y revient remonte la fiche en haut ───
            Column(
                Modifier.fillMaxWidth().focusGroup().onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(0, tween(160)) } },
                verticalArrangement = Arrangement.spacedBy(18.design),
            ) {
                TypeBadge(D.typeSeries)
                DetailTitle(T.tidy(series.title))
                val line = remember(series.id, series.year, series.genre, data.seasons.size, data.episodes.size, data.ratings, series.rating, D.lang) {
                    buildInfoLine(
                        series.year?.toString(), T.presentable(series.genre),
                        if (data.seasons.isEmpty()) null else "${D.seasonsCount(data.seasons.size)} · ${D.episodesCount(data.episodes.size)}",
                        data.ratings, series.rating?.takeIf { it > 0.0 && it <= 10.0 },
                    )
                }
                InfoLineRow(line, listOfNotNull(series.lang.takeIf { it.isNotBlank() }?.uppercase()))
                T.presentable(series.plot)?.let { DetailSynopsis(it) }
                Spacer(Modifier.height(4.design))
                if (data.target != null) PillButton(
                    if (data.resuming) D.resumeEpisode(data.target.season, data.target.episode) else D.playEpisodeLabel(data.target.season, data.target.episode),
                    onClick = onPlayTarget, heightPx = 76, hPadPx = 44, fontPx = 28, weight = FontWeight.Bold, iconPath = Icons.Play, iconFill = true, bg = Ux.Cta,
                    modifier = Modifier.focusRequester(playRequester).onFocusChanged { playFocused = it.isFocused },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.design), verticalAlignment = Alignment.CenterVertically) {
                    FavoritePill("SERIES", series.remoteId)
                    if (trailerKey != null) PillButton(D.trailerLabel, onClick = { trailerOpen = true }, heightPx = 64, hPadPx = 30, fontPx = 22, iconPath = Icons.Play)
                    com.ultratv.tv.nativeapp.ui.trakt.TraktSeriesButton(series, data.episodes, data.activeSeason)
                }
                val cast = remember(series.cast) { T.presentable(series.cast).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() && T.presentable(it) != null }.distinct().take(4) }
                PeopleRow(D.castTitle, cast, onOpenPerson)
            }

            // ─── Volet saisons (gauche) + épisodes (droite) ───
            Row(
                Modifier.fillMaxWidth().height(paneHeight)
                    .clip(RoundedCornerShape(28.design)).background(Ux.Bg.copy(alpha = 0.80f)).padding(24.design)
                    .onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(scroll.maxValue, tween(160)) } }
                    // Haut / Bas vont à l'élément juste au-dessus / au-dessous (pas de saut vers l'épisode à reprendre).
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(28.design),
            ) {
                SeasonsColumn(
                    seasons = data.seasons, active = data.activeSeason, episodes = data.episodes, upTo = FocusRequester.Default,
                    seasonEntry = seasonEntry, awayKey = awayFromSeasons, D = D,
                    onSeason = onSeason, onEnterEpisodes = { scope.launch { focusEntry() } },
                    modifier = Modifier.width(320.design),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.design)) {
                    // Barre d'aide + saut rapide + téléchargement de la saison
                    Row(horizontalArrangement = Arrangement.spacedBy(16.design), verticalAlignment = Alignment.CenterVertically) {
                        Text(D.seasonsHelp, color = Ux.Text3, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (shown.isNotEmpty()) {
                            PillButton(D.jumpToEpisode, onClick = { jumpOpen = true }, heightPx = 56, hPadPx = 28, fontPx = 22, modifier = Modifier.focusRequester(toolbarReq))
                            PillButton(
                                dlStrings.downloadSeason, onClick = { dlVm.downloadSeason(series, shown, dlStrings) }, heightPx = 56, hPadPx = 28, fontPx = 22,
                                iconPath = com.ultratv.tv.nativeapp.ui.downloads.DownloadIcons.Download,
                            )
                        }
                    }
                    if (shown.isNotEmpty()) Text(D.longPressDownload, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        shown.isEmpty() -> Text(if (data.loading) S.detailLoading else S.seriesNoEpisodes, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx)
                        else -> LazyColumn(
                            Modifier.fillMaxSize()
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (ev.key) {
                                        awayFromSeasons -> {
                                            // Droite : saison suivante, focus sur son point d'entrée. Pas de saison suivante : rien ne bouge.
                                            stepSeason(seasonsNow, activeNow, +1)?.let { n -> onSeason(n); scope.launch { focusEntry() } }
                                            true
                                        }
                                        towardSeasons -> { runCatching { seasonEntry.requestFocus() }; true }
                                        else -> false
                                    }
                                }
                                .focusGroup(),
                            state = listState, verticalArrangement = Arrangement.spacedBy(14.design),
                        ) {
                            itemsIndexed(shown, key = { _, e -> "${e.season}:${e.episode}:${e.remoteId}" }, contentType = { _, _ -> "episode" }) { i, ep ->
                                EpisodeCard(
                                    ep, cleanEpisodeTitle(ep.title, series.name, series.title), series.backdrop ?: series.poster, data.progress[ep.remoteId], D,
                                    seen = com.ultratv.tv.nativeapp.data.trakt.TraktLibrary.episodeKey(ep.season, ep.episode) in data.traktSeen,
                                    modifier = Modifier
                                        .then(if (i == entryIdx) Modifier.focusRequester(entry) else Modifier)
                                        .then(if (i == 0) Modifier.focusProperties { up = toolbarReq } else Modifier),
                                    onLongClick = { val d = data.downloads[ep.remoteId]; if (d != null) dlVm.act(d, dlStrings) else dlVm.downloadEpisode(series, ep, dlStrings) },
                                    onClick = { onPlayEpisode(ep) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (jumpOpen) ChoiceDialog(
        title = D.jumpToEpisode,
        options = shown.map { ep -> ep.episode to "${ep.episode}. ${cleanEpisodeTitle(ep.title, series.name, series.title).ifEmpty { D.episodeWord(ep.episode) }}" },
        selected = shown.getOrNull(entryIdx)?.episode,
        onPick = { n ->
            jumpOpen = false
            jumpIndex(shown, n)?.let { i -> jumpIdx = i; scope.launch { focusEntry() } }
        },
        onDismiss = { jumpOpen = false },
    )
    if (trailerOpen && trailerKey != null) TrailerPlayer(trailerKey, T.tidy(series.title), onClose = { trailerOpen = false })
}

/** Colonne des saisons : « Saison 1 · 24 épisodes » ; la saison choisie est surlignée, le focus la choisit après une courte pause. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SeasonsColumn(
    seasons: List<Int>, active: Int?, episodes: List<EpisodeEntity>, upTo: FocusRequester, seasonEntry: FocusRequester, awayKey: Key, D: DesignStrings,
    onSeason: (Int) -> Unit, onEnterEpisodes: () -> Unit, modifier: Modifier,
) {
    // Nombre d'épisodes par saison, calculé une fois par liste d'épisodes.
    val counts = remember(episodes) { episodes.groupingBy { it.season }.eachCount() }
    // État local : le survol des saisons ne recompose que cette colonne, pas tout l'écran.
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        if (p == active) return@LaunchedEffect
        delay(140)
        onSeason(p)
    }
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { val i = seasons.indexOf(active); if (i > 2) state.scrollToItem(i - 1) }
    LazyColumn(
        modifier
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == awayKey) {
                    pending?.takeIf { it != active }?.let(onSeason)
                    onEnterEpisodes(); true
                } else false
            }
            .focusGroup(),
        state = state, verticalArrangement = Arrangement.spacedBy(10.design),
    ) {
        items(seasons, key = { it }, contentType = { "season" }) { n ->
            val sel = n == active
            FocusSurface(
                onClick = { onSeason(n); onEnterEpisodes() }, shape = RoundedCornerShape(18.design),
                bg = if (sel) Ux.Accent.copy(alpha = 0.22f) else Color.Transparent, ringWidth = 4.design, focusedScale = 1f,
                modifier = Modifier.fillMaxWidth().height(96.designT)
                    .then(if (sel) Modifier.focusRequester(seasonEntry) else Modifier)
                    .then(if (n == seasons.first()) Modifier.focusProperties { up = upTo } else Modifier)
                    .onFocusChanged { if (it.isFocused) pending = n }
                    .semantics { selected = sel },
            ) { f ->
                Row(Modifier.fillMaxSize().padding(horizontal = 18.design), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(6.design).height(48.design).clip(RoundedCornerShape(3.design)).background(if (sel) (if (f) Ux.TextOnLight else Ux.Accent) else Color.Transparent))
                    Spacer(Modifier.width(16.design))
                    Column(verticalArrangement = Arrangement.spacedBy(2.design)) {
                        Text(D.seasonLabel(n), color = if (f) Ux.TextOnLight else if (sel) Ux.Text else Ux.Text2, fontFamily = Manrope, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold, fontSize = 28.spx, maxLines = 1)
                        if (sel || f) Text(D.episodesCount(counts[n] ?: 0), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 21.spx, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Grande carte d'épisode : vignette 16:9 (petite, décodée à sa taille), « 1. Titre », durée, « vu », résumé sur 3 lignes, progression. */
@Composable
private fun EpisodeCard(
    ep: EpisodeEntity, title: String, fallbackImage: String?, h: WatchHistoryEntity?, D: DesignStrings, seen: Boolean,
    modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit,
) {
    val dur = h?.durationMs ?: 0L
    val pos = h?.positionMs ?: 0L
    val fraction = progressFraction(dur, pos)
    val length = remember(ep.duration, D.lang) {
        movieDuration(TitleCleaner.presentable(ep.duration), D.hourShort.replace("%d", "").trim(), D.minShort.replace("%d", "").trim())
    }
    val done = isDone(dur, pos) || (seen && dur <= 0)
    val left = if (!done && dur > 0 && pos > 0) D.remainingMin(((dur - pos) / 60_000L).toInt().coerceAtLeast(1)) else null
    FocusSurface(
        onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(20.design), bg = Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1f,
        modifier = modifier.fillMaxWidth().height(188.designT),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 20.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
            Box(Modifier.width(264.design).aspectRatio(16f / 9f)) {
                ThumbImage(ep.image ?: fallbackImage, "", Modifier.fillMaxSize(), radius = 14, decodeSize = DpSize(132.dp, 75.dp))
                if (fraction > 0f && !done) ProgressLine(fraction, Modifier.align(Alignment.BottomStart).fillMaxWidth(), heightPx = 6, track = Color(0x66000000))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.design)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.design), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (title.isEmpty()) D.episodeWord(ep.episode) else "${ep.episode}. $title",
                        color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    for (b in listOfNotNull(length, left)) EpisodeBadge(b, f)
                    if (done) EpisodeBadge("✓ " + D.watchedLabel, f, accent = true)
                }
                ep.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 30.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EpisodeBadge(text: String, focused: Boolean, accent: Boolean = false) {
    val bg = when { accent -> Ux.Accent; focused -> Color(0x14000000); else -> Ux.Surface2 }
    val ink = when { accent -> Ux.OnAccent; focused -> Ux.OnFocus2; else -> Ux.Text2 }
    Box(Modifier.clip(RoundedCornerShape(8.design)).background(bg).padding(horizontal = 12.design, vertical = 3.design)) {
        Text(text, color = ink, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 20.spx, maxLines = 1)
    }
}
