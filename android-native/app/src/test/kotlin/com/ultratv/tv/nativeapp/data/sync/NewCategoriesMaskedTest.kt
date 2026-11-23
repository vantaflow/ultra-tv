package com.ultratv.tv.nativeapp.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore
import com.ultratv.tv.nativeapp.data.prefs.NewCategoriesStore
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.CatalogPlan
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.stalker.StalkerClient
import com.ultratv.tv.nativeapp.data.stalker.StalkerEndpointStore
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Règles pures : nouvelles catégories masquées, renommage, première synchro, bibliothèques exclues. */
class NewCategoriesMaskRulesTest {
    private fun cat(id: String, name: String, enabled: Boolean = true, position: Int = 0, locked: Boolean = false) =
        CategoryEntity(providerId = 1, kind = "LIVE", remoteId = id, name = name, locked = locked, enabled = enabled, position = position)

    @Test fun `premiere synchro inchangee, synchros suivantes masquent l'inconnue`() {
        assertFalse(CatalogPlan.shouldMaskNew("XTREAM", emptyList()))
        // Seule « Mes chaînes » (ajoutée à la main) : c'est encore la première synchro.
        assertFalse(CatalogPlan.shouldMaskNew("M3U", listOf(cat(com.ultratv.tv.nativeapp.data.repo.ManualChannels.CUSTOM_CAT, "Mes chaînes"))))
        assertTrue(CatalogPlan.shouldMaskNew("XTREAM", listOf(cat("1", "A"))))
        assertTrue(CatalogPlan.shouldMaskNew("M3U", listOf(cat("1", "A"))))
        assertTrue(CatalogPlan.shouldMaskNew("STALKER", listOf(cat("1", "A"))))
        for (k in listOf("JELLYFIN", "PLEX", "EMBY", "NAS")) assertFalse(k, CatalogPlan.shouldMaskNew(k, listOf(cat("1", "A"))))
    }

    @Test fun `nouvelle categorie desactivee et listee, existantes inchangees`() {
        val before = listOf(cat("1", "A", enabled = false, position = 3), cat("2", "B"))
        val m = CatalogPlan.mergeDetailed(listOf(cat("1", "A"), cat("2", "B"), cat("3", "C")), before, emptySet(), maskNew = true)
        assertEquals(listOf("3"), m.newMasked)
        val byId = m.categories.associateBy { it.remoteId }
        assertFalse(byId.getValue("3").enabled)
        assertFalse(byId.getValue("1").enabled); assertEquals(3, byId.getValue("1").position)
        assertTrue(byId.getValue("2").enabled)
        // Sans masquage (première synchro) : comportement d'avant.
        assertTrue(CatalogPlan.mergeDetailed(listOf(cat("3", "C")), emptyList(), emptySet(), maskNew = false).categories.single().enabled)
    }

    @Test fun `identifiant change, nom identique - meme categorie, etat repris`() {
        val before = listOf(cat("10", "Sport FR", enabled = true, position = 5, locked = true), cat("11", "Cinéma", enabled = false))
        val m = CatalogPlan.mergeDetailed(listOf(cat("99", " Sport FR "), cat("98", "Cinéma")), before, emptySet(), maskNew = true)
        assertEquals(emptyList<String>(), m.newMasked)
        val byId = m.categories.associateBy { it.remoteId }
        assertTrue(byId.getValue("99").enabled); assertTrue(byId.getValue("99").locked); assertEquals(5, byId.getValue("99").position)
        assertFalse(byId.getValue("98").enabled)
    }

    @Test fun `langue exclue - geree par le filtre des langues, pas listee comme nouvelle`() {
        val m = CatalogPlan.mergeDetailed(listOf(cat("1", "A"), cat("7", "DE| FILME")), listOf(cat("1", "A")), setOf("fr"), maskNew = true)
        assertEquals(emptyList<String>(), m.newMasked)
        assertFalse(m.categories.first { it.remoteId == "7" }.enabled)
    }
}

/** Synchro M3U de bout en bout (faux serveur en mémoire) : masquage des nouvelles, liste refusée sans effet, signalement. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class NewCategoriesMaskedTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries()
        .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback()).build()
    @After fun close() = db.close()

    private var playlist = ""

    private fun m3u(vararg groups: Pair<String, Int>): String = buildString {
        append("#EXTM3U\n")
        var n = 0
        for ((g, count) in groups) repeat(count) { append("#EXTINF:-1 tvg-id=\"c$n\" group-title=\"$g\",Chaine $n\nhttp://flux.invalid/$n.ts\n"); n++ }
    }

    private fun repo(store: NewCategoriesStore): Pair<ProviderRepository, Long> {
        val ok = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(playlist.toResponseBody("audio/x-mpegurl".toMediaType())).build()
        }.build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val prefs = UserPreferencesStore(ctx, profiles)
        val repo = ProviderRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = SyncStatusBus(), xmltv = XmltvParser(ok), epgDao = db.epgDao(), db = db, prefs = prefs,
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx), langDisabled = LangDisabledStore(ctx),
            hiddenChannels = com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()), newCategories = store,
        )
        val id = runBlocking { db.providerDao().upsert(ProviderEntity(name = "liste", kind = "M3U", baseUrl = "http://liste.invalid/l.m3u", username = "", password = "", active = true)) }
        return repo to id
    }

    private fun cats(id: Long) = runBlocking { db.categoryDao().forProviderKind(id, "LIVE").associateBy { it.name } }

    @Test fun `premiere synchro tout actif, puis la nouvelle categorie arrive masquee et signalee`() = runBlocking {
        val store = NewCategoriesStore(ctx).also { it.clear() }
        val (r, id) = repo(store)
        playlist = m3u("Sport" to 20, "Infos" to 20)
        r.syncM3u(id)
        assertTrue(cats(id).values.all { it.enabled })
        assertEquals(emptySet<String>(), store.keys.value)

        playlist = m3u("Sport" to 20, "Infos" to 20, "Nouveautés" to 10)
        r.syncM3u(id)
        val c = cats(id)
        assertFalse(c.getValue("Nouveautés").enabled)
        assertTrue(c.getValue("Sport").enabled && c.getValue("Infos").enabled)
        assertEquals(setOf(NewCategoriesStore.key(id, "LIVE", "g:Nouveautés")), store.keys.value)
        // Aucune chaîne d'une catégorie masquée n'est chargée.
        assertEquals(40, db.channelDao().countProvided(id))
        // L'utilisateur l'a vue : la ligne disparaît ; une synchro de plus ne la recompte pas (elle est connue).
        store.clear()
        r.syncM3u(id)
        assertEquals(emptySet<String>(), store.keys.value)
        assertFalse(cats(id).getValue("Nouveautés").enabled)
    }

    @Test fun `liste refusee par la garde - rien n'est cree ni signale`() = runBlocking {
        val store = NewCategoriesStore(ctx).also { it.clear() }
        val (r, id) = repo(store)
        playlist = m3u("Sport" to 600, "Infos" to 600)
        r.syncM3u(id)
        // Liste effondrée (3 chaînes au lieu de 1 200) avec une catégorie inconnue : refusée par CatalogGuard, base inchangée.
        playlist = m3u("Inconnue" to 3)
        runCatching { r.syncM3u(id) }
        assertEquals(setOf("Sport", "Infos"), cats(id).keys)
        assertEquals(emptySet<String>(), store.keys.value)
    }
}
