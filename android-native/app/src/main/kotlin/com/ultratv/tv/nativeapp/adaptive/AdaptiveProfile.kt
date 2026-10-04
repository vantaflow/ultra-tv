package com.ultratv.tv.nativeapp.adaptive

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import com.ultratv.tv.nativeapp.ui.player.engine.BufferParams
import com.ultratv.tv.nativeapp.ui.player.engine.BufferPlanner
import com.ultratv.tv.nativeapp.ui.player.engine.BufferPreset
import com.ultratv.tv.nativeapp.ui.player.engine.CustomBuffer
import com.ultratv.tv.nativeapp.ui.player.engine.DecoderMode
import com.ultratv.tv.nativeapp.ui.player.engine.EngineChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import javax.inject.Inject
import javax.inject.Singleton

/** Source des données brutes de l'appareil : réelle sur Android, fausse en test. */
interface DeviceInfoSource { fun read(): DeviceInfo }

class AndroidDeviceInfoSource(private val ctx: Context) : DeviceInfoSource {
    override fun read(): DeviceInfo {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val dm = ctx.resources.displayMetrics
        val hdr = runCatching {
            @Suppress("DEPRECATION")
            (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(0)
                ?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        }.getOrDefault(false)
        return DeviceInfo(
            ramMb = (mi.totalMem / 1_048_576L).toInt(),
            memoryClassMb = am.memoryClass,
            lowRamFlag = am.isLowRamDevice,
            cores = Runtime.getRuntime().availableProcessors(),
            maxFreqMhz = runCatching { java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toInt() / 1000 }.getOrNull(),
            is64Bit = android.os.Process.is64Bit(),
            sdk = Build.VERSION.SDK_INT,
            h264 = hw("video/avc"), hevc = hw("video/hevc"), vp9 = hw("video/x-vnd.on2.vp9"), av1 = hw("video/av01"),
            screenWidth = maxOf(dm.widthPixels, dm.heightPixels), screenHeight = minOf(dm.widthPixels, dm.heightPixels),
            hdr = hdr,
            benchmarkMs = null,
        )
    }

    private fun hw(mime: String): HwCodec? = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.asSequence()
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
            .filter { if (Build.VERSION.SDK_INT >= 29) it.isHardwareAccelerated else !(it.name.startsWith("OMX.google") || it.name.startsWith("c2.android")) }
            .mapNotNull { runCatching { it.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() }
            .map { v -> HwCodec(v.supportedWidths.upper, v.supportedHeights.upper, v.supportedFrameRates.upper.toInt()) }
            .maxByOrNull { it.maxWidth * it.maxHeight }
    }.getOrNull()

    companion object {
        /** Micro-benchmark < 300 ms : analyse JSON répétée, à lancer hors thread principal. */
        fun benchmarkMs(): Long {
            val t0 = System.nanoTime()
            val sb = StringBuilder("[")
            for (i in 0 until 400) sb.append("{\"id\":$i,\"name\":\"Chaîne $i\",\"cat\":\"${i % 17}\",\"logo\":\"http://x/y/$i.png\"},")
            sb.setLength(sb.length - 1); sb.append("]")
            repeat(3) { val a = org.json.JSONArray(sb.toString()); for (i in 0 until a.length()) a.getJSONObject(i).getString("name") }
            return (System.nanoTime() - t0) / 1_000_000
        }
    }
}

/**
 * Réseau : type et débit annoncés par Android + débit/latence MESURÉS sur les vrais téléchargements (API, EPG,
 * segments) + rebufferings et erreurs récents. Réévalué à chaque changement de réseau et en continu.
 */
@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var ewmaKbps: Double? = null
    private var ewmaTtfb: Double? = null
    private val rebuffers = ArrayDeque<Long>()
    private val errors = ArrayDeque<Long>()
    private val _sample = MutableStateFlow(snapshot())
    val sample: StateFlow<NetSample> = _sample.asStateFlow()


    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { reset(); publish() }
                override fun onLost(network: Network) { publish() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { publish() }
            })
        }
    }

    private fun reset() { synchronized(this) { ewmaKbps = null; ewmaTtfb = null } }

    /** Un transfert terminé : [bytes] reçus en [ms]. Les petits transferts (latence dominante) sont ignorés. */
    fun recordTransfer(bytes: Long, ms: Long) {
        if (bytes < 128_000 || ms <= 0) return
        val kbps = bytes * 8.0 / ms
        synchronized(this) { ewmaKbps = ewmaKbps?.let { it * 0.7 + kbps * 0.3 } ?: kbps }
        publish()
    }
    fun recordTtfb(ms: Long) { synchronized(this) { ewmaTtfb = ewmaTtfb?.let { it * 0.7 + ms * 0.3 } ?: ms.toDouble() }; publish() }
    /** Débit estimé par le lecteur (bits/s) : source la plus représentative pendant la lecture. */
    fun recordPlayerBitrate(bps: Long) { if (bps > 0) recordTransfer((bps / 8), 1000) }
    fun onRebuffer() { synchronized(this) { rebuffers.addLast(System.currentTimeMillis()) }; publish() }
    fun onError() { synchronized(this) { errors.addLast(System.currentTimeMillis()) }; publish() }

    private fun prune(q: ArrayDeque<Long>): Int { val now = System.currentTimeMillis(); while (q.isNotEmpty() && now - q.first() > 120_000) q.removeFirst(); return q.size }

    private fun publish() { _sample.value = snapshot() }

    private fun snapshot(): NetSample = synchronized(this) {
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val type = when {
            caps == null -> NetType.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetType.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.MOBILE
            else -> NetType.OTHER
        }
        NetSample(type, caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }, caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) } ?: false,
            ewmaKbps?.toInt(), ewmaTtfb?.toInt(), prune(rebuffers), prune(errors))
    }

    /** Intercepteur OkHttp : temps jusqu'au premier octet et débit réel de chaque réponse. */
    val interceptor = Interceptor { chain ->
        val t0 = System.nanoTime()
        val resp: Response = chain.proceed(chain.request())
        val ttfb = (System.nanoTime() - t0) / 1_000_000
        recordTtfb(ttfb)
        val body = resp.body ?: return@Interceptor resp
        val tStart = System.nanoTime()
        val counting = object : ForwardingSource(body.source()) {
            var bytes = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val n = super.read(sink, byteCount)
                if (n < 0) recordTransfer(bytes, (System.nanoTime() - tStart) / 1_000_000) else bytes += n
                return n
            }
        }
        resp.newBuilder().body(object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source(): BufferedSource = (counting as Source).buffer()
        }).build()
    }
}

