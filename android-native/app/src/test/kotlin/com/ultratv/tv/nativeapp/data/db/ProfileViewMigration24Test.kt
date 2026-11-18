package com.ultratv.tv.nativeapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ultratv.tv.nativeapp.data.prefs.HiddenChannel
import com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import com.ultratv.tv.nativeapp.data.repo.ProfileChannelHiding
import com.ultratv.tv.nativeapp.data.repo.ProfileSwitcher
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 23 → 24 : tables des réglages de Direct par profil, ordre des catégories recopié pour chaque profil existant ; puis
 * comportement : chaîne masquée par un profil = encore visible pour l'autre, supprimée seulement si tous la masquent ;
 * ordre des catégories propre à chaque profil, rang des chaînes recalculé au changement de profil.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ProfileViewMigration24Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), UltraDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun migrate23to24_ordreDesCategoriesRecopiePourChaqueProfil_chaineIntacte() {
        helper.createDatabase("p24", 23).apply {
            execSQL("INSERT INTO profile (id, name, color, initial, isKids, pinHash, createdAt) VALUES (1, 'Moi', 0, 'M', 0, NULL, 0), (2, 'Maman', 0, 'M', 0, NULL, 0)")
            execSQL("INSERT INTO category (providerId, kind, remoteId, name, locked, enabled, position, lang) VALUES (1, 'LIVE', 'a', 'A', 0, 1, 2, ''), (1, 'LIVE', 'b', 'B', 0, 1, 1, ''), (1, 'LIVE', 'c', 'C', 0, 1, 0, '')")
            execSQL("INSERT INTO channel (providerId, remoteId, name, logo, categoryId, streamUrl, epgChannelId, catchupSource, catchupDays, userPosition, title, sortKey, num, seq, junk, isSeparator, country, quality, flags, lang, catRank, props) VALUES (1, 'c1', 'Chaîne', NULL, 'a', 'http://serveur-fictif.invalid/1.ts', NULL, NULL, 0, 0, 'Chaîne', 'chaine', 0, 0, 0, 0, NULL, 0, 0, '', 0, NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate("p24", 24, true, MIGRATION_23_24)
        // Deux catégories placées × deux profils ; la catégorie non placée (0) n'est pas recopiée (ordre du fournisseur).
        db.query("SELECT COUNT(*) FROM profile_category_order").use { it.moveToFirst(); assertEquals(4, it.getInt(0)) }
        db.query("SELECT position FROM profile_category_order WHERE profileId = 2 AND remoteId = 'a'").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM profile_hidden_channel").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM channel_hidden_now").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT name FROM channel WHERE remoteId = 'c1'").use { it.moveToFirst(); assertEquals("Chaîne", it.getString(0)) }
    }

    private val pid = 1L
    private fun chan(rid: String, cat: String, num: Int) = ChannelEntity(providerId = pid, remoteId = rid, name = "Chaine $rid", logo = null, categoryId = cat, streamUrl = "u/$rid", num = num, seq = num)

    private class Rig(val room: UltraDb, val hiding: ProfileChannelHiding, val switcher: ProfileSwitcher, val store: HiddenChannelsStore)

    private fun rig(): Rig {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries().build()
        val store = HiddenChannelsStore(ctx).also { it.replace(pid, emptyList()) }
        val hiding = ProfileChannelHiding(room, store, SyncCoordinator(ctx), ctx)
        runBlocking {
            room.providerDao().upsert(ProviderEntity(id = pid, name = "S", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid", username = "u", password = ""))
            room.profileDao().insert(ProfileEntity(id = 1, name = "Moi", color = 0, initial = "M"))
            room.profileDao().insert(ProfileEntity(id = 2, name = "Maman", color = 0, initial = "M"))
            room.categoryDao().upsertAll(listOf(CategoryEntity(providerId = pid, kind = "LIVE", remoteId = "9", name = "Cat 9")))
            room.channelDao().upsertAll(listOf(chan("a", "9", 1), chan("b", "9", 2), chan("c", "9", 3)).map { it.copy(catRank = 1) })
        }
        return Rig(room, hiding, ProfileSwitcher(room, hiding), store)
    }

    private fun visible(r: Rig) = runBlocking { r.room.channelDao().windowCategory(pid, "9", 50, 0).map { it.remoteId } }
    private fun visibleAll(r: Rig) = runBlocking { r.room.channelDao().allAfter(pid, 0, 0, 0, 50, 0, listOf(""), 0, listOf("")).map { it.remoteId } }
    private val b = listOf(HiddenChannel("b", "9", "Chaine b"))

    @Test fun chaineMasqueeParUnProfil_resteVisiblePourLautre_supprimeeSeulementSiTousLaMasquent() = runBlocking {
        val r = rig()
        r.switcher.onSwitch(1)
        r.hiding.hide(1, pid, b, current = 1)
        // Profil 1 (courant) : « b » filtrée à la lecture (Tout, catégorie, favoris), mais toujours en base.
        assertEquals(listOf("a", "c"), visible(r)); assertEquals(listOf("a", "c"), visibleAll(r))
        assertEquals("b", r.room.channelDao().byRemoteId(pid, "b")?.remoteId)
        assertTrue(r.store.list(pid).isEmpty())
        // Profil 2 : la voit.
        r.switcher.onSwitch(2)
        assertEquals(listOf("a", "b", "c"), visible(r)); assertEquals(listOf("a", "b", "c"), visibleAll(r))
        // Compteurs : retranchés pour le profil courant seulement.
        r.switcher.onSwitch(1)
        assertEquals(2, r.room.channelDao().countLive(pid))
        assertEquals(2, r.room.channelDao().observeCategoryCounts(pid).let { it.first() }.first { c -> c.categoryId == "9" }.n)
        r.switcher.onSwitch(2)
        assertEquals(3, r.room.channelDao().countLive(pid))
        // Le profil 2 la masque aussi : unanimité → supprimée de la base et ignorée par la synchro, numéros resserrés.
        r.hiding.hide(2, pid, b, current = 2)
        assertEquals(null, r.room.channelDao().byRemoteId(pid, "b"))
        assertEquals(listOf("b"), r.store.list(pid).map { it.id })
        assertEquals(listOf(1, 2), r.room.channelDao().seqsInCategory(pid, "9").map { it.seq })
        // Un profil créé ensuite hérite de la suppression (rien à recharger).
        r.room.profileDao().insert(ProfileEntity(id = 3, name = "Invité", color = 0, initial = "I"))
        r.hiding.inheritAll(3)
        assertEquals(listOf("b"), r.hiding.listOf(3, pid).map { it.id })
        // Le profil 2 la réaffiche : plus unanime → retirée de la liste « par tous » (la synchro la rechargera).
        runCatching { r.hiding.show(2, pid, listOf("b"), current = 2) }
        assertTrue(r.store.list(pid).isEmpty())
        assertFalse(r.hiding.listOf(2, pid).any { it.id == "b" })
        r.room.close()
    }

    @Test fun ordreDesCategories_parProfil_rangsRecalculesAuChangementDeProfil() = runBlocking {
        val r = rig()
        val cats = r.room.categoryDao()
        cats.upsertAll(listOf(CategoryEntity(providerId = pid, kind = "LIVE", remoteId = "8", name = "Cat 8")))
        r.room.channelDao().upsertAll(listOf(chan("x", "8", 1).copy(catRank = 2)))
        r.switcher.onSwitch(1)
        // Profil 2 : « 8 » avant « 9 » ; profil 1 : sans ordre (ordre du fournisseur : 9 puis 8).
        r.switcher.saveOrderFor(2, pid, "LIVE", listOf("8", "9"))
        r.switcher.onSwitch(2)
        assertEquals(listOf("8", "9"), cats.orderedIds(pid, "LIVE"))
        assertEquals(1, r.room.channelDao().byRemoteId(pid, "x")!!.catRank)
        assertEquals(2, r.room.channelDao().byRemoteId(pid, "a")!!.catRank)
        // Retour au profil 1 : son ordre revient, les rangs aussi.
        r.switcher.onSwitch(1)
        assertEquals(listOf("9", "8"), cats.orderedIds(pid, "LIVE"))
        assertEquals(3, r.room.channelDao().byRemoteId(pid, "x")!!.catRank)
        assertEquals(2, r.room.channelDao().byRemoteId(pid, "a")!!.catRank)
        r.room.close()
    }
}
