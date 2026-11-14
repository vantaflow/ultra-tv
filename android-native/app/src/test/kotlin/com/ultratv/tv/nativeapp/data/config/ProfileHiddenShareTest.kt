package com.ultratv.tv.nativeapp.data.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileHiddenShareTest {
    @Test fun categoriesParProfil_allerRetour_etEmpreinte() {
        val p = CloudPrefs(null, mapOf("live" to emptyList(), "movie" to emptyList(), "series" to emptyList()), 1, pHidden = listOf(ProfileHiddenShare("Enfants", "live", listOf("12"))))
        val back = DisplayPrefs.parse(JSONObject(DisplayPrefs.toJson(p)))!!
        assertEquals(listOf(ProfileHiddenShare("Enfants", "live", listOf("12"))), back.pHidden)
        assertNotEquals(DisplayPrefs.fingerprint(p), DisplayPrefs.fingerprint(p.copy(pHidden = emptyList())))
    }
}
