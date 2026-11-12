package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.extmeta.ExtMetaApi
import com.ultratv.tv.nativeapp.data.extmeta.ExtResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemStore : TeamBadgeStore {
    val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
}

private class FakeTeamApi(var enabled: Boolean = true, var fail: Boolean = false, val answer: (String) -> ExtResponse) : ExtMetaApi {
    val calls = mutableListOf<String>()
    override fun isEnabled() = enabled
    override suspend fun get(pathAndQuery: String): ExtResponse? {
        calls += pathAndQuery
        if (fail) throw java.io.IOException("réseau")
        return answer(pathAndQuery)
    }
}

class TeamExtractionTest {
    @Test fun matchLine_separateursTiretTiretLongEtVs() {
        assertEquals("Club Nord" to "Club Sud", matchLineOf("Club Nord - Club Sud").let { it.home to it.away })
        assertEquals("Club Nord" to "Club Sud", matchLineOf("Club Nord – Club Sud").let { it.home to it.away })
        assertEquals("Club Nord" to "Club Sud", matchLineOf("Club Nord vs Club Sud").let { it.home to it.away })
        assertEquals("Club Nord" to "Club Sud", matchLineOf("Club Nord vs. Club Sud").let { it.home to it.away })
        val c = matchLineOf("Ligue Fictive : Club Nord – Club Sud")
        assertEquals("Ligue Fictive", c.competition); assertEquals("Club Nord", c.home); assertEquals("Club Sud", c.away)
    }

    @Test fun matchLine_traitDUnion_dansUnNom_pasUnSeparateur() {
        val m = matchLineOf("Saint-Ouen Club - Club Sud")
        assertEquals("Saint-Ouen Club", m.home); assertEquals("Club Sud", m.away)
        assertNull(matchLineOf("Magazine du sport").home)
    }
}

class TeamBadgeLogicTest {
    @Test fun queryName_nettoieEtBorne() {
        assertEquals("Club Nord", TeamBadgeLogic.queryName("  Club   Nord (F) "))
        assertEquals("Club Nord", TeamBadgeLogic.queryName("Club Nord [HD]"))
        assertEquals("Olympique Fictif", TeamBadgeLogic.queryName("Olympique Fictif ★"))
        assertEquals("St. Club's & Co", TeamBadgeLogic.queryName("St. Club's & Co"))
        assertNull(TeamBadgeLogic.queryName("A"))
        assertNull(TeamBadgeLogic.queryName("2024"))
        assertNull(TeamBadgeLogic.queryName("x".repeat(41)))
        assertNull(TeamBadgeLogic.queryName(null))
    }

    @Test fun keyOf_casseEtAccentsIgnores() {
        assertEquals(TeamBadgeLogic.keyOf("Séville Fictif"), TeamBadgeLogic.keyOf("SEVILLE fictif"))
    }

    private fun entry(id: Long, title: String) = SportEntry(
        ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = "Stade $id", logo = null, categoryId = null, streamUrl = "u"),
        EpgEntity(id = id, channelId = id, title = title, description = null, startMs = 0, endMs = 1), sportChannel = true, strict = true,
    )

    @Test fun namesOf_equipesSansDoublon_sportsIndividuelsIgnores() {
        val names = TeamBadgeLogic.namesOf(
            listOf(
                entry(1, "Ligue 1 : Club Nord – Club Sud"),
                entry(2, "Ligue 1 : club nord – Club Est"),
                entry(3, "ATP : Joueur Un – Joueur Deux"),
                entry(4, "UFC 320"),
                entry(5, "NBA : Celtes – Lacs"),
            ),
        )
        assertEquals(listOf("Club Nord", "Club Sud", "Club Est", "Celtes", "Lacs"), names)
        assertEquals(2, TeamBadgeLogic.namesOf(listOf(entry(1, "Club Nord – Club Sud"), entry(2, "Club Est – Club Ouest")), max = 2).size)
    }

    @Test fun encodeDecode_inconnueComprise_perimeeIgnoree() {
        assertEquals("https://img.example/b.png", TeamBadgeLogic.decode(TeamBadgeLogic.encode("https://img.example/b.png", 0), 10)!!.badge)
        val unknown = TeamBadgeLogic.decode(TeamBadgeLogic.encode(null, 0), 10)!!
        assertNull(unknown.badge)
        assertNull(TeamBadgeLogic.decode(TeamBadgeLogic.encode(null, 0), TeamBadgeLogic.TTL_MS))
        assertNull(TeamBadgeLogic.decode("illisible", 0))
    }
}

class TeamBadgeRepositoryTest {
    private val found = """{"team":{"name":"Club Nord","short":null,"badge":"https://img.example/n.png","league":"Ligue Fictive","sport":"Soccer"}}"""

    @Test fun ecussonTrouve_memoriseEnMemoireEtPreferences() = runBlocking {
        val store = MemStore()
        val api = FakeTeamApi { ExtResponse(200, found) }
        val r = TeamBadgeRepository(api, store)
        assertEquals("https://img.example/n.png", r.badge("Club Nord", now = 0))
        assertEquals("https://img.example/n.png", r.badge("CLUB NORD", now = 1))
        assertEquals(listOf("sports/team?name=Club%20Nord"), api.calls)
        // Nouvelle instance (redémarrage) : relu depuis les préférences, sans appel.
        val api2 = FakeTeamApi { ExtResponse(200, found) }
        assertEquals("https://img.example/n.png", TeamBadgeRepository(api2, store).badge("Club Nord", now = 2))
        assertTrue(api2.calls.isEmpty())
    }

    @Test fun equipeInconnue_memorisee_pasRedemandeePendant30Jours() = runBlocking {
        val store = MemStore()
        val api = FakeTeamApi { ExtResponse(200, """{"team":null}""") }
        val r = TeamBadgeRepository(api, store)
        assertNull(r.badge("Club Inconnu", now = 0))
        assertNull(TeamBadgeRepository(api, store).badge("Club Inconnu", now = 29L * 24 * 3_600_000))
        assertEquals(1, api.calls.size)
        assertNull(TeamBadgeRepository(api, store).badge("Club Inconnu", now = 31L * 24 * 3_600_000))
        assertEquals(2, api.calls.size)
    }

    @Test fun panneOuServiceIndisponible_rienEtNonMemorise() = runBlocking {
        val store = MemStore()
        val api = FakeTeamApi(fail = true) { ExtResponse(200, found) }
        val r = TeamBadgeRepository(api, store)
        assertNull(r.badge("Club Nord", now = 0))
        api.fail = false
        val r502 = TeamBadgeRepository(FakeTeamApi { ExtResponse(502, null) }, store)
        assertNull(r502.badge("Club Nord", now = 0))
        assertTrue(store.map.isEmpty())
        assertEquals("https://img.example/n.png", r.badge("Club Nord", now = 0))
    }

    @Test fun nonAppaire_aucunAppel() = runBlocking {
        val api = FakeTeamApi(enabled = false) { ExtResponse(200, found) }
        assertNull(TeamBadgeRepository(api, MemStore()).badge("Club Nord"))
        assertTrue(api.calls.isEmpty())
    }

    @Test fun badges_plusieursNoms_seulsLesEcussonsConnus() = runBlocking {
        val api = FakeTeamApi { p -> if (p.contains("Nord")) ExtResponse(200, found) else ExtResponse(200, """{"team":null}""") }
        val m = TeamBadgeRepository(api, MemStore()).badges(listOf("Club Nord", "Club Sud"), now = 0)
        assertEquals(mapOf(TeamBadgeLogic.keyOf("Club Nord")!! to "https://img.example/n.png"), m)
    }
}
