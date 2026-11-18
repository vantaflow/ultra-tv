package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ultratv.tv.nativeapp.data.repo.ManualChannelRepository
import com.ultratv.tv.nativeapp.data.repo.ManualChannels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 22 → 23 : table `manual_channel` vide au départ ; les chaînes manuelles survivent à une « synchro » (table `channel` vidée et réécrite). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ManualChannelMigration23Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate22to23_tableVide_chaineExistanteIntacte() {
        helper.createDatabase("p23", 22).apply {
            execSQL("INSERT INTO channel (providerId, remoteId, name, logo, categoryId, streamUrl, epgChannelId, catchupSource, catchupDays, userPosition, title, sortKey, num, seq, junk, isSeparator, country, quality, flags, lang, catRank, props) VALUES (1, 'c1', 'Chaîne', NULL, '9', 'http://serveur-fictif.invalid/1.ts', NULL, NULL, 0, 0, 'Chaîne', 'chaine', 0, 0, 0, 0, NULL, 0, 0, '', 0, NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("p23", 23, true, MIGRATION_22_23)
        db.query("SELECT COUNT(*) FROM manual_channel").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT name FROM channel WHERE remoteId = 'c1'").use { it.moveToFirst(); assertEquals("Chaîne", it.getString(0)) }
    }

    private fun prov(room: UltraDb) = runBlocking { room.providerDao().upsert(ProviderEntity(name = "Test", kind = "XTREAM", baseUrl = "http://s.invalid", username = "u", password = "p")) }

    private fun serverChannel(rid: String, cat: String, num: Int) =
        ChannelEntity(providerId = 1, remoteId = rid, name = "Chaine $rid", logo = null, categoryId = cat, streamUrl = "http://s.invalid/$rid.ts", num = num, seq = num)

    @Test fun chaineManuelle_survitALaSynchro_etSeRangeApresCellesDuFournisseur() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries().build()
        val pid = prov(room)
        assertEquals(1L, pid)
        val repo = ManualChannelRepository(room)
        // État après une première synchro : une catégorie et deux chaînes du fournisseur.
        room.categoryDao().upsertAll(listOf(CategoryEntity(providerId = 1, kind = "LIVE", remoteId = "9", name = "Sport")))
        room.channelDao().upsertAll(listOf(serverChannel("a", "9", 1), serverChannel("b", "9", 2)))

        val rid = repo.add(1, "Ma chaîne", "http://perso.invalid/flux.m3u8", null, "ma.tvg", "9", null)
        val rid2 = repo.add(1, "Autre", "http://perso.invalid/2.m3u8", null, null, ManualChannels.CUSTOM_CAT, "Mes chaînes")
        assertEquals(true, rid.startsWith("custom:"))
        var mine = room.channelDao().byRemoteId(1, rid)!!
        assertEquals(3, mine.seq); assertEquals(3, mine.num); assertEquals("ma.tvg", mine.epgChannelId)
        assertNotNull(room.categoryDao().forProviderKind(1, "LIVE").firstOrNull { it.remoteId == ManualChannels.CUSTOM_CAT })

        // « Synchro » : la table des chaînes ET celle des catégories sont vidées puis réécrites sans les chaînes manuelles.
        room.channelDao().deleteForProvider(1)
        room.categoryDao().deleteForProviderKind(1, "LIVE")
        room.categoryDao().upsertAll(listOf(CategoryEntity(providerId = 1, kind = "LIVE", remoteId = "9", name = "Sport")))
        room.channelDao().upsertAll(listOf(serverChannel("a", "9", 1), serverChannel("b", "9", 2), serverChannel("c", "9", 3)))
        assertNull(room.channelDao().byRemoteId(1, rid))
        ManualChannels.reinject(room, 1)
        mine = room.channelDao().byRemoteId(1, rid)!!
        assertEquals(4, mine.seq)                       // après a, b, c
        assertNotNull(room.channelDao().byRemoteId(1, rid2))
        assertNotNull(room.categoryDao().forProviderKind(1, "LIVE").firstOrNull { it.remoteId == ManualChannels.CUSTOM_CAT })
        // Rejouer ne change rien (idempotent, identifiant conservé).
        ManualChannels.reinject(room, 1)
        assertEquals(mine.id, room.channelDao().byRemoteId(1, rid)!!.id)

        // Modification : même identifiant fournisseur, autre nom / catégorie.
        repo.update(1, rid, "Renommée", "http://perso.invalid/x.m3u8", null, null, ManualChannels.CUSTOM_CAT, "Mes chaînes")
        val moved = room.channelDao().byRemoteId(1, rid)!!
        assertEquals("Renommée", moved.name); assertEquals(ManualChannels.CUSTOM_CAT, moved.categoryId)

        // Suppression : retirée de la table locale et de `channel`, plus jamais réinjectée.
        repo.delete(1, rid)
        ManualChannels.reinject(room, 1)
        assertNull(room.channelDao().byRemoteId(1, rid))
        assertNull(room.manualChannelDao().byRemoteId(1, rid))
        assertNotNull(room.channelDao().byRemoteId(1, rid2))
        room.close()
    }
}
