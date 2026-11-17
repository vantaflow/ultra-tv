package com.ultratv.tv.nativeapp.data.nas

import com.ultratv.tv.nativeapp.data.nas.NasHttp.RangeResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/** Relais HTTP local : plages `Range` (partie pure) puis serveur réel sur 127.0.0.1 avec un faux fichier. */
class NasRelayTest {

    // ───── Range ─────

    @Test fun range_absentOuIncomprisVautToutLeFichier() {
        assertEquals(RangeResult.Full, NasHttp.parseRange(null, 100))
        assertEquals(RangeResult.Full, NasHttp.parseRange("items=0-5", 100))
        assertEquals(RangeResult.Full, NasHttp.parseRange("bytes=abc-def", 100))
        assertEquals(RangeResult.Full, NasHttp.parseRange("bytes=-", 100))
    }

    @Test fun range_formesUsuelles() {
        assertEquals(RangeResult.Partial(NasHttp.Range(0, 9)), NasHttp.parseRange("bytes=0-9", 100))
        assertEquals(RangeResult.Partial(NasHttp.Range(50, 99)), NasHttp.parseRange("bytes=50-", 100))
        assertEquals(RangeResult.Partial(NasHttp.Range(90, 99)), NasHttp.parseRange("bytes=-10", 100))
        assertEquals(RangeResult.Partial(NasHttp.Range(0, 99)), NasHttp.parseRange("bytes=-500", 100))
        assertEquals(RangeResult.Partial(NasHttp.Range(10, 99)), NasHttp.parseRange("bytes=10-5000", 100))
        // Plusieurs plages : la première.
        assertEquals(RangeResult.Partial(NasHttp.Range(0, 4)), NasHttp.parseRange("bytes=0-4,10-14", 100))
        assertEquals(10L, NasHttp.Range(5, 14).length)
    }

    @Test fun range_horsFichier() {
        assertEquals(RangeResult.Unsatisfiable, NasHttp.parseRange("bytes=100-", 100))
        assertEquals(RangeResult.Unsatisfiable, NasHttp.parseRange("bytes=200-300", 100))
        assertEquals(RangeResult.Unsatisfiable, NasHttp.parseRange("bytes=9-3", 100))
        assertEquals(RangeResult.Unsatisfiable, NasHttp.parseRange("bytes=0-5", 0))
        assertEquals(RangeResult.Unsatisfiable, NasHttp.parseRange("bytes=-0", 100))
    }

    @Test fun typesEtLigneDeRequete() {
        assertEquals("video/x-matroska", NasHttp.contentType("MKV"))
        assertEquals("video/mp4", NasHttp.contentType("mp4"))
        assertEquals("application/octet-stream", NasHttp.contentType("xyz"))
        assertEquals("GET" to "/a/b", NasHttp.parseRequestLine("GET /a/b?x=1 HTTP/1.1"))
        assertEquals("HEAD" to "/a", NasHttp.parseRequestLine("head /a HTTP/1.0"))
        assertNull(NasHttp.parseRequestLine("GET /a"))
        assertNull(NasHttp.parseRequestLine("garbage"))
    }

    // ───── Serveur réel ─────

    private class MemFile(private val data: ByteArray) : NasFile {
        var closed = false
        override val size: Long get() = data.size.toLong()
        override fun read(offset: Long, buf: ByteArray, off: Int, len: Int): Int {
            if (offset >= data.size) return -1
            val n = minOf(len, data.size - offset.toInt())
            System.arraycopy(data, offset.toInt(), buf, off, n)
            return n
        }
        override fun close() { closed = true }
    }

    private val payload = ByteArray(700_000) { (it % 251).toByte() }

    private fun withRelay(block: (NasRelay, MemFile) -> Unit) {
        val file = MemFile(payload)
        val opened = ArrayList<Pair<Long, List<String>>>()
        val relay = NasRelay { pid, path -> opened += pid to path; if (path.last() == "absent.mkv") null else file }
        try { block(relay, file) } finally { relay.stop() }
    }

