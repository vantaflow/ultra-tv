package com.ultratv.tv.nativeapp.ui.simple

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import com.ultratv.tv.nativeapp.data.profile.ProfilePrefs
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.nav.StartScreen
import com.ultratv.tv.nativeapp.nav.StartupTarget
import com.ultratv.tv.nativeapp.ui.components.isSelected
import com.ultratv.tv.nativeapp.ui.mobile.MobileTab
import com.ultratv.tv.nativeapp.ui.mobile.railRouteFor
import com.ultratv.tv.nativeapp.ui.mobile.tabForRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleHomeLogicTest {
    private fun hist(kind: String, rid: String, url: String = "http://x/$rid", title: String = "T$rid", provider: Long = 1) =
        WatchHistoryEntity(providerId = provider, kind = kind, remoteId = rid, title = title, poster = null, streamUrl = url)

    private fun chan(id: Long, name: String = "C$id", sep: Boolean = false) =
        ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = name, logo = null, categoryId = null, streamUrl = "u$id", isSeparator = sep)

    // ── Choix de l'écran d'accueil selon le mode / le profil ──

    @Test fun accueil_parDefaut_standard() {
        assertEquals(HomeKind.STANDARD, homeKindFor(UserPrefs()))
    }

    @Test fun accueil_profilEnModeSimple_tuiles() {
        val eff = ProfilePrefs(simpleMode = true).applyTo(UserPrefs())
        assertEquals(HomeKind.SIMPLE, homeKindFor(eff))
    }

    @Test fun accueil_profilSansSurcharge_heriteDuGlobal() {
        assertEquals(HomeKind.SIMPLE, homeKindFor(ProfilePrefs().applyTo(UserPrefs(simpleMode = true))))
        assertEquals(HomeKind.STANDARD, homeKindFor(ProfilePrefs().applyTo(UserPrefs(simpleMode = false))))
    }

    @Test fun accueil_surchargeDuProfil_l_emporteSurLeGlobal() {
        assertEquals(HomeKind.STANDARD, homeKindFor(ProfilePrefs(simpleMode = false).applyTo(UserPrefs(simpleMode = true))))
        assertEquals(HomeKind.SIMPLE, homeKindFor(ProfilePrefs(simpleMode = true).applyTo(UserPrefs(simpleMode = false))))
    }

    @Test fun accueil_deuxProfils_chacunSonMode() {
        val base = UserPrefs()
        val grandMere = ProfilePrefs.fromMap(mapOf(ProfilePrefs.SIMPLE_MODE to "true"))
        val papa = ProfilePrefs.fromMap(emptyMap())
        assertEquals(HomeKind.SIMPLE, homeKindFor(grandMere.applyTo(base)))
        assertEquals(HomeKind.STANDARD, homeKindFor(papa.applyTo(base)))
    }

    @Test fun profilPrefs_fromMap_valeurInvalide_ignoree() {
        assertNull(ProfilePrefs.fromMap(mapOf(ProfilePrefs.SIMPLE_MODE to "peut-être")).simpleMode)
        assertEquals(false, ProfilePrefs.fromMap(mapOf(ProfilePrefs.SIMPLE_MODE to "false")).simpleMode)
    }

    // ── Contenu des tuiles ──

    @Test fun tuiles_sixToujoursDansLOrdre() {
        assertEquals(
            listOf(SimpleTile.LIVE, SimpleTile.RESUME, SimpleTile.MOVIES, SimpleTile.SERIES, SimpleTile.FAVORITES, SimpleTile.SEARCH),
            SimpleHome.tiles(),
        )
    }

    @Test fun tuiles_destinations() {
        assertEquals(Routes.SIMPLE_CHANNELS, SimpleTile.LIVE.route)
        assertNull(SimpleTile.RESUME.route)
        assertEquals(Routes.MOVIES, SimpleTile.MOVIES.route)
        assertEquals(Routes.SERIES, SimpleTile.SERIES.route)
        assertEquals(Routes.FAVORITES, SimpleTile.FAVORITES.route)
        assertEquals(Routes.SEARCH, SimpleTile.SEARCH.route)
    }

    @Test fun grille_troisColonnes_deuxLignesDeTrois() {
        val rows = SimpleHome.rows(SimpleHome.tiles(), 3)
        assertEquals(2, rows.size)
        assertEquals(listOf(3, 3), rows.map { it.size })
        assertEquals(SimpleTile.LIVE, rows[0][0])
    }

    @Test fun grille_deuxColonnes_troisLignes() {
        assertEquals(listOf(2, 2, 2), SimpleHome.rows(SimpleHome.tiles(), 2).map { it.size })
    }

    @Test fun colonnes_tvEtTelephone() {
        assertEquals(3, SimpleHome.columnsFor(touch = false, widthDp = 960f))
        assertEquals(3, SimpleHome.columnsFor(touch = false, widthDp = 360f))
        assertEquals(2, SimpleHome.columnsFor(touch = true, widthDp = 400f))
        assertEquals(3, SimpleHome.columnsFor(touch = true, widthDp = 900f))
    }

    // ── Reprise ──

    @Test fun reprise_prendLePremierFilmOuEpisode() {
        val l = listOf(hist("MOVIE", "m1"), hist("EPISODE", "e1"))
        assertEquals("m1", SimpleHome.resumeItem(l)?.remoteId)
    }

    @Test fun reprise_ignoreLesChaines() {
        val l = listOf(hist("LIVE", "c1"), hist("EPISODE", "e1"))
        assertEquals("e1", SimpleHome.resumeItem(l)?.remoteId)
    }

    @Test fun reprise_ignoreSansAdresse() {
        val l = listOf(hist("MOVIE", "m1", url = ""), hist("MOVIE", "m2", url = "  "), hist("MOVIE", "m3"))
        assertEquals("m3", SimpleHome.resumeItem(l)?.remoteId)
    }

    @Test fun reprise_aucune_null() {
        assertNull(SimpleHome.resumeItem(emptyList()))
        assertNull(SimpleHome.resumeItem(listOf(hist("LIVE", "c1"))))
    }

    @Test fun reprise_action_lectureOuFilms() {
        val h = hist("MOVIE", "m1")
        assertEquals(SimpleHome.ResumeAction.Play(h), SimpleHome.resumeAction(h))
        assertEquals(SimpleHome.ResumeAction.Browse(Routes.MOVIES), SimpleHome.resumeAction(null))
    }

    // ── Direct ──

    @Test fun direct_sansHistorique_liste() {
        assertEquals(SimpleHome.LiveAction.ShowList, SimpleHome.liveAction(null, emptySet()))
    }

    @Test fun direct_derniereChaine_lectureDirecte() {
        val h = hist("LIVE", "c9")
        assertEquals(SimpleHome.LiveAction.PlayLast(h, needsPin = false), SimpleHome.liveAction(h, setOf("1:autre")))
    }

    @Test fun direct_chaineVerrouillee_codeParentalDAbord() {
        val h = hist("LIVE", "c9", provider = 1)
        assertEquals(SimpleHome.LiveAction.PlayLast(h, needsPin = true), SimpleHome.liveAction(h, setOf("1:c9")))
    }

    @Test fun derniereChaine_ignoreSansAdresseEtNonDirect() {
        val l = listOf(hist("MOVIE", "m1"), hist("LIVE", "c1", url = ""), hist("LIVE", "c2"))
        assertEquals("c2", SimpleHome.lastLive(l)?.remoteId)
        assertNull(SimpleHome.lastLive(emptyList()))
    }

    // ── Liste de chaînes : favoris d'abord ──

    @Test fun chaines_favorisEnPremier_sansDoublon() {
        val favs = listOf(chan(5), chan(2))
        val others = listOf(chan(1), chan(2), chan(3), chan(5))
        assertEquals(listOf(5L, 2L, 1L, 3L), SimpleHome.orderChannels(favs, others).map { it.id })
    }

    @Test fun chaines_sansSeparateurs() {
        val others = listOf(chan(1), chan(2, "##### SPORT #####", sep = true), chan(3))
        assertEquals(listOf(1L, 3L), SimpleHome.orderChannels(emptyList(), others).map { it.id })
    }

    @Test fun chaines_plafonnees() {
        val others = (1L..300L).map { chan(it) }
        assertEquals(SimpleHome.MAX_CHANNELS, SimpleHome.orderChannels(emptyList(), others).size)
        assertEquals(listOf(1L, 2L), SimpleHome.orderChannels(emptyList(), others, max = 2).map { it.id })
    }

    @Test fun chaines_estFavori() {
        assertTrue(SimpleHome.isFavorite(chan(7), setOf(7L)))
        assertFalse(SimpleHome.isFavorite(chan(8), setOf(7L)))
    }

    // ── Ouverture, rail, sortie ──

    @Test fun ouverture_modeSimple_rubriqueRemplaceeParLesTuiles() {
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(true, StartScreen.MOVIES))
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(true, StartScreen.GUIDE))
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(true, StartScreen.HOME))
    }

    @Test fun ouverture_modeSimple_derniereChaineRespectee() {
        assertEquals(StartupTarget.LastChannel, SimpleHome.startupTarget(true, StartScreen.LAST_CHANNEL))
    }

    @Test fun ouverture_modeNormal_inchangee() {
        assertEquals(StartupTarget.Route(Routes.MOVIES), SimpleHome.startupTarget(false, StartScreen.MOVIES))
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(false, StartScreen.HOME))
    }

    @Test fun ouverture_dernierEcran() {
        assertEquals(StartupTarget.Route(Routes.GUIDE), SimpleHome.startupTarget(false, StartScreen.LAST_SCREEN, Routes.GUIDE))
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(false, StartScreen.LAST_SCREEN, Routes.HOME))
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(false, StartScreen.LAST_SCREEN, null))
        // Fiche, lecteur, réglages : jamais rouverts au lancement.
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(false, StartScreen.LAST_SCREEN, Routes.SETTINGS))
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(false, StartScreen.LAST_SCREEN, Routes.PLAYER))
        // Mode simple : les tuiles restent l'écran d'ouverture.
        assertEquals(StartupTarget.Stay, SimpleHome.startupTarget(true, StartScreen.LAST_SCREEN, Routes.GUIDE))
        assertEquals(StartScreen.LAST_SCREEN, StartScreen.parse("last_screen", false))
    }

    @Test fun rail_masqueSeulementSurLesTuiles() {
        assertTrue(SimpleHome.railHidden(true, Routes.HOME))
        assertFalse(SimpleHome.railHidden(true, Routes.MOVIES))
        assertFalse(SimpleHome.railHidden(true, Routes.SIMPLE_CHANNELS))
        assertFalse(SimpleHome.railHidden(false, Routes.HOME))
        assertFalse(SimpleHome.railHidden(true, null))
    }

    @Test fun sortie_codeParentalSeulementSiDefini() {
        assertTrue(SimpleHome.exitNeedsPin(true))
        assertFalse(SimpleHome.exitNeedsPin(false))
    }

    // ── Navigation : la liste des chaînes se range sous « Direct » ──

    @Test fun listeSimple_rangeSousDirect() {
        assertEquals(MobileTab.LIVE, tabForRoute(Routes.SIMPLE_CHANNELS))
        assertEquals(Routes.LIVE, railRouteFor(Routes.SIMPLE_CHANNELS))
        assertTrue(isSelected(Routes.SIMPLE_CHANNELS, Routes.LIVE))
        assertFalse(isSelected(Routes.SIMPLE_CHANNELS, Routes.MOVIES))
    }

    @Test fun nouvelAccueil_cinqTuilesSansReprise() {
        assertEquals(listOf(SimpleTile.LIVE, SimpleTile.MOVIES, SimpleTile.SERIES, SimpleTile.FAVORITES, SimpleTile.SEARCH), SimpleHome.mainTiles())
        assertEquals(5, SimpleHome.mainColumnsFor(touch = false, widthDp = 960f))
        assertEquals(2, SimpleHome.mainColumnsFor(touch = true, widthDp = 400f))
        assertEquals(3, SimpleHome.mainColumnsFor(touch = true, widthDp = 800f))
    }

    @Test fun reprise_progressionEtMinutesRestantes() {
        val h = WatchHistoryEntity(providerId = 1, kind = "MOVIE", remoteId = "m", title = "t", poster = null, streamUrl = "u", positionMs = 30 * 60_000L, durationMs = 90 * 60_000L)
        assertEquals(1f / 3f, SimpleHome.progressOf(h)!!, 0.001f)
        assertEquals(60, SimpleHome.minutesLeft(h))
        assertEquals(null, SimpleHome.progressOf(h.copy(durationMs = 0)))
        assertEquals(1, SimpleHome.minutesLeft(h.copy(positionMs = h.durationMs)))
    }
}
