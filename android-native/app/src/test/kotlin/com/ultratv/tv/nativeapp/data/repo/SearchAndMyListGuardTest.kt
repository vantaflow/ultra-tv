package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.ui.favorites.MyListGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recherche et Ma liste : jamais d'affiche ni de titre d'un contenu verrouillé, ni d'adulte pour un profil Enfants. */
class SearchAndMyListGuardTest {
    @Test fun `recherche - categories verrouillees exclues avec les masquees et desactivees`() {
        val r = CategorySearchVisibility.excludedRemoteIds("MOVIE", 1L, setOf("MOVIE:1:h"), listOf("d"), lockedRemoteIds = listOf("l"))
        assertEquals(setOf("h", "d", "l"), r.toSet())
    }

    @Test fun `recherche - titres adultes retires seulement pour un profil Enfants`() {
        val titles = listOf("Le Roi Lion", "XXX Night", "Heroes")
        assertEquals(titles, titles.withoutAdultTitles(kids = false) { it })
        assertEquals(listOf("Le Roi Lion", "Heroes"), titles.withoutAdultTitles(kids = true) { it })
    }

    @Test fun `Ma liste - verrouille toujours retire, masquage volontaire sans effet`() {
        val g = MyListGuard(kids = false, lockedCategoryIds = setOf("lock"), kidsBlockedKeys = emptySet())
        assertFalse(g.allows("MOVIE", 1, "lock", "Film"))
        assertTrue(g.allows("MOVIE", 1, "hidden-by-choice", "Film"))
        assertTrue(g.allows("MOVIE", 1, null, "XXX Night"))
    }

    @Test fun `Ma liste - profil Enfants - categories fermees et titres adultes retires`() {
        val g = MyListGuard(kids = true, lockedCategoryIds = emptySet(), kidsBlockedKeys = setOf("SERIES:1:adult"))
        assertFalse(g.allows("SERIES", 1, "adult", "Série"))
        assertFalse(g.allows("MOVIE", 1, "family", "XXX Night"))
        assertTrue(g.allows("MOVIE", 1, "family", "Le Roi Lion"))
    }
}
