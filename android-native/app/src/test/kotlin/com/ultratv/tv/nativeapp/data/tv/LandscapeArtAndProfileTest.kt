package com.ultratv.tv.nativeapp.data.tv

import androidx.tvprovider.media.tv.TvContractCompat
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.tv.NewsChannelPlan.Item
import com.ultratv.tv.nativeapp.data.tv.NewsChannelPlan.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Rangées Android TV : films, séries et épisodes publiés avec leur AFFICHE en 2:3 (plus la photo de scène 16:9 de la
 * 1.3.73) ; sans affiche seulement, le fond composé en 2:3. Et « Nouveautés » qui suit le profil.
 */
class LandscapeArtAndProfileTest {
    private val r23 = TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3

    private fun m(id: String, poster: String? = "p$id", landscape: String? = null, cat: String? = "c", title: String = "t$id") =
        Item(Kind.MOVIE, 1, id, title, poster, id.toLong(), landscape = landscape, categoryId = cat)

    @Test fun nouveautes_affiche_en_2_3_meme_avec_un_fond() {
        assertEquals(NewsChannelPlan.Art("p1", r23), NewsChannelPlan.art(m("1", landscape = "bg")))
        assertEquals(NewsChannelPlan.Art("p1", r23), NewsChannelPlan.art(m("1")))
        assertEquals(NewsChannelPlan.Art("p1", r23), NewsChannelPlan.art(m("1", landscape = " ")))
    }

    @Test fun nouveautes_sans_affiche_fond_compose_en_2_3() {
        val a = NewsChannelPlan.art(m("1", poster = null, landscape = "bg", title = "Dune"))
        assertEquals(r23, a.aspectRatio)
        assertEquals(TvArtRequest.Backdrop("bg", "Dune"), a.compose)
        assertEquals(null, NewsChannelPlan.art(m("1", poster = " ", landscape = null)).compose)
    }

    @Test fun chaines_activables_films_et_series_en_affiche_2_3() {
        val v = TvChannelPlan.vodProgram("TOP10", TvChannelPlan.Vod(Kind.MOVIE, 1, "9", "Dune", "affiche", "fond", "c"))
        assertEquals("affiche", v.image)
        assertEquals(r23, v.aspectRatio)
        assertEquals(null, v.compose)
        val s = TvChannelPlan.vodProgram("MYLIST", TvChannelPlan.Vod(Kind.SERIES, 1, "8", "Lost", null, "fond", "c"))
        assertEquals(r23, s.aspectRatio)
        assertEquals(TvArtRequest.Backdrop("fond", "Lost"), s.compose)
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

    private fun img(poster: String?, backdrop: String?) = WatchNextPlan.TitleImages(poster, backdrop)

    @Test fun continuer_a_regarder_film_affiche_en_2_3() {
        assertEquals(NewsChannelPlan.Art("catalogue", r23), WatchNextPlan.art(h("MOVIE"), img("catalogue", "fond")))
        // Catalogue introuvable : l'affiche gardée dans l'historique.
        assertEquals(NewsChannelPlan.Art("affiche", r23), WatchNextPlan.art(h("MOVIE"), null))
        assertEquals(NewsChannelPlan.Art("affiche", r23), WatchNextPlan.art(h("MOVIE"), img(null, "fond")))
    }

    @Test fun continuer_a_regarder_episode_affiche_de_la_serie() {
        // L'historique d'un épisode garde l'image de l'épisode (scène) : jamais publiée comme affiche.
        assertEquals(NewsChannelPlan.Art("serie", r23), WatchNextPlan.art(h("EPISODE", poster = "scene"), img("serie", "fond")))
        val noPoster = WatchNextPlan.art(h("EPISODE", poster = "scene"), img(null, "fond"))
        assertEquals(r23, noPoster.aspectRatio)
        assertEquals(TvArtRequest.Backdrop("fond", "Titre"), noPoster.compose)
        val nothing = WatchNextPlan.art(h("EPISODE", poster = "scene"), null)
        assertEquals(TvArtRequest.Backdrop("scene", "Titre"), nothing.compose)
    }

    @Test fun continuer_a_regarder_cle_du_titre() {
        // Film : lui-même ; épisode : sa série.
        assertEquals(WatchNextPlan.TitleKey(Kind.MOVIE, "9"), WatchNextPlan.titleKey(h("MOVIE")))
        assertEquals(WatchNextPlan.TitleKey(Kind.SERIES, "s1"), WatchNextPlan.titleKey(h("EPISODE")))
        assertEquals(null, WatchNextPlan.titleKey(h("LIVE")))
    }
}
