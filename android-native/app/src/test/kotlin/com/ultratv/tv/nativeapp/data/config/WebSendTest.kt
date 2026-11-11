package com.ultratv.tv.nativeapp.data.config

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WebSendLogicTest {
    @Test fun `sendUrl encode le code sans tiret en majuscules`() =
        assertEquals("https://w.test/envoyer?c=K7Q2M9", WebSendLogic.sendUrl("https://w.test/", "k7q-2m9"))

    @Test fun `shortAddress donne hote et chemin sans schema`() {
        assertEquals("w.test/envoyer", WebSendLogic.shortAddress("https://w.test/"))
        assertEquals("w.test:8787/envoyer", WebSendLogic.shortAddress("http://w.test:8787"))
        assertEquals("", WebSendLogic.shortAddress(""))
    }

    @Test fun `groupCode coupe en deux groupes de trois`() {
        assertEquals("K7Q-2M9", WebSendLogic.groupCode("K7Q2M9"))
        assertEquals("K7Q-2M9", WebSendLogic.groupCode("K7Q-2M9"))
        assertEquals("AB", WebSendLogic.groupCode("AB"))
    }

    private fun src(vararg kv: Pair<String, String>) = JSONObject().also { o -> kv.forEach { (k, v) -> o.put(k, v) } }

    @Test fun `parseSource guide XMLTV valide`() {
        val s = WebSendLogic.parseSource(src("kind" to "GUIDE", "url" to "https://g.test/epg.xml.gz?t=1"))!!
        assertEquals(WebSource("GUIDE", "", "https://g.test/epg.xml.gz?t=1", "", "", ""), s)
    }

    @Test fun `parseSource guide refuse ftp et adresse sans extension xml`() {
        assertNull(WebSendLogic.parseSource(src("kind" to "GUIDE", "url" to "ftp://g.test/epg.xml")))
        assertNull(WebSendLogic.parseSource(src("kind" to "GUIDE", "url" to "https://g.test/epg")))
    }

    @Test fun `parseSource Xtream valide`() {
        val s = WebSendLogic.parseSource(src("kind" to "XTREAM", "name" to " Salon ", "url" to "http://s.test:8080", "username" to "u", "password" to "p"))!!
        assertEquals(WebSource("XTREAM", "Salon", "http://s.test:8080", "u", "p", ""), s)
    }

    @Test fun `parseSource M3U sans nom`() =
        assertEquals("M3U", WebSendLogic.parseSource(src("kind" to "M3U", "url" to "https://x.test/l.m3u"))!!.kind)

    @Test fun `parseSource Stalker normalise la MAC`() =
        assertEquals("00:1A:79:AB:CD:EF", WebSendLogic.parseSource(src("kind" to "STALKER", "url" to "http://p.test/c/", "mac" to "00-1a-79-ab-cd-ef"))!!.mac)

    @Test fun `parseSource refuse les sources invalides`() {
        assertNull(WebSendLogic.parseSource(null))
        assertNull(WebSendLogic.parseSource(src("kind" to "FTP", "url" to "http://x.test")))
        assertNull(WebSendLogic.parseSource(src("kind" to "M3U", "url" to "ftp://x.test/l.m3u")))
        assertNull(WebSendLogic.parseSource(src("kind" to "M3U", "url" to "javascript:alert(1)")))
        assertNull(WebSendLogic.parseSource(src("kind" to "XTREAM", "url" to "http://x.test", "username" to "u", "password" to "")))
        assertNull(WebSendLogic.parseSource(src("kind" to "STALKER", "url" to "http://p.test/c/", "mac" to "zz")))
    }

    @Test fun `toString d'une source ne montre jamais les identifiants`() {
        val s = WebSource("XTREAM", "n", "http://s.test", "alice", "mot-de-passe-secret", "")
        assertFalse(s.toString().contains("alice")); assertFalse(s.toString().contains("secret")); assertFalse(s.toString().contains("s.test"))
    }
}

class WebSendClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebSendClient
    private val base get() = server.url("/").toString().trimEnd('/')

    @Before fun setUp() { server = MockWebServer().apply { start() }; client = WebSendClient(OkHttpClient()) }
    @After fun tearDown() { server.shutdown() }

    @Test fun `start envoie l'identifiant d'installation seulement s'il est fourni`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"K7Q2M9","secret":"sec","expiresIn":900,"interval":3}"""))
        server.enqueue(MockResponse().setBody("""{"code":"K7Q2M9","secret":"sec","expiresIn":900,"interval":3}"""))
        assertEquals(WebSendSession("K7Q2M9", "sec", 900, 3), client.start(base, "install-1234567890abcdef"))
        val r1 = server.takeRequest()
        assertEquals("/api/envoi/start", r1.path)
        assertTrue(r1.body.readUtf8().contains("install-1234567890abcdef"))
        client.start(base, null)
        assertFalse(server.takeRequest().body.readUtf8().contains("install"))
    }

    @Test fun `poll 202 attend, 404 est termine, 200 livre la source`() = runBlocking {
        val s = WebSendSession("K7Q2M9", "sec", 900, 3)
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"status":"pending"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}"""))
        server.enqueue(MockResponse().setBody("""{"status":"ready","provider":{"kind":"M3U","name":"","url":"http://x.test/l.m3u","username":"","password":""}}"""))
        assertEquals(WebPollResult.Pending, client.poll(base, s))
        assertEquals(WebPollResult.Gone, client.poll(base, s))
        val got = client.poll(base, s) as WebPollResult.Received
        assertEquals("http://x.test/l.m3u", got.source.url)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"secret\":\"sec\""))
    }

    @Test fun `poll avec une source invalide est ignore comme un code termine`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"ready","provider":{"kind":"M3U","url":"ftp://x"}}"""))
        assertEquals(WebPollResult.Gone, client.poll(base, WebSendSession("A", "b", 900, 3)))
    }

    @Test fun `429 expose le delai Retry-After`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "42"))
        try { client.start(base, null); fail() } catch (e: RateLimitedException) { assertEquals(42, e.retryAfterSec) }
    }
}

class WebSendFlowTest {
    private lateinit var server: MockWebServer
    private val base get() = server.url("/").toString().trimEnd('/')
    private val start = """{"code":"K7Q2M9","secret":"sec","expiresIn":900,"interval":3}"""

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun flowOf() = WebSend(WebSendClient(OkHttpClient())).also { it.sleeper = { } }

    @Test fun `parcours nominal, code puis attente puis source recue`() = runBlocking {
        server.enqueue(MockResponse().setBody(start))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setBody("""{"status":"ready","provider":{"kind":"XTREAM","name":"Salon","url":"http://s.test","username":"u","password":"p"}}"""))
        val ev = flowOf().run(base, null).toList()
        assertEquals(WebSendEvent.CodeReady("K7Q2M9", 900), ev[0])
        val rec = ev[1] as WebSendEvent.Received
        assertEquals("XTREAM", rec.source.kind); assertEquals("Salon", rec.source.name)
        assertEquals(2, ev.size)
    }

    @Test fun `code expire cote serveur`() = runBlocking {
        server.enqueue(MockResponse().setBody(start))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(WebSendEvent.Expired, flowOf().run(base, null).toList().last())
    }

    @Test fun `echec de creation du code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val ev = flowOf().run(base, null).toList()
        assertTrue(ev.single() is WebSendEvent.Failed)
    }

    @Test fun `limite de debit sur le sondage ralentit puis continue`() = runBlocking {
        server.enqueue(MockResponse().setBody(start))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "5"))
        server.enqueue(MockResponse().setBody("""{"status":"ready","provider":{"kind":"M3U","url":"http://x.test/l.m3u"}}"""))
        val ev = flowOf().run(base, null).toList()
        assertNotNull(ev.last() as? WebSendEvent.Received)
    }
}

class AnonPingPlanTest {
    private val H = 3600_000L

    @Test fun `du si jamais envoye`() = assertTrue(AnonPingPlan.due(0L, 1_000_000L))
    @Test fun `pas du avant 20 heures`() = assertFalse(AnonPingPlan.due(1_000_000L, 1_000_000L + 19 * H))
    @Test fun `du apres 20 heures`() = assertTrue(AnonPingPlan.due(1_000_000L, 1_000_000L + 20 * H))
    @Test fun `du si l'horloge est revenue en arriere`() = assertTrue(AnonPingPlan.due(2_000_000L, 1_000_000L))

    @Test fun `le corps ne contient que l'identifiant et la version`() {
        val o = JSONObject(AnonPingPlan.body("abc-123", "1.2.83"))
        assertEquals(setOf("install", "version"), o.keys().asSequence().toSet())
        assertEquals("abc-123", o.getString("install")); assertEquals("1.2.83", o.getString("version"))
    }

    @Test fun `post renvoie vrai sur 200, faux sur erreur ou reseau absent`() = runBlocking {
        val server = MockWebServer().apply { start() }
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(AnonPingPlan.post(OkHttpClient(), base, "abc-123", "1.2.83"))
        assertEquals("/api/anon/ping", server.takeRequest().path)
        assertFalse(AnonPingPlan.post(OkHttpClient(), base, "abc-123", "1.2.83"))
        server.shutdown()
        assertFalse(AnonPingPlan.post(OkHttpClient(), base, "abc-123", "1.2.83"))
    }
}
