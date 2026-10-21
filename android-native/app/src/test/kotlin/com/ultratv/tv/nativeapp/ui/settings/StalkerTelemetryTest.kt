package com.ultratv.tv.nativeapp.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Adresses fictives uniquement (.invalid) ; MAC du préfixe générique des boîtiers MAG. */
class StalkerTelemetryTest {
    @Test fun hoteSeul_jamaisLeChemin_niLePort_niLaRequete() {
        assertEquals("portail-fictif.invalid", StalkerTelemetry.portalHost("http://Portail-Fictif.invalid:8080/c/?mac=00:1A:79:00:00:01"))
        assertEquals("portail-fictif.invalid", StalkerTelemetry.portalHost("portail-fictif.invalid/stalker_portal/c/"))
        assertEquals("invalid", StalkerTelemetry.portalHost(""))
    }

    @Test fun erreur_seulementSaClasse() {
        assertEquals("IllegalStateException", StalkerTelemetry.errorClass(IllegalStateException("http://portail-fictif.invalid 00:1A:79:00:00:01")))
        assertEquals("unknown", StalkerTelemetry.errorClass(null))
    }

    @Test fun limiteur_parCle_etParSession() {
        var t = 0L
        val l = StalkerTelemetry.Limiter(perKeyMs = 1_000, maxPerSession = 3, now = { t })
        assertTrue(l.allow("a"))
        assertFalse(l.allow("a"))          // même clé trop tôt
        assertTrue(l.allow("b"))
        t = 1_000
        assertTrue(l.allow("a"))
        t = 5_000
        assertFalse(l.allow("c"))          // plafond de la session atteint
    }

    @Test fun messages_sansMacNiIdentifiants() {
        val got = mutableListOf<String>()
        val old = StalkerTelemetry.sink
        StalkerTelemetry.sink = { got += it }
        try {
            StalkerTelemetry.addAttempt("http://portail-fictif.invalid:8080/c/")
            StalkerTelemetry.probeFailed(java.io.IOException("00:1A:79:00:00:01 refusé"))
        } finally { StalkerTelemetry.sink = old }
        assertTrue(got.isNotEmpty())
        got.forEach { m -> assertFalse(m, m.contains("00:1A:79") || m.contains("8080") || m.contains("/c/")) }
    }
}
