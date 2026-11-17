package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.extmeta.ExtMetaApi
import com.ultratv.tv.nativeapp.data.extmeta.ExtResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val M = 60_000L
private const val NOW = 1_700_000_000_000L

/** Accueil Sport unifié : cartes « En direct », filtre « À venir », zones de classement, forme, identifiants opaques. */
class SportHubLogicTest {
    private fun ch(id: Long, name: String, quality: Int) =
        ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = name, logo = null, categoryId = "sport", streamUrl = "u", num = id.toInt(), quality = quality)

    private fun entry(c: ChannelEntity, title: String, start: Long, end: Long = start + 120 * M) =
        SportEntry(c, EpgEntity(id = start + c.id, channelId = c.id, title = title, description = null, startMs = start, endMs = end), sportChannel = true, strict = true)

    private fun live(id: String, h: String, a: String, hs: Int?, a2: Int?, p: String? = "67") =
        LeagueMatch(id, NOW - 67 * M, null, h, a, null, null, hs, a2, MatchState.LIVE, p, 14)

    @Test fun zones_ligue1_ldc_europa_relegation() {
        assertEquals(Zone.QUALIFIED, StandingZones.of("ligue1", 3, 18))
        assertEquals(Zone.EUROPA, StandingZones.of("ligue1", 4, 18))
        assertEquals(Zone.NONE, StandingZones.of("ligue1", 9, 18))
        assertEquals(Zone.RELEGATION, StandingZones.of("ligue1", 16, 18))
        assertEquals(Zone.RELEGATION, StandingZones.of("bundesliga", 17, 18))
        assertEquals(Zone.NONE, StandingZones.of("bundesliga", 16, 18))
        // Pas de zones pour la NBA, un classement par groupes ou un petit tableau.
        assertEquals(Zone.NONE, StandingZones.of("nba", 1, 30))
        assertEquals(Zone.NONE, StandingZones.of("ligue1", 1, 18, grouped = true))
        assertEquals(Zone.NONE, StandingZones.of("ligue1", 1, 4))
    }

    @Test fun forme_normaliseeSurCinq() {
        assertEquals(listOf('W', 'D', 'L', 'W', 'W'), FormLogic.parse("xWDLWW"))
        assertEquals(listOf('W', 'W', 'D', 'L'), FormLogic.parse("VVNP"))
        assertEquals(emptyList<Char>(), FormLogic.parse(null))
    }

    @Test fun qualite_etMinuteEtPourcentage() {
        assertEquals("4K", SportHubLogic.qualityOf(ch(1, "a", 4)))
        assertEquals("HD", SportHubLogic.qualityOf(ch(1, "a", 3)))
        assertEquals("SD", SportHubLogic.qualityOf(ch(1, "a", 1)))
        assertEquals("", SportHubLogic.qualityOf(ch(1, "a", 0)))
        assertEquals("67'", SportHubLogic.minuteOf(live("1", "A", "B", 0, 0, "67")))
        assertEquals("HT", SportHubLogic.minuteOf(live("1", "A", "B", 0, 0, "HT")))
        assertNull(SportHubLogic.minuteOf(live("1", "A", "B", 0, 0, null)))
        assertEquals(61, SportHubLogic.sharePercent(61, 39))
        assertEquals(50, SportHubLogic.sharePercent(0, 0))
    }

    @Test fun carteDirect_ligueChoisie_scoreEtChainesTrieesParQualite() {
        val hd = ch(1, "Stade 1 HD", 2); val k4 = ch(2, "Stade 1 4K", 4)
        val entries = listOf(entry(hd, "Ligue 1 : PSG – Marseille", NOW - 67 * M), entry(k4, "Ligue 1 : Paris SG vs OM", NOW - 66 * M))
        val cards = SportHubLogic.liveCards("ligue1", "Ligue 1", SportKind.FOOTBALL, listOf(live("fd-123", "Paris SG", "Marseille", 2, 1)), emptyList(), entries, emptyMap(), NOW) { "Programme" }
        assertEquals(1, cards.size)
        val c = cards[0]
        assertEquals("mfd-123", c.key)
        assertEquals("Ligue 1", c.league); assertEquals("67'", c.minute)
        assertEquals(2, c.homeScore); assertEquals(1, c.awayScore)
        assertEquals(listOf(2L, 1L), c.channels.map { it.channel.id })   // 4K d'abord
        assertNotNull(c.progress)
    }

    @Test fun carteDirect_toutLeGuide_enrichiDuScoreQuandLeMatchEstConnu() {
        val a = ch(1, "Stade 1", 2); val b = ch(2, "Arena", 2)
        val pm = entry(a, "Ligue 1 : PSG – Marseille", NOW - 60 * M)
        val mag = entry(b, "Canal Football Club", NOW - 10 * M)
        val cards = SportHubLogic.liveCards(null, "Ligue 1", SportKind.FOOTBALL, listOf(live("7", "Paris SG", "Marseille", 1, 0)), listOf(pm, mag), listOf(pm, mag), emptyMap(), NOW) { "Programme" }
        assertEquals(2, cards.size)
        assertEquals(1, cards[0].homeScore)                  // enrichi
        assertNull(cards[1].homeScore)                       // émission sans match : pas de score
        assertEquals("Canal Football Club", cards[1].title)
        assertEquals("Programme", cards[1].league)
    }

    @Test fun carteDirect_toutLeGuide_scoreDUneAutreLigueQueCelleChargee() {
        // Bug 1.3.42 : « Tout » ne rattachait que les matchs de la ligue sélectionnée (ici Ligue 1) ; un match de
        // la Champions League diffusé au guide n'avait ni score ni feuille de match.
        val a = ch(1, "Stade 1", 2); val b = ch(2, "Arena", 2)
        val fr = entry(a, "France – Türkiye", NOW - 30 * M)
        val ldc = entry(b, "Champions League : Real Madrid – Bayern", NOW - 40 * M)
        val l1 = live("7", "Paris SG", "Marseille", 1, 0)
        val tr = live("u1", "France", "Türkiye", 2, 1, "30").copy(startMs = NOW - 30 * M)
        val rm = live("u2", "Real Madrid", "Bayern", 0, 3, "40").copy(startMs = NOW - 40 * M)
        val cards = SportHubLogic.liveCards(
            null, "Ligue 1", SportKind.FOOTBALL, listOf(l1), listOf(fr, ldc), listOf(fr, ldc), emptyMap(), NOW,
            mapOf("ligue1" to listOf(l1), "ucl" to listOf(tr, rm)),
        ) { "Programme" }
        assertEquals(2, cards.size)
        assertEquals("u1", cards[0].match?.id); assertEquals(2, cards[0].homeScore); assertEquals(1, cards[0].awayScore)
        assertEquals("30'", cards[0].minute); assertEquals("Champions League", cards[0].league)
        assertEquals("u2", cards[1].match?.id); assertEquals(3, cards[1].awayScore)
    }

    @Test fun carteDirect_toutLeGuide_matchNonLiveOuInconnu_resteSansMatch() {
        val a = ch(1, "Stade 1", 2)
        val fr = entry(a, "France – Türkiye", NOW - 30 * M)
        val fini = live("u1", "France", "Türkiye", 2, 1).copy(state = MatchState.POST)
        val cards = SportHubLogic.liveCards(null, null, null, emptyList(), listOf(fr), listOf(fr), emptyMap(), NOW, mapOf("ucl" to listOf(fini))) { "Programme" }
        assertNull(cards[0].match); assertNull(cards[0].homeScore)
        assertEquals("France", cards[0].home)
    }

    @Test fun titre_suffixeApresLeSecondTiret_neFaitPasPartieDeLEquipe() {
        val l = matchLineOf("Liechtenstein - Azerbaijan - U21")
        assertEquals("Liechtenstein", l.home); assertEquals("Azerbaijan", l.away)
        val p1 = SportMatching.parse("Liechtenstein - Azerbaijan - U21"); val p2 = SportMatching.parse("Liechtenstein - Azerbaijan")
        assertEquals(p2.key, p1.key); assertTrue(p1.isMatch)
        val l2 = matchLineOf("Ligue 1 - PSG - OM")
        assertEquals("PSG", l2.home); assertEquals("OM", l2.away)
    }

    @Test fun carteDirect_nhl_nomCourtDuGuideRattacheAuNomCompletDeLApi() {
        val a = ch(1, "Sport 1", 2)
        val e = entry(a, "NHL : Rangers - Canadiens", NOW - 20 * M)
        val m = live("n1", "New York Rangers", "Montréal Canadiens", 3, 2, "P2").copy(startMs = NOW - 20 * M)
        val cards = SportHubLogic.liveCards(null, null, null, emptyList(), listOf(e), listOf(e), emptyMap(), NOW, mapOf("nhl" to listOf(m))) { "Programme" }
        assertEquals(3, cards[0].homeScore); assertEquals("NHL", cards[0].league)
    }

    @Test fun aVenir_ligueChoisie_gardeSesMatchsEtSaCompetition_toutGardeLesMagazines() {
        val c = ch(1, "Stade 1", 2)
        val derby = entry(c, "Ligue 1 : Lyon – Lille", NOW + 120 * M)
        val nba = entry(c, "NBA : Lakers – Celtics", NOW + 180 * M)
        val mag = entry(c, "Canal Football Club", NOW + 240 * M)
        val days = listOf(SportDay(0, listOf(derby, nba, mag)))
        val fixture = LeagueMatch("af-9", NOW + 120 * M, null, "Lyon", "Lille", null, null, null, null, MatchState.PRE, null, 15)
        assertEquals(3, SportHubLogic.filterUpcoming(null, emptyList(), emptyList(), days).sumOf { it.entries.size })
        val l1 = SportHubLogic.filterUpcoming("ligue1", listOf(fixture), listOf(derby, nba, mag), days)
        assertEquals(listOf(derby), l1.flatMap { it.entries })
        val nbaOnly = SportHubLogic.filterUpcoming("nba", emptyList(), listOf(derby, nba, mag), days)
        assertEquals(listOf(nba), nbaOnly.flatMap { it.entries })
    }

    @Test fun classement_derniereJournee() {
        fun r(p: Int?) = StandingRow(1, "A", null, null, p, null, null, null, null, null, null)
        assertEquals(14, SportHubLogic.roundOf(listOf(r(13), r(14), r(null))))
        assertNull(SportHubLogic.roundOf(listOf(r(null))))
    }

    @Test fun initiales_et_couleurStable() {
        assertEquals("PSG", com.ultratv.tv.nativeapp.ui.sport.initialsOf("PSG"))
        assertEquals("PS", com.ultratv.tv.nativeapp.ui.sport.initialsOf("Paris SG"))
        assertEquals("MAR", com.ultratv.tv.nativeapp.ui.sport.initialsOf("Marseille"))
        assertEquals(com.ultratv.tv.nativeapp.ui.sport.crestColor("Paris SG"), com.ultratv.tv.nativeapp.ui.sport.crestColor("PSG"))
    }

    // ── Identifiants de match opaques (« 123 », « fd-123 », « jo-2026-12 ») : jamais validés comme des nombres ──

    @Test fun identifiantOpaque_lectureJson_chaineOuNombre() {
        val ev = LeagueParser.events("""{"events":[{"i":"fd-123","t":1,"h":"A","a":"B","st":"pre"},{"i":456,"t":2,"h":"C","a":"D","st":"pre"},{"i":"jo-2026-12","t":3,"n":"GP","st":"pre"}]}""")!!
        assertEquals(listOf("fd-123", "456", "jo-2026-12"), ev.map { it.id })
        val sheet = LeagueParser.sheet("""{"event":{"i":"fd-123","t":5,"h":"A","a":"B","hs":1,"as":0,"st":"post","v":"Stade","src":"fd","stale":true},"timeline":[],"stats":[]}""")!!
        assertEquals("fd-123", sheet.match.id)
    }

    @Test fun identifiantOpaque_appelFeuilleDeMatch_encodeNonValide() = runBlocking {
        val calls = mutableListOf<String>()
        val api = object : ExtMetaApi {
            override fun isEnabled() = true
            override suspend fun get(pathAndQuery: String): ExtResponse? {
                calls += pathAndQuery
                return ExtResponse(200, """{"event":{"i":"fd-123","t":5,"h":"A","a":"B","st":"post"}}""")
            }
        }
        val r = LeagueRepository(api).sheet("fd-123", finished = true, now = NOW)
        assertTrue(r is LeagueResult.Ok)
        assertEquals("sports/match?id=fd-123", calls.single())
        LeagueRepository(api).sheet("jo/2026 12", finished = true, now = NOW)
        assertEquals("sports/match?id=jo%2F2026+12", calls.last())
    }

    @Test fun guideMatch_carteSansScore_equipesDuTitreEtEnDirect() {
        val e = SportEntry(ch(1, "Stade 1", 3), EpgEntity(id = 1, channelId = 1, title = "Foot : A / B", description = null, startMs = NOW - 10 * M, endMs = NOW + 80 * M), true, true)
        val c = LiveCardModel("gk", "CAN", null, "A", "B", "Foot : A / B", null, null, null, listOf(e), 0.1f)
        val m = SportHubLogic.guideMatch(c)
        assertEquals("gk", m.id); assertEquals("A", m.home); assertEquals("B", m.away)
        assertEquals(MatchState.LIVE, m.state); assertEquals(NOW - 10 * M, m.startMs)
        assertNull(m.homeScore)
    }
}
