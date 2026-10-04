package com.ultratv.tv.nativeapp.data.db

import androidx.room.testing.MigrationTestHelper
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

    @Test
    fun migrate12to15_donneesExistantes_rattacheesAuProfilPrincipal() {
        helper.createDatabase("t", 12).apply {
            execSQL("INSERT INTO favorite (providerId, kind, remoteId) VALUES (7, 'MOVIE', 'm1')")
            execSQL("INSERT INTO watch_history (providerId, kind, remoteId, title, poster, streamUrl, positionMs, durationMs, watchedAt, parentRemoteId) VALUES (7, 'MOVIE', 'm1', 'Titre', NULL, 'u', 500, 9000, 1, NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("t", 15, true, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)

        // Chaîne complète : les tables de B1 (recording, tmdb_info) existent bien dans le schéma 15
        db.query("SELECT COUNT(*) FROM recording").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM tmdb_info").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
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
