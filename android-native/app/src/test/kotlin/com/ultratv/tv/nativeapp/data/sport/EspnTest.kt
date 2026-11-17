package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.extmeta.ExtMetaApi
import com.ultratv.tv.nativeapp.data.extmeta.ExtResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class EspnFake(val answer: (String) -> ExtResponse?) : EspnApi {
    val calls = mutableListOf<String>()
    override suspend fun get(pathAndQuery: String): ExtResponse? { calls += pathAndQuery; return answer(pathAndQuery) }
}

private class WorkerFake(val enabled: Boolean = true, val answer: (String) -> ExtResponse) : ExtMetaApi {
    val calls = mutableListOf<String>()
    override fun isEnabled() = enabled
    override suspend fun get(pathAndQuery: String): ExtResponse? { calls += pathAndQuery; return answer(pathAndQuery) }
}

/** Fixtures réelles (réduites) d'ESPN ; résultats attendus = sortie de esEvent / esMatchBody du Worker sur les mêmes fichiers. */
class EspnTest {
    private fun res(n: String) = javaClass.getResource("/espn/$n")!!.readText()
    private val scoreboard = res("scoreboard.json")
    private val summary = res("summary.json")
    // 3 h après le coup d'envoi du match terminé (Tahiti – Cook Islands).
    private val now = 1_794_893_400_000L + 3 * 3_600_000L

    @Test fun jour_directsEtTerminesRecentsSeulement() {
        val l = EspnParser.day(scoreboard, now)!!
        assertEquals(listOf("es-401929704", "es-401927138", "es-401933879"), l.map { it.id }) // le match à venir est écarté
        val live = l[0]
        assertEquals("Belize", live.home); assertEquals("Sint Maarten", live.away); assertEquals("Belize vs Sint Maarten", live.name)
        assertEquals(MatchState.LIVE, live.state); assertEquals("53", live.progress)
        assertEquals(0, live.homeScore); assertEquals(1, live.awayScore)
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/blz.png", live.homeBadge)
        assertEquals(1_794_942_000_000L, live.startMs)
        assertEquals("HT", l[1].progress)
        val post = l[2]
        assertEquals(MatchState.POST, post.state); assertNull(post.progress)
        assertEquals(3, post.homeScore); assertEquals(3, post.awayScore); assertEquals("Cook Isl", post.away)
    }

    @Test fun jour_termineDepuisPlusDe5h_ecarte() {
        val l = EspnParser.day(scoreboard, now + 3 * 3_600_000L)!!
        assertEquals(listOf("es-401929704", "es-401927138"), l.map { it.id })
    }

    @Test fun jour_corpsIllisible_ou_sansEvents_estNull() {
        assertNull(EspnParser.day("<html>", now))
        assertNull(EspnParser.day("{}", now))
        assertNull(EspnParser.day(null, now))
        assertEquals(emptyList<LeagueMatch>(), EspnParser.day("""{"events":[{"id":"1"}]}""", now))
    }

    @Test fun statuts_reportesEtAnnules_sont_off_sansScore() {
        val j = """{"events":[{"id":"9","date":"2026-11-17T10:00Z","status":{"type":{"name":"STATUS_POSTPONED","state":"pre"}},
            "competitions":[{"competitors":[{"homeAway":"home","score":"1","team":{"shortDisplayName":"A"}},{"homeAway":"away","score":"0","team":{"shortDisplayName":"B"}}]}]}]}"""
        val m = EspnParser.scoreboardEvent(org.json.JSONObject(j).getJSONArray("events").getJSONObject(0))!!
        assertEquals(MatchState.OFF, m.state); assertNull(m.homeScore)
    }

