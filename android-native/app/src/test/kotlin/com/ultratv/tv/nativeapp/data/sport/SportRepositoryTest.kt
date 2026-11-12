package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val H = 3_600_000L

/** Les requêtes de la rubrique Sport sur une vraie base Room (SQL, LIKE, fenêtre de temps, source, cache). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SportRepositoryTest {
    private val now = 100 * H

    private fun ch(remote: String, name: String, cat: String? = null, pid: Long = 1) =
        ChannelEntity(providerId = pid, remoteId = remote, name = name, logo = null, categoryId = cat, streamUrl = "u$remote")

    @Test fun chainesDeSport_parCategorieEtParNom_programmesDeLaFenetreSeulement() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), UltraDb::class.java).allowMainThreadQueries().build()
        db.categoryDao().upsertAll(listOf(
            CategoryEntity(providerId = 1, kind = "LIVE", remoteId = "c-sport", name = "FR | SPORTS"),
            CategoryEntity(providerId = 1, kind = "LIVE", remoteId = "c-cine", name = "Cinema"),
        ))
        db.channelDao().upsertAll(listOf(
            ch("a", "Chaine dans categorie sport", cat = "c-sport"),
            ch("b", "beIN SPORTS 1 HD", cat = "c-cine"),
            ch("c", "Generaliste", cat = "c-cine"),
            ch("d", "Transport TV", cat = "c-cine"),
            ch("e", "Sport autre source", pid = 2),
        ))
        fun id(r: String, pid: Long = 1) = runBlocking { db.channelDao().byRemoteId(pid, r)!!.id }
        val (a, b, c, d) = listOf(id("a"), id("b"), id("c"), id("d"))
        db.epgDao().upsertAll(listOf(
            EpgEntity(channelId = a, title = "Studio", description = "d", startMs = now - H, endMs = now + H),                  // chaîne de sport, en cours
            EpgEntity(channelId = b, title = "Ligue 1 : Lens - Auxerre", description = null, startMs = now + 2 * H, endMs = now + 4 * H),
            EpgEntity(channelId = b, title = "Trop loin", description = null, startMs = now + 60 * H, endMs = now + 61 * H),     // hors fenêtre 48 h
            EpgEntity(channelId = b, title = "Passe", description = null, startMs = now - 6 * H, endMs = now - 5 * H),            // terminé
            EpgEntity(channelId = c, title = "Live: Wydad - Raja", description = null, startMs = now - H, endMs = now + H),       // autre chaîne, reconnu
            EpgEntity(channelId = c, title = "Les Feux de l'amour", description = null, startMs = now - 2 * H, endMs = now + H),     // pas du sport
            EpgEntity(channelId = c, title = "Godzilla vs Kong", description = null, startMs = now + H, endMs = now + 3 * H),    // « vs » sans équipe
            EpgEntity(channelId = d, title = "Transport en commun", description = null, startMs = now - H, endMs = now + H),
        ))
        val repo = SportRepository(db.channelDao(), db.epgDao(), db.categoryDao())
        val data = repo.load(1, now)

        assertEquals(setOf("Chaine dans categorie sport", "beIN SPORTS 1 HD"), data.channels.map { it.name }.toSet())   // la source 2 et « Transport » sont écartés
        assertTrue(data.hasGuide)
        val titles = data.entries.map { it.prog.title }.toSet()
        assertEquals(setOf("Studio", "Ligue 1 : Lens - Auxerre", "Live: Wydad - Raja"), titles)
        assertTrue(data.entries.first { it.prog.title == "Studio" }.sportChannel)
        assertFalse(data.entries.first { it.prog.title == "Studio" }.strict)                    // plateau : pas une rencontre reconnue
        assertTrue(data.entries.first { it.prog.title == "Live: Wydad - Raja" }.strict)
        assertFalse(data.entries.first { it.prog.title == "Live: Wydad - Raja" }.sportChannel)
        assertTrue(data.entries.all { it.prog.description == null })                             // descriptions jamais chargées

        // Les listes de l'écran : « maintenant » = chaînes de sport d'abord ; « à venir » = rencontres reconnues seulement.
        val live = SportLogic.liveNow(data.entries, now)
        assertEquals(listOf("Studio", "Live: Wydad - Raja"), live.map { it.prog.title })
        val up = SportLogic.upcoming(data.entries, now, java.util.TimeZone.getTimeZone("UTC")).flatMap { it.entries }
        assertEquals(listOf("Ligue 1 : Lens - Auxerre"), up.map { it.prog.title })
        db.close()
    }

    @Test fun sansGuide_chainesQuandMeme_etSansProgrammes() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), UltraDb::class.java).allowMainThreadQueries().build()
        db.channelDao().upsertAll(listOf(ch("a", "Eurosport 1")))
        val data = SportRepository(db.channelDao(), db.epgDao(), db.categoryDao()).load(1, now)
        assertFalse(data.hasGuide)
        assertEquals(1, data.channels.size)
        assertTrue(data.entries.isEmpty())
        db.close()
    }

    @Test fun cache_invalidateRelitLaBase() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), UltraDb::class.java).allowMainThreadQueries().build()
        db.channelDao().upsertAll(listOf(ch("a", "DAZN 1")))
        val repo = SportRepository(db.channelDao(), db.epgDao(), db.categoryDao())
        assertEquals(1, repo.load(1, now).channels.size)
        db.channelDao().upsertAll(listOf(ch("b", "ESPN 2")))
        assertEquals(1, repo.load(1, now + 1000).channels.size)   // en cache : pas de nouvelle lecture
        repo.invalidate()
        assertEquals(2, repo.load(1, now + 2000).channels.size)
        db.close()
    }
}