data class AdaptiveState(
    val device: DeviceProfile,
    val net: NetSample,
    val quality: NetQuality,
    val auto: AutoSettings,
)

/** Réglages de lecture EFFECTIFS : un choix manuel l'emporte toujours, sinon l'automatique. */
data class ResolvedPlayback(
    val engine: EngineChoice,
    val decoder: DecoderMode,
    val buffer: BufferParams,
    val bufferPreset: BufferPreset,
    val maxVideoHeight: Int,
    val maxVideoBitrateBps: Int?,
    val preferH264: Boolean,
    val manualEngine: Boolean,
    val manualDecoder: Boolean,
    val manualBuffer: Boolean,
    val lowRam: Boolean = false,
    val heapClassMb: Int = 192,
)

object PlaybackResolver {
    fun resolve(auto: AutoSettings, engine: String, decoder: String, bufferPreset: String, custom: CustomBuffer, heapClassMb: Int, levelBump: Int = 0): ResolvedPlayback {
        val eng = when (engine) { "exo" -> EngineChoice.EXO; "vlc" -> EngineChoice.VLC; else -> EngineChoice.AUTO }
        val dec = when (decoder) { "hw" -> DecoderMode.HARDWARE; "sw" -> DecoderMode.SOFTWARE; else -> DecoderMode.AUTO }
        val manualBuf = bufferPreset != "auto"
        val preset = when (bufferPreset) {
            "low_latency" -> BufferPreset.LOW_LATENCY; "balanced" -> BufferPreset.BALANCED; "stable" -> BufferPreset.STABLE; "custom" -> BufferPreset.CUSTOM
            else -> com.ultratv.tv.nativeapp.adaptive.PlaybackAdapter.bump(auto.bufferPreset, levelBump)
        }
        return ResolvedPlayback(
            engine = eng, decoder = dec,
            buffer = BufferPlanner.resolve(preset, custom, heapClassMb, auto.lowRam),
            bufferPreset = preset,
            maxVideoHeight = auto.maxVideoHeight,
            maxVideoBitrateBps = PlaybackAdapter.scaleBitrate(auto.maxVideoBitrateBps, levelBump),
            preferH264 = auto.preferH264,
            manualEngine = eng != EngineChoice.AUTO, manualDecoder = dec != DecoderMode.AUTO, manualBuffer = manualBuf,
            lowRam = auto.lowRam, heapClassMb = heapClassMb,
        )
    }
    fun resolve(auto: AutoSettings, p: UserPrefs, heapClassMb: Int, levelBump: Int = 0) =
        resolve(auto, p.playerEngine, p.decoderMode, p.bufferPreset, CustomBuffer(p.bufMinSec, p.bufMaxSec, p.bufStartSec, p.bufRebufferSec, p.bufMaxMb), heapClassMb, levelBump)
}

