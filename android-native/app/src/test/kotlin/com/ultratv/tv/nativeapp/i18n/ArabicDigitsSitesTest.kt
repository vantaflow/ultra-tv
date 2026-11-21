package com.ultratv.tv.nativeapp.i18n

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Sites d'affichage extraits pour être testés, rappel de la locale après un changement de configuration, et formatage
 * protégé des traductions. Locale par défaut forcée à ar-EG SANS nu-latn (remise à zéro du framework).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArabicDigitsSitesTest {
    private lateinit var saved: Locale

    @Before fun setUp() {
        saved = Locale.getDefault()
        I18n.init(ApplicationProvider.getApplicationContext())
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
    }
    @After fun restore() { Locale.setDefault(saved) }

    private fun assertLatin(label: String, s: String?) =
        assertEquals("$label → « $s »", "", s.orEmpty().filter { it.isDigit() && it !in '0'..'9' })

    @Test fun sitesDAffichage_enChiffresLatins() {
        val D = DesignStrings(AppLang.Arabic)
        assertLatin("Reprendre (min)", com.ultratv.tv.nativeapp.ui.home.minutesLeftLabel(D, 45))
        assertLatin("Reprendre (h)", com.ultratv.tv.nativeapp.ui.home.minutesLeftLabel(D, 125))
        assertLatin("lecteur", com.ultratv.tv.nativeapp.ui.player.playerTime(3_922_000))
        assertEquals("1:05:22", com.ultratv.tv.nativeapp.ui.player.playerTime(3_922_000))
        assertLatin("date OSD", com.ultratv.tv.nativeapp.ui.player.osd.osdDate(D.locale, 1_763_800_000_000L))
        assertTrue(com.ultratv.tv.nativeapp.ui.player.osd.osdDate(D.locale, 1_763_800_000_000L).any { it in '0'..'9' })
        assertLatin("première synchro", com.ultratv.tv.nativeapp.ui.sync.etaText(D, 300))
        assertLatin("il y a", com.ultratv.tv.nativeapp.ui.settings.agoText(D, 3 * 3_600_000L))
        assertLatin("catégories", com.ultratv.tv.nativeapp.ui.settings.activeDisabledText(D, 12, 3))
        assertLatin("compteur Direct", com.ultratv.tv.nativeapp.ui.home.formatCount(1234, D.locale))
        assertLatin("statistiques", com.ultratv.tv.nativeapp.ui.player.statDecimal(D.locale, "%.1f Mb/s", 4.25))
    }

    @Test fun valeursPourLaBase_enAscii() {
        assertEquals("S02E05", LatinDigits.episodeTag(2, 5))
        assertEquals("01:32", LatinDigits.normalize("٠١:٣٢"))
        assertEquals("12", LatinDigits.normalize("۱۲"))
        val same = "S02E05"
        assertTrue(LatinDigits.normalize(same) === same)
    }

    @Test fun rappelApresChangementDeConfiguration_retablitLaLocale() {
        I18n.applyDefaultLocale(AppLang.Arabic)
        assertEquals("latn", Locale.getDefault().getUnicodeLocaleType("nu"))
        // Le framework remet la langue du système, sans nu-latn (rotation, manette, HDMI…).
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("٢٢", String.format("%d", 22))
        I18n.onConfigurationChanged(Locale.forLanguageTag("ar-EG"))
        assertEquals("latn", Locale.getDefault().getUnicodeLocaleType("nu"))
        assertEquals("22", String.format("%d", 22))
    }

    @Test fun modeSysteme_suitLaNouvelleLangueDuSysteme() {
        I18n.applyDefaultLocale(AppLang.System)
        Locale.setDefault(Locale.forLanguageTag("ar-SA"))
        I18n.onConfigurationChanged(Locale.forLanguageTag("ar-SA"))
        assertEquals("ar", Locale.getDefault().language)
        assertEquals("SA", Locale.getDefault().country)
        assertEquals("22", String.format("%d", 22))
    }

    @Test fun traductionMalFormee_jamaisDePlantage_texteBrut() {
        // « 50 % م » : un % isolé suivi d'un espace et d'une lettre lèverait UnknownFormatConversionException.
        assertEquals("50 % م %d", I18n.formatSafely(DisplayLocale.of(AppLang.Arabic), "50 % م %d", arrayOf(3)))
        assertEquals("3 د", I18n.formatSafely(DisplayLocale.of(AppLang.Arabic), "%d د", arrayOf(3)))
    }

    @Test fun ressourcesEnLangueNue_formatageParLaLocaleDAffichage() {
        // I18n.string doit formater lui-même (et non Resources.getString(id, args), dont la locale peut être nue).
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cfg = android.content.res.Configuration(ctx.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag("ar-EG"))
        val nue = ctx.createConfigurationContext(cfg).resources
        I18n.resourcesOverride = { nue }
        try {
            assertLatin("ds_min_left", I18n.string(AppLang.Arabic, com.ultratv.tv.nativeapp.R.string.ds_min_left, 22))
            assertLatin("ds_active_disabled", I18n.string(AppLang.Arabic, com.ultratv.tv.nativeapp.R.string.ds_active_disabled, 12, 3))
        } finally { I18n.resourcesOverride = null }
    }
}
