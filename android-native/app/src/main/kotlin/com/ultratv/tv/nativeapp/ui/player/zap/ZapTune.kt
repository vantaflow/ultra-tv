package com.ultratv.tv.nativeapp.ui.player.zap

/**
 * Zapping en rafale (touche CH+/− maintenue, appuis rapides) : un appui ISOLÉ ouvre la chaîne tout de suite ; un appui qui
 * suit le précédent de moins de [BURST_MS] attend [SETTLE_MS] avant d'ouvrir le flux — une nouvelle chaîne entre-temps
 * annule l'attente. Sans cela, une touche maintenue ouvrait un flux par répétition (une douzaine en quelques secondes) :
 * certains fournisseurs y voient un abus et bloquent le compte quelques minutes (HTTP 458 / 429).
 */
object ZapTune {
    const val BURST_MS = 1_500L
    const val SETTLE_MS = 1_000L

    @Volatile private var lastZapMs = 0L
    @Volatile private var pending = false

    /** À appeler à chaque changement de chaîne par zapping. */
    fun onZap(nowMs: Long) {
        pending = nowMs - lastZapMs < BURST_MS
        lastZapMs = nowMs
    }

    /** Attente avant d'ouvrir le flux qui vient d'être choisi (0 = tout de suite), consommée. */
    fun takeDelay(): Long = if (pending) { pending = false; SETTLE_MS } else 0L

    /** Logique pure (testée) : attente pour un zap à [nowMs] après un précédent à [previousMs]. */
    fun delayFor(nowMs: Long, previousMs: Long): Long = if (previousMs > 0 && nowMs - previousMs < BURST_MS) SETTLE_MS else 0L
}
