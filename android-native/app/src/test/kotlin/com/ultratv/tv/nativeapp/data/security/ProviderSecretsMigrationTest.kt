package com.ultratv.tv.nativeapp.data.security

import android.content.Context
import androidx.room.Room
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderSecretsMigrationTest {
    private lateinit var db: UltraDb
    private lateinit var dao: ProviderDao
    private lateinit var saved: SecretBox

    @Before fun setUp() {
        saved = SecretBox.shared
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        SecretBox.shared = SecretBox { key }
        db = Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication() as Context, UltraDb::class.java)
            .allowMainThreadQueries().build()
        dao = ProviderDao(db.providerDao())
    }

    @After fun tearDown() { db.close(); SecretBox.shared = saved }

    private fun entity(pw: String) =
        ProviderEntity(name = "p", kind = "XTREAM", baseUrl = "http://h", username = "u", password = pw)

    @Test fun ecriture_chiffreSurDisque_etLectureRestitueLeClair() = runBlocking {
        val id = dao.upsert(entity("s3cret"))
        val onDisk = db.providerDao().rawPasswords().single { it.id == id }.raw
        assertTrue(onDisk.startsWith(SecretBox.PREFIX))
        assertEquals("s3cret", dao.byId(id)!!.password)
    }

    @Test fun migration_rechiffreLesMotsDePasseHeritesEnClair() = runBlocking {
        // Ligne écrite par une ancienne version : on contourne la façade pour stocker du clair.
        val id = db.providerDao().upsert(entity("ancien"))
        assertEquals("ancien", db.providerDao().rawPasswords().single().raw)
        assertEquals("ancien", dao.byId(id)!!.password)           // lisible avant migration

        assertEquals(1, ProviderSecretsMigrator(dao).migrate())

        assertTrue(db.providerDao().rawPasswords().single().raw.startsWith(SecretBox.PREFIX))
        assertEquals("ancien", dao.byId(id)!!.password)           // et après
        assertEquals(0, ProviderSecretsMigrator(dao).migrate())   // idempotent
    }
}
