package com.ultratv.tv.nativeapp.data.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Emplacement des enregistrements et enregistrements simultanés (issue #180). */
class RecordingLocationTest {
    private val internal = File("/data/app/files")
    private val device = File("/sdcard/Android/data/app/files")
    private val usb = RecordingLocation.Volume("ABCD-1234", File("/storage/ABCD-1234/Android/data/app/files"), "SanDisk")

    @Test fun cleUsbBranchee_ecritDessus() {
        assertEquals(usb.dir to false, RecordingLocation.resolve(RecordingLocation.usb("ABCD-1234"), listOf(usb), device, internal))
    }

    @Test fun cleUsbDebranchee_retombeSurLAppareil() {
        assertEquals(device to true, RecordingLocation.resolve(RecordingLocation.usb("ABCD-1234"), emptyList(), device, internal))
    }

    @Test fun interne_appareil_et_nas() {
        assertEquals(internal to false, RecordingLocation.resolve(RecordingLocation.INTERNAL, listOf(usb), device, internal))
        assertEquals(device to false, RecordingLocation.resolve(RecordingLocation.DEVICE, listOf(usb), device, internal))
        // NAS : écriture locale puis copie à la fin.
        assertEquals(device to false, RecordingLocation.resolve(RecordingLocation.nas(7), listOf(usb), device, internal))
        // Appareil sans stockage externe : mémoire interne.
        assertEquals(internal to false, RecordingLocation.resolve(RecordingLocation.DEVICE, emptyList(), null, internal))
    }

    @Test fun cles() {
        assertEquals(7L, RecordingLocation.nasId(RecordingLocation.nas(7)))
        assertNull(RecordingLocation.nasId(RecordingLocation.DEVICE))
        assertEquals("ABCD-1234", RecordingLocation.usbUuid(RecordingLocation.usb("ABCD-1234")))
        assertNull(RecordingLocation.usbUuid("usb:"))
        assertEquals("Match_ PSG_OM.ts", RecordingLocation.nasFileName("Match: PSG/OM.ts"))
    }

    @Test fun deuxEnregistrementsQuandLAbonnementAutoriseDeuxConnexions() {
        assertTrue(RecordingPlan.canRecordAlongside(busy = 0, maxConnections = 1))
        assertFalse(RecordingPlan.canRecordAlongside(busy = 1, maxConnections = 1))
        assertTrue(RecordingPlan.canRecordAlongside(busy = 1, maxConnections = 2))
        assertFalse(RecordingPlan.canRecordAlongside(busy = 2, maxConnections = 2))
        // Limite inconnue ou invalide : une seule connexion.
        assertFalse(RecordingPlan.canRecordAlongside(busy = 1, maxConnections = 0))
    }
}
