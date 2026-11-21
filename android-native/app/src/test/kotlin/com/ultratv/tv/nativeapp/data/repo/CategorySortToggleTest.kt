package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tri A → Z réversible : l'ordre du fournisseur (ordre d'arrivée) se retrouve après le tri. */
class CategorySortToggleTest {
    private fun cat(id: Long, rid: String, name: String) = CategoryEntity(id = id, providerId = 1, kind = "LIVE", remoteId = rid, name = name)
    private val provider = listOf(cat(1, "s", "Sport"), cat(2, "n", "news"), cat(3, "a", "Animation"))

    @Test fun triAZ_puisRetourOrdreDuFournisseur() {
        val az = alphabeticalIds(provider)
        assertEquals(listOf("a", "n", "s"), az)
        val sorted = az.map { r -> provider.first { it.remoteId == r } }
        assertTrue(isAlphabetical(sorted))
        assertEquals(listOf("s", "n", "a"), providerIds(sorted))
    }

    @Test fun ordreDuFournisseur_nEstPasAZ() {
        assertFalse(isAlphabetical(provider))
    }

    @Test fun uneSeuleCategorie_rienABasculer() {
        assertFalse(isAlphabetical(listOf(cat(1, "x", "X"))))
        assertFalse(isAlphabetical(emptyList()))
    }
}
