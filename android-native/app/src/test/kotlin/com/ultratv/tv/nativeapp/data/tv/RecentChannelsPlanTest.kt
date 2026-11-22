package com.ultratv.tv.nativeapp.data.tv

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Chaîne de prévisualisation « Chaînes récentes » (contrat Kairo) : distincte de « Favoris ». */
class RecentChannelsPlanTest {
    private fun ch(id: Long, name: String = "Chaîne $id", cat: String? = "c") =
        ChannelEntity(id = id, providerId = 2, remoteId = "r$id", name = name, logo = null, categoryId = cat, streamUrl = "u")

    @Test fun ordre_de_l_historique_sans_doublon_au_plus_20() {
        val out = RecentChannelsPlan.select((1L..30L).map { ch(it) } + ch(3), emptySet())
        assertEquals(20, out.size)
        assertEquals((1L..20L).toList(), out.map { it.id })
    }

    @Test fun meme_nom_une_seule_tuile_et_separateurs_ecartes() {
        val out = RecentChannelsPlan.select(listOf(ch(1, "TF1 HD"), ch(2, "TF1 HD"), ch(3, "##### FR #####"), ch(4)), emptySet())
        assertEquals(listOf(1L, 4L), out.map { it.id })
    }

    @Test fun categories_masquees_par_le_profil_ecartees() {
        val out = RecentChannelsPlan.select(listOf(ch(1, cat = "adulte"), ch(2)), setOf("LIVE:2:adulte"))
        assertEquals(listOf(2L), out.map { it.id })
    }

    @Test fun identifiants_distincts_des_favoris_et_lien_de_lecture() {
        val c = ch(5)
        assertEquals("RECENT:LIVE:2:r5", RecentChannelsPlan.internalId(c))
        assertEquals("ultratv://live/2/r5", RecentChannelsPlan.deepLink(c))
        assertNotEquals(FavoritesChannelPlan.signature(listOf(c)), RecentChannelsPlan.signature(listOf(c)))
    }

    @Test fun signature_suit_la_selection() {
        assertNotEquals(RecentChannelsPlan.signature(listOf(ch(1), ch(2))), RecentChannelsPlan.signature(listOf(ch(2), ch(1))))
        assertEquals(RecentChannelsPlan.signature(listOf(ch(1))), RecentChannelsPlan.signature(listOf(ch(1))))
    }
}
