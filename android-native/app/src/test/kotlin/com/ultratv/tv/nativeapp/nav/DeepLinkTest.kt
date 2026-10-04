package com.ultratv.tv.nativeapp.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    @Test fun live_aller_retour_avec_caracteres_speciaux() {
        val rid = "ch 1/é+x"
        assertEquals(DeepLink.PlayLive(7, rid), DeepLink.parse(DeepLink.live(7, rid)))
    }

    @Test fun film_et_episode_sont_distingues() {
        assertEquals(DeepLink.PlayMovie(1, "42"), DeepLink.parse(DeepLink.movie(1, "42")))
        assertEquals(DeepLink.PlayEpisode(1, "9"), DeepLink.parse(DeepLink.episode(1, "9")))
    }

    @Test fun recherche_decode_la_requete() {
        assertEquals(DeepLink.Search("le journal & co"), DeepLink.parse(DeepLink.search("le journal & co")))
    }

    @Test fun lien_invalide_ou_etranger_renvoie_null() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse("https://exemple.test/live/1/2"))
        assertNull(DeepLink.parse("ultratv://live/abc/2"))
        assertNull(DeepLink.parse("ultratv://live/1"))
        assertNull(DeepLink.parse("ultratv://search?q="))
        assertNull(DeepLink.parse("ultratv://inconnu/1/2"))
    }
}
