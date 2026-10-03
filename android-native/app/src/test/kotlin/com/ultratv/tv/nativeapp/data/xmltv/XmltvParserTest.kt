package com.ultratv.tv.nativeapp.data.xmltv

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class XmltvParserTest {
    private val parser = XmltvParser(OkHttpClient())

    @Test fun parseXmltvDate_avecDecalage_convertitEnUtc() {
        // 14:00 à +0100 == 13:00 UTC
        assertEquals(1_765_803_600_000L, parseXmltvDate("20251215140000 +0100"))
    }

    @Test fun parseXmltvDate_sansDecalage_estUtc() {
        assertEquals(1_765_807_200_000L, parseXmltvDate("20251215140000"))
    }

    @Test fun parseXmltvDate_invalide_renvoieNull() {
        assertNull(parseXmltvDate(""))
        assertNull(parseXmltvDate("abc"))
    }

    private val xml = """
        <tv>
          <programme start="20251215100000 +0000" stop="20251215110000 +0000" channel="a"><title>Matin</title></programme>
          <programme start="20251215200000 +0000" stop="20251215210000 +0000" channel="a"><title>Soir</title><desc>d</desc></programme>
          <programme start="20251215200000 +0000" stop="20251215210000 +0000" channel="inconnu"><title>X</title></programme>
        </tv>
    """.trimIndent()

    @Test fun parse_ignoreLesChainesNonMappees() {
        val r = parser.parse(xml.byteInputStream(), mapOf("a" to 5L))
        assertEquals(listOf("Matin", "Soir"), r.map { it.title })
        assertEquals(5L, r[0].channelId)
    }

    @Test fun parse_fenetre_ecarteLesProgrammesHorsPlage() {
        val soir = parseXmltvDate("20251215190000 +0000")!!..parseXmltvDate("20251215220000 +0000")!!
        val r = parser.parse(xml.byteInputStream(), mapOf("a" to 5L), soir)
        assertEquals(listOf("Soir"), r.map { it.title })
        assertEquals("d", r.single().description)
    }
}
