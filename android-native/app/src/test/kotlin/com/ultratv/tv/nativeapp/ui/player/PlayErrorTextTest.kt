package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.engine.PlayErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cas relevés dans la télémétrie : chacun doit donner à l'utilisateur la vraie cause, pas « Problème de réseau ». */
class PlayErrorTextTest {
    @Test fun `503 du fournisseur - panne chez lui, pas un probleme de reseau`() {
        assertEquals(PlayErrorCase.SERVER_DOWN, PlayErrorText.case(PlayErrorKind.NETWORK, 503))
        assertEquals(PlayErrorCase.SERVER_DOWN, PlayErrorText.case(PlayErrorKind.NETWORK, 500))
        assertEquals("HTTP 503", PlayErrorText.code(PlayErrorCase.SERVER_DOWN, 503))
    }

    @Test fun `429 - trop de lectures simultanees`() {
        assertEquals(PlayErrorCase.TOO_MANY, PlayErrorText.case(PlayErrorKind.NETWORK, 429))
        assertEquals("HTTP 429", PlayErrorText.code(PlayErrorCase.TOO_MANY, 429))
    }

    @Test fun `autre 4xx - demande rejetee`() {
        assertEquals(PlayErrorCase.REJECTED, PlayErrorText.case(PlayErrorKind.NETWORK, 400))
    }

    @Test fun `connexion echouee sans reponse - serveur injoignable, aucun code`() {
        val c = PlayErrorText.case(PlayErrorKind.NETWORK, null)
        assertEquals(PlayErrorCase.UNREACHABLE, c)
        assertNull(PlayErrorText.code(c, null))
    }

    @Test fun `refus et chaine disparue gardent leur cas et montrent le code`() {
        assertEquals(PlayErrorCase.REFUSED, PlayErrorText.case(PlayErrorKind.REFUSED, 403))
        assertEquals("HTTP 403", PlayErrorText.code(PlayErrorCase.REFUSED, 403))
        assertEquals(PlayErrorCase.PORTAL_REFUSED, PlayErrorText.case(PlayErrorKind.PORTAL_REFUSED, 458))
        assertEquals(PlayErrorCase.NOT_FOUND, PlayErrorText.case(PlayErrorKind.NOT_FOUND, 404))
        assertEquals("HTTP 404", PlayErrorText.code(PlayErrorCase.NOT_FOUND, 404))
    }

    @Test fun `format, decodeur et image absente sont distingues, sans vieux code HTTP`() {
        assertEquals(PlayErrorCase.FORMAT, PlayErrorText.case(PlayErrorKind.FORMAT, null))
        assertEquals(PlayErrorCase.DECODER, PlayErrorText.case(PlayErrorKind.DECODER, null))
        assertEquals(PlayErrorCase.NO_PICTURE, PlayErrorText.case(PlayErrorKind.NO_PICTURE, 503))
        assertNull(PlayErrorText.code(PlayErrorCase.NO_PICTURE, 503))
        assertNull(PlayErrorText.code(PlayErrorCase.FORMAT, 200))
    }

    @Test fun `tout type d erreur a un cas - aucun ecran sans explication`() {
        for (k in PlayErrorKind.entries) for (http in listOf(null, 0, 200, 401, 404, 429, 500, 503, 884, -1)) PlayErrorText.case(k, http)
        assertEquals(PlayErrorCase.NO_RESPONSE, PlayErrorText.case(PlayErrorKind.UNKNOWN, null))
        assertEquals(PlayErrorCase.NO_RESPONSE, PlayErrorText.case(PlayErrorKind.BEHIND_LIVE, null))
        assertNull(PlayErrorText.code(PlayErrorCase.SERVER_DOWN, 0))
    }
}
