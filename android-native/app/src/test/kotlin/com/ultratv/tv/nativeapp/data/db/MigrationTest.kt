package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migrations Room vérifiées sur une VRAIE base : on reconstruit la base de la version 10 à partir du schéma exporté
 * (schemas/…/10.json), on y met des données, puis on l'ouvre avec Room et les migrations 10→11 et 11→12 ; Room compare
 * alors la structure obtenue au schéma attendu (identityHash) et refuse la base si elle diffère.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    /** Crée « nom.db » à la version [version] d'après le schéma exporté correspondant. */
    private fun createFromSchema(name: String, version: Int): File {
        val json = JSONObject(File("schemas/com.ultratv.tv.nativeapp.data.db.UltraDb/$version.json").readText()).getJSONObject("database")
        val file = ctx.getDatabasePath(name).also { it.parentFile?.mkdirs(); it.delete() }
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = json.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val idx = e.getJSONArray("indices")
            for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = json.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        db.close()
        return file
    }

    private fun sql(file: File, block: (android.database.sqlite.SQLiteDatabase) -> Unit) {
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        try { block(db) } finally { db.close() }
    }

    @Test fun migration10a12_conserveLesDonnees_etLeSchemaObtenuEstValide() {
        val file = createFromSchema("m10.db", 10)
        sql(file) { db ->
            db.execSQL("INSERT INTO provider(id,name,kind,baseUrl,username,password,active) VALUES(1,'Source fictive','XTREAM','http://serveur-fictif.invalid','u','p',1)")
            db.execSQL("INSERT INTO channel(providerId,remoteId,name,logo,categoryId,streamUrl,epgChannelId,catchupSource,catchupDays,userPosition) VALUES(1,'c1','FR | TF1 HD',NULL,'9','http://serveur-fictif.invalid/live/1.ts',NULL,NULL,0,0)")
            db.execSQL("INSERT INTO movie(providerId,remoteId,name,poster,categoryId,streamUrl,container,year,rating,plot) VALUES(1,'m1','FR - Un Film (2020)',NULL,'3','http://serveur-fictif.invalid/m/1.mkv','mkv',2020,7.0,NULL)")
            db.execSQL("INSERT INTO favorite(providerId,kind,remoteId) VALUES(1,'LIVE','c1')")
        }

        val db = Room.databaseBuilder(ctx, UltraDb::class.java, "m10.db")
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val sdb = db.openHelper.writableDatabase     // ouvre ET valide le schéma contre le schéma courant
            assertEquals(14, sdb.version)
            // Données conservées
            sdb.query("SELECT name, title, sortKey, lang FROM channel").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("FR | TF1 HD", c.getString(0)); assertEquals("FR | TF1 HD", c.getString(1)); assertEquals("fr | tf1 hd", c.getString(2))
            }
            sdb.query("SELECT COUNT(*) FROM favorite").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            sdb.query("SELECT COUNT(*) FROM movie").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            // Recherche plein texte reconstruite (10→11)
            sdb.query("SELECT c.remoteId FROM channel c JOIN channel_fts ON c.id = channel_fts.docid WHERE channel_fts MATCH 'tf1*'").use { c -> assertTrue(c.moveToFirst()); assertEquals("c1", c.getString(0)) }
            // Nouveautés 11→12 : cache de détails et durée des épisodes
            sdb.execSQL("INSERT INTO vod_info(providerId,remoteId,plot,fetchedAt) VALUES(1,'m1','Synopsis fictif',1)")
            sdb.query("SELECT plot FROM vod_info WHERE remoteId='m1'").use { c -> assertTrue(c.moveToFirst()); assertEquals("Synopsis fictif", c.getString(0)) }
            sdb.query("SELECT duration FROM episode").use { }   // la colonne existe
        } finally { db.close() }
    }

    @Test fun migration11a12_depuisUneBaseVersion11() {
        val file = createFromSchema("m11.db", 11)
        sql(file) { db -> db.execSQL("INSERT INTO provider(id,name,kind,baseUrl,username,password,active,lastLiveSyncAt,lastVodSyncAt,lastSeriesSyncAt,lastEpgSyncAt,categoryFilter) VALUES(1,'S','XTREAM','http://serveur-fictif.invalid','u','p',1,0,0,0,0,-1)") }
        val db = Room.databaseBuilder(ctx, UltraDb::class.java, "m11.db").addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        try {
            val sdb = db.openHelper.writableDatabase
            assertEquals(14, sdb.version)
            sdb.query("SELECT COUNT(*) FROM provider").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        } finally { db.close() }
    }
}
