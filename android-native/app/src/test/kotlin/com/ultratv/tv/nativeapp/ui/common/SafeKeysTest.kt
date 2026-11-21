package com.ultratv.tv.nativeapp.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Règle commune des clés de listes paresseuses dérivées des données (« Key "— · M6" was already used », Freebox Player
 * POP, 1.3.72) : unicité garantie, première occurrence inchangée (focus conservé), doublons stables.
 */
class SafeKeysTest {
    @Test fun `cles uniques - inchangees et de meme type`() {
        assertEquals(listOf<Any>(1L, 2L, 3L), uniqueKeys(listOf(1L, 2L, 3L)) { it })
    }

    @Test fun `qualite inconnue et meme nom - deux variantes donnent deux cles distinctes`() {
        val keys = uniqueKeys(listOf("— · M6", "— · M6", "HD · M6")) { it }
        assertEquals(3, keys.toSet().size)
        assertEquals("— · M6", keys[0])
        assertEquals("HD · M6", keys[2])
    }

    @Test fun `la cle d un doublon ne depend pas de sa position dans la liste`() {
        val a = uniqueKeys(listOf("x", "M6", "M6")) { it }
        val b = uniqueKeys(listOf("y", "z", "M6", "w", "M6")) { it }
        assertEquals(a[2], b[4])
    }

    @Test fun `une cle derivee ne heurte jamais une cle des donnees`() {
        val derived = uniqueKeys(listOf("A", "A")) { it }[1] as String
        val keys = uniqueKeys(listOf("A", derived, "A", "A")) { it }
        assertEquals(4, keys.toSet().size)
    }

    @Test fun `identifiants numeriques repetes`() {
        val keys = uniqueKeys(listOf(7L, 7L, 7L, 8L)) { it }
        assertEquals(4, keys.toSet().size)
        assertEquals(7L, keys[0])
    }

    @Test fun `liste vide`() { assertEquals(emptyList<Any>(), uniqueKeys(emptyList<String>()) { it }) }
}
