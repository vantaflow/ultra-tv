package com.ultratv.tv.nativeapp.data.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPrefsTest {
    private fun local(sel: Set<String> = setOf("fr"), multi: Boolean = true, unknown: Boolean = false, live: Set<String> = setOf("12", "3")) =
        LocalDisplayPrefs(sel, multi, unknown, mapOf("LIVE" to live, "MOVIE" to setOf("7"), "SERIES" to emptySet()))

    @Test fun toCloud_languesMinuscules_multiEtOther_typesDuProtocole() {
        val c = DisplayPrefs.toCloud(local(sel = setOf("FR", "en"), unknown = true), 42)
        assertEquals(listOf("en", "fr", "multi", "other"), c.langs)
        assertEquals(listOf("12", "3"), c.disabled["live"])
        assertEquals(listOf("7"), c.disabled["movie"])
        assertEquals(emptyList<String>(), c.disabled["series"])
        assertEquals(42L, c.updatedAt)
    }

    @Test fun toCloud_aucuneLangueChoisie_null() = assertNull(DisplayPrefs.toCloud(local(sel = emptySet()), 1).langs)

    @Test fun langsFromCloud_allerRetour() {
        val (sel, multi, unknown) = DisplayPrefs.langsFromCloud(listOf("fr", "multi"))
        assertEquals(setOf("fr"), sel); assertTrue(multi); assertEquals(false, unknown)
    }

    @Test fun langsFromCloud_nullOuSansLangueExplicite_toutVisible() {
        assertEquals(Triple(emptySet<String>(), true, true), DisplayPrefs.langsFromCloud(null))
        assertEquals(Triple(emptySet<String>(), true, true), DisplayPrefs.langsFromCloud(listOf("other")))
    }

    @Test fun empreinte_ignoreLHorodatageEtLOrdre() {
        val a = DisplayPrefs.toCloud(local(live = setOf("3", "12")), 1)
        val b = DisplayPrefs.toCloud(local(live = setOf("12", "3")), 999)
        assertEquals(DisplayPrefs.fingerprint(a), DisplayPrefs.fingerprint(b))
        assertNotEquals(DisplayPrefs.fingerprint(a), DisplayPrefs.fingerprint(DisplayPrefs.toCloud(local(live = setOf("12")), 1)))
    }

    @Test fun json_allerRetour() {
        val c = DisplayPrefs.toCloud(local(), 1234)
        val back = DisplayPrefs.parse(JSONObject(DisplayPrefs.toJson(c)))
        assertEquals(c, back)
    }

    @Test fun parseConfig_lesPrefsDUneSource() {
        val body = """{"version":3,"self":"d1","devices":[],"providers":[{"id":"aaaaaaaa","kind":"XTREAM","name":"S","url":"http://h","username":"u","password":"p","sharedWith":"all","updatedAt":1,
            "prefs":{"langs":null,"disabled":{"live":["5"],"movie":[],"series":["9"]},"updatedAt":77,"by":"d2"}},
            {"id":"bbbbbbbb","kind":"M3U","name":"M","url":"http://m","username":"","password":"","prefs":null}]}"""
        val cfg = CloudSyncLogic.parseConfig(body)
        val p = cfg.providers[0].prefs!!
        assertNull(p.langs); assertEquals(listOf("5"), p.disabled["live"]); assertEquals(listOf("9"), p.disabled["series"]); assertEquals(77L, p.updatedAt)
        assertNull(cfg.providers[1].prefs)
    }

    @Test fun tropDIdentifiants_nonPublie() {
        val big = (0..5000).map { it.toString() }.toSet()
        assertTrue(DisplayPrefs.tooLarge(DisplayPrefs.toCloud(local(live = big), 1)))
    }
}
