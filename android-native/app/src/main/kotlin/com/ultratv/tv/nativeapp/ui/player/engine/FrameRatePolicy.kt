package com.ultratv.tv.nativeapp.ui.player.engine

import kotlin.math.abs
import kotlin.math.round

/** Un mode d'affichage (résolution + fréquence) tel que l'écran l'annonce. */
data class DisplayModeInfo(val id: Int, val refreshHz: Float, val width: Int, val height: Int)

/**
 * Changement automatique de fréquence d'écran (AFR) : règles pures, testables sans écran.
 * Principe : ne jamais basculer sur une fréquence « devinée » — on attend le format réellement décodé (ou une mesure sur
 * les images rendues), on arrondit aux cadences usuelles, et sans valeur fiable on ne touche à rien.
 */
object FrameRatePolicy {
    /** Cadences usuelles (i/s) : 23,976 / 24 (cinéma), 25 / 50 (Europe), 29,97 / 30 / 59,94 / 60 (NTSC). */
    val STANDARD = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)

    /** Écart maximal (Hz) entre une cadence annoncée / mesurée et la cadence usuelle retenue ; au-delà : inconnue. */
    private const val SNAP_TOLERANCE_HZ = 0.12f

    /** Écart relatif toléré entre la fréquence de l'écran et un multiple entier de la cadence (0,5 % : 24 i/s sur 23,976 Hz passe). */
    private const val MATCH_TOLERANCE = 0.005f

    /** Cadence usuelle la plus proche, ou null si la valeur est absente, aberrante ou trop éloignée de toute cadence usuelle. */
    fun snap(fps: Float?): Float? {
        if (fps == null || !fps.isFinite() || fps <= 0f) return null
        val best = STANDARD.minByOrNull { abs(it - fps) } ?: return null
        return if (abs(best - fps) <= SNAP_TOLERANCE_HZ) best else null
    }

    /** Multiple entier (≥ 1) de [fps] le plus proche de [refreshHz] : 25 i/s sur 50 Hz → 2. */
    private fun multiple(refreshHz: Float, fps: Float): Int = round(refreshHz / fps).toInt().coerceAtLeast(1)

    /** Vrai si [refreshHz] affiche [fps] sans saccade (multiple entier, à 0,5 % près) : 50 Hz pour 25 i/s, 60 Hz pour 29,97 i/s. */
    fun compatible(refreshHz: Float, fps: Float): Boolean {
        if (refreshHz <= 0f || fps <= 0f) return false
        val target = fps * multiple(refreshHz, fps)
        return abs(refreshHz - target) <= target * MATCH_TOLERANCE
    }

    /**
     * Mode d'écran à demander pour [fps] (déjà arrondie par [snap]) ; null = ne rien changer : cadence inconnue,
     * fréquence actuelle déjà compatible, ou aucun mode compatible. Seuls les modes de la MÊME résolution que le mode
     * actuel sont retenus (changer de résolution coûte une renégociation HDMI de plusieurs secondes). Le plus petit multiple
     * l'emporte (24 Hz avant 48 Hz), puis l'écart le plus faible (23,976 Hz pour 23,976 i/s plutôt que 24 Hz).
     */
    fun chooseMode(fps: Float?, current: DisplayModeInfo, modes: List<DisplayModeInfo>): Int? {
        if (fps == null || fps <= 0f) return null
        if (compatible(current.refreshHz, fps)) return null
        return modes.filter { it.width == current.width && it.height == current.height && compatible(it.refreshHz, fps) }
            .minWithOrNull(compareBy<DisplayModeInfo>({ multiple(it.refreshHz, fps) }, { abs(it.refreshHz - fps * multiple(it.refreshHz, fps)) }))
            ?.id
    }
}

/**
 * Mesure la cadence réelle d'après les horodatages des images rendues (médiane des intervalles : robuste aux trous et aux
 * images en double). Sert quand le flux ne déclare aucune cadence (MPEG-TS), ou la déclare à tort.
 * Appelée depuis le fil de lecture : méthodes synchronisées.
 */
class FrameRateEstimator(private val samplesNeeded: Int = 48) {
    private val deltas = ArrayList<Long>()
    private var last = NONE

    /** Une image rendue, d'horodatage [ptsUs]. Un saut en arrière ou un trou (> 250 ms) repart d'une mesure neuve. */
    @Synchronized fun add(ptsUs: Long) {
        val prev = last
        last = ptsUs
        if (prev == NONE) return
        val d = ptsUs - prev
        if (d <= 0L || d > MAX_GAP_US) { deltas.clear(); return }
        if (deltas.size < samplesNeeded) deltas.add(d)
    }

    /** Cadence mesurée (i/s) une fois assez d'images vues ; null avant. À arrondir avec [FrameRatePolicy.snap]. */
    @Synchronized fun estimate(): Float? {
        if (deltas.size < samplesNeeded) return null
        val median = deltas.sorted()[deltas.size / 2]
        return if (median > 0) 1_000_000f / median else null
    }

    @Synchronized fun reset() { deltas.clear(); last = NONE }

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val MAX_GAP_US = 250_000L
    }
}

/**
 * Fréquence de l'écran adaptée à la vidéo SEULEMENT dans le lecteur plein écran, et rendue au système en le quittant.
 * Avant : l'aperçu du Direct changeait déjà le mode, et rien ne le rendait — après une vidéo à 25 i/s, un écran 120 Hz
 * restait en 50 Hz dans toute l'appli (défilement saccadé, « tout est devenu lent »).
 */
object DisplayModeGate {
    @Volatile var fullscreen = false
        private set
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    /** Entrée dans le lecteur plein écran : les moteurs en cours réévaluent la cadence (aperçu devenu plein écran). */
    fun enter() { fullscreen = true; listeners.forEach { it() } }

    /** Sortie du lecteur : le mode d'affichage revient au choix du système (fréquence native de l'écran). */
    fun exit(ctx: android.content.Context) {
        fullscreen = false
        var c: android.content.Context? = ctx
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        val w = (c as? android.app.Activity)?.window ?: return
        val lp = w.attributes
        if (lp.preferredDisplayModeId != 0) { lp.preferredDisplayModeId = 0; w.attributes = lp }
    }
}
