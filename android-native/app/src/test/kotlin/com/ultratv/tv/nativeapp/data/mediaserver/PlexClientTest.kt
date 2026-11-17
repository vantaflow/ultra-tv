package com.ultratv.tv.nativeapp.data.mediaserver

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Client Plex contre un faux serveur : bibliothèques, pagination, épisodes, lecture, reprise, rapports, choix de connexion. */
class PlexClientTest {
    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private val token = "srv-secret-tok"

    private fun movie(i: Int) = """{"ratingKey":"$i","type":"movie","title":"Film $i","duration":1000,"Media":[{"container":"mp4","Part":[{"key":"/library/parts/$i/1/file.mp4"}]}]}"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                val u = request.requestUrl!!
                if (request.getHeader("X-Plex-Token") != token) return MockResponse().setResponseCode(401)
                return when {
                    u.encodedPath == "/identity" -> MockResponse().setBody("""{"MediaContainer":{}}""")
                    u.encodedPath == "/library/sections" -> MockResponse().setBody("""{"MediaContainer":{"Directory":[{"key":"1","title":"Films","type":"movie"},{"key":"2","title":"Séries","type":"show"}]}}""")
                    u.encodedPath == "/library/sections/1/all" -> {
                        val start = u.queryParameter("X-Plex-Container-Start")!!.toInt()
                        val items = (start until minOf(start + 300, 650)).joinToString(",") { movie(it) }
                        MockResponse().setBody("""{"MediaContainer":{"size":300,"totalSize":650,"Metadata":[$items]}}""")
                    }
                    u.encodedPath == "/library/sections/2/all" -> MockResponse().setBody("""{"MediaContainer":{"totalSize":1,"Metadata":[{"ratingKey":"500","type":"show","title":"La Série"}]}}""")
                    u.encodedPath == "/library/metadata/500/allLeaves" -> MockResponse().setBody("""{"MediaContainer":{"Metadata":[{"ratingKey":"501","type":"episode","title":"Pilote","parentIndex":1,"index":1},{"ratingKey":"502","type":"episode","title":"Deux","parentIndex":1,"index":2}]}}""")
                    u.encodedPath == "/library/metadata/7" -> MockResponse().setBody("""{"MediaContainer":{"Metadata":[{"ratingKey":"7","title":"Sept","duration":90000,"viewOffset":30000,"Media":[{"container":"mkv","Part":[{"key":"/library/parts/77/9/file.mkv"}]}]}]}}""")
                    u.encodedPath == "/library/metadata/8" -> MockResponse().setBody("""{"MediaContainer":{"Metadata":[{"ratingKey":"8","title":"Huit","duration":1000,"viewCount":2}]}}""")
                    u.encodedPath == "/:/timeline" -> MockResponse().setBody("")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun client() = MediaServerClient(OkHttpClient(), { "dev-1" })
    private fun provider(tok: String = token) = ProviderEntity(id = 5, name = "Salon", kind = "PLEX", baseUrl = base(), username = "mach1", password = tok)

    @Test fun bibliotheques_viaLeClientMultimedia() = runBlocking {
        val l = client().libraries(provider())
        assertEquals(listOf("1" to "movies", "2" to "tvshows"), l.map { it.id to it.type })
        val req = seen.first()
        assertEquals(token, req.getHeader("X-Plex-Token")); assertEquals("dev-1", req.getHeader("X-Plex-Client-Identifier"))
        assertEquals("Ultra TV", req.getHeader("X-Plex-Product")); assertEquals("application/json", req.getHeader("Accept"))
        // Le jeton ne voyage jamais dans l'adresse.
        assertTrue(seen.none { it.requestUrl.toString().contains(token) })
    }

    @Test fun films_pagines_650_en_3_pages() = runBlocking {
        val m = client().movies(provider(), "1")
        assertEquals(650, m.size)
        assertEquals("mediaserver://5/0", m[0].streamUrl)
        assertEquals(3, seen.count { it.requestUrl!!.encodedPath == "/library/sections/1/all" })
        assertTrue(m.all { !it.poster.orEmpty().contains(token) })
    }

    @Test fun series_et_episodes() = runBlocking {
        val s = client().series(provider(), "2")
        assertEquals("500", s.single().remoteId)
        val e = client().episodes(provider(), "500", 99)
        assertEquals(listOf(1 to 1, 1 to 2), e.map { it.season to it.episode })
    }

    @Test fun lectureDirecte_urlSansJeton_enTeteAvecJeton() = runBlocking {
        val (url, headers) = client().openUrl(provider(), "7")
        assertEquals("${base()}/library/parts/77/9/file.mkv", url)
        assertFalse(url.contains(token))
        assertEquals(token, headers["X-Plex-Token"]); assertEquals("dev-1", headers["X-Plex-Client-Identifier"])
    }

    @Test fun lectureSansFichier_leveUneErreur() = runBlocking {
        try { client().openUrl(provider(), "8"); fail() } catch (_: java.io.IOException) { }
    }

    @Test fun reprise_serveurPrime_vuEnEntierDuDebut_reseauKoGardeLocal() = runBlocking {
        assertEquals(30_000L, client().resumeMs(provider(), "7", 5_000))
        assertEquals(0L, client().resumeMs(provider(), "8", 5_000))
        assertEquals(5_000L, client().resumeMs(provider("mauvais"), "7", 5_000))   // 401 : position locale
    }

    @Test fun rapports_timeline_avecDuree() = runBlocking {
        val c = client()
        c.resumeMs(provider(), "7", 0)   // retient la durée (90 s)
        c.report(provider(), MediaServer.Report.PROGRESS, "7", 31_000, false)
        c.report(provider(), MediaServer.Report.STOP, "7", 32_000, true)
        val t = seen.filter { it.requestUrl!!.encodedPath == "/:/timeline" }
        assertEquals(listOf("playing", "stopped"), t.map { it.requestUrl!!.queryParameter("state") })
        assertEquals("31000", t[0].requestUrl!!.queryParameter("time")); assertEquals("90000", t[0].requestUrl!!.queryParameter("duration"))
        assertEquals("7", t[0].requestUrl!!.queryParameter("ratingKey")); assertEquals(token, t[0].getHeader("X-Plex-Token"))
    }

    @Test fun rapport_neLevePasEnCasDeEchec() = runBlocking {
        client().report(provider("mauvais"), MediaServer.Report.START, "7", 0, false)   // 401 : silencieux
    }

    @Test fun meilleureConnexion_passeALaSuivanteSiInjoignable() = runBlocking {
        val dead = Plex.Connection("http://127.0.0.1:1", local = true, relay = false, https = false, ipv6 = false)
        val ok = Plex.Connection(base(), local = false, relay = false, https = false, ipv6 = false)
        val s = Plex.Server("S", "m", token, true, listOf(dead, ok))
        assertEquals(base(), client().plex.bestConnection(s)!!.uri)
        assertNull(client().plex.bestConnection(Plex.Server("S", "m", token, true, listOf(dead))))
    }

    @Test fun jetonRefuse_leveHttpStatusSansAdresse() = runBlocking {
        try { client().libraries(provider("mauvais")); fail() } catch (e: HttpStatusException) {
            assertEquals(401, e.code); assertFalse(e.message.orEmpty().contains("127.0.0.1"))
        }
    }

    @Test fun imageAuth_neVisequeLesAffichesPlex() {
        val img = okhttp3.HttpUrl.Builder().scheme("http").host("h").port(32400).addPathSegments("photo/:/transcode").build()
        val other = okhttp3.HttpUrl.Builder().scheme("http").host("h").port(32400).addPathSegments("library/parts/1/file.mkv").build()
        assertTrue(PlexImageAuth.isPlexImage(img)); assertFalse(PlexImageAuth.isPlexImage(other))
        assertEquals("http://h:32400", PlexImageAuth.originOf(img))
    }
}
