package com.ultratv.tv.nativeapp.data.config

import android.content.Context
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.flow.toList
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudPairingFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CloudPairingClient
    private lateinit var store: DeviceTokenStore
    private var now = 1_700_000_000_000L
    private val base get() = server.url("/").toString().trimEnd('/')
    private val start = """{"code":"ABCD-EFGH","pollSecret":"s3","expiresIn":600,"interval":3}"""

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = CloudPairingClient(OkHttpClient())
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("cloud_sync_flow", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = DeviceTokenStore(prefs, FakeCipher()) { now }
    }
    @After fun tearDown() { server.shutdown() }

    private fun pairing() = CloudPairing(client, store).also { it.sleeper = { } }

    @Test fun `parcours nominal affiche le code puis stocke le jeton`() = runBlocking {
        server.enqueue(MockResponse().setBody(start))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setBody("""{"token":"utv_tok","deviceId":"d1"}"""))
        val events = pairing().run(base, "02:aa").toList()
        assertEquals(listOf(PairingEvent.CodeReady("ABCD-EFGH", 600), PairingEvent.Paired), events)
        assertEquals("utv_tok", store.token())
        assertEquals("d1", store.deviceId())
    }

    @Test fun `code expire cote serveur`() = runBlocking {
        server.enqueue(MockResponse().setBody(start))
        server.enqueue(MockResponse().setResponseCode(404))
        val events = pairing().run(base, "x").toList()
        assertEquals(PairingEvent.Expired, events.last())
        assertNull(store.token())
    }

    @Test fun `abandon apres l'expiration locale sans jamais recevoir de jeton`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"ABCD-EFGH","pollSecret":"s3","expiresIn":6,"interval":3}"""))
        repeat(10) { server.enqueue(MockResponse().setResponseCode(202)) }
        val events = pairing().run(base, "x").toList()
        assertEquals(PairingEvent.Expired, events.last())
        assertFalse(store.isPaired)
    }

    @Test fun `serveur injoignable donne Failed`() = runBlocking {
        server.shutdown()
        val events = pairing().run(base, "x").toList()
        assertTrue(events.single() is PairingEvent.Failed)
    }

    @Test fun `429 au demarrage donne Failed avec le delai`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        val e = pairing().run(base, "x").toList().single() as PairingEvent.Failed
        assertTrue(e.message.contains("120"))
    }

    @Test fun `config source sans jeton leve NotPaired`() = runBlocking {
        try { CloudConfigSource(client, store).fetch(base); fail() } catch (_: NotPairedException) { }
    }

    @Test fun `config source efface le jeton quand le serveur le revoque`() = runBlocking {
        store.save("utv_old", "d1")
        server.enqueue(MockResponse().setResponseCode(401))
        try { CloudConfigSource(client, store).fetch(base); fail() } catch (_: TokenRejectedException) { }
        assertFalse(store.isPaired)
    }

    @Test fun `config source renvoie le corps avec le jeton`() = runBlocking {
        store.save("utv_old", "d1")
        server.enqueue(MockResponse().setBody("""{"providers":[{"kind":"M3U"}]}"""))
        assertTrue(CloudConfigSource(client, store).fetch(base).contains("M3U"))
        assertEquals("Bearer utv_old", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `config source fait tourner un jeton de plus de 90 jours`() = runBlocking {
        store.save("utv_old", "d1")
        now += 100L * 24 * 3600 * 1000
        server.enqueue(MockResponse().setBody("""{"token":"utv_new","deviceId":"d1"}"""))
        server.enqueue(MockResponse().setBody("""{"providers":[]}"""))
        CloudConfigSource(client, store).fetch(base)
        assertEquals("/api/device/rotate", server.takeRequest().path)
        assertEquals("Bearer utv_new", server.takeRequest().getHeader("Authorization"))
        assertEquals("utv_new", store.token())
        assertEquals(0L, store.ageDays())
    }

    @Test fun `echec de rotation ne bloque pas la synchronisation`() = runBlocking {
        store.save("utv_old", "d1")
        now += 100L * 24 * 3600 * 1000
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""{"providers":[]}"""))
        CloudConfigSource(client, store).fetch(base)
        assertEquals("utv_old", store.token())
    }
}
