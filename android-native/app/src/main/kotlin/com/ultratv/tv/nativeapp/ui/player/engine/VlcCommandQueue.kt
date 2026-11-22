package com.ultratv.tv.nativeapp.ui.player.engine

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Fil dédié aux appels natifs d'un moteur LibVLC, un par moteur. `MediaPlayer.stop()` et `setVideoTrack` attendent le fil
 * de sortie vidéo de LibVLC (parfois plusieurs secondes avec l'affichage direct MediaCodec) : appelés depuis le fil
 * principal, ils provoquaient des ANR (nativeStop, nativeSetVideoTrack). Ici ils s'exécutent un par un, dans l'ordre
 * d'envoi ; [close] passe la libération en dernier puis arrête le fil (aucune fuite).
 */
class VlcCommandQueue(name: String) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true } }
    @Volatile private var closed = false

    /** Envoie [block] au fil du moteur ; ignoré après [close]. Une exception native ne tue pas le fil. */
    fun post(block: () -> Unit) {
        if (closed) return
        try { executor.execute { runCatching(block) } } catch (_: RejectedExecutionException) { }
    }

    /**
     * Envoie [block] et l'attend au plus [timeoutMs] : pour un appel qui doit, en temps normal, précéder la suite (sortie
     * vidéo coupée avant la destruction de la surface) sans jamais bloquer l'appelant plus longtemps. Vrai s'il a fini.
     */
    fun postAndWait(timeoutMs: Long, block: () -> Unit): Boolean {
        if (closed) return false
        val f = try { executor.submit { runCatching(block) } } catch (_: RejectedExecutionException) { return false }
        return runCatching { f.get(timeoutMs, TimeUnit.MILLISECONDS); true }.getOrDefault(false)
    }

    /** Dernière commande ([finalBlock] : stop → detach → release), après toutes celles en attente ; puis le fil s'arrête. */
    fun close(finalBlock: () -> Unit) {
        if (closed) return
        try { executor.execute { runCatching(finalBlock) } } catch (_: RejectedExecutionException) { }
        closed = true
        executor.shutdown()
    }

    /** Tests : attend la fin du fil après [close]. */
    internal fun awaitTermination(timeoutMs: Long): Boolean = executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)
}
