package com.ultratv.tv.nativeapp.data.parental

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Chaînes d'une catégorie du Direct verrouillée : leurs clés rejoignent celles des chaînes verrouillées une à une. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParentalLocksDbTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries().build()

    @After fun close() = db.close()

    @Test fun chainesDesCategoriesVerrouillees_seulementLeDirect() = runBlocking {
        val pid = db.providerDao().upsert(ProviderEntity(name = "p", kind = "XTREAM", baseUrl = "http://127.0.0.1", username = "u", password = "p", active = true))
        db.categoryDao().upsertAll(listOf(
            CategoryEntity(providerId = pid, kind = "LIVE", remoteId = "A", name = "Adultes", locked = true),
            CategoryEntity(providerId = pid, kind = "LIVE", remoteId = "N", name = "Infos"),
            CategoryEntity(providerId = pid, kind = "MOVIE", remoteId = "N", name = "Films", locked = true),
        ))
        fun ch(rid: String, cat: String) = ChannelEntity(providerId = pid, remoteId = rid, name = "C$rid", logo = null, categoryId = cat, streamUrl = "u$rid")
        db.channelDao().upsertAll(listOf(ch("1", "A"), ch("2", "A"), ch("3", "N")))
        assertEquals(setOf("$pid:1", "$pid:2"), db.channelDao().observeCategoryLockedKeys().first().toSet())
        db.categoryDao().setLockedRemote(pid, "LIVE", "A", false)
        assertEquals(emptyList<String>(), db.channelDao().observeCategoryLockedKeys().first())
    }
}
