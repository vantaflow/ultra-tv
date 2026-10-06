package com.ultratv.tv.nativeapp.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 16 → 17 : addedKey calculé comme String.toLongOrNull() (0 si non numérique). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PerfMigration17Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    fun migrate16to17_addedKeyRempli() {
        helper.createDatabase("p17", 16).apply {
            execSQL("INSERT INTO movie (providerId, remoteId, categoryId, name, title, sortKey, streamUrl, lang) VALUES (7, '12345', 'c', 'A', 'A', 'a', 'u', '')")
            execSQL("INSERT INTO movie (providerId, remoteId, categoryId, name, title, sortKey, streamUrl, lang) VALUES (7, 'x9', 'c', 'B', 'B', 'b', 'u', '')")
            execSQL("INSERT INTO series (providerId, remoteId, categoryId, name, title, sortKey, lang) VALUES (7, '77', 'c', 'S', 'S', 's', '')")
            close()
        }
        val db = helper.runMigrationsAndValidate("p17", 17, true, MIGRATION_16_17)
        db.query("SELECT remoteId, addedKey FROM movie ORDER BY remoteId").use { c ->
            c.moveToNext(); assertEquals("12345", c.getString(0)); assertEquals(12345L, c.getLong(1))
            c.moveToNext(); assertEquals("x9", c.getString(0)); assertEquals(0L, c.getLong(1))
        }
        db.query("SELECT addedKey FROM series").use { it.moveToFirst(); assertEquals(77L, it.getLong(0)) }
    }
}
