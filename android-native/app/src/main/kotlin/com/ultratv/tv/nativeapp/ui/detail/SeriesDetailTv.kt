package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.sp
import com.ultratv.tv.nativeapp.ui.design.Text
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
import com.ultratv.tv.nativeapp.i18n.jumpToEpisode
import com.ultratv.tv.nativeapp.i18n.longPressDownload
import com.ultratv.tv.nativeapp.i18n.trailerLabel
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.Sora
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
    /** Logo officiel de la série (Fanart.tv) : remplace le titre texte une fois chargé ; null = titre texte. */
    val logo: String? = null,
    /** « Prochain épisode : S2 É5 · titre · date » / « Dernier épisode diffusé… » / « Série terminée » (TVmaze) ; null = rien. */
    val episodeLine: String? = null,
)

/**
 * Fiche série pour télécommande (version liée à Hilt) : branche le favori, Trakt et les téléchargements sur la mise en page
 * [SeriesDetailTvContent], qui ne dépend d'aucun ViewModel.
 */
@Composable
fun SeriesDetailTv(
    data: SeriesDetailData,
    onSeason: (Int) -> Unit,
    onPlayTarget: () -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onOpenPerson: (String) -> Unit,
    dlVm: DownloadActionsViewModel,
    dlStrings: DownloadStrings,
    onBack: () -> Unit = {},
) {
    val series = data.series
    SeriesDetailTvContent(
        data = data, onSeason = onSeason, onPlayTarget = onPlayTarget, onPlayEpisode = onPlayEpisode, onOpenPerson = onOpenPerson, onBack = onBack,
        onLongPressEpisode = { ep -> val d = data.downloads[ep.remoteId]; if (d != null) dlVm.act(d, dlStrings) else dlVm.downloadEpisode(series, ep, dlStrings) },
        downloadLabel = dlStrings.downloadSeason,
        onDownloadSeason = { dlVm.downloadSeason(series, data.episodes.filter { it.season == data.activeSeason }, dlStrings) },
        favorite = { FavoriteIconButton("SERIES", series.remoteId) },
        trakt = { com.ultratv.tv.nativeapp.ui.trakt.TraktSeriesButton(series, data.episodes, data.activeSeason) },
    )
}

