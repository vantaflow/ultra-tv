package com.ultratv.tv.nativeapp.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.trailerLabel
import com.ultratv.tv.nativeapp.i18n.typeMovie
import com.ultratv.tv.nativeapp.i18n.directorLabel
import com.ultratv.tv.nativeapp.i18n.similarTitles
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import com.ultratv.tv.nativeapp.i18n.traktWatched
import com.ultratv.tv.nativeapp.ui.common.FavoriteButton
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.series.BackLink
import com.ultratv.tv.nativeapp.ui.design.AvatarImage
import com.ultratv.tv.nativeapp.ui.design.BackdropImage
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PosterImage
import com.ultratv.tv.nativeapp.ui.design.SectionTitle
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playback: PlaybackContext,
    private val provider: com.ultratv.tv.nativeapp.data.repo.ProviderRepository,
    private val history: com.ultratv.tv.nativeapp.data.repo.HistoryRepository,
    private val tmdb: com.ultratv.tv.nativeapp.data.tmdb.TmdbRepository,
    private val trakt: com.ultratv.tv.nativeapp.data.trakt.TraktLibraryRepository,
    private val ratingsClient: com.ultratv.tv.nativeapp.data.tmdb.RatingsClient,
) : ViewModel() {

    /** Notes IMDb / Rotten Tomatoes (Worker → OMDb) ; null tant qu'inconnues. */
    private val _ratings = MutableStateFlow<com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings?>(null)
    val ratings: StateFlow<com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings?> = _ratings.asStateFlow()

    /** Position de reprise en ms (0 = jamais commencé). */
    private val _info = MutableStateFlow<com.ultratv.tv.nativeapp.data.db.VodInfoEntity?>(null)
    val info: StateFlow<com.ultratv.tv.nativeapp.data.db.VodInfoEntity?> = _info.asStateFlow()
    private val _infoLoading = MutableStateFlow(true)
    val infoLoading: StateFlow<Boolean> = _infoLoading.asStateFlow()

    /** Photos de la distribution (TMDB) : nom en minuscules → URL. Vide : initiales dans les bulles. */
    private val _photos = MutableStateFlow<Map<String, String>>(emptyMap())
    val photos: StateFlow<Map<String, String>> = _photos.asStateFlow()

    /** Titres proches (même genre, sinon même catégorie) déjà au catalogue : aucune requête réseau. */
    private val _similar = MutableStateFlow<List<MovieEntity>>(emptyList())
    val similar: StateFlow<List<MovieEntity>> = _similar.asStateFlow()

    private val _resume = MutableStateFlow(0L)
    val resumeMs: StateFlow<Long> = _resume.asStateFlow()

    /** « Lecture » repart du début : on efface la position mémorisée avant de lancer. */
    fun restart(m: MovieEntity, onReady: (url: String, title: String) -> Unit) {
        viewModelScope.launch {
            history.remove(m.providerId, "MOVIE", m.remoteId)
            _resume.value = 0L
            play(m, onReady)
        }
    }

    private val _m = MutableStateFlow<MovieEntity?>(null)
    val movie: StateFlow<MovieEntity?> = _m.asStateFlow()

    /** Film vu sur Trakt (historique du compte lié) : affiche la marque « Vu » sur la fiche. */
    val traktWatched: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(_m, trakt.library) { m, lib -> m != null && lib.isMovieWatched(m.title, m.year) }
                .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)
    fun load(id: Long) {
        viewModelScope.launch {
            val m = catalog.movieById(id)
            _m.value = m
            _resume.value = if (m == null) 0L else history.resumePositionMs(m.providerId, "MOVIE", m.remoteId)
            if (m != null) _similar.value = runCatching { catalog.similarMovies(m.providerId, m.remoteId, 12)?.second }.getOrNull().orEmpty()
        }
        // Détails (get_vod_info) en paresseux : cache Room d'abord, réseau seulement si périmé.
        viewModelScope.launch {
            _infoLoading.value = true
            val m = catalog.movieById(id)
            val source = if (m == null) null else runCatching { catalog.vodInfo(m) }.getOrNull()
            _info.value = source
            _infoLoading.value = false
            // Fiche enrichie TMDB (désactivée si l'appareil n'est pas appairé) : comble ce que la source ne fournit pas.
            if (m != null) {
                val t = runCatching { tmdb.forMovie(m, source?.tmdbId) }.getOrNull()
                if (t?.tmdbId != null) {
                    _info.value = com.ultratv.tv.nativeapp.data.tmdb.mergeVodInfo(source, m, t)
                    _m.value = com.ultratv.tv.nativeapp.data.tmdb.mergeMovie(m, t)
                    _photos.value = runCatching { tmdb.castPhotos(com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE, t.tmdbId) }.getOrDefault(emptyMap())
                    _ratings.value = runCatching { ratingsClient.forTmdb(com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE, t.tmdbId) }.getOrNull()
                }
            }
        }
    }

    /** Enregistre le contexte de lecture puis appelle [onReady] avec l'URL du film. */
    fun play(m: MovieEntity, onReady: (url: String, title: String) -> Unit) {
        playback.set(PlaybackContext.Item(
            providerId = m.providerId, kind = "MOVIE", remoteId = m.remoteId,
            title = m.name, poster = m.poster, streamUrl = m.streamUrl,
        ))
        onReady(m.streamUrl, m.name)
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MovieDetailScreen(
    movieId: Long,
    onPlay: (url: String, title: String) -> Unit,
    onBack: () -> Unit = {},
    onOpenPerson: (String) -> Unit = {},
    onOpenMovie: (Long) -> Unit = {},
    vm: MovieDetailViewModel = hiltViewModel(),
) {
    val m by vm.movie.collectAsState()
    val resume by vm.resumeMs.collectAsState()
    val traktSeen by vm.traktWatched.collectAsState()
    LaunchedEffect(movieId) { vm.load(movieId) }

    val movie = m
    val S = LocalStrings.current
    val D = LocalDs.current
    if (movie == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(S.detailLoading, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx) }
        return
    }
    // Lecture en ligne pendant un téléchargement : avertit si l'abonnement n'a qu'une connexion (rien si le film est téléchargé).
    val dlVm: com.ultratv.tv.nativeapp.ui.downloads.DownloadActionsViewModel = hiltViewModel()
    val dlStrings = com.ultratv.tv.nativeapp.ui.downloads.rememberDownloadStrings()
    val playOnline = onPlay
    val onPlay: (String, String) -> Unit = { u, t -> dlVm.warnBeforePlay(movie.providerId, "MOVIE", movie.remoteId, dlStrings); playOnline(u, t) }
    val info by vm.info.collectAsState()
    val infoLoading by vm.infoLoading.collectAsState()
    val photos by vm.photos.collectAsState()
    val extRatings by vm.ratings.collectAsState()
    val T = com.ultratv.tv.nativeapp.data.repo.TitleCleaner
    val title = remember(movie.id) { T.tidy(movie.title) }
    val year = movie.year?.toString() ?: T.presentable(info?.releaseDate)?.take(4)?.takeIf { it.all(Char::isDigit) }
    val duration = movieDuration(T.presentable(info?.duration) ?: T.presentable(movie.duration), D.hourShort.replace("%d", "").trim(), D.minShort.replace("%d", "").trim())
    val genre = T.presentable(info?.genre) ?: T.presentable(movie.genre)
    val plot = T.presentable(info?.plot) ?: T.presentable(movie.plot)
    val rating = (info?.rating ?: movie.rating)?.takeIf { it > 0.0 && it <= 10.0 }
    val backdrop = T.presentable(info?.backdrop) ?: T.presentable(movie.backdrop)
    val quality = remember(movie.id) { T.clean(movie.name).quality }
    val langBadge = movie.lang.takeIf { it.isNotBlank() }?.uppercase()
    val seenBadge = if (traktSeen) "✓ " + D.traktWatched else null
    val people = remember(info, movie.id) {
        // Le champ réalisateur peut, lui aussi, lister plusieurs noms (« Ridley Scott, Ridley Scott ») : découpé comme la distribution.
        (T.presentable(info?.director).orEmpty().split(',') + (T.presentable(info?.cast) ?: T.presentable(movie.cast)).orEmpty().split(','))
            .map { it.trim() }
            .filter { it.isNotEmpty() && T.presentable(it) != null }.distinct().take(6)
    }
    val skeleton = infoLoading && info == null

    if (com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current) {
        val M = com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings.current
        val fav: com.ultratv.tv.nativeapp.ui.common.FavoriteToggleViewModel = hiltViewModel()
        LaunchedEffect(movie.remoteId) { fav.set("MOVIE", movie.remoteId) }
        val isFav by fav.isFav.collectAsState()
        // Bande-annonce : clé YouTube de la source (ou TMDB), lue dans l'appli par le lecteur intégré.
        val trailerKey = T.presentable(info?.trailer)?.let(com.ultratv.tv.nativeapp.data.tmdb.TmdbTrailer::keyOf)
        var trailerOpen by remember { mutableStateOf(false) }
        com.ultratv.tv.nativeapp.ui.mobile.MobileDetailFrame(backdrop, movie.poster, title, onBack) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (skeleton) Skeleton(Modifier.width(220.dp).height(18.dp))
            else com.ultratv.tv.nativeapp.ui.mobile.MetaRow(listOfNotNull(year, duration, genre) + com.ultratv.tv.nativeapp.data.tmdb.RatingsLogic.labels(extRatings, rating), listOfNotNull(quality, langBadge, seenBadge))
            com.ultratv.tv.nativeapp.ui.mobile.MobilePrimaryButton(S.play, { if (resume > 0) vm.restart(movie, onPlay) else vm.play(movie, onPlay) })
            if (resume > 0) com.ultratv.tv.nativeapp.ui.mobile.MobileSecondaryButton(D.resumeAt(formatClock(resume)), onClick = { vm.play(movie, onPlay) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.ultratv.tv.nativeapp.ui.mobile.MobileActionTile(Icons.Heart, if (isFav) M.inMyList else M.myList, { fav.toggle() }, Modifier.weight(1f), active = isFav, fill = isFav)
                com.ultratv.tv.nativeapp.ui.downloads.MovieDownloadTile(movie, Modifier.weight(1f))
                if (trailerKey != null) com.ultratv.tv.nativeapp.ui.mobile.MobileActionTile(com.ultratv.tv.nativeapp.ui.mobile.MobileIcons.Trailer, D.trailerLabel, { trailerOpen = true }, Modifier.weight(1f))
            }
            // Trakt : watchlist, vu / non vu, note (rien si le compte n'est pas lié).
            com.ultratv.tv.nativeapp.ui.trakt.TraktMovieActionsMobile(movie)
            if (skeleton) { Skeleton(Modifier.fillMaxWidth().height(16.dp)); Skeleton(Modifier.fillMaxWidth().height(16.dp)); Skeleton(Modifier.width(200.dp).height(16.dp)) }
            else plot?.let { com.ultratv.tv.nativeapp.ui.mobile.ExpandableText(it) }
            if (people.isNotEmpty()) {
                Text(D.castTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(people) { name ->
                        Column(Modifier.width(60.dp).clickable { onOpenPerson(name) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AvatarImage(photos[name.lowercase()], name, Modifier.size(52.dp))
                            Text(name, color = Ux.Text2, fontFamily = Manrope, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (trailerOpen && trailerKey != null) com.ultratv.tv.nativeapp.ui.trailer.TrailerPlayer(trailerKey, title, onClose = { trailerOpen = false })
        return
    }

    val similar by vm.similar.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val scroll = androidx.compose.foundation.rememberScrollState()
    val playRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    RequestInitialFocus(playRequester, hasFocus = { focused }, key = movie.id)
    val directors = remember(info) { T.presentable(info?.director).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() && T.presentable(it) != null }.distinct().take(2) }
    val cast = remember(people, directors) { people.filter { it !in directors } }
    val trailerKey = T.presentable(info?.trailer)?.let(com.ultratv.tv.nativeapp.data.tmdb.TmdbTrailer::keyOf)
    var trailerOpen by remember { mutableStateOf(false) }
    val line = remember(year, genre, duration, extRatings, rating) { com.ultratv.tv.nativeapp.ui.detail.buildInfoLine(year, genre, duration, extRatings, rating) }
    val tween = androidx.compose.animation.core.tween<Float>(160)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Ux.Bg)) {
        val screenH = maxHeight
        com.ultratv.tv.nativeapp.ui.detail.DetailBackdrop(backdrop, movie.poster, title)
        com.ultratv.tv.nativeapp.ui.detail.DetailScrim()
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            // ─── En-tête : bas de l'écran initial, le focus qui y revient remonte la fiche ───
            Column(
                Modifier.fillMaxWidth().height(screenH - 150.design)
                    .padding(start = 72.design, end = 96.design, top = 56.design)
                    .focusGroup().onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(0, tween) } },
                verticalArrangement = Arrangement.spacedBy(18.design, Alignment.Bottom),
            ) {
                com.ultratv.tv.nativeapp.ui.detail.TypeBadge(D.typeMovie)
                com.ultratv.tv.nativeapp.ui.detail.DetailTitle(title)
                if (skeleton) Skeleton(Modifier.width(420.design).height(30.design))
                else com.ultratv.tv.nativeapp.ui.detail.InfoLineRow(line, listOfNotNull(quality, langBadge, seenBadge))
                if (skeleton) Column(verticalArrangement = Arrangement.spacedBy(12.design)) {
                    Skeleton(Modifier.fillMaxWidth(0.7f).height(24.design)); Skeleton(Modifier.fillMaxWidth(0.7f).height(24.design)); Skeleton(Modifier.width(520.design).height(24.design))
                } else plot?.let { com.ultratv.tv.nativeapp.ui.detail.DetailSynopsis(it) }
                Spacer(Modifier.height(4.design))
                // Principal : « Reprendre à … » si le film est commencé, sinon « Regarder » (depuis le début).
                PillButton(
                    if (resume > 0) D.resumeAt(formatClock(resume)) else S.play,
                    onClick = { if (resume > 0) vm.play(movie, onPlay) else vm.restart(movie, onPlay) },
                    heightPx = 76, hPadPx = 44, fontPx = 28, weight = FontWeight.Bold, iconPath = Icons.Play, iconFill = true, bg = Ux.Cta,
                    modifier = Modifier.focusRequester(playRequester).onFocusChanged { focused = it.isFocused },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.design), verticalAlignment = Alignment.CenterVertically) {
                    if (resume > 0) PillButton(S.play, onClick = { vm.restart(movie, onPlay) }, heightPx = 64, hPadPx = 30, fontPx = 22, iconPath = Icons.Play)
                    com.ultratv.tv.nativeapp.ui.detail.FavoritePill("MOVIE", movie.remoteId)
                    if (trailerKey != null) PillButton(D.trailerLabel, onClick = { trailerOpen = true }, heightPx = 64, hPadPx = 30, fontPx = 22, iconPath = Icons.Play)
                    // Téléchargement hors ligne (file, pause, reprise, progression).
                    com.ultratv.tv.nativeapp.ui.downloads.MovieDownloadPill(movie)
                }
                // Trakt : watchlist, vu / non vu, note (rien si le compte n'est pas lié).
                com.ultratv.tv.nativeapp.ui.trakt.TraktMovieActions(movie)
                com.ultratv.tv.nativeapp.ui.detail.PeopleRow(D.directorLabel, directors, onOpenPerson)
            }

            // ─── Distribution puis titres similaires : le focus qui y entre amène les deux rangées à l'écran ───
            Column(
                Modifier.fillMaxWidth().padding(start = 72.design, end = 72.design, bottom = 60.design)
                    .onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(scroll.maxValue, tween) } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(28.design),
            ) {
                if (skeleton) Column(verticalArrangement = Arrangement.spacedBy(16.design)) {
                    SectionTitle(D.castTitle, 28)
                    Row(horizontalArrangement = Arrangement.spacedBy(32.design)) { repeat(5) { Column(Modifier.width(120.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.design)) { Skeleton(Modifier.size(96.design), circle = true); Skeleton(Modifier.width(80.design).height(18.design)) } } }
                } else if (cast.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(16.design)) {
                    SectionTitle(D.castTitle, 28)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(24.design)) {
                        items(cast, key = { it }, contentType = { "person" }) { name ->
                            // Focalisable : OK ouvre la page de la personne (ses titres dans la playlist).
                            FocusSurface(onClick = { onOpenPerson(name) }, shape = RoundedCornerShape(24.design), bg = Color.Transparent, focusedScale = 1.04f, focusedBg = Ux.Surface, modifier = Modifier.width(132.design)) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.design)) {
                                    AvatarImage(photos[name.lowercase()], name, Modifier.size(96.design))
                                    Text(name, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 28.spx, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (similar.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(16.design)) {
                    SectionTitle(D.similarTitles, 28)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(20.design)) {
                        items(similar, key = { it.id }, contentType = { "similar" }) { m ->
                            com.ultratv.tv.nativeapp.ui.design.VodCard(T.tidy(m.title), m.poster, Modifier.width(170.design), year = m.year) { onOpenMovie(m.id) }
                        }
                    }
                }
            }
        }
        // Bande-annonce : surcouche plein écran (rendue par-dessus l'écran, focus piégé, Retour revient à la fiche).
        if (trailerOpen && trailerKey != null) com.ultratv.tv.nativeapp.ui.trailer.TrailerPlayer(trailerKey, title, onClose = { trailerOpen = false })
    }
}

