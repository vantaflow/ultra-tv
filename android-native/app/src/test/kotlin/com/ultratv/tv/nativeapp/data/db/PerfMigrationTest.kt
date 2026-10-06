package com.ultratv.tv.nativeapp.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 15 → 16 : guide dédoublonné et purgé, index des films / séries alignés sur leur tri. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PerfMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    fun migrate15to16_guideDedoublonnePurge_indexEnPlace() {
        val now = System.currentTimeMillis()
        helper.createDatabase("p", 15).apply {
            // Deux fois le même programme (doublon), un programme terminé depuis hier, un programme à venir.
            execSQL("INSERT INTO epg (channelId, title, description, startMs, endMs) VALUES (1, 'A', NULL, ${now}, ${now + 3_600_000})")
            execSQL("INSERT INTO epg (channelId, title, description, startMs, endMs) VALUES (1, 'A bis', NULL, ${now}, ${now + 3_600_000})")
            execSQL("INSERT INTO epg (channelId, title, description, startMs, endMs) VALUES (1, 'Hier', NULL, ${now - 26 * 3_600_000L}, ${now - 25 * 3_600_000L})")
            execSQL("INSERT INTO epg (channelId, title, description, startMs, endMs) VALUES (2, 'B', NULL, ${now + 3_600_000}, ${now + 7_200_000})")
            execSQL("INSERT INTO movie (providerId, remoteId, categoryId, name, title, sortKey, streamUrl, lang) VALUES (7, 'm1', 'c', 'Film', 'Film', 'film', 'u', '')")
            close()
        }
        val db = helper.runMigrationsAndValidate("p", 16, true, MIGRATION_15_16)

        db.query("SELECT title FROM epg ORDER BY channelId, startMs").use { c ->
            val titles = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()
            assertEquals(listOf("A bis", "B"), titles)   // doublon retiré (le plus récent gardé), passé purgé
        }
        db.query("SELECT COUNT(*) FROM movie").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name IN ('movie', 'epg') ORDER BY name").use { c ->
            val idx = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
            assertTrue(idx.containsAll(setOf("index_movie_providerId", "index_movie_providerId_categoryId", "index_epg_channelId_startMs", "index_epg_endMs")))
            assertTrue("index_movie_providerId_sortKey" !in idx)
        }
    }
}
