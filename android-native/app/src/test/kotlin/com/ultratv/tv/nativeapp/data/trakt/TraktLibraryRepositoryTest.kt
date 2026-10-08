package com.ultratv.tv.nativeapp.data.trakt

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TraktLibraryRepositoryTest {
    private class FakeSource(var paired: Boolean = true, var body: String? = LINKED) : TraktLibrarySource {
        var calls = 0
        val langs = mutableListOf<String>()
        override val isPaired get() = paired
        override suspend fun fetch(lang: String): String? { calls++; langs += lang; return body }
    }

    private companion object {
        const val LINKED = """{"linked":true,"updatedAt":1,"watchlist":[{"type":"movie","title":"Alien","keys":["alien"]}],"recommendations":[],"watched":{"movies":[],"shows":[]}}"""
        const val MIN = 60_000L
    }

    private var now = 1_000_000L
    private fun repo(src: FakeSource) = TraktLibraryRepository(src) { now }

    @Test
    fun refresh_compteNonLie_donneEtatVide() = runBlocking {
        val r = repo(FakeSource(body = """{"linked":false}"""))
        r.refreshIfStale("fr")
        assertSame(TraktLibrary.EMPTY, r.library.value)
    }

    @Test
    fun refresh_appareilNonAppaire_neFaitAucuneRequete() = runBlocking {
        val src = FakeSource(paired = false)
        val r = repo(src)
        r.refreshIfStale("fr")
        assertEquals(0, src.calls)
        assertTrue(!r.library.value.linked)
    }

    @Test
    fun refresh_moinsDeQuinzeMinutes_nInterrogePasDeNouveau() = runBlocking {
        val src = FakeSource()
        val r = repo(src)
        r.refreshIfStale("fr")
        now += 14 * MIN
        r.refreshIfStale("fr")
        assertEquals(1, src.calls)
        now += 2 * MIN
        r.refreshIfStale("fr")
        assertEquals(2, src.calls)
    }

    @Test
    fun refresh_changementDeLangue_relit() = runBlocking {
        val src = FakeSource()
        val r = repo(src)
        r.refreshIfStale("fr")
        r.refreshIfStale("en")
        assertEquals(listOf("fr", "en"), src.langs)
    }

    @Test
    fun refresh_echec_conserveLaDerniereValeurValide() = runBlocking {
        val src = FakeSource()
        val r = repo(src)
        r.refreshIfStale("fr")
        val good = r.library.value
        src.body = null
        now += 20 * MIN
        r.refreshIfStale("fr")
        assertSame(good, r.library.value)
        src.body = "pas du json"
        now += 2 * MIN
        r.refreshIfStale("fr")
        assertSame(good, r.library.value)
    }

    @Test
    fun refresh_apresEchec_attendUneMinuteAvantDeRetenter() = runBlocking {
        val src = FakeSource(body = null)
        val r = repo(src)
        r.refreshIfStale("fr")
        now += 30_000
        r.refreshIfStale("fr")
        assertEquals(1, src.calls)
        now += 31_000
        r.refreshIfStale("fr")
        assertEquals(2, src.calls)
    }

    @Test
    fun refresh_desappairage_oublieLeCompte() = runBlocking {
        val src = FakeSource()
        val r = repo(src)
        r.refreshIfStale("fr")
        assertTrue(r.library.value.linked)
        src.paired = false
        r.refreshIfStale("fr")
        assertSame(TraktLibrary.EMPTY, r.library.value)
    }

    @Test
    fun langParam_langueSupportee_ouRepliEn() {
        assertEquals("fr", TraktLibraryRepository.langParam("fr", "en"))
        assertEquals("es", TraktLibraryRepository.langParam("system", "ES"))
        assertEquals("en", TraktLibraryRepository.langParam("system", "de"))
    }

    private fun tmpStore() = FileTraktStore(java.nio.file.Files.createTempDirectory("trakt-store").toFile())

    @Test
    fun restore_apresUnRefresh_rechargeLaBibliothequeSansReseau() = runBlocking {
        val store = tmpStore()
        val first = TraktLibraryRepository(FakeSource(), store) { now }
        first.refreshIfStale("fr")
        // Nouveau « lancement » : même disque, réseau en panne.
        val src = FakeSource(body = null)
        val second = TraktLibraryRepository(src, store) { now + 5 * MIN }
        second.restore()
        assertTrue(second.library.value.linked)
        assertEquals("Alien", second.library.value.watchlist.single().title)
        // Fichier de moins de 15 min et même langue : aucun appel réseau.
        second.refreshIfStale("fr")
        assertEquals(0, src.calls)
    }

    @Test
    fun restore_fichierPerime_relitLeReseau() = runBlocking {
        val store = tmpStore()
        TraktLibraryRepository(FakeSource(), store) { now }.refreshIfStale("fr")
        val src = FakeSource()
        val r = TraktLibraryRepository(src, store) { now + 20 * MIN }
        r.refreshIfStale("fr")
        assertTrue(r.library.value.linked)
        assertEquals(1, src.calls)
    }

    @Test
    fun restore_fichierIllisible_resteVide() = runBlocking {
        val store = tmpStore()
        store.writeLibrary("pas du json")
        val r = TraktLibraryRepository(FakeSource(body = null), store) { now }
        r.restore()
        assertSame(TraktLibrary.EMPTY, r.library.value)
    }

    @Test
    fun refresh_desappairage_effaceLeDisque() = runBlocking {
        val store = tmpStore()
        val src = FakeSource()
        val r = TraktLibraryRepository(src, store) { now }
        r.refreshIfStale("fr")
        assertTrue(store.readLibrary() != null)
        src.paired = false
        r.refreshIfStale("fr")
        assertEquals(null, store.readLibrary())
    }

    @Test
    fun store_rangees_allerRetourSurDisque() {
        val store = tmpStore()
        val rows = TraktRows(listOf(TraktCard(false, 5, "Alien", null, 1979, 8.0)), emptyList(), emptyList(), listOf(TraktCard(true, 6, "Lost", "p", null, null)))
        store.writeRows(TraktRowsCodec.encode(mapOf(2L to rows)))
        assertEquals(rows, TraktRowsCodec.decode(store.readRows())[2L])
    }
}
