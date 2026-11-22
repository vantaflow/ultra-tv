package com.ultratv.tv.nativeapp.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #247 : grille latin ⇄ arabe de la recherche TV et texte reçu d'un clavier ou de la télécommande. */
class SearchKeyboardTest {
    @Test fun grilles_rangeesCompletes_de6() {
        val latin = SearchKeyboard.rows(KeyLayout.LATIN)
        assertEquals(6, latin.size)
        assertEquals("ABCDEF", latin.first().joinToString(""))
        val arabic = SearchKeyboard.rows(KeyLayout.ARABIC)
        assertEquals(7, arabic.size)
        assertTrue(arabic.all { it.size == SearchKeyboard.COLUMNS })
        // Lettres de base toutes présentes, une seule fois, et chiffres latins.
        val keys = SearchKeyboard.ARABIC_KEYS
        for (c in "ابتثجحخدذرزسشصضطظعغفقكلمنهوي") assertEquals("lettre $c", 1, keys.count { it == c })
        assertEquals(keys.length, keys.toSet().size)
        assertTrue(keys.endsWith("0123456789"))
    }

    @Test fun dispositionInitiale_memoriseeSinonLangueDeLAppli() {
        assertEquals(KeyLayout.ARABIC, SearchKeyboard.initial(null, appArabic = true))
        assertEquals(KeyLayout.LATIN, SearchKeyboard.initial(null, appArabic = false))
        assertEquals(KeyLayout.LATIN, SearchKeyboard.initial("LATIN", appArabic = true))
        assertEquals(KeyLayout.ARABIC, SearchKeyboard.initial("ARABIC", appArabic = false))
        assertEquals(KeyLayout.LATIN, SearchKeyboard.initial("???", appArabic = false))
    }

    @Test fun bascule_etLibelle() {
        assertEquals(KeyLayout.ARABIC, SearchKeyboard.toggle(KeyLayout.LATIN))
        assertEquals(KeyLayout.LATIN, SearchKeyboard.toggle(KeyLayout.ARABIC))
        assertEquals("عربي", SearchKeyboard.toggleLabel(KeyLayout.LATIN))
        assertEquals("ABC", SearchKeyboard.toggleLabel(KeyLayout.ARABIC))
    }

    @Test fun texteRecu_toutesEcritures_ponctuationIgnoree() {
        assertEquals("مسلسل الهيبة", SearchKeyboard.accept("مسلسل الهيبة"))
        assertEquals("breaking bad", SearchKeyboard.accept("Breaking Bad!"))
        assertEquals("одесса 2", SearchKeyboard.accept("Одесса 2?"))
        assertEquals('ب', SearchKeyboard.typed('ب'))
        assertEquals('a', SearchKeyboard.typed('A'))
    }
}
