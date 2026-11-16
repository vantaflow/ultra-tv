package com.ultratv.tv.nativeapp.data.m3u

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Export M3U des favoris : format, nettoyage des valeurs, adresses exclues, relecture par le lecteur de playlists. */
class M3uExportTest {

    @Test fun build_formatStandard() {
        val out = M3uExport.build(listOf(M3uExport.Entry("BBC One", "http://s/u/p/1.ts", "bbc1", "http://l/x.png", "News", 12)))
        assertEquals(
            "#EXTM3U\n#EXTINF:-1 tvg-id=\"bbc1\" tvg-name=\"BBC One\" tvg-logo=\"http://l/x.png\" tvg-chno=\"12\" group-title=\"News\",BBC One\nhttp://s/u/p/1.ts\n",
            out,
        )
    }

    @Test fun build_listeVide_nEcritQueLEntete() {
        assertEquals("#EXTM3U\n", M3uExport.build(emptyList()))
    }

    @Test fun build_attributsVidesOmis() {
        val out = M3uExport.build(listOf(M3uExport.Entry("Arte", "https://s/2", tvgId = "", logo = null, group = " ")))
        assertFalse(out.contains("tvg-id"))
        assertFalse(out.contains("tvg-logo"))
        assertFalse(out.contains("group-title"))
        assertFalse(out.contains("tvg-chno"))
    }

    @Test fun build_guillemetsEtSautsDeLigneNeCassentPasLeFormat() {
        val out = M3uExport.build(listOf(M3uExport.Entry("Canal \"Plus\"\nSport", "http://s/3", group = "A\"B\nC")))
        val lines = out.trimEnd().split("\n")
        assertEquals(3, lines.size)   // entête, EXTINF, adresse : aucune ligne parasite
        assertTrue(lines[1].contains("group-title=\"A'B C\""))
        assertTrue(lines[1].endsWith(",Canal \"Plus\" Sport"))
    }

    @Test fun build_adressesInternesIgnorees_ordreConserve() {
        val entries = listOf(
            M3uExport.Entry("A", "http://s/a"), M3uExport.Entry("Stalker", "stalker://1/2"),
            M3uExport.Entry("B", "rtmp://s/b"), M3uExport.Entry("Vide", ""),
        )
        val out = M3uExport.build(entries)
        assertEquals(2, M3uExport.count(entries))
        assertFalse(out.contains("stalker"))
        assertTrue(out.indexOf("http://s/a") < out.indexOf("rtmp://s/b"))
    }

    @Test fun build_nomVide_remplaceParUnTiret() {
        assertTrue(M3uExport.build(listOf(M3uExport.Entry("  ", "http://s/x"))).contains(",-\n"))
    }

    @Test fun fileName_datee() {
        assertEquals("ultra-tv-favorites-20261105.m3u", M3uExport.fileName(2026, 11, 5))
    }

    @Test fun relecture_parLeLecteurDePlaylists() {
        val entries = listOf(
            M3uExport.Entry("BBC One", "http://s/u/p/1.ts", "bbc1", "http://l/x.png", "News"),
            M3uExport.Entry("Arte", "http://s/u/p/2.ts", "arte.fr", null, "Culture"),
        )
        val r = M3uParser(OkHttpClient()).parse(M3uExport.build(entries), providerId = 1)
        assertEquals(listOf("BBC One", "Arte"), r.channels.map { it.name })
        assertEquals(listOf("bbc1", "arte.fr"), r.channels.map { it.epgChannelId })
        assertEquals(listOf("http://s/u/p/1.ts", "http://s/u/p/2.ts"), r.channels.map { it.streamUrl })
        assertEquals("http://l/x.png", r.channels[0].logo)
        assertEquals(setOf("News", "Culture"), r.categories.map { it.name }.toSet())
    }
}
