package com.ultratv.tv.nativeapp.ui.player.engine

import android.content.Context
import android.net.Uri
import android.view.View
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Moteur LibVLC : MPEG-TS difficiles, AC3/E-AC3/DTS sans passthrough, MKV exotiques, flux mal formés.
 * `--quiet` : LibVLC ne journalise rien (ses messages peuvent contenir l'URL du flux).
 */
class VlcEngine(private val ctx: Context, override val config: EngineConfig) : PlayerEngine {
    override val kind = EngineKind.VLC
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<EngineEvent> = _events

    private val layout = VLCVideoLayout(ctx)
    override val view: View get() = layout

    private val libVlc: LibVLC
    private val mp: MediaPlayer
    private var firstFrame = false

    init {
        val b = config.buffer
        val opts = arrayListOf(
            "--quiet", "--no-drop-late-frames", "--no-skip-frames",
            "--network-caching=${b.vlcNetworkCachingMs}", "--live-caching=${b.vlcLiveCachingMs}", "--file-caching=${b.vlcFileCachingMs}",
            "--http-user-agent=${config.userAgent}", "--codec=all",
        )
        if (config.decoder != DecoderMode.HARDWARE) opts += "--no-mediacodec-dr"
        libVlc = LibVLC(ctx, opts)
        mp = MediaPlayer(libVlc)
        mp.attachViews(layout, null, false, false)
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Buffering -> _events.tryEmit(if (e.buffering >= 100f) EngineEvent.Ready else EngineEvent.Buffering)
                MediaPlayer.Event.Playing -> _events.tryEmit(EngineEvent.Ready)
                MediaPlayer.Event.Vout -> if (e.voutCount > 0 && !firstFrame) { firstFrame = true; _events.tryEmit(EngineEvent.FirstFrame) }
                MediaPlayer.Event.EndReached -> _events.tryEmit(EngineEvent.Ended)
                // LibVLC n'expose pas de code : on classe selon qu'une image est déjà passée.
                MediaPlayer.Event.EncounteredError -> _events.tryEmit(EngineEvent.Error(if (firstFrame) PlayErrorKind.NETWORK else PlayErrorKind.FORMAT))
            }
        }
    }

    override fun load(url: String, startPositionMs: Long) {
        firstFrame = false
        val media = Media(libVlc, Uri.parse(url)).apply {
            when (config.decoder) {
                DecoderMode.HARDWARE -> setHWDecoderEnabled(true, true)
                DecoderMode.SOFTWARE -> setHWDecoderEnabled(false, false)
                DecoderMode.AUTO -> setHWDecoderEnabled(true, false)
            }
            addOption(":network-caching=${config.buffer.vlcNetworkCachingMs}")
            addOption(":live-caching=${config.buffer.vlcLiveCachingMs}")
            addOption(":file-caching=${config.buffer.vlcFileCachingMs}")
            if (startPositionMs > 5_000) addOption(":start-time=${startPositionMs / 1000}")
        }
        mp.media = media
        media.release()
        mp.play()
    }

    override fun play() { mp.play() }
    override fun pause() { mp.pause() }
    override fun seekTo(ms: Long) { mp.time = ms }
    override val isPlaying get() = mp.isPlaying
    override val positionMs get() = mp.time.coerceAtLeast(0)
    override val durationMs get() = mp.length.takeIf { it > 0 } ?: -1L

    override fun audioTracks(): List<TrackInfo> = mp.audioTracks.orEmpty().filter { it.id >= 0 }.map { TrackInfo(it.id.toString(), it.name, it.id == mp.audioTrack) }
    override fun subtitleTracks(): List<TrackInfo> = mp.spuTracks.orEmpty().filter { it.id >= 0 }.map { TrackInfo(it.id.toString(), it.name, it.id == mp.spuTrack) }
    override fun selectAudio(id: String) { mp.audioTrack = id.toInt() }
    override fun selectSubtitle(id: String?) { mp.spuTrack = id?.toInt() ?: -1 }

    override fun setAspect(mode: AspectMode) {
        mp.setAspectRatio(null)
        when (mode) {
            AspectMode.FIT -> mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
            AspectMode.FILL -> mp.videoScale = MediaPlayer.ScaleType.SURFACE_FILL
            AspectMode.ZOOM -> mp.videoScale = MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            AspectMode.R16_9 -> mp.setAspectRatio("16:9")
            AspectMode.R4_3 -> mp.setAspectRatio("4:3")
        }
    }
    override fun setSpeed(speed: Float) { mp.rate = speed }
    override val hasVideo get() = mp.videoTracksCount > 0
    override fun limitQuality(maxHeight: Int, maxBitrateBps: Int?) { /* LibVLC choisit la variante HLS seul */ }

    override fun stats(): EngineStats {
        val v = mp.currentVideoTrack
        val s = mp.media?.stats
        return EngineStats(
            resolution = v?.let { "${it.width}×${it.height}" }, videoCodec = v?.codec,
            frameRate = v?.frameRateNum?.takeIf { it > 0 && v.frameRateDen > 0 }?.let { it.toFloat() / v.frameRateDen },
            videoBitrateKbps = s?.inputBitrate?.let { (it * 8000).toInt() }, audioCodec = null, audioChannels = null,
            bufferedSeconds = null, droppedFrames = s?.lostPictures, hardwareDecoding = config.decoder != DecoderMode.SOFTWARE,
        )
    }

    override fun release() {
        runCatching { mp.setEventListener(null) }
        runCatching { mp.stop() }
        runCatching { mp.detachViews() }
        runCatching { mp.release() }
        runCatching { libVlc.release() }
    }
}