/**
 * Profil adaptatif CENTRAL : appareil (calculé une fois, mis en cache, réévalué après mise à jour) + réseau
 * (continu). Aucun écran ne lit la RAM ou `isLowRamDevice` directement : tous lisent ce profil.
 */
@Singleton
class AdaptiveProfile @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val network: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sp = ctx.getSharedPreferences("adaptive_device", Context.MODE_PRIVATE)
    private val source: DeviceInfoSource = AndroidDeviceInfoSource(ctx)
    private var previousQuality: NetQuality? = null

    private val _state: MutableStateFlow<AdaptiveState>
    val state: StateFlow<AdaptiveState> get() = _state.asStateFlow()

    val heapClassMb: Int = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass

    init {
        val device = loadCached() ?: DeviceProfile(source.read(), DeviceClassifier.tier(source.read())).also { }
        _state = MutableStateFlow(compute(device, network.sample.value))
        if (sp.getInt("version", -1) != versionCode() || !sp.contains("bench")) scope.launch { evaluateDevice() }
        scope.launch { network.sample.collect { s -> delay(500); _state.value = compute(_state.value.device, s) } }
    }

    private fun versionCode(): Int = runCatching { @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode }.getOrDefault(0)

    private fun loadCached(): DeviceProfile? {
        if (sp.getInt("version", -1) != versionCode() || !sp.contains("bench")) return null
        val base = source.read().copy(benchmarkMs = sp.getLong("bench", 0))
        return DeviceProfile(base, DeviceClassifier.tier(base))
    }

    /** Évalue (ou réévalue) l'appareil : lecture des capacités + micro-benchmark, mise en cache. */
    fun reevaluate() { scope.launch { evaluateDevice() } }

    private suspend fun evaluateDevice() {
        val bench = AndroidDeviceInfoSource.benchmarkMs()
        val info = source.read().copy(benchmarkMs = bench)
        val profile = DeviceProfile(info, DeviceClassifier.tier(info))
        sp.edit().putInt("version", versionCode()).putLong("bench", bench).apply()
        _state.value = compute(profile, network.sample.value)
    }

    private fun compute(device: DeviceProfile, net: NetSample): AdaptiveState {
        val q = NetClassifier.quality(net, previousQuality).also { previousQuality = it }
        return AdaptiveState(device, net, q, AutoTuner.settings(device, q, net.metered, net.ttfbMs))
    }
}

/** ViewModel mince : expose le profil aux composables. */
@dagger.hilt.android.lifecycle.HiltViewModel
class AdaptiveViewModel @Inject constructor(private val profile: AdaptiveProfile) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<AdaptiveState> get() = profile.state
    fun reevaluate() = profile.reevaluate()
}

/** Profil courant, lisible par tout composable. */
val LocalAdaptive = androidx.compose.runtime.compositionLocalOf<AdaptiveState?> { null }
