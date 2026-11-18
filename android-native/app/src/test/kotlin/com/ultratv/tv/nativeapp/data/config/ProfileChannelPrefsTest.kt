package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.prefs.HiddenChannel
import com.ultratv.tv.nativeapp.data.repo.ChannelHidingRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Chaînes masquées et ordre des catégories PAR PROFIL : logique pure et protocole cloud (champs pHiddenCh / pOrder). */
@RunWith(RobolectricTestRunner::class)
class ProfileChannelPrefsTest {
    private val none = mapOf("live" to emptyList<String>(), "movie" to emptyList(), "series" to emptyList())

    @Test fun unanimite_intersectionDesProfils() {
        assertEquals(setOf("b"), ChannelHidingRules.hiddenByAll(mapOf(1L to setOf("a", "b"), 2L to setOf("b", "c"))))
        assertEquals(emptySet<String>(), ChannelHidingRules.hiddenByAll(mapOf(1L to setOf("a"), 2L to emptySet())))
        assertEquals(emptySet<String>(), ChannelHidingRules.hiddenByAll(emptyMap()))
        // Un seul profil : tout ce qu'il masque est supprimé (comportement d'avant les profils).
        assertEquals(setOf("a"), ChannelHidingRules.hiddenByAll(mapOf(1L to setOf("a"))))
    }

    @Test fun choixPropres_sansLesUnanimes() {
        assertEquals(setOf("a"), ChannelHidingRules.partial(setOf("a", "b"), setOf("b")))
    }

    @Test fun fusionCloud_nouveauClient_chaqueProfilPrendSesChoixPlusLesUnanimes() {
        val local = mapOf("moi" to setOf("x", "u"), "maman" to setOf("u"))
        val out = ChannelHidingRules.mergeRemote(local, remoteAll = setOf("u", "v"), remotePartial = mapOf("moi" to setOf("y")))
        assertEquals(setOf("y", "u", "v"), out["moi"])
        assertEquals(setOf("u", "v"), out["maman"])
    }

    @Test fun fusionCloud_ancienClient_sansPHiddenCh_lesChoixPropresSontConserves() {
        val local = mapOf("moi" to setOf("x", "u"), "maman" to setOf("u"))
        // L'ancien appareil retire « u » de sa liste globale et en ajoute « v » : « x » (choix propre de « moi ») reste.
        val out = ChannelHidingRules.mergeRemote(local, remoteAll = setOf("v"), remotePartial = null)
        assertEquals(setOf("x", "v"), out["moi"])
        assertEquals(setOf("v"), out["maman"])
    }

    @Test fun protocole_allerRetour_empreinteEtCompatibiliteAncienneVersion() {
        val p = CloudPrefs(
            null, none, 1,
            hidden = listOf(HiddenChannel("u", "1", "Tous")),
            pHiddenCh = listOf(ProfileChannelsShare("Maman", listOf(HiddenChannel("x", "2", "Chaîne X")))),
            pOrder = listOf(ProfileHiddenShare("Maman", "live", listOf("3", "1"))),
        )
        val back = DisplayPrefs.parse(JSONObject(DisplayPrefs.toJson(p)))!!
        assertEquals(p.pHiddenCh, back.pHiddenCh)
        assertEquals(p.pOrder, back.pOrder)
        assertEquals(listOf("u"), back.hidden!!.map { it.id })
        assertNotEquals(DisplayPrefs.fingerprint(p), DisplayPrefs.fingerprint(p.copy(pHiddenCh = emptyList())))
        assertNotEquals(DisplayPrefs.fingerprint(p), DisplayPrefs.fingerprint(p.copy(pOrder = emptyList())))
        // Un envoi d'un appareil ancien (aucun des nouveaux champs) : lus comme « non transmis ».
        val old = DisplayPrefs.parse(JSONObject(DisplayPrefs.toJson(p.copy(pHiddenCh = null, pOrder = null))))!!
        assertNull(old.pHiddenCh); assertNull(old.pOrder)
    }
}
