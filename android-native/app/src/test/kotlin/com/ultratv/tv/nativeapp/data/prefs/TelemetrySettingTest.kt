package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * « Télémétrie et rapports de plantage » remise dans les Réglages : le choix de l'utilisateur est de nouveau respecté
 * (pendant la grosse mise à jour, il était ignoré et la télémétrie toujours active). Valeur par défaut inchangée : active.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelemetrySettingTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries()
        .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback()).build()

    @After fun close() = db.close()

    @Test fun telemetrieCoupee_estRespectee_puisRetablie() = runBlocking {
        val store = UserPreferencesStore(ctx, ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default)))
        try {
            store.setTelemetry(false)
            assertFalse(store.flow.first().telemetryEnabled)
        } finally {
            store.setTelemetry(true)   // DataStore partagé par les tests de la même JVM : valeur par défaut remise
        }
        assertTrue(store.flow.first().telemetryEnabled)
    }

    @Test fun valeurParDefaut_active() {
        assertTrue(UserPrefs().telemetryEnabled)
    }
}
