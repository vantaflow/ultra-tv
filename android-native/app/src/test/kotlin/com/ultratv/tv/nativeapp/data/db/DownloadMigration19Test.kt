package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ultratv.tv.nativeapp.data.downloads.DownloadEntity
import com.ultratv.tv.nativeapp.data.downloads.DownloadKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 18 → 19 : table `download` (téléchargements hors ligne) ajoutée, aucune donnée existante touchée. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class DownloadMigration19Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate18to19_tableEtIndex_donneesExistantesIntactes() {
        helper.createDatabase("d19", 18).apply {
            execSQL("INSERT INTO provider (id, name, kind, baseUrl, username, password, active, lastLiveSyncAt, lastVodSyncAt, lastSeriesSyncAt, lastEpgSyncAt, categoryFilter) VALUES (3, 'S', 'XTREAM', 'http://serveur-fictif.invalid', 'u', 'p', 1, 0, 0, 0, 0, -1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("d19", 19, true, MIGRATION_18_19)
        db.query("SELECT name FROM provider WHERE id = 3").use { it.moveToFirst(); assertEquals("S", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM download").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'download'").use { c ->
            val idx = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
            assertTrue(idx.toString(), "index_download_providerId_kind_remoteId" in idx && "index_download_state" in idx)
        }
        // Un même élément ne peut être téléchargé qu'une fois (index unique).
        db.execSQL("INSERT INTO download (providerId, kind, remoteId, title, urlHash, filePath, totalBytes, doneBytes, state, createdAt) VALUES (3, 'MOVIE', '9', 'T', 'h', '/f', 0, 0, 'QUEUED', 0)")
        val dup = runCatching { db.execSQL("INSERT INTO download (providerId, kind, remoteId, title, urlHash, filePath, totalBytes, doneBytes, state, createdAt) VALUES (3, 'MOVIE', '9', 'T', 'h', '/g', 0, 0, 'QUEUED', 0)") }
        assertTrue(dup.isFailure)
    }

    @Test fun chaineComplete_baseMigreeParRoom_daoUtilisable() = runBlocking {
        helper.createDatabase("d19full", 18).close()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(ctx, UltraDb::class.java, "d19full").addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        val dao = room.downloadDao()
        val id = dao.insert(DownloadEntity(providerId = 1, kind = DownloadKind.EPISODE, remoteId = "e1", parentRemoteId = "s1", title = "S · S01E01", season = 1, episode = 1, urlHash = "h", filePath = "/x"))
        assertEquals("QUEUED", dao.byId(id)!!.state)
        assertEquals(listOf(id), dao.nextQueued(listOf(-1L), 2).map { it.id })
        room.close()
    }
}
