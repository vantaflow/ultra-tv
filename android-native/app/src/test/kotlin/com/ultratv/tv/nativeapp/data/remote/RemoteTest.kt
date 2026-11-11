package com.ultratv.tv.nativeapp.data.remote

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.repo.SearchResults
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.Socket

private class FakeApi : RemoteApi {
    var hits = listOf(RemoteHit("movie", 7, "Le Film", 2020))
    val opened = mutableListOf<Pair<String, Long>>()
    val controls = mutableListOf<RemoteAction>()
    var openOk = true
    override suspend fun search(query: String, kind: String) = hits
    override suspend fun open(kind: String, id: Long): Boolean { opened += kind to id; return openOk }
    override fun control(action: RemoteAction) { controls += action }
    override fun now() = RemoteNow("Titre en cours", "movie", true, true)
}

class RemoteTest {
    private val token = "a".repeat(32)
    private val api = FakeApi()
    private val router = RemoteRouter({ token }, api) { "fr" }

    private fun req(method: String, path: String, query: Map<String, String> = emptyMap(), headers: Map<String, String> = emptyMap(), body: String = "") =
        HttpRequest(method, path, query, headers, body)
    private fun call(r: HttpRequest) = runBlocking { router.handle(r) }
    private val key get() = mapOf("x-key" to token)

    // ── Jeton ─────────────────────────────────────────────────────────────
    @Test fun sansJeton_toutesLesRoutes_donnent401() {
        for ((m, p) in listOf("GET" to "/r", "GET" to "/api/search", "GET" to "/api/now", "POST" to "/api/open", "POST" to "/api/control", "GET" to "/nimporte/quoi", "GET" to "/")) {
            val r = call(req(m, p))
            assertEquals("$m $p", 401, r.status)
            assertFalse(String(r.body).contains("Ultra"))
        }
        assertTrue(api.opened.isEmpty() && api.controls.isEmpty())
    }

    @Test fun mauvaisJeton_401_memeLongueurOuVide() {
        assertEquals(401, call(req("GET", "/r", query = mapOf("k" to "b".repeat(32)))).status)
        assertEquals(401, call(req("GET", "/r", query = mapOf("k" to "a".repeat(31)))).status)
        assertEquals(401, call(req("GET", "/r", query = mapOf("k" to ""))).status)
        assertEquals(401, call(req("POST", "/api/control", headers = mapOf("x-key" to "zz"), body = """{"cmd":"ok"}""")).status)
        assertTrue(api.controls.isEmpty())
    }

    @Test fun jetonAccepteEnParametreOuEnEnTete() {
        assertEquals(200, call(req("GET", "/r", query = mapOf("k" to token))).status)
        assertEquals(200, call(req("GET", "/api/now", headers = key)).status)
    }

    @Test fun jetonRegenere_l_ancien_ne_marche_plus() {
        var t = token
        val rt = RemoteRouter({ t }, api) { "en" }
        assertEquals(200, runBlocking { rt.handle(req("GET", "/api/now", headers = key)) }.status)
        t = "c".repeat(32)
        assertEquals(401, runBlocking { rt.handle(req("GET", "/api/now", headers = key)) }.status)
    }

    // ── Routage ───────────────────────────────────────────────────────────
    @Test fun routeInconnue_avecJeton_404_etMauvaiseMethode_405() {
        assertEquals(404, call(req("GET", "/secret", headers = key)).status)
        assertEquals(405, call(req("POST", "/r", headers = key)).status)
        assertEquals(405, call(req("GET", "/api/control", headers = key)).status)
        assertEquals(405, call(req("GET", "/api/open", headers = key)).status)
    }

