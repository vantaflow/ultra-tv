package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.ChannelGroupRepository
import com.ultratv.tv.nativeapp.data.repo.ChannelOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 24 → 25 : tables des groupes de chaînes créées vides, rien d'existant réécrit ; puis comportement : groupes par profil et
 * par source, ordre d'ajout modifiable, chaînes absentes non affichées, exclusion de catégories (profil Enfants),
 * suppression avec le profil.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ChannelGroupMigration25Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate24to25_tablesVides_donneesExistantesIntactes() {
        helper.createDatabase("g25", 24).apply {
            execSQL("INSERT INTO profile (id, name, color, initial, isKids, pinHash, createdAt) VALUES (1, 'Moi', 0, 'M', 0, NULL, 0)")
            execSQL("INSERT INTO channel (providerId, remoteId, name, logo, categoryId, streamUrl, epgChannelId, catchupSource, catchupDays, userPosition, title, sortKey, num, seq, junk, isSeparator, country, quality, flags, lang, catRank, props) VALUES (1, 'c1', 'Chaîne', NULL, 'a', 'http://serveur-fictif.invalid/1.ts', NULL, NULL, 0, 0, 'Chaîne', 'chaine', 0, 0, 0, 0, NULL, 0, 0, '', 0, NULL)")
            execSQL("INSERT INTO channel_order (profileId, providerId, scope, remoteId, position) VALUES (1, 1, '__fav__', 'c1', 1)")
            execSQL("INSERT INTO favorite (providerId, kind, remoteId, profileId) VALUES (1, 'LIVE', 'c1', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("g25", 25, true, MIGRATION_24_25)
        db.query("SELECT COUNT(*) FROM channel_group").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM channel_group_member").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT name FROM channel WHERE remoteId = 'c1'").use { it.moveToFirst(); assertEquals("Chaîne", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM channel_order").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM favorite").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT name FROM profile WHERE id = 1").use { it.moveToFirst(); assertEquals("Moi", it.getString(0)) }
    }

    private val pid = 1L
    private fun chan(rid: String, cat: String, num: Int) = ChannelEntity(providerId = pid, remoteId = rid, name = "Chaine $rid", logo = null, categoryId = cat, streamUrl = "u/$rid", num = num, seq = num)

    private class Rig(val room: UltraDb, val profiles: ProfileRepository, val repo: ChannelGroupRepository)

    private fun rig(): Rig {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries().build()
        val profiles = ProfileRepository(room.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        runBlocking {
            room.providerDao().upsert(ProviderEntity(id = pid, name = "S", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid", username = "u", password = ""))
            room.profileDao().insert(ProfileEntity(id = 1, name = "Moi", color = 0, initial = "M"))
            room.profileDao().insert(ProfileEntity(id = 2, name = "Maman", color = 0, initial = "M"))
            room.channelDao().upsertAll(listOf(chan("a", "9", 1), chan("b", "9", 2), chan("c", "adultes", 3)))
            switch(profiles, 1)
        }
        return Rig(room, profiles, ChannelGroupRepository(room.channelGroupDao(), profiles))
    }

    private suspend fun switch(p: ProfileRepository, id: Long) { p.select(id); p.current.first { it?.id == id } }

    private suspend fun paged(r: Rig, id: Long, ex: List<String> = emptyList()): List<String> {
        val res = r.repo.paged(pid, id, 0, listOf(""), ex).load(PagingSource.LoadParams.Refresh(null, 50, false))
        return (res as PagingSource.LoadResult.Page).data.map { it.remoteId }
    }

    @Test fun groupe_creation_ajout_ordre_retrait_suppression() = runBlocking {
        val r = rig()
        val g = r.repo.create(pid, "  Sport  ", "b")!!
        assertEquals("Sport", r.repo.groups(pid).single().name)
        assertTrue(r.repo.add(g, "a"))
        assertFalse(r.repo.add(g, "a"))   // déjà présente
        assertTrue(r.repo.add(g, "zz"))   // absente de la source : notée, jamais affichée
        assertEquals(listOf("b", "a"), paged(r, g))
        assertEquals(listOf("b", "a"), r.repo.channels(pid, g, emptyList()).map { it.remoteId })
        assertEquals(setOf(g), r.repo.groupsOf(pid, "a"))
        // Monter « a » : l'ordre suit.
        r.repo.setOrder(g, ChannelOrder.move(r.repo.keys(g), "a", -1))
        assertEquals(listOf("a", "b"), paged(r, g))
        // Compteur = chaînes affichables seulement.
        assertEquals(2, r.room.channelGroupDao().observeGroups(1, pid, 0, emptyList()).first().single().n)
        assertTrue(r.repo.remove(g, "a"))
        assertEquals(listOf("b"), paged(r, g))
        assertTrue(r.repo.rename(g, "Foot"))
        assertFalse(r.repo.rename(g, "   "))
        assertEquals("Foot", r.repo.groups(pid).single().name)
        r.repo.delete(g)
        assertTrue(r.repo.groups(pid).isEmpty())
        assertEquals(0, r.room.channelGroupDao().memberCount(g))
        assertNull(r.repo.create(pid, "   "))
        r.room.close()
    }

    @Test fun groupes_parProfil_exclusionEnfants_supprimesAvecLeProfil() = runBlocking {
        val r = rig()
        val g = r.repo.create(pid, "Mix", "a")!!
        r.repo.add(g, "c")
        assertEquals(listOf("a", "c"), paged(r, g))
        // Catégorie exclue (adultes d'un profil Enfants) : chaîne retirée de la liste et du compteur.
        assertEquals(listOf("a"), paged(r, g, listOf("adultes")))
        assertEquals(1, r.room.channelGroupDao().observeGroups(1, pid, 1, listOf("adultes")).first().single().n)
        // Autre profil : ne voit ni ne modifie le groupe.
        switch(r.profiles, 2)
        assertTrue(r.repo.groups(pid).isEmpty())
        assertFalse(r.repo.add(g, "b"))
        assertTrue(r.repo.channels(pid, g, emptyList()).isEmpty())
        r.repo.delete(g)
        switch(r.profiles, 1)
        assertEquals(1, r.repo.groups(pid).size)
        // Suppression du profil 1 : ses groupes et leurs chaînes partent avec lui.
        switch(r.profiles, 2)
        assertTrue(r.profiles.delete(1))
        assertEquals(0, r.room.channelGroupDao().countGroups(1, pid))
        assertEquals(0, r.room.channelGroupDao().memberCount(g))
        r.room.close()
    }
}
