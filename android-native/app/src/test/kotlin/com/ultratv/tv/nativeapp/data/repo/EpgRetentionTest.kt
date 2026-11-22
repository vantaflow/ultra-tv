package com.ultratv.tv.nativeapp.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val H = 3_600_000L
private const val DAY = 24 * H

/** #248 : programmes passés gardés seulement pour les chaînes avec archive, dans leur archive (plafond 7 jours). */
class EpgRetentionTest {
    private val now = 1_000 * DAY
    private val base = (now - 2 * H)..(now + 24 * H)
    private fun prog(ch: Long, start: Long, end: Long, desc: String? = "d") = EpgEntity(channelId = ch, title = "t", description = desc, startMs = start, endMs = end)

    @Test fun fenetreDuFlux_elargieALaPlusLongueArchive_plafond7() {
        assertEquals(base, EpgRetention.feedWindow(base, now, 0))
        assertEquals((now - 3 * DAY)..base.last, EpgRetention.feedWindow(base, now, 3))
        assertEquals((now - 7 * DAY)..base.last, EpgRetention.feedWindow(base, now, 30))
    }

    @Test fun garde_fenetreNormale_telleQuelle_passeSeulementAvecArchive() {
        val days = mapOf(1L to 3, 2L to 0, 3L to 14)
        val plan = EpgRetention.Plan(now, base, days)
        val current = prog(2, now - H, now + H)
        assertSame(current, plan.keep(current))
        // Passé de 2 jours : chaîne avec 3 jours d'archive oui, chaîne sans archive non.
        val past1 = prog(1, now - 2 * DAY, now - 2 * DAY + H)
        assertEquals(past1, plan.keep(past1))
        assertNull(plan.keep(prog(2, now - 2 * DAY, now - 2 * DAY + H)))
        // Hors archive (4 jours pour 3) : écarté ; archive de 14 jours plafonnée à 7.
        assertNull(plan.keep(prog(1, now - 4 * DAY, now - 4 * DAY + H)))
        assertEquals(6 * DAY, now - plan.keep(prog(3, now - 6 * DAY, now - 6 * DAY + H))!!.startMs)
        assertNull(plan.keep(prog(3, now - 8 * DAY, now - 8 * DAY + H)))
        // Chaîne inconnue : aucune archive.
        assertNull(plan.keep(prog(99, now - 2 * DAY, now - 2 * DAY + H)))
    }

    @Test fun passeDArchive_descriptionRaccourcie() {
        val plan = EpgRetention.Plan(now, base, mapOf(1L to 7))
        val long = "x".repeat(400)
        val kept = plan.keep(prog(1, now - DAY, now - DAY + H, long))!!
        assertEquals(EpgRetention.ARCHIVE_DESC_MAX + 1, kept.description!!.length)
        // Fenêtre normale : description intacte.
        assertEquals(400, plan.keep(prog(1, now - H, now + H, long))!!.description!!.length)
    }

    @Test fun lot_sansArchive_inchange() {
        val plan = EpgRetention.Plan(now, base, emptyMap())
        val batch = listOf(prog(1, now - H, now + H), prog(2, now, now + H))
        assertSame(batch, plan.apply(batch))
        assertEquals(base, plan.window)
    }
}

/** Purge du guide (#248) : passé effacé après 3 h, sauf pour une chaîne avec archive, dans son archive. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class EpgArchivePurgeTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries().build()

    @Test fun purge_gardeLArchive_effaceLeReste() = runBlocking {
        val now = 1_000 * DAY
        val ch = db.channelDao()
        ch.upsertAll(listOf(
            ChannelEntity(id = 1, providerId = 7, remoteId = "a", name = "A", logo = null, categoryId = null, streamUrl = "u/a", catchupDays = 3),
            ChannelEntity(id = 2, providerId = 7, remoteId = "b", name = "B", logo = null, categoryId = null, streamUrl = "u/b"),
            ChannelEntity(id = 3, providerId = 7, remoteId = "c", name = "C", logo = null, categoryId = null, streamUrl = "u/c", catchupDays = 30),
        ))
        fun p(c: Long, endAgo: Long) = EpgEntity(channelId = c, title = "t$c-$endAgo", description = null, startMs = now - endAgo - H, endMs = now - endAgo)
        db.epgDao().upsertAll(listOf(p(1, 2 * DAY), p(1, 4 * DAY), p(2, 5 * H), p(2, H), p(3, 6 * DAY), p(3, 8 * DAY)))
        db.epgDao().deletePast(now - 3 * H, now)
        val left = db.epgDao().rangeForChannels(listOf(1, 2, 3), 0, Long.MAX_VALUE).map { it.title }.sorted()
        assertEquals(listOf("t1-${2 * DAY}", "t2-$H", "t3-${6 * DAY}"), left)

        assertEquals(30, ch.maxArchiveDays(7))
        assertEquals(mapOf(1L to 3, 3L to 30), ch.archiveDays(7).associate { it.id to it.catchupDays })
        assertEquals(0, ch.maxArchiveDays(8))
        db.close()
    }
}
