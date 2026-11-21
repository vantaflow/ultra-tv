package com.ultratv.tv.nativeapp.i18n

import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.ui.detail.airDayLabel
import com.ultratv.tv.nativeapp.ui.mobile.MobileStrings
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
 * Retour propriétaire : « En arabe on doit avoir les mêmes 1 2 3… pas les chiffres arabes ». AUCUN texte affiché ne doit
 * contenir de chiffre arabo-indien (U+0660-0669) ni persan (U+06F0-06F9), quel que soit le JDK (sous JDK 17,
 * `String.format(Locale("ar"), "%d", 22)` donne « ٢٢ », sous un JDK récent « 22 ») et quelle que soit la version d'Android.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArabicDigitsTest {
    private lateinit var saved: Locale
    private val nowMs = 1_763_800_000_000L // 2025-11-22

    @Before fun setUp() {
        saved = Locale.getDefault()
        // Défaut = arabe SANS nu-latn : le pire cas (les formatages ne doivent pas en dépendre).
        Locale.setDefault(Locale.forLanguageTag("ar"))
        I18n.init(ApplicationProvider.getApplicationContext())
    }
    @After fun restore() { Locale.setDefault(saved) }

    private fun bad(s: String) = s.filter { it in '٠'..'٩' || it in '۰'..'۹' }
    private fun assertLatin(label: String, s: String) = assertEquals("$label → « $s »", "", bad(s))

    @Test fun datesDuModeSimpleEtDeSport_enChiffresLatins() {
        val D = DesignStrings(AppLang.Arabic)
        assertLatin("accueil simple", DisplayFormats.simpleHomeDate(AppLang.Arabic, nowMs))
        assertTrue(DisplayFormats.simpleHomeDate(AppLang.Arabic, nowMs).any { it in '0'..'9' })
        val loc = DisplayFormats.sportLocale(AppLang.Arabic)
        assertLatin("sport début de match", DisplayFormats.sportStart(loc, nowMs))
        assertLatin("sport jour court", com.ultratv.tv.nativeapp.ui.sport.shortDay(D, nowMs + 5 * 86_400_000L, nowMs, loc))
        assertLatin("jour de diffusion proche", airDayLabel(D, nowMs + 4 * 86_400_000L, nowMs))
        assertLatin("jour de diffusion lointain", airDayLabel(D, nowMs + 40 * 86_400_000L, nowMs))
        // Locale d'affichage : jamais de chiffres propres, même pour une langue qui en a.
        assertEquals("latn", DisplayLocale.of(AppLang.Arabic).getUnicodeLocaleType("nu"))
        assertEquals("latn", DisplayLocale.of("fa").getUnicodeLocaleType("nu"))
        for (lang in listOf(AppLang.Arabic, AppLang.Thai, AppLang.French)) assertEquals("0", java.text.NumberFormat.getIntegerInstance(DisplayLocale.of(lang)).format(0))
        assertEquals("22", String.format(DisplayLocale.of(AppLang.Arabic), "%d", 22))
        assertEquals("latn", D.locale.getUnicodeLocaleType("nu"))
    }

    @Test fun nombresFormates_parLesPorteursDeTextes() {
        val D = DesignStrings(AppLang.Arabic)
        assertLatin("channels", D.channels(1698))
        assertLatin("moviesOf", D.moviesOf(22))
        assertLatin("seriesOf", D.seriesOf(2))
        assertLatin("channelCount", D.channelCount(1234))
        assertLatin("epg", com.ultratv.tv.nativeapp.i18n.epgProgressText(D, 12_000_000, 45_000_000, 20_000))
        assertLatin("bottomTabSlot", D.bottomTabSlot(3))
    }

    /** Android 7 et 8 : ressources en langue NUE (« ar »), sans nu-latn — les textes à %d ne doivent pas en dépendre. */
    @Test fun textesANombre_ressourcesSansNuLatn_sousAndroid7Et8() {
        for (sdk in listOf(25, 26, 27)) {
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val cfg = Configuration(ctx.resources.configuration)
            cfg.setLocale(I18n.resourceLocale("ar", sdkInt = sdk))
            assertEquals("ar (sdk $sdk, ressources sans extension)", "ar", cfg.locales[0].toLanguageTag())
            val r = ctx.createConfigurationContext(cfg).resources
            val display = DisplayLocale.of(AppLang.Arabic)
            for (id in listOf(R.string.st_minutes, R.string.st_summary_limit, R.string.ds_bottom_tab_slot, R.string.ds_min_left, R.string.ds_active_disabled)) {
                val raw = r.getString(id)
                val args = Array<Any>(Regex("%\\d?\\$?\\d*d").findAll(raw).count().coerceAtLeast(1)) { 22 }
                assertLatin("sdk $sdk ${r.getResourceEntryName(id)}", I18n.stringIn(r, display, id, args))
            }
            assertLatin("sdk $sdk pluriel", I18n.pluralIn(r, display, R.plurals.ds_channels, 22, arrayOf<Any>("1,698")))
        }
    }

    @Test fun valeursDeFormatage_memeSousLocaleDefautArabeNue() {
        // Les textes passent par I18n (locale d'affichage), pas par la locale par défaut de Java.
        assertLatin("st_minutes", I18n.string(AppLang.Arabic, R.string.st_minutes, 22))
        assertLatin("ds_hour_min_left", I18n.string(AppLang.Arabic, R.string.ds_hour_min_left, 1, 5))
    }

    /** Garde-fou : TOUS les membres de DesignStrings / Strings / MobileStrings & co rendus en arabe. */
    @Test fun garde_fou_aucunChiffreArabo_indienDansAucunTexte() {
        val lang = AppLang.Arabic
        val dump = GoldenDump.dump(lang, stringsOf(lang), MobileStrings.forLang(lang, lang.code))
        assertTrue("Reflexion vide", dump.size > 1000)
        val offenders = dump.filter { (_, v) -> bad(v).isNotEmpty() }.map { (k, v) -> "$k = « $v »" }
        assertTrue("${offenders.size} texte(s) arabe avec chiffres arabo-indiens :\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    /** Les textes écrits à la main en arabe (et le golden) ne contiennent aucun chiffre arabo-indien ni persan. */
    @Test fun valuesAr_etGolden_sansChiffresArabes() {
        val dir = java.io.File("src/main/res/values-ar").takeIf { it.isDirectory } ?: java.io.File("app/src/main/res/values-ar")
        val files = dir.listFiles { f -> f.name.endsWith(".xml") }!!.toList() +
            listOfNotNull(javaClass.classLoader!!.getResource("i18n-golden/ar.json")?.let { java.io.File(it.toURI()) })
        assertTrue(files.isNotEmpty())
        for (f in files) f.readLines(Charsets.UTF_8).forEachIndexed { i, l -> assertLatin("${f.name}:${i + 1}", l) }
    }
}
