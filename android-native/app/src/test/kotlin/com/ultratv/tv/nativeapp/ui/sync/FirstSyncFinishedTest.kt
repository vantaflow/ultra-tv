package com.ultratv.tv.nativeapp.ui.sync

import com.ultratv.tv.nativeapp.data.db.SyncPart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstSyncFinishedTest {
    private val xtream = requiredParts("XTREAM")

    @Test fun firstSyncFinished_guideEnEchecApresSynchro_neRetientPasLEcran() {
        val done = setOf(SyncPart.LIVE, SyncPart.VOD, SyncPart.SERIES)
        assertTrue(firstSyncFinished(xtream, { it in done }, syncRunning = false))
    }

    @Test fun firstSyncFinished_synchroEnCours_garderLEcran() {
        val done = setOf(SyncPart.LIVE, SyncPart.VOD, SyncPart.SERIES)
        assertFalse(firstSyncFinished(xtream, { it in done }, syncRunning = true))
    }

    @Test fun firstSyncFinished_catalogueIncomplet_garderLEcran() {
        assertFalse(firstSyncFinished(xtream, { it == SyncPart.LIVE }, syncRunning = false))
    }

    @Test fun firstSyncFinished_toutFait_termine() {
        assertTrue(firstSyncFinished(xtream, { true }, syncRunning = true))
    }
}
