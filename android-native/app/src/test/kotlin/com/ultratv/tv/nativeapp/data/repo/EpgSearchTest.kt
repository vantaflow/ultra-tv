package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val H = 3_600_000L

class EpgSearchPureTest {
    private fun ch(id: Long, name: String = "Chaine $id") = ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = name, logo = null, categoryId = null, streamUrl = "u$id")
    private fun row(c: Long, t: String, s: Long, e: Long) = ProgramHitRow(ch(c), t, s, e)
    private val now = 100 * H

    @Test fun likePattern_accentsEtJokersEchappes() {
        assertEquals("%d_ss__s%", EpgSearch.likePattern("Dessins"))
        assertEquals("%_t_%", EpgSearch.likePattern("été"))
        assertNull(EpgSearch.likePattern("  "))
        assertEquals("%100%", EpgSearch.likePattern("100%_"))   // ponctuation et jokers du texte saisi ignorés
    }
    @Test fun select_accentsEtCasse() {
        val out = EpgSearch.select(listOf(row(1, "Les DESSINS animés", now - H, now + H), row(2, "Le Journal", now - H, now + H)), "dessins", now)
        assertEquals(listOf("Les DESSINS animés"), out.map { it.title })
        assertTrue(out[0].live)
        assertEquals(1, EpgSearch.select(listOf(row(1, "Été meurtrier", now, now + H)), "ete", now).size)
    }
    @Test fun select_uneLigneParChaine_enCoursPuisProche() {
        val out = EpgSearch.select(listOf(
            row(1, "Dessins B", now + 5 * H, now + 6 * H), row(1, "Dessins A", now + 2 * H, now + 3 * H),
            row(2, "Dessins C", now - H, now + H), row(2, "Dessins D", now + 9 * H, now + 10 * H),
        ), "dessins", now)
        assertEquals(listOf("Dessins C", "Dessins A"), out.map { it.title })   // en cours d'abord
        assertEquals(listOf(true, false), out.map { it.live })
    }
    @Test fun select_horsFenetre_exclu_etLimite24() {
        assertTrue(EpgSearch.select(listOf(row(1, "Dessins", now - 3 * H, now - H), row(2, "Dessins", now + 8 * 24 * H, now + 8 * 24 * H + H)), "dessins", now).isEmpty())
        val many = (1L..40L).map { row(it, "Dessins", now + it * H, now + it * H + H) }
        assertEquals(24, EpgSearch.select(many, "dessins", now).size)
    }
    @Test fun select_requeteVide_rien() = assertTrue(EpgSearch.select(listOf(row(1, "X", now, now + H)), " ", now).isEmpty())
}

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class EpgSearchDaoTest {
    @Test fun requeteRoom_accentsJokersFenetre_puisSelect() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), UltraDb::class.java).allowMainThreadQueries().build()
        val now = 100 * H
        db.channelDao().upsertAll(listOf(ChannelEntity(providerId = 1, remoteId = "a", name = "Canal A", logo = null, categoryId = null, streamUrl = "ua")))
        val c1 = db.channelDao().byRemoteId(1, "a")!!.id
        db.channelDao().upsertAll(listOf(ChannelEntity(providerId = 1, remoteId = "b", name = "Canal B", logo = null, categoryId = null, streamUrl = "ub")))
        val c2 = db.channelDao().byRemoteId(1, "b")!!.id
        db.channelDao().upsertAll(listOf(ChannelEntity(providerId = 2, remoteId = "c", name = "Autre", logo = null, categoryId = null, streamUrl = "uc")))
        val other = db.channelDao().byRemoteId(2, "c")!!.id
        db.epgDao().upsertAll(listOf(
            EpgEntity(channelId = c1, title = "Les Dessins animés", description = null, startMs = now + H, endMs = now + 2 * H),
            EpgEntity(channelId = c1, title = "Dessins du soir", description = null, startMs = now - H, endMs = now + H),
            EpgEntity(channelId = c2, title = "100% foot", description = null, startMs = now + 3 * H, endMs = now + 4 * H),
            EpgEntity(channelId = c2, title = "Dessins passés", description = null, startMs = now - 5 * H, endMs = now - 4 * H),
            EpgEntity(channelId = other, title = "Dessins ailleurs", description = null, startMs = now, endMs = now + H),
        ))
        fun rows(q: String) = runBlocking { db.epgDao().searchPrograms(1, EpgSearch.likePattern(q)!!, now, now + EpgSearch.WINDOW_MS, EpgSearch.SQL_LIMIT) }
        val hits = EpgSearch.select(rows("dessins"), "dessins", now)
        assertEquals(1, hits.size)                               // une ligne par chaîne, passé et autre source exclus
        assertEquals("Dessins du soir", hits[0].title); assertTrue(hits[0].live)
        assertEquals(1, EpgSearch.select(rows("100%"), "100%", now).size)
        assertFalse(rows("zzzz").isNotEmpty())
        db.close()
    }
}
