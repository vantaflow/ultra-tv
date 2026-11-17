package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.extmeta.ExtMetaApi
import com.ultratv.tv.nativeapp.data.extmeta.ExtResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L
private const val T0 = 1_700_000_000_000L

private class FakeLeagueApi(var enabled: Boolean = true, var fail: Boolean = false, val answer: (String) -> ExtResponse) : ExtMetaApi {
    val calls = mutableListOf<String>()
    override fun isEnabled() = enabled
    override suspend fun get(pathAndQuery: String): ExtResponse? {
        calls += pathAndQuery
        if (fail) throw java.io.IOException("réseau")
        return answer(pathAndQuery)
    }
}

class SportLeaguesTest {
    private val scheduleJson = """{"league":"ligue1","events":[
        {"i":"1","t":1000,"n":"A vs B","h":"A","a":"B","hb":"https://b/a.png","ab":"http://b/b.png","hs":2,"as":1,"st":"post","p":null,"r":10},
        {"i":"2","t":3000,"h":"C","a":"D","hs":null,"as":null,"st":"pre","r":11},
        {"i":"3","t":2000,"h":"E","a":"F","hs":0,"as":0,"st":"live","p":"37","r":11}]}"""

    @Test fun catalogue_memeClesQueLeWorker() {
        val keys = LeagueCatalog.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.containsAll(listOf("ligue1", "premier", "liga", "seriea", "bundesliga", "ucl", "botola", "nba", "nfl", "nhl", "mlb", "f1")))
    }

    @Test fun ordreEtMasquage_codageAller_retour() {
        assertEquals(LeaguePrefs.DEFAULT, LeaguePrefs.decode(null))
        assertEquals("", LeaguePrefs.DEFAULT.encode())
        val p = LeaguePrefs.DEFAULT.moved("nba", -3).withHidden("nfl", true)
        val back = LeaguePrefs.decode(p.encode())
        assertEquals(p, back)
        assertFalse(back.visible().any { it.key == "nfl" })
        assertTrue(back.everything().any { it.key == "nfl" })
        assertEquals(LeagueCatalog.all.size, back.everything().size)
    }

    @Test fun ordre_clesInconnuesIgnorees_nouvellesLiguesAjoutees() {
        val p = LeaguePrefs.decode("nba,zzz,-liga")
        assertEquals(listOf("nba", "liga"), p.order.take(2))
        assertTrue(p.isHidden("liga"))
        assertEquals(LeagueCatalog.all.size, p.order.size)
        assertEquals(LeaguePrefs.DEFAULT, LeaguePrefs.DEFAULT.moved("ligue1", -1))
        assertEquals(LeaguePrefs.DEFAULT, LeaguePrefs.DEFAULT.withHidden("inconnue", true))
    }

    @Test fun lecture_calendrier_ecussonsHttpsSeulement() {
        val l = LeagueParser.events(scheduleJson)!!
        assertEquals(3, l.size)
        assertEquals("https://b/a.png", l[0].homeBadge)
        assertNull(l[0].awayBadge)
        assertEquals(MatchState.POST, l[0].state)
        assertEquals(2, l[0].homeScore)
        assertEquals("A – B", l[0].title)
        assertEquals(MatchState.LIVE, l[2].state)
        assertEquals("37", l[2].progress)
        assertNull(LeagueParser.events("pas du json"))
        assertNull(LeagueParser.events(null))
    }

    @Test fun lecture_classementEtFeuille() {
        val t = LeagueParser.table("""{"rows":[{"rk":1,"team":"PSG","badge":"https://b","p":10,"w":8,"d":1,"l":1,"gd":15,"pts":25,"form":"WWDWL"},{"rk":2}]}""")!!
        assertEquals(1, t.size)
        assertEquals(25, t[0].points)
        val s = LeagueParser.sheet("""{"event":{"i":"9","t":5,"h":"A","a":"B","hs":1,"as":0,"st":"post","v":"Stade"},"timeline":[{"k":"goal","h":true,"m":20,"pl":"X"},{"d":"no kind"}],"stats":[{"k":"Shots","h":5,"a":2},{"k":"bad"}]}""")!!
        assertEquals("Stade", s.venue)
        assertEquals(1, s.timeline.size)
        assertEquals("X", s.timeline[0].player)
        assertEquals(listOf(StatItem("Shots", 5, 2)), s.stats)
    }

    @Test fun mise_en_page_directResultatsAvenir() {
        val sched = LeagueParser.events(scheduleJson)!!
        val live = listOf(sched[2].copy(homeScore = 1, progress = "41"))
        val all = LeagueLogic.merge(sched, live)
        assertEquals(3, all.size)
        assertEquals(1, all.first { it.id == "3" }.homeScore)
        assertEquals(listOf("3"), LeagueLogic.live(all).map { it.id })
        assertEquals(listOf("2"), LeagueLogic.fixtures(all, 0).map { it.id })
        assertEquals(listOf("1"), LeagueLogic.results(all).map { it.id })
    }

    @Test fun direct_interroge_seulement_si_un_match_est_proche() {
        val now = T0
        fun m(state: MatchState, start: Long?) = LeagueMatch("x", start, null, "A", "B", null, null, null, null, state, null, null)
        assertFalse(LeagueLogic.mayBeLive(listOf(m(MatchState.PRE, now + 3 * 3_600_000L)), now))
        assertTrue(LeagueLogic.mayBeLive(listOf(m(MatchState.PRE, now + 5 * MIN)), now))
        assertTrue(LeagueLogic.mayBeLive(listOf(m(MatchState.PRE, now - 30 * MIN)), now))
        assertFalse(LeagueLogic.mayBeLive(listOf(m(MatchState.PRE, now - 9 * 3_600_000L)), now))
        assertFalse(LeagueLogic.mayBeLive(listOf(m(MatchState.POST, now - 3_600_000L)), now))
        assertTrue(LeagueLogic.mayBeLive(listOf(m(MatchState.LIVE, null)), now))
    }

    @Test fun sansSpoiler_scoreMasqueJusquaRevelation() {
        val done = LeagueMatch("1", 1, null, "A", "B", null, null, 2, 1, MatchState.POST, null, null)
        val future = done.copy(id = "2", homeScore = null, awayScore = null, state = MatchState.PRE)
        assertTrue(SpoilerLogic.showScore(false, "1", emptySet()))
        assertFalse(SpoilerLogic.showScore(true, "1", emptySet()))
        assertTrue(SpoilerLogic.showScore(true, "1", setOf("1")))
        assertTrue(SpoilerLogic.hasSpoiler(done))
        assertFalse(SpoilerLogic.hasSpoiler(future))
        assertFalse(SpoilerLogic.showTable(true, "ligue1", emptySet()))
        assertTrue(SpoilerLogic.showTable(true, "ligue1", setOf("ligue1")))
        assertTrue(SpoilerLogic.showTable(false, "ligue1", emptySet()))
    }

    private fun entry(id: Long, title: String, start: Long, quality: Int = 2): SportEntry {
        val c = ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = "Chaine $id", logo = null, categoryId = null, streamUrl = "u$id", num = id.toInt()).copy(quality = quality)
        return SportEntry(c, EpgEntity(id = id, channelId = id, title = title, description = null, startMs = start, endMs = start + 120 * MIN), true, true)
    }

    @Test fun chaines_dun_match_alias_horaire_et_meilleureQualiteDabord() {
        val m = LeagueMatch("1", T0, null, "Paris Saint-Germain", "Marseille", null, null, null, null, MatchState.PRE, null, null)
        val hd = entry(1, "Ligue 1 : PSG - OM", T0 + 5 * MIN, quality = 2)
        val fhd = entry(2, "Paris SG vs Marseille", T0, quality = 3)
        val tooEarly = entry(3, "PSG - OM", T0 - 3 * 60 * MIN)
        val other = entry(4, "Lyon - Lille", T0)
        val got = LeagueChannels.channelsFor(m, listOf(hd, fhd, tooEarly, other))
        assertEquals(listOf(2L, 1L), got.map { it.channel.id })
        assertTrue(LeagueChannels.channelsFor(m.copy(home = null), listOf(hd)).isEmpty())
        assertTrue(LeagueChannels.channelsFor(m.copy(startMs = null), listOf(hd)).isEmpty())
    }

    // ── dépôt : cache sur l'appareil, échecs, non appairé ──

    private fun api(onGet: (String) -> ExtResponse = { ExtResponse(200, scheduleJson) }) = FakeLeagueApi(answer = onGet)

    @Test fun depot_calendrierMemorise1h_directMemorise60s() = runBlocking {
        val a = api()
        val repo = LeagueRepository(a)
        assertTrue(repo.schedule("ligue1", 0) is LeagueResult.Ok)
        repo.schedule("ligue1", 30 * MIN)
        assertEquals(1, a.calls.size)
        repo.schedule("ligue1", 61 * MIN)
        assertEquals(2, a.calls.size)
        repo.live("ligue1", 0); repo.live("ligue1", 59_000)
        assertEquals(3, a.calls.size)
        repo.live("ligue1", 61_000)
        assertEquals(4, a.calls.size)
        assertTrue(a.calls.all { it.startsWith("sports/league?id=ligue1&view=") })
    }

    @Test fun depot_nonAppaire_aucunAppel() = runBlocking {
        val a = FakeLeagueApi(enabled = false) { ExtResponse(200, scheduleJson) }
        assertEquals(LeagueResult.NotPaired, LeagueRepository(a).schedule("ligue1", 0))
        assertTrue(a.calls.isEmpty())
    }

    @Test fun depot_echec_pause30s_puis_ancienneValeurServie() = runBlocking {
        val b = FakeLeagueApi { ExtResponse(503, null) }
        val r2 = LeagueRepository(b)
        assertEquals(LeagueResult.Unavailable, r2.schedule("nba", 0))
        r2.schedule("nba", 10_000)
        assertEquals(1, b.calls.size)           // pas de rappel pendant la pause
        r2.schedule("nba", 31_000)
        assertEquals(2, b.calls.size)
        // valeur connue puis échec : ancienne valeur, marquée « ancienne »
        var ok = true
        val c = FakeLeagueApi { if (ok) ExtResponse(200, scheduleJson) else throw java.io.IOException("x") }
        val r3 = LeagueRepository(c)
        assertTrue((r3.schedule("liga", 0) as LeagueResult.Ok).value.isNotEmpty())
        ok = false
        val stale = r3.schedule("liga", 2 * 3_600_000L) as LeagueResult.Ok
        assertTrue(stale.stale)
        assertEquals(3, stale.value.size)
    }

    @Test fun depot_corpsIllisible_estUnEchec() = runBlocking {
        val a = FakeLeagueApi { ExtResponse(200, "<html>") }
        assertEquals(LeagueResult.Unavailable, LeagueRepository(a).schedule("ligue1", 0))
    }

    @Test fun profil_prefs_sportLu_depuis_la_table() {
        val p = com.ultratv.tv.nativeapp.data.profile.ProfilePrefs.fromMap(mapOf("sport_leagues" to "nba,-nfl", "sport_no_spoiler" to "true"))
        assertEquals(true, p.sportNoSpoiler)
        assertTrue(LeaguePrefs.decode(p.sportLeagues).isHidden("nfl"))
        assertNull(com.ultratv.tv.nativeapp.data.profile.ProfilePrefs.fromMap(mapOf("sport_no_spoiler" to "peut-être")).sportNoSpoiler)
    }
}
