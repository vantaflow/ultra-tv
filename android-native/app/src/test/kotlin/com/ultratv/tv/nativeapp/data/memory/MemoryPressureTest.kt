package com.ultratv.tv.nativeapp.data.memory

import com.ultratv.tv.nativeapp.data.memory.MemoryPressure.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pression mémoire : gravité par niveau, registre des caches, taille du cache d'images, relevé joint aux plantages. */
class MemoryPressureTest {

    // ── Gravité ──
    @Test fun gravite_niveauxCritiques() {
        assertEquals(Severity.CRITICAL, MemoryPressure.severity(15, false))   // RUNNING_CRITICAL
        assertEquals(Severity.CRITICAL, MemoryPressure.severity(80, false))   // COMPLETE
    }

    @Test fun gravite_niveauxModeres() {
        assertEquals(Severity.MODERATE, MemoryPressure.severity(10, false))   // RUNNING_LOW
        assertEquals(Severity.MODERATE, MemoryPressure.severity(40, false))   // BACKGROUND
        assertEquals(Severity.MODERATE, MemoryPressure.severity(60, false))   // MODERATE (arrière-plan)
        assertEquals(Severity.LIGHT, MemoryPressure.severity(20, false))      // UI_HIDDEN : les caches restent sur un appareil confortable
    }

    @Test fun gravite_niveauLeger_devientModereSurAppareilLimite() {
        assertEquals(Severity.LIGHT, MemoryPressure.severity(5, false))
        assertEquals(Severity.MODERATE, MemoryPressure.severity(5, true))
    }

    @Test fun gravite_niveauInconnuOuNul_neFaitRien() {
        assertEquals(Severity.NONE, MemoryPressure.severity(0, true))
    }

    // ── Registre ──
    @Test fun registre_actionsSelonGravite() {
        var moderate = 0; var critical = 0
        val a = MemoryPressure.register(Severity.MODERATE) { moderate++ }
        val b = MemoryPressure.register(Severity.CRITICAL) { critical++ }
        try {
            MemoryPressure.trim(Severity.MODERATE)
            assertEquals(1, moderate); assertEquals(0, critical)
            MemoryPressure.trim(Severity.CRITICAL)
            assertEquals(2, moderate); assertEquals(1, critical)
            MemoryPressure.trim(Severity.NONE)
            assertEquals(2, moderate)
        } finally { a.close(); b.close() }
    }

    @Test fun registre_uneActionEnEchecNeBloquePasLesSuivantes() {
        var ran = false
        val a = MemoryPressure.register { error("boom") }
        val b = MemoryPressure.register { ran = true }
        try { MemoryPressure.trim(Severity.CRITICAL); assertTrue(ran) } finally { a.close(); b.close() }
    }

    @Test fun registre_desinscriptionRetireLAction() {
        var n = 0
        MemoryPressure.register { n++ }.close()
        MemoryPressure.onLowMemory()
        assertEquals(0, n)
    }

    // ── Cache d'images ──
    private val mb = 1024L * 1024

    @Test fun cacheImages_petiteClasseDeTas_restePetit() {
        // TV avec un tas de 128 Mo : ~15 Mo, pas les 32 Mo d'un quart.
        assertEquals((128 * mb * 0.12).toLong(), ImageMemoryPolicy.maxBytes(128, false, false))
    }

    @Test fun cacheImages_appareilLowRam_memeRegleQuePetitTas() {
        assertEquals((256 * mb * 0.12).toLong(), ImageMemoryPolicy.maxBytes(256, true, false))
    }

    @Test fun cacheImages_economie_pasPlusGrandQueLeNormal() {
        assertTrue(ImageMemoryPolicy.maxBytes(256, false, true) < ImageMemoryPolicy.maxBytes(256, false, false))
    }

    @Test fun cacheImages_borne() {
        assertEquals(ImageMemoryPolicy.MIN_BYTES, ImageMemoryPolicy.maxBytes(8, false, true))
        assertEquals(ImageMemoryPolicy.MAX_BYTES, ImageMemoryPolicy.maxBytes(4096, false, false))
    }

    // ── Plantages ──
    @Test fun oom_detecteDirectEtDansLesCauses() {
        assertTrue(CrashMemory.isOutOfMemory(OutOfMemoryError("x")))
        assertTrue(CrashMemory.isOutOfMemory(RuntimeException("a", IllegalStateException("b", OutOfMemoryError()))))
        assertFalse(CrashMemory.isOutOfMemory(RuntimeException("a")))
        assertFalse(CrashMemory.isOutOfMemory(null))
    }

    @Test fun oom_causeCirculaire_ne_boucle_pas() {
        val e = RuntimeException("a")
        val f = RuntimeException("b", e)
        e.initCause(f)
        assertFalse(CrashMemory.isOutOfMemory(e))
    }

    @Test fun releve_ligneContientTasEtMemoireLibre() {
        val line = CrashMemory.line(CrashMemory.Snapshot(128, 120, 128, 256, true, 300, 1500, true))
        assertTrue(line.contains("tas max=128 Mo"))
        assertTrue(line.contains("utilise=120 Mo"))
        assertTrue(line.contains("ram libre=300/1500 Mo"))
        assertTrue(line.contains("systeme faible=oui"))
        assertTrue(line.contains("lowRamDevice=oui"))
    }

    @Test fun etiquette_seulementPourOom() {
        assertEquals("[OOM] ", CrashMemory.tag(true))
        assertEquals("", CrashMemory.tag(false))
    }
}
