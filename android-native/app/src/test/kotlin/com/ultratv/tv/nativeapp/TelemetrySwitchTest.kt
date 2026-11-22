package com.ultratv.tv.nativeapp

import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
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
 * Les tests ne doivent RIEN envoyer : 162 faux plantages et 498 événements « robolectric robolectric » en 48 h dans la
 * télémétrie de production. Sous Robolectric, l'interrupteur général est coupé par défaut, même vers un serveur local ;
 * seul un test qui l'ouvre lui-même (et le referme après) envoie quelque chose.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TelemetrySwitchTest {
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

    @Test fun sousRobolectric_interrupteurCoupeParDefaut() {
        assertFalse(TelemetrySwitch.sendingAllowed)
    }

    @Test fun regleParEmpreinte() {
        assertFalse(TelemetrySwitch.allowedByDefault("robolectric"))
        assertFalse(TelemetrySwitch.allowedByDefault(null))
        assertTrue(TelemetrySwitch.allowedByDefault("google/sabrina/sabrina:14/x:user/release-keys"))
    }

    @Test fun sousRobolectric_niPlantageNiEvenementNePart_memeVersUnServeurLocal() {
        repeat(3) { server.enqueue(MockResponse().setBody("{\"ok\":true}")) }
        RemoteLog.crashSync(Thread.currentThread(), IllegalStateException("boom"))
        RemoteLog.error("anr", "main thread blocked ≥ 5 s")
        RemoteLog.flushNow()
        assertNull(server.takeRequest(2, TimeUnit.SECONDS))
    }
}
