package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.prefs.ChannelRef
import com.ultratv.tv.nativeapp.data.prefs.RecentChannelsStore
import com.ultratv.tv.nativeapp.ui.player.zap.NumberEntry
import com.ultratv.tv.nativeapp.ui.player.zap.ZapLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZapLogicTest {
    private fun ch(id: Long, name: String, seq: Int = 0) = ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = name, logo = null, categoryId = "c", streamUrl = "x", seq = seq)

    private val list = listOf(
        ch(1, "##### SPORT #####"),
        ch(2, "Sport 1", seq = 1),
        ch(3, "===== 4K ====="),
        ch(4, "Sport 2", seq = 2),
        ch(5, "Sport 3", seq = 3),
    )

    @Test fun step_vers_lavant_saute_les_separateurs() {
        assertEquals(3, ZapLogic.step(list, 1, true))     // 2 -> 4 (saute le séparateur d'index 2)
        assertEquals(1, ZapLogic.step(list, 3, false))
    }

    @Test fun step_reboucle_en_sautant_le_separateur_de_tete() {
        assertEquals(1, ZapLogic.step(list, 4, true))     // 5 -> (0 séparateur) -> 2
        assertEquals(4, ZapLogic.step(list, 1, false))
    }

    @Test fun step_sans_autre_chaine_lisible_renvoie_null() {
        assertNull(ZapLogic.step(listOf(ch(1, "##### A #####"), ch(2, "Seule", 1), ch(3, "===== B =====")), 1, true))
        assertNull(ZapLogic.step(emptyList(), 0, true))
    }

    @Test fun byNumber_trouve_par_numero_sequentiel_et_ignore_les_separateurs() {
        assertEquals(3, ZapLogic.byNumber(list, 2))
        assertNull(ZapLogic.byNumber(list, 9))
        assertNull(ZapLogic.byNumber(list, 0))
    }

    @Test fun pushRecent_met_en_tete_sans_doublon_et_limite_a_vingt() {
        assertEquals(listOf("c", "a", "b"), ZapLogic.pushRecent(listOf("a", "b", "c"), "c"))
        val full = (1..20).map { "k$it" }
        val out = ZapLogic.pushRecent(full, "new")
        assertEquals(20, out.size); assertEquals("new", out.first()); assertFalse(out.contains("k20"))
    }

    @Test fun recentStore_encodage_aller_retour() {
        val l = listOf(ChannelRef(1, "a|b"), ChannelRef(2, "x"))
        assertEquals(l, RecentChannelsStore.decode(RecentChannelsStore.encode(l)))
        assertTrue(RecentChannelsStore.decode("garbage\n|\n").isEmpty())
    }

    @Test fun saisie_validation_automatique_apres_1500_ms() {
        val e = NumberEntry()
        e.append(1, 1_000); e.append(0, 1_400)
        assertEquals("10", e.text); assertEquals(10, e.value)
        assertFalse(e.shouldCommit(2_800))
        assertTrue(e.shouldCommit(2_900))
    }

    @Test fun saisie_quatre_chiffres_max_puis_repart() {
        val e = NumberEntry()
        listOf(1, 2, 3, 4, 5).forEach { e.append(it, 0) }
        assertEquals("5", e.text)
        e.clear(); assertFalse(e.isActive); assertFalse(e.shouldCommit(99_999))
    }

    @Test fun touches_chiffres_et_pave_numerique() {
        assertEquals(0, NumberEntry.digitOfKeyCode(7)); assertEquals(9, NumberEntry.digitOfKeyCode(16))
        assertEquals(0, NumberEntry.digitOfKeyCode(144)); assertEquals(9, NumberEntry.digitOfKeyCode(153))
        assertNull(NumberEntry.digitOfKeyCode(66))
    }
}
