package com.ultratv.tv.nativeapp.ui.player.engine

import android.content.Context
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.PlaybackAdapter
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Phase { LOADING, PLAYING, ENDED, ERROR }

/** Avis discret à l'utilisateur (toast) ; le texte est localisé par l'interface. */
enum class Notice { USING_EXO, USING_VLC, USING_SOFTWARE, RETRYING }

data class SessionState(
    val phase: Phase = Phase.LOADING,
    val error: PlayErrorKind? = null,
    val combo: Combo = Combo(EngineKind.EXO, DecoderMode.AUTO),
    val bufferPreset: BufferPreset = BufferPreset.AUTO,
    val hasPicture: Boolean = false,
)

/**
 * Une session de lecture : UN seul moteur vivant à la fois (connexion unique du fournisseur : `release()`
 * précède toujours l'ouverture suivante). Gère le repli automatique moteur/décodage, la mémorisation par
 * chaîne, la nouvelle tentative après un 403 et l'adaptation du débit pendant la lecture.
 */
@UnstableApi
class PlaybackSession(
    private val ctx: Context,
    private val scope: CoroutineScope,
    val container: FrameLayout,
    private val settings: () -> ResolvedPlayback,
    private val memory: ChannelPlaybackMemory,
    private val network: NetworkMonitor,
    private val isLive: Boolean,
    private val autoFrameRate: Boolean,
    private val userAgent: String,
    private val engineFactory: (EngineKind, EngineConfig) -> PlayerEngine = { k, c -> if (k == EngineKind.EXO) ExoEngine(ctx, c) else VlcEngine(ctx, c) },
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val notices: SharedFlow<Notice> = _notices.asSharedFlow()

    var engine: PlayerEngine? = null
        private set

    private var url = ""
    private var key: String? = null
    private var resumeMs = 0L
    private var tried = mutableSetOf<Combo>()
    private var sameRetries = 0
    private var combo = Combo(EngineKind.EXO, DecoderMode.AUTO)
    private var presetOverride: BufferPreset? = null
    private var eventsJob: Job? = null
    private var watchdog: Job? = null
    private var rememberJob: Job? = null
    private val adapter = PlaybackAdapter()
    private var firstFrame = false
    private var adaptJob: Job? = null
    private var manualSwitch = false

    /** Ouvre [url]. [channelKey] = « fournisseur:chaîne » pour la mémoire par chaîne (null = pas de mémoire). */
    fun start(url: String, channelKey: String?, resumeMs: Long = 0) {
        this.url = url; this.key = channelKey; this.resumeMs = resumeMs
        tried = mutableSetOf(); sameRetries = 0; manualSwitch = false
        val s = settings()
        presetOverride = channelKey?.let { memory.bufferPreset(it) }
        combo = PlaybackPlanner.initial(s.engine, s.decoder, channelKey?.let { memory.combo(it) })
        launch(combo)
        adaptJob?.cancel()
        adaptJob = scope.launch { while (true) { delay(5_000); applyQualityLimit(adapter.tick(System.currentTimeMillis())) } }
    }

    fun retry() { tried = mutableSetOf(); sameRetries = 0; launch(combo) }

    /** Changement manuel (pilule « Lecteur ») : on le mémorise tout de suite pour cette chaîne. */
    fun switchTo(c: Combo) { manualSwitch = true; tried = mutableSetOf(); combo = c; key?.let { memory.remember(it, c, null) }; launch(c) }

    fun setBufferPreset(p: BufferPreset) { presetOverride = p; key?.let { memory.remember(it, null, p) }; launch(combo) }

    private fun launch(c: Combo) {
        eventsJob?.cancel(); watchdog?.cancel(); rememberJob?.cancel()
        releaseEngine()
        combo = c; tried += c; firstFrame = false
        val s = settings()
        val preset = presetOverride
        val buffer = if (preset != null) BufferPlanner.resolve(preset, CustomBuffer(), s.heapClassMb, s.lowRam) else s.buffer
        _state.value = SessionState(Phase.LOADING, null, c, presetOverride ?: s.bufferPreset, false)
        val cfg = EngineConfig(c.decoder, buffer, isLive, autoFrameRate, userAgent)
        val e = runCatching { engineFactory(c.engine, cfg) }.getOrElse { onError(PlayErrorKind.UNKNOWN); return }
        engine = e
        container.removeAllViews(); container.addView(e.view, FrameLayout.LayoutParams(-1, -1))
        e.limitQuality(s.maxVideoHeight, PlaybackAdapter.scaleBitrate(s.maxVideoBitrateBps, adapter.level))
        eventsJob = scope.launch { e.events.collect { onEvent(it, e) } }
        e.load(url, resumeMs)
    }

    private fun applyQualityLimit(level: Int) {
        val s = settings()
        engine?.limitQuality(s.maxVideoHeight, PlaybackAdapter.scaleBitrate(s.maxVideoBitrateBps, level))
    }

    private fun onEvent(ev: EngineEvent, e: PlayerEngine) {
        when (ev) {
            EngineEvent.Buffering -> if (firstFrame) { network.onRebuffer(); applyQualityLimit(adapter.onRebuffer(System.currentTimeMillis())) } else Unit
            EngineEvent.Ready -> if (!firstFrame && watchdog?.isActive != true) armPictureWatchdog(e)
            EngineEvent.FirstFrame -> {
                firstFrame = true; watchdog?.cancel()
                _state.value = _state.value.copy(phase = Phase.PLAYING, error = null, hasPicture = true)
                // La combinaison est retenue pour la chaîne si elle a demandé un repli ou un choix manuel et tient 5 s.
                rememberJob?.cancel()
                rememberJob = scope.launch { delay(5_000); if (tried.size > 1 || manualSwitch) key?.let { memory.remember(it, combo, null) } }
                sameRetries = 0
            }
            EngineEvent.Ended -> _state.value = _state.value.copy(phase = Phase.ENDED)
            is EngineEvent.Error -> onError(ev.kind)
        }
    }

    /** Le son passe mais aucune image au bout de 8 s alors que le flux a une piste vidéo : on change de décodeur/moteur. */
    private fun armPictureWatchdog(e: PlayerEngine) {
        watchdog = scope.launch {
            delay(8_000)
            if (!firstFrame && e.hasVideo) onError(PlayErrorKind.NO_PICTURE)
        }
    }

    private fun onError(kind: PlayErrorKind) {
        network.onError()
        val settingsNow = settings()
        scope.launch {
            if (PlaybackPlanner.shouldRetrySame(kind) && sameRetries < 1) {
                sameRetries++
                _notices.tryEmit(Notice.RETRYING)
                releaseEngine()          // libère la connexion AVANT de rouvrir (connexion unique)
                delay(1_500)
                launch(combo)
                return@launch
            }
            if (PlaybackPlanner.shouldFallBack(kind)) {
                val next = PlaybackPlanner.next(settingsNow.engine, tried)
                if (next != null) {
                    _notices.tryEmit(if (next.engine == EngineKind.VLC) Notice.USING_VLC else if (next.decoder == DecoderMode.SOFTWARE) Notice.USING_SOFTWARE else Notice.USING_EXO)
                    launch(next)
                    return@launch
                }
            }
            releaseEngine()
            _state.value = _state.value.copy(phase = Phase.ERROR, error = kind)
        }
    }

    private fun releaseEngine() {
        engine?.let { runCatching { it.release() } }
        engine = null
        container.removeAllViews()
    }

    fun release() {
        eventsJob?.cancel(); watchdog?.cancel(); rememberJob?.cancel(); adaptJob?.cancel()
        releaseEngine()
    }
}
