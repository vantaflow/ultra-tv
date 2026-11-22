package com.ultratv.tv.nativeapp

import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * ANR réel (MatcherNative.findImpl sur le fil principal) : RemoteLog.event() passait chaque message (jusqu'à 8 000
 * caractères) dans les six expressions de LogSanitizer sur le fil de l'appelant, presque toujours le fil principal.
 * Le nettoyage se fait maintenant à l'envoi, en arrière-plan ; le texte envoyé reste nettoyé.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RemoteLogOffMainTest {
    private val server = MockWebServer()
    private val sanitizedOn = Collections.synchronizedList(mutableListOf<Thread>())

    @Before fun setUp() {
        TelemetrySwitch.sendingAllowed = true // serveur local du test uniquement
        server.start()
        RemoteLog.workerUrlOverride = server.url("/").toString().trimEnd('/')
        RemoteLog.telemetryEnabled = true
        RemoteLog.sanitizer = { s -> sanitizedOn += Thread.currentThread(); LogSanitizer.sanitize(s) }
        RemoteLog.init(ApplicationProvider.getApplicationContext(), mac = { "AA:BB:CC:DD:EE:FF" }, versionName = "9.9.9", versionCode = 99999)
    }

    @After fun tearDown() {
        RemoteLog.sanitizer = { LogSanitizer.sanitize(it) }
        TelemetrySwitch.sendingAllowed = false
        RemoteLog.workerUrlOverride = ""
        server.shutdown()
    }

    @Test fun leNettoyageNeSeFaitPasSurLeFilDeLAppelant_maisLeTexteEnvoyeEstNettoye() {
        server.enqueue(MockResponse().setBody("{\"ok\":true}"))
        val caller = Thread.currentThread()
        RemoteLog.error("UtvSync", "échec http://u:p@h.example/live/user1/pass1/1.ts password=secret1")
        assertFalse("nettoyage fait sur le fil de l'appelant", sanitizedOn.any { it === caller })
        RemoteLog.flushNow()
        val req = server.takeRequest(5, TimeUnit.SECONDS)!!
        val ev = JSONObject(req.body.readUtf8()).getJSONArray("events")
        val msg = (0 until ev.length()).map { ev.getJSONObject(it) }.first { it.getString("tag") == "UtvSync" }.getString("message")
        assertFalse(msg, msg.contains("secret1") || msg.contains("pass1") || msg.contains("u:p@"))
        assertTrue(msg.contains("<redacted>"))
        assertTrue(sanitizedOn.isNotEmpty())
        assertEquals(0, sanitizedOn.count { it === caller })
    }
}
