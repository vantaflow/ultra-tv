package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.engine.BufferPlanner
import com.ultratv.tv.nativeapp.ui.player.engine.BufferPreset
import com.ultratv.tv.nativeapp.ui.player.engine.ClampReason
import com.ultratv.tv.nativeapp.ui.player.engine.Combo
import com.ultratv.tv.nativeapp.ui.player.engine.CustomBuffer
import com.ultratv.tv.nativeapp.ui.player.engine.DecoderMode
import com.ultratv.tv.nativeapp.ui.player.engine.EngineChoice
import com.ultratv.tv.nativeapp.ui.player.engine.EngineKind
import com.ultratv.tv.nativeapp.ui.player.engine.InMemoryChannelPlaybackMemory
import com.ultratv.tv.nativeapp.ui.player.engine.PlayErrorKind
import com.ultratv.tv.nativeapp.ui.player.engine.PlaybackPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePlanningTest {
    private val exoAuto = Combo(EngineKind.EXO, DecoderMode.AUTO)
    private val exoSw = Combo(EngineKind.EXO, DecoderMode.SOFTWARE)
    private val vlcAuto = Combo(EngineKind.VLC, DecoderMode.AUTO)
    private val vlcSw = Combo(EngineKind.VLC, DecoderMode.SOFTWARE)

    // ── Sélection du moteur ──
    @Test fun initial_auto_commenceParExoPlayer() = assertEquals(exoAuto, PlaybackPlanner.initial(EngineChoice.AUTO, DecoderMode.AUTO, null))
    @Test fun initial_vlcImpose_estRespecte() = assertEquals(Combo(EngineKind.VLC, DecoderMode.HARDWARE), PlaybackPlanner.initial(EngineChoice.VLC, DecoderMode.HARDWARE, null))
    @Test fun initial_chaineMemorisee_estReprise() = assertEquals(vlcSw, PlaybackPlanner.initial(EngineChoice.AUTO, DecoderMode.AUTO, vlcSw))
    @Test fun initial_memoireIncompatibleAvecLeChoixManuel_estIgnoree() = assertEquals(exoAuto, PlaybackPlanner.initial(EngineChoice.EXO, DecoderMode.AUTO, vlcSw))

    // ── Repli ──
    @Test fun next_ordreDeRepli() {
        val tried = mutableSetOf(exoAuto)
        assertEquals(exoSw, PlaybackPlanner.next(EngineChoice.AUTO, tried)); tried += exoSw
        assertEquals(vlcAuto, PlaybackPlanner.next(EngineChoice.AUTO, tried)); tried += vlcAuto
        assertEquals(vlcSw, PlaybackPlanner.next(EngineChoice.AUTO, tried)); tried += vlcSw
        assertNull(PlaybackPlanner.next(EngineChoice.AUTO, tried))
    }
    @Test fun next_moteurImpose_neQuittePasLeMoteur() {
        assertEquals(exoSw, PlaybackPlanner.next(EngineChoice.EXO, setOf(exoAuto)))
        assertNull(PlaybackPlanner.next(EngineChoice.EXO, setOf(exoAuto, exoSw)))
        assertEquals(vlcSw, PlaybackPlanner.next(EngineChoice.VLC, setOf(vlcAuto)))
    }
    @Test fun repli_seulementPourFormatDecodeurEtImageAbsente() {
        for (k in listOf(PlayErrorKind.FORMAT, PlayErrorKind.DECODER, PlayErrorKind.NO_PICTURE, PlayErrorKind.UNKNOWN)) assertTrue(PlaybackPlanner.shouldFallBack(k))
        for (k in listOf(PlayErrorKind.NETWORK, PlayErrorKind.REFUSED, PlayErrorKind.NOT_FOUND)) assertFalse(PlaybackPlanner.shouldFallBack(k))
    }
    @Test fun nouvelleTentative_memeCombinaison_pourRefusEtReseau() {
        assertTrue(PlaybackPlanner.shouldRetrySame(PlayErrorKind.REFUSED)); assertTrue(PlaybackPlanner.shouldRetrySame(PlayErrorKind.NETWORK)); assertFalse(PlaybackPlanner.shouldRetrySame(PlayErrorKind.FORMAT))
    }
    @Test fun classifyExo_statutsHttpEtCodes() {
        assertEquals(PlayErrorKind.REFUSED, PlaybackPlanner.classifyExo(2004, 403))
        assertEquals(PlayErrorKind.REFUSED, PlaybackPlanner.classifyExo(2004, 401))
        assertEquals(PlayErrorKind.NOT_FOUND, PlaybackPlanner.classifyExo(2004, 404))
        assertEquals(PlayErrorKind.NETWORK, PlaybackPlanner.classifyExo(2004, 503))
        assertEquals(PlayErrorKind.NETWORK, PlaybackPlanner.classifyExo(2001, null))
        assertEquals(PlayErrorKind.BEHIND_LIVE, PlaybackPlanner.classifyExo(1002, null))
        assertEquals(PlayErrorKind.FORMAT, PlaybackPlanner.classifyExo(3003, null))
        assertEquals(PlayErrorKind.FORMAT, PlaybackPlanner.classifyExo(4005, null))
        assertEquals(PlayErrorKind.DECODER, PlaybackPlanner.classifyExo(4001, null))
        assertEquals(PlayErrorKind.UNKNOWN, PlaybackPlanner.classifyExo(1000, null))
    }

    // ── Mémorisation par chaîne ──
    @Test fun memoire_parChaine() {
        val m = InMemoryChannelPlaybackMemory()
        assertNull(m.combo("1:42"))
        m.remember("1:42", vlcSw, BufferPreset.STABLE)
        assertEquals(vlcSw, m.combo("1:42")); assertEquals(BufferPreset.STABLE, m.bufferPreset("1:42")); assertNull(m.combo("1:43"))
    }
    @Test fun combo_encodage() { assertEquals(vlcAuto, Combo.decode(vlcAuto.encode())); assertNull(Combo.decode("n'importe quoi")); assertNull(Combo.decode(null)) }

    // ── Tampon : préréglage → paramètres, bornage selon la RAM ──
    @Test fun buffer_faibleLatence_demarrageUneSeconde() {
        val b = BufferPlanner.resolve(BufferPreset.LOW_LATENCY, CustomBuffer(), heapClassMb = 256, lowRam = false)
        assertEquals(1_000, b.startMs); assertTrue(b.maxMs in 8_000..15_000)
    }
    @Test fun buffer_stable_soixanteSecondes_siLaRamLePermet() {
        val b = BufferPlanner.resolve(BufferPreset.STABLE, CustomBuffer(), heapClassMb = 512, lowRam = false)
        assertEquals(60_000, b.maxMs); assertEquals(4_000, b.startMs); assertEquals(ClampReason.NONE, b.clamp)
    }
    @Test fun buffer_stable_surBoxFaibleRam_estBorneEtExplique() {
        val b = BufferPlanner.resolve(BufferPreset.STABLE, CustomBuffer(), heapClassMb = 192, lowRam = true)
        assertEquals(ClampReason.MEMORY, b.clamp); assertTrue(b.maxMs < 60_000); assertTrue(b.maxBytes <= 16 * 1_048_576)
    }
    @Test fun buffer_perso_minSuperieurAuMax_estCorrige() {
        val b = BufferPlanner.resolve(BufferPreset.CUSTOM, CustomBuffer(minSec = 40, maxSec = 10, startSec = 20, rebufferSec = 5, maxMb = 32), 256, false)
        assertTrue(b.minMs <= b.maxMs); assertTrue(b.startMs <= b.minMs); assertEquals(ClampReason.ORDER, b.clamp)
    }
    @Test fun buffer_perso_plafondMemoireDepasseLeTas_estBorne() {
        val b = BufferPlanner.resolve(BufferPreset.CUSTOM, CustomBuffer(maxMb = 200), heapClassMb = 128, lowRam = false)
        assertTrue(b.maxBytes <= 32 * 1_048_576); assertEquals(ClampReason.MEMORY, b.clamp)
    }
    @Test fun buffer_auto_dependDeLaRam() {
        val low = BufferPlanner.resolve(BufferPreset.AUTO, CustomBuffer(), 192, true)
        val high = BufferPlanner.resolve(BufferPreset.AUTO, CustomBuffer(), 512, false)
        assertTrue(low.maxMs < high.maxMs && low.maxBytes < high.maxBytes)
    }
    @Test fun buffer_mappingVlc_enMillisecondes() {
        val b = BufferPlanner.resolve(BufferPreset.BALANCED, CustomBuffer(), 256, false)
        assertEquals(b.minMs, b.vlcNetworkCachingMs); assertTrue(b.vlcLiveCachingMs >= 300)
    }
}
