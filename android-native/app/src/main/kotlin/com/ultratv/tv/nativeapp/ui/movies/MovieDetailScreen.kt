package com.ultratv.tv.nativeapp.ui.movies

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val playback: PlaybackContext,
    private val provider: com.ultratv.tv.nativeapp.data.repo.ProviderRepository,
    private val recordings: com.ultratv.tv.nativeapp.data.recording.RecordingRepository,
) : ViewModel() {

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
    fun load(id: Long) { viewModelScope.launch { _m.value = catalog.movieById(id) } }

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
    vm: MovieDetailViewModel = hiltViewModel(),
) {
    val m by vm.movie.collectAsState()
    LaunchedEffect(movieId) { vm.load(movieId) }

    val movie = m
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    if (movie == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(S.detailLoading, color = MaterialTheme.colorScheme.onBackground) }
        return
    }
    val T = com.ultratv.tv.nativeapp.ui.theme.UltraTokens
    val F = com.ultratv.tv.nativeapp.ui.theme.UltraFonts
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 140.dp, end = 80.dp, top = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(50.dp),
    ) {
        Box(
            Modifier
                .width(320.dp)
                .height(480.dp)
                .clip(RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (movie.poster != null) AsyncImage(model = movie.poster, contentDescription = movie.name, modifier = Modifier.fillMaxSize())
            else Text("🎬", fontSize = 80.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxSize()) {
            Text(
                "FILM · ${movie.year ?: ""}".trim().removeSuffix("·").trim(),
                color = T.Accent,
                fontSize = 13.sp,
                letterSpacing = 2.3.sp,
                fontWeight = FontWeight.Medium,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
            Text(
                movie.name,
                fontFamily = F.Serif,
                fontSize = 72.sp,
                lineHeight = 70.sp,
                letterSpacing = (-2.0).sp,
                color = T.Fg,
                maxLines = 2,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                movie.rating?.let {
                    Text(
                        "${(it * 10).toInt()}% match",
                        color = T.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                movie.year?.let { Text("$it", color = T.Fg2, fontSize = 13.sp) }
                movie.container?.let { Text(it.uppercase(), color = T.Fg2, fontSize = 13.sp) }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            movie.plot?.let {
                Text(it, color = T.Fg2, fontSize = 17.sp, lineHeight = 26.sp, maxLines = 6)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { vm.play(movie, onPlay) },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = T.CtaBg,
                        contentColor = T.CtaFgOnCta,
                    ),
                    modifier = Modifier.border(3.dp, T.Accent, RoundedCornerShape(14.dp)),
                ) { Text("▶  " + S.play, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = { vm.record(movie, S.toastRecordingQueued) },
                    colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = T.Surface2),
                ) { Text("⏺  " + S.playerRecord, fontSize = 14.sp, color = T.Fg2) }
                com.ultratv.tv.nativeapp.ui.common.FavoriteButton(kind = "MOVIE", remoteId = movie.remoteId)
            }
        }
    }
}
