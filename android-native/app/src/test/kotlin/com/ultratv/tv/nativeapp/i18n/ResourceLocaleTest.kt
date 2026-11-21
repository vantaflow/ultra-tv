package com.ultratv.tv.nativeapp.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/** Langue passée aux ressources : chiffres latins imposés seulement là où le système comprend l'extension (Android 9+). */
class ResourceLocaleTest {
    @Test fun android9EtPlus_chiffresLatinsImposes() {
        for (code in listOf("ar", "el", "th", "fr", "pt-BR")) {
            val l = I18n.resourceLocale(code, sdkInt = 28)
            assertEquals(code, "latn", l.getUnicodeLocaleType("nu"))
            assertEquals(code, java.util.Locale.forLanguageTag(code).language, l.language)
        }
        assertEquals("ar-u-nu-latn", I18n.resourceLocale("ar", sdkInt = 34).toLanguageTag())
    }

    @Test fun android7Et8_langueNue_sansExtension() {
        for (sdk in listOf(25, 26, 27)) for (code in listOf("ar", "el", "th", "fr", "pt-BR")) {
            assertEquals("$code sdk $sdk", code, I18n.resourceLocale(code, sdkInt = sdk).toLanguageTag())
        }
    }
}
