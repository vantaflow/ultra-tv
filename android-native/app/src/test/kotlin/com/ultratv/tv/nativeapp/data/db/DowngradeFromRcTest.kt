package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Incident 1.3.75-rc1 (base 25) → 1.3.75 (base 24) : Room reconstruit la base (rétrogradation destructive) mais n'appelle pas
 * onCreate, donc plus aucun profil : « Qui regarde ? » et les préférences n'émettaient jamais, écran noir permanent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DowngradeFromRcTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun open(name: String) = Room.databaseBuilder(ctx, UltraDb::class.java, name).allowMainThreadQueries()
        .addMigrations(*ALL_MIGRATIONS)
        .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8, 9)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .addCallback(DefaultProfileCallback())
        .build()

    private fun profileCount(db: UltraDb): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM profile").use { it.moveToFirst(); it.getInt(0) }

    @Test fun baseEn25_rouverteEn24_reconstruiteAvecProfilPrincipal() {
        val name = "downgrade-test.db"
        // Base « de la RC » : version 25, table propre à la 25, aucun profil exploitable.
        ctx.getDatabasePath(name).parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(ctx.getDatabasePath(name), null).use {
            it.execSQL("CREATE TABLE channel_group (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
            it.version = 25
        }
        val db = open(name)
        assertEquals(1, profileCount(db))
        db.openHelper.writableDatabase.query("SELECT name FROM profile WHERE id = 1").use { c -> c.moveToFirst(); assertEquals("Principal", c.getString(0)) }
        assertEquals(24, db.openHelper.writableDatabase.version)
        db.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE name = 'channel_group'").use { assertEquals(0, it.count) }
        db.close()
    }

    @Test fun baseSansProfil_estReparee_sansDoublonNiResurrection() {
        val name = "no-profile-test.db"
        var db = open(name)
        assertEquals(1, profileCount(db))
        // Déjà un profil : la réouverture n'en ajoute pas.
        db.close(); db = open(name)
        assertEquals(1, profileCount(db))
        // Base vidée de ses profils (état laissé par la 1.3.75 sur la box) : réparée à l'ouverture suivante.
        db.openHelper.writableDatabase.execSQL("DELETE FROM profile")
        assertEquals(0, profileCount(db))
        db.close(); db = open(name)
        assertEquals(1, profileCount(db))
        db.close()
    }
}
