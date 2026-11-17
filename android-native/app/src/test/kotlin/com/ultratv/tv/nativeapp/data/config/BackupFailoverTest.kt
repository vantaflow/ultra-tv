package com.ultratv.tv.nativeapp.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFailoverTest {
    @Test fun `eligible - lectures de la liste blanche seulement`() {
        assertTrue(BackupFailover.eligible("GET", "/api/config"))
        assertTrue(BackupFailover.eligible("get", "/api/announcements"))
        assertFalse(BackupFailover.eligible("POST", "/api/config"))
        assertFalse(BackupFailover.eligible("GET", "/api/pair/start"))
        assertFalse(BackupFailover.eligible("GET", "/api/device/trakt/library"))
        assertFalse(BackupFailover.eligible("GET", "/api/config/aa:bb"))
    }

    @Test fun `isOutageStatus - 5xx de panne et 52x, jamais 4xx ni 304 ni 429`() {
        for (c in listOf(500, 502, 503, 504, 520, 522, 526)) assertTrue("$c", BackupFailover.isOutageStatus(c))
        for (c in listOf(200, 304, 401, 404, 410, 429, 501, 505, 527)) assertFalse("$c", BackupFailover.isOutageStatus(c))
    }

    @Test fun `accepted - seulement 200 et 304 du secours`() {
        assertTrue(BackupFailover.accepted(200))
        assertTrue(BackupFailover.accepted(304))
        for (c in listOf(401, 404, 429, 500, 503)) assertFalse("$c", BackupFailover.accepted(c))
    }

    @Test fun `breaker - ouvre apres 2 echecs, double la pause, referme au succes`() {
        var t = 0L
        val b = BackupFailover.Breaker { t }
        b.failure()
        assertFalse(b.preferBackup())
        b.failure()
        assertTrue(b.preferBackup())
        t = 59_999; assertTrue(b.preferBackup())
        t = 60_000; assertFalse(b.preferBackup())
        b.failure()
        t = 60_000 + 119_999; assertTrue(b.preferBackup())
        t = 60_000 + 120_000; assertFalse(b.preferBackup())
        repeat(5) { b.failure(); t += 400_000 }
        b.failure()
        t += 300_000
        assertFalse(b.preferBackup()) // plafond 5 min
        b.success()
        b.failure()
        assertFalse(b.preferBackup())
    }

    @Test fun `le secours est sur un hote distinct des hotes du Worker`() {
        assertEquals(null, WorkerFallbackLogic.alternate(BackupFailover.HOST))
    }
}
