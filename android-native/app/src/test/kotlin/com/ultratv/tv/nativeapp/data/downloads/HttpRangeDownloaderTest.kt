package com.ultratv.tv.nativeapp.data.downloads

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Moteur de téléchargement contre un vrai serveur HTTP local : reprise par plage, partiel, renommage, nouvelles tentatives. */
class HttpRangeDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    private val sleeps = mutableListOf<Long>()
    private val content = ByteArray(10) { (it + 1).toByte() }

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun downloader(free: Long = Long.MAX_VALUE, checkEveryMs: Long = 1_000) =
        HttpRangeDownloader(client, freeBytes = { free }, sleep = { sleeps += it }, checkEveryMs = checkEveryMs)

    private fun target(): File = File(tmp.root, "downloads/movie-abc.mkv")
    private fun url() = server.url("/movie/u/p/1.mkv").toString()
    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    @Test fun download_sansPartiel_ecritLeFichierPuisRenommeLePartiel() = runBlocking {
        server.enqueue(MockResponse().setBody(body(content)))
        val r = downloader().download(url(), target())
        assertEquals(HttpRangeDownloader.Outcome.Completed(10), r)
        assertArrayEquals(content, target().readBytes())
        assertFalse(DownloadPlan.partFile(target()).exists())
        assertNull(server.takeRequest().getHeader("Range"))
    }

    @Test fun download_partielExistant_envoieRangeEtAjouteLaSuite() = runBlocking {
        target().parentFile!!.mkdirs()
        DownloadPlan.partFile(target()).writeBytes(content.copyOfRange(0, 4))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 4-9/10").setBody(body(content.copyOfRange(4, 10))))
        val r = downloader().download(url(), target())
        assertEquals(HttpRangeDownloader.Outcome.Completed(10), r)
        assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(content, target().readBytes())
    }

    @Test fun download_serveurIgnoreLaPlage_repartDeZeroSansMelanger() = runBlocking {
        target().parentFile!!.mkdirs()
        DownloadPlan.partFile(target()).writeBytes(byteArrayOf(9, 9, 9))
        server.enqueue(MockResponse().setResponseCode(200).setBody(body(content)))
        val r = downloader().download(url(), target())
        assertEquals(HttpRangeDownloader.Outcome.Completed(10), r)
        assertArrayEquals(content, target().readBytes())
    }

    @Test fun download_416SurPartielComplet_termineSansRetelecharger() = runBlocking {
        target().parentFile!!.mkdirs()
        DownloadPlan.partFile(target()).writeBytes(content)
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */10"))
        val r = downloader().download(url(), target())
        assertEquals(HttpRangeDownloader.Outcome.Completed(10), r)
        assertArrayEquals(content, target().readBytes())
        assertEquals(1, server.requestCount)
    }

    @Test fun download_reponseTronquee_garderLePartielEtReprendreParPlage() = runBlocking {
        val big = ByteArray(256 * 1024) { (it % 251).toByte() }
        val d = downloader()
        // 1er appel coupé au milieu du corps ; le 2e répond depuis l'octet demandé : on lit la plage reçue pour construire la réponse.
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            var n = 0
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                n++
                if (n == 1) return MockResponse().setBody(body(big)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                val from = request.getHeader("Range")!!.removePrefix("bytes=").removeSuffix("-").toInt()
                return MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $from-${big.size - 1}/${big.size}")
                    .setBody(body(big.copyOfRange(from, big.size)))
            }
        }
        val r = d.download(url(), target(), baseBackoffMs = 10)
        assertEquals(HttpRangeDownloader.Outcome.Completed(big.size.toLong()), r)
        assertArrayEquals(big, target().readBytes())
        assertEquals(listOf(10L), sleeps)
    }

    @Test fun download_503PuisSucces_reessaieAvecAttenteExponentielle() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(body(content)))
        val r = downloader().download(url(), target(), baseBackoffMs = 100)
        assertEquals(HttpRangeDownloader.Outcome.Completed(10), r)
        assertEquals(listOf(100L, 200L), sleeps)
    }

    @Test fun download_503Persistant_echecPassagerApresLesTentatives() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }
        val r = downloader().download(url(), target(), maxAttempts = 3, baseBackoffMs = 1)
        assertEquals(HttpRangeDownloader.Outcome.Failed(DownloadError.HTTP, retryable = true), r)
        assertEquals(3, server.requestCount)
    }

    @Test fun download_404_echecDefinitifSansNouvelleTentative() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val r = downloader().download(url(), target())
        assertEquals(HttpRangeDownloader.Outcome.Failed(DownloadError.HTTP, retryable = false), r)
        assertEquals(1, server.requestCount)
        assertTrue(sleeps.isEmpty())
        assertFalse(target().exists())
    }

    @Test fun download_pasAssezDePlace_refuseAvantDEcrire() = runBlocking {
        server.enqueue(MockResponse().setBody(body(content)))
        val r = downloader(free = DownloadPlan.SPACE_MARGIN_BYTES).download(url(), target())
        assertEquals(HttpRangeDownloader.Outcome.Failed(DownloadError.NO_SPACE, retryable = false), r)
        assertFalse(target().exists())
        assertTrue(DownloadPlan.partFile(target()).let { !it.exists() || it.length() == 0L })
    }

    @Test fun download_arretDemande_conserveLePartielSansRenommer() = runBlocking {
        val big = ByteArray(512 * 1024) { 7 }
        server.enqueue(MockResponse().setBody(body(big)).throttleBody(64 * 1024, 20, TimeUnit.MILLISECONDS))
        val r = downloader(checkEveryMs = 0).download(url(), target(), shouldStop = { true })
        assertEquals(HttpRangeDownloader.Outcome.Stopped, r)
        assertFalse(target().exists())
        assertTrue(DownloadPlan.partFile(target()).length() > 0)
    }

    @Test fun parseContentRange_formesUsuelles() {
        assertEquals(HttpRangeDownloader.ContentRange(100, 1000), HttpRangeDownloader.parseContentRange("bytes 100-999/1000"))
        assertEquals(HttpRangeDownloader.ContentRange(5, null), HttpRangeDownloader.parseContentRange("bytes 5-9/*"))
        assertEquals(HttpRangeDownloader.ContentRange(0, 10), HttpRangeDownloader.parseContentRange("bytes */10"))
        assertNull(HttpRangeDownloader.parseContentRange(null))
        assertNull(HttpRangeDownloader.parseContentRange("n'importe quoi"))
    }
}
