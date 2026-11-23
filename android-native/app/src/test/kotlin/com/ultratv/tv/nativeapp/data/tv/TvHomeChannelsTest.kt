package com.ultratv.tv.nativeapp.data.tv

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.ui.home.HomeGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chaînes de l'écran d'accueil Android TV activables : défaut inchangé, publication filtrée (contrôle parental, profil). */
class TvHomeChannelsTest {

    // ---- Choix de l'utilisateur ------------------------------------------------------------

    @Test fun defaut_inchange_les_trois_chaines_historiques_seules() {
        val d = TvChannelsChoice.parse(null)
        assertEquals(setOf(TvHomeChannel.RECENT, TvHomeChannel.FAVORITES, TvHomeChannel.NEWS), d.enabled)
        assertTrue(d.categories.isEmpty())
        for (c in listOf(TvHomeChannel.NEW_MOVIES, TvHomeChannel.NEW_SERIES, TvHomeChannel.TOP10, TvHomeChannel.TOP_RATED_MOVIES, TvHomeChannel.MY_LIST, TvHomeChannel.SPORT_LIVE))
            assertFalse(c.name, d.isEffective(c, sportSection = true))
        assertEquals(3, d.count(sportSection = true))
    }

    @Test fun tout_desactive_reste_tout_desactive() {
        val none = TvChannelsChoice.parse("")
        assertTrue(none.enabled.isEmpty())
        assertEquals("", TvChannelsChoice(emptySet()).encode())
    }

    @Test fun aller_retour_avec_categories_encodees() {
        val c = TvChannelsChoice.DEFAULT.toggled(TvHomeChannel.TOP10).toggled(TvHomeChannel.NEWS)
            .withCategory(TvCategoryRef(3, "Sport, FR/HD")).withCategory(TvCategoryRef(3, "12"))
        val back = TvChannelsChoice.parse(c.encode())
        assertEquals(c, back)
        assertFalse(back.isOn(TvHomeChannel.NEWS))
        assertTrue(back.isOn(TvHomeChannel.TOP10))
        assertEquals(listOf("Sport, FR/HD", "12"), back.categories.map { it.categoryId })
    }

    @Test fun au_plus_trois_categories_sans_doublon_et_de_la_source_active() {
        var c = TvChannelsChoice(emptySet())
        for (i in 1..5) c = c.withCategory(TvCategoryRef(1, "c$i"))
        c = c.withCategory(TvCategoryRef(1, "c1"))
        assertEquals(listOf("c1", "c2", "c3"), c.categories.map { it.categoryId })
        assertTrue(c.categoriesFor(2).isEmpty())
        assertEquals(3, c.categoriesFor(1).size)
        assertEquals(listOf("c2", "c3"), c.withoutCategory(TvCategoryRef(1, "c1")).categories.map { it.categoryId })
    }

    @Test fun sport_en_direct_seulement_avec_la_rubrique_sport() {
        val c = TvChannelsChoice(setOf(TvHomeChannel.SPORT_LIVE))
        assertFalse(c.isEffective(TvHomeChannel.SPORT_LIVE, sportSection = false))
        assertTrue(c.isEffective(TvHomeChannel.SPORT_LIVE, sportSection = true))
    }

    @Test fun chaines_nouvellement_activees_pour_l_invite_systeme() {
        val before = TvChannelsChoice.DEFAULT
        val after = before.toggled(TvHomeChannel.MY_LIST).toggled(TvHomeChannel.RECENT).withCategory(TvCategoryRef(1, "a"))
        assertEquals(setOf("my_list", TvCategoryRef(1, "a").key), TvChannelsChoice.newlyEnabled(before, after))
        assertTrue(TvChannelsChoice.newlyEnabled(after, before).contains("recent"))
    }

    @Test fun pas_de_travail_lourd_juste_apres_le_demarrage() {
        assertEquals(120_000L, TvChannelPlan.bootWaitMs(0))
        assertEquals(60_000L, TvChannelPlan.bootWaitMs(60_000))
        assertEquals(0L, TvChannelPlan.bootWaitMs(3_600_000))
    }

    // ---- Publication filtrée -----------------------------------------------------------------

    private fun cat(kind: String, rid: String, locked: Boolean = false, enabled: Boolean = true, name: String = "Cat $rid") =
        CategoryEntity(providerId = 1, kind = kind, remoteId = rid, name = name, locked = locked, enabled = enabled)

    private val cats = listOf(
        cat("MOVIE", "ok"), cat("MOVIE", "verrou", locked = true), cat("MOVIE", "off", enabled = false),
        cat("SERIES", "ok"), cat("SERIES", "verrou", locked = true),
        cat("LIVE", "ok"), cat("LIVE", "verrou", locked = true),
    )

    private fun vod(rid: String, catId: String?, title: String = "Titre $rid", kind: NewsChannelPlan.Kind = NewsChannelPlan.Kind.MOVIE) =
        TvChannelPlan.Vod(kind, 1, rid, title, "http://p/$rid.jpg", null, catId)

    private fun ch(rid: String, catId: String? = "ok", name: String = "Chaîne $rid") =
        ChannelEntity(providerId = 1, remoteId = rid, name = name, logo = null, categoryId = catId, streamUrl = "u$rid")

    @Test fun aucun_titre_verrouille_desactive_masque_ou_adulte_publie() {
        val g = HomeGuard.of(hidden = setOf("MOVIE:1:masque"), categories = cats, lockedChannels = emptySet())
        val out = TvChannelPlan.selectVod(listOf(
            vod("1", "ok"), vod("2", "verrou"), vod("3", "off"), vod("4", "masque"), vod("5", "ok", title = "XXX"), vod("6", null),
        ), g)
        assertEquals(listOf("1", "6"), out.map { it.remoteId })
    }

