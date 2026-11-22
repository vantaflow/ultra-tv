package com.ultratv.tv.nativeapp.ui.player.engine

/**
 * Direct figé SANS erreur ni chargement : le moteur se dit en lecture, mais le décodeur vidéo ne sort plus aucune image
 * (son seul, image gelée), ou — sans images contrôlables — la position n'avance plus. Le garde-fou « chargement de plus
 * de 12 s » ne le voit pas : le moteur reste en état « prêt ». Signalé sur Reddit (gel au bout de 5 à 10 min, sur toutes
 * les chaînes, avec Media3).
 *
 * La position seule n'est PAS un signal fiable : quand le flux du fournisseur fait repartir ses horodatages en arrière,
 * Media3 garde l'ancienne position tant que le nouveau minutage ne l'a pas rattrapée (10 à 25 s) alors que la lecture
 * continue. La prendre pour un gel relançait la chaîne, qui repartait en arrière, d'où la boucle répétition / gel /
 * reconnexion (box, 1.3.73-rc7). Une position immobile ne compte donc que si les images ne sont pas contrôlables, qu'aucun
 * octet n'arrive et qu'aucun saut d'horodatage n'a été signalé depuis [limitMs].
 *
 * Logique pure, testée : on lui donne un relevé toutes les ~2 s ; vrai = reconnecter.
 */
class FrozenLiveDetector(private val limitMs: Long = 10_000L) {
    private var lastPos = Long.MIN_VALUE
    private var lastFrames = Long.MIN_VALUE
    private var lastBytes = Long.MIN_VALUE
    private var lastJumps = Long.MIN_VALUE
    private var posSince = 0L
    private var framesSince = 0L
    private var bytesSince = 0L
    private var jumpAt = Long.MIN_VALUE

    /** Ce qui a figé lors du dernier « vrai » (« frames » ou « position »), pour le journal (jamais d'adresse). */
    var lastReason: String = ""
        private set

    /** Position immobile ignorée au dernier relevé (images, octets ou saut d'horodatage) : pour un journal unique. */
    var positionStallIgnored: Boolean = false
        private set

    /**
     * [playing] : le moteur dit lire (pas en pause, pas en chargement). [frames] : images vidéo sorties du décodeur (rendues,
     * sautées ou perdues), -1 si le moteur ne l'expose pas. [checkFrames] : faux sans piste vidéo ou sans surface affichée.
     * [bytes] : octets reçus du réseau depuis l'ouverture (-1 = inconnu). [jumps] : sauts d'horodatage signalés par le moteur.
     */
    fun feed(nowMs: Long, playing: Boolean, positionMs: Long, frames: Long, checkFrames: Boolean, bytes: Long = -1L, jumps: Long = 0L): Boolean {
        if (!playing) { reset(); return false }
        if (positionMs != lastPos) { lastPos = positionMs; posSince = nowMs }
        val framesKnown = checkFrames && frames >= 0
        if (!framesKnown) { lastFrames = Long.MIN_VALUE; framesSince = nowMs }
        else if (frames != lastFrames) { lastFrames = frames; framesSince = nowMs }
        if (bytes < 0) { lastBytes = Long.MIN_VALUE; bytesSince = 0L }
        else if (bytes != lastBytes) { lastBytes = bytes; bytesSince = nowMs }
        if (lastJumps != Long.MIN_VALUE && jumps != lastJumps) jumpAt = nowMs
        lastJumps = jumps

        val framesStalled = framesKnown && nowMs - framesSince >= limitMs
        val positionStalled = nowMs - posSince >= limitMs
        val bytesMoving = bytes >= 0 && nowMs - bytesSince < limitMs
        val recentJump = jumpAt != Long.MIN_VALUE && nowMs - jumpAt < limitMs
        val framesMoving = framesKnown && !framesStalled
        val positionFrozen = positionStalled && !framesMoving && !bytesMoving && !recentJump
        positionStallIgnored = positionStalled && !positionFrozen

        val frozen = framesStalled || positionFrozen
        if (frozen) { lastReason = if (framesStalled) "frames" else "position"; reset() }
        return frozen
    }

    /** Ce qui a figé, pour le journal (jamais d'adresse). */
    fun reason(@Suppress("UNUSED_PARAMETER") nowMs: Long): String = lastReason

    fun reset() {
        lastPos = Long.MIN_VALUE; lastFrames = Long.MIN_VALUE; lastBytes = Long.MIN_VALUE; lastJumps = Long.MIN_VALUE
        posSince = 0L; framesSince = 0L; bytesSince = 0L; jumpAt = Long.MIN_VALUE; positionStallIgnored = false
    }
}
