package com.ultratv.tv.nativeapp.data.trakt

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Vecteurs PARTAGÉS avec le Worker et le bureau : toute règle se change d'abord dans le fichier JSON. */
class TraktMatchTest {
    // Le répertoire de travail des tests est le module (android-native/app).
    private val vectors: JSONObject = File("../../cloudflare-config/test/fixtures/trakt-match-vectors.json").let {
        check(it.isFile) { "Vecteurs partagés introuvables : ${it.absolutePath}" }
        JSONObject(it.readText())
    }

    private fun strings(a: JSONArray) = List(a.length()) { a.getString(it) }

    @Test
    fun matchKey_vecteursPartages_toutesLesCles() {
        val keys = vectors.getJSONArray("keys")
        assertTrue("fichier de vecteurs vide", keys.length() > 10)
        for (i in 0 until keys.length()) {
            val pair = keys.getJSONArray(i)
            assertEquals("matchKey(\"${pair.getString(0)}\")", pair.getString(1), TraktMatch.matchKey(pair.getString(0)))
        }
    }

    @Test
    fun titleMatches_vecteursPartages_tousLesCas() {
        val cases = vectors.getJSONArray("matches")
        assertTrue(cases.length() > 3)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val cy = if (c.isNull("catalogYear")) null else c.getInt("catalogYear")
            val y = if (c.isNull("year")) null else c.getInt("year")
            assertEquals("cas $i (${c.getString("catalog")})", c.getBoolean("match"), TraktMatch.titleMatches(c.getString("catalog"), cy, strings(c.getJSONArray("titles")), y))
        }
    }

    @Test
    fun matchKey_null_donneChaineVide() {
        assertEquals("", TraktMatch.matchKey(null))
    }

    @Test
    fun matchKey_prefixeEnMinuscules_estConserve() {
        // Le préfixe pays / source est volontairement sensible à la casse (comme côté Worker).
        assertEquals("fr alien", TraktMatch.matchKey("fr - Alien"))
    }

    @Test
    fun yearsCompatible_ecartSuperieurAUn_refuse() {
        assertTrue(TraktMatch.yearsCompatible(1979, 1980))
        assertTrue(TraktMatch.yearsCompatible(null, 1980))
        assertFalse(TraktMatch.yearsCompatible(1979, 1981))
    }
}
