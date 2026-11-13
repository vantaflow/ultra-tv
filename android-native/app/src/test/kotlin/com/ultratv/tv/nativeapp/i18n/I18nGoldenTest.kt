package com.ultratv.tv.nativeapp.i18n

import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.ui.mobile.MobileStrings
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Non-régression de la migration vers `strings.xml` : les textes recalculés via les porteurs (DesignStrings,
 * Strings, MobileStrings…) doivent être identiques, au caractère près, à la photo de référence prise sur les anciennes
 * tables Kotlin (src/test/resources/i18n-golden/<langue>.json). Seuls les écarts listés dans
 * `accepted-deviations.txt` (ligne « langue|clé|raison ») sont tolérés.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class I18nGoldenTest {
    private lateinit var saved: Locale

    @Before fun setUp() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.ROOT)
        I18n.init(ApplicationProvider.getApplicationContext())
    }

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("i18n-golden/$name")!!.readBytes().toString(Charsets.UTF_8)

    private fun accepted(): Map<String, String> =
        resource("accepted-deviations.txt").lines().filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { l -> val p = l.split("|", limit = 3); "${p[0]}|${p[1]}" to p[2] }

    @Test fun textes_identiquesAuGolden_dansLes4Langues() {
        try {
            val ok = accepted()
            val seen = mutableSetOf<String>()
            val problems = mutableListOf<String>()
            for (lang in listOf(AppLang.English, AppLang.French, AppLang.Spanish, AppLang.Arabic)) {
                val golden = GoldenDump.parseJson(resource("${lang.code}.json"))
                val now = GoldenDump.dump(lang, stringsOf(lang), MobileStrings.forLang(lang, lang.code))
                assertTrue("Reflexion vide pour ${lang.code}", now.size > 1000)
                for ((key, expected) in golden) {
                    val actual = now[key]
                    if (actual == expected) continue
                    val id = "${lang.code}|$key"
                    if (id in ok) { seen += id; continue }
                    problems += "$id\n    attendu : « $expected »\n    obtenu  : « $actual »"
                }
                // Le polonais (langue communautaire) est postérieur au golden : seul son nom dans le sélecteur est nouveau.
                for (key in now.keys - golden.keys) if (key != "DesignStrings.langName(Polish)" && key != "DesignStrings.langName(PortugueseBr)") problems += "${lang.code}|$key : entrée absente du golden"
            }
            val stale = ok.keys - seen
            assertTrue("Écarts acceptés devenus inutiles :\n" + stale.joinToString("\n"), stale.isEmpty())
            assertTrue("${problems.size} écart(s) avec le golden :\n" + problems.joinToString("\n"), problems.isEmpty())
            assertTrue("Membres ignorés par le golden : ${GoldenDump.skipped}", GoldenDump.skipped.isEmpty())
        } finally {
            Locale.setDefault(saved)
        }
    }
}
