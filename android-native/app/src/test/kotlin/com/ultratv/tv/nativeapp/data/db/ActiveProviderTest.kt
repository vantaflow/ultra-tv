package com.ultratv.tv.nativeapp.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Source « active » : la marquée, sinon la première (la recherche ne doit jamais rester sans source). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ActiveProviderTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries().build()
    @After fun close() = db.close()
    private fun src(id: Long, active: Boolean) = ProviderEntity(id = id, name = "S$id", kind = "XTREAM", baseUrl = "http://s.invalid", username = "u", password = "p", active = active)

    @Test fun aucuneSource_null() = runBlocking { assertNull(db.providerDao().firstActiveId()) }

    @Test fun sourceMarqueeActive_prioritaire() = runBlocking {
        db.providerDao().upsert(src(1, false)); db.providerDao().upsert(src(2, true))
        assertEquals(2L, db.providerDao().firstActiveId())
    }

    @Test fun aucuneMarqueeActive_laPremiere() = runBlocking {
        db.providerDao().upsert(src(5, false)); db.providerDao().upsert(src(3, false))
        assertEquals(3L, db.providerDao().firstActiveId())
    }
}
