package com.ultratv.tv.nativeapp.data.tv

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextPlanTest {
    private fun h(kind: String = "MOVIE", rid: String = "1", pos: Long = 120_000, dur: Long = 3_600_000, at: Long = 1, parent: String? = null, poster: String? = "p") =
        WatchHistoryEntity(providerId = 1, kind = kind, remoteId = rid, title = "t$rid", poster = poster, streamUrl = "u", positionMs = pos, durationMs = dur, watchedAt = at, parentRemoteId = parent)

    @Test fun inProgress_commenceEtPasTermine_vrai() = assertTrue(WatchNextPlan.inProgress(h()))
    @Test fun inProgress_moinsDUneMinute_faux() = assertFalse(WatchNextPlan.inProgress(h(pos = 30_000)))
    @Test fun inProgress_presqueFini_faux() = assertFalse(WatchNextPlan.inProgress(h(pos = 3_580_000)))
    @Test fun inProgress_direct_faux() = assertFalse(WatchNextPlan.inProgress(h(kind = "LIVE")))
    @Test fun inProgress_dureeInconnue_vrai() = assertTrue(WatchNextPlan.inProgress(h(dur = 0)))

    @Test fun selectLive_chainesRecentes_sansDoublon_etAuPlusCinq() {
        val now = 10L * 24 * 3_600_000
        val list = (1..7).map { h("LIVE", "c$it", pos = 0, dur = 0, at = now - it * 60_000L) } + h("LIVE", "c1", pos = 0, dur = 0, at = now - 1_000) +
            h("LIVE", "old", pos = 0, dur = 0, at = now - 8L * 24 * 3_600_000) + h("MOVIE", "m", at = now)
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5"), WatchNextPlan.selectLive(list, now).map { it.remoteId })
    }
    @Test fun waitMs_jamaisSynchronise_publieToutDeSuite() = assertEquals(0L, WatchNextPlan.waitMs(0L, 1_000_000L))
    @Test fun waitMs_derniereEcritureAncienne_publieToutDeSuite() = assertEquals(0L, WatchNextPlan.waitMs(1_000L, 1_000L + 45_000L))
    @Test fun waitMs_ecritureRecente_attendLeReste() = assertEquals(35_000L, WatchNextPlan.waitMs(1_000L, 11_000L))
    @Test fun waitMs_horlogeReculee_neDepasseJamaisLIntervalle() = assertEquals(45_000L, WatchNextPlan.waitMs(100_000L, 1_000L))

    @Test fun deepLink_direct() = assertTrue(WatchNextPlan.deepLink(h("LIVE", "c9")).contains("/live/1/c9"))

    @Test fun select_unSeulEpisodeParSerie_leDernierRegarde_etRecentsDAbord() {
        val list = listOf(
            h("EPISODE", "e1", parent = "S", at = 10), h("EPISODE", "e2", parent = "S", at = 20),
            h("MOVIE", "m1", at = 15), h("MOVIE", "m2", at = 5, poster = null),
        )
        assertEquals(listOf("e2", "m1"), WatchNextPlan.select(list).map { it.remoteId })
    }

    @Test fun select_auPlusDix() {
        assertEquals(WatchNextPlan.MAX, WatchNextPlan.select((1..30).map { h(rid = "$it", at = it.toLong()) }).size)
    }

    @Test fun deepLink_filmEtEpisode() {
        assertTrue(WatchNextPlan.deepLink(h()).startsWith("ultratv://movie/1/"))
        assertTrue(WatchNextPlan.deepLink(h("EPISODE")).startsWith("ultratv://episode/1/"))
    }
}

class FavoritesChannelPlanTest {
    private fun c(rid: String, name: String = "N$rid", logo: String? = null) = ChannelEntity(providerId = 1, remoteId = rid, name = name, logo = logo, categoryId = null, streamUrl = "u")

    @Test fun select_ecarteLesDoublonsEtLesSansNom_auPlusVingt() {
        assertEquals(listOf("1", "2"), FavoritesChannelPlan.select(listOf(c("1"), c("1"), c("2"), c("3", name = " "))).map { it.remoteId })
        assertEquals(FavoritesChannelPlan.MAX, FavoritesChannelPlan.select((1..40).map { c("$it") }).size)
    }

    @Test fun signature_change_quandLaSelectionChange() {
        assertFalse(FavoritesChannelPlan.signature(listOf(c("1"))) == FavoritesChannelPlan.signature(listOf(c("1"), c("2"))))
        assertEquals(FavoritesChannelPlan.signature(listOf(c("1"))), FavoritesChannelPlan.signature(listOf(c("1"))))
    }

    @Test fun deepLink_versLaChaine() = assertEquals("ultratv://live/1/7", FavoritesChannelPlan.deepLink(c("7")))
}
