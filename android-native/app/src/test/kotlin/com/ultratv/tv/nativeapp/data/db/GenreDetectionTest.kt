package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.repo.Genre
import com.ultratv.tv.nativeapp.data.repo.GenreDetector
import com.ultratv.tv.nativeapp.data.tmdb.TmdbInfoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Détection des genres automatiques sur une vraie base : classement, comptes GROUP BY, pages triées, incrémental. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GenreDetectionTest {
    private lateinit var db: UltraDb
    private lateinit var detector: GenreDetector

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UltraDb::class.java).allowMainThreadQueries().build()
        detector = GenreDetector(db.genreDao())
    }
    @After fun tearDown() { db.close() }

    private fun movie(rid: String, genre: String? = null, rating: Double? = null, added: Long = 0, year: Int? = 2020) =
        MovieEntity(providerId = 1, remoteId = rid, name = "Film $rid", poster = null, categoryId = "c", streamUrl = "http://serveur-fictif.invalid/$rid", container = "mp4", year = year, rating = rating, plot = null, genre = genre, lang = "", addedKey = added)

    private suspend fun counts(kind: String = "MOVIE") = db.genreDao().observeCounts(1, kind).first().associate { it.categoryId to it.n }

    private suspend fun page(source: PagingSource<Int, MovieEntity>) =
        (source.load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page).data.map { it.remoteId }

    @Test fun classement_comptes_etAutres() = runBlocking {
        db.movieDao().upsertAll(listOf(movie("1", "Action, Drama"), movie("2", "Comedy"), movie("3", null), movie("4", "Inconnu")))
        assertEquals(4, detector.runScan(1, "MOVIE", chunk = 2, pauseMs = 0))
        assertEquals(mapOf<String?, Int>("ACTION" to 1, "DRAMA" to 1, "COMEDY" to 1, "OTHER" to 2), counts())
        // Rien à refaire : deuxième passage sans travail.
        assertEquals(0, detector.runScan(1, "MOVIE", pauseMs = 0))
    }

    @Test fun genresTmdb_etRecalculIncrementalDuSeulTitreEnrichi() = runBlocking {
        db.movieDao().upsertAll(listOf(movie("1"), movie("2"), movie("3", "Drama")))
        detector.runScan(1, "MOVIE", pauseMs = 0)
        assertEquals(mapOf<String?, Int>("OTHER" to 2, "DRAMA" to 1), counts())
        // Enrichissement TMDB du titre 1 (animation japonaise) : seul ce titre est recalculé.
        db.tmdbDao().upsert(TmdbInfoEntity(kind = "movie", providerId = 1, remoteId = "1", tmdbId = 5, originalLanguage = "ja", genreIds = "16,28"))
        assertEquals(1, detector.runScan(1, "MOVIE", pauseMs = 0))
        val c = counts()
        assertEquals(1, c["ANIME"]); assertEquals(1, c["ANIMATION"]); assertEquals(1, c["ACTION"]); assertEquals(1, c["OTHER"]); assertEquals(1, c["DRAMA"])
    }

    @Test fun nouveauxTitresApresSynchro_etTitresDisparus() = runBlocking {
        db.movieDao().upsertAll(listOf(movie("1", "Horror"), movie("2", "Horror")))
        detector.runScan(1, "MOVIE", pauseMs = 0)
        // Synchro : le titre 1 est réinséré (REPLACE : nouvel id, même remoteId), le 2 disparaît, le 3 arrive.
        db.movieDao().upsertAll(listOf(movie("1", "Horror"), movie("3", "Western")))
        db.openHelper.writableDatabase.execSQL("DELETE FROM movie WHERE remoteId = '2'")
        assertEquals(1, detector.runScan(1, "MOVIE", pauseMs = 0))
        assertEquals(mapOf<String?, Int>("HORROR" to 1, "WESTERN" to 1), counts())
    }

    @Test fun pagesParGenre_selonLeTri() = runBlocking {
        db.movieDao().upsertAll(listOf(
            movie("1", "Drama", rating = 6.0, added = 30), movie("2", "Drama", rating = 9.0, added = 10),
            movie("3", "Drama", rating = 7.5, added = 20, year = 1985), movie("4", "Comedy", rating = 9.9, added = 99),
        ))
        detector.runScan(1, "MOVIE", pauseMs = 0)
        val dao = db.movieDao()
        assertEquals(listOf("1", "2", "3"), page(dao.pagedGenreByProvider(1, "DRAMA", 0, emptyList(), 0, 0, 0)))
        assertEquals(listOf("1", "3", "2"), page(dao.pagedGenreByRecent(1, "DRAMA", 0, emptyList(), 0, 0, 0)))
        assertEquals(listOf("2", "3", "1"), page(dao.pagedGenreByRating(1, "DRAMA", 0, emptyList(), 0, 0, 0)))
        // Filtres : note minimale 7 et années avant 1990.
        assertEquals(listOf("2", "3"), page(dao.pagedGenreByRating(1, "DRAMA", 0, emptyList(), 7, 0, 0)))
        assertEquals(listOf("3"), page(dao.pagedGenreByRating(1, "DRAMA", 0, emptyList(), 0, 0, 1989)))
        assertTrue(page(dao.pagedGenreByProvider(1, "WAR", 0, emptyList(), 0, 0, 0)).isEmpty())
    }

    @Test fun series_genreFournisseur() = runBlocking {
        db.seriesDao().upsertAll(listOf(
            SeriesEntity(providerId = 1, remoteId = "s1", name = "S1", poster = null, categoryId = "c", year = 2020, rating = 8.0, plot = null, genre = "Crime, Drama", lang = ""),
            SeriesEntity(providerId = 1, remoteId = "s2", name = "S2", poster = null, categoryId = "c", year = 2020, rating = 8.0, plot = null, lang = ""),
        ))
        assertEquals(2, detector.runScan(1, "SERIES", pauseMs = 0))
        assertEquals(mapOf<String?, Int>("CRIME" to 1, "DRAMA" to 1, "OTHER" to 1), counts("SERIES"))
        assertEquals(Genre.OTHER.name, "OTHER")
    }
}