    private fun get(url: String, range: String? = null, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            if (range != null) setRequestProperty("Range", range)
            connectTimeout = 5000; readTimeout = 5000
        }

    @Test fun relais_fichierComplet() = withRelay { relay, file ->
        val c = get(relay.urlFor(5, listOf("Films", "Titre (2019).mkv")))
        assertEquals(200, c.responseCode)
        assertEquals("video/x-matroska", c.contentType)
        assertEquals("bytes", c.getHeaderField("Accept-Ranges"))
        assertEquals(payload.size.toLong(), c.contentLengthLong)
        assertArrayEquals(payload, c.inputStream.readBytes())
        // Le fichier SMB est refermé une fois la réponse envoyée (fermeture côté serveur, juste après la dernière écriture).
        val deadline = System.currentTimeMillis() + 3000
        while (!file.closed && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(file.closed)
    }

    @Test fun relais_plageEnMilieu_recherche() = withRelay { relay, _ ->
        val c = get(relay.urlFor(5, listOf("Films", "a.mp4")), range = "bytes=300000-300999")
        assertEquals(206, c.responseCode)
        assertEquals("bytes 300000-300999/${payload.size}", c.getHeaderField("Content-Range"))
        assertEquals(1000L, c.contentLengthLong)
        assertArrayEquals(payload.copyOfRange(300000, 301000), c.inputStream.readBytes())
    }

    @Test fun relais_plageOuverteEtFinDeFichier() = withRelay { relay, _ ->
        val c = get(relay.urlFor(5, listOf("a.mkv")), range = "bytes=699000-")
        assertEquals(206, c.responseCode)
        assertArrayEquals(payload.copyOfRange(699000, payload.size), c.inputStream.readBytes())
        val tail = get(relay.urlFor(5, listOf("a.mkv")), range = "bytes=-100")
        assertEquals(206, tail.responseCode)
        assertArrayEquals(payload.copyOfRange(payload.size - 100, payload.size), tail.inputStream.readBytes())
    }

    @Test fun relais_plageHorsFichier_416() = withRelay { relay, _ ->
        val c = get(relay.urlFor(5, listOf("a.mkv")), range = "bytes=900000-")
        assertEquals(416, c.responseCode)
        assertEquals("bytes */${payload.size}", c.getHeaderField("Content-Range"))
    }

    @Test fun relais_head_sansCorps() = withRelay { relay, _ ->
        val c = get(relay.urlFor(5, listOf("a.mkv")), method = "HEAD")
        assertEquals(200, c.responseCode)
        assertEquals(payload.size.toLong(), c.contentLengthLong)
    }

    @Test fun relais_jetonObligatoire_etFichierAbsent() = withRelay { relay, _ ->
        val good = relay.urlFor(5, listOf("a.mkv"))
        val bad = good.replace(Regex("(127\\.0\\.0\\.1:\\d+/)[^/]+/"), "$1mauvaisjeton/")
        assertEquals(404, get(bad).responseCode)
        assertEquals(404, get(relay.urlFor(5, listOf("absent.mkv"))).responseCode)
        assertEquals(404, get("http://127.0.0.1:${relay.port}/").responseCode)
    }

    @Test fun relais_methodeNonPermise() = withRelay { relay, _ ->
        val c = get(relay.urlFor(5, listOf("a.mkv")), method = "DELETE")
        assertEquals(405, c.responseCode)
    }

    @Test fun relais_adresseSansIdentifiantsEtSurLoopback() = withRelay { relay, _ ->
        val u = relay.urlFor(12, listOf("Films", "Secret.mkv"))
        assertTrue(u.startsWith("http://127.0.0.1:"))
        assertTrue(!u.contains("Secret") && !u.contains("smb"))
        assertTrue(u.endsWith(".mkv"))
    }
}
