package com.ultratv.tv.nativeapp.ui.player.timeshift

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.data.timeshift.TimeshiftMath
import com.ultratv.tv.nativeapp.data.timeshift.TimeshiftPlanner
import com.ultratv.tv.nativeapp.data.timeshift.TimeshiftProxy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class TimeshiftSupport { OK, HLS_UNSUPPORTED, NO_SPACE, NOT_HTTP }

/** État affiché : retard sur le direct et position dans la fenêtre, en secondes. */
data class TimeshiftSnapshot(val behindSec: Int, val windowSec: Int, val failed: Boolean) {
    /** 0..1 : où se trouve la lecture dans la fenêtre tamponnée (1 = le direct). */
    val fraction: Float get() = if (windowSec <= 0) 1f else ((windowSec - behindSec).toFloat() / windowSec).coerceIn(0f, 1f)
}

/**
 * Pause du direct : tient le [TimeshiftProxy] (et donc l'unique connexion au fournisseur) le temps d'une session de lecteur.
 * Le tampon est supprimé à la fermeture ; les fichiers orphelins d'un arrêt brutal le sont au lancement suivant.
 */
@HiltViewModel
class TimeshiftViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    http: OkHttpClient,
    private val adaptive: AdaptiveProfile,
) : ViewModel() {
    private val dir = File(ctx.cacheDir, "timeshift")
    // Flux continu : ni délai global (callTimeout) ni lecture arrêtée trop tôt.
    private val client = http.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private var proxy: TimeshiftProxy? = null

    init { runCatching { dir.listFiles()?.forEach { it.delete() } } }

    val isActive: Boolean get() = proxy != null

    fun isLocalUrl(url: String) = url.startsWith("http://127.0.0.1:")

    private fun plan() = runCatching {
        dir.mkdirs()
        TimeshiftPlanner.plan(StatFs(dir.absolutePath).availableBytes, adaptive.state.value.device.tier, adaptive.state.value.device.info.screenHeight)
    }.getOrNull()

    fun support(url: String): TimeshiftSupport = when {
        !(url.startsWith("http://") || url.startsWith("https://")) -> TimeshiftSupport.NOT_HTTP
        url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> TimeshiftSupport.HLS_UNSUPPORTED
        plan() == null -> TimeshiftSupport.NO_SPACE
        else -> TimeshiftSupport.OK
    }

    /** Démarre le relais : à appeler APRÈS avoir libéré le flux en cours (une seule connexion). Renvoie l'URL locale. */
    fun activate(upstreamUrl: String, userAgent: String): String? {
        deactivate()
        val plan = plan() ?: return null
        val p = TimeshiftProxy(client, upstreamUrl, userAgent, File(dir, "buf-${System.nanoTime()}.ts"), plan.capacityBytes, plan.assumedBytesPerSec)
        p.start()
        proxy = p
        return p.localUrl(null)
    }

    /** Saut de [deltaSec] depuis la position de lecture ; renvoie l'URL locale à recharger. */
    fun jump(deltaSec: Int): String? {
        val p = proxy ?: return null
        val to = TimeshiftMath.jump(p.playPos, deltaSec, p.bytesPerSec, p.buffer.start, p.buffer.end)
        return p.localUrl(to)
    }

    fun goLive(): String? = proxy?.localUrl(null)

    fun snapshot(): TimeshiftSnapshot? {
        val p = proxy ?: return null
        return TimeshiftSnapshot(
            TimeshiftMath.behindLiveSec(p.playPos, p.buffer.end, p.bytesPerSec),
            TimeshiftMath.windowSec(p.buffer.start, p.buffer.end, p.bytesPerSec), p.upstreamFailed,
        )
    }

    /** Ferme le flux amont (libère la connexion du fournisseur) et supprime le tampon. */
    fun deactivate() { proxy?.close(); proxy = null }

    override fun onCleared() { deactivate() }
}
