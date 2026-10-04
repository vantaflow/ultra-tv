package com.ultratv.tv.nativeapp.data.recording

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingPlanTest {
    private fun prog(id: Long, title: String, start: Long, end: Long = start + 3_600_000L) =
        EpgEntity(id = id, channelId = 1, title = title, description = null, startMs = start, endMs = end)

    @Test fun hasRoom_assezDEspace_vrai() {
        val oneHour = 3_600_000L
        assertTrue(RecordingPlan.hasRoom(freeBytes = 10L * 1024 * 1024 * 1024, durationMs = oneHour))
    }

    @Test fun hasRoom_disquePresquePlein_faux() {
        assertFalse(RecordingPlan.hasRoom(freeBytes = 100L * 1024 * 1024, durationMs = 3_600_000L))
    }

    @Test fun seriesMatches_memeTitreFuturSeulement_exclutLeProgrammeCourantEtLesAutres() {
        val now = 1_000_000L
        val list = listOf(
            prog(1, "Le Journal", now - 5_000_000),        // passé
            prog(2, "  le journal ", now + 1_000),          // même titre (casse / espaces)
            prog(3, "Autre émission", now + 2_000),
            prog(4, "Le Journal", now + 90_000_000),
        )
        assertEquals(listOf(2L, 4L), RecordingPlan.seriesMatches("Le Journal", list, now).map { it.id })
    }

    @Test fun paddedWindow_ajouteUneMargeAvantEtApres() {
        val (start, end) = RecordingPlan.paddedWindow(startMs = 10_000_000, endMs = 20_000_000)
        assertEquals(10_000_000 - RecordingPlan.PAD_BEFORE_MS, start)
        assertEquals(20_000_000 + RecordingPlan.PAD_AFTER_MS, end)
    }

    @Test fun connexion_enregistrementEnCoursEtLectureDemandee_avertit() {
        assertEquals(ConnectionWarning.PLAYBACK_TAKES_CONNECTION, ConnectionPolicy.onPlayRequested(recordingRunning = true))
        assertEquals(ConnectionWarning.NONE, ConnectionPolicy.onPlayRequested(recordingRunning = false))
    }

    @Test fun connexion_lectureEnCoursEtEnregistrementDemarre_avertit() {
        assertEquals(ConnectionWarning.RECORDING_TAKES_CONNECTION, ConnectionPolicy.onRecordingStart(playing = true))
        assertEquals(ConnectionWarning.NONE, ConnectionPolicy.onRecordingStart(playing = false))
    }

    @Test fun connexion_plusieursConnexions_pasDAvertissement() {
        assertEquals(ConnectionWarning.NONE, ConnectionPolicy.onPlayRequested(recordingRunning = true, maxConnections = 2))
    }
}
