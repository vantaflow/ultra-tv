package com.ultratv.tv.nativeapp.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ultratv.tv.nativeapp.ui.common.FavoriteButton
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
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
) : ViewModel() {

    /** Position de reprise en ms (0 = jamais commencé). */
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

    /** Queue a VOD download for this movie. Resolves stalker:// first if
     *  needed so the worker downloads the actual stream URL, not the
     *  unplayable cmd. */
    fun record(m: MovieEntity, queuedMsg: String = "Recording queued — see Recordings screen") {
        viewModelScope.launch {
            val url = if (m.streamUrl.startsWith("stalker://"))
                provider.resolveStalkerUrl(m.providerId, m.streamUrl)
            else m.streamUrl
            recordings.enqueue(m.providerId, "MOVIE", m.remoteId, m.name, url)
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
    }

    /**
     * Resolves any `stalker://…` URL to a playable one, sets PlaybackContext
     * with the resolved URL, then invokes onReady. Non-Stalker URLs are
     * forwarded directly.
     */
    fun play(m: MovieEntity, onReady: (url: String, title: String) -> Unit) {
        if (!m.streamUrl.startsWith("stalker://")) {
            playback.set(PlaybackContext.Item(
                providerId = m.providerId, kind = "MOVIE", remoteId = m.remoteId,
                title = m.name, poster = m.poster, streamUrl = m.streamUrl,
            ))
            onReady(m.streamUrl, m.name)
            return
        }
        viewModelScope.launch {
            val resolved = provider.resolveStalkerUrl(m.providerId, m.streamUrl)
            playback.set(PlaybackContext.Item(
                providerId = m.providerId, kind = "MOVIE", remoteId = m.remoteId,
                title = m.name, poster = m.poster, streamUrl = resolved,
            ))
            onReady(resolved, m.name)
        }
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
    val playRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    RequestInitialFocus(playRequester, hasFocus = { focused }, key = movie.id)
    Box(Modifier.fillMaxSize().background(Ux.Bg)) {
        // Visuel : zone fixe de 1100 px à droite ; paysage en Crop plein cadre, sinon l'affiche dans sa propre zone.
        Box(Modifier.align(Alignment.CenterEnd).width(1100.design).fillMaxHeight().background(Ux.Tone)) {
            if (movie.backdrop != null) BackdropImage(movie.backdrop, Modifier.fillMaxSize())
            else PosterImage(movie.poster, movie.name, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(720.design), radius = 0)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Ux.Bg, 0.5f to Ux.Bg.copy(alpha = 0.55f), 1f to Color.Transparent)))
        }
        FocusSurface(
            onClick = onBack, shape = RoundedCornerShape(22.design), bg = Color.Transparent, ringWidth = 4.design, focusedScale = 1f,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 72.design, top = 40.design).height(52.design),
        ) { f ->
            Row(Modifier.padding(horizontal = 14.design).height(52.design), verticalAlignment = Alignment.CenterVertically) {
                DIcon(Icons.Chevron, 22.design, if (f) Ux.TextOnLight else Ux.Text2, strokeWidth = 2.5f)
                Spacer(Modifier.width(10.design))
                Text(S.moviesTitle, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
            }
        }
        Column(
            Modifier.fillMaxSize().padding(start = 72.design, end = 96.design, top = 100.design, bottom = 54.design),
            verticalArrangement = Arrangement.spacedBy(28.design, Alignment.Bottom),
        ) {
            Column(Modifier.widthIn(max = 900.design), verticalArrangement = Arrangement.spacedBy(20.design)) {
                Text(
                    movie.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 80.spx, lineHeight = 82.spx,
                    letterSpacing = (-2).spx, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.design), verticalAlignment = Alignment.CenterVertically) {
                    val bits = listOfNotNull(movie.year?.toString(), movie.duration?.takeIf { it.isNotBlank() }, movie.genre?.takeIf { it.isNotBlank() })
                    bits.forEachIndexed { i, b ->
                        if (i > 0) Text("·", color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
                        Text(b, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 360.design))
                    }
                    movie.container?.takeIf { it.isNotBlank() }?.let {
                        Box(Modifier.border(2.design, Ux.LineKey, RoundedCornerShape(8.design)).padding(horizontal = 12.design, vertical = 4.design)) {
                            Text(it.uppercase(), color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.spx, maxLines = 1)
                        }
                    }
                }
                movie.plot?.takeIf { it.isNotBlank() }?.let {
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
                FavoriteButton(kind = "MOVIE", remoteId = movie.remoteId)
            }
            val cast = movie.cast.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(6)
            if (cast.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(16.design)) {
                SectionTitle(D.castTitle, 28)
                Row(horizontalArrangement = Arrangement.spacedBy(32.design)) {
                    cast.forEach { name ->
                        Column(Modifier.width(120.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.design)) {
                            AvatarImage(null, name, Modifier.size(96.design))
                            Text(name, color = Ux.Text2, fontFamily = Manrope, fontSize = 18.spx, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** h:mm:ss (ou m:ss sous l'heure) pour « Reprendre à … ». */
fun formatClock(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600; val mi = (t % 3600) / 60; val se = t % 60
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, mi, se) else String.format(java.util.Locale.ROOT, "%d:%02d", mi, se)
}
