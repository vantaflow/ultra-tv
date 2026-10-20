package com.ultratv.tv.nativeapp.data.repo

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ordre de « Tout » (liste du Direct ET zapping) : la nouvelle pagination par clé sur catRank doit rendre EXACTEMENT
 * l'ordre de l'ancienne requête jointe (ORDER BY CASE position… , k.position, k.id, num, id), sur un jeu varié :
 * catégories réordonnées, non placées, désactivée, chaînes sans catégorie ou de catégorie inconnue, « +1 », numéros
 * en double ou nuls, séparateurs et entrées masquées (exclus), autre source (jamais mêlée).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class LiveOrderTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries().build()
    private val ch = db.channelDao()
    private val cats = db.categoryDao()
    @After fun close() = db.close()

    private val pid = 7L

    private fun cat(rid: String, position: Int, enabled: Boolean = true) =
        CategoryEntity(providerId = pid, kind = "LIVE", remoteId = rid, name = "Cat $rid", position = position, enabled = enabled)

    private fun chan(rid: String, cat: String?, num: Int, name: String = "Chaîne $rid", lang: String = "fr", provider: Long = pid) =
        ChannelEntity(providerId = provider, remoteId = rid, name = name, logo = null, categoryId = cat, streamUrl = "u/$rid", num = num, lang = lang)

    /** Jeu de données : insère les catégories puis les chaînes ; [stamp] = rangs posés à l'insertion (comme la synchro). */
    private fun seed(stamp: Boolean) = runBlocking {
        db.providerDao().upsert(com.ultratv.tv.nativeapp.data.db.ProviderEntity(id = pid, name = "S", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid", username = "u", password = ""))
        // Ordre d'insertion ≠ ordre d'affichage : les id des catégories départagent les non placées.
        cats.upsertAll(listOf(cat("sport", 3), cat("news", 0), cat("kids", 1), cat("film", 0), cat("off", 2, enabled = false), cat("docs", 2)))
        cats.upsertAll(listOf(CategoryEntity(providerId = pid, kind = "MOVIE", remoteId = "sport", name = "VOD", position = 1)))
        val rows = listOf(
            chan("s1", "sport", 5), chan("s2", "sport", 1), chan("s3", "sport", 1), chan("s4", "sport", 0),
            chan("n1", "news", 2, "TF1 +1"), chan("n2", "news", 1, "TF1"),
            chan("k1", "kids", 9, lang = "en"), chan("k2", "kids", 3),
            chan("f1", "film", 1), chan("o1", "off", 1), chan("d1", "docs", 4, lang = "ar"),
            chan("x1", null, 2), chan("x2", "inconnue", 1), chan("x3", null, 0, lang = "en"),
            chan("sep", "sport", 2, "##### SPORT #####"), chan("junk", "news", 3, "0001"),
            chan("autre", "sport", 1, provider = 8),
        )
        val ranks = LiveOrder.ranks(cats.forProviderKind(pid, "LIVE"))
        ch.upsertAll(if (stamp) ranks.stamp(rows) else rows)
        ch.upsertAll(listOf(chan("autre", "sport", 1, provider = 8)))
    }

    private fun legacyIds(): List<String> = runBlocking { ch.orderedAllIdsLegacy(pid).map { id -> ch.byId(id)!!.remoteId } }

    /** Parcours complet de la source par clé, par petites pages (les bornes entre pages sont testées). */
    private fun keysetIds(filter: LiveAllFilter = LiveAllFilter(), page: Int = 3): List<String> = runBlocking {
        val src = LiveAllPagingSource(ch, pid, filter)
        val out = ArrayList<String>()
        var r = src.load(PagingSource.LoadParams.Refresh(null, page, false)) as PagingSource.LoadResult.Page
        assertEquals(null, r.prevKey)
        out += r.data.map { it.remoteId }
        var next = r.nextKey
        while (next != null) {
            r = src.load(PagingSource.LoadParams.Append(next, page, false)) as PagingSource.LoadResult.Page
            out += r.data.map { it.remoteId }
            next = r.nextKey
        }
        out
    }

    private fun legacyFiltered(hidden: List<String>, langs: List<String>): List<String> = runBlocking {
        val src = if (hidden.isEmpty()) ch.pagedAllLegacy(pid, if (langs.isEmpty()) 0 else 1, langs.ifEmpty { listOf("") })
        else ch.pagedAllExcludingLegacy(pid, hidden, if (langs.isEmpty()) 0 else 1, langs.ifEmpty { listOf("") })
        (src.load(PagingSource.LoadParams.Refresh(null, 1_000, false)) as PagingSource.LoadResult.Page).data.map { it.remoteId }
    }

    @Test
    fun ordreParCle_identiqueALancienneRequeteJointe() {
        seed(stamp = true)
        val legacy = legacyIds()
        // Contrôle du jeu : placées kids(1), docs/off(2, par id), sport(3) ; puis sans catégorie ; puis non placées par id.
        assertEquals(listOf("k2", "k1", "o1", "d1", "s4", "s2", "s3", "s1", "x3", "x2", "x1", "n2", "n1", "f1"), legacy)
        assertEquals(legacy, keysetIds())
        assertEquals(legacy, keysetIds(page = 1))
        assertEquals(legacy, keysetIds(page = 100))
    }

    @Test
    fun ordreParCle_filtresCategoriesMasqueesEtLangue_identiques() {
        seed(stamp = true)
        assertEquals(legacyFiltered(listOf("sport", "news"), emptyList()), keysetIds(LiveAllFilter(hidden = listOf("sport", "news"))))
        assertEquals(legacyFiltered(emptyList(), listOf("en")), keysetIds(LiveAllFilter(useLang = 1, langs = listOf("en"))))
        assertEquals(legacyFiltered(listOf("kids"), listOf("fr", "ar")), keysetIds(LiveAllFilter(listOf("kids"), 1, listOf("fr", "ar"))))
    }

    @Test
    fun categoriesReordonnees_saveOrder_recalculeLesRangs_ordreToujoursIdentique() = runBlocking {
        seed(stamp = true)
        val manager = CategoryManager(db, cats, ch, db.movieDao(), db.seriesDao(), SyncCoordinator(ctx))
        manager.saveOrder(pid, "LIVE", listOf("film", "sport", "news", "kids", "docs", "off"))
        val legacy = legacyIds()
        assertEquals("f1", legacy.first())
        assertEquals(legacy, keysetIds())
        // Les films (autre type) gardent leur ordre propre : aucune chaîne n'est touchée par un réordonnancement VOD.
        manager.saveOrder(pid, "MOVIE", listOf("sport"))
        assertEquals(legacy, keysetIds())
    }

    @Test
    fun categorieDesactivee_puisReactivee_ordreIdentique() = runBlocking {
        seed(stamp = true)
        val manager = CategoryManager(db, cats, ch, db.movieDao(), db.seriesDao(), SyncCoordinator(ctx))
        manager.setEnabled(pid, "LIVE", listOf("sport"), false)
        assertFalse(keysetIds().any { it.startsWith("s") })
        assertEquals(legacyIds(), keysetIds())
        // Réactivation : la synchro ciblée réinsère avec le rang courant (Ranks.stamp).
        db.categoryDao().setEnabled(pid, "LIVE", listOf("sport"), true)
        ch.upsertAll(LiveOrder.ranks(cats.forProviderKind(pid, "LIVE")).stamp(listOf(chan("s9", "sport", 1))))
        assertEquals(legacyIds(), keysetIds())
    }

    @Test
    fun zapping_fenetreAutourDeLaChaine_identiqueALancienneFenetre() = runBlocking {
        seed(stamp = true)
        val legacy = legacyIds()
        for (rid in legacy) {
            val c = ch.byRemoteId(pid, rid)!!
            val rank = legacy.indexOf(rid)
            val expected = ch.windowAllLegacy(pid, 5, (rank - 2).coerceAtLeast(0)).map { it.remoteId }
            assertEquals("autour de $rid", expected, LiveAllPagingSource.window(ch, c, side = 2).map { it.remoteId })
        }
    }

    @Test
    fun rafraichissementAuMilieu_pagePrecedentePuisSuivante_sansDoublon() = runBlocking {
        seed(stamp = true)
        val legacy = legacyIds()
        val mid = ch.byRemoteId(pid, legacy[6])!!
        val src = LiveAllPagingSource(ch, pid, LiveAllFilter())
        val r = src.load(PagingSource.LoadParams.Refresh(LiveKey.justBefore(mid), 3, false)) as PagingSource.LoadResult.Page
        assertEquals(legacy.subList(6, 9), r.data.map { it.remoteId })
        val prev = src.load(PagingSource.LoadParams.Prepend(r.prevKey!!, 3, false)) as PagingSource.LoadResult.Page
        assertEquals(legacy.subList(3, 6), prev.data.map { it.remoteId })
    }

    @Test
    fun rangsTousPositifs_zeroReservePourNonCalcule() {
        val r = LiveOrder.ranks(listOf(cat("a", 0), cat("b", 5)))
        assertTrue(r.byCategory.values.all { it >= 1 } && r.orphan >= 1)
        assertTrue(LiveOrder.NONE.orphan >= 1)
    }

    @Test
    fun baseMigree_repliPuisRemplissageDiffere_parLots() = runBlocking {
        seed(stamp = false)                                     // lignes d'une base 17 migrée : catRank = 0
        val legacy = legacyIds()
        assertTrue(ch.observeHasUnranked(pid).first())          // → le Direct garde la requête jointe
        assertEquals(listOf(pid), ch.unrankedProviders())
        val written = LiveOrderBackfill(ch, cats).runNow(batch = 2, pauseMs = 0)
        assertTrue(written >= legacy.size)
        assertFalse(ch.observeHasUnranked(pid).first())         // → bascule sur la pagination par clé
        assertEquals(legacy, keysetIds())
        assertEquals(0, LiveOrderBackfill(ch, cats).runNow(batch = 2, pauseMs = 0))   // idempotent
        // Remplissage = même résultat que les rangs posés à l'insertion par la synchro.
        val viaBackfill = legacy.map { ch.byRemoteId(pid, it)!!.catRank }
        val ranks = LiveOrder.ranks(cats.forProviderKind(pid, "LIVE"))
        assertEquals(legacy.map { ranks.of(ch.byRemoteId(pid, it)!!.categoryId) }, viaBackfill)
    }
}
