package com.ultratv.tv.nativeapp.data.profile

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesScopeTest {
    private fun p(id: Long, name: String, active: Boolean = false) =
        ProviderEntity(id = id, name = name, kind = "XTREAM", baseUrl = "u", username = "", password = "", active = active)
    private fun ch(id: Long, pid: Long, cat: String?) =
        ChannelEntity(id = id, providerId = pid, remoteId = "r$id", name = "n$id", logo = null, categoryId = cat, streamUrl = "http://x/$id")

    private val two = listOf(p(1, "Alpha", active = true), p(2, "Bravo"))

    @Test fun choixPossibleSeulementAvecDeuxSourcesVisibles() {
        assertFalse(FavoritesScope.canMix(1))
        assertTrue(FavoritesScope.canMix(2))
    }

    @Test fun sansChoix_seuleLaSourceActiveEstLue() {
        assertEquals(listOf(1L), FavoritesScope.providerIds(two, null, false))
        assertEquals(listOf(2L), FavoritesScope.providerIds(listOf(p(1, "Alpha"), p(2, "Bravo", active = true)), null, false))
    }

    @Test fun avecChoix_toutesLesSourcesVisiblesSontLues() {
        assertEquals(listOf(1L, 2L), FavoritesScope.providerIds(two, null, true))
    }

    @Test fun sourceMasqueeParLeProfil_neRevientPasDansLaVueMelangee() {
        val three = two + p(3, "Charlie")
        assertEquals(listOf(1L, 3L), FavoritesScope.providerIds(three, listOf("alpha", "Charlie"), true))
    }

    @Test fun uneSeuleSourceVisible_choixSansEffet() {
        assertEquals(listOf(1L), FavoritesScope.providerIds(two, listOf("Alpha"), true))
    }

    @Test fun nomsDeSource_seulementEnVueMelangee() {
        assertNull(FavoritesScope.sourceNames(two, listOf(1L)))
        assertEquals(mapOf(1L to "Alpha", 2L to "Bravo"), FavoritesScope.sourceNames(two, listOf(1L, 2L)))
    }

    @Test fun categorieMasquee_retireeParSource() {
        val l = listOf(ch(1, 1, "a"), ch(2, 2, "a"), ch(3, 2, "b"), ch(4, 2, null))
        val hidden = setOf("LIVE:2:a")
        assertEquals(listOf(1L, 3L, 4L), FavoritesScope.dropHidden(l, hidden).map { it.id })
        assertEquals(l, FavoritesScope.dropHidden(l, emptySet()))
    }
}
