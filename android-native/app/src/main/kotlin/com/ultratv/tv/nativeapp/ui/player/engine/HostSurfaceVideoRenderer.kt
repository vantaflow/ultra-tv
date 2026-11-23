package com.ultratv.tv.nativeapp.ui.player.engine

import android.view.Surface
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer

/**
 * Sortie vidéo du décodeur avant la première vraie surface (pur, testé).
 *
 * Constat box (Android 14, Amlogic, 1.3.73) : à l'entrée du Direct ou du Guide, le moteur démarre AVANT que l'encadré
 * d'aperçu n'ait créé sa surface. Media3 configure alors le décodeur sur sa surface factice (PlaceholderSurface), puis
 * bascule sur la vraie surface par `setOutputSurface` une fois l'encadré à l'écran. Sur ce décodeur, cette bascule faite
 * avant la première image le laisse muet côté vidéo : son seul, aucune image pendant ~30 s, jusqu'au changement de
 * surface suivant (journal : `setOutputSurface` vers la surface de l'encadré, puis `firstFrame ms=29333`).
 *
 * Règle : tant qu'AUCUNE vraie surface n'a été donnée au moteur, la surface factice est refusée — le décodeur vidéo
 * attend la surface de l'hôte à l'écran et s'y configure directement (le son, lui, démarre). Une fois une vraie surface
 * vue, comportement normal de Media3 : surface factice pendant un changement d'hôte (aperçu ↔ plein écran sans
 * recréer le décodeur).
 */
object VideoOutputPolicy {
    /** Vrai : la surface factice est refusée (le décodeur attend une vraie surface). */
    fun refusePlaceholder(realOutputNow: Boolean, realOutputSeen: Boolean): Boolean = !realOutputNow && !realOutputSeen

    /** Suit les sorties vidéo données au moteur (ordre réel des messages du lecteur). */
    class Tracker {
        var now = false
            private set
        var seen = false
            private set

        fun onOutput(real: Boolean) { now = real; if (real) seen = true }

        val refusePlaceholder: Boolean get() = refusePlaceholder(now, seen)
    }
}

/**
 * Rendu vidéo Media3 qui applique [VideoOutputPolicy] : `codecNeedsSetOutputSurfaceWorkaround` (lu par Media3 pour décider
 * d'utiliser la surface factice et, à la configuration du décodeur, pour choisir entre `setOutputSurface` et une
 * recréation) répond vrai tant qu'aucune vraie surface n'a été vue.
 */
@UnstableApi
internal class HostSurfaceVideoRenderer(builder: Builder) : MediaCodecVideoRenderer(builder) {
    private val outputs = VideoOutputPolicy.Tracker()

    override fun handleMessage(messageType: Int, message: Any?) {
        if (messageType == Renderer.MSG_SET_VIDEO_OUTPUT) outputs.onOutput((message as? Surface)?.isValid == true)
        super.handleMessage(messageType, message)
    }

    override fun codecNeedsSetOutputSurfaceWorkaround(name: String): Boolean =
        super.codecNeedsSetOutputSurfaceWorkaround(name) || outputs.refusePlaceholder
}

/** Filet de sécurité de la surface ([PlaybackSession]) : décision pure, testée. */
object SurfaceWatch {
    /**
     * Rebrancher la sortie vidéo : même hôte depuis le début de l'attente, sa surface toujours à l'écran, flux avec vidéo
     * dont le son avance, et toujours aucune image.
     */
    fun shouldReattach(sameHost: Boolean, surfaceShown: Boolean, hasVideo: Boolean, playing: Boolean, firstFrame: Boolean): Boolean =
        sameHost && surfaceShown && hasVideo && playing && !firstFrame
}
