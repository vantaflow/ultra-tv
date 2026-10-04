package com.ultratv.tv.nativeapp.adaptive

import com.ultratv.tv.nativeapp.ui.player.engine.BufferPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {
    private fun dev(ram: Int = 4000, cls: Int = 256, low: Boolean = false, cores: Int = 8, is64: Boolean = true, bench: Long? = 80, hevc: Boolean = true, screenH: Int = 1080) = DeviceInfo(
        ramMb = ram, memoryClassMb = cls, lowRamFlag = low, cores = cores, maxFreqMhz = 2000, is64Bit = is64, sdk = 34,
        h264 = HwCodec(4096, 2160, 60), hevc = if (hevc) HwCodec(4096, 2160, 60) else null, vp9 = null, av1 = null,
        screenWidth = screenH * 16 / 9, screenHeight = screenH, hdr = false, benchmarkMs = bench,
    )
    private fun profile(i: DeviceInfo) = DeviceProfile(i, DeviceClassifier.tier(i))

    // ── Appareil ──
    @Test fun tier_boxMille_estBas() = assertEquals(Tier.LOW, DeviceClassifier.tier(dev(ram = 1000, cls = 128, cores = 2, is64 = false)))
    @Test fun tier_drapeauLowRam_estBas() = assertEquals(Tier.LOW, DeviceClassifier.tier(dev(low = true)))
    @Test fun tier_chromecastHdSousLes1800_estBas() = assertEquals(Tier.LOW, DeviceClassifier.tier(dev(ram = 1650, cores = 4)))
    @Test fun tier_machineTresLente_estBas() = assertEquals(Tier.LOW, DeviceClassifier.tier(dev(bench = 1200)))
    @Test fun tier_deuxGoQuadCoeur64_estMoyen() = assertEquals(Tier.MID, DeviceClassifier.tier(dev(ram = 2400, cls = 192, cores = 4, bench = 200)))
    @Test fun tier_grosseBox_estHaut() = assertEquals(Tier.HIGH, DeviceClassifier.tier(dev(ram = 4000, cores = 8, bench = 80)))

    // ── Réseau ──
    private fun net(kbps: Int? = 20_000, type: NetType = NetType.ETHERNET, rebuf: Int = 0, ttfb: Int? = 100, err: Int = 0) =
        NetSample(type, 100_000, false, kbps, ttfb, rebuf, err)

    @Test fun quality_debits() {
        assertEquals(NetQuality.POOR, NetClassifier.quality(net(2_000), null))
        assertEquals(NetQuality.FAIR, NetClassifier.quality(net(5_000), null))
        assertEquals(NetQuality.GOOD, NetClassifier.quality(net(15_000), null))
        assertEquals(NetQuality.EXCELLENT, NetClassifier.quality(net(60_000), null))
    }
    @Test fun quality_hysteresis_monterExigeUneMarge() {
        // 8 100 kbps passe juste le seuil 8 000 : sans marge de 25 %, on reste FAIR.
        assertEquals(NetQuality.FAIR, NetClassifier.quality(net(8_100), NetQuality.FAIR))
        assertEquals(NetQuality.GOOD, NetClassifier.quality(net(11_000), NetQuality.FAIR))
    }
    @Test fun quality_descenteImmediate() = assertEquals(NetQuality.FAIR, NetClassifier.quality(net(5_000), NetQuality.EXCELLENT))
    @Test fun quality_rebufferingsEnchaines_baissentD_unCran() = assertEquals(NetQuality.GOOD, NetClassifier.quality(net(60_000, rebuf = 3), null))
    @Test fun quality_mobile_plafonneABon() = assertEquals(NetQuality.GOOD, NetClassifier.quality(net(60_000, NetType.MOBILE), null))
    @Test fun quality_latenceElevee_plafonneAMoyen() = assertEquals(NetQuality.FAIR, NetClassifier.quality(net(60_000, ttfb = 2000), null))
    @Test fun quality_sansReseau_estMauvaise() = assertEquals(NetQuality.POOR, NetClassifier.quality(net(type = NetType.NONE), null))
    @Test fun quality_sansMesure_seFondeSurLeDebitAnnonce() = assertEquals(NetQuality.EXCELLENT, NetClassifier.quality(NetSample(NetType.ETHERNET, 100_000, false, null, null, 0, 0), null))

    // ── Réglages automatiques ──
    @Test fun auto_gtvlow_reseauRapide_interfaceSansEffetsEtImages565() {
        val a = AutoTuner.settings(profile(dev(ram = 1000, cls = 128, cores = 2, is64 = false)), NetQuality.EXCELLENT, false, 80)
        assertEquals(UiEffects.NONE, a.uiEffects); assertTrue(a.imageRgb565); assertTrue(a.lowRam)
        assertEquals(24, a.epgForwardHours); assertEquals(500, a.insertBatch); assertEquals(BufferPreset.AUTO, a.bufferPreset)
    }
    @Test fun auto_gtvlow_plafonneResolutionEtDebit() {
        val a = AutoTuner.settings(profile(dev(ram = 1000, cls = 128, cores = 2, is64 = false)), NetQuality.EXCELLENT, false, 80)
        assertEquals(720, a.maxVideoHeight); assertEquals(6_000_000, a.maxVideoBitrateBps)
    }
    @Test fun auto_gtvlow_reseauMauvais_gardeLePlafondLePlusBas() {
        val a = AutoTuner.settings(profile(dev(ram = 1000, cls = 128, cores = 2, is64 = false)), NetQuality.POOR, false, 80)
        assertEquals(480, a.maxVideoHeight); assertEquals(2_000_000, a.maxVideoBitrateBps)
    }
    @Test fun auto_grosseBox_reseauRapide_toutEnPleinEtFaibleLatence() {
        val a = AutoTuner.settings(profile(dev()), NetQuality.EXCELLENT, false, 50)
        assertEquals(UiEffects.FULL, a.uiEffects); assertEquals(BufferPreset.LOW_LATENCY, a.bufferPreset)
        assertEquals(72, a.epgForwardHours); assertEquals(2, a.syncParallelism); assertNull(a.maxVideoBitrateBps)
    }
    @Test fun auto_reseauMauvais_tamponStableEtDebitBorne() {
        val a = AutoTuner.settings(profile(dev()), NetQuality.POOR, false, 300)
        assertEquals(BufferPreset.STABLE, a.bufferPreset); assertEquals(2_000_000, a.maxVideoBitrateBps); assertEquals(480, a.maxVideoHeight)
    }
    @Test fun auto_ecran720p_pasDeQuatreK() {
        val a = AutoTuner.settings(profile(dev(screenH = 720)), NetQuality.EXCELLENT, false, 50)
        assertEquals(720, a.maxVideoHeight)
    }
    @Test fun auto_sansHevcMateriel_prefereH264() = assertTrue(AutoTuner.settings(profile(dev(hevc = false)), NetQuality.GOOD, false, 50).preferH264)
    @Test fun auto_reseauFacture_synchroLourdeSeulementSurReseauNonFacture() {
        assertTrue(AutoTuner.settings(profile(dev()), NetQuality.GOOD, true, 50).syncHeavyOnlyUnmetered)
    }
    @Test fun auto_timeoutsSuiventLaLatence() {
        assertEquals(5_000, AutoTuner.settings(profile(dev()), NetQuality.GOOD, false, 100).connectTimeoutMs)
        assertEquals(20_000, AutoTuner.settings(profile(dev()), NetQuality.GOOD, false, 9_000).connectTimeoutMs)
    }

    // ── Adaptation en cours de lecture (hystérésis) ──
    @Test fun adapter_deuxRebufferings_unCranPlusPrudent() {
        val a = PlaybackAdapter()
        a.onRebuffer(0); assertEquals(0, a.level)
        a.onRebuffer(10_000); assertEquals(1, a.level)
    }
    @Test fun adapter_rebufferingsEspaces_neChangentRien() {
        val a = PlaybackAdapter()
        a.onRebuffer(0); a.onRebuffer(120_000); assertEquals(0, a.level)
    }
    @Test fun adapter_calme_remonteProgressivement() {
        val a = PlaybackAdapter()
        a.onRebuffer(0); a.onRebuffer(1_000)
        assertEquals(1, a.level)
        assertEquals(1, a.tick(30_000))                  // trop tôt
        assertEquals(0, a.tick(100_000))                 // 90 s sans incident
    }
    @Test fun adapter_pasDOscillation_entreDeuxChangements() {
        val a = PlaybackAdapter()
        a.onRebuffer(0); a.onRebuffer(1_000)             // niveau 1 à t=1 s
        a.onRebuffer(2_000); a.onRebuffer(3_000)         // pendant la fenêtre de garde
        assertEquals(1, a.level)
    }
    @Test fun adapter_plafonneAuNiveauMax() {
        val a = PlaybackAdapter(maxLevel = 2)
        var t = 0L
        repeat(10) { a.onRebuffer(t); t += 100; a.onRebuffer(t); t += 61_000 }
        assertEquals(2, a.level)
    }
    @Test fun adapter_bump_etDebit() {
        assertEquals(BufferPreset.BALANCED, PlaybackAdapter.bump(BufferPreset.AUTO, 1))
        assertEquals(BufferPreset.STABLE, PlaybackAdapter.bump(BufferPreset.LOW_LATENCY, 9))
        assertEquals(8_000_000, PlaybackAdapter.scaleBitrate(12_000_000, 1))
        assertEquals(12_000_000, PlaybackAdapter.scaleBitrate(12_000_000, 0))
    }

    // ── Choix manuels prioritaires ──
    @Test fun resolver_autoParDefaut() {
        val a = AutoTuner.settings(profile(dev()), NetQuality.GOOD, false, 50)
        val r = PlaybackResolver.resolve(a, "auto", "auto", "auto", com.ultratv.tv.nativeapp.ui.player.engine.CustomBuffer(), 256)
        assertTrue(!r.manualEngine && !r.manualDecoder && !r.manualBuffer)
        assertEquals(BufferPreset.BALANCED, r.bufferPreset)
    }
    @Test fun resolver_manuelPrioritaire() {
        val a = AutoTuner.settings(profile(dev()), NetQuality.EXCELLENT, false, 50)
        val r = PlaybackResolver.resolve(a, "vlc", "sw", "stable", com.ultratv.tv.nativeapp.ui.player.engine.CustomBuffer(), 256)
        assertEquals(com.ultratv.tv.nativeapp.ui.player.engine.EngineChoice.VLC, r.engine)
        assertEquals(com.ultratv.tv.nativeapp.ui.player.engine.DecoderMode.SOFTWARE, r.decoder)
        assertEquals(BufferPreset.STABLE, r.bufferPreset); assertTrue(r.manualEngine && r.manualDecoder && r.manualBuffer)
    }
}
