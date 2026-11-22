package com.ultratv.tv.nativeapp.data.tv

import androidx.tvprovider.media.tv.TvContractCompat
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.tv.NewsChannelPlan.Item
import com.ultratv.tv.nativeapp.data.tv.NewsChannelPlan.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Rangées Android TV (contrat Kairo) : image paysage 16:9 quand le titre en a une, et « Nouveautés » qui suit le profil. */
class LandscapeArtAndProfileTest {
    private val r169 = TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
    private val r23 = TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3

    private fun m(id: String, poster: String? = "p$id", landscape: String? = null, cat: String? = "c", title: String = "t$id") =
        Item(Kind.MOVIE, 1, id, title, poster, id.toLong(), landscape = landscape, categoryId = cat)

    @Test fun nouveautes_fond_du_titre_en_16_9_sinon_affiche_en_2_3() {
        assertEquals(NewsChannelPlan.Art("bg", r169), NewsChannelPlan.art(m("1", landscape = "bg")))
        assertEquals(NewsChannelPlan.Art("p1", r23), NewsChannelPlan.art(m("1")))
        assertEquals(NewsChannelPlan.Art("p1", r23), NewsChannelPlan.art(m("1", landscape = " ")))
    }

    @Test fun nouveautes_titre_avec_seulement_un_fond_garde() {
        assertEquals(listOf("1"), NewsChannelPlan.select(listOf(m("1", poster = null, landscape = "bg")), emptyList()).map { it.remoteId })
    }

    @Test fun nouveautes_signature_suit_l_image_paysage() {
        assertNotEquals(NewsChannelPlan.signature(listOf(m("1"))), NewsChannelPlan.signature(listOf(m("1", landscape = "bg"))))
    }

    @Test fun nouveautes_categories_masquees_par_le_profil_ecartees() {
        val out = NewsChannelPlan.visible(listOf(m("1", cat = "a"), m("2", cat = "b")), hiddenKeys = setOf("MOVIE:1:a"), lockedCategoryKeys = emptySet(), kids = false)
        assertEquals(listOf("2"), out.map { it.remoteId })
    }

    @Test fun nouveautes_categories_verrouillees_ecartees() {
        val out = NewsChannelPlan.visible(listOf(m("1", cat = "a"), m("2", cat = "b")), hiddenKeys = emptySet(), lockedCategoryKeys = setOf("MOVIE:1:b"), kids = false)
        assertEquals(listOf("1"), out.map { it.remoteId })
    }

    @Test fun nouveautes_profil_enfant_sans_titre_adulte() {
        val items = listOf(m("1", title = "XXX Nuit chaude"), m("2", title = "Le Roi Lion"))
        assertEquals(listOf("2"), NewsChannelPlan.visible(items, emptySet(), emptySet(), kids = true).map { it.remoteId })
        assertEquals(listOf("1", "2"), NewsChannelPlan.visible(items, emptySet(), emptySet(), kids = false).map { it.remoteId })
    }

    @Test fun nouveautes_sans_categorie_gardee() {
        assertEquals(listOf("1"), NewsChannelPlan.visible(listOf(m("1", cat = null)), setOf("MOVIE:1:a"), setOf("MOVIE:1:a"), kids = false).map { it.remoteId })
    }

    private fun h(kind: String, poster: String? = "affiche") = WatchHistoryEntity(providerId = 1, kind = kind, remoteId = "9", title = "Titre", poster = poster, streamUrl = "u", positionMs = 120_000, durationMs = 3_600_000, parentRemoteId = "s1")

    @Test fun continuer_a_regarder_fond_en_16_9_sinon_affiche_en_2_3() {
        assertEquals(NewsChannelPlan.Art("fond", r169), WatchNextPlan.art(h("MOVIE"), "fond"))
        assertEquals(NewsChannelPlan.Art("fond", r169), WatchNextPlan.art(h("EPISODE"), "fond"))
        assertEquals(NewsChannelPlan.Art("affiche", r23), WatchNextPlan.art(h("MOVIE"), null))
        assertEquals(NewsChannelPlan.Art("affiche", r23), WatchNextPlan.art(h("EPISODE"), ""))
    }

    @Test fun continuer_a_regarder_cle_de_l_image_paysage() {
        // Film : son propre fond ; épisode : le fond de sa série.
        assertEquals(WatchNextPlan.LandscapeKey(Kind.MOVIE, "9"), WatchNextPlan.landscapeKey(h("MOVIE")))
        assertEquals(WatchNextPlan.LandscapeKey(Kind.SERIES, "s1"), WatchNextPlan.landscapeKey(h("EPISODE")))
        assertEquals(null, WatchNextPlan.landscapeKey(h("LIVE")))
    }
}
