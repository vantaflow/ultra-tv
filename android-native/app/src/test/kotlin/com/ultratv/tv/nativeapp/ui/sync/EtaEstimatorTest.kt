package com.ultratv.tv.nativeapp.ui.sync

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SyncPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaEstimatorTest {
    @Test fun etaSeconds_sansMesure_estNull() { val e = EtaEstimator(); e.add(0, 0); assertNull(e.etaSeconds()) }
    @Test fun etaSeconds_vitesseConstante_extrapole() {
        val e = EtaEstimator()
        e.add(0, 0); e.add(10_000, 10)           // 1 % par seconde
        assertEquals(90, e.etaSeconds())
    }
    @Test fun etaSeconds_fenetreGlissante_oublieLeVieuxRythme() {
        val e = EtaEstimator(windowMs = 30_000)
        e.add(0, 0); e.add(10_000, 50)           // rapide au début
        e.add(60_000, 55); e.add(70_000, 60)     // puis lent : 0,5 % par seconde
        assertEquals(80, e.etaSeconds())
    }
    @Test fun etaSeconds_progressionNulle_estNull() { val e = EtaEstimator(); e.add(0, 10); e.add(10_000, 10); assertNull(e.etaSeconds()) }
    @Test fun etaSeconds_recul_repartDeZero() { val e = EtaEstimator(); e.add(0, 80); e.add(10_000, 90); e.add(11_000, 5); assertNull(e.etaSeconds()) }
    @Test fun etaSeconds_mesureTropCourte_estNull() { val e = EtaEstimator(); e.add(0, 0); e.add(1_000, 5); assertNull(e.etaSeconds()) }

    private fun p(kind: String, live: Long = 0, vod: Long = 0, series: Long = 0, epg: Long = 0) =
        ProviderEntity(name = "n", kind = kind, baseUrl = "u", username = "", password = "", lastLiveSyncAt = live, lastVodSyncAt = vod, lastSeriesSyncAt = series, lastEpgSyncAt = epg)

    @Test fun requiredParts_xtream_toutDansLOrdre() = assertEquals(listOf(SyncPart.LIVE, SyncPart.VOD, SyncPart.SERIES, SyncPart.EPG), requiredParts("XTREAM"))
    @Test fun requiredParts_m3u_seulLeDirect() = assertEquals(listOf(SyncPart.LIVE), requiredParts("M3U"))
    @Test fun isDone_selonLHorodatage() { val x = p("XTREAM", live = 5); assertTrue(isDone(x, SyncPart.LIVE)); assertTrue(!isDone(x, SyncPart.VOD)) }
}
