package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 21 → 22 : table `channel_order` (ordre personnel par profil), vide au départ ; requêtes triées par place puis ordre naturel. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ChannelOrderMigration22Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate21to22_tableVide_chaineExistanteIntacte() {
        helper.createDatabase("p22", 21).apply {
            execSQL("INSERT INTO channel (providerId, remoteId, name, logo, categoryId, streamUrl, epgChannelId, catchupSource, catchupDays, userPosition, title, sortKey, num, seq, junk, isSeparator, country, quality, flags, lang, catRank, props) VALUES (1, 'c1', 'Chaîne', NULL, '9', 'http://serveur-fictif.invalid/1.ts', NULL, NULL, 0, 0, 'Chaîne', 'chaine', 0, 0, 0, 0, NULL, 0, 0, '', 0, NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("p22", 22, true, MIGRATION_21_22)
        db.query("SELECT COUNT(*) FROM channel_order").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT name FROM channel WHERE remoteId = 'c1'").use { it.moveToFirst(); assertEquals("Chaîne", it.getString(0)) }
    }

    private fun ch(rid: String, num: Int, cat: String = "9") =
        ChannelEntity(providerId = 1, remoteId = rid, name = "Chaine $rid", logo = null, categoryId = cat, streamUrl = "http://s.invalid/$rid.ts", num = num)

    @Test fun ordrePersonnel_parProfil_categorieFavorisEtFenetre() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries().build()
        val dao = room.channelDao()
        val order = room.channelOrderDao()
        dao.upsertAll(listOf(ch("a", 1), ch("b", 2), ch("c", 3), ch("d", 4)))
        room.favoriteDao().add(FavoriteEntity(1, "LIVE", "b", 1))
        room.favoriteDao().add(FavoriteEntity(1, "LIVE", "d", 1))
        room.favoriteDao().add(FavoriteEntity(1, "LIVE", "b", 2))

        // Sans ordre personnel : ordre naturel, pour tous les profils.
        assertEquals(listOf("a", "b", "c", "d"), dao.orderedKeysForCategory(1, "9", 1))
        assertFalse(order.has(1, 1, "9"))

        // Profil 1 : c, a (b et d sans place, à la suite en ordre naturel).
        order.upsertAll(listOf(ChannelOrderEntity(1, 1, "9", "c", 1), ChannelOrderEntity(1, 1, "9", "a", 2)))
        assertTrue(order.has(1, 1, "9"))
        assertEquals(listOf("c", "a", "b", "d"), dao.orderedKeysForCategory(1, "9", 1))
        assertEquals(listOf("c", "a", "b", "d"), dao.windowCategoryOrdered(1, "9", 1, 10, 0).map { it.remoteId })
        assertEquals(listOf("a", "b"), dao.windowCategoryOrdered(1, "9", 1, 2, 1).map { it.remoteId })
        // Rang dans l'ordre du profil : c = 0, a = 1, b = 2, d = 3.
        val b = dao.byRemoteId(1, "b")!!
        assertEquals(2, dao.rankCategoryOrdered(1, "9", 1, order.positionOf(1, 1, "9", "b") ?: Int.MAX_VALUE, b.num, b.id))
        val a = dao.byRemoteId(1, "a")!!
        assertEquals(1, dao.rankCategoryOrdered(1, "9", 1, order.positionOf(1, 1, "9", "a") ?: Int.MAX_VALUE, a.num, a.id))

        // Autre profil et autre catégorie : inchangés.
        assertEquals(listOf("a", "b", "c", "d"), dao.orderedKeysForCategory(1, "9", 2))
        assertFalse(order.has(2, 1, "9"))

        // Favoris : liste à part (scope « __fav__ »).
        assertEquals(listOf("b", "d"), dao.favoritesListOrdered(1, 1).map { it.remoteId })
        order.upsertAll(listOf(ChannelOrderEntity(1, 1, "__fav__", "d", 1), ChannelOrderEntity(1, 1, "__fav__", "b", 2)))
        assertEquals(listOf("d", "b"), dao.favoritesListOrdered(1, 1).map { it.remoteId })
        assertEquals(listOf("d", "b"), dao.orderedKeysForFavorites(1, 1))
        assertEquals(listOf("b"), dao.favoritesListOrdered(1, 2).map { it.remoteId })
        assertEquals(setOf("9", "__fav__"), order.observeScopes(1, 1).first().toSet())

        // Réinitialiser : retour à l'ordre naturel, l'autre liste est intacte.
        order.clear(1, 1, "9")
        assertEquals(listOf("a", "b", "c", "d"), dao.orderedKeysForCategory(1, "9", 1))
        assertEquals(listOf("d", "b"), dao.orderedKeysForFavorites(1, 1))

        // Suppression d'un profil : son ordre disparaît.
        order.deleteForProfile(1)
        assertFalse(order.has(1, 1, "__fav__"))
        room.close()
    }
}
