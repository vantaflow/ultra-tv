package com.ultratv.tv.nativeapp.ui.player.engine

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Moteur Media3 / ExoPlayer. Décodage : matériel (extensions OFF), logiciel (FFmpeg audio + codecs logiciels), auto. */
@UnstableApi
class ExoEngine(private val ctx: Context, override val config: EngineConfig) : PlayerEngine {
    override val kind = EngineKind.EXO
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<EngineEvent> = _events

    private val player: ExoPlayer
    private val playerView: PlayerView = PlayerView(ctx).apply { useController = false; setKeepContentOnPlayerReset(false) }
    override val view: View get() = playerView

    private val hardwareFirst = MediaCodecSelector { mime, secure, tunneling ->
        val all = MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
        val hw = all.filter { !it.softwareOnly }
        if (hw.isNotEmpty()) hw else all
    }
    private val softwareFirst = MediaCodecSelector { mime, secure, tunneling ->
        MediaCodecUtil.getDecoderInfos(mime, secure, tunneling).sortedBy { if (it.softwareOnly) 0 else 1 }
    }

    init {
        val b = config.buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(b.minMs, b.maxMs.coerceAtLeast(b.minMs), b.startMs, b.rebufferMs)
            .setTargetBufferBytes(b.maxBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val http = DefaultHttpDataSource.Factory().setUserAgent(config.userAgent).setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000).setReadTimeoutMs(15_000)
        val mediaSources = DefaultMediaSourceFactory(ctx).setDataSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(ctx, http),
        )
        val renderers = DefaultRenderersFactory(ctx).apply {
            setEnableDecoderFallback(true)
            when (config.decoder) {
                DecoderMode.HARDWARE -> { setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF); setMediaCodecSelector(hardwareFirst) }
                DecoderMode.SOFTWARE -> { setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER); setMediaCodecSelector(softwareFirst) }
                DecoderMode.AUTO -> setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            }
        }
        player = ExoPlayer.Builder(ctx, renderers).setLoadControl(loadControl).setMediaSourceFactory(mediaSources).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> _events.tryEmit(EngineEvent.Buffering)
                        Player.STATE_READY -> _events.tryEmit(EngineEvent.Ready)
                        Player.STATE_ENDED -> _events.tryEmit(EngineEvent.Ended)
                    }
                }
                override fun onRenderedFirstFrame() { _events.tryEmit(EngineEvent.FirstFrame) }
                override fun onPlayerError(error: PlaybackException) {
                    val status = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                    _events.tryEmit(EngineEvent.Error(PlaybackPlanner.classifyExo(error.errorCode, status)))
                }
                override fun onVideoSizeChanged(videoSize: VideoSize) { applyFrameRate() }
            })
        }
        playerView.player = player
        playerView.subtitleView?.apply { setApplyEmbeddedStyles(false); setApplyEmbeddedFontSizes(false) }
        applySubtitleStyle(config.subtitleStyle)
        // Choix automatique des pistes : listes ordonnées de langues préférées (la première disponible l'emporte).
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*config.preferredAudio.toTypedArray())
            .setPreferredTextLanguages(*config.preferredText.toTypedArray())
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, config.textOff)
            .build()
    }

    override fun applySubtitleStyle(style: com.ultratv.tv.nativeapp.data.subtitles.SubtitleStyle): Boolean {
        val c = com.ultratv.tv.nativeapp.data.subtitles.SubtitleStyleMapper.toCaption(style)
        playerView.subtitleView?.apply {
            setStyle(androidx.media3.ui.CaptionStyleCompat(c.foregroundArgb, c.backgroundArgb, android.graphics.Color.TRANSPARENT, c.edgeType, c.edgeArgb, null))
            setFractionalTextSize(c.textSizeFraction)
            setBottomPaddingFraction(c.bottomPaddingFraction)
        }
        return true
    }

    override fun addExternalSubtitle(path: String): Boolean {
        val cur = player.currentMediaItem ?: return false
        val sub = MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(java.io.File(path)))
            .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val pos = player.currentPosition
        player.setMediaItem(cur.buildUpon().setSubtitleConfigurations(listOf(sub)).build(), pos)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        player.prepare()
        return true
    }

    override fun load(url: String, startPositionMs: Long) {
        val item = MediaItem.Builder().setUri(url).apply {
            if (config.isLive) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(config.buffer.liveOffsetMs).build())
        }.build()
        player.setMediaItem(item)
        player.prepare()
        if (startPositionMs > 5_000) player.seekTo(startPositionMs)
        player.play()
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override val isPlaying get() = player.isPlaying
    override val positionMs get() = player.currentPosition
    override val durationMs get() = if (player.duration == C.TIME_UNSET) -1L else player.duration

    private fun tracks(type: Int): List<TrackInfo> = player.currentTracks.groups.withIndex().filter { it.value.type == type }.flatMap { (gi, g) ->
        (0 until g.length).map { ti ->
            val f = g.getTrackFormat(ti)
            val label = listOfNotNull(f.label, f.language?.takeIf { it != "und" }, f.sampleMimeType?.substringAfter('/')?.uppercase(), f.channelCount.takeIf { it > 0 && type == C.TRACK_TYPE_AUDIO }?.let { "${it}ch" })
                .joinToString(" · ").ifBlank { "#${ti + 1}" }
            TrackInfo("$gi:$ti", label, g.isTrackSelected(ti))
        }
    }
    override fun audioTracks() = tracks(C.TRACK_TYPE_AUDIO)
    override fun subtitleTracks() = tracks(C.TRACK_TYPE_TEXT)

    private fun select(id: String, type: Int, enableText: Boolean = false) {
        val (gi, ti) = id.split(':').map { it.toInt() }
        val group = player.currentTracks.groups.getOrNull(gi) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enableText && type == C.TRACK_TYPE_TEXT)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, ti)).build()
    }
    override fun selectAudio(id: String) = select(id, C.TRACK_TYPE_AUDIO)
    override fun selectSubtitle(id: String?) {
        if (id == null) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        else select(id, C.TRACK_TYPE_TEXT, enableText = true)
    }

    override fun setAspect(mode: AspectMode) {
        playerView.resizeMode = when (mode) {
            AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectMode.R16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            AspectMode.R4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        }
    }
    override fun setSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed) }
    override val hasVideo get() = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
    override fun limitQuality(maxHeight: Int, maxBitrateBps: Int?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, maxHeight.coerceAtLeast(240))
            .setMaxVideoBitrate(maxBitrateBps ?: Int.MAX_VALUE)
            .build()
    }

    override fun stats(): EngineStats {
        val v = player.videoFormat; val a = player.audioFormat
        val counters = player.videoDecoderCounters
        val hw = v?.sampleMimeType?.let { true }
        return EngineStats(
            resolution = v?.let { "${it.width}×${it.height}" }, videoCodec = v?.sampleMimeType?.substringAfter('/'),
            frameRate = v?.frameRate?.takeIf { it > 0 }, videoBitrateKbps = v?.bitrate?.takeIf { it > 0 }?.div(1000),
            audioCodec = a?.sampleMimeType?.substringAfter('/'), audioChannels = a?.channelCount?.takeIf { it > 0 },
            bufferedSeconds = ((player.bufferedPosition - player.currentPosition).coerceAtLeast(0) / 1000).toInt(),
            droppedFrames = counters?.let { it.maxConsecutiveDroppedBufferCount.let { _ -> it.droppedBufferCount } },
            hardwareDecoding = hw?.let { config.decoder != DecoderMode.SOFTWARE },
        )
    }

    /** Adapte la fréquence de l'écran à celle de la vidéo (24/25/50 ips), si l'option est active. */
    private fun applyFrameRate() {
        if (!config.autoFrameRate) return
        val act = (ctx as? Activity) ?: return
        val fps = player.videoFormat?.frameRate?.takeIf { it > 0f } ?: return
        @Suppress("DEPRECATION") val display = act.windowManager.defaultDisplay ?: return
        val target = display.supportedModes.minByOrNull { m ->
            val multiple = (m.refreshRate / fps).coerceAtLeast(1f)
            kotlin.math.abs(m.refreshRate - fps * kotlin.math.round(multiple))
        } ?: return
        val lp = act.window.attributes
        if (lp.preferredDisplayModeId != target.modeId) { lp.preferredDisplayModeId = target.modeId; act.window.attributes = lp }
    }

    override fun release() {
        runCatching { playerView.player = null }
        runCatching { player.release() }
    }
}