    @Test fun page_estServieEnHtml_dansLaLangueDeLAppli_sansRessourceExterne() {
        val r = call(req("GET", "/r", headers = key))
        assertEquals(200, r.status)
        assertTrue(r.contentType.startsWith("text/html"))
        val html = String(r.body)
        assertTrue(html.contains("""lang="fr""""))
        assertFalse(html.contains("src=\"http"))
        assertTrue(r.extraHeaders["Content-Security-Policy"]!!.contains("default-src 'none'"))
        val ar = RemotePage.html("ar"); assertTrue(ar.contains("""dir="rtl""""))
        assertTrue(RemotePage.html("xx").contains("""lang="en""""))
    }

    @Test fun recherche_court_vide_ouMauvaisType_400() {
        assertEquals(0, JSONObject(String(call(req("GET", "/api/search", query = mapOf("q" to "a", "t" to "movie"), headers = key)).body)).getJSONArray("results").length())
        assertEquals(400, call(req("GET", "/api/search", query = mapOf("q" to "abc", "t" to "tout"), headers = key)).status)
        val ok = call(req("GET", "/api/search", query = mapOf("q" to "film", "t" to "movie"), headers = key))
        val first = JSONObject(String(ok.body)).getJSONArray("results").getJSONObject(0)
        assertEquals(7L, first.getLong("id")); assertEquals("Le Film", first.getString("title")); assertEquals(2020, first.getInt("year"))
    }

    @Test fun ouvrir_valideLaCibleEtRenvoie404SiIntrouvable() {
        assertEquals(200, call(req("POST", "/api/open", headers = key, body = """{"kind":"series","id":3}""")).status)
        assertEquals("series" to 3L, api.opened.single())
        assertEquals(400, call(req("POST", "/api/open", headers = key, body = """{"kind":"url","id":3}""")).status)
        assertEquals(400, call(req("POST", "/api/open", headers = key, body = "pas du json")).status)
        api.openOk = false
        assertEquals(404, call(req("POST", "/api/open", headers = key, body = """{"kind":"live","id":9}""")).status)
    }

    @Test fun controle_traduitLesCommandes_etRefuseLeReste() {
        assertEquals(200, call(req("POST", "/api/control", headers = key, body = """{"cmd":"back30"}""")).status)
        assertEquals(RemoteAction.Seek(-30_000), api.controls.single())
        assertEquals(400, call(req("POST", "/api/control", headers = key, body = """{"cmd":"rm -rf"}""")).status)
        assertEquals(400, call(req("POST", "/api/control", headers = key, body = "{}")).status)
        assertEquals(1, api.controls.size)
    }

    @Test fun now_exposeLeTitreEnCours() {
        val j = JSONObject(String(call(req("GET", "/api/now", headers = key)).body))
        assertEquals("Titre en cours", j.getString("title")); assertTrue(j.getBoolean("playing")); assertTrue(j.getBoolean("player"))
    }

    // ── Commandes → actions ───────────────────────────────────────────────
    @Test fun commandes_traduites_en_actions() {
        assertEquals(RemoteAction.PlayPause, RemoteCommands.parse("playpause"))
        assertEquals(RemoteAction.Seek(-30_000), RemoteCommands.parse("back30"))
        assertEquals(RemoteAction.Seek(-10_000), RemoteCommands.parse("back10"))
        assertEquals(RemoteAction.Seek(10_000), RemoteCommands.parse("fwd10"))
        assertEquals(RemoteAction.Seek(30_000), RemoteCommands.parse("fwd30"))
        assertEquals(RemoteAction.Zap(false), RemoteCommands.parse("prev"))
        assertEquals(RemoteAction.Zap(true), RemoteCommands.parse("next"))
        assertEquals(RemoteAction.Key(RemoteKey.UP), RemoteCommands.parse("up"))
        assertEquals(RemoteAction.Key(RemoteKey.DOWN), RemoteCommands.parse("down"))
        assertEquals(RemoteAction.Key(RemoteKey.LEFT), RemoteCommands.parse("left"))
        assertEquals(RemoteAction.Key(RemoteKey.RIGHT), RemoteCommands.parse("right"))
        assertEquals(RemoteAction.Key(RemoteKey.OK), RemoteCommands.parse(" OK "))
        assertEquals(RemoteAction.Key(RemoteKey.BACK), RemoteCommands.parse("back"))
        assertNull(RemoteCommands.parse("home")); assertNull(RemoteCommands.parse("")); assertNull(RemoteCommands.parse(null))
    }

    @Test fun bus_transmetEtIgnoreSansAbonne() = runBlocking {
        val bus = RemoteBus()
        bus.send(RemoteAction.PlayPause)       // aucun abonné : perdu, sans erreur
        val got = mutableListOf<RemoteAction>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { bus.actions.collect { got += it } }
        bus.send(RemoteAction.Zap(true))
        job.cancel()
        assertEquals(listOf<RemoteAction>(RemoteAction.Zap(true)), got)
    }

    // ── JSON de recherche : jamais d'URL de flux ──────────────────────────
    @Test fun jsonDeRecherche_necontientAucuneUrlNiIdentifiantIptv() {
        val secret = "http://iptv.example.net:8080/movie/monuser/monmotdepasse/123.mkv"
        val r = SearchResults(
            channels = listOf(ChannelEntity(1, 9, "rid-ch", "FR: TF1 HD", "http://logo.example/x.png", "5", secret)),
            movies = listOf(MovieEntity(2, 9, "rid-m", "Le Film (2020)", "http://img.example/p.jpg", "3", secret, "mkv", 2020, 7.0, "plot")),
            series = listOf(SeriesEntity(3, 9, "rid-s", "La Série", "http://img.example/s.jpg", "4", 2019, 8.0, "plot")),
        )
        for (kind in RemoteKinds.all) {
            val hits = RemoteHits.from(r, kind)
            assertEquals(kind, 1, hits.size)
            val json = RemoteRouter.searchJson(hits).toString()
            for (bad in listOf("http", "iptv", "monuser", "monmotdepasse", "example", "rid-", "mkv", ".jpg", ".png", "9080")) assertFalse("$kind contient $bad : $json", json.contains(bad))
        }
        val o = RemoteRouter.searchJson(RemoteHits.from(r, "movie")).getJSONArray("results").getJSONObject(0)
        assertEquals(setOf("kind", "id", "title", "year"), o.keys().asSequence().toSet())
        assertEquals(2L, o.getLong("id"))
    }

    @Test fun recherche_ecarteSeparateursEtLimiteLeNombre() {
        val many = (1..50).map { SeriesEntity(it.toLong(), 1, "r$it", "Série $it", null, null, null, null, null) }
        assertEquals(RemoteHits.LIMIT, RemoteHits.from(SearchResults(series = many), "series").size)
        val sep = ChannelEntity(1, 1, "x", "##### SPORT #####", null, null, "http://u/1")
        assertTrue(RemoteHits.from(SearchResults(channels = listOf(sep)), "live").isEmpty())
    }

    // ── Requête HTTP / réseau local ───────────────────────────────────────
    @Test fun analyse_requete_http() {
        val raw = "POST /api/open?k=abc%20d&x=1 HTTP/1.1\r\nHost: h\r\nX-Key: tok\r\nContent-Length: 5\r\n\r\nhello".toByteArray()
        val r = HttpParser.read(ByteArrayInputStream(raw))
        assertEquals("POST", r.method); assertEquals("/api/open", r.path); assertEquals("abc d", r.query["k"]); assertEquals("tok", r.headers["x-key"]); assertEquals("hello", r.body)
    }

    @Test fun requete_trop_grosse_ou_tronquee_refusee() {
        val big = "POST /x HTTP/1.1\r\nContent-Length: 999999\r\n\r\n".toByteArray()
        assertEquals(413, runCatching { HttpParser.read(ByteArrayInputStream(big)) }.exceptionOrNull().let { (it as HttpParser.Bad).status })
        val cut = "GET /x HTTP/1.1\r\nHost".toByteArray()
        assertEquals(400, runCatching { HttpParser.read(ByteArrayInputStream(cut)) }.exceptionOrNull().let { (it as HttpParser.Bad).status })
        val huge = ("GET /x HTTP/1.1\r\nX: " + "a".repeat(20_000) + "\r\n\r\n").toByteArray()
        assertEquals(413, runCatching { HttpParser.read(ByteArrayInputStream(huge)) }.exceptionOrNull().let { (it as HttpParser.Bad).status })
    }

    @Test fun reseauLocal_clientsEtAdresseDEcoute() {
        fun ip(s: String) = InetAddress.getByName(s)
        assertTrue(RemoteNet.isLocalPeer(ip("192.168.1.20"))); assertTrue(RemoteNet.isLocalPeer(ip("10.0.0.3")))
        assertTrue(RemoteNet.isLocalPeer(ip("172.16.5.1"))); assertTrue(RemoteNet.isLocalPeer(ip("127.0.0.1")))
        assertTrue(RemoteNet.isLocalPeer(ip("fd12:3456::1"))); assertTrue(RemoteNet.isLocalPeer(ip("fe80::1")))
        assertFalse(RemoteNet.isLocalPeer(ip("8.8.8.8"))); assertFalse(RemoteNet.isLocalPeer(ip("172.32.0.1")))
        assertFalse(RemoteNet.isLocalPeer(ip("2001:4860:4860::8888")))
        assertEquals("192.168.1.5", RemoteNet.pickLocalAddress(listOf(ip("8.8.8.8"), ip("fe80::1"), ip("192.168.1.5")))?.hostAddress)
        assertNull(RemoteNet.pickLocalAddress(listOf(ip("8.8.8.8"), ip("127.0.0.1"))))
    }

    @Test fun jeton_aleatoire_de_128_bits_et_langue_de_la_page() {
        val a = RemoteNet.newToken(); val b = RemoteNet.newToken()
        assertEquals(32, a.length); assertTrue(a.all { it in "0123456789abcdef" }); assertNotEquals(a, b)
        assertEquals("http://192.168.1.5:8766/r?k=$a", RemoteNet.url("192.168.1.5", 8766, a))
        assertEquals("es", RemoteNet.pageLang("es", "fr")); assertEquals("fr", RemoteNet.pageLang("system", "fr")); assertEquals("en", RemoteNet.pageLang("system", "de"))
    }

    // ── Serveur réel (boucle locale) ──────────────────────────────────────
    private fun http(port: Int, raw: String): String = Socket(InetAddress.getByName("127.0.0.1"), port).use { s ->
        s.soTimeout = 5_000
        s.getOutputStream().write(raw.toByteArray()); s.getOutputStream().flush()
        String(s.getInputStream().readBytes())
    }

    @Test fun serveur_reel_401_sansJeton_200_avecJeton() {
        RemoteServer(router, InetAddress.getByName("127.0.0.1"), firstPort = 18766).use { srv ->
            srv.start()
            val none = http(srv.port, "GET /r HTTP/1.1\r\nHost: x\r\n\r\n")
            assertTrue(none, none.startsWith("HTTP/1.1 401"))
            val ok = http(srv.port, "GET /api/now?k=$token HTTP/1.1\r\nHost: x\r\n\r\n")
            assertTrue(ok, ok.startsWith("HTTP/1.1 200") && ok.contains("Titre en cours"))
            val body = """{"cmd":"fwd10"}"""
            val post = http(srv.port, "POST /api/control HTTP/1.1\r\nX-Key: $token\r\nContent-Length: ${body.length}\r\n\r\n$body")
            assertTrue(post, post.startsWith("HTTP/1.1 200"))
            assertEquals(RemoteAction.Seek(10_000), api.controls.last())
        }
    }
}
