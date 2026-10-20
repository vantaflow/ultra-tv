package com.ultratv.tv.nativeapp.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 17 → 18 : colonne catRank + index de la liste « Tout », SANS réécriture des lignes (remplissage différé, voir
 * LiveOrderBackfill) ; les chaînes, leur recherche plein texte et l'ancien ordre joint restent intacts.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PerfMigration18Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    private fun ch(rid: String, cat: String?, num: Int, name: String = "Chaîne $rid") =
        "INSERT INTO channel (providerId, remoteId, name, logo, categoryId, streamUrl, catchupDays, userPosition, title, sortKey, num, seq, junk, isSeparator, quality, flags, lang) " +
            "VALUES (7, '$rid', '$name', NULL, ${cat?.let { "'$it'" } ?: "NULL"}, 'u', 0, 0, '$name', '${name.lowercase()}', $num, 0, 0, 0, 0, 0, '')"

    @Test
    fun migrate17to18_colonneEtIndex_lignesIntactesEtNonReecrites() {
        helper.createDatabase("p18", 17).apply {
            execSQL("INSERT INTO category (providerId, kind, remoteId, name, locked, enabled, position, lang) VALUES (7, 'LIVE', 'b', 'B', 0, 1, 1, '')")
            execSQL("INSERT INTO category (providerId, kind, remoteId, name, locked, enabled, position, lang) VALUES (7, 'LIVE', 'a', 'A', 0, 1, 0, '')")
            execSQL(ch("1", "a", 1, "Alpha"))
            execSQL(ch("2", "b", 2, "Bravo"))
            execSQL(ch("3", null, 3, "Charlie"))
            close()
        }
        val db = helper.runMigrationsAndValidate("p18", 18, true, MIGRATION_17_18)
        // Aucune ligne réécrite pendant l'ouverture de la base : toutes à 0 (« pas encore calculé »).
        db.query("SELECT COUNT(*) FROM channel WHERE catRank = 0").use { it.moveToFirst(); assertEquals(3, it.getInt(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'channel'").use { c ->
            val idx = generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
            assertTrue(idx.toString(), "index_channel_providerId_junk_isSeparator_catRank_num" in idx)
        }
        // Recherche plein texte intacte.
        db.query("SELECT c.remoteId FROM channel c JOIN channel_fts ON c.id = channel_fts.docid WHERE channel_fts MATCH 'bravo*'").use { it.moveToFirst(); assertEquals("2", it.getString(0)) }
        // Repli : l'ancien ordre joint fonctionne sur la base migrée (placée « b », sans catégorie, non placée « a »).
        db.query(
            "SELECT ch.remoteId FROM channel ch LEFT JOIN category k ON k.providerId = ch.providerId AND k.kind = 'LIVE' AND k.remoteId = ch.categoryId " +
                "WHERE ch.providerId = 7 ORDER BY CASE WHEN COALESCE(k.position, 0) = 0 THEN 1 ELSE 0 END, k.position, k.id, ch.num, ch.id",
        ).use { c -> assertEquals(listOf("2", "3", "1"), generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList()) }
    }
}
