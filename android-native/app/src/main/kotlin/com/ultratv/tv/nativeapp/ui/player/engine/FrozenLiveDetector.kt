package com.ultratv.tv.nativeapp.ui.player.engine

/**
 * Direct figé SANS erreur ni chargement : le moteur se dit en lecture, mais la position n'avance plus, ou le décodeur vidéo
 * ne sort plus aucune image (son seul, image gelée). Le garde-fou « chargement de plus de 12 s » ne le voit pas : le moteur
 * reste en état « prêt ». Signalé sur Reddit (gel au bout de 5 à 10 min, sur toutes les chaînes, avec Media3).
 * Logique pure, testée : on lui donne un relevé toutes les ~2 s ; vrai = reconnecter.
 */
class FrozenLiveDetector(private val limitMs: Long = 10_000L) {
    private var lastPos = Long.MIN_VALUE
    private var lastFrames = Long.MIN_VALUE
    private var posSince = 0L
    private var framesSince = 0L

    /**
     * [playing] : le moteur dit lire (pas en pause, pas en chargement). [frames] : images vidéo sorties du décodeur (rendues,
     * sautées ou perdues), -1 si le moteur ne l'expose pas. [checkFrames] : faux sans piste vidéo ou sans surface affichée.
     */
    fun feed(nowMs: Long, playing: Boolean, positionMs: Long, frames: Long, checkFrames: Boolean): Boolean {
        if (!playing) { reset(); return false }
        if (positionMs != lastPos) { lastPos = positionMs; posSince = nowMs }
        if (!checkFrames || frames < 0) { lastFrames = Long.MIN_VALUE; framesSince = nowMs }
        else if (frames != lastFrames) { lastFrames = frames; framesSince = nowMs }
        val frozen = nowMs - posSince >= limitMs || nowMs - framesSince >= limitMs
        if (frozen) reset()
        return frozen
    }

    /** Ce qui a figé, pour le journal (jamais d'adresse). */
    fun reason(nowMs: Long): String = if (nowMs - posSince >= limitMs) "position" else "frames"

    fun reset() { lastPos = Long.MIN_VALUE; lastFrames = Long.MIN_VALUE; posSince = 0L; framesSince = 0L }
}
