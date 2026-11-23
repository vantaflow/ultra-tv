package com.ultratv.tv.nativeapp.data.profile

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Code PROPRE à un profil (Réglages › Profils) : le retirer ou le changer exige le code actuel de ce profil, comme pour le
 * code parental (ParentalStore.clearWith / changeWith). Mauvais code ou annulation (aucun appel) : rien ne change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfilePinChangeTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries()
        .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback()).build()
    private lateinit var repo: ProfileRepository
    private var id = 0L

    @Before fun setUp() = runBlocking {
        repo = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        id = repo.create("Salon", 0xFF2266AA.toInt(), pin = "1234")!!
    }

    @After fun close() = db.close()

    private fun hash() = runBlocking { db.profileDao().byId(id)?.pinHash }

    @Test fun `retirer - bon code retire, mauvais code ne change rien`() = runBlocking {
        val before = hash()
        assertNotNull(before)
        assertFalse(repo.clearPinWith(id, "0000"))
        assertEquals(before, hash())
        assertTrue(repo.clearPinWith(id, "1234"))
        assertNull(hash())
    }

    @Test fun `changer - bon code remplace, mauvais code ou nouveau code vide ne change rien`() = runBlocking {
        val before = hash()
        assertFalse(repo.changePinWith(id, "9999", "5678"))
        assertEquals(before, hash())
        assertFalse(repo.changePinWith(id, "1234", ""))
        assertEquals(before, hash())
        assertTrue(repo.changePinWith(id, "1234", "5678"))
        assertTrue(repo.verifyPin(id, "5678"))
        assertFalse(repo.verifyPin(id, "1234"))
    }

    @Test fun `annulation - aucun appel, le code reste, et un premier code ne remplace jamais un code existant`() = runBlocking {
        val before = hash()
        assertFalse(repo.setFirstPin(id, "0000"))
        assertEquals(before, hash())
        assertTrue(repo.verifyPin(id, "1234"))
        // Profil sans code : le premier code se pose librement.
        assertTrue(repo.clearPinWith(id, "1234"))
        assertTrue(repo.setFirstPin(id, "4321"))
        assertTrue(repo.verifyPin(id, "4321"))
    }
}
