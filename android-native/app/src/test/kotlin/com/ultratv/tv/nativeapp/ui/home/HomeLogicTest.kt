package com.ultratv.tv.nativeapp.ui.home

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.data.trakt.TraktCard
import com.ultratv.tv.nativeapp.ui.catalog.PosterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    private val supported = setOf("XTREAM", "M3U", "M3U_LOCAL")
    private fun hist(id: String, kind: String = "MOVIE", pos: Long = 0, dur: Long = 0, title: String = "T$id") =
        WatchHistoryEntity(providerId = 1, kind = kind, remoteId = id, title = title, poster = null, streamUrl = "http://x/$id", positionMs = pos, durationMs = dur, watchedAt = 0)
    private fun poster(id: Long, show: Boolean = false) = catalogPosterOf(PosterItem(id, "P$id", null, null, null), show)
    private fun channel(id: Long) = ChannelCardUi(ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = "C$id", logo = null, categoryId = null, streamUrl = "u"), null)

    // ── Ordre et visibilité des rangées ──

    @Test fun homeSectionOrder_toutVide_aucuneRangee() {
        assertEquals(emptyList<HomeSectionId>(), HomeRows.EMPTY.order)
    }

    @Test fun homeSectionOrder_toutRempli_ordreDeLaMaquette() {
        val r = HomeRows(
            resume = listOf(resumeCardOf(hist("1"), "", { it })),
            watchlist = listOf(poster(1)), recommended = listOf(poster(2)), trending = listOf(poster(3)), popular = listOf(poster(4)),
            upNext = listOf(poster(7, true)), traktLists = (1..5).map { TraktListUi("$it", "L$it", listOf(poster(10L + it))) },
            recentChannels = listOf(resumeCardOf(hist("2", "LIVE"), "rc-", { it })),
            latestMovies = listOf(poster(5)), latestSeries = listOf(poster(6, true)), channels = listOf(channel(1)),
        )
        assertEquals(HomeSectionId.entries.toList(), r.order)
    }

    @Test fun homeSectionOrder_rangeesVides_sautees_ordreConserve() {
        val r = HomeRows(latestSeries = listOf(poster(1, true)), resume = listOf(resumeCardOf(hist("1"), "", { it })), channels = listOf(channel(3)))
        assertEquals(listOf(HomeSectionId.RESUME, HomeSectionId.LATEST_SERIES, HomeSectionId.CHANNELS), r.order)
    }

    @Test fun homeSectionOrder_clesUniques_utilisablesCommeClesLazy() {
        // Les clés de la LazyColumn sont les noms : jamais deux fois la même (sinon plantage de la liste).
        val names = HomeSectionId.entries.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun mobileSectionOrder_retireLesRangeesPropresALaTv_ordreConserve() {
        val all = HomeSectionId.entries.toList()
        assertEquals(
            listOf(
                HomeSectionId.RESUME, HomeSectionId.TRAKT_UP_NEXT, HomeSectionId.TRAKT_WATCHLIST, HomeSectionId.TRAKT_RECOMMENDED, HomeSectionId.TRAKT_TRENDING, HomeSectionId.TRAKT_POPULAR,
                HomeSectionId.TRAKT_LIST_1, HomeSectionId.TRAKT_LIST_2, HomeSectionId.TRAKT_LIST_3, HomeSectionId.TRAKT_LIST_4, HomeSectionId.TRAKT_LIST_5, HomeSectionId.CHANNELS,
            ),
            mobileSectionOrder(all),
        )
    }

    @Test fun contentType_rangeesDeMemeForme_partagentLeType() {
        assertEquals(HomeSectionId.TRAKT_WATCHLIST.contentType, HomeSectionId.LATEST_MOVIES.contentType)
        assertEquals(HomeSectionId.RESUME.contentType, HomeSectionId.RECENT_CHANNELS.contentType)
        assertNotEquals(HomeSectionId.CHANNELS.contentType, HomeSectionId.RESUME.contentType)
    }

    // ── Encart du haut ──

    @Test fun homeTopSlot_heroPresent_heroMemeSiErreur() {
        assertEquals(HomeTopSlot.HERO, homeTopSlot(true, loaded = true, providerKinds = listOf("XTREAM"), supportedKinds = supported, hasFailure = true))
    }

    @Test fun homeTopSlot_roomPasEncoreLu_rien() {
        assertEquals(HomeTopSlot.NONE, homeTopSlot(false, loaded = false, providerKinds = emptyList(), supportedKinds = supported, hasFailure = false))
    }

    @Test fun homeTopSlot_aucuneSource_carteAucuneSource() {
        assertEquals(HomeTopSlot.NO_SOURCE, homeTopSlot(false, true, emptyList(), supported, hasFailure = true))
    }

    @Test fun homeTopSlot_sourcesNonPrisesEnCharge_carteDediee() {
        assertEquals(HomeTopSlot.UNSUPPORTED, homeTopSlot(false, true, listOf("LEGACY"), supported, hasFailure = false))
    }

    @Test fun homeTopSlot_sourceEnErreur_carteErreur() {
        assertEquals(HomeTopSlot.FAILURE, homeTopSlot(false, true, listOf("LEGACY", "M3U"), supported, hasFailure = true))
    }

    @Test fun homeTopSlot_sourceSaineSansHero_chargement() {
        assertEquals(HomeTopSlot.LOADING, homeTopSlot(false, true, listOf("XTREAM"), supported, hasFailure = false))
    }

    // ── Méta du hero et cartes « Reprendre » ──

    @Test fun heroMeta_toutConnu_separateurPoint() {
        assertEquals("2024 · Drame · ★ 7.8", heroMeta(2024, "Drame", 7.84))
    }

    @Test fun heroMeta_elementsAbsents_masques() {
        assertEquals("★ 6.0", heroMeta(null, " ", 6.0))
        assertEquals("", heroMeta(null, null, null))
    }

    @Test fun resumeProgress_dureeInconnue_zero_etBorneA1() {
        assertEquals(0f, resumeProgress(5_000, 0), 0f)
        assertEquals(0.5f, resumeProgress(30_000, 60_000), 1e-6f)
        assertEquals(1f, resumeProgress(90_000, 60_000), 0f)
    }

    @Test fun minutesLeft_dureeInconnue_null_jamaisNegatif() {
        assertNull(minutesLeft(10, 0))
        assertEquals(0, minutesLeft(90_000, 60_000))
        assertEquals(95, minutesLeft(0, 95 * 60_000L + 30_000))
    }

    @Test fun resumeCardOf_titreNettoye_cleAvecPrefixe() {
        val c = resumeCardOf(hist("42", "EPISODE", pos = 60_000, dur = 120_000, title = "FR| Titre"), "rc-", { it.removePrefix("FR| ") })
        assertEquals("rc-EPISODE-42", c.key)
        assertEquals("Titre", c.title)
        assertEquals(0.5f, c.progress, 1e-6f)
        assertEquals(1, c.minutesLeft)
    }

    @Test fun traktPosterOf_filmEtSerieMemeId_clesDistinctes_typeTmdb() {
        val m = traktPosterOf(TraktCard(false, 7, "A", null, 2020, 7.0))
        val s = traktPosterOf(TraktCard(true, 7, "B", null, null, null))
        assertNotEquals(m.key, s.key)
        assertEquals(TmdbKind.MOVIE, m.kind)
        assertEquals(TmdbKind.TV, s.kind)
        assertTrue(s.isShow)
    }

    @Test fun distinctKeys_doublons_premierGarde() {
        val l = listOf(traktPosterOf(TraktCard(false, 1, "A", null, null, null)), traktPosterOf(TraktCard(false, 1, "A bis", null, null, null)))
        val d = l.distinctKeys { it.key }
        assertEquals(1, d.size)
        assertEquals("A", d.first().item.title)
    }

    // ── Pivot vertical ──

    @Test fun pivotScrollDistance_elementBas_amenéA30Pourcent() {
        // Élément de 100 à y=800 dans 1000 : son haut doit finir à 300 → défilement de 500.
        assertEquals(500f, pivotScrollDistance(800f, 100f, 1000f), 0.001f)
    }

    @Test fun pivotScrollDistance_elementDejaAuPivot_aucunDefilement() {
        assertEquals(0f, pivotScrollDistance(300f, 100f, 1000f), 0.001f)
    }

    @Test fun pivotScrollDistance_elementHaut_defilementNegatif() {
        assertEquals(-300f, pivotScrollDistance(0f, 100f, 1000f), 0.001f)
    }

    @Test fun pivotScrollDistance_tropGrandSousLePivot_colleEnBas() {
        // 800 de haut : sous le pivot il ne reste que 700 → l'élément est collé en bas (haut à 200).
        assertEquals(400f, pivotScrollDistance(600f, 800f, 1000f), 0.001f)
    }

    @Test fun pivotScrollDistance_plusGrandQueLEcran_hautAuPivot() {
        assertEquals(300f, pivotScrollDistance(600f, 1200f, 1000f), 0.001f)
    }
}
