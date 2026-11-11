package com.ultratv.tv.nativeapp.data.xmltv

import com.ultratv.tv.nativeapp.data.db.EpgTitle
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream

/**
 * Guide complémentaire d'un fournisseur polonais (identifiants avec espaces, plusieurs noms par chaîne, décalage +0100)
 * face à des chaînes M3U sans identifiant EPG ou avec un identifiant différent. Aucun réseau.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ExtraEpgPlGuideTest {
    private val parser = XmltvParser(OkHttpClient())

    private class Feed(val id: String, val names: List<String>)

    private val feeds = listOf(
        Feed("TVP 1", listOf("TVP 1", "TVP1", "PL: TVP 1 HD", "PL| TVP 1", "TVP 1 HD PL", "TVP 1 FHD PL")),
        Feed("TVP 2", listOf("TVP 2", "TVP2", "PL: TVP 2 HD", "TVP 2 FHD PL")),
        Feed("Polsat", listOf("Polsat", "POLSAT HD", "PL | POLSAT")),
        Feed("TVN", listOf("TVN", "TVN HD PL")),
        Feed("TVN 7", listOf("TVN 7", "TVN7", "PL: TVN 7 HD")),
        Feed("TVN24", listOf("TVN24", "TVN 24", "TVN24 FHD PL")),
    )

    private fun channelXml(f: Feed) =
        "<channel id=\"${f.id}\">" + f.names.joinToString("") { "<display-name lang=\"pl\">$it</display-name>" } + "</channel>\n"

    private fun programmeXml(f: Feed, k: Int) =
        "<programme start=\"2026110${1 + k % 9}180000 +0100\" stop=\"2026110${1 + k % 9}190000 +0100\" channel=\"${f.id}\">" +
            "<title lang=\"pl\">Program ${f.id} $k</title><desc lang=\"pl\">Opis</desc></programme>\n"

    private val fixture: String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"fixture\">\n")
        feeds.forEach { append(channelXml(it)) }
        feeds.forEach { f -> for (k in 0 until 3) append(programmeXml(f, k)) }
        append("</tv>\n")
    }

    private val targets = listOf(
        EpgTitle(1, "PL: TVP 1 HD", null, null),
        EpgTitle(2, "PL | POLSAT HD", null, null),
        EpgTitle(3, "TVN FHD", null, null),
        EpgTitle(4, "Telewizja Polska 1", "TVP1.pl", null),
        EpgTitle(5, "TVN 7 HD", null, "PL"),
        EpgTitle(6, "PL: TVN 24 FHD", "", "PL"),
        EpgTitle(7, "BBC One HD", null, "GB"),
        EpgTitle(8, "TVP2 UHD PL", "TVP2.pl", "PL"),
    )

    private fun run(xml: String = fixture): Map<Long, List<String>> {
        val resolver = ExtraEpg.Resolver(targets)
        val out = HashMap<Long, MutableList<String>>()
        parser.externalProgrammes(xml.byteInputStream(), resolver::resolve).forEach { out.getOrPut(it.channelId) { mutableListOf() } += it.title }
        return out
    }

    @Test fun chainesPolonaises_recoiventLeursProgrammes() {
        val r = run()
        for (id in listOf(1L, 2L, 3L, 4L, 5L, 6L, 8L)) assertTrue("chaîne $id sans programme", r[id]?.size == 3)
        assertTrue("BBC One n'a aucun équivalent dans ce guide", 7L !in r)
    }

    @Test fun tvn_neCorrespondPasATvn7() {
        val r = run()
        assertTrue(r.getValue(3).all { it.startsWith("Program TVN ") && !it.startsWith("Program TVN 7") && !it.startsWith("Program TVN24") })
        assertTrue(r.getValue(5).all { it.startsWith("Program TVN 7") })
    }

    @Test fun identifiantDePlaylistDifferent_rapprocheParLeRadical() {
        assertTrue(run().getValue(4).all { it.startsWith("Program TVP 1 ") })
    }

    @Test fun decalageDeFuseau_convertiEnUtc() {
        val resolver = ExtraEpg.Resolver(targets)
        val first = parser.externalProgrammes(fixture.byteInputStream(), resolver::resolve).first { it.channelId == 1L }
        // 18:00 à +0100 == 17:00 UTC
        assertEquals(parseXmltvDate("20261101170000"), first.startMs)
    }

    @Test fun norm_prefixesSuffixesEtQualite() {
        val expected = "tvp1"
        for (n in listOf("TVP 1", "TVP1", "PL: TVP 1 HD", "PL| TVP 1", "PL | TVP 1", "TVP 1 HD PL", "TVP 1 FHD PL", "[PL] tvp 1 4K", "tvp 1 pl uhd"))
            assertEquals(n, expected, ExtraEpg.norm(n))
        assertEquals("polsat", ExtraEpg.norm("PL | POLSAT HD"))
        assertEquals("tvn", ExtraEpg.norm("TVN FHD"))
        assertEquals("tvn7", ExtraEpg.norm("TVN 7"))
    }

    @Test fun norm_nAvalePasUnNomEntier() {
        assertEquals("pl", ExtraEpg.norm("PL"))
        assertEquals("canal+", ExtraEpg.norm("Canal+"))
        assertEquals("tv5monde", ExtraEpg.norm("TV5 Monde"))
    }

    // ---- charge : ~110 000 programmes, flux généré à la volée (aucun fichier, aucun réseau) ----

    /** XMLTV synthétique de [channels] chaînes × [perChannel] programmes, produit par morceaux : la mémoire reste plate. */
    private class SyntheticFeed(val channels: Int, val perChannel: Int) : InputStream() {
        private val chunks: Iterator<ByteArray> = sequence {
            yield("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n".toByteArray())
            for (c in 0 until channels) yield("<channel id=\"CH $c\"><display-name>CH $c</display-name><display-name>PL: CH $c HD</display-name><display-name>CH $c FHD PL</display-name></channel>\n".toByteArray())
            val desc = "Opis programu o długości zwykłego opisu w przewodniku telewizyjnym, z polskimi znakami: ąęłńóśźż. ".repeat(3)
            for (c in 0 until channels) for (k in 0 until perChannel) {
                val d = 1 + k / 24; val h = k % 24
                yield("<programme start=\"202611%02d%02d0000 +0100\" stop=\"202611%02d%02d3000 +0100\" channel=\"CH $c\"><title lang=\"pl\">Program $c-$k</title><desc lang=\"pl\">$desc</desc></programme>\n".format(d, h, d, h).toByteArray())
            }
            yield("</tv>\n".toByteArray())
        }.iterator()
        private var cur = ByteArray(0); private var pos = 0
        var bytes = 0L
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (pos >= cur.size) { if (!chunks.hasNext()) return -1; cur = chunks.next(); pos = 0 }
            val n = minOf(len, cur.size - pos)
            System.arraycopy(cur, pos, b, off, n); pos += n; bytes += n
            return n
        }
    }

    @Test fun charge_110000Programmes_seulesLesChainesRapprocheesSontEmises() {
        val channels = 800; val perChannel = 137            // 109 600 programmes, ~ 60 Mo
        // 40 chaînes locales seulement : « PL: CH 7 HD » doit trouver « CH 7 ».
        val mine = (0 until 40).map { EpgTitle(1000L + it, "PL: CH ${it * 20} HD", null, "PL") }
        val resolver = ExtraEpg.Resolver(mine)
        val feed = SyntheticFeed(channels, perChannel)
        val t0 = System.nanoTime()
        var emitted = 0
        var maxHeld = 0
        parser.externalProgrammes(feed, resolver::resolve).forEach { emitted++; maxHeld = maxOf(maxHeld, 1) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("charge guide : ${feed.bytes / 1_000_000} Mo lus, $emitted programmes émis sur ${channels * perChannel}, $ms ms")
        assertEquals(40 * perChannel, emitted)
        assertTrue("lecture d'un flux de ${feed.bytes} octets trop lente : $ms ms", ms < 120_000)
    }
}
