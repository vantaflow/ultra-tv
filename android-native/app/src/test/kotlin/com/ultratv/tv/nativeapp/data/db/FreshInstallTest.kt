package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Installation neuve : sans profil par défaut, « Qui regarde ? » ne répondait jamais (écran noir au premier lancement). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FreshInstallTest {
    @Test fun baseNeuve_creeLeProfilPrincipal() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries()
            .addCallback(DefaultProfileCallback()).build()
        db.openHelper.writableDatabase.query("SELECT id, name FROM profile").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(1L, c.getLong(0))
            assertEquals("Principal", c.getString(1))
        }
        db.close()
    }
}
