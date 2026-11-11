package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure de l'ordre personnel des chaînes : fusion avec l'ordre naturel, déplacement, réécriture minimale, remise à zéro. */
class ChannelOrderTest {
    private val natural = listOf("a", "b", "c", "d", "e")

    @Test fun merge_sansPlace_gardeLOrdreNaturel() {
        assertSame(natural, ChannelOrder.merge(natural, emptyMap()) { it })
    }

    @Test fun merge_lesChainesPlaceesPassentAvant_lesAutresSuiventEnOrdreNaturel() {
        // c en 1, a en 2 : b, d, e (sans place) suivent dans leur ordre naturel.
        val merged = ChannelOrder.merge(natural, mapOf("c" to 1, "a" to 2)) { it }
        assertEquals(listOf("c", "a", "b", "d", "e"), merged)
    }

    @Test fun merge_placesEgales_departageesParLOrdreNaturel() {
        assertEquals(listOf("b", "d", "a", "c", "e"), ChannelOrder.merge(natural, mapOf("d" to 1, "b" to 1)) { it })
    }

    @Test fun merge_placeDUneChaineAbsente_estIgnoree() {
        assertEquals(natural, ChannelOrder.merge(natural, mapOf("zzz" to 1)) { it })
    }

    @Test fun move_versLeBas_etVersLeHaut() {
        assertEquals(listOf("a", "c", "b", "d", "e"), ChannelOrder.move(natural, "b", 1))
        assertEquals(listOf("b", "a", "c", "d", "e"), ChannelOrder.move(natural, "b", -1))
    }

    @Test fun move_estBorneAuxExtremites_sansChangement() {
        assertSame(natural, ChannelOrder.move(natural, "a", -1))
        assertSame(natural, ChannelOrder.move(natural, "e", 1))
        assertEquals(listOf("e", "a", "b", "c", "d"), ChannelOrder.move(natural, "e", -10))
    }

    @Test fun move_cleInconnueOuDeplacementNul_listeInchangee() {
        assertSame(natural, ChannelOrder.move(natural, "x", 1))
        assertSame(natural, ChannelOrder.move(natural, "c", 0))
    }

    @Test fun positionsOf_numeroteA_partirDeUn() {
        assertEquals(mapOf("c" to 1, "a" to 2, "b" to 3), ChannelOrder.positionsOf(listOf("c", "a", "b")))
    }

    @Test fun changed_premierDeplacement_ecritToutesLesPlaces_puisDeuxLignesParRang() {
        val first = ChannelOrder.positionsOf(ChannelOrder.move(natural, "c", -1))   // a c b d e : tout est matérialisé
        assertEquals(natural.size, ChannelOrder.changed(emptyMap(), first).size)
        val second = ChannelOrder.positionsOf(ChannelOrder.move(listOf("a", "c", "b", "d", "e"), "c", -1))   // c a b d e
        assertEquals(mapOf("c" to 1, "a" to 2), ChannelOrder.changed(first, second))
    }

    @Test fun changed_ordreIdentique_rienAReecrire() {
        val p = ChannelOrder.positionsOf(natural)
        assertTrue(ChannelOrder.changed(p, p).isEmpty())
    }

    @Test fun reset_redonneLOrdreNaturel() {
        val custom = ChannelOrder.positionsOf(listOf("e", "d", "c", "b", "a"))
        assertEquals(listOf("e", "d", "c", "b", "a"), ChannelOrder.merge(natural, custom) { it })
        assertEquals(natural, ChannelOrder.merge(natural, ChannelOrder.reset()) { it })
    }

    @Test fun deplacementsSuccessifs_correspondentAuMerge() {
        var keys = natural
        keys = ChannelOrder.move(keys, "d", -2)   // a d b c e
        keys = ChannelOrder.move(keys, "a", 3)    // d b c a e
        assertEquals(listOf("d", "b", "c", "a", "e"), keys)
        assertEquals(keys, ChannelOrder.merge(natural, ChannelOrder.positionsOf(keys)) { it })
    }
}
