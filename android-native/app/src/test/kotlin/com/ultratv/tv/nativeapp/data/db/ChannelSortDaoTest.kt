package com.ultratv.tv.nativeapp.data.db

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Requêtes triées du Direct (tri par numéro, A → Z, dernières regardées) et recherches de numéro, sur une vraie base Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelSortDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries().build()
    private val dao get() = db.channelDao()

    private fun ch(id: Long, name: String, cat: String, seq: Int, num: Int, rank: Int = 1) =
        ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = name, logo = null, categoryId = cat, streamUrl = "x", seq = seq, num = num, catRank = rank)

    @Before fun setUp() = runBlocking {
        dao.upsertAll(listOf(
            ch(1, "Zeta", "a", 1, 30), ch(2, "Éclair", "a", 2, 10), ch(3, "alpha", "a", 3, 20),
            ch(4, "Bravo", "b", 1, 5, rank = 2),
        ))
    }

    @After fun close() = db.close()

    private fun names(l: List<ChannelEntity>) = l.map { it.name }

    @Test fun azSansCasseNiAccents() = runBlocking {
        assertEquals(listOf("alpha", "Éclair", "Zeta"), names(dao.categorySorted(1, "a", 1, ChannelSort2.AZ, 0)))
    }

    @Test fun parNumero_autoUtiliseSeq_sourceUtiliseNum() = runBlocking {
        assertEquals(listOf("Zeta", "Éclair", "alpha"), names(dao.categorySorted(1, "a", 1, ChannelSort2.NUMBER, 0)))
        assertEquals(listOf("Éclair", "alpha", "Zeta"), names(dao.categorySorted(1, "a", 1, ChannelSort2.NUMBER, 1)))
    }

    @Test fun dernieresRegardeesEnPremier_puisLesAutresDansLOrdreSource() = runBlocking {
        db.watchHistoryDao().upsert(WatchHistoryEntity(1, "LIVE", "r3", "alpha", null, "x", watchedAt = 1_000, profileId = 1))
        db.watchHistoryDao().upsert(WatchHistoryEntity(1, "LIVE", "r1", "Zeta", null, "x", watchedAt = 2_000, profileId = 1))
        db.watchHistoryDao().upsert(WatchHistoryEntity(1, "LIVE", "r2", "Éclair", null, "x", watchedAt = 9_000, profileId = 2))   // autre profil : ignoré
        assertEquals(listOf("Zeta", "alpha", "Éclair"), names(dao.categorySorted(1, "a", 1, ChannelSort2.RECENT, 0)))
    }

    @Test fun toutGardeLOrdreDesCategories_etTriDansChacune() = runBlocking {
        val ids = dao.allSortedIds(1, 1, ChannelSort2.AZ, 0)
        assertEquals(listOf(3L, 2L, 1L, 4L), ids)
    }

    @Test fun pagedAllSorted_memeOrdre() = runBlocking {
        val src = dao.pagedAllSorted(1, 0, listOf(""), 0, listOf(""), 1, ChannelSort2.AZ, 0)
        val page = src.load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page
        assertEquals(listOf("alpha", "Éclair", "Zeta", "Bravo"), names(page.data))
    }

    @Test fun chaineMasqueeAbsenteDesTris() = runBlocking {
        db.profileViewDao().hideAll(listOf(ProfileHiddenChannelEntity(1, 1, "r3", "a", "alpha")))
        db.profileViewDao().projectNow(1)
        assertEquals(listOf("Éclair", "Zeta"), names(dao.categorySorted(1, "a", 1, ChannelSort2.AZ, 0)))
    }

    @Test fun rechercheDeNumero() = runBlocking {
        assertEquals("Éclair", dao.bySourceNumber(1, 10)?.name)
        assertNull(dao.bySourceNumber(1, 99))
        assertEquals("Bravo", dao.byCategorySeq(1, "b", 1)?.name)
    }

    @Test fun comptageParCategorie_enExcluantLesMasquees() = runBlocking {
        db.profileViewDao().hideAll(listOf(ProfileHiddenChannelEntity(1, 1, "r1", "a", "Zeta")))
        db.profileViewDao().projectNow(1)
        val rows = db.profileViewDao().observeCategoryCounts().first()
        assertEquals(listOf(CatCountRow(1, "a", 2), CatCountRow(1, "b", 1)), rows)
    }
}

private object ChannelSort2 {
    val NUMBER = com.ultratv.tv.nativeapp.data.repo.ChannelSort.NUMBER.sql
    val AZ = com.ultratv.tv.nativeapp.data.repo.ChannelSort.AZ.sql
    val RECENT = com.ultratv.tv.nativeapp.data.repo.ChannelSort.RECENT.sql
}
