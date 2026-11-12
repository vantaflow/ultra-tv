package com.ultratv.tv.nativeapp.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeldKeyGuardTest {
    @Test fun appuiLong_repetitionsEtRelachementAvales_puisNouvelAppuiAccepte() {
        val g = HeldKeyGuard()
        // La fenêtre vient de s'ouvrir, OK toujours enfoncé : répétitions puis relâchement.
        assertFalse(g.allow(isOk = true, down = true, repeat = 3))
        assertFalse(g.allow(isOk = true, down = true, repeat = 4))
        assertFalse(g.allow(isOk = true, down = false, repeat = 0))
        // Nouvel appui : validation normale.
        assertTrue(g.allow(isOk = true, down = true, repeat = 0))
        assertTrue(g.allow(isOk = true, down = false, repeat = 0))
    }

    @Test fun autresTouches_toujoursTransmises() {
        val g = HeldKeyGuard()
        assertTrue(g.allow(isOk = false, down = true, repeat = 0))
        assertTrue(g.allow(isOk = false, down = true, repeat = 5))
        assertTrue(g.allow(isOk = false, down = false, repeat = 0))
    }

    @Test fun fenetreOuverteParUnClic_premierAppuiAccepte() {
        val g = HeldKeyGuard()
        assertTrue(g.allow(isOk = true, down = true, repeat = 0))
        assertTrue(g.allow(isOk = true, down = true, repeat = 1))
        assertTrue(g.allow(isOk = true, down = false, repeat = 0))
    }
}
