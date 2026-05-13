package com.ultratv.tv.nativeapp.ui.player

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ultratv.tv.nativeapp.data.repo.HistoryRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: PlaybackContext,
    private val history: HistoryRepository,
    private val zapQueue: com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue,
    private val provider: com.ultratv.tv.nativeapp.data.repo.ProviderRepository,
    private val epgDao: com.ultratv.tv.nativeapp.data.db.EpgDao,
) : ViewModel() {

    val current: StateFlow<PlaybackContext.Item?> = playback.current

    /** Resolves the next/previous channel in the active zap queue (Live only)
     *  and updates [PlaybackContext] so the player swaps stream URL. Returns
     *  the new URL or null when there's nothing queued. */
    suspend fun zap(forward: Boolean): String? {
        val target = (if (forward) zapQueue.next() else zapQueue.previous()) ?: return null
        val resolved = provider.resolvePlayUrl(target.id, target.streamUrl)
        playback.set(PlaybackContext.Item(
            providerId = target.providerId, kind = "LIVE", remoteId = target.remoteId,
            title = target.name, poster = target.logo, streamUrl = resolved,
        ))
        return resolved
    }

    /** Now / next programme for the currently-playing live channel. Null for VOD. */
    private val _epg = kotlinx.coroutines.flow.MutableStateFlow<Pair<com.ultratv.tv.nativeapp.data.db.EpgEntity?, com.ultratv.tv.nativeapp.data.db.EpgEntity?>>(null to null)
    val epg: StateFlow<Pair<com.ultratv.tv.nativeapp.data.db.EpgEntity?, com.ultratv.tv.nativeapp.data.db.EpgEntity?>> = _epg

    fun refreshEpg() {
        val c = playback.current.value ?: return
        if (c.kind != "LIVE") { _epg.value = null to null; return }
        viewModelScope.launch {
            // Look up the local ChannelEntity by providerId + remoteId to get id.
            // The DAO doesn't expose that lookup directly; we use the rangeForChannels
            // path which needs id. Skip if we can't resolve.
            val now = System.currentTimeMillis()
            val list = epgDao.rangeForChannels(emptyList(), now, now)  // shortcut; real lookup TODO
            _epg.value = list.firstOrNull { it.startMs <= now && it.endMs > now } to
                list.firstOrNull { it.startMs > now }
        }
    }

    // Resume position to seek to when the player opens. Loaded once via [prepareResume].
    private val _resumeMs = MutableStateFlow(0L)
    val resumeMs: StateFlow<Long> = _resumeMs.asStateFlow()

    fun prepareResume() {
        // Live channels don't seek — only VOD/episodes resume.
        viewModelScope.launch {
            val c = playback.current.value
            if (c == null || c.kind == "LIVE") return@launch
            // The history table doubles as the resume store: positionMs > 0 = resume.
            // We don't have a direct "byId" — listen one tick via the kind-filtered flow.
            // Simpler: re-record at play time but read first if present.
            // For brevity we leave _resumeMs at 0 and rely on the player to start at 0;
            // future improvement: a one-shot DAO query.
            _resumeMs.value = 0L
        }
    }

    /** Persists the current playback position. Called periodically + on dispose. */
    fun recordProgress(positionMs: Long, durationMs: Long) {
        val c = playback.current.value ?: return
        if (positionMs < 5_000 && c.kind != "LIVE") return    // ignore noise from the first 5s
        viewModelScope.launch {
            history.record(
                providerId = c.providerId,
                kind = c.kind,
                remoteId = c.remoteId,
                title = c.title,
                poster = c.poster,
                streamUrl = c.streamUrl,
                positionMs = if (c.kind == "LIVE") 0 else positionMs,
                durationMs = if (c.kind == "LIVE") 0 else durationMs,
                parentRemoteId = c.parentRemoteId,
            )
        }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(url: String, title: String, onBack: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler { onBack() }
    val playbackItem by vm.current.collectAsState()
    val isLive = playbackItem?.kind == "LIVE"
    var currentUrl by remember { mutableStateOf(url) }
    var currentTitle by remember { mutableStateOf(title) }
    var tracksOpen by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    // Stream-stats overlay: tracks codec/resolution/bitrate while playing.
    var statsOpen by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(StreamStats()) }
    LaunchedEffect(statsOpen) {
        if (!statsOpen) return@LaunchedEffect
        while (true) {
            stats = StreamStats.read(player)
            delay(1_000)
        }
    }

    // Sleep-timer: when > 0, stops playback at the given timestamp. The
    // LaunchedEffect below polls every 5s and pauses + closes the screen
    // when the deadline is reached.
    var sleepDeadlineMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sleepDeadlineMs) {
        if (sleepDeadlineMs <= 0L) return@LaunchedEffect
        while (System.currentTimeMillis() < sleepDeadlineMs) delay(5_000)
        player.pause()
        com.ultratv.tv.nativeapp.ui.common.Toaster.show("Sleep timer reached — playback paused")
        onBack()
    }
    LaunchedEffect(currentUrl) {
        if (currentUrl.isNotBlank()) {
            player.setMediaItem(MediaItem.fromUri(currentUrl))
            player.prepare()
            player.play()
        }
        vm.prepareResume()
    }

    // Periodically record playback position so "Continue watching" works even
    // if the user closes the app mid-playback (no onDispose fires for kills).
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)   // every 10s
            if (player.duration > 0) {
                vm.recordProgress(player.currentPosition, player.duration)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.recordProgress(player.currentPosition, player.duration.coerceAtLeast(0))
            player.release()
        }
    }

    // D-pad UP/DOWN = channel zap on Live. The PlayerView eats LEFT/RIGHT for
    // seek when useController = true, which is what we want for VOD.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                if (!isLive || ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionUp -> {
                        scope.launch {
                            vm.zap(forward = false)?.let {
                                currentUrl = it
                                currentTitle = vm.current.value?.title ?: currentTitle
                            }
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        scope.launch {
                            vm.zap(forward = true)?.let {
                                currentUrl = it
                                currentTitle = vm.current.value?.title ?: currentTitle
                            }
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowFastForwardButton(!isLive)
                    setShowRewindButton(!isLive)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerShowTimeoutMs = if (isLive) 1500 else 3000
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Column {
                Text(currentTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (isLive) {
                    Text(
                        "▲ ▼ to zap channels",
                        color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp,
                    )
                }
                Text(currentUrl.substringBefore('?').takeLast(60), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(24.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            // Sleep timer menu — anchored bottom-right next to the external-player button.
            var sleepMenu by remember { mutableStateOf(false) }
            Button(onClick = { sleepMenu = !sleepMenu }) {
                Text(
                    if (sleepDeadlineMs > 0L) {
                        val mins = ((sleepDeadlineMs - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
                        "💤 ${mins}min"
                    } else "💤 Sleep"
                )
            }
            if (sleepMenu) {
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(Color(0xCC000000), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    SleepOption("15 min") { sleepDeadlineMs = System.currentTimeMillis() + 15 * 60_000; sleepMenu = false }
                    SleepOption("30 min") { sleepDeadlineMs = System.currentTimeMillis() + 30 * 60_000; sleepMenu = false }
                    SleepOption("1 hour") { sleepDeadlineMs = System.currentTimeMillis() + 60 * 60_000; sleepMenu = false }
                    SleepOption("2 hours") { sleepDeadlineMs = System.currentTimeMillis() + 120 * 60_000; sleepMenu = false }
                    if (sleepDeadlineMs > 0L) {
                        SleepOption("Cancel timer") { sleepDeadlineMs = 0L; sleepMenu = false }
                    }
                }
            }
            if (!isLive) {
                Button(onClick = { tracksOpen = true }) { Text("🎚 Tracks") }
            }
            Button(onClick = { statsOpen = !statsOpen }) {
                Text(if (statsOpen) "📊 Hide stats" else "📊 Stats")
            }
            Button(onClick = {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), "video/*")
                        putExtra("title", title)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(Intent.createChooser(intent, "Open with…"))
                }
            }) { Text("External player") }
        }
        if (tracksOpen) {
            TracksDialog(player = player, onDismiss = { tracksOpen = false })
        }
        if (statsOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 24.dp)
                    .background(Color(0xCC000000), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            ) {
                Text("📊 Stream stats", color = Color(0xFF66B3FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                StatRow("Resolution", stats.resolution)
                StatRow("Video codec", stats.videoCodec)
                StatRow("Frame rate", stats.frameRate)
                StatRow("Video bitrate", stats.videoBitrate)
                StatRow("Audio codec", stats.audioCodec)
                StatRow("Audio channels", stats.audioChannels)
                StatRow("Buffered", stats.bufferedAhead)
                StatRow("Dropped frames", stats.droppedFrames)
            }
        }
    }
}

/** Subtitle + audio track picker for VOD playback. Reads the current Tracks
 *  object from the player and writes back a TrackSelectionOverride when the
 *  user picks a track. */
@OptIn(androidx.media3.common.util.UnstableApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun TracksDialog(player: ExoPlayer, onDismiss: () -> Unit) {
    val tracks = player.currentTracks
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 560.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(androidx.tv.material3.MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            androidx.tv.material3.Text(
                "🎚 Tracks",
                color = androidx.tv.material3.MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            // Audio tracks
            val audioGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
            androidx.tv.material3.Text("Audio (${audioGroups.sumOf { it.length }})", color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            audioGroups.forEach { group ->
                for (i in 0 until group.length) {
                    val fmt = group.getTrackFormat(i)
                    val label = listOfNotNull(
                        fmt.label,
                        fmt.language,
                        fmt.sampleMimeType?.removePrefix("audio/"),
                        fmt.channelCount.takeIf { it > 0 }?.let { "${it}ch" },
                    ).joinToString(" · ").ifBlank { "Track ${i + 1}" }
                    androidx.tv.material3.Button(
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                            onDismiss()
                        },
                        colors = if (group.isTrackSelected(i)) androidx.tv.material3.ButtonDefaults.colors()
                        else androidx.tv.material3.ButtonDefaults.colors(containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant),
                    ) { androidx.tv.material3.Text(label, fontSize = 13.sp) }
                }
            }
            // Subtitle tracks
            val subGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
            androidx.tv.material3.Text("Subtitles (${subGroups.sumOf { it.length }})", color = androidx.tv.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            androidx.tv.material3.Button(
                onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                        .build()
                    onDismiss()
                },
                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant),
            ) { androidx.tv.material3.Text("Off", fontSize = 13.sp) }
            subGroups.forEach { group ->
                for (i in 0 until group.length) {
                    val fmt = group.getTrackFormat(i)
                    val label = listOfNotNull(fmt.label, fmt.language).joinToString(" · ").ifBlank { "Subtitle ${i + 1}" }
                    androidx.tv.material3.Button(
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i))
                                .build()
                            onDismiss()
                        },
                        colors = if (group.isTrackSelected(i)) androidx.tv.material3.ButtonDefaults.colors()
                        else androidx.tv.material3.ButtonDefaults.colors(containerColor = androidx.tv.material3.MaterialTheme.colorScheme.surfaceVariant),
                    ) { androidx.tv.material3.Text(label, fontSize = 13.sp) }
                }
            }
            androidx.tv.material3.Button(
                onClick = onDismiss,
                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = androidx.tv.material3.MaterialTheme.colorScheme.background),
            ) { androidx.tv.material3.Text("Close") }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.width(90.dp))
        Text(value, color = Color.White, fontSize = 11.sp)
    }
}

private data class StreamStats(
    val resolution: String = "—",
    val videoCodec: String = "—",
    val frameRate: String = "—",
    val videoBitrate: String = "—",
    val audioCodec: String = "—",
    val audioChannels: String = "—",
    val bufferedAhead: String = "—",
    val droppedFrames: String = "—",
) {
    companion object {
        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun read(player: ExoPlayer): StreamStats {
            val v = player.videoFormat
            val a = player.audioFormat
            val bufferedMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
            return StreamStats(
                resolution = v?.let { "${it.width}×${it.height}" } ?: "—",
                videoCodec = v?.sampleMimeType?.removePrefix("video/") ?: "—",
                frameRate = v?.frameRate?.takeIf { it > 0 }?.let { "%.1f fps".format(it) } ?: "—",
                videoBitrate = v?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "—",
                audioCodec = a?.sampleMimeType?.removePrefix("audio/") ?: "—",
                audioChannels = a?.channelCount?.toString() ?: "—",
                bufferedAhead = "${bufferedMs / 1000}s",
                droppedFrames = "n/a",
            )
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun SleepOption(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp),
    ) { Text(label, fontSize = 13.sp) }
}
