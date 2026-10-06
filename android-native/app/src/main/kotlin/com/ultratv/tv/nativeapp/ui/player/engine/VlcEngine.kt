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

    /** Direct : tampon de DÉMARRAGE (comme Media3), pas le tampon minimum — 5 s d'attente avant l'image sinon. */
    private val networkCachingMs = if (config.isLive) config.buffer.startMs.coerceAtLeast(1_000) else config.buffer.vlcNetworkCachingMs
    @Volatile private var released = false

    init {
        val b = config.buffer
        val opts = arrayListOf(
            "--quiet", "--no-drop-late-frames", "--no-skip-frames",
            "--network-caching=$networkCachingMs", "--live-caching=${b.vlcLiveCachingMs}", "--file-caching=${b.vlcFileCachingMs}",
            "--http-user-agent=${config.userAgent}", "--codec=all",
        )
        if (config.decoder != DecoderMode.HARDWARE) opts += "--no-mediacodec-dr"
        opts += com.ultratv.tv.nativeapp.data.subtitles.SubtitleStyleMapper.toVlcOptions(config.subtitleStyle)
        libVlc = LibVLC(ctx, opts)
        mp = MediaPlayer(libVlc)
        mp.attachViews(layout, null, false, false)
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Buffering -> _events.tryEmit(if (e.buffering >= 100f) EngineEvent.Ready else EngineEvent.Buffering)
                MediaPlayer.Event.Playing -> _events.tryEmit(EngineEvent.Ready)
                MediaPlayer.Event.Vout -> if (e.voutCount > 0 && !firstFrame) { firstFrame = true; _events.tryEmit(EngineEvent.FirstFrame); autoSelectTracks() }
                MediaPlayer.Event.EndReached -> _events.tryEmit(EngineEvent.Ended)
                // LibVLC n'expose pas de code : on classe selon qu'une image est déjà passée.
                MediaPlayer.Event.EncounteredError -> _events.tryEmit(EngineEvent.Error(if (firstFrame) PlayErrorKind.NETWORK else PlayErrorKind.FORMAT))
            }
        }
    }

    /** Choix automatique audio / sous-titres selon les listes de langues préférées + décalage enregistré. */
    private fun autoSelectTracks() {
        val picker = com.ultratv.tv.nativeapp.data.subtitles.TrackLanguagePicker
        if (config.preferredAudio.isNotEmpty()) {
            val tracks = mp.audioTracks.orEmpty().filter { it.id >= 0 }
            if (tracks.size > 1) picker.pick(tracks.map { it.id.toString() to it.name }, config.preferredAudio)?.let { mp.audioTrack = it.toInt() }
        }
        if (config.textOff) mp.spuTrack = -1
        else if (config.preferredText.isNotEmpty()) {
            val tracks = mp.spuTracks.orEmpty().filter { it.id >= 0 }
            picker.pick(tracks.map { it.id.toString() to it.name }, config.preferredText)?.let { mp.spuTrack = it.toInt() }
        }
        if (config.subtitleStyle.delayMs != 0) setSubtitleDelay(config.subtitleStyle.delayMs)
    }

    /** Les options `--freetype-*` se fixent à la création de LibVLC : appliquées au prochain démarrage du moteur. */
    override fun applySubtitleStyle(style: com.ultratv.tv.nativeapp.data.subtitles.SubtitleStyle): Boolean = false
    override fun setSubtitleDelay(ms: Int): Boolean { mp.setSpuDelay(ms * 1000L); return true }
    override fun addExternalSubtitle(path: String): Boolean =
        mp.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, Uri.fromFile(java.io.File(path)), true)

    /** Zapping : le MÊME LibVLC / MediaPlayer enchaîne le média suivant (options de cache posées par média). */
    override val reusable: Boolean get() = true

    override fun load(url: String, startPositionMs: Long) {
        if (released) return
        firstFrame = false
        // Flux précédent arrêté AVANT d'ouvrir le suivant (connexion unique du fournisseur).
        if (mp.media != null) runCatching { mp.stop() }
        val media = Media(libVlc, Uri.parse(url)).apply {
            when (config.decoder) {
                DecoderMode.HARDWARE -> setHWDecoderEnabled(true, true)
                DecoderMode.SOFTWARE -> setHWDecoderEnabled(false, false)
                DecoderMode.AUTO -> setHWDecoderEnabled(true, false)
            }
            addOption(":network-caching=$networkCachingMs")
            addOption(":live-caching=${config.buffer.vlcLiveCachingMs}")
            addOption(":file-caching=${config.buffer.vlcFileCachingMs}")
            if (maxHeight != Int.MAX_VALUE) addOption(":preferred-resolution=${maxHeight.coerceAtLeast(240)}")
            maxBitrateBps?.let { addOption(":adaptive-maxbitrate=$it") }
            if (startPositionMs > 5_000) addOption(":start-time=${startPositionMs / 1000}")
        }
        mp.media = media
        media.release()
        mp.play()
    }

    override fun play() { mp.play() }
    override fun pause() { mp.pause() }
    override fun seekTo(ms: Long) { mp.time = ms }
    // Lus par les boucles du lecteur (500 ms, 10 s) : jamais sur un MediaPlayer natif déjà libéré (plantage natif).
    override val isPlaying get() = !released && mp.isPlaying
    override val positionMs get() = if (released) 0L else mp.time.coerceAtLeast(0)
    override val durationMs get() = if (released) -1L else mp.length.takeIf { it > 0 } ?: -1L

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
    override val hasVideo get() = !released && mp.videoTracksCount > 0
    private var maxHeight = Int.MAX_VALUE
    private var maxBitrateBps: Int? = null

    /** LibVLC n'a pas de plafond dur : on borne la variante HLS/DASH choisie (pris en compte au prochain [load]). */
    override fun limitQuality(maxHeight: Int, maxBitrateBps: Int?) { this.maxHeight = maxHeight; this.maxBitrateBps = maxBitrateBps }

    override fun stats(): EngineStats {
        if (released) return EngineStats()
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
        if (released) return
        released = true
        runCatching { mp.setEventListener(null) }
        runCatching { mp.stop() }
        runCatching { mp.detachViews() }
        runCatching { mp.release() }
        runCatching { libVlc.release() }
    }
}
