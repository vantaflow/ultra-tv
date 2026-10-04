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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playback: PlaybackContext,
    private val provider: com.ultratv.tv.nativeapp.data.repo.ProviderRepository,
    private val recordings: com.ultratv.tv.nativeapp.data.recording.RecordingRepository,
    private val history: com.ultratv.tv.nativeapp.data.repo.HistoryRepository,
    private val tmdb: com.ultratv.tv.nativeapp.data.tmdb.TmdbRepository,
) : ViewModel() {

    /** Position de reprise en ms (0 = jamais commencé). */
    private val _info = MutableStateFlow<com.ultratv.tv.nativeapp.data.db.VodInfoEntity?>(null)
    val info: StateFlow<com.ultratv.tv.nativeapp.data.db.VodInfoEntity?> = _info.asStateFlow()
    private val _infoLoading = MutableStateFlow(true)
    val infoLoading: StateFlow<Boolean> = _infoLoading.asStateFlow()

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

    /** Queue a VOD download for this movie. */
    fun record(m: MovieEntity, queuedMsg: String = "Recording queued — see Recordings screen") {
        viewModelScope.launch {
            recordings.enqueue(m.providerId, "MOVIE", m.remoteId, m.name, m.streamUrl)
            com.ultratv.tv.nativeapp.ui.common.Toaster.ok(queuedMsg)
        }
    }
    private val _m = MutableStateFlow<MovieEntity?>(null)
    val movie: StateFlow<MovieEntity?> = _m.asStateFlow()
    fun load(id: Long) {
        viewModelScope.launch {
            val m = catalog.movieById(id)
            _m.value = m
            _resume.value = if (m == null) 0L else history.resumePositionMs(m.providerId, "MOVIE", m.remoteId)
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

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieId: Long,
    onPlay: (url: String, title: String) -> Unit,
    onBack: () -> Unit = {},
    vm: MovieDetailViewModel = hiltViewModel(),
) {
    val m by vm.movie.collectAsState()
    val resume by vm.resumeMs.collectAsState()
    LaunchedEffect(movieId) { vm.load(movieId) }

    val movie = m
    val S = LocalStrings.current
    val D = LocalDs.current
    if (movie == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(S.detailLoading, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx) }
        return
    }
    val info by vm.info.collectAsState()
    val infoLoading by vm.infoLoading.collectAsState()
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
    val people = remember(info, movie.id) {
        (listOfNotNull(T.presentable(info?.director)) + (T.presentable(info?.cast) ?: T.presentable(movie.cast)).orEmpty().split(',').map { it.trim() })
            .filter { it.isNotEmpty() && T.presentable(it) != null }.distinct().take(6)
    }
    val skeleton = infoLoading && info == null

    val playRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    RequestInitialFocus(playRequester, hasFocus = { focused }, key = movie.id)
    Box(Modifier.fillMaxSize().background(Ux.Bg)) {
        // Visuel plein écran : fond paysage entier (jamais recadré dans une colonne étroite) fondu vers la gauche ; sinon l'affiche 2:3 nette à droite.
        Box(Modifier.fillMaxSize().background(Ux.Tone)) {
            DetailVisual(backdrop, movie.poster, title)
            Box(Modifier.fillMaxSize().background(com.ultratv.tv.nativeapp.ui.design.startToEndBrush(0f to Ux.Bg, 0.6f to Ux.Bg.copy(alpha = 0.96f), 0.85f to Ux.Bg.copy(alpha = 0.3f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Ux.Bg.copy(alpha = 0.7f))))
        }
        BackLink(S.moviesTitle, onBack, Modifier.align(Alignment.TopStart).padding(start = 72.design, top = 40.design))
        Column(
            Modifier.fillMaxSize().padding(start = 72.design, end = 96.design, top = 100.design, bottom = 54.design),
            verticalArrangement = Arrangement.spacedBy(28.design, Alignment.Bottom),
        ) {
            Column(Modifier.widthIn(max = 900.design), verticalArrangement = Arrangement.spacedBy(20.design)) {
                Text(
                    title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 80.spx, lineHeight = 82.spx,
                    letterSpacing = (-2).spx, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (skeleton) Skeleton(Modifier.width(420.design).height(30.design))
                else Row(horizontalArrangement = Arrangement.spacedBy(16.design), verticalAlignment = Alignment.CenterVertically) {
                    val bits = listOfNotNull(year, duration, genre, rating?.let { String.format(java.util.Locale.ROOT, "★ %.1f", it) })
                    bits.forEachIndexed { i, b ->
                        if (i > 0) Text("·", color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
                        Text(b, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 360.design))
                    }
                    for (badge in listOfNotNull(quality, langBadge)) {
                        Box(Modifier.border(2.design, Ux.LineKey, RoundedCornerShape(8.design)).padding(horizontal = 12.design, vertical = 4.design)) {
                            Text(badge, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
                        }
                    }
                }
                if (skeleton) Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
                    Skeleton(Modifier.fillMaxWidth().height(24.design)); Skeleton(Modifier.fillMaxWidth().height(24.design)); Skeleton(Modifier.width(520.design).height(24.design))
                } else plot?.let {
                    Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, lineHeight = 39.spx, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.design), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    S.play, onClick = { if (resume > 0) vm.restart(movie, onPlay) else vm.play(movie, onPlay) },
                    heightPx = 84, hPadPx = 48, fontPx = 30, weight = FontWeight.Bold, iconPath = Icons.Play, iconFill = true, bg = Ux.Cta,
                    modifier = Modifier.focusRequester(playRequester).onFocusChanged { focused = it.isFocused },
                )
                if (resume > 0) PillButton(D.resumeAt(formatClock(resume)), onClick = { vm.play(movie, onPlay) }, heightPx = 72, fontPx = 26)
                PillButton(S.playerRecord, onClick = { vm.record(movie, S.toastRecordingQueued) }, heightPx = 72, fontPx = 26)
                T.presentable(info?.trailer)?.let { url ->
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    PillButton(D.trailerLabel, onClick = { com.ultratv.tv.nativeapp.data.tmdb.TmdbTrailer.open(ctx, url) }, heightPx = 72, fontPx = 26)
                }
                FavoriteButton(kind = "MOVIE", remoteId = movie.remoteId)
            }
            if (skeleton) Column(verticalArrangement = Arrangement.spacedBy(16.design)) {
                SectionTitle(D.castTitle, 28)
                Row(horizontalArrangement = Arrangement.spacedBy(32.design)) { repeat(5) { Column(Modifier.width(120.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.design)) { Skeleton(Modifier.size(96.design), circle = true); Skeleton(Modifier.width(80.design).height(18.design)) } } }
            } else if (people.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(16.design)) {
                SectionTitle(D.castTitle, 28)
                Row(horizontalArrangement = Arrangement.spacedBy(32.design)) {
                    people.forEach { name ->
                        Column(Modifier.width(120.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.design)) {
                            AvatarImage(null, name, Modifier.size(96.design))
                            Text(name, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 30.spx, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
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
            coil.compose.AsyncImage(
                model = backdrop, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = { st -> val d = st.result.drawable; landscape = d.intrinsicWidth.toFloat() / d.intrinsicHeight.coerceAtLeast(1) >= 1.3f },
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
    coil.compose.AsyncImage(
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
        Regex("""\d{1,2}:\d{2}(:\d{2})?""").matches(t) -> t.split(':').let { it[0].toInt() * 60 + it[1].toInt() }
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
