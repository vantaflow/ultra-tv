package com.ultratv.tv.nativeapp.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** #247 : une saisie en arabe trouve les titres quelle que soit la graphie (hamza, ة/ه, ى/ي, voyelles). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ArabicSearchDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries().build()
    @After fun close() = db.close()

    private fun movie(rid: String, name: String) =
        MovieEntity(providerId = 7, remoteId = rid, name = name, poster = null, categoryId = "c", streamUrl = "u/$rid", container = "mp4", year = 2020, rating = 7.0, plot = null)

    private fun movies(q: String) = runBlocking {
        ArabicFold.search(q, 100, { p, n -> db.movieDao().nameLike(7, p, n) }) { it.name }.map { it.remoteId }.sorted()
    }

    @Test fun films_graphiesDifferentes_trouvees() = runBlocking {
        db.movieDao().upsertAll(listOf(
            movie("1", "AR - أحمد حلمي"), movie("2", "مدرسة المشاغبين"), movie("3", "مُسَلْسَل الهيبة"),
            movie("4", "مصطفى"), movie("5", "Spider-Man"), movie("6", "محمود"),
        ))
        assertEquals(listOf("1"), movies("احمد"))
        assertEquals(listOf("1"), movies("احمد حلمي"))
        assertEquals(listOf("2"), movies("مدرسه"))
        assertEquals(listOf("3"), movies("مسلسل"))       // titre voyellé : repli lettre à lettre
        assertEquals(listOf("4"), movies("مصطفي"))
        assertEquals(emptyList<String>(), movies("spider")) // pas d'arabe : la recherche plein texte suffit
    }

    @Test fun series_et_chaines() = runBlocking {
        db.seriesDao().upsertAll(listOf(SeriesEntity(providerId = 7, remoteId = "s1", name = "الأسطورة", poster = null, categoryId = "c", year = null, rating = null, plot = null)))
        db.channelDao().upsertAll(listOf(ChannelEntity(providerId = 7, remoteId = "c1", name = "AR: قناة الجزيرة", logo = null, categoryId = "c", streamUrl = "u/c1")))
        assertEquals(listOf("s1"), ArabicFold.search("الاسطوره", 50, { p, n -> db.seriesDao().nameLike(7, p, n) }) { it.name }.map { it.remoteId })
        assertEquals(listOf("c1"), ArabicFold.search("قناه", 50, { p, n -> db.channelDao().nameLike(7, p, n) }) { it.name }.map { it.remoteId })
    }
}
