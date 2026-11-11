package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 19 → 20 : colonne `tmdb_info.genreIds` et table `title_genre` ajoutées, données existantes intactes. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class GenreMigration20Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate19to20_tableColonneEtIndex_donneesExistantesIntactes() {
        helper.createDatabase("g20", 19).apply {
            execSQL("INSERT INTO tmdb_info (kind, providerId, remoteId, tmdbId, lang, fetchedAt) VALUES ('movie', 1, 'm1', 42, 'fr-FR', 1)")
            execSQL("INSERT INTO movie (providerId, remoteId, name, poster, categoryId, streamUrl, container, year, rating, plot, title, sortKey, lang, addedKey) VALUES (1, 'm1', 'Film', NULL, '3', 'http://serveur-fictif.invalid/m/1.mkv', 'mkv', 2020, 7.0, NULL, 'Film', 'film', '', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("g20", 20, true, MIGRATION_19_20)
        db.query("SELECT tmdbId, genreIds FROM tmdb_info WHERE remoteId = 'm1'").use { it.moveToFirst(); assertEquals(42, it.getInt(0)); assertTrue(it.isNull(1)) }
        db.query("SELECT title FROM movie").use { it.moveToFirst(); assertEquals("Film", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM title_genre").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'title_genre'").use { c ->
            val idx = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
            assertTrue(idx.toString(), "index_title_genre_providerId_kind_genre_remoteId" in idx)
        }
        // Un titre ne porte un genre qu'une fois (clé primaire).
        db.execSQL("INSERT INTO title_genre (providerId, kind, remoteId, genre, lvl) VALUES (1, 'MOVIE', 'm1', 'DRAMA', 4)")
        assertTrue(runCatching { db.execSQL("INSERT INTO title_genre (providerId, kind, remoteId, genre, lvl) VALUES (1, 'MOVIE', 'm1', 'DRAMA', 4)") }.isFailure)
    }

    @Test fun chaineComplete_baseMigreeParRoom_daoUtilisable() = runBlocking {
        helper.createDatabase("g20full", 19).close()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(ctx, UltraDb::class.java, "g20full").addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        val dao = room.genreDao()
        dao.insertAll(listOf(TitleGenreEntity(1, "MOVIE", "a", "DRAMA"), TitleGenreEntity(1, "MOVIE", "b", "DRAMA"), TitleGenreEntity(1, "MOVIE", "b", "OTHER")))
        assertEquals(2, dao.classifiedCount(1, "MOVIE"))
        room.close()
    }
}
