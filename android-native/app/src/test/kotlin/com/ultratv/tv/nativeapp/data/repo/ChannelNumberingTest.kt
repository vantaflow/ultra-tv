package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelNumberingTest {
    private fun ch(seq: Int, cat: String? = "a", pid: Long = 1, num: Int = 0) =
        ChannelEntity(id = seq.toLong(), providerId = pid, remoteId = "r$seq", name = "n$seq", logo = null, categoryId = cat, streamUrl = "x", seq = seq, num = num)

    // Le profil masque 1, 2 et 5 dans la catégorie « a », et 3 dans « b ».
    private val n = ChannelNumbering.of(listOf(Triple(1L, "a", 1), Triple(1L, "a", 2), Triple(1L, "a", 5), Triple(1L, "b", 3)))

    @Test fun sansMasquage_identite() {
        assertTrue(ChannelNumbering.NONE.isIdentity)
        assertEquals(7, ChannelNumbering.NONE.shown(1, "a", 7))
        assertEquals(7, ChannelNumbering.NONE.seqOf(1, "a", 7))
    }

    @Test fun masquer_1_et_2_fait_de_3_la_chaine_1() {
        assertEquals(1, n.shown(1, "a", 3))
        assertEquals(2, n.shown(1, "a", 4))
        assertEquals(3, n.shown(1, "a", 6))   // 5 masquée aussi : 6 - 3
    }

    @Test fun categories_et_sources_independantes() {
        assertEquals(3, n.shown(1, "b", 4))   // « b » : seule la 3 est masquée, 4 -> 3
        assertEquals(4, n.shown(1, "c", 4))
        assertEquals(4, n.shown(2, "a", 4))
        assertEquals(4, n.shown(1, null, 4))
    }

    @Test fun inverse_retrouve_le_seq_et_saute_les_masquees() {
        assertEquals(3, n.seqOf(1, "a", 1))
        assertEquals(4, n.seqOf(1, "a", 2))
        assertEquals(6, n.seqOf(1, "a", 3))
        assertEquals(1, n.seqOf(1, "b", 1))
        assertEquals(4, n.seqOf(1, "b", 3))
        assertEquals(9, n.seqOf(1, "c", 9))
        assertNull(n.seqOf(1, "a", 0))
    }

    @Test fun inverse_et_direct_concordent() {
        for (seq in 1..40) if (seq !in setOf(1, 2, 5)) assertEquals(seq, n.seqOf(1, "a", n.shown(1, "a", seq)))
    }

    @Test fun masquees_en_tete_desordonnees_et_doublons() {
        val m = ChannelNumbering.of(listOf(Triple(1L, "a", 2), Triple(1L, "a", 1), Triple(1L, "a", 2), Triple(1L, "a", 0)))
        assertEquals(1, m.shown(1, "a", 3))
        assertEquals(3, m.seqOf(1, "a", 1))
    }

    @Test fun seq_zero_garde_le_numero_fournisseur() {
        assertEquals(120, n.numberOf(ch(0, num = 120)))
        assertEquals(0, n.numberOf(ch(0)))
        assertEquals(0, n.shown(1, "a", 0))
        assertEquals(1, n.numberOf(ch(3)))
    }
}
