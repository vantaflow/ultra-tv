package com.ultratv.tv.nativeapp.data.m3u

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {
    private val parser = M3uParser(OkHttpClient())

    @Test fun parse_extinfAvecAttributs_produitChaineEtCategorie() {
        val r = parser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1" tvg-logo="http://l/x.png" group-title="News" catchup-days="3" catchup-source="http://c/{utc}",BBC One
            http://s/1.ts
            """.trimIndent(),
            providerId = 7,
        )
        assertEquals(1, r.channels.size)
        val c = r.channels.single()
        assertEquals("BBC One", c.name)
        assertEquals("bbc1", c.remoteId)
        assertEquals("bbc1", c.epgChannelId)
        assertEquals("g:News", c.categoryId)
        assertEquals("http://s/1.ts", c.streamUrl)
        assertEquals(3, c.catchupDays)
        assertEquals(7L, c.providerId)
        assertEquals(listOf("News"), r.categories.map { it.name })
    }

    @Test fun parse_ignoreLignesCommentairesEntreExtinfEtUrl() {
        val r = parser.parse("#EXTINF:-1,A\n#EXTVLCOPT:foo\n\nhttp://s/a\n", 1)
        assertEquals("http://s/a", r.channels.single().streamUrl)
    }

    @Test fun parse_extinfSansUrl_estIgnore() {
        val r = parser.parse("#EXTINF:-1,A\n#EXTINF:-1,B\nhttp://s/b\n", 1)
        assertEquals(listOf("B"), r.channels.map { it.name })
    }

    @Test fun parse_sansTvgId_numeroteLesIdentifiants() {
        val r = parser.parse("#EXTINF:-1,A\nhttp://a\n#EXTINF:-1,B\nhttp://b\n", 1)
        assertEquals(listOf("m3u-0", "m3u-1"), r.channels.map { it.remoteId })
        assertNull(r.channels[0].epgChannelId)
    }

    @Test fun parse_grosseListe_resteLineaire() {
        val sb = StringBuilder("#EXTM3U\n")
        repeat(50_000) { sb.append("#EXTINF:-1 tvg-id=\"c$it\" group-title=\"G${it % 100}\",Ch $it\nhttp://s/$it\n") }
        val r = parser.parse(sb.toString(), 1)
        assertEquals(50_000, r.channels.size)
        assertEquals(100, r.categories.size)
    }

    @Test fun parse_tvgIdRepete_chaqueChaineGardeUnIdentifiantUnique() {
        val r = parser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="tf1.fr" group-title="HD",TF1 HD
            http://s/1.ts
            #EXTINF:-1 tvg-id="tf1.fr" group-title="SD",TF1 SD
            http://s/2.ts
            #EXTINF:-1 tvg-id="tf1.fr" group-title="4K",TF1 4K
            http://s/3.ts
            """.trimIndent(),
            providerId = 7,
        )
        assertEquals(3, r.channels.map { it.remoteId }.toSet().size)
        assertEquals("tf1.fr", r.channels.first().remoteId)                 // la première garde son identifiant (favoris)
        assertEquals(listOf("tf1.fr", "tf1.fr", "tf1.fr"), r.channels.map { it.epgChannelId })   // toutes gardent le guide
    }
}
