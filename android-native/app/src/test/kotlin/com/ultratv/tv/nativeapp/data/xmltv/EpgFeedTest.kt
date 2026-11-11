package com.ultratv.tv.nativeapp.data.xmltv

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Téléchargement du guide : gzip en flux, débit lent, connexion qui cale, délais, reprises. Faux serveur en mémoire. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class EpgFeedTest {
    private val server = FakeFeedServer()
    private val provider = ProviderEntity(id = 1, name = "fictif", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid", username = "u", password = "p")
    private val fast = EpgLimits(connectMs = 1_000, stallMs = 400, totalMs = 30_000, minBytesPerSecond = 0, maxAttempts = 3, backoffMs = listOf(5, 10))
    private val sleeps = mutableListOf<Long>()

    @After fun tearDown() { server.closeAll() }

    private fun parser(limits: EpgLimits = fast) =
        XmltvParser(OkHttpClient.Builder().addInterceptor(server.interceptor).build(), limits) { sleeps += it }

    private fun feed(n: Int, now: Long = System.currentTimeMillis()): ByteArray {
        val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n")
            for (i in 0 until n) {
                val s = now - 30 * 60_000L + i * 30 * 60_000L
                append("<programme start=\"${fmt.format(java.util.Date(s))} +0000\" stop=\"${fmt.format(java.util.Date(s + 30 * 60_000L))} +0000\" channel=\"a\"><title>P$i</title><desc>d</desc></programme>\n")
            }
            append("</tv>\n")
        }.toByteArray()
    }

    private fun gz(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    private fun read(p: XmltvParser, onBytes: (Long, Long) -> Unit = { _, _ -> }): List<String> = runBlocking {
        p.withProgrammes(provider, mapOf("a" to 7L), onBytes = onBytes) { seq -> seq.map { it.title }.toList() }
    }

    @Test fun gzip_et_nonCompresse_donnentLesMemesProgrammes() {
        val raw = feed(20)
        server.add("xmltv.php", raw)
        val plain = read(parser())
        server.add("xmltv.php", gz(raw))
        val zipped = read(parser())
        assertEquals(plain, zipped)
        assertEquals(20, plain.size)
    }

    @Test fun gzip_estLuEnFlux_sansFichierNiChaineGeante() {
        // 40 000 programmes générés à la volée (jamais en mémoire en entier) : flux gzip lu séquentiellement.
        val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val s0 = System.currentTimeMillis()
        val src = java.io.PipedInputStream(1 shl 16)
        val out = java.io.PipedOutputStream(src)
        val t = Thread {
            GZIPOutputStream(out).bufferedWriter().use { w ->
                w.write("<tv>")
                for (i in 0 until 40_000) w.write("<programme start=\"${fmt.format(java.util.Date(s0 + i * 1000L))} +0000\" stop=\"${fmt.format(java.util.Date(s0 + i * 1000L + 900_000))} +0000\" channel=\"a\"><title>P$i</title></programme>")
                w.write("</tv>")
            }
        }.apply { isDaemon = true; start() }
        val n = XmltvParser(OkHttpClient()).programmes(maybeGunzip(src), mapOf("a" to 1L), Long.MIN_VALUE..Long.MAX_VALUE).count()
        t.join()
        assertEquals(40_000, n)
    }

    @Test fun fluxLent_aboutit_etLaProgressionMonteJusquAuTotal() {
        val raw = gz(feed(300))
        server.add("xmltv.php", raw, bytesPerSecond = 20_000L)
        val seen = mutableListOf<Pair<Long, Long>>()
        val t0 = System.currentTimeMillis()
        val titles = read(parser()) { b, t -> seen += b to t }
        assertTrue(titles.isNotEmpty())
        assertTrue("le débit limité est bien appliqué", System.currentTimeMillis() - t0 >= raw.size * 1000L / 20_000 / 2)
        assertTrue(seen.isNotEmpty() && seen.all { it.second == raw.size.toLong() })
        assertTrue("octets croissants", seen.zipWithNext().all { (a, b) -> b.first >= a.first })
        assertEquals(raw.size.toLong(), seen.last().first)
    }

    @Test fun fluxQuiCale_echoueApresLeDelai_puisSignaleLeTimeout() {
        server.add("xmltv.php", feed(200), stallAfterBytes = 2_000)
        val t0 = System.currentTimeMillis()
        try { read(parser()); fail("un flux calé doit échouer") } catch (e: XmltvParser.FetchException) {
            val c = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<EpgTimeoutException>().firstOrNull()
            assertEquals(EpgTimeoutException.Kind.STALL, c?.kind)
            assertEquals(3, e.attempts)
            assertEquals(EpgFailReason.TIMEOUT, EpgErrors.reason(e))
        }
        // 3 tentatives de ~400 ms + attentes : borné, jamais infini.
        assertTrue("trop long : ${System.currentTimeMillis() - t0} ms", System.currentTimeMillis() - t0 < 15_000)
        assertEquals(3, server.routes.getValue("xmltv.php").hits.get())
        assertEquals(listOf(5L, 10L), sleeps)
    }

    @Test fun dureeTotale_estBornee_memeSiDesOctetsArrivent() {
        server.add("xmltv.php", feed(2_000), bytesPerSecond = 3_000)
        val p = parser(fast.copy(stallMs = 10_000, totalMs = 600, maxAttempts = 1))
        try { read(p); fail("la durée totale doit être bornée") } catch (e: XmltvParser.FetchException) {
            assertEquals(EpgTimeoutException.Kind.TOTAL, generateSequence<Throwable>(e) { it.cause }.filterIsInstance<EpgTimeoutException>().first().kind)
        }
    }

    @Test fun debitTropFaible_estCoupe() {
        server.add("xmltv.php", feed(2_000), bytesPerSecond = 500)
        val p = parser(fast.copy(stallMs = 10_000, minBytesPerSecond = 5_000, slowGraceMs = 300, maxAttempts = 1))
        try { read(p); fail() } catch (e: XmltvParser.FetchException) {
            assertEquals(EpgTimeoutException.Kind.TOO_SLOW, generateSequence<Throwable>(e) { it.cause }.filterIsInstance<EpgTimeoutException>().first().kind)
        }
    }

    @Test fun reprise_apres503_puisSucces_avecAttenteCroissante() {
        server.add("xmltv.php", feed(10)).apply { failFirst = 2; failCode = 503 }
        assertTrue(read(parser()).isNotEmpty())
        assertEquals(3, server.routes.getValue("xmltv.php").hits.get())
        assertEquals(listOf(5L, 10L), sleeps)
    }

    @Test fun reprise_apresCoupureEnMilieuDeFlux() {
        val raw = feed(40)
        server.add("xmltv.php", raw).apply { truncateAfterBytes = raw.size / 2L; truncateFirst = 1 }
        assertEquals(40, read(parser()).size)
        assertEquals(2, server.routes.getValue("xmltv.php").hits.get())
    }

    @Test fun pasDeReprise_surIdentifiantsRefuses_ouAdresseIntrouvable() {
        for (code in listOf(401, 403, 404)) {
            server.routes.clear(); sleeps.clear()
            server.add("xmltv.php", feed(1)).apply { failFirst = 99; failCode = code }
            try { read(parser()); fail() } catch (e: XmltvParser.FetchException) {
                assertEquals(1, e.attempts)
                assertEquals(1, server.routes.getValue("xmltv.php").hits.get())
            }
            assertTrue(sleeps.isEmpty())
        }
    }

    @Test fun nombreDeReprises_estLimite() {
        server.add("xmltv.php", feed(1)).apply { failFirst = 99 }
        try { read(parser(fast.copy(maxAttempts = 4))); fail() } catch (e: XmltvParser.FetchException) { assertEquals(4, e.attempts) }
        assertEquals(4, server.routes.getValue("xmltv.php").hits.get())
    }

    @Test fun xmlInvalide_neDeclenchePasDeReprise() {
        server.add("xmltv.php", "<tv><programme start=\"x\"".toByteArray())
        try { read(parser()); fail() } catch (e: XmltvParser.FetchException) { assertEquals(1, e.attempts); assertEquals(EpgFailReason.BAD_FORMAT, EpgErrors.reason(e)) }
    }

    // ── url-tvg / x-tvg-url ──

    @Test fun tvgUrls_uneOuPlusieursAdresses() {
        assertEquals(listOf("http://a.invalid/g.xml", "https://b.invalid/g.xml.gz"), parseTvgUrls("#EXTM3U url-tvg=\"http://a.invalid/g.xml, https://b.invalid/g.xml.gz\""))
        assertEquals(listOf("https://c.invalid/x.xml"), parseTvgUrls("#EXTM3U x-tvg-url=\"https://c.invalid/x.xml\" tvg-shift=\"0\""))
        assertEquals(listOf("http://a.invalid/g.xml"), parseTvgUrls("#EXTM3U url-tvg=\"http://a.invalid/g.xml\" x-tvg-url=\"http://a.invalid/g.xml\""))
        assertTrue(parseTvgUrls("#EXTM3U").isEmpty())
        assertTrue("schémas non http ignorés", parseTvgUrls("#EXTM3U url-tvg=\"file:///etc/passwd,ftp://x.invalid/a\"").isEmpty())
    }

    @Test fun m3u_enTete_alimenteLesAdressesDeGuide() {
        val r = com.ultratv.tv.nativeapp.data.m3u.M3uParser(OkHttpClient()).parse(
            "#EXTM3U url-tvg=\"http://a.invalid/g.xml,http://b.invalid/g.xml.gz\"\n#EXTINF:-1 tvg-id=\"a\" group-title=\"G\",Chaine\nhttp://s.invalid/1.ts\n", 1L)
        assertEquals(listOf("http://a.invalid/g.xml", "http://b.invalid/g.xml.gz"), r.epgUrls)
        assertEquals("a", r.channels.single().epgChannelId)
    }

    // ── journaux : jamais d'identifiants ──

    @Test fun journal_neContientNiAdresseNiIdentifiants() {
        val secret = IllegalStateException("Failed to connect to http://user:pass123@serveur.invalid:8080/xmltv.php?username=bob&password=hunter2")
        val line = EpgErrors.logLine(XmltvParser.FetchException(secret, 3), 3)
        for (bad in listOf("http", "pass123", "hunter2", "bob", "serveur.invalid", "user")) assertTrue("« $bad » dans le journal : $line", !line.contains(bad, ignoreCase = true))
        // Et même si un message brut était journalisé, RemoteLog le passe par LogSanitizer.
        val cleaned = com.ultratv.tv.nativeapp.LogSanitizer.sanitize(secret.message!!)
        assertTrue(cleaned, !cleaned.contains("pass123") && !cleaned.contains("hunter2") && !cleaned.contains("bob"))
    }
}