/**
 * Fiche série (maquette FicheSerie) : colonne gauche (retour, titre, infos, synopsis, boutons, Distribution) et, à droite,
 * onglets de saisons puis liste d'épisodes. Fond uni, sans flou. Gauche / Droite dans la liste changent de saison.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailTvContent(
    data: SeriesDetailData,
    onSeason: (Int) -> Unit,
    onPlayTarget: () -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onOpenPerson: (String) -> Unit,
    onBack: () -> Unit,
    onLongPressEpisode: (EpisodeEntity) -> Unit,
    downloadLabel: String,
    onDownloadSeason: () -> Unit,
    favorite: @Composable () -> Unit,
    trakt: @Composable () -> Unit,
) {
    val series = data.series
    val S = LocalStrings.current
    val D = LocalDs.current
    val M = com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings.current
    val scope = rememberCoroutineScope()
    val playRequester = remember { FocusRequester() }
    val firstBtn = remember { FocusRequester() }
    var playFocused by remember { mutableStateOf(false) }
    // Focus initial : « Reprendre » ; sans lui, le premier bouton de la rangée suivante (bande-annonce).
    RequestInitialFocus(if (data.target != null) playRequester else firstBtn, hasFocus = { playFocused }, key = series.remoteId to (data.target != null))

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
    val activeNow by rememberUpdatedState(data.activeSeason)
    val seasonsNow by rememberUpdatedState(data.seasons)
    val listState = rememberLazyListState()
    val entry = remember { FocusRequester() }
    val seasonEntry = remember { FocusRequester() }
    val firstEp = remember { FocusRequester() }
    val firstVisIdx by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    var jumpOpen by remember { mutableStateOf(false) }
    // Bande-annonce : clé YouTube (TMDB), lue dans l'appli par le lecteur intégré.
    val trailerKey = remember(data.trailerUrl) { data.trailerUrl?.let(TmdbTrailer::keyOf) }
    var trailerOpen by remember { mutableStateOf(false) }

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

    val rowExit = Modifier.focusProperties {
        exit = { dir ->
            if (dir == awayFromSeasons2(ltr) && shown.isNotEmpty()) { scope.launch { focusEntry() }; FocusRequester.Cancel }
            else FocusRequester.Default
        }
    }.focusGroup()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun leaveToLeftColumn() {
        if (!(runCatching { playRequester.requestFocus() }.getOrDefault(false) || (trailerKey != null && runCatching { firstBtn.requestFocus() }.getOrDefault(false)))) {
            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
        }
    }

    Row(Modifier.fillMaxSize().background(Ux.Bg)) {
        // ─── Colonne gauche ───
        Column(
            Modifier.width(780.design).fillMaxHeight().padding(start = 72.design, top = 56.design, bottom = 48.design).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(22.design),
        ) {
            V2BackButton(M.a11yBack, onBack)
            Spacer(Modifier.height(18.design))
            TitleLogo(data.logo, T.tidy(series.title), maxWidth = 620.design, maxHeight = 170.design) {
                Text(T.tidy(series.title), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 64.spx, lineHeight = 64.spx, letterSpacing = (-1).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val line = remember(series.id, series.year, series.genre, data.seasons.size, data.episodes.size, data.ratings, series.rating, D.lang) {
                buildInfoLine(
                    series.year?.toString(), T.presentable(series.genre),
                    if (data.seasons.isEmpty()) null else D.seasonsCount(data.seasons.size),
                    data.ratings, series.rating?.takeIf { it > 0.0 && it <= 10.0 },
                )
            }
            val meta = (line.texts + listOfNotNull(line.imdb, line.rt?.let { "🍅 $it" }, series.lang.takeIf { it.isNotBlank() }?.uppercase())).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            data.episodeLine?.let { Text(it, color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            T.presentable(series.plot)?.let {
                Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 21.spx, lineHeight = 32.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            // Une ligne = un focusGroup : Droite/Gauche restent dans la ligne (jamais de descente vers une autre ligne) et,
            // depuis le dernier élément, mènent à l'épisode d'entrée (exception voulue : la géométrie choisirait l'épisode le plus
            // proche en hauteur, pas la reprise). Haut/Bas restent géométriques.
            if (data.target != null) Box(rowExit) {
                V2Pill(
                    if (data.resuming) D.resumeEpisode(data.target.season, data.target.episode) else D.playEpisodeLabel(data.target.season, data.target.episode),
                    onPlayTarget, primary = true, heightPx = 62, fontPx = 20, hPadPx = 28, iconPath = Icons.Play, iconFill = true,
                    modifier = Modifier.focusRequester(playRequester).onFocusChanged { playFocused = it.isFocused },
                )
            }
            Row(rowExit, horizontalArrangement = Arrangement.spacedBy(12.design), verticalAlignment = Alignment.CenterVertically) {
                if (trailerKey != null) V2Pill(D.trailerLabel, { trailerOpen = true }, heightPx = 62, fontPx = 19, hPadPx = 26, modifier = Modifier.focusRequester(firstBtn).onFocusChanged { if (data.target == null) playFocused = it.isFocused })
                favorite()
                trakt()
            }
            val cast = remember(series.cast) {
                T.presentable(series.cast).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() && T.presentable(it) != null }.distinct().take(6)
                    .map { CastUi(it, null, null) }
            }
            V2CastRow(D.castTitle, cast, onOpenPerson, Modifier.padding(top = 4.design), rowModifier = rowExit, avatarPx = 80, cellPx = 100, namePx = 15, rolePx = 13)
        }

        // ─── Colonne droite : saisons puis épisodes ───
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(start = 32.design, top = 56.design, end = 72.design, bottom = 48.design).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(18.design),
        ) {
            // Onglets sur toute la largeur (défilent, la saison active reste visible : 10 saisons restent atteignables), puis la
            // barre « Aller à l'épisode » / « Télécharger la saison » sur sa propre ligne : plus rien ne masque une saison.
            SeasonTabs(
                seasons = data.seasons, active = data.activeSeason, seasonEntry = seasonEntry, D = D,
                onSeason = onSeason, onEnterEpisodes = { scope.launch { focusEntry() } }, modifier = Modifier.fillMaxWidth(),
                onDown = { committed -> if (committed) scope.launch { focusEntry() } else runCatching { firstEp.requestFocus() } },
                // Gauche depuis la première saison : colonne de gauche (Lecture / Reprendre, sinon premier bouton).
                onLeftEdge = { leaveToLeftColumn() },
                leftKey = towardSeasons, awayKey = awayFromSeasons,
            )
            if (shown.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.design)) {
                // Bas : premier épisode visible.
                val toFirst = Modifier.focusProperties { down = firstEp }
                V2Pill(D.jumpToEpisode, { jumpOpen = true }, heightPx = 52, fontPx = 18, hPadPx = 22, modifier = toFirst)
                V2Pill(downloadLabel, onDownloadSeason, heightPx = 52, fontPx = 18, hPadPx = 22, iconPath = com.ultratv.tv.nativeapp.ui.downloads.DownloadIcons.Download, modifier = toFirst)
            }
            if (shown.isNotEmpty()) Text(D.longPressDownload, color = Ux.Text3, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                shown.isEmpty() -> Text(if (data.loading) S.detailLoading else S.seriesNoEpisodes, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx)
                else -> LazyColumn(
                    Modifier.fillMaxSize()
                        // Gauche (Droite en RTL) depuis un épisode : « Reprendre », sinon le premier bouton de la colonne de gauche.
                        .onPreviewKeyEvent { ev ->
                            if (ev.key != towardSeasons) false
                            else { if (ev.type == KeyEventType.KeyDown) leaveToLeftColumn(); true }
                        }
                        .focusGroup(),
                    state = listState, verticalArrangement = Arrangement.spacedBy(12.design),
                ) {
                    itemsIndexed(shown, key = { _, e -> "${e.season}:${e.episode}:${e.remoteId}" }, contentType = { _, _ -> "episode" }) { i, ep ->
                        EpisodeCard(
                            ep, cleanEpisodeTitle(ep.title, series.name, series.title), series.backdrop ?: series.poster, data.progress[ep.remoteId], D,
                            seen = com.ultratv.tv.nativeapp.data.trakt.TraktLibrary.episodeKey(ep.season, ep.episode) in data.traktSeen,
                            modifier = Modifier
                                .then(if (i == entryIdx) Modifier.focusRequester(entry) else Modifier)
                                .then(if (i == firstVisIdx) Modifier.focusRequester(firstEp) else Modifier)
                                // Haut depuis le premier épisode : l'onglet de la saison active (et non la barre d'actions, à droite).
                                .then(if (i == 0) Modifier.focusProperties { up = seasonEntry } else Modifier),
                            onLongClick = { onLongPressEpisode(ep) },
                            onClick = { onPlayEpisode(ep) },
                        )
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

/**
 * Onglets de saisons : la saison choisie prend les couleurs de focus, le focus la choisit après une courte pause ; Bas (ou OK)
 * entre dans la liste d'épisodes.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SeasonTabs(
    seasons: List<Int>, active: Int?, seasonEntry: FocusRequester, D: DesignStrings,
    onSeason: (Int) -> Unit, onEnterEpisodes: () -> Unit, modifier: Modifier,
    onDown: (committed: Boolean) -> Unit, onLeftEdge: () -> Unit, leftKey: Key, awayKey: Key,
) {
    // État local : le survol des saisons ne recompose que cette rangée, pas tout l'écran.
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        if (p == active) return@LaunchedEffect
        delay(140)
        onSeason(p)
    }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reqs = remember(seasons) { seasons.map { FocusRequester() } }
    LaunchedEffect(Unit) { val i = seasons.indexOf(active); if (i > 2) state.scrollToItem(i - 1) }
    // Gauche/Droite : saison voisine, avec défilement si elle n'est pas encore composée (10 saisons et plus).
    fun step(from: Int, delta: Int) {
        val to = from + delta
        if (to !in seasons.indices) return
        scope.launch {
            state.animateScrollToItem(to)
            repeat(10) {
                androidx.compose.runtime.withFrameNanos { }
                if (runCatching { reqs[to].requestFocus() }.getOrDefault(false)) return@launch
                delay(30)
            }
        }
    }
    LazyRow(
        modifier
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) {
                    // Valide la saison survolée, puis premier épisode visible (ou épisode d'entrée de la nouvelle saison).
                    val p = pending?.takeIf { it != active }
                    p?.let(onSeason)
                    onDown(p != null); true
                } else false
            }
            .focusGroup(),
        state = state, horizontalArrangement = Arrangement.spacedBy(10.design),
    ) {
        itemsIndexed(seasons, key = { _, n -> n }, contentType = { _, _ -> "season" }) { idx, n ->
            val sel = n == active
            FocusSurface(
                onClick = { onSeason(n); onEnterEpisodes() }, shape = RoundedCornerShape(26.design),
                bg = if (sel) Ux.Cta else Ux.Card, focusedBg = if (sel) Ux.Cta else Ux.Button, ringWidth = 4.design, focusedScale = 1f,
                modifier = Modifier.height(52.designT)
                    .then(if (sel) Modifier.focusRequester(seasonEntry) else Modifier)
                    .focusRequester(reqs[idx])
                    .onPreviewKeyEvent { ev ->
                        when {
                            ev.key != leftKey && ev.key != awayKey -> false
                            ev.type != KeyEventType.KeyDown -> true
                            ev.key == leftKey -> { if (idx == 0) onLeftEdge() else step(idx, -1); true }
                            else -> { step(idx, +1); true }
                        }
                    }
                    .onFocusChanged { if (it.isFocused) pending = n }
                    .semantics { selected = sel },
            ) { _ ->
                Box(Modifier.height(52.designT).padding(horizontal = 24.design), contentAlignment = Alignment.Center) {
                    Text(D.seasonLabel(n), color = if (sel) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 19.spx, maxLines = 1)
                }
            }
        }
    }
}

/** Carte d'épisode (maquette) : vignette 220 × 124, « 1. Titre », durée / « Vu » / reste, résumé sur une ligne, progression. Focus = couleurs de focus. */
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
    val state = if (done) D.watchedLabel.replaceFirstChar { it.uppercase() } else left
    val meta = listOfNotNull(length, state).joinToString(" · ")
    FocusSurface(
        onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(22.design), bg = Ux.Card2, focusedBg = Ux.Cta,
        ringWidth = 4.design, focusedScale = 1f, modifier = modifier.fillMaxWidth(),
    ) { f ->
        Row(Modifier.fillMaxWidth().padding(14.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.design)) {
            ThumbImage(ep.image ?: fallbackImage, "", Modifier.width(220.design).aspectRatio(16f / 9f), radius = 16, decodeSize = DpSize(110.dp, 62.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.design)) {
                Text(
                    if (title.isEmpty()) D.episodeWord(ep.episode) else "${ep.episode}. $title",
                    color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // Méta : rouge foncé sur le fond clair du focus (thème sombre), rose clair sur le fond encre (thème clair).
                val metaInk = if (f) (if (Ux.palette.light) DarkPalette.accentText else Color(0xFFB3121D)) else Ux.Text3
                if (meta.isNotEmpty()) Text(meta, color = metaInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.spx, maxLines = 1)
                ep.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (fraction > 0f) ProgressLine(
                    fraction, Modifier.fillMaxWidth().padding(top = 4.design).clip(RoundedCornerShape(2.design)), heightPx = 4,
                    track = (if (f) Ux.TextOnLight else Ux.Text).copy(alpha = 0.12f),
                )
            }
        }
    }
}


/** Direction qui mène de la colonne de gauche vers les épisodes : Droite en LTR, Gauche en RTL. */
internal fun awayFromSeasons2(ltr: Boolean) = if (ltr) androidx.compose.ui.focus.FocusDirection.Right else androidx.compose.ui.focus.FocusDirection.Left
