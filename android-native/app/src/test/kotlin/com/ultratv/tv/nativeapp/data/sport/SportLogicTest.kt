package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

private const val H = 3_600_000L

class SportLogicTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_700_000_000_000L - (1_700_000_000_000L % (24 * H)) + 10 * H   // 10:00 UTC

    private fun ch(id: Long, name: String = "Chaine $id", num: Int = 0) =
        ChannelEntity(id = id, providerId = 1, remoteId = "r$id", name = name, logo = null, categoryId = null, streamUrl = "u$id", num = num)

    private fun e(c: ChannelEntity, title: String, start: Long, end: Long, sportChannel: Boolean = false, strict: Boolean = true) =
        SportEntry(c, EpgEntity(id = c.id * 1000 + start / H, channelId = c.id, title = title, description = null, startMs = start, endMs = end), sportChannel, strict)

    @Test fun reglage_desactiveParDefaut() {
        assertFalse(UserPrefs().sportSection)
    }

    @Test fun liveNow_chainesDeSportDabord_puisDebut_etUnParChaine() {
        val a = ch(1, "Generaliste"); val b = ch(2, "beIN SPORTS 1"); val c = ch(3, "Eurosport")
        val list = listOf(
            e(a, "Match A", now - 2 * H, now + H),
            e(b, "Match B", now - H, now + H, sportChannel = true),
            e(c, "Match C", now - 3 * H, now + H, sportChannel = true),
            e(c, "Studio C", now - H / 2, now + 2 * H, sportChannel = true),   // même chaîne : le plus récent gagne
            e(b, "Plus tard", now + H, now + 2 * H, sportChannel = true),       // pas en cours
            e(a, "Passé", now - 5 * H, now - 4 * H),
        )
        val out = SportLogic.liveNow(list, now)
        assertEquals(listOf("Match B", "Studio C", "Match A"), out.map { it.prog.title })
    }

    @Test fun liveNow_borne_finExclue_debutInclus() {
        val a = ch(1)
        val out = SportLogic.liveNow(listOf(e(a, "Fini", now - H, now), e(ch(2), "Commence", now, now + H)), now)
        assertEquals(listOf("Commence"), out.map { it.prog.title })
    }

    @Test fun liveNow_limite() {
        val list = (1L..100L).map { e(ch(it), "T$it", now - H, now + H) }
        assertEquals(SportLogic.MAX_LIVE, SportLogic.liveNow(list, now).size)
        assertEquals(5, SportLogic.liveNow(list, now, limit = 5).size)
    }

    @Test fun upcoming_fenetre48h_dedoublonne_etGroupeParJour() {
        val a = ch(1); val b = ch(2)
        val list = listOf(
            e(a, "Ce soir", now + 4 * H, now + 6 * H),             // 14:00 aujourd'hui
            e(a, "Ce soir", now + 4 * H, now + 6 * H),             // doublon exact
            e(b, "Demain matin", now + 20 * H, now + 22 * H),      // 06:00 demain
            e(b, "Demain soir", now + 30 * H, now + 32 * H),       // 16:00 demain
            e(a, "Dans 47 h", now + 47 * H, now + 48 * H),         // 09:00 après-demain, dans la fenêtre
            e(a, "Dans 49 h", now + 49 * H, now + 50 * H),         // hors fenêtre
            e(a, "En cours", now - H, now + H),                    // pas « à venir »
            e(a, "Plateau", now + 2 * H, now + 3 * H, strict = false),   // chaîne de sport mais pas une rencontre
        )
        val days = SportLogic.upcoming(list, now, utc)
        assertEquals(3, days.size)
        assertEquals(listOf("Ce soir"), days[0].entries.map { it.prog.title })
        assertEquals(listOf("Demain matin", "Demain soir"), days[1].entries.map { it.prog.title })
        assertEquals(listOf("Dans 47 h"), days[2].entries.map { it.prog.title })
        assertEquals(listOf(0, 1, 2), days.map { SportLogic.dayOffset(it.dayStartMs, now, utc) })
        assertTrue(days.zipWithNext().all { (x, y) -> x.dayStartMs < y.dayStartMs })
    }

    @Test fun upcoming_triParHeure_puisChaineDeSportDabord() {
        val a = ch(1, "Zebre"); val b = ch(2, "Alpha")
        val t = now + 3 * H
        val days = SportLogic.upcoming(listOf(
            e(b, "Meme heure B", t, t + H), e(a, "Meme heure A", t, t + H, sportChannel = true), e(a, "Plus tot", t - H, t),
        ), now, utc)
        assertEquals(listOf("Plus tot", "Meme heure A", "Meme heure B"), days.flatMap { it.entries }.map { it.prog.title })
    }

    @Test fun upcoming_limite() {
        val list = (1L..300L).map { e(ch(it), "T$it", now + H + it * 1000, now + 2 * H) }
        assertEquals(SportLogic.MAX_UPCOMING, SportLogic.upcoming(list, now, utc).sumOf { it.entries.size })
    }

    @Test fun upcoming_vide() = assertTrue(SportLogic.upcoming(emptyList(), now, utc).isEmpty())

    @Test fun dayStart_minuitLocal() {
        assertEquals(now - 10 * H, SportLogic.dayStart(now, utc))
        val paris = TimeZone.getTimeZone("Europe/Paris")
        val s = SportLogic.dayStart(now, paris)
        assertTrue(s <= now && now - s < 24 * H)
    }

    @Test fun progress_bornee() {
        assertEquals(0.5f, SportLogic.progress(0, 100, 50), 0.001f)
        assertEquals(0f, SportLogic.progress(100, 200, 50), 0f)
        assertEquals(1f, SportLogic.progress(0, 100, 500), 0f)
        assertEquals(0f, SportLogic.progress(10, 10, 10), 0f)   // durée nulle : pas de division par zéro
    }

    @Test fun sportChannels_sansDoublon_numerotesDabord_limite() {
        val list = listOf(ch(1, "Zed", num = 0), ch(2, "Beta", num = 7), ch(3, "Alpha", num = 3), ch(3, "Alpha", num = 3), ch(4, "Alpha 2", num = 0))
        assertEquals(listOf(3L, 2L, 4L, 1L), SportLogic.sportChannels(list).map { it.id })
        assertEquals(2, SportLogic.sportChannels(list, limit = 2).size)
    }

    @Test fun matchLine_competitionAvantLaBarre_equipesSepareesParUnSlash() {
        val m = matchLineOf("Football : Qualifications de la CAN | RD Congo / Sierra Leone")
        assertEquals("Qualifications de la CAN", m.competition)
        assertEquals("RD Congo", m.home)
        assertEquals("Sierra Leone", m.away)
    }

    @Test fun discipline_titreAvantResume() {
        assertEquals(null, SportKind.ofProgramme("Ski alpin", "Avant le football de ce soir"))
        assertEquals(null, SportKind.ofProgramme("Ski alpin : Coupe du monde à Kitzbühel", null))
        assertEquals(SportKind.FOOTBALL, SportKind.ofProgramme("Ligue 1 · Nova - Stade", null))
    }

    @Test fun memeProgrammeSurPlusieursChaines_uneSeuleCarte() {
        val a = ch(1, "Stade 1"); val b = ch(2, "Stade 1 HD")
        val list = listOf(e(a, "Club du soir", 100, 200, sportChannel = true), e(b, "Club du soir", 100, 200, sportChannel = true))
        assertEquals(1, SportLogic.liveNow(list, 150).size)
        assertEquals(1, SportLogic.upcoming(list, 50, TimeZone.getTimeZone("UTC")).flatMap { it.entries }.size)
    }
}
