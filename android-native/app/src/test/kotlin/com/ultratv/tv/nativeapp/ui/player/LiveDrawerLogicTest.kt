package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.data.repo.CategoryRow
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveDrawerLogicTest {
    private fun row(id: String, label: String = id, count: Int? = 10) = CategoryRow(id, label, null, count, true, "", false)

    // ── Ordre de la colonne des catégories ──

    @Test fun ordre_toutesLesChaines_favoris_puisCategories() {
        val out = LiveDrawerLogic.categories("Toutes les chaînes", "Favoris", allCount = 120, favoritesCount = 3, cats = listOf(row("sport"), row("news")))
        assertEquals(listOf(CATEGORY_ALL, CATEGORY_FAVORITES, "sport", "news"), out.map { it.id })
        assertEquals("Toutes les chaînes", out[0].label)
        assertEquals(120, out[0].count)
        assertEquals(3, out[1].count)
    }

    @Test fun favorisAbsents_sansFavoris() {
        val out = LiveDrawerLogic.categories("Toutes", "Favoris", allCount = 5, favoritesCount = 0, cats = listOf(row("a")))
        assertEquals(listOf(CATEGORY_ALL, "a"), out.map { it.id })
    }

    @Test fun entreeToutDejaPresente_deplaceeEnTete_sansDoublon() {
        val cats = listOf(row("a"), row("b"), row(CATEGORY_ALL, "Tout", count = 77), row("c"))
        val out = LiveDrawerLogic.categories("Toutes les chaînes", "Favoris", allCount = null, favoritesCount = 0, cats = cats)
        assertEquals(listOf(CATEGORY_ALL, "a", "b", "c"), out.map { it.id })
        assertEquals(1, out.count { it.id == CATEGORY_ALL })
        assertEquals("Tout", out[0].label)
        assertEquals(77, out[0].count)   // sans total fourni, le compte de l'entrée existante sert
    }

    @Test fun entreeFavorisDejaPresente_deplaceeApresToutes() {
        val cats = listOf(row("a"), row(CATEGORY_FAVORITES, "Mes favoris", count = 4), row("b"))
        val out = LiveDrawerLogic.categories("Toutes", "Favoris", allCount = 9, favoritesCount = 0, cats = cats)
        assertEquals(listOf(CATEGORY_ALL, CATEGORY_FAVORITES, "a", "b"), out.map { it.id })
        assertEquals("Mes favoris", out[1].label)
    }

    @Test fun listeVide_resteToutesLesChaines() {
        assertEquals(listOf(CATEGORY_ALL), LiveDrawerLogic.categories("Toutes", "Favoris", null, 0, emptyList()).map { it.id })
    }

    // ── Compteur ──

    @Test fun compteur_pendantLeChargement_montreLeCompteDeLaCategorie_pasZero() {
        // Liste vide ou ancienne liste (12) : on affiche le compte connu de la catégorie demandée.
        assertEquals(48, LiveDrawerLogic.counter(entriesSize = 0, categoryCount = 48, listIsCategory = true, loading = true))
        assertEquals(48, LiveDrawerLogic.counter(entriesSize = 12, categoryCount = 48, listIsCategory = true, loading = true))
    }

    @Test fun compteur_listeChargee_compteReelSiLaListeEstPlafonnee() {
        assertEquals(55000, LiveDrawerLogic.counter(entriesSize = 401, categoryCount = 55000, listIsCategory = true, loading = false))
        assertEquals(30, LiveDrawerLogic.counter(entriesSize = 30, categoryCount = null, listIsCategory = true, loading = false))
        // Compte périmé plus petit que la liste : la liste fait foi.
        assertEquals(30, LiveDrawerLogic.counter(entriesSize = 30, categoryCount = 0, listIsCategory = true, loading = false))
    }

    @Test fun compteur_fileDeZapping_taillDeLaListe() {
        assertEquals(25, LiveDrawerLogic.counter(entriesSize = 25, categoryCount = 400, listIsCategory = false, loading = false))
    }

    // ── Focus ──

    @Test fun categorieDejaAffichee_nEstPasRechargee() {
        assertFalse(LiveDrawerLogic.shouldBrowse("sport", "sport"))
        assertTrue(LiveDrawerLogic.shouldBrowse("news", "sport"))
        assertTrue(LiveDrawerLogic.shouldBrowse("news", null))
    }

    @Test fun entreeDansLaListe_chaineEnCoursSinonPremiere() {
        assertEquals(4, LiveDrawerLogic.entryFocusIndex(currentIndex = 4, size = 10))
        assertEquals(0, LiveDrawerLogic.entryFocusIndex(currentIndex = -1, size = 10))
        assertEquals(0, LiveDrawerLogic.entryFocusIndex(currentIndex = 12, size = 10))
        assertNull(LiveDrawerLogic.entryFocusIndex(currentIndex = 0, size = 0))
    }

    // ── Chargement anti-rebond ──

    @Test fun antiRebond_seuleLaDerniereCategorieEstChargee() = runTest {
        val loaded = mutableListOf<String>()
        val b = DebouncedBrowser(backgroundScope, 250) { loaded += it }
        b.request("a"); advanceTimeBy(100)
        b.request("b"); advanceTimeBy(100)
        b.request("c")
        assertEquals("c", b.pending.value)
        advanceTimeBy(249); runCurrent()
        assertTrue("rien avant la fin du délai", loaded.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("c"), loaded)
        assertNull("à jour : plus rien en attente", b.pending.value)
    }

    @Test fun antiRebond_categorieUnique_chargeeApres250ms() = runTest {
        val loaded = mutableListOf<String>()
        val b = DebouncedBrowser(backgroundScope, LiveDrawerLogic.BROWSE_DEBOUNCE_MS) { loaded += it }
        b.request("news")
        advanceTimeBy(LiveDrawerLogic.BROWSE_DEBOUNCE_MS - 1); runCurrent()
        assertTrue(loaded.isEmpty())
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("news"), loaded)
    }

    @Test fun chargementDepasse_estAnnule_etNEcraseRien() = runTest {
        val done = mutableListOf<String>()
        val b = DebouncedBrowser(backgroundScope, 250) { id -> delay(500); done += id }   // chargement lent
        b.request("a")
        advanceTimeBy(300); runCurrent()          // « a » est en cours de chargement
        b.request("b")                            // l'utilisateur est passé plus loin
        advanceTimeBy(250 + 501); runCurrent()
        assertEquals(listOf("b"), done)
        assertNull(b.pending.value)
    }

    @Test fun categorieDejaChargee_nEstPasRedemandee() = runTest {
        val loaded = mutableListOf<String>()
        val b = DebouncedBrowser(backgroundScope, 250) { loaded += it }
        b.request("a"); advanceTimeBy(300); runCurrent()
        b.request("a"); advanceTimeBy(600); runCurrent()
        assertEquals(listOf("a"), loaded)
        assertNull(b.pending.value)
    }

    @Test fun reinitialisation_oublieLaDemande() = runTest {
        val loaded = mutableListOf<String>()
        val b = DebouncedBrowser(backgroundScope, 250) { loaded += it }
        b.request("a")
        b.reset()
        advanceTimeBy(600); runCurrent()
        assertTrue(loaded.isEmpty())
        assertNull(b.pending.value)
    }
}
