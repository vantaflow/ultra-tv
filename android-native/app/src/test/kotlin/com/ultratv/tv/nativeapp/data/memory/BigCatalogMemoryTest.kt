package com.ultratv.tv.nativeapp.data.memory

import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.Pager
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.repo.LangView
import com.ultratv.tv.nativeapp.data.repo.PagingMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

/**
 * Non-régression mémoire « gros catalogue » (issue #148) : 50 000 films en base ; la grille ne doit lire qu'une fenêtre,
 * même en faisant défiler tout le catalogue, et les tris/filtres se font en SQL (aucune copie complète en Kotlin).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class BigCatalogMemoryTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries().build()
    private val movies = db.movieDao()
    private val pid = 1L
    @After fun close() = db.close()

    private companion object { const val TOTAL = 50_000 }

    private fun seed() = runBlocking {
        db.providerDao().upsert(ProviderEntity(id = pid, name = "S", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid", username = "u", password = ""))
        (1..TOTAL).chunked(5_000).forEach { chunk ->
            movies.upsertAll(chunk.map { i ->
                MovieEntity(providerId = pid, remoteId = "$i", name = "Film $i", poster = null, categoryId = "c${i % 40}",
                    streamUrl = "u/$i", container = null, year = 1980 + i % 40, rating = (i % 100) / 10.0, plot = null, lang = "fr")
            })
        }
        assertEquals(TOTAL, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM movie").use { it.moveToFirst(); it.getInt(0) })
    }

    /** Source décorée : compte les lignes réellement lues dans Room (ce qui finit en mémoire). */
    private class Counting<K : Any, V : Any>(private val inner: PagingSource<K, V>, val rows: AtomicInteger) : PagingSource<K, V>() {
        override val jumpingSupported get() = inner.jumpingSupported
        override val keyReuseSupported get() = inner.keyReuseSupported
        init { inner.registerInvalidatedCallback { invalidate() } }
        override fun getRefreshKey(state: PagingState<K, V>): K? = inner.getRefreshKey(state)
        override suspend fun load(params: LoadParams<K>): LoadResult<K, V> {
            val r = inner.load(params)
            if (r is LoadResult.Page) rows.addAndGet(r.data.size)
            return r
        }
    }

    private object NoopUpdate : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {}
        override fun onRemoved(position: Int, count: Int) {}
        override fun onMoved(fromPosition: Int, toPosition: Int) {}
        override fun onChanged(position: Int, count: Int, payload: Any?) {}
    }

    private fun differ() = AsyncPagingDataDiffer(
        diffCallback = object : DiffUtil.ItemCallback<MovieEntity>() {
            override fun areItemsTheSame(a: MovieEntity, b: MovieEntity) = a.id == b.id
            override fun areContentsTheSame(a: MovieEntity, b: MovieEntity) = a == b
        },
        updateCallback = NoopUpdate,
        mainDispatcher = Dispatchers.Unconfined,
        workerDispatcher = Dispatchers.Unconfined,
    )

    private suspend fun until(what: String, cond: () -> Boolean) = withTimeout(20_000) { while (!cond()) delay(2) }

    @Test
    fun grille_50000Films_neChargeQuUneFenetre_meme_enDefilantToutLeCatalogue() = runBlocking {
        seed()
        val rows = AtomicInteger()
        val cfg = PagingMemory.config(42, 28, 84)
        val d = differ()
        val job = launch(Dispatchers.Default) {
            Pager(cfg) { Counting(movies.pagedAll(pid, 0, listOf("")), rows) }.flow.collectLatest { d.submitData(it) }
        }
        // 1) Premier affichage : une fenêtre, pas le catalogue.
        until("première fenêtre") { d.itemCount >= 84 }
        assertTrue("seul le chargement initial (+ préchargement) doit être lu, pas ${rows.get()} lignes", rows.get() <= 84 + 2 * 42)
        assertTrue(d.itemCount <= PagingMemory.maxItems(42, 28, 84) + 42)

        // 2) Défilement jusqu'au bout : tout le catalogue est lu, mais jamais gardé (plafond maxSize).
        var peak = d.itemCount
        var stalled = 0
        var lastRows = rows.get()
        while (rows.get() < TOTAL && stalled < 500) {
            runCatching { d.getItem(d.itemCount - 1) }   // la liste change sous nos pieds (pages libérées) : un index périmé est sans gravité
            delay(1)
            peak = maxOf(peak, d.itemCount)
            if (rows.get() == lastRows) stalled++ else { stalled = 0; lastRows = rows.get() }
        }
        job.cancel()
        assertTrue("le défilement doit avoir lu tout le catalogue (lu: ${rows.get()})", rows.get() >= TOTAL * 9 / 10)
        val ceiling = PagingMemory.maxItems(42, 28, 84) + 3 * 42
        assertTrue("éléments gardés en mémoire: pic $peak > plafond $ceiling (50 000 sans maxSize)", peak <= ceiling)
    }

    @Test
    fun plafondDeLaConfig_respecteLesExigencesDePaging() {
        // Paging refuse maxSize < pageSize + 2 × prefetchDistance : la fabrique ne doit jamais lever, quelle que soit la grille.
        for ((p, f, i) in listOf(Triple(42, 28, 84), Triple(40, 24, 60), Triple(20, 10, 30), Triple(10, 50, 10))) {
            val c = PagingMemory.config(p, f, i)
            assertTrue(c.maxSize >= p + 2 * f && c.maxSize >= i)
        }
        assertTrue(PagingMemory.config(42, 28, 84).maxSize < 1_000)
    }

    @Test
    fun triEtFiltreSql_unePageSeulement_ordreRespecte() = runBlocking {
        seed()
        val rows = AtomicInteger()
        // Tri par note décroissante + filtre note/période : la base trie et filtre, Kotlin ne reçoit que 84 lignes.
        val src = Counting(movies.pagedFilteredByRating(pid, 0, listOf(""), 5, 2000, 2010), rows)
        val page = src.load(PagingSource.LoadParams.Refresh(null, 84, false)) as PagingSource.LoadResult.Page
        assertEquals(84, page.data.size)
        assertEquals(84, rows.get())
        assertTrue(page.data.all { (it.rating ?: 0.0) >= 5.0 && (it.year ?: 0) in 2000..2010 })
        assertEquals(page.data.sortedByDescending { it.rating }.map { it.rating }, page.data.map { it.rating })

        val byTitle = Counting(movies.pagedFilteredByTitle(pid, 0, listOf(""), 0, 0, 0), AtomicInteger())
        val p2 = byTitle.load(PagingSource.LoadParams.Refresh(null, 42, false)) as PagingSource.LoadResult.Page
        assertEquals(42, p2.data.size)
        assertEquals(p2.data.map { it.sortKey }.sorted(), p2.data.map { it.sortKey })
    }

    @Test
    fun compteurDeCategories_parSql_sansChargerLesLignes() = runBlocking {
        seed()
        db.categoryDao().upsertAll((1..300).map { com.ultratv.tv.nativeapp.data.db.CategoryEntity(providerId = pid, kind = "MOVIE", remoteId = "c$it", name = "C$it", position = it) })
        assertEquals(300, db.categoryDao().observeCount(pid, "MOVIE").first())
        assertEquals(0, db.categoryDao().observeCount(pid, "LIVE").first())
    }

    @Test
    fun filtreDeLangue_sansFiltre_nAlloueAucuneCopie() {
        val big = List(TOTAL) { it }
        // Sans filtre de langue, la même liste est rendue (aucune copie de 50 000 éléments).
        assertSame(big, LangView.ALL.filter(big) { "fr" })
        val fr = LangView(setOf("fr")).filter(listOf(1, 2, 3)) { if (it == 2) "en" else "fr" }
        assertEquals(listOf(1, 3), fr)
    }
}
