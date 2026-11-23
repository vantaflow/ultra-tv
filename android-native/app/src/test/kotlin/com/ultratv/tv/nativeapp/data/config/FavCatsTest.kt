package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.db.CategoryCount
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.prefs.FavoriteCategoriesStore
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES
import com.ultratv.tv.nativeapp.ui.live.buildDirectCategories
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catégories favorites (issue #249) : lecture, conservation, publication et tri en tête. */
class FavCatsTest {
    private fun base(extra: String = "") = JSONObject("""{"langs":null,"disabled":{"live":[],"movie":[],"series":[]},"updatedAt":5$extra}""")
    private fun local(fav: Map<String, List<String>>?) = LocalDisplayPrefs(emptySet(), true, true, emptyMap(), favCats = fav)

    @Test fun parse_litFavCats() {
        val p = DisplayPrefs.parse(base(""","favCats":{"live":["3","1","3"],"movie":["9"]}"""))!!
        assertEquals(mapOf("live" to listOf("3", "1"), "movie" to listOf("9"), "series" to emptyList<String>()), p.favCats)
    }

    @Test fun parse_champAbsent_estNull_pasVide() = assertNull(DisplayPrefs.parse(base())!!.favCats)

    @Test fun nonLu_neSEnvoiePas() {
        val c = DisplayPrefs.toCloud(local(null), 1)
        assertNull(c.favCats)
        assertFalse(JSONObject(DisplayPrefs.toJson(c)).has("favCats"))
    }

    @Test fun lu_estRenvoye_etVideExplicitePermis() {
        val c = DisplayPrefs.toCloud(local(mapOf("live" to listOf("b", "a"))), 1)
        val o = JSONObject(DisplayPrefs.toJson(c)).getJSONObject("favCats")
        assertEquals("a", o.getJSONArray("live").getString(0))
        assertEquals(0, o.getJSONArray("series").length())
        val back = DisplayPrefs.parse(JSONObject(DisplayPrefs.toJson(c)))!!
        assertEquals(c.favCats, back.favCats)
    }

    @Test fun empreinte_inchangeeSansFavCats_etSensibleAvec() {
        val sans = DisplayPrefs.fingerprint(DisplayPrefs.toCloud(local(null), 1))
        val avecVide = DisplayPrefs.fingerprint(DisplayPrefs.toCloud(local(emptyMap()), 1))
        val avec = DisplayPrefs.fingerprint(DisplayPrefs.toCloud(local(mapOf("live" to listOf("1"))), 1))
        assertTrue(sans != avec && avecVide != avec)
        // Même empreinte quel que soit l'ordre.
        assertEquals(avec, DisplayPrefs.fingerprint(DisplayPrefs.toCloud(local(mapOf("live" to listOf("1"))), 99)))
    }

    @Test fun cles_ids_parSourceEtType() {
        val keys = setOf(FavoriteCategoriesStore.key(1, "LIVE", "7"), FavoriteCategoriesStore.key(1, "MOVIE", "8"), FavoriteCategoriesStore.key(2, "LIVE", "9"), FavoriteCategoriesStore.key(1, "LIVE", "a:b"))
        assertEquals(setOf("7", "a:b"), FavoriteCategoriesStore.idsOf(keys, 1, "LIVE"))
        assertEquals(setOf("9"), FavoriteCategoriesStore.idsOf(keys, 2, "LIVE"))
    }

    @Test fun favoritesFirst_gardeLOrdrePersonnaliseDesAutres() {
        val l = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("b", "d", "a", "c", "e"), FavoriteCategoriesStore.favoritesFirst(l, setOf("b", "d")) { it })
        assertEquals(l, FavoriteCategoriesStore.favoritesFirst(l, emptySet()) { it })
        assertEquals(l, FavoriteCategoriesStore.favoritesFirst(l, setOf("zz")) { it })
    }

    @Test fun direct_favoritesEnTeteApresFavorisEtTout() {
        fun cat(id: String) = CategoryEntity(providerId = 1, kind = "LIVE", remoteId = id, name = id)
        val cats = listOf(cat("10"), cat("11"), cat("12"))
        val counts = listOf(CategoryCount("10", 1), CategoryCount("11", 2), CategoryCount("12", 3))
        val l = buildDirectCategories(cats, counts, 0, 1, emptySet(), favs = setOf("12"))
        assertEquals(listOf(CATEGORY_FAVORITES, CATEGORY_ALL, "12", "10", "11"), l.map { it.id })
        assertTrue(l.first { it.id == "12" }.fav)
        assertFalse(l.first { it.id == "10" }.fav)
    }
}
