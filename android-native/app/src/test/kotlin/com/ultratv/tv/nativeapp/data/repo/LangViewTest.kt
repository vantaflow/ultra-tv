package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.ui.live.pickVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LangViewTest {
    @Test fun aucunFiltre_neFiltreRien_etLaListeSqlN_estJamaisVide() {
        assertEquals(0, LangView.ALL.useLang)
        assertEquals(listOf(""), LangView.ALL.langs)
        assertEquals(listOf(1, 2), LangView.ALL.filter(listOf(1, 2)) { "x" })
    }
    @Test fun toggle_ajouteEtRetire_etRetombeSurAucunFiltre() {
        val v = LangView.ALL.toggle("fr").toggle("ar")
        assertEquals(setOf("fr", "ar"), v.selected); assertEquals(1, v.useLang)
        val back = v.toggle("fr").toggle("ar")
        assertNull(back.selected); assertEquals(0, back.useLang)
    }
    @Test fun filter_garde_seulement_lesLanguesCochees() {
        val v = LangView(setOf("fr", "MULTI"))
        assertEquals(listOf("fr", "MULTI"), v.filter(listOf("fr", "en", "MULTI", "")) { it })
    }
    @Test fun nonDetermine_estUneCaseCochable() {
        assertEquals(listOf(""), LangView(setOf("")).langs)
        assertEquals(1, LangView(setOf("")).useLang)
    }
}

class PickVariantTest {
    private fun ch(id: Long, q: Int) = ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = "TF1", logo = null, categoryId = "c", streamUrl = "x", quality = q)
    private val sd = ch(1, 1); private val hd = ch(2, 2); private val fhd = ch(3, 3); private val uhd = ch(4, 4)
    private val all = listOf(uhd, fhd, hd, sd)

    @Test fun exacte_quandDisponible() = assertEquals(fhd, pickVariant(sd, all, "fhd"))
    @Test fun sinon_laMeilleureEnDessous() = assertEquals(hd, pickVariant(uhd, listOf(uhd, hd, sd), "fhd"))
    @Test fun sinon_laPlusBasseAuDessus() = assertEquals(fhd, pickVariant(fhd, listOf(uhd, fhd), "hd"))
    @Test fun auto_gardeLaChaineChoisie() = assertEquals(sd, pickVariant(sd, all, "auto"))
    @Test fun qualiteInconnue_gardeLaChaineChoisie() = assertEquals(ch(9, 0), pickVariant(ch(9, 0), listOf(ch(9, 0), ch(8, 0)), "4k").let { ch(9, 0) })
}
