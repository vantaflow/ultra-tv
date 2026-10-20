package com.ultratv.tv.nativeapp.data.tv

import com.ultratv.tv.nativeapp.data.tv.TvProviderGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fournisseur TV absent (Shield « Unknown URL ») : sonde une fois, un seul événement par jour, rétrogradation sur erreur de résolveur. */
class TvProviderGateTest {
    private val day = TvProviderGate.REPORT_INTERVAL_MS
    private val now = 20L * day
    private fun unknownUrl() = IllegalArgumentException("Unknown URL content://android.media.tv/channel")

    // ---- decide (pure : résolu ?, dernier signalement, maintenant) ----

    @Test fun decide_jamaisSonde_sonde() = assertEquals(Decision.PROBE, TvProviderGate.decide(null, 0L, now))
    @Test fun decide_fournisseurPresent_publie() = assertEquals(Decision.PUBLISH, TvProviderGate.decide(true, 0L, now))
    @Test fun decide_fournisseurPresentDejaSignale_publieQuandMeme() = assertEquals(Decision.PUBLISH, TvProviderGate.decide(true, now - 1_000, now))
    @Test fun decide_absentJamaisSignale_sauteEtSignale() = assertEquals(Decision.SKIP_REPORT, TvProviderGate.decide(false, 0L, now))
    @Test fun decide_absentSignaleIlYAUneHeure_sauteEnSilence() = assertEquals(Decision.SKIP_SILENT, TvProviderGate.decide(false, now - 3_600_000, now))
    @Test fun decide_absentSignaleHier_signaleANouveau() = assertEquals(Decision.SKIP_REPORT, TvProviderGate.decide(false, now - day, now))
    @Test fun decide_absentHorlogeReculee_signale() = assertEquals(Decision.SKIP_REPORT, TvProviderGate.decide(false, now + day, now))

    @Test fun decide_cinqLancementsDansLaJournee_unSeulSignalement() {
        var lastReportedAt = 0L
        var reports = 0
        for (launch in 0 until 5) {
            val t = now + launch * 3_600_000L
            if (TvProviderGate.decide(false, lastReportedAt, t) == Decision.SKIP_REPORT) { reports++; lastReportedAt = t }
        }
        assertEquals(1, reports)
        assertEquals(Decision.SKIP_REPORT, TvProviderGate.decide(false, lastReportedAt, now + day))
    }

    // ---- shouldReport ----

    @Test fun shouldReport_jamaisSignale_vrai() = assertTrue(TvProviderGate.shouldReport(0L, now))
    @Test fun shouldReport_intervalleNonEcoule_faux() = assertFalse(TvProviderGate.shouldReport(now - day + 1, now))
    @Test fun shouldReport_intervalleEcoule_vrai() = assertTrue(TvProviderGate.shouldReport(now - day, now))
    @Test fun shouldReport_horlogeReculee_vrai() = assertTrue(TvProviderGate.shouldReport(now + 1, now))
    @Test fun shouldReport_intervallePersonnalise_respecte() {
        assertFalse(TvProviderGate.shouldReport(now - 999, now, intervalMs = 1_000))
        assertTrue(TvProviderGate.shouldReport(now - 1_000, now, intervalMs = 1_000))
    }

    // ---- isResolverFailure ----

    @Test fun isResolverFailure_unknownUrl_vrai() = assertTrue(TvProviderGate.isResolverFailure(unknownUrl()))
    @Test fun isResolverFailure_unknownUriDuFournisseur_vrai() = assertTrue(TvProviderGate.isResolverFailure(IllegalArgumentException("Unknown URI content://android.media.tv/preview_program")))
    @Test fun isResolverFailure_insertionRefusee_faux() = assertFalse(TvProviderGate.isResolverFailure(NullPointerException("Channel insertion failed")))
    @Test fun isResolverFailure_securityException_faux() = assertFalse(TvProviderGate.isResolverFailure(SecurityException("revoked")))
    @Test fun isResolverFailure_ioException_faux() = assertFalse(TvProviderGate.isResolverFailure(java.io.IOException("logo")))

    // ---- état du processus ----

    @Test fun probed_present_publie() {
        val g = TvProviderGate()
        assertNull(g.resolved)
        g.probed(true, "n/a")
        assertEquals(true, g.resolved)
        assertEquals(Decision.PUBLISH, g.decide(0L, now))
        assertEquals("", g.reason)
    }

    @Test fun probed_absent_sauteEtSignaleAvecLaRaison() {
        val g = TvProviderGate()
        g.probed(false, "authority android.media.tv not resolved")
        assertEquals(Decision.SKIP_REPORT, g.decide(0L, now))
        assertEquals("authority android.media.tv not resolved", g.reason)
        assertEquals(Decision.SKIP_SILENT, g.decide(now, now + 1_000))
    }

    @Test fun failed_unknownUrl_devientIndisponiblePourLeProcessus() {
        val g = TvProviderGate()
        g.probed(true, "n/a")
        assertTrue(g.failed("favorites channel", unknownUrl()))
        assertEquals(false, g.resolved)
        assertEquals(Decision.SKIP_REPORT, g.decide(0L, now))
        assertTrue(g.reason.startsWith("favorites channel: IllegalArgumentException Unknown URL"))
    }

    @Test fun failed_autreErreur_resteDisponible() {
        val g = TvProviderGate()
        g.probed(true, "n/a")
        assertFalse(g.failed("news channel", NullPointerException("Channel insertion failed")))
        assertEquals(true, g.resolved)
        assertEquals(Decision.PUBLISH, g.decide(0L, now))
    }

    @Test fun failed_puisSonde_resteIndisponible() {
        val g = TvProviderGate()
        assertTrue(g.failed("watch next", unknownUrl()))
        g.probed(true, "n/a")
        assertEquals(false, g.resolved)
        assertEquals(Decision.SKIP_SILENT, g.decide(now, now))
    }

    @Test fun failed_messageLong_tronque() {
        val g = TvProviderGate()
        g.failed("watch next", IllegalArgumentException("x".repeat(500)))
        assertTrue(g.reason.length < 200)
    }

    @Test fun decide_intervalleDuConstructeur_respecte() {
        val g = TvProviderGate(intervalMs = 10L)
        g.probed(false, "absent")
        assertEquals(Decision.SKIP_SILENT, g.decide(now - 9, now))
        assertEquals(Decision.SKIP_REPORT, g.decide(now - 10, now))
    }
}
