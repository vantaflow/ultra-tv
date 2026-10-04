package com.ultratv.tv.nativeapp.ui.player.subtitles

/** Logique pure du panneau : rotation d'une valeur, liste ordonnée de langues. */
object SubtitleLogic {
    fun <T> cycle(values: List<T>, current: T, step: Int): T {
        val i = values.indexOf(current).coerceAtLeast(0)
        return values[((i + step) % values.size + values.size) % values.size]
    }

    /** Ajoute en fin de liste ou retire : l'ordre de sélection est l'ordre de préférence. */
    fun toggleLanguage(list: List<String>, code: String): List<String> = if (code in list) list - code else list + code

    /** Décalage : pas de 100 ms, borné à ±10 s. */
    fun stepDelay(delayMs: Int, step: Int): Int = (delayMs + step * 100).coerceIn(-10_000, 10_000)

    fun delayLabel(ms: Int): String = (if (ms >= 0) "+" else "−") + "%d,%d s".format(kotlin.math.abs(ms) / 1000, kotlin.math.abs(ms) % 1000 / 100)

    val LANGUAGES = listOf("fr", "en", "ar", "es", "de", "it", "pt", "nl", "tr")
}
