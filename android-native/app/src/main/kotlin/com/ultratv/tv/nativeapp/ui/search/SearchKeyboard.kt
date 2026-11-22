package com.ultratv.tv.nativeapp.ui.search

/** Disposition de la grille de saisie intégrée (TV). */
enum class KeyLayout { LATIN, ARABIC }

/**
 * Grille de saisie de la recherche TV (#247) : latin ⇄ arabe, choix mémorisé (arabe par défaut quand l'appli est en
 * arabe), et filtrage du texte reçu d'un clavier ou de la télécommande. Logique pure, sans interface.
 */
object SearchKeyboard {
    const val COLUMNS = 6
    const val LATIN_KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    /** 28 lettres de base + أ ة ى ء (graphies courantes des titres), puis chiffres latins : 7 rangées de 6. */
    const val ARABIC_KEYS = "اأبتةثجحخدذرزسشصضطظعغفقكلمنهوىيء0123456789"

    fun keys(layout: KeyLayout): String = if (layout == KeyLayout.ARABIC) ARABIC_KEYS else LATIN_KEYS

    fun rows(layout: KeyLayout): List<List<Char>> = keys(layout).toList().chunked(COLUMNS)

    /** Choix mémorisé s'il existe, sinon arabe quand la langue de l'appli est l'arabe. */
    fun initial(stored: String?, appArabic: Boolean): KeyLayout =
        KeyLayout.entries.firstOrNull { it.name == stored } ?: if (appArabic) KeyLayout.ARABIC else KeyLayout.LATIN

    fun toggle(layout: KeyLayout): KeyLayout = if (layout == KeyLayout.ARABIC) KeyLayout.LATIN else KeyLayout.ARABIC

    /** Libellé de la touche de bascule : l'écriture vers laquelle elle bascule (identique dans toutes les langues). */
    fun toggleLabel(layout: KeyLayout): String = if (layout == KeyLayout.ARABIC) "ABC" else "عربي"

    /** Caractère d'une touche de la grille tel qu'ajouté à la saisie (minuscules ; l'arabe n'a pas de casse). */
    fun typed(key: Char): Char = key.lowercaseChar()

    /**
     * Texte reçu d'un clavier physique ou injecté par la télécommande (toutes écritures) : lettres, chiffres et espaces
     * gardés, en minuscules ; le reste (contrôles, ponctuation) ignoré.
     */
    fun accept(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetterOrDigit(cp) || cp == ' '.code) sb.appendCodePoint(Character.toLowerCase(cp))
            i += Character.charCount(cp)
        }
        return sb.toString()
    }
}
