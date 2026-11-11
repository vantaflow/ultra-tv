package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason
import com.ultratv.tv.nativeapp.data.xmltv.EpgProgress
import com.ultratv.tv.nativeapp.data.xmltv.EpgState
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.epgProgressText
import com.ultratv.tv.nativeapp.i18n.epgReason
import com.ultratv.tv.nativeapp.i18n.guideRetry
import com.ultratv.tv.nativeapp.i18n.guideUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideLoadStateTest {
    @Test fun etatDuGuide_chargementEchecOuRien() {
        val pr = EpgProgress(1_048_576, 4_194_304, 120)
        assertEquals(GuideLoad.Loading(pr), guideLoadState(EpgState.Loading(1, pr), false))
        assertEquals(GuideLoad.Loading(null), guideLoadState(EpgState.Idle, true))
        assertEquals(GuideLoad.Idle, guideLoadState(EpgState.Idle, false))
        assertEquals(GuideLoad.Unavailable(EpgFailReason.TIMEOUT), guideLoadState(EpgState.Failed(1, SyncErrorKind.TIMEOUT, EpgFailReason.TIMEOUT), true))
        assertEquals(25, pr.percent)
        assertNull(EpgProgress(10, -1, 0).percent)
    }

    @Test fun progression_texte() {
        val fr = DesignStrings(AppLang.French)
        assertEquals("1.0 / 4.0 Mo · 120 programmes", epgProgressText(fr, 1_048_576, 4_194_304, 120))
        assertNull(guideProgressLine(fr, GuideLoad.Idle))
        assertTrue(guideProgressLine(fr, GuideLoad.Loading(EpgProgress(1_048_576, -1, 0)))!!.contains("1.0 Mo"))
    }

    @Test fun textes_FR_EN_ES_AR_pourChaqueRaison() {
        val en = DesignStrings(AppLang.English)
        val problems = mutableListOf<String>()
        for (lang in listOf(AppLang.French, AppLang.Spanish, AppLang.Arabic)) {
            val d = DesignStrings(lang)
            for ((k, f) in listOf<Pair<String, (DesignStrings) -> String>>("titre" to { it.guideUnavailable }, "réessayer" to { it.guideRetry }) +
                EpgFailReason.values().map { r -> r.name to { x: DesignStrings -> epgReason(x, r) } }) {
                val v = f(d)
                if (v.isBlank()) problems += "${lang.code}/$k vide"
                if (v == f(en)) problems += "${lang.code}/$k identique à l'anglais"
                if (lang == AppLang.Arabic && !Regex("[\\u0600-\\u06FF]").containsMatchIn(v)) problems += "ar/$k sans arabe"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertEquals("Guide indisponible", DesignStrings(AppLang.French).guideUnavailable)
        assertEquals("Réessayer", DesignStrings(AppLang.French).guideRetry)
    }
}
