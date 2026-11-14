package com.ultratv.tv.nativeapp.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

/** Box en arabe, appli en français : les chiffres formatés par le code doivent être latins. */
class DefaultLocaleTest {
    private val saved = Locale.getDefault()
    @After fun restore() { Locale.setDefault(saved) }

    @Test fun appliEnFrancais_surBoxEnArabe_chiffresLatins() {
        Locale.setDefault(Locale.forLanguageTag("ar-MA"))
        I18n.applyDefaultLocale(AppLang.French)
        assertEquals("1 698".replace(" ", " "), NumberFormat.getIntegerInstance().format(1698).replace(' ', ' '))
        assertEquals("42", String.format("%d", 42))
    }

    @Test fun appliEnArabe_chiffresLatinsPourLeCode() {
        I18n.applyDefaultLocale(AppLang.Arabic)
        assertEquals("ar", Locale.getDefault().language)
        // Adresses / requêtes construites par le code : jamais de chiffres arabo-indiens (synchro bloquée sinon).
        assertEquals("category_id=12&page=3", String.format("category_id=%d&page=%d", 12, 3))
        assertEquals("1698", NumberFormat.getIntegerInstance().apply { isGroupingUsed = false }.format(1698))
    }

    @Test fun appareilEnArabe_langueSysteme_chiffresLatins() {
        Locale.setDefault(Locale.forLanguageTag("ar-MA"))
        I18n.applyDefaultLocale(AppLang.French)
        I18n.applyDefaultLocale(AppLang.System)
        assertEquals("7", String.format("%d", 7))
    }
}