    @Test fun au_plus_20_titres_un_par_titre_avec_image() {
        val many = (1..40).map { vod("$it", "ok") } + vod("41", "ok", title = "Titre 1") + vod("42", "ok").copy(poster = null)
        val out = TvChannelPlan.selectVod(many, HomeGuard.NONE)
        assertEquals(20, out.size)
        assertFalse(out.any { it.remoteId == "41" || it.remoteId == "42" })
    }

    @Test fun top10_tendances_sinon_mieux_notes_jamais_verrouille() {
        val g = HomeGuard.of(emptySet(), cats, emptySet())
        val trending = listOf(vod("t1", "ok"), vod("t2", "verrou"), vod("t3", "ok"))
        val top = (1..15).map { vod("m$it", if (it % 2 == 0) "verrou" else "ok") }
        val out = TvChannelPlan.top10(trending, top, g)
        assertTrue(out.size <= TvChannelPlan.TOP10_SIZE)
        assertTrue(out.all { it.categoryId == "ok" })
        assertEquals("m1", out.first().remoteId)
        val trend = (1..6).map { vod("t$it", "ok") }
        assertEquals(trend.map { it.remoteId }, TvChannelPlan.top10(trend, top, g).map { it.remoteId })
    }

    @Test fun chaines_du_direct_ni_verrouillees_ni_de_categorie_verrouillee() {
        val g = HomeGuard.of(emptySet(), cats, lockedChannels = setOf("1:b"))
        val out = TvChannelPlan.selectChannels(listOf(ch("a"), ch("b"), ch("c", "verrou"), ch("d", null), ch("e", name = "##### FR #####")), g)
        assertEquals(listOf("a", "d"), out.map { it.remoteId })
    }

    @Test fun chaines_recentes_filtre_de_l_historique_sans_verrouille() {
        val g = HomeGuard.forHistory(cats.filter { it.kind == "LIVE" } + cat("LIVE", "masquee", enabled = false), lockedChannels = setOf("1:b"), kids = false)
        val out = RecentChannelsPlan.select(listOf(ch("a"), ch("b"), ch("c", "verrou"), ch("d", "masquee")), g)
        // Catégorie désactivée : la chaîne regardée reste ; verrouillées : jamais.
        assertEquals(listOf("a", "d"), out.map { it.remoteId })
    }

    @Test fun nouveautes_sans_categorie_verrouillee() {
        val g = HomeGuard.of(emptySet(), cats, emptySet())
        fun item(rid: String, c: String) = NewsChannelPlan.Item(NewsChannelPlan.Kind.MOVIE, 1, rid, "T$rid", "p", 1, categoryId = c)
        assertEquals(listOf("1"), NewsChannelPlan.visible(listOf(item("1", "ok"), item("2", "verrou")), g).map { it.remoteId })
    }

    @Test fun continuer_a_regarder_sans_element_verrouille() {
        val live = HomeGuard.forHistory(cats.filter { it.kind == "LIVE" }, lockedChannels = setOf("1:lc"), kids = false)
        val vodG = HomeGuard.of(emptySet(), cats, setOf("1:lc"))
        fun h(kind: String, rid: String, parent: String? = null) = WatchHistoryEntity(providerId = 1, kind = kind, remoteId = rid, title = "T$rid", poster = "p", streamUrl = "u", parentRemoteId = parent)
        assertTrue(HistoryGuardPlan.allows(h("MOVIE", "m"), "ok", live, vodG))
        assertFalse(HistoryGuardPlan.allows(h("MOVIE", "m"), "verrou", live, vodG))
        assertFalse(HistoryGuardPlan.allows(h("EPISODE", "e", parent = "s"), "verrou", live, vodG))
        assertEquals("s", HistoryGuardPlan.titleRemoteId(h("EPISODE", "e", parent = "s")))
        assertFalse(HistoryGuardPlan.allows(h("LIVE", "lc"), "ok", live, vodG))
        assertFalse(HistoryGuardPlan.allows(h("LIVE", "x"), "verrou", live, vodG))
        assertTrue(HistoryGuardPlan.allows(h("LIVE", "x"), "ok", live, vodG))
    }

    @Test fun programmes_identifiants_stables_et_liens_existants() {
        val v = TvChannelPlan.vodProgram("TOP10", vod("9", "ok"))
        assertEquals("TOP10:MOVIE:1:9", v.internalId)
        assertEquals("ultratv://moviedetail/1/9", v.link)
        val s = TvChannelPlan.vodProgram("MYLIST", vod("8", "ok", kind = NewsChannelPlan.Kind.SERIES))
        assertEquals("ultratv://series/1/8", s.link)
        val c = TvChannelPlan.channelProgram("CAT", ch("7"), "banner")
        assertEquals("ultratv://live/1/7", c.link)
        assertEquals("banner", c.image)
        assertTrue(c.live)
        // Identifiants des chaînes historiques inchangés.
        assertEquals("RECENT:LIVE:1:7", RecentChannelsPlan.internalId(ch("7")))
    }

    @Test fun difference_sans_tout_supprimer() {
        val a = TvChannelPlan.channelProgram("CAT", ch("a"), "b")
        val b = TvChannelPlan.channelProgram("CAT", ch("b"), "b")
        val d = TvChannelPlan.diff(mapOf(a.internalId to 10L, "CAT:LIVE:1:z" to 11L), listOf(a, b))
        assertEquals(listOf(b), d.insert)
        assertEquals(listOf(10L to a), d.update)
        assertEquals(listOf(11L), d.deleteRowIds)
        assertEquals(setOf("cat:1:x"), TvChannelPlan.stale(setOf("cat:1:x", "cat:1:y"), setOf("cat:1:y")))
    }
}
