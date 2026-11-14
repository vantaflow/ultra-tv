package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.config.CloudPrefs
import com.ultratv.tv.nativeapp.data.config.DisplayPrefs
import com.ultratv.tv.nativeapp.data.prefs.HiddenChannel
import com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ordre des catégories (liste filtrée) et chaînes masquées : règles pures et format partagé entre appareils. */
@RunWith(RobolectricTestRunner::class)
class CategoryOrderHiddenTest {
    @Test fun ordreFiltre_reprendLesPlacesDesVisibles_sansToucherAuxAutres() {
        val full = listOf("a", "b", "c", "d", "e")
        // Filtre « b, d » : on met d avant b. a, c, e ne bougent pas.
        assertEquals(listOf("a", "d", "c", "b", "e"), CategoryManager.mergeOrder(full, listOf("d", "b")))
        // Liste complète : ordre pris tel quel.
        assertEquals(listOf("e", "d", "c", "b", "a"), CategoryManager.mergeOrder(full, full.reversed()))
        // Inconnue : ajoutée à la fin, sans doublon.
        assertEquals(listOf("a", "b", "c", "d", "e", "z"), CategoryManager.mergeOrder(full, listOf("a", "z")))
    }

    @Test fun chainesMasquees_allerRetourJson() {
        val items = listOf(HiddenChannel("501", "12", "Chaîne « test »"), HiddenChannel("502", null, ""))
        assertEquals(items, HiddenChannelsStore.parse(HiddenChannelsStore.toJson(items)))
        assertEquals(emptyList<HiddenChannel>(), HiddenChannelsStore.parse("pas du json"))
        assertEquals(emptyList<HiddenChannel>(), HiddenChannelsStore.parse(null))
    }

    @Test fun prefs_ordreEtChainesMasquees_transmis_etAbsentsSiAncienFormat() {
        val p = CloudPrefs(listOf("fr"), mapOf("live" to listOf("1"), "movie" to emptyList(), "series" to emptyList()), 10,
            order = mapOf("live" to listOf("2", "1"), "movie" to emptyList(), "series" to emptyList()), hidden = listOf(HiddenChannel("9", "1", "x")))
        val back = DisplayPrefs.parse(JSONObject(DisplayPrefs.toJson(p)))!!
        assertEquals(listOf("2", "1"), back.order!!["live"])
        assertEquals(listOf(HiddenChannel("9", "1", "x")), back.hidden)
        // Ancien format (sans order / hidden) : null = « ne pas y toucher ».
        val old = DisplayPrefs.parse(JSONObject("""{"langs":null,"disabled":{"live":[]},"updatedAt":5}"""))!!
        assertNull(old.order); assertNull(old.hidden)
        // L'empreinte change avec l'ordre (sinon un changement d'ordre ne serait jamais publié).
        assertNotEquals(DisplayPrefs.fingerprint(p), DisplayPrefs.fingerprint(p.copy(order = mapOf("live" to listOf("1", "2")))))
    }
}
