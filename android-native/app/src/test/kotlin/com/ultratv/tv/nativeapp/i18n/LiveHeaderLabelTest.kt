package com.ultratv.tv.nativeapp.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.Locale

class LiveHeaderLabelTest {
    private lateinit var saved: Locale
    @Before fun setUp() { saved = Locale.getDefault(); Locale.setDefault(Locale.ROOT) }
    @After fun tearDown() { Locale.setDefault(saved) }

    private val en = DesignStrings(AppLang.English)
    private val fr = DesignStrings(AppLang.French)
    private val es = DesignStrings(AppLang.Spanish)
    private val ar = DesignStrings(AppLang.Arabic)

    @Test fun channelCount_anglais_singulierEtPluriel() {
        assertEquals("0 channels", en.channelCount(0))
        assertEquals("1 channel", en.channelCount(1))
        assertEquals("40 channels", en.channelCount(40))
    }

    @Test fun channelCount_francais_zeroEtUnAuSingulier() {
        assertEquals("0 chaîne", fr.channelCount(0))
        assertEquals("1 chaîne", fr.channelCount(1))
        assertEquals("40 chaînes", fr.channelCount(40))
    }

    @Test fun channelCount_espagnol_singulierEtPluriel() {
        assertEquals("1 canal", es.channelCount(1))
        assertEquals("0 canales", es.channelCount(0))
        assertEquals("40 canales", es.channelCount(40))
    }

    @Test fun channelCount_arabe_singulierDuelPlurielEtAuDela() {
        assertEquals("قناة واحدة", ar.channelCount(1))
        assertEquals("قناتان", ar.channelCount(2))
        assertEquals("3 قنوات", ar.channelCount(3))
        assertEquals("10 قنوات", ar.channelCount(10))
        assertEquals("40 قناة", ar.channelCount(40))
    }

    @Test fun liveHeaderCount_uneSectionOuAucune_sectionsMasquees() {
        assertEquals("40 chaînes", fr.liveHeaderCount(40, 1))
        assertEquals("40 chaînes", fr.liveHeaderCount(40, 0))
        assertEquals("40 channels", en.liveHeaderCount(40, 1))
    }

    @Test fun liveHeaderCount_plusieursSections_sectionsAffichees() {
        assertEquals("40 chaînes · 3 sections", fr.liveHeaderCount(40, 3))
        assertEquals("40 channels · 3 sections", en.liveHeaderCount(40, 3))
        assertEquals("40 canales · 3 secciones", es.liveHeaderCount(40, 3))
        assertEquals("40 قناة · 3 أقسام", ar.liveHeaderCount(40, 3))
        assertEquals("1 canal · 2 secciones", es.liveHeaderCount(1, 2))
        assertEquals("قناتان · قسمان", ar.liveHeaderCount(2, 2))
    }

    @Test fun liveHeaderCount_jamaisLeNomDeCategorie_pasDeMelangeDEcritures() {
        // Le compteur arabe ne contient aucune lettre latine, le compteur français aucune lettre arabe.
        for (n in listOf(1, 2, 7, 40)) for (s in listOf(1, 4)) {
            assertFalse(Regex("[A-Za-z]").containsMatchIn(ar.liveHeaderCount(n, s)))
            assertFalse(Regex("[\\u0600-\\u06FF]").containsMatchIn(fr.liveHeaderCount(n, s)))
        }
    }
}
