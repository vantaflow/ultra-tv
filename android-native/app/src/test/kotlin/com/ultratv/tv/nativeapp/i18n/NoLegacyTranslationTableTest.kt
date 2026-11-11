package com.ultratv.tv.nativeapp.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde-fou : l'ancien système `t("en", "fr", "es", "ar")` (textes littéraux côte à côte dans le code) ne doit pas
 * réapparaître. Les textes de l'interface vont dans res/values*, lus via I18n (voir tools/i18n/README ou I18n.kt).
 */
class NoLegacyTranslationTableTest {
    private fun mainSources(): File = listOf("src/main/kotlin", "app/src/main/kotlin").map(::File).first { it.isDirectory }

    /** t( suivi de 2 à 4 littéraux de chaîne séparés par des virgules (éventuellement sur plusieurs lignes). */
    private val legacyCall = Regex("(?<![\\w])t\\(\\s*\"(?:[^\"\\\\]|\\\\.)*\"\\s*,\\s*\"", RegexOption.DOT_MATCHES_ALL)
    private val legacyDef = Regex("fun\\s+(?:\\w+\\.)?t\\(\\s*en\\s*:\\s*String")

    @Test fun aucunAppelT_aTextesLitteraux() {
        val offenders = mainSources().walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            (legacyCall.findAll(text) + legacyDef.findAll(text)).map { m ->
                "${f.path}:${text.substring(0, m.range.first).count { it == '\n' } + 1}"
            }
        }.toList()
        assertTrue(
            "Ancien système t(\"…\", \"…\") détecté (utiliser res/values*/strings.xml) :\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
