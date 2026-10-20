package com.ultratv.tv.nativeapp.data.stalker

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client Stalker contre un faux portail (MockWebServer) : sonde du point d'entrée, en-têtes MAG, jeton renouvelé,
 * genres, chaînes (flux + pagination), VOD (films / séries), épisodes, create_link, programme court, abonnement.
 * Réponses enregistrées sur le modèle des portails Ministra (forme `{"js": …}`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StalkerClientTest {
    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private val handshakes = AtomicInteger()
    /** Réponses par action (« type/action ») ; « refuse » renvoie « Authorization failed. » au premier appel. */
    private val routes = HashMap<String, (RecordedRequest) -> MockResponse>()
    private var refuseOnce = mutableSetOf<String>()
    private val mac = "00:1A:79:AB:CD:EF"

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "text/javascript").setBody(body)

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                val url = request.requestUrl!!
                // Seul /stalker_portal/server/load.php répond : les autres points d'entrée sondés sont des 404.
                if (url.encodedPath != "/stalker_portal/server/load.php") return MockResponse().setResponseCode(404)
                val key = "${url.queryParameter("type")}/${url.queryParameter("action")}"
                if (key == "stb/handshake") {
                    val n = handshakes.incrementAndGet()
                    return json("""{"js":{"token":"TOKEN$n","random":"x"}}""")
                }
                if (request.getHeader("Authorization").isNullOrEmpty()) return json("Authorization failed.")
                if (refuseOnce.remove(key)) return json("Authorization failed.")
                return routes[key]?.invoke(request) ?: json("""{"js":[]}""")
            }
        }
        server.start()
        routes["stb/get_profile"] = { json("""{"js":{"id":"1","status":0,"blocked":"0"}}""") }
    }

    @After fun tearDown() { server.shutdown() }

    private fun portal() = server.url("/stalker_portal/c/").toString()
    private fun provider(password: String = "") = ProviderEntity(id = 5, name = "P", kind = "STALKER", baseUrl = portal().trimEnd('/'), username = mac, password = password)
    private fun client(store: StalkerEndpointStore = StalkerEndpointStore.inMemory()) = StalkerClient(OkHttpClient(), store)

    @Test fun sonde_trouveLoadPhp_memoriseEtEnvoieLesEnTetesMag() = runBlocking {
        val store = StalkerEndpointStore.inMemory()
        val api = client(store).probe(StalkerClient.Account(portal(), mac))
        assertTrue(api.endsWith("/stalker_portal/server/load.php"))
        assertEquals(api, store.get(portal()))
        val hs = seen.first { it.requestUrl!!.queryParameter("action") == "handshake" && it.requestUrl!!.encodedPath.endsWith("load.php") }
        assertEquals(StalkerDevice.USER_AGENT, hs.getHeader("User-Agent"))
        assertEquals("Model: MAG250; Link: WiFi", hs.getHeader("X-User-Agent"))
        assertEquals("mac=00%3A1A%3A79%3AAB%3ACD%3AEF; stb_lang=en; timezone=UTC", hs.getHeader("Cookie"))
        assertEquals(portal(), hs.getHeader("Referer"))
        assertEquals("1-xml", hs.requestUrl!!.queryParameter("JsHttpRequest"))
        // Profil envoyé avec le jeton et l'identité dérivée de la MAC.
        val prof = seen.first { it.requestUrl!!.queryParameter("action") == "get_profile" }
        assertEquals("Bearer TOKEN1", prof.getHeader("Authorization"))
        assertEquals(StalkerDevice(mac).sn, prof.requestUrl!!.queryParameter("sn"))
        // Les points d'entrée précédents ont été essayés dans l'ordre (404).
        assertTrue(seen.any { it.requestUrl!!.encodedPath == "/stalker_portal/c/portal.php" })
    }

    @Test fun pointDEntreeMemorise_plusDeSondeEnsuite() = runBlocking {
        val store = StalkerEndpointStore.inMemory()
        client(store).probe(StalkerClient.Account(portal(), mac))
        seen.clear()
        client(store).genres(provider())
        assertFalse(seen.any { it.requestUrl!!.encodedPath.endsWith("portal.php") })
    }

    @Test fun jetonRefuse_nouvellePoigneeDeMain_uneSeuleFois() = runBlocking {
        routes["itv/get_genres"] = { json("""{"js":[{"id":"*","title":"All"},{"id":"10","title":"FR| News"},{"id":"11","title":"Sport"}]}""") }
        val c = client()
        refuseOnce += "itv/get_genres"
        val genres = c.genres(provider())
        assertEquals(listOf("10", "11"), genres.map { it.remoteId })
        assertEquals("LIVE", genres.first().kind)
        assertEquals(2, handshakes.get())
        // Appel suivant : même jeton, pas de nouvelle poignée de main.
        c.genres(provider())
        assertEquals(2, handshakes.get())
    }

    @Test fun refusPersistant_echecClasseIdentifiantsRefuses() = runBlocking {
        routes["itv/get_genres"] = { json("Authorization failed.") }
        try { client().genres(provider()); fail("doit échouer") } catch (e: com.ultratv.tv.nativeapp.data.net.HttpStatusException) {
            assertEquals(com.ultratv.tv.nativeapp.data.net.SyncErrorKind.UNAUTHORIZED, com.ultratv.tv.nativeapp.data.net.NetErrors.classify(e))
        }
        assertEquals(2, handshakes.get())
    }

    @Test fun identifiantDuPortail_doAuthEnvoye() = runBlocking {
        routes["stb/do_auth"] = { json("""{"js":true}""") }
        client().probe(StalkerClient.Account(portal(), mac, "jean", "pw"))
        val auth = seen.first { it.requestUrl!!.queryParameter("action") == "do_auth" }
        assertEquals("jean", auth.requestUrl!!.queryParameter("login"))
        assertEquals("pw", auth.requestUrl!!.queryParameter("password"))
    }

    @Test fun identifiantRefuse_doAuthFalse_echec() = runBlocking {
        routes["stb/do_auth"] = { json("""{"js":false}""") }
        try { client().probe(StalkerClient.Account(portal(), mac, "jean", "faux")); fail("doit échouer") } catch (_: Exception) {}
    }

    @Test fun toutesLesChaines_enFlux_avecLogosEtCommande() = runBlocking {
        routes["itv/get_all_channels"] = { json("""{"js":{"total_items":2,"max_page_items":14,"data":[
            {"id":"1234","name":"FR| TF1 HD","number":"1","cmd":"ffmpeg http://localhost/ch/1234_","tv_genre_id":"10","logo":"/stalker_portal/misc/logos/320/1234.png","xmltv_id":"tf1.fr"},
            {"id":"1235","name":"Sport 1","number":"2","cmd":"ffmpeg http://localhost/ch/1235_","tv_genre_id":"11","logo":""},
            {"id":"bad"}
        ]}}""") }
        val p = provider()
        val rows = client().withAllChannels(p) { it.toList() }
        assertEquals(listOf("1234", "1235"), rows.map { it.remoteId })
        val tf1 = rows.first()
        assertEquals("10", tf1.categoryId); assertEquals(1, tf1.num); assertEquals("tf1.fr", tf1.epgChannelId)
        assertTrue(tf1.logo!!.endsWith("/stalker_portal/misc/logos/320/1234.png"))
        val link = StalkerLink.parse(tf1.streamUrl)!!
        assertEquals(5L, link.providerId); assertEquals("itv", link.type); assertEquals("ffmpeg http://localhost/ch/1234_", link.cmd)
        assertNull(rows[1].logo)
    }

    @Test fun toutesLesChaines_indisponibles_replisPagine() = runBlocking {
        routes["itv/get_all_channels"] = { MockResponse().setResponseCode(500) }
        routes["itv/get_ordered_list"] = { r ->
            val page = r.requestUrl!!.queryParameter("p")
            assertEquals("*", r.requestUrl!!.queryParameter("genre"))
            when (page) {
                "1" -> json("""{"js":{"total_items":"3","max_page_items":"2","data":[{"id":"1","name":"A","cmd":"c1"},{"id":"2","name":"B","cmd":"c2"}]}}""")
                "2" -> json("""{"js":{"total_items":"3","max_page_items":"2","data":[{"id":"3","name":"C","cmd":"c3"}]}}""")
                else -> json("""{"js":{"total_items":"3","max_page_items":"2","data":[]}}""")
            }
        }
        val rows = client().withAllChannels(provider()) { it.toList() }
        assertEquals(listOf("1", "2", "3"), rows.map { it.remoteId })
        val pages = seen.filter { it.requestUrl!!.queryParameter("action") == "get_ordered_list" }.map { it.requestUrl!!.queryParameter("p") }
        assertEquals(setOf("1", "2"), pages.toSet())
    }

    @Test fun chainesDUnGenre_pagination() = runBlocking {
        routes["itv/get_ordered_list"] = { r ->
            assertEquals("11", r.requestUrl!!.queryParameter("genre"))
            val n = r.requestUrl!!.queryParameter("p")!!.toInt()
            val items = (1..3).map { i -> (n - 1) * 3 + i }.filter { it <= 7 }.joinToString(",") { """{"id":"$it","name":"Ch $it","cmd":"c$it","tv_genre_id":"11"}""" }
            json("""{"js":{"total_items":7,"max_page_items":3,"data":[$items]}}""")
        }
        val rows = client().channelsOfGenre(provider(), "11")
        assertEquals((1..7).map { it.toString() }, rows.map { it.remoteId })
    }

    @Test fun vod_filmsEtSeriesSepares_puisEpisodesDuTableauSeries() = runBlocking {
        routes["vod/get_categories"] = { json("""{"js":[{"id":"*","title":"All"},{"id":"20","title":"Films"},{"id":"21","title":"Séries"}]}""") }
        routes["vod/get_ordered_list"] = { r ->
            if (r.requestUrl!!.queryParameter("movie_id") != null) json("""{"js":{"total_items":0,"max_page_items":14,"data":[]}}""")
            else json("""{"js":{"total_items":2,"max_page_items":14,"data":[
                {"id":"501","name":"Inception (2010)","cmd":"eyJtb3ZpZSI6NTAxfQ==","year":"2010","rating_imdb":"8.8","screenshot_uri":"http://img/501.jpg","added":"2024-05-01 10:00:00","time":"148","description":"Rêves."},
                {"id":"777","name":"Dark","cmd":"eyJzZXJpZXMiOjc3N30=","is_series":"1","series":[1,2,3]}
            ]}}""")
        }
        val c = client()
        val p = provider()
        assertEquals(listOf("20", "21"), c.vodCategories(p).map { it.remoteId })
        val split = c.vodOfCategory(p, "20")
        val m = split.movies.single()
        assertEquals("501", m.remoteId); assertEquals(2010, m.year); assertEquals(8.8, m.rating!!, 0.001); assertEquals("20", m.categoryId)
        assertEquals("148 min", m.duration); assertEquals("http://img/501.jpg", m.poster)
        assertEquals("vod", StalkerLink.parse(m.streamUrl)!!.type)
        assertEquals(1714557600L, m.addedKey)
        val s = split.series.single()
        assertEquals("777", s.series.remoteId); assertEquals(listOf(1, 2, 3), s.episodes)
        val eps = c.episodes(p, s.series.copy(id = 42))
        assertEquals(listOf(1, 2, 3), eps.map { it.episode })
        assertTrue(eps.all { it.seriesId == 42L && it.season == 1 })
        val l = StalkerLink.parse(eps[1].streamUrl)!!
        assertEquals("eyJzZXJpZXMiOjc3N30=", l.cmd); assertEquals(2, l.series)
    }

    @Test fun episodes_saisonsDuPortailRecent() = runBlocking {
        routes["vod/get_ordered_list"] = { r ->
            val season = r.requestUrl!!.queryParameter("season_id")
            when (season) {
                "0" -> json("""{"js":{"total_items":2,"max_page_items":14,"data":[{"id":"s1","name":"Season 1","is_season":true,"cmd":"x"},{"id":"s2","name":"Saison 2","is_season":"1","cmd":"y"}]}}""")
                "s1" -> json("""{"js":{"total_items":1,"max_page_items":14,"data":[{"id":"e1","name":"Pilote","series_number":"1","is_episode":true,"cmd":"ep-cmd-1"}]}}""")
                else -> json("""{"js":{"total_items":2,"max_page_items":14,"data":[{"id":"e3","series_number":"1","cmd":"ep-cmd-3"},{"id":"e4","series_number":"2","cmd":"ep-cmd-4"}]}}""")
            }
        }
        val s = SeriesEntity(id = 9, providerId = 5, remoteId = "900", name = "Show", poster = null, categoryId = "21", year = null, rating = null, plot = null)
        val eps = client().episodes(provider(), s)
        assertEquals(listOf(1 to 1, 2 to 1, 2 to 2), eps.map { it.season to it.episode })
        assertEquals("Pilote", eps[0].title)
        assertEquals("ep-cmd-4", StalkerLink.parse(eps[2].streamUrl)!!.cmd)
    }

    @Test fun createLink_prefixeRetire_etResolveurAuMomentDeLaLecture() = runBlocking {
        routes["itv/create_link"] = { r ->
            assertEquals("ffmpeg http://localhost/ch/1234_", r.requestUrl!!.queryParameter("cmd"))
            json("""{"js":{"id":"1234","cmd":"ffmpeg http://real.host/play/live.php?mac=x&stream=1234&play_token=ABC"}}""")
        }
        routes["vod/create_link"] = { r ->
            assertEquals("3", r.requestUrl!!.queryParameter("series"))
            json("""{"js":{"cmd":"auto http://real.host/vod/abc.mkv"}}""")
        }
        val c = client()
        val p = provider()
        assertEquals("http://real.host/play/live.php?mac=x&stream=1234&play_token=ABC", c.createLink(p, "itv", "ffmpeg http://localhost/ch/1234_"))
        assertEquals("http://real.host/vod/abc.mkv", c.createLink(p, "vod", "eyJ9", 3))
    }

    @Test fun createLink_echec_repliSurCommandeDirecte() = runBlocking {
        routes["itv/create_link"] = { MockResponse().setResponseCode(500) }
        assertEquals("http://real.host/x.ts", client().createLink(provider(), "itv", "ffmpeg http://real.host/x.ts"))
        try { client().createLink(provider(), "itv", "ffmpeg http://localhost/ch/1_"); fail("doit échouer") } catch (_: Exception) {}
    }

    @Test fun programmeCourt_horodatages() = runBlocking {
        routes["itv/get_short_epg"] = { r ->
            assertEquals("1234", r.requestUrl!!.queryParameter("ch_id"))
            json("""{"js":[{"id":"1","ch_id":"1234","name":"Journal","descr":"Infos","start_timestamp":1767225600,"stop_timestamp":"1767227400"},{"name":"Sans heure"}]}""")
        }
        val rows = client().shortEpg(provider(), "1234", 77)
        val e = rows.single()
        assertEquals("Journal", e.title); assertEquals(77L, e.channelId)
        assertEquals(1767225600000L, e.startMs); assertEquals(1767227400000L, e.endMs)
    }

    @Test fun abonnement_dateDeFinLue() = runBlocking {
        routes["account_info/get_main_info"] = { json("""{"js":{"mac":"00:1A:79:AB:CD:EF","phone":"","end_date":"2099-12-31 00:00:00"}}""") }
        val a = client().fetchAccount(provider())!!
        assertEquals("Active", a.status)
        assertEquals(4102358400000L, a.expiresAt)
        assertNull(a.maxConnections)
    }

    @Test fun abonnement_expire_etBloque() {
        val past = StalkerParse.account(mapOf("end_date" to "2020-01-01"), null, now = 1_700_000_000_000L)!!
        assertEquals("Expired", past.status)
        val blocked = StalkerParse.account(mapOf("phone" to "Expire: 2099-01-01"), mapOf("blocked" to "1"))!!
        assertEquals("Disabled", blocked.status)
        assertEquals(4070908800000L, blocked.expiresAt)
        assertNull(StalkerParse.account(null, null))
    }

    @Test fun resolveur_adressesOrdinairesInchangees() = runBlocking {
        val resolver = DefaultStreamUrlResolver(FakeDao.of(provider()), client())
        assertEquals("http://h/live/u/p/1.ts", resolver.resolve("http://h/live/u/p/1.ts"))
    }

    @Test fun motDePasseEmpaquete_relu() {
        val a = client().accountOf(provider(StalkerCreds.pack("jean", "pw")))
        assertEquals("jean", a.login); assertEquals("pw", a.password); assertEquals(mac, a.mac)
        assertFalse(a.toString().contains("AB:CD"))
    }
}

/** ProviderDao minimal pour le résolveur (aucune base). */
private object FakeDao {
    fun of(p: ProviderEntity): com.ultratv.tv.nativeapp.data.db.ProviderDao {
        val raw = java.lang.reflect.Proxy.newProxyInstance(
            com.ultratv.tv.nativeapp.data.db.ProviderRawDao::class.java.classLoader,
            arrayOf(com.ultratv.tv.nativeapp.data.db.ProviderRawDao::class.java),
        ) { _, m, _ -> if (m.name == "byId") p else null } as com.ultratv.tv.nativeapp.data.db.ProviderRawDao
        return com.ultratv.tv.nativeapp.data.db.ProviderDao(raw)
    }
}
