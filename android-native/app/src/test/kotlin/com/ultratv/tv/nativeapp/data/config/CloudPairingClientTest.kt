package com.ultratv.tv.nativeapp.data.config

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CloudPairingClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CloudPairingClient
    private val base get() = server.url("/").toString().trimEnd('/')

    @Before fun setUp() { server = MockWebServer().apply { start() }; client = CloudPairingClient(OkHttpClient()) }
    @After fun tearDown() { server.shutdown() }

    @Test fun `start envoie l'etiquette et lit le code`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"ABCD-EFGH","pollSecret":"s3","expiresIn":600,"interval":3}"""))
        val s = client.start(base, "02:aa:bb:cc:dd:ee")
        assertEquals(PairingSession("ABCD-EFGH", "s3", 600, 3), s)
        val req = server.takeRequest()
        assertEquals("POST", req.method); assertEquals("/api/pair/start", req.path)
        assertTrue(req.body.readUtf8().contains("02:aa:bb:cc:dd:ee"))
    }

    @Test fun `poll 202 est en attente, 404 est termine, 200 livre le jeton`() = runBlocking {
        val s = PairingSession("ABCD-EFGH", "s3", 600, 3)
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"status":"pending"}"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"not_found"}"""))
        server.enqueue(MockResponse().setBody("""{"token":"utv_abc","deviceId":"d1"}"""))
        assertEquals(PollResult.Pending, client.poll(base, s))
        assertEquals(PollResult.Gone, client.poll(base, s))
        assertEquals(PollResult.Paired("utv_abc", "d1"), client.poll(base, s))
        assertTrue(server.takeRequest().body.readUtf8().contains("\"pollSecret\":\"s3\""))
    }

    @Test fun `fetchConfig envoie le jeton en Bearer et jamais dans l'URL`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"providers":[]}"""))
        assertEquals("""{"providers":[]}""", client.fetchConfig(base, "utv_abc"))
        val req = server.takeRequest()
        assertEquals("Bearer utv_abc", req.getHeader("Authorization"))
        assertEquals("/api/config", req.path)
        assertFalse(req.requestUrl.toString().contains("utv_abc"))
    }

    @Test fun `fetchConfig 401 leve TokenRejectedException`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try { client.fetchConfig(base, "utv_abc"); fail() } catch (_: TokenRejectedException) { }
    }

    @Test fun `429 expose le delai Retry-After`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "42"))
        try { client.fetchConfig(base, "utv_abc"); fail() } catch (e: RateLimitedException) { assertEquals(42, e.retryAfterSec) }
    }

    @Test fun `une redirection n'est jamais suivie (le jeton ne part pas ailleurs)`() = runBlocking {
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/stolen").toString()))
            try { client.fetchConfig(base, "utv_abc"); fail() } catch (_: CloudSyncException) { }
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }

    @Test fun `rotate renvoie le nouveau jeton`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"token":"utv_new","deviceId":"d1"}"""))
        assertEquals("utv_new" to "d1", client.rotate(base, "utv_old"))
        assertEquals("Bearer utv_old", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `erreur serveur 500 est une CloudSyncException sans fuite du jeton`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        try { client.fetchConfig(base, "utv_abc"); fail() } catch (e: CloudSyncException) { assertFalse(e.message!!.contains("utv_abc")) }
    }

    @Test fun `aucune requete n'est faite sans jeton`() { assertNull(server.takeRequest(10, java.util.concurrent.TimeUnit.MILLISECONDS)) }
}
