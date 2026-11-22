package com.ultratv.tv.nativeapp.data.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** Fiches fermées au profil : seul le profil Enfants (masquages + adultes) ; un masquage volontaire ailleurs n'est qu'un tri. */
class DetailBlockedKeysTest {
    @Test fun `profil ordinaire - aucune fiche fermee par un masquage`() {
        assertEquals(emptySet<String>(), KidsRules.detailBlockedKeys(kids = false, chosen = setOf("MOVIE:1:c1"), adult = setOf("MOVIE:1:x")))
    }

    @Test fun `profil Enfants - masquages et categories adultes`() {
        assertEquals(setOf("MOVIE:1:c1", "MOVIE:1:x"), KidsRules.detailBlockedKeys(kids = true, chosen = setOf("MOVIE:1:c1"), adult = setOf("MOVIE:1:x")))
    }
}
