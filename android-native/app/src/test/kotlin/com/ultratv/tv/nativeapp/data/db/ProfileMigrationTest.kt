package com.ultratv.tv.nativeapp.data.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ProfileMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    /** Stand-in de la 12 → 13 de l'agent B1 (table recording) : sans effet sur les tables testées ici. */
    private val noopB1 = object : Migration(12, 13) { override fun migrate(db: SupportSQLiteDatabase) {} }

    @Test
    fun migrate13to14_donneesExistantes_rattacheesAuProfilPrincipal() {
        helper.createDatabase("t", 12).apply {
            execSQL("INSERT INTO favorite (providerId, kind, remoteId) VALUES (7, 'MOVIE', 'm1')")
            execSQL("INSERT INTO watch_history (providerId, kind, remoteId, title, poster, streamUrl, positionMs, durationMs, watchedAt, parentRemoteId) VALUES (7, 'MOVIE', 'm1', 'Titre', NULL, 'u', 500, 9000, 1, NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("t", 14, true, noopB1, MIGRATION_13_14)

        db.query("SELECT id, name, isKids, pinHash FROM profile").use {
            assertEquals(1, it.count); it.moveToFirst()
            assertEquals(1L, it.getLong(0)); assertEquals("Principal", it.getString(1))
            assertEquals(0, it.getInt(2)); assert(it.isNull(3))
        }
        db.query("SELECT profileId, remoteId FROM favorite").use {
            it.moveToFirst(); assertEquals(1L, it.getLong(0)); assertEquals("m1", it.getString(1))
        }
        db.query("SELECT profileId, positionMs, title FROM watch_history").use {
            it.moveToFirst(); assertEquals(1L, it.getLong(0)); assertEquals(500L, it.getLong(1)); assertEquals("Titre", it.getString(2))
        }
    }
}
