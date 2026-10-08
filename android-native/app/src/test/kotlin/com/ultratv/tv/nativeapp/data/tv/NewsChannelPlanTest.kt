package com.ultratv.tv.nativeapp.data.tv

import com.ultratv.tv.nativeapp.data.tv.NewsChannelPlan.Item
import com.ultratv.tv.nativeapp.data.tv.NewsChannelPlan.Kind
import com.ultratv.tv.nativeapp.nav.DeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsChannelPlanTest {
    private fun m(id: String, key: Long = id.toLong(), poster: String? = "p", title: String = "t$id") = Item(Kind.MOVIE, 1, id, title, poster, key)
    private fun s(id: String, key: Long = id.toLong(), poster: String? = "p", title: String = "t$id") = Item(Kind.SERIES, 1, id, title, poster, key)

    @Test fun select_filmsEtSeries_entrelacesParAddedKeyDecroissant() {
        val out = NewsChannelPlan.select(listOf(m("1"), m("3"), m("2")), listOf(s("10"), s("30"), s("20")))
        assertEquals(listOf("3", "30", "2", "20", "1", "10"), out.map { it.remoteId })
    }

    @Test fun select_sansAfficheOuSansTitre_ecartes() {
        val out = NewsChannelPlan.select(listOf(m("1", poster = null), m("2", poster = " "), m("3", title = " "), m("4")), emptyList())
        assertEquals(listOf("4"), out.map { it.remoteId })
    }

    @Test fun select_unTypeEpuise_laisseLaPlaceALAutre() {
        val out = NewsChannelPlan.select(listOf(m("1")), listOf(s("5"), s("4"), s("3")))
        assertEquals(listOf("1", "5", "4", "3"), out.map { it.remoteId })
    }

    @Test fun select_auPlusVingt() {
        val out = NewsChannelPlan.select((1..30).map { m("$it") }, (1..30).map { s("$it") })
        assertEquals(NewsChannelPlan.MAX, out.size)
        assertEquals(10, out.count { it.kind == Kind.MOVIE })
    }

    @Test fun select_vide_renvoieVide() = assertTrue(NewsChannelPlan.select(emptyList(), emptyList()).isEmpty())

    @Test fun internalId_memeIdFilmEtSerie_restentDistincts() = assertNotEquals(NewsChannelPlan.internalId(m("1")), NewsChannelPlan.internalId(s("1")))

    @Test fun deepLink_film_ouvreLaFiche_serie_ouvreLaFiche() {
        assertEquals(DeepLink.OpenMovie(1, "9"), DeepLink.parse(NewsChannelPlan.deepLink(m("9"))))
        assertEquals(DeepLink.OpenSeries(1, "9"), DeepLink.parse(NewsChannelPlan.deepLink(s("9"))))
    }

    @Test fun diff_ajoutMiseAJourEtSuppression() {
        val existing = mapOf(NewsChannelPlan.internalId(m("1")) to 100L, NewsChannelPlan.internalId(m("2")) to 101L)
        val d = NewsChannelPlan.diff(existing, listOf(m("2"), m("3")))
        assertEquals(listOf("3"), d.insert.map { it.remoteId })
        assertEquals(listOf(101L), d.update.map { it.first })
        assertEquals(listOf(100L), d.deleteRowIds)
    }

    @Test fun diff_chaineVide_toutEstInsere() {
        val d = NewsChannelPlan.diff(emptyMap(), listOf(m("1"), s("1")))
        assertEquals(2, d.insert.size)
        assertTrue(d.update.isEmpty() && d.deleteRowIds.isEmpty())
    }

    @Test fun weight_lePremierAuPoidsLePlusFort() {
        assertTrue(NewsChannelPlan.weight(0, 5) > NewsChannelPlan.weight(4, 5))
        assertEquals(1, NewsChannelPlan.weight(4, 5))
    }

    @Test fun signature_stableSiIdentique_changeSiOrdreOuAffiche() {
        val a = listOf(m("1"), s("2"))
        assertEquals(NewsChannelPlan.signature(a), NewsChannelPlan.signature(listOf(m("1"), s("2"))))
        assertNotEquals(NewsChannelPlan.signature(a), NewsChannelPlan.signature(a.reversed()))
        assertNotEquals(NewsChannelPlan.signature(a), NewsChannelPlan.signature(listOf(m("1", poster = "x"), s("2"))))
    }
}