/**
 * Visuel de fiche. Un fond PAYSAGE est recadré plein cadre ; mais certaines sources renvoient en `backdrop_path` une
 * image portrait (l'affiche) : on la détecte au chargement et on bascule sur l'affiche 2:3 nette dans son cadre,
 * sur un fond flou. Jamais d'affiche portrait agrandie en plein cadre.
 */
@Composable
fun DetailVisual(backdrop: String?, poster: String?, title: String) {
    var landscape by remember(backdrop) { mutableStateOf<Boolean?>(if (backdrop == null) false else null) }
    Box(Modifier.fillMaxSize()) {
        if (backdrop != null && landscape != false) {
            coil3.compose.AsyncImage(
                model = backdrop, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { st -> val img = st.result.image; landscape = img.width.toFloat() / img.height.coerceAtLeast(1) >= 1.3f },
                onError = { landscape = false },
            )
        }
        if (landscape == false) {
            val art = poster ?: backdrop
            Box(Modifier.align(Alignment.CenterEnd).width(1100.design).fillMaxHeight()) {
                BlurredFill(art, Modifier.fillMaxSize())
                PosterImage(art, title, Modifier.align(Alignment.Center).padding(start = 220.design).height(760.design).aspectRatio(2f / 3f), radius = 22)
            }
        }
    }
}

