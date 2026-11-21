package com.ultratv.tv.nativeapp

import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Appareil sans compte : avant, ni plantage ni erreur ne partaient (aucun jeton d'appareil). Un utilisateur qui venait
 * d'installer l'appli et n'arrivait pas à ajouter sa source restait invisible. Ils partent maintenant par les routes
 * ouvertes, avec le modèle et la version, sans l'étiquette d'appareil.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteLogUnpairedTest {
    private val server = MockWebServer()

    @Before fun setUp() {
        server.start()
        RemoteLog.workerUrlOverride = server.url("/").toString().trimEnd('/')
        RemoteLog.telemetryEnabled = true
        RemoteLog.init(ApplicationProvider.getApplicationContext(), mac = { "AA:BB:CC:DD:EE:FF" }, versionName = "9.9.9", versionCode = 99999)
    }

    @After fun tearDown() {
        RemoteLog.workerUrlOverride = ""
        server.shutdown()
    }

    @Test fun plantage_partParLaRouteSansCompte_avecModeleEtVersion_sansEtiquetteDAppareil() {
        server.enqueue(MockResponse().setBody("{\"ok\":true}"))
        RemoteLog.crashSync(Thread.currentThread(), IllegalStateException("boom"))
        val req = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/anon/crash", req.path)
        assertNull(req.getHeader("Authorization"))
        val body = JSONObject(req.body.readUtf8())
        assertTrue(body.getString("install").length >= 16)
        assertEquals("9.9.9", body.getString("version"))
        assertEquals(99999, body.getInt("versionCode"))
        assertTrue(body.getString("device").isNotBlank())
        assertTrue(body.has("androidSdk"))
        assertTrue(body.getString("stack").contains("IllegalStateException"))
        assertFalse(body.has("mac"))
    }

    @Test fun erreur_part_maisPasLesLignesDInformation() {
        server.enqueue(MockResponse().setBody("{\"ok\":true}"))
        repeat(40) { RemoteLog.info("nav", "→ home $it") }
        RemoteLog.error("UtvSync", "échec STALKER après 200 ms: HTTP 403")
        RemoteLog.flushNow()
        val req = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/anon/events", req.path)
        val body = JSONObject(req.body.readUtf8())
        assertFalse(body.has("mac"))
        val events = body.getJSONArray("events")
        val levels = (0 until events.length()).map { events.getJSONObject(it).getString("level") }
        assertTrue(levels.isNotEmpty())
        assertTrue(levels.all { it == "error" || it == "warn" })
        assertTrue((0 until events.length()).any { events.getJSONObject(it).getString("tag") == "UtvSync" })
    }
}