    @Test fun feuille_score_tempsForts_stats() {
        val s = EspnParser.sheet(summary)!!
        assertEquals("es-401916335", s.match.id); assertEquals("Singapore vs Brazil", s.match.name)
        assertEquals(MatchState.POST, s.match.state); assertEquals(0, s.match.homeScore); assertEquals(7, s.match.awayScore)
        assertEquals("Singapore National Stadium", s.venue)
        assertEquals(5, s.timeline.size)
        assertEquals(TimelineItem("goal", "Goal", false, 9, "Vinícius Júnior", "Bruno Guimarães"), s.timeline[0])
        assertEquals(TimelineItem("card", "yellow-card", true, 45, "Safuwan Baharudin", null), s.timeline.last())
        assertEquals(StatItem("Possession", 39, 60), s.stats[0])
        assertEquals(StatItem("Shots on target", 1, 11), s.stats[2])
        assertEquals(9, s.stats.size)
        assertNull(EspnParser.sheet("{}")); assertNull(EspnParser.sheet("pas du json"))
    }

    // ── dépôt ──

    @Test fun depot_jour_espnDirect_cache60s_sansWorker() = runBlocking {
        val e = EspnFake { ExtResponse(200, scoreboard) }
        val w = WorkerFake(enabled = false) { ExtResponse(200, "{}") }
        val repo = LeagueRepository(w, e)
        val r = repo.day(now) as LeagueResult.Ok
        assertEquals(3, r.value.size)
        repo.day(now + 59_000)
        assertEquals(listOf("scoreboard"), e.calls) // une seule requête dans la minute
        repo.day(now + 61_000)
        assertEquals(2, e.calls.size)
        assertTrue(w.calls.isEmpty()) // fonctionne même appareil non appairé
    }

    @Test fun depot_jour_espn403_repliSurWorker_et_recul() = runBlocking {
        val e = EspnFake { ExtResponse(403, null) }
        val w = WorkerFake { ExtResponse(200, """{"events":[{"i":"es-7","t":1,"h":"A","a":"B","hs":1,"as":0,"st":"live","p":"10"}]}""") }
        val repo = LeagueRepository(w, e)
        val r = repo.day(now) as LeagueResult.Ok
        assertEquals("es-7", r.value.single().id)
        assertEquals(listOf("sports/day"), w.calls)
        repo.day(now + 61_000) // recul de 30 s écoulé : ESPN retenté (échoue encore), recul doublé
        assertEquals(2, e.calls.size)
        repo.day(now + 61_000 + 30_000)
        assertEquals(2, e.calls.size) // recul de 60 s pas fini après 30 s
        repo.day(now + 61_000 + 61_000)
        assertEquals(3, e.calls.size)
    }

    @Test fun depot_jour_espnReseauHS_repliSurWorker() = runBlocking {
        val e = EspnFake { throw java.io.IOException("réseau") }
        val w = WorkerFake { ExtResponse(200, """{"events":[]}""") }
        assertTrue(LeagueRepository(w, e).day(now) is LeagueResult.Ok)
        assertEquals(1, w.calls.size)
    }

    @Test fun depot_feuilleEs_lueChezEspn_autresIdentifiantsParLeWorker() = runBlocking {
        val e = EspnFake { ExtResponse(200, summary) }
        val w = WorkerFake { ExtResponse(503, null) }
        val repo = LeagueRepository(w, e)
        val s = (repo.sheet("es-401916335", finished = true, now = now) as LeagueResult.Ok).value
        assertEquals("Singapore National Stadium", s.venue)
        assertEquals(listOf("summary?event=401916335"), e.calls)
        assertTrue(w.calls.isEmpty())
        assertTrue(repo.sheet("fd-123", finished = false, now = now) is LeagueResult.Unavailable)
        assertEquals(1, e.calls.size) // fd-123 ne passe jamais par ESPN
        assertEquals(1, w.calls.size)
    }

    @Test fun depot_feuilleEs_espnEchoue_repliSurWorker() = runBlocking {
        val e = EspnFake { ExtResponse(500, null) }
        val w = WorkerFake { ExtResponse(200, """{"event":{"i":"es-5","h":"A","a":"B","st":"live","hs":1,"as":1},"timeline":[],"stats":[]}""") }
        val s = (LeagueRepository(w, e).sheet("es-5", finished = false, now = now) as LeagueResult.Ok).value
        assertEquals("es-5", s.match.id)
        assertEquals(1, w.calls.size)
    }
}
