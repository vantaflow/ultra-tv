package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.i18n.AppLang
import org.junit.Assert.assertTrue
import org.junit.Test

/** Même garde-fou que TranslationsCompleteTest, pour la table dédiée [DnsStrings]. */
class DnsStringsTest {
    private fun all(l: AppLang): Map<String, String> {
        val s = DnsStrings(l)
        return mapOf(
            "group" to s.group, "server" to s.server, "system" to s.system, "doh" to s.doh, "custom" to s.custom,
            "dohProvider" to s.dohProvider, "dohUrl" to s.dohUrl, "dohUrlSub" to s.dohUrlSub, "invalidDohUrl" to s.invalidDohUrl,
            "servers" to s.servers, "serversSub" to s.serversSub, "primary" to s.primary, "secondary" to s.secondary,
            "invalidIp" to s.invalidIp, "notSet" to s.notSet, "test" to s.test, "testHint" to s.testHint, "testing" to s.testing,
            "testFallback" to s.testFallback("1.2.3.4", "timeout"), "testFailed" to s.testFailed, "vlcNote" to s.vlcNote, "scopeNote" to s.scopeNote,
        )
    }

    @Test fun enFrEsAr_traduits() {
        val en = all(AppLang.English)
        val problems = mutableListOf<String>()
        for (lang in listOf(AppLang.French, AppLang.Spanish, AppLang.Arabic)) {
            for ((k, v) in all(lang)) {
                if (v.isBlank()) problems += "${lang.code}/$k vide"
                if (v == en[k]) problems += "${lang.code}/$k identique à l'anglais"
                if (lang == AppLang.Arabic && !Regex("[\\u0600-\\u06FF]").containsMatchIn(v)) problems += "ar/$k sans arabe"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
