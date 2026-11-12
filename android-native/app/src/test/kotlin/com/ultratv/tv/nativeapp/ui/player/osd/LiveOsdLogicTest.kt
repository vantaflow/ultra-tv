package com.ultratv.tv.nativeapp.ui.player.osd

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOsdLogicTest {
    private fun decide(key: OsdKey, open: Boolean = false, hidden: Boolean = false, ts: Boolean = false) =
        LiveOsdLogic.decide(key, osdOpen = open, controlsHidden = hidden, tsActive = ts)

    // ── OSD fermé ──

    @Test fun ferme_OK_ouvreLaListe_Bas_ouvreLOsd() {
        assertEquals(OsdDecision.OpenChannels, decide(OsdKey.OK))   // OK : liste des chaînes
        assertEquals(OsdDecision.OpenOsd, decide(OsdKey.DOWN))
        assertEquals(OsdDecision.OpenChannels, decide(OsdKey.OK, hidden = true))
        assertEquals(OsdDecision.OpenOsd, decide(OsdKey.DOWN, hidden = true))
    }

    @Test fun ferme_HautEtBas_ouvrentLOsd() {
        for (h in listOf(false, true)) {
            assertEquals(OsdDecision.OpenOsd, decide(OsdKey.UP, hidden = h))
            assertEquals(OsdDecision.OpenOsd, decide(OsdKey.DOWN, hidden = h))
        }
    }

    @Test fun ferme_MenuInfo_engagentLOsd() {
        for (k in listOf(OsdKey.MENU, OsdKey.INFO)) assertEquals("$k", OsdDecision.OpenOsd, decide(k))
    }

    @Test fun ferme_GaucheDroite_sansDiffere_chainePrecedenteEtSuivante() {
        for (h in listOf(false, true)) {
            assertEquals(OsdDecision.Zap(forward = false, closeOsd = false), decide(OsdKey.LEFT, hidden = h))
            assertEquals(OsdDecision.Zap(forward = true, closeOsd = false), decide(OsdKey.RIGHT, hidden = h))
        }
    }

    @Test fun ouvert_Gauche_resteAuFocus() {
        assertEquals(OsdDecision.Pass, decide(OsdKey.LEFT, open = true))
    }

    @Test fun ferme_differe_commandesMasquees_GaucheDroiteSautentDe30s() {
        assertEquals(OsdDecision.TimeshiftJump(-30), decide(OsdKey.LEFT, hidden = true, ts = true))
        assertEquals(OsdDecision.TimeshiftJump(30), decide(OsdKey.RIGHT, hidden = true, ts = true))
        // Bandeau visible (non masqué) : Gauche/Droite engagent l'OSD en différé.
        assertEquals(OsdDecision.OpenOsd, decide(OsdKey.LEFT, hidden = false, ts = true))
        assertEquals(OsdDecision.OpenOsd, decide(OsdKey.RIGHT, hidden = false, ts = true))
        // Menu n'est jamais un saut.
        assertEquals(OsdDecision.OpenOsd, decide(OsdKey.MENU, hidden = true, ts = true))
    }

    @Test fun ferme_autresTouches_absorbees() {
        assertEquals(OsdDecision.Consume, decide(OsdKey.OTHER))
    }

    // ── Touches valables partout ──

    @Test fun guideListeLesChaines_toujours() {
        assertEquals(OsdDecision.OpenChannels, decide(OsdKey.GUIDE))
        assertEquals(OsdDecision.OpenChannels, decide(OsdKey.GUIDE, open = true))
    }

    @Test fun chainePrecedente_toujours() {
        assertEquals(OsdDecision.RecallPrevious, decide(OsdKey.LAST_CHANNEL))
        assertEquals(OsdDecision.RecallPrevious, decide(OsdKey.LAST_CHANNEL, open = true))
    }

    @Test fun chaineMoinsPlus_zappentEtFermentLOsd() {
        for (open in listOf(false, true)) {
            assertEquals(OsdDecision.Zap(forward = false, closeOsd = true), decide(OsdKey.CHANNEL_UP, open = open))
            assertEquals(OsdDecision.Zap(forward = true, closeOsd = true), decide(OsdKey.CHANNEL_DOWN, open = open))
        }
    }

    // ── OSD ouvert : le focus de Compose circule entre les étages ──

    @Test fun ouvert_lesFlechesEtOK_sontLaisseesAuFocus() {
        for (k in listOf(OsdKey.OK, OsdKey.UP, OsdKey.DOWN, OsdKey.LEFT, OsdKey.RIGHT, OsdKey.MENU, OsdKey.INFO, OsdKey.OTHER))
            assertEquals("$k", OsdDecision.Pass, decide(k, open = true))
    }

    // ── Panneau d'erreur prioritaire (#85) ──

    @Test fun erreur_toutesLesTouches_vontAuPanneauDErreur() {
        for (k in OsdKey.entries) for (open in listOf(false, true)) for (hidden in listOf(false, true))
            assertEquals("$k open=$open hidden=$hidden", OsdDecision.Pass,
                LiveOsdLogic.decide(k, osdOpen = open, controlsHidden = hidden, tsActive = false, errorShown = true))
    }

    @Test fun erreur_leFocusNeRevientPasALaRacine() {
        assertFalse(LiveOsdLogic.shouldRestoreRootFocus(isLive = true, osdOpen = false, errorShown = true))
        assertTrue(LiveOsdLogic.shouldRestoreRootFocus(isLive = true, osdOpen = false, errorShown = false))
        assertFalse(LiveOsdLogic.shouldRestoreRootFocus(isLive = true, osdOpen = true, errorShown = false))
        assertFalse(LiveOsdLogic.shouldRestoreRootFocus(isLive = false, osdOpen = false, errorShown = false))
    }

    // ── Retour ──

    @Test fun retour_recents_ramenelaBarre() {
        assertEquals(LiveOsdLogic.BackEffect.FOCUS_CONTROLS, LiveOsdLogic.backEffect(osdOpen = true, stage = OsdStage.RECENTS, playing = true))
    }

    @Test fun retour_barre_fermeLOsd() {
        assertEquals(LiveOsdLogic.BackEffect.CLOSE_OSD, LiveOsdLogic.backEffect(osdOpen = true, stage = OsdStage.CONTROLS, playing = true))
    }

    @Test fun retour_osdFerme_laisseLeLecteurQuitter() {
        // Issue #30 : Retour doit finir par quitter ; fermé, l'OSD n'intercepte rien.
        assertEquals(LiveOsdLogic.BackEffect.NOT_OSD, LiveOsdLogic.backEffect(osdOpen = false, stage = OsdStage.CONTROLS, playing = true))
        assertEquals(LiveOsdLogic.BackEffect.NOT_OSD, LiveOsdLogic.backEffect(osdOpen = false, stage = OsdStage.RECENTS, playing = true))
    }

    @Test fun retour_sansImage_quitteDirectement() {
        assertEquals(LiveOsdLogic.BackEffect.NOT_OSD, LiveOsdLogic.backEffect(osdOpen = true, stage = OsdStage.CONTROLS, playing = false))
    }

    @Test fun retour_enchaine_recents_barre_fermeture_sortie() {
        var open = true; var stage = OsdStage.RECENTS
        // 1er Retour : récents → barre
        assertEquals(LiveOsdLogic.BackEffect.FOCUS_CONTROLS, LiveOsdLogic.backEffect(open, stage, true)); stage = OsdStage.CONTROLS
        // 2e : l'OSD se ferme
        assertEquals(LiveOsdLogic.BackEffect.CLOSE_OSD, LiveOsdLogic.backEffect(open, stage, true)); open = false
        // 3e : le lecteur reprend la main (quitte)
        assertEquals(LiveOsdLogic.BackEffect.NOT_OSD, LiveOsdLogic.backEffect(open, stage, true))
    }

    // ── Format d'image ──

    @Test fun formatDImage_defileDansLOrdre() {
        var a = AspectMode.FIT
        val seen = mutableListOf(a)
        repeat(5) { a = LiveOsdLogic.nextAspect(a); seen += a }
        assertEquals(listOf(AspectMode.FIT, AspectMode.FILL, AspectMode.ZOOM, AspectMode.R16_9, AspectMode.R4_3, AspectMode.FIT), seen)
    }

    // ── Barre ──

    private val labels = OsdLabels(
        play = "Play", pause = "Pause", audio = "Audio", subs = "Subs", aspect = "Picture", aspectNames = mapOf(AspectMode.FIT to "Fit", AspectMode.R16_9 to "16:9"),
        channels = "Channels", history = "History", addFavorite = "Add", removeFavorite = "Remove", record = "Record", pip = "PiP", sleep = "Sleep", player = "Player",
        myList = "My list", movies = "Movies", series = "Series", recordings = "Recordings", settings = "Settings",
    )
    private fun inputs(
        playing: Boolean = true, ts: Boolean = false, fav: Boolean = false, replay: String? = null, replayActive: Boolean = false,
        sleep: Boolean = false, pip: Boolean = true, shortcuts: Boolean = true, aspect: AspectMode = AspectMode.FIT,
    ) = OsdActionInputs(playing, ts, fav, aspect, replay, replayActive, sleep, pip, shortcuts)

    @Test fun barre_ordreDemande() {
        val ids = OsdActions.build(inputs(replay = "From start"), labels).map { it.id }
        assertEquals(
            listOf(
                OsdActionId.PLAY_PAUSE, OsdActionId.AUDIO, OsdActionId.SUBS, OsdActionId.ASPECT, OsdActionId.CHANNELS, OsdActionId.HISTORY,
                OsdActionId.FAVORITE, OsdActionId.RECORD, OsdActionId.PIP, OsdActionId.REPLAY, OsdActionId.SLEEP, OsdActionId.PLAYER,
                OsdActionId.MY_LIST, OsdActionId.MOVIES, OsdActionId.SERIES, OsdActionId.RECORDINGS, OsdActionId.SETTINGS,
            ),
            ids,
        )
    }

    @Test fun barre_separateurAvantLesRaccourcisSeulement() {
        val a = OsdActions.build(inputs(), labels)
        assertEquals(listOf(OsdActionId.MY_LIST), a.filter { it.separatorBefore }.map { it.id })
    }

    @Test fun barre_pipSeulementSiPris_enCharge() {
        assertTrue(OsdActions.build(inputs(pip = true), labels).any { it.id == OsdActionId.PIP })
        assertFalse(OsdActions.build(inputs(pip = false), labels).any { it.id == OsdActionId.PIP })
    }

    @Test fun barre_replaySeulementSiDisponible() {
        assertFalse(OsdActions.build(inputs(replay = null), labels).any { it.id == OsdActionId.REPLAY })
        val r = OsdActions.build(inputs(replay = "Back to live", replayActive = true), labels).first { it.id == OsdActionId.REPLAY }
        assertEquals("Back to live", r.label); assertTrue(r.active)
    }

    @Test fun barre_differe_ajouteLesSauts_apresLecturePause() {
        val ids = OsdActions.build(inputs(ts = true, replay = "Back to live", replayActive = true), labels).map { it.id }
        assertEquals(listOf(OsdActionId.PLAY_PAUSE, OsdActionId.JUMP_BACK, OsdActionId.JUMP_FORWARD, OsdActionId.AUDIO), ids.take(4))
        assertTrue(OsdActionId.REPLAY in ids)      // « Retour au direct »
        assertFalse(OsdActionId.JUMP_BACK in OsdActions.build(inputs(ts = false), labels).map { it.id })
    }

    @Test fun barre_lecturePause_suitLEtat() {
        assertEquals("Pause", OsdActions.build(inputs(playing = true), labels).first().label)
        assertEquals("Play", OsdActions.build(inputs(playing = false), labels).first().label)
    }

    @Test fun barre_favori_etatVisible() {
        val off = OsdActions.build(inputs(fav = false), labels).first { it.id == OsdActionId.FAVORITE }
        val on = OsdActions.build(inputs(fav = true), labels).first { it.id == OsdActionId.FAVORITE }
        assertEquals("Add", off.label); assertFalse(off.active); assertFalse(off.fillIcon)
        assertEquals("Remove", on.label); assertTrue(on.active); assertTrue(on.fillIcon)
    }

    @Test fun barre_formatDImage_libelleCourantVisible() {
        assertEquals("16:9", OsdActions.build(inputs(aspect = AspectMode.R16_9), labels).first { it.id == OsdActionId.ASPECT }.detail)
    }

    @Test fun barre_sansNavigation_pasDeRaccourcis() {
        val ids = OsdActions.build(inputs(shortcuts = false), labels).map { it.id }
        assertTrue(ids.none { OsdActions.routeOf(it) != null })
    }

    @Test fun barre_veille_etatActif() {
        assertTrue(OsdActions.build(inputs(sleep = true), labels).first { it.id == OsdActionId.SLEEP }.active)
    }

    @Test fun raccourcis_routes() {
        assertEquals("favorites", OsdActions.routeOf(OsdActionId.MY_LIST))
        assertEquals("movies", OsdActions.routeOf(OsdActionId.MOVIES))
        assertEquals("series", OsdActions.routeOf(OsdActionId.SERIES))
        assertEquals("recordings", OsdActions.routeOf(OsdActionId.RECORDINGS))
        assertEquals("settings", OsdActions.routeOf(OsdActionId.SETTINGS))
        assertNull(OsdActions.routeOf(OsdActionId.CHANNELS))
    }

    // ── Étage haut et récents ──

    private fun epg(title: String, start: Long, end: Long, desc: String? = null) = EpgEntity(channelId = 1, title = title, description = desc, startMs = start, endMs = end)

    @Test fun programme_progression_et_reste() {
        val p = OsdModel.programme(
            epg("Journal", 0L, 90 * 60_000L, " Les titres. "), epg("Débat", 90 * 60_000L, 150 * 60_000L), nowMs = 36 * 60_000L,
            thenWord = "Next", tsWindow = null, remaining = { "left $it" }, hm = { "t${it / 60_000}" }, range = { a, b -> "${a / 60_000}-${b / 60_000}" },
        )!!
        assertEquals("Journal", p.title)
        assertEquals("Les titres.", p.description)
        assertEquals("0-90", p.timeRange)
        assertEquals(0.4f, p.progress!!.fraction, 0.001f)
        assertEquals("left 54 min", p.progress!!.remainingLabel)
        assertEquals("Next t90 · Débat", p.thenLine)
    }

    @Test fun programme_sansGuide_null() {
        assertNull(OsdModel.programme(null, null, 0, "Next", null, { it }, { "" }, { _, _ -> "" }))
    }

    @Test fun programme_differe_remplaceLaProgression() {
        val ts = OsdProgress("12:00", "12:30", 0.5f, directMark = "LIVE")
        val p = OsdModel.programme(epg("Journal", 0L, 90 * 60_000L), null, 36 * 60_000L, "Next", ts, { it }, { "" }, { _, _ -> "x" })!!
        assertEquals(ts, p.progress); assertNull(p.timeRange); assertNull(p.thenLine); assertNull(p.description)
    }

    @Test fun recents_sansLaChaineCourante_dixAuPlus() {
        val all = (1..14).map { OsdRecent("p:$it", null, it, "Ch $it", null) }
        val r = OsdModel.recents(all, "p:1")
        assertEquals(10, r.size)
        assertEquals("p:2", r.first().key)
        assertTrue(r.none { it.key == "p:1" })
        assertEquals(3, OsdModel.recents(all.take(3), null).size)
    }

    @Test fun recents_memeChaineSousDeuxIdentifiants_apparaitUneSeuleFois() {
        val all = listOf(
            OsdRecent("p:10", null, 1, "TF1", null),
            OsdRecent("p:253", null, 253, "RMC MECANIC", null),
            OsdRecent("p:11", null, 1, " tf1 ", null),
            OsdRecent("q:10", null, 5, "TF1", null),
            OsdRecent("p:253", null, 253, "RMC MECANIC", null),
        )
        assertEquals(listOf("p:10", "p:253"), OsdModel.recents(all, null).map { it.key })
    }

    @Test fun recents_exclutLesVariantesDeLaChaineCourante() {
        val all = listOf(
            OsdRecent("p:11", null, 1, "TF1", null),
            OsdRecent("p:98", null, 98, "SERIE CLUB", null),
        )
        assertEquals(listOf("p:98"), OsdModel.recents(all, "p:10", "TF1").map { it.key })
    }

    @Test fun recents_gardeLOrdreEtLePlafondApresDedoublonnage() {
        val all = (1..30).map { OsdRecent("p:$it", null, it, "Ch ${it % 15}", null) }
        val r = OsdModel.recents(all, null)
        assertEquals(10, r.size)
        assertEquals((1..10).map { "p:$it" }, r.map { it.key })
    }
}