/** Barre de remplissage de même taille que le contenu attendu : aucun saut de mise en page à l'arrivée des détails. */
@Composable
fun Skeleton(modifier: Modifier, circle: Boolean = false) {
    Box(modifier.clip(if (circle) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.design)).background(Ux.Surface))
}

/** Fond flou de l'image (API 31+, hors petites box) ; sinon simple aplat — le voile de lisibilité fait le reste. */
@Composable
fun BlurredFill(url: String?, modifier: Modifier) {
    val lowRam = com.ultratv.tv.nativeapp.ui.common.LocalLowRam.current
    if (url.isNullOrBlank() || lowRam || android.os.Build.VERSION.SDK_INT < 31) { Box(modifier.background(Ux.Surface)); return }
    coil3.compose.AsyncImage(
        model = url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier.blur(48.design),
    )
}

/** « 01:52:00 » ou « 112 » (minutes) → « 1 h 52 » ; une valeur illisible est masquée. */
fun movieDuration(raw: String?, hUnit: String = "h", minUnit: String = "min"): String? {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return null
    val mins = when {
        t.all { it.isDigit() } -> t.toIntOrNull()
        HMS.matches(t) -> t.split(':').let { it[0].toInt() * 60 + it[1].toInt() }
        else -> return t.takeIf { it.length <= 12 }
    } ?: return null
    if (mins <= 0) return null
    return if (mins >= 60) "${mins / 60} $hUnit ${"%02d".format(mins % 60)}" else "$mins $minUnit"
}

/** h:mm:ss (ou m:ss sous l'heure) pour « Reprendre à … ». */
fun formatClock(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600; val mi = (t % 3600) / 60; val se = t % 60
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, mi, se) else String.format(java.util.Locale.ROOT, "%d:%02d", mi, se)
}

/** Créée une fois (movieDuration est appelée pour chaque ligne d'épisode). */
private val HMS = Regex("""\d{1,2}:\d{2}(:\d{2})?""")
