package com.ultratv.tv.nativeapp.ui.player.zap

import com.ultratv.tv.nativeapp.data.db.ChannelEntity

/** Logique pure du zapping : saut des séparateurs, résolution d'un numéro, liste des récentes. */
object ZapLogic {
    /** Une chaîne qu'on peut réellement lire : ni séparateur de section « ##### XXX ##### », ni entrée masquée. */
    fun isZappable(c: ChannelEntity): Boolean = !c.isSeparator && !c.junk

    /** Index suivant / précédent (circulaire) en SAUTANT les séparateurs ; null s'il n'existe aucune autre chaîne lisible. */
    fun step(channels: List<ChannelEntity>, index: Int, forward: Boolean): Int? {
        val n = channels.size
        if (n == 0) return null
        val dir = if (forward) 1 else -1
        var i = index
        repeat(n) {
            i = ((i + dir) % n + n) % n
            if (i == index) return null
            if (isZappable(channels[i])) return i
        }
        return null
    }

    /** Numéro affiché d'une chaîne : séquentiel stable dans la catégorie, à défaut celui du fournisseur. */
    fun numberOf(c: ChannelEntity): Int = if (c.seq > 0) c.seq else c.num

    /** Index de la chaîne portant ce numéro dans la liste en cours (la catégorie parcourue), ou null. */
    fun byNumber(channels: List<ChannelEntity>, number: Int): Int? {
        if (number <= 0) return null
        val i = channels.indexOfFirst { isZappable(it) && numberOf(it) == number }
        return if (i >= 0) i else null
    }

    const val MAX_RECENT = 20

    /** Place [key] en tête, sans doublon, limitée à [max] entrées. */
    fun pushRecent(list: List<String>, key: String, max: Int = MAX_RECENT): List<String> =
        (listOf(key) + list.filter { it != key }).take(max)
}

/**
 * Saisie du numéro de chaîne à la télécommande. Validation automatique après [autoCommitMs] sans nouvelle touche,
 * ou immédiatement avec OK. Pure : l'horloge est fournie par l'appelant.
 */
class NumberEntry(private val maxDigits: Int = 4, val autoCommitMs: Long = 1_500L) {
    private val sb = StringBuilder()
    private var lastKeyMs = 0L

    val text: String get() = sb.toString()
    val isActive: Boolean get() = sb.isNotEmpty()
    val value: Int? get() = sb.toString().toIntOrNull()

    /** Ajoute un chiffre ; au-delà de [maxDigits] on repart de zéro avec ce chiffre. */
    fun append(digit: Int, nowMs: Long) {
        require(digit in 0..9)
        if (sb.length >= maxDigits) sb.clear()
        sb.append(digit)
        lastKeyMs = nowMs
    }

    fun shouldCommit(nowMs: Long): Boolean = isActive && nowMs - lastKeyMs >= autoCommitMs

    fun clear() { sb.clear() }

    companion object {
        /** Chiffre porté par une touche (rangée du haut ou pavé numérique), via KEYCODE_0..9 / KEYCODE_NUMPAD_0..9. */
        fun digitOfKeyCode(keyCode: Int): Int? = when (keyCode) {
            in 7..16 -> keyCode - 7            // KEYCODE_0..KEYCODE_9
            in 144..153 -> keyCode - 144       // KEYCODE_NUMPAD_0..KEYCODE_NUMPAD_9
            else -> null
        }
    }
}
