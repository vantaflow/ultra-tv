package com.ultratv.tv.nativeapp

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sous Robolectric, la vraie application ne lance ni la surveillance des blocages ni le gestionnaire de plantages distant. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UltraTvAppTelemetryTest {
    @Test fun sousRobolectric_niSurveillanceANR_niGestionnaireDePlantage() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue(app is UltraTvApp)
        val watchdogAlive = Thread.getAllStackTraces().keys.any { it.name == "ultra-anr-watchdog" && it.isAlive && !it.isInterrupted }
        assertFalse("surveillance ANR lancée sous Robolectric", watchdogAlive)
        assertFalse("gestionnaire de plantage distant installé sous Robolectric", Thread.getDefaultUncaughtExceptionHandler() is UltraTvApp.RemoteCrashHandler)
    }
}
