package com.ultratv.tv.nativeapp.data.sync

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SyncPart
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
    private val h = 3_600_000L
    private val now = 1_000 * h
    private val ttl = SyncPolicy.ttl(0)

    private fun xtream(live: Long = 0, vod: Long = 0, series: Long = 0, epg: Long = 0) = ProviderEntity(
        id = 1, name = "x", kind = "XTREAM", baseUrl = "http://h", username = "u", password = "p",
        lastLiveSyncAt = live, lastVodSyncAt = vod, lastSeriesSyncAt = series, lastEpgSyncAt = epg,
    )

    @Test fun dueParts_jamaisSynchronise_toutDansLOrdreDuDirectDAbord() {
        assertEquals(
            listOf(SyncPart.LIVE, SyncPart.VOD, SyncPart.SERIES, SyncPart.EPG),
            SyncPolicy.dueParts(xtream(), now, ttl, liveCount = 0, force = false),
        )
    }

    @Test fun dueParts_toutFrais_rienARecharger() {
        val p = xtream(now - h, now - h, now - h, now - h)
        assertEquals(emptyList<SyncPart>(), SyncPolicy.dueParts(p, now, ttl, liveCount = 100, force = false))
    }

    @Test fun dueParts_seulLeDirectEstPerime_seulLeDirectEstRecharge() {
        val p = xtream(live = now - 7 * h, vod = now - h, series = now - h, epg = now - h)
        assertEquals(listOf(SyncPart.LIVE), SyncPolicy.dueParts(p, now, ttl, liveCount = 100, force = false))
    }

    @Test fun dueParts_vodEtSeriesSurVingtQuatreHeures() {
        val p = xtream(live = now - h, vod = now - 25 * h, series = now - 23 * h, epg = now - h)
        assertEquals(listOf(SyncPart.VOD), SyncPolicy.dueParts(p, now, ttl, liveCount = 100, force = false))
    }

    @Test fun dueParts_catalogueDirectVide_rechargeMemeSiRecent() {
        val p = xtream(now - h, now - h, now - h, now - h)
        assertEquals(listOf(SyncPart.LIVE), SyncPolicy.dueParts(p, now, ttl, liveCount = 0, force = false))
    }

    @Test fun dueParts_forcer_ignoreLesTtl() {
        val p = xtream(now - h, now - h, now - h, now - h)
        assertEquals(4, SyncPolicy.dueParts(p, now, ttl, liveCount = 100, force = true).size)
    }

    @Test fun dueParts_horlogeRecaleeEnArriere_estConsidereePerimee() {
        val p = xtream(live = now + 5 * h, vod = now - h, series = now - h, epg = now - h)
        assertEquals(listOf(SyncPart.LIVE), SyncPolicy.dueParts(p, now, ttl, liveCount = 100, force = false))
    }

    @Test fun dueParts_m3uLocal_neSeSynchroniseJamais() {
        val p = xtream().copy(kind = "M3U_LOCAL")
        assertEquals(emptyList<SyncPart>(), SyncPolicy.dueParts(p, now, ttl, liveCount = 0, force = true))
    }

    @Test fun dueParts_m3u_seulLeDirect() {
        assertEquals(listOf(SyncPart.LIVE), SyncPolicy.dueParts(xtream().copy(kind = "M3U"), now, ttl, 0, false))
    }

    /** Source Stalker enregistrée avant la 1.1.1 : plus synchronisée, ne plante pas, et la première synchro n'attend rien. */
    @Test fun dueParts_sourceStalkerHeritee_aucuneSynchro() {
        val legacy = xtream().copy(kind = "STALKER")
        assertEquals(emptyList<SyncPart>(), SyncPolicy.dueParts(legacy, now, ttl, 0, true))
        assertEquals(emptyList<SyncPart>(), com.ultratv.tv.nativeapp.ui.sync.requiredParts("STALKER"))
    }

    @Test fun ttl_intervalleUtilisateur_s_applique_au_direct() {
        val t = SyncPolicy.ttl(3)
        assertEquals(3 * h, t.liveMs)
        assertEquals(24 * h, t.vodMs)
    }

    @Test
    fun epgNeedsRefresh_guideRecentEtCouvrant_neRechargePas() {
        assertEquals(false, SyncPolicy.epgNeedsRefresh(lastSyncAt = now - 2 * h, lastEndMs = now + 20 * h, now = now))
    }

    @Test
    fun epgNeedsRefresh_synchroDePlusDe12h_recharge() {
        assertEquals(true, SyncPolicy.epgNeedsRefresh(lastSyncAt = now - 13 * h, lastEndMs = now + 20 * h, now = now))
    }

    @Test
    fun epgNeedsRefresh_guideQuiNeCouvrePlusLes6ProchainesHeures_recharge() {
        assertEquals(true, SyncPolicy.epgNeedsRefresh(lastSyncAt = now - 2 * h, lastEndMs = now + 3 * h, now = now))
    }

    @Test
    fun epgNeedsRefresh_guideVideOuJamaisSynchronise_recharge() {
        assertEquals(true, SyncPolicy.epgNeedsRefresh(lastSyncAt = now - h, lastEndMs = null, now = now))
        assertEquals(true, SyncPolicy.epgNeedsRefresh(lastSyncAt = 0, lastEndMs = now + 20 * h, now = now))
    }

    @Test
    fun epgNeedsRefresh_horlogeQuiARecule_recharge() {
        assertEquals(true, SyncPolicy.epgNeedsRefresh(lastSyncAt = now + h, lastEndMs = now + 20 * h, now = now))
    }
}
