package com.ultratv.tv.nativeapp.ui.player.engine

/** Une combinaison moteur + décodage. */
data class Combo(val engine: EngineKind, val decoder: DecoderMode) {
    fun encode() = "${engine.name}:${decoder.name}"
    companion object {
        fun decode(s: String?): Combo? {
            val (e, d) = s?.split(':')?.takeIf { it.size == 2 } ?: return null
            return runCatching { Combo(EngineKind.valueOf(e), DecoderMode.valueOf(d)) }.getOrNull()
        }
    }
}

/**
 * Choix du moteur et logique de repli (pur, testable).
 *  - Préférence UTILISATEUR (Auto / ExoPlayer / VLC) + décodage (Auto / Matériel / Logiciel).
 *  - Chaîne déjà mémorisée avec une combinaison qui marche : on la reprend directement.
 *  - Sinon ordre de repli : ExoPlayer(auto) → ExoPlayer(logiciel) → VLC(auto) → VLC(logiciel).
 *    Un moteur imposé par l'utilisateur n'est jamais quitté (seul le décodage change).
 */
object PlaybackPlanner {
    fun initial(engine: EngineChoice, decoder: DecoderMode, remembered: Combo?): Combo {
        if (remembered != null && allowed(engine, remembered)) return remembered
        return when (engine) {
            EngineChoice.VLC -> Combo(EngineKind.VLC, decoder)
            else -> Combo(EngineKind.EXO, decoder)
        }
    }

    private fun allowed(choice: EngineChoice, c: Combo) = when (choice) {
        EngineChoice.AUTO -> true
        EngineChoice.EXO -> c.engine == EngineKind.EXO
        EngineChoice.VLC -> c.engine == EngineKind.VLC
    }

    /** Prochaine combinaison à essayer après un échec, ou null s'il n'y en a plus. */
    fun next(choice: EngineChoice, tried: Set<Combo>): Combo? {
        val order = listOf(
            Combo(EngineKind.EXO, DecoderMode.AUTO), Combo(EngineKind.EXO, DecoderMode.SOFTWARE),
            Combo(EngineKind.VLC, DecoderMode.AUTO), Combo(EngineKind.VLC, DecoderMode.SOFTWARE),
        )
        return order.firstOrNull { allowed(choice, it) && it !in tried }
    }

    /** Un repli ne sert à rien si le problème n'est ni le format ni le décodage. */
    fun shouldFallBack(kind: PlayErrorKind) = when (kind) {
        PlayErrorKind.FORMAT, PlayErrorKind.DECODER, PlayErrorKind.NO_PICTURE, PlayErrorKind.UNKNOWN -> true
        else -> false
    }

    /** 403 / réseau : une seule nouvelle tentative automatique avec la MÊME combinaison, après un court délai. */
    fun shouldRetrySame(kind: PlayErrorKind) = kind == PlayErrorKind.REFUSED || kind == PlayErrorKind.NETWORK || kind == PlayErrorKind.BEHIND_LIVE

    /** Classification d'une erreur Media3 (code d'erreur + statut HTTP éventuel). */
    fun classifyExo(errorCode: Int, httpStatus: Int?): PlayErrorKind = when {
        errorCode == 1002 -> PlayErrorKind.BEHIND_LIVE                       // ERROR_CODE_BEHIND_LIVE_WINDOW
        errorCode == 2004 -> when (httpStatus) {                             // ERROR_CODE_IO_BAD_HTTP_STATUS
            401, 403 -> PlayErrorKind.REFUSED
            404, 410 -> PlayErrorKind.NOT_FOUND
            else -> PlayErrorKind.NETWORK
        }
        errorCode in 2000..2008 -> PlayErrorKind.NETWORK                     // IO_*
        errorCode in 3001..3004 -> PlayErrorKind.FORMAT                      // PARSING_*
        errorCode == 4005 -> PlayErrorKind.FORMAT                            // DECODING_FORMAT_UNSUPPORTED
        errorCode in 4001..4006 || errorCode in 5001..5003 -> PlayErrorKind.DECODER   // DECODER_*, AUDIO_TRACK_*
        else -> PlayErrorKind.UNKNOWN
    }
}
