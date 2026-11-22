package com.ultratv.tv.nativeapp.ui.player.engine

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * Flux dont les horodatages repartent en ARRIÈRE (fournisseur qui relance son flux : journal « Unexpected audio track
 * timestamp discontinuity: expected 1000024808333, got 1000000019000 »).
 *
 * Media3 1.11 : la sortie audio signale la discontinuité (onPositionDiscontinuity) au moment où elle ÉCRIT le tampon
 * décalé ; le moteur audio s'autorise alors UN recul de position, consommé dès le relevé suivant. Mais la position de la
 * sortie ne recule qu'une fois ce tampon réellement joué (point de reprise mis en file, quelques centaines de ms plus
 * tard) : l'autorisation est déjà perdue, et `max(ancienne, nouvelle)` garde l'ancienne position pendant toute la durée
 * du recul (10 à 25 s). Position figée, images jugées en retard et jetées : image gelée, son qui continue, puis
 * « live frozen (position) » et reconnexion.
 *
 * Cette porte (logique pure, testée) dit QUAND annoncer le recul : au premier relevé où la position recule vraiment de
 * plus de [thresholdUs] après un signal encore frais. La lecture continue alors sur le nouveau minutage.
 */
class AudioJumpGate(private val thresholdUs: Long = 200_000L, private val expiryUs: Long = 5_000_000L) {
    private var pending = false
    private var lastUs = UNSET
    private var signalAtUs = UNSET

    /** La sortie a signalé une discontinuité (tampon décalé écrit, pas encore joué). */
    fun onDiscontinuity() { pending = true; signalAtUs = lastUs }

    /** Relevé de position de la sortie ; vrai = annoncer maintenant le recul au moteur audio. */
    fun onPosition(us: Long): Boolean {
        if (us == UNSET) return false
        val prev = lastUs
        lastUs = us
        if (!pending || prev == UNSET) return false
        if (us < prev - thresholdUs) { pending = false; return true }
        // Saut en avant (accepté tel quel par Media3) ou recul jamais venu : le signal expire.
        if (signalAtUs != UNSET && us - signalAtUs > expiryUs) pending = false
        return false
    }

    fun reset() { pending = false; lastUs = UNSET; signalAtUs = UNSET }

    private companion object { const val UNSET = Long.MIN_VALUE }   // = AudioSink.CURRENT_POSITION_NOT_SET
}

/** Ligne de journal d'un saut d'horodatage du flux (pure, testée). Jamais d'adresse. */
internal object TimestampJumpLog {
    fun line(expectedUs: Long, actualUs: Long, n: Long, live: Boolean): String {
        val deltaMs = (actualUs - expectedUs) / 1_000
        val dir = if (deltaMs < 0) "back" else "forward"
        val s = kotlin.math.abs(deltaMs) / 100 / 10.0
        return "${if (live) "live " else ""}timestamp jump $dir=${s}s n=$n engine=EXO -> keep playing"
    }
}

/**
 * Sortie audio qui ré-annonce le recul de position au moment où il se produit vraiment (voir [AudioJumpGate]).
 * Tout le reste est délégué à la sortie de Media3. Appelée sur le fil de lecture d'ExoPlayer uniquement.
 */
@UnstableApi
internal class JumpTolerantAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private val gate = AudioJumpGate()
    private var listener: AudioSink.Listener? = null

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        super.setListener(GateListener(listener))
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val p = super.getCurrentPositionUs(sourceEnded)
        // Annoncé AVANT que le moteur audio ne compare la position (il lit son autorisation juste après cet appel).
        if (gate.onPosition(p)) listener?.onPositionDiscontinuity()
        return p
    }

    override fun flush() { gate.reset(); super.flush() }
    override fun reset() { gate.reset(); super.reset() }

    private inner class GateListener(private val inner: AudioSink.Listener) : AudioSink.Listener {
        override fun onPositionDiscontinuity() { gate.onDiscontinuity(); inner.onPositionDiscontinuity() }
        override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) = inner.onPositionAdvancing(playoutStartSystemTimeMs)
        override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) = inner.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs)
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = inner.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        override fun onOffloadBufferEmptying() = inner.onOffloadBufferEmptying()
        override fun onOffloadBufferFull() = inner.onOffloadBufferFull()
        override fun onAudioSinkError(audioSinkError: Exception) = inner.onAudioSinkError(audioSinkError)
        override fun onAudioCapabilitiesChanged() = inner.onAudioCapabilitiesChanged()
        override fun onAudioTrackInitialized(audioTrackConfig: AudioSink.AudioTrackConfig) = inner.onAudioTrackInitialized(audioTrackConfig)
        override fun onAudioTrackReleased(audioTrackConfig: AudioSink.AudioTrackConfig) = inner.onAudioTrackReleased(audioTrackConfig)
        override fun onSilenceSkipped() = inner.onSilenceSkipped()
        override fun onAudioSessionIdChanged(audioSessionId: Int) = inner.onAudioSessionIdChanged(audioSessionId)
    }
}
