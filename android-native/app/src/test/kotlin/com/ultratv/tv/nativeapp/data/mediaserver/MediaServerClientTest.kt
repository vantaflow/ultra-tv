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

/** Client Jellyfin / Emby contre un faux serveur (MockWebServer) : connexion, bibliothèques, pagination, épisodes, reprise, rapports. */
class MediaServerClientTest {
    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private val token = "tok-secret-123"
    private val userId = "u1"

    private fun movie(i: Int) = """{"Name":"Film $i","Id":"m$i","Type":"Movie","ProductionYear":2000,"MediaSources":[{"Id":"m$i","Container":"mkv"}],"ImageTags":{"Primary":"t$i"}}"""

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                val path = request.requestUrl!!.encodedPath
                val authed = request.getHeader("X-Emby-Token") == token
                return when {
                    path == "/Users/AuthenticateByName" ->
                        if (request.body.readUtf8().contains("\"Pw\":\"bon\"")) MockResponse().setBody("""{"User":{"Id":"$userId","Name":"lilou"},"AccessToken":"$token","ServerId":"s"}""")
                        else MockResponse().setResponseCode(401)
                    path == "/Users" -> if (request.getHeader("X-Emby-Token") == "cle-api") MockResponse().setBody("""[{"Id":"$userId","Name":"admin","Policy":{"IsAdministrator":true}}]""") else MockResponse().setResponseCode(401)
                    !authed -> MockResponse().setResponseCode(401)
                    path == "/Users/$userId/Views" -> MockResponse().setBody("""{"Items":[{"Name":"Films","Id":"lib1","CollectionType":"movies"},{"Name":"Séries","Id":"lib2","CollectionType":"tvshows"},{"Name":"Musique","Id":"lib3","CollectionType":"music"}]}""")
                    path == "/Users/$userId/Items" -> {
                        val q = request.requestUrl!!
                        val type = q.queryParameter("IncludeItemTypes")
                        val start = q.queryParameter("StartIndex")!!.toInt()
                        if (type == "Series") MockResponse().setBody("""{"Items":[{"Name":"La Série","Id":"ser1","Type":"Series"}],"TotalRecordCount":1}""")
                        else {
                            // 650 films : 3 pages de 300 au plus.
                            val items = (start until minOf(start + 300, 650)).joinToString(",") { movie(it) }
                            MockResponse().setBody("""{"Items":[$items],"TotalRecordCount":650}""")
                        }
                    }
                    path == "/Shows/ser1/Episodes" -> MockResponse().setBody("""{"Items":[{"Name":"Pilote","Id":"e1","Type":"Episode","ParentIndexNumber":1,"IndexNumber":1},{"Name":"Deux","Id":"e2","Type":"Episode","ParentIndexNumber":1,"IndexNumber":2}],"TotalRecordCount":2}""")
                    path == "/Users/$userId/Items/m1" -> MockResponse().setBody("""{"Name":"Film 1","Id":"m1","MediaSources":[{"Id":"srcA","Container":"mp4"}],"UserData":{"PlaybackPositionTicks":6000000000,"Played":false}}""")
                    path == "/Users/$userId/Items/m2" -> MockResponse().setBody("""{"Name":"Film 2","Id":"m2","UserData":{"PlaybackPositionTicks":0,"Played":true}}""")
                    path.startsWith("/Sessions/Playing") -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun base() = server.url("/").toString().trimEnd('/')
    private fun client() = MediaServerClient(OkHttpClient(), { "dev-1" })
    private fun provider(tok: String = token) = ProviderEntity(id = 5, name = "NAS", kind = "JELLYFIN", baseUrl = base(), username = userId, password = tok)

    @Test fun connexion_motDePasse_renvoieJetonEtUtilisateur() = runBlocking {
        val l = client().login(base(), "lilou", "bon")
        assertEquals(MediaServerClient.Login(userId, "lilou", token), l)
        val req = seen.first { it.requestUrl!!.encodedPath == "/Users/AuthenticateByName" }
        assertTrue(req.getHeader("X-Emby-Authorization")!!.contains("DeviceId=\"dev-1\""))
        assertEquals(req.getHeader("X-Emby-Authorization"), req.getHeader("Authorization"))
        assertNull(req.getHeader("X-Emby-Token"))
    }

    @Test fun connexion_motDePasseRefuse_401() = runBlocking {
        try { client().login(base(), "lilou", "mauvais"); fail("doit échouer") } catch (e: HttpStatusException) { assertEquals(401, e.code) }
    }

    @Test fun connexion_cleApi_choisitUnUtilisateur() = runBlocking {
        val l = client().login(base(), "", "", apiKey = "cle-api")
        assertEquals(MediaServerClient.Login(userId, "admin", "cle-api"), l)
    }

    @Test fun bibliotheques_filmsEtSeriesSeulement() = runBlocking {
        val libs = client().libraries(provider())
        assertEquals(listOf("lib1" to "movies", "lib2" to "tvshows"), libs.map { it.id to it.type })
    }

    @Test fun films_pagines_tousLus() = runBlocking {
        val movies = client().movies(provider(), "lib1")
        assertEquals(650, movies.size)
        assertEquals("mediaserver://5/m649", movies.last().streamUrl)
        assertEquals(3, seen.count { it.requestUrl!!.encodedPath == "/Users/$userId/Items" })
        assertTrue(movies.all { it.categoryId == "lib1" && it.providerId == 5L })
    }

    @Test fun series_etEpisodes() = runBlocking {
        val c = client()
        val s = c.series(provider(), "lib2")
        assertEquals(listOf("ser1"), s.map { it.remoteId })
        val eps = c.episodes(provider(), "ser1", 42)
        assertEquals(listOf(1 to 1, 1 to 2), eps.map { it.season to it.episode })
        assertTrue(eps.all { it.seriesId == 42L && it.streamUrl.startsWith("mediaserver://5/e") })
    }

    @Test fun lectureDirecte_urlSansJeton_enteteAvecJeton() = runBlocking {
        val (url, headers) = client().openUrl(provider(), "m1")
        assertEquals("${base()}/Videos/m1/stream.mp4?static=true&MediaSourceId=srcA", url)
        assertFalse(url.contains(token))
        assertEquals(mapOf("X-Emby-Token" to token), headers)
    }

    @Test fun lectureDirecte_ficheInjoignable_urlSimple() = runBlocking {
        val (url, _) = client().openUrl(provider(), "inconnu")   // 404 sur la fiche : repli sur l'adresse nue
        assertEquals("${base()}/Videos/inconnu/stream?static=true", url)
    }

    @Test fun reprise_depuisLeServeur() = runBlocking {
        assertEquals(600_000L, client().resumeMs(provider(), "m1", 1_000L))
        assertEquals(0L, client().resumeMs(provider(), "m2", 1_000L))         // vu en entier
        assertEquals(1_000L, client().resumeMs(provider(), "inconnu", 1_000L)) // injoignable : position locale
    }

    @Test fun rapports_envoyesAuServeur_avecJeton() = runBlocking {
        val c = client()
        c.report(provider(), MediaServer.Report.START, "m1", 0, false)
        c.report(provider(), MediaServer.Report.PROGRESS, "m1", 90_000, false)
        c.report(provider(), MediaServer.Report.STOP, "m1", 120_000, true)
        val paths = seen.map { it.requestUrl!!.encodedPath }.filter { it.startsWith("/Sessions") }
        assertEquals(listOf("/Sessions/Playing", "/Sessions/Playing/Progress", "/Sessions/Playing/Stopped"), paths)
        val stop = seen.last()
        assertEquals("POST", stop.method)
        assertEquals(token, stop.getHeader("X-Emby-Token"))
        assertTrue(stop.body.readUtf8().contains("\"PositionTicks\":1200000000"))
    }

    @Test fun rapport_serveurHorsLigne_neLevePas() = runBlocking {
        val p = provider().copy(baseUrl = "http://127.0.0.1:1")
        client().report(p, MediaServer.Report.PROGRESS, "m1", 1000, false)   // aucune exception
    }

    @Test fun jetonRefuse_erreur401_sansAdresseDansLeMessage() = runBlocking {
        try { client().libraries(provider("perime")); fail("doit échouer") } catch (e: HttpStatusException) {
            assertEquals(401, e.code)
            assertFalse((e.message ?: "").contains("perime")); assertFalse((e.message ?: "").contains("http"))
        }
    }
}
