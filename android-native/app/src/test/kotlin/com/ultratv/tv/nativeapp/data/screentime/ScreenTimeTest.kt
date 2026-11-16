package com.ultratv.tv.nativeapp.data.screentime

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ScreenTimeTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun ms(s: String, zone: ZoneId = paris) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private fun w(a: Int, b: Int) = TimeWindow(a, b)
    private val min = 60_000L

    // 2026-11-16 est un lundi (heure d'hiver, UTC+1).
    private val monday = ScreenTimeRules(days = mapOf(1 to listOf(w(480, 1200))))

    // ── Codage ──

    @Test fun codec_aller_retour() {
        val r = ScreenTimeRules(90, mapOf(1 to listOf(w(480, 720), w(840, 1200)), 6 to emptyList(), 7 to listOf(w(0, 1440))))
        val s = ScreenTime.encode(r)
        assertEquals("L90;1=480-720,840-1200;6=;7=0-1440", s)
        assertEquals(r, ScreenTime.decode(s))
    }

    @Test fun codec_vide_et_illisible() {
        assertTrue(ScreenTime.decode(null).isEmpty)
        assertTrue(ScreenTime.decode("").isEmpty)
        assertTrue(ScreenTime.decode("n'importe quoi").isEmpty)
        assertTrue(ScreenTime.decode("x".repeat(500)).isEmpty)
        assertEquals("", ScreenTime.encode(ScreenTimeRules()))
    }

    @Test fun decode_tolerant_borne_et_fusionne() {
        val r = ScreenTime.decode("L0;L5000;8=1-2;1=900-800,600-700,650-720,0-0,1500-1600;2=480-1200,x,5")
        assertNull(r.dailyLimitMin)
        assertNull(r.days[8])
        assertEquals(listOf(w(600, 720)), r.days[1])
        assertEquals(listOf(w(480, 1200)), r.days[2])
    }

    @Test fun normalize_fusionne_les_plages_qui_se_touchent_et_borne_a_quatre() {
        assertEquals(listOf(w(0, 300)), ScreenTime.normalize(listOf(w(0, 100), w(100, 300), w(50, 60))))
        val many = (0 until 8).map { w(it * 100, it * 100 + 50) }
        assertEquals(4, ScreenTime.normalize(many).size)
    }

    // ── Plages ──

    @Test fun dans_la_plage_avec_temps_restant() {
        val v = ScreenTime.evaluate(monday, 0, ms("2026-11-16T10:00"), paris)
        assertEquals(Verdict.Allowed(10 * 3600L, false), v)
    }

    @Test fun avertissement_cinq_minutes_avant_la_fin_de_plage() {
        val v = ScreenTime.evaluate(monday, 0, ms("2026-11-16T19:55"), paris) as Verdict.Allowed
        assertTrue(v.warn); assertEquals(300L, v.secondsLeft)
        assertFalse((ScreenTime.evaluate(monday, 0, ms("2026-11-16T19:54"), paris) as Verdict.Allowed).warn)
    }

    @Test fun hors_plage_bloque_et_donne_la_prochaine_plage() {
        val v = ScreenTime.evaluate(monday, 0, ms("2026-11-16T20:00"), paris)
        // Le mardi n'a aucune restriction : autorisé dès minuit.
        assertEquals(Verdict.Blocked(BlockReason.OUTSIDE_WINDOW, ms("2026-11-17T00:00")), v)
        val early = ScreenTime.evaluate(monday, 0, ms("2026-11-16T07:30"), paris)
        assertEquals(Verdict.Blocked(BlockReason.OUTSIDE_WINDOW, ms("2026-11-16T08:00")), early)
    }

    @Test fun jour_interdit_toute_la_journee_puis_lendemain_en_plage() {
        val r = ScreenTimeRules(days = mapOf(1 to emptyList(), 2 to listOf(w(540, 600))))
        val v = ScreenTime.evaluate(r, 0, ms("2026-11-16T12:00"), paris)
        assertEquals(Verdict.Blocked(BlockReason.OUTSIDE_WINDOW, ms("2026-11-17T09:00")), v)
    }

    @Test fun semaine_entierement_interdite_sans_prochaine_plage() {
        val r = ScreenTimeRules(days = (1..7).associateWith { emptyList<TimeWindow>() })
        assertEquals(Verdict.Blocked(BlockReason.OUTSIDE_WINDOW, null), ScreenTime.evaluate(r, 0, ms("2026-11-16T12:00"), paris))
    }

    @Test fun plusieurs_plages_dans_la_journee() {
        val r = ScreenTimeRules(days = mapOf(1 to listOf(w(480, 720), w(840, 1200))))
        assertEquals(Verdict.Blocked(BlockReason.OUTSIDE_WINDOW, ms("2026-11-16T14:00")), ScreenTime.evaluate(r, 0, ms("2026-11-16T13:00"), paris))
        assertTrue(ScreenTime.evaluate(r, 0, ms("2026-11-16T14:00"), paris) is Verdict.Allowed)
    }

    @Test fun plages_enchainees_a_minuit() {
        // Lundi jusqu'à 24:00, mardi à partir de 00:00 : pas de coupure à minuit, la fin est celle de mardi.
        val r = ScreenTimeRules(days = mapOf(1 to listOf(w(1200, 1440)), 2 to listOf(w(0, 360))))
        assertEquals(ms("2026-11-17T06:00"), ScreenTime.allowedUntil(r, ms("2026-11-16T21:00"), paris))
        val v = ScreenTime.evaluate(r, 0, ms("2026-11-16T23:57"), paris) as Verdict.Allowed
        assertFalse(v.warn); assertEquals(6 * 3600L + 3 * 60, v.secondsLeft)
    }

    @Test fun jour_sans_restriction_enchaine_avec_la_plage_du_lendemain() {
        // Dimanche libre, lundi 08:00-20:00 : à 23:00 dimanche, l'autorisation s'arrête à minuit.
        val r = ScreenTimeRules(days = mapOf(1 to listOf(w(480, 1200))))
        assertEquals(ms("2026-11-16T00:00"), ScreenTime.allowedUntil(r, ms("2026-11-15T23:00"), paris))
        assertEquals(Verdict.Blocked(BlockReason.OUTSIDE_WINDOW, ms("2026-11-16T08:00")), ScreenTime.evaluate(r, 0, ms("2026-11-16T00:00"), paris))
    }

    @Test fun aucune_contrainte_horaire_dans_la_semaine_a_venir() {
        val r = ScreenTimeRules(dailyLimitMin = 60)
        assertNull(ScreenTime.allowedUntil(r, ms("2026-11-16T10:00"), paris))
    }

    // ── Limite quotidienne ──

    @Test fun limite_atteinte_bloque_jusqu_a_minuit() {
        val r = ScreenTimeRules(dailyLimitMin = 60)
        val v = ScreenTime.evaluate(r, 3600, ms("2026-11-16T15:00"), paris)
        assertEquals(Verdict.Blocked(BlockReason.LIMIT_REACHED, ms("2026-11-17T00:00")), v)
    }

    @Test fun limite_atteinte_prochaine_plage_du_lendemain() {
        val r = ScreenTimeRules(dailyLimitMin = 60, days = mapOf(1 to listOf(w(480, 1200)), 2 to listOf(w(480, 1200))))
        val v = ScreenTime.evaluate(r, 4000, ms("2026-11-16T15:00"), paris)
        assertEquals(Verdict.Blocked(BlockReason.LIMIT_REACHED, ms("2026-11-17T08:00")), v)
    }

    @Test fun avertissement_a_cinq_minutes_de_la_limite_et_le_plus_proche_gagne() {
        val r = ScreenTimeRules(dailyLimitMin = 60, days = mapOf(1 to listOf(w(480, 1200))))
        val v = ScreenTime.evaluate(r, 3300, ms("2026-11-16T10:00"), paris) as Verdict.Allowed
        assertTrue(v.warn); assertEquals(300L, v.secondsLeft)
        // La fin de plage (10 min) arrive avant le reste de la limite (50 min).
        val w2 = ScreenTime.evaluate(r, 600, ms("2026-11-16T19:50"), paris) as Verdict.Allowed
        assertEquals(600L, w2.secondsLeft); assertFalse(w2.warn)
    }

    @Test fun hors_plage_prime_sur_la_limite() {
        val r = ScreenTimeRules(dailyLimitMin = 60, days = mapOf(1 to listOf(w(480, 1200))))
        assertTrue((ScreenTime.evaluate(r, 5000, ms("2026-11-16T21:00"), paris) as Verdict.Blocked).reason == BlockReason.OUTSIDE_WINDOW)
    }

    @Test fun sans_regle_jamais_de_blocage() {
        assertEquals(Verdict.Unrestricted, ScreenTime.evaluate(ScreenTimeRules(), 999_999, ms("2026-11-16T03:00"), paris))
    }

    // ── Dérogations ──

    @Test fun derogation_30_minutes_passe_outre_plage_et_limite_puis_rebloque() {
        val now = ms("2026-11-16T21:00")
        val g = ScreenTime.extend(null, now)
        assertEquals(now + 30 * min, g.untilMs)
        val during = ScreenTime.evaluate(monday, 0, now + 10 * min, paris, g) as Verdict.Allowed
        assertEquals(20 * 60L, during.secondsLeft); assertFalse(during.warn)
        assertTrue((ScreenTime.evaluate(monday, 0, now + 26 * min, paris, g) as Verdict.Allowed).warn)
        assertTrue(ScreenTime.evaluate(monday, 0, now + 30 * min, paris, g) is Verdict.Blocked)
    }

    @Test fun derogation_cumulative() {
        val now = ms("2026-11-16T21:00")
        val g1 = ScreenTime.extend(null, now)
        assertEquals(now + 60 * min, ScreenTime.extend(g1, now + 5 * min).untilMs)
        // Une dérogation expirée ne s'additionne pas : on repart de maintenant.
        assertEquals(now + 90 * min + 30 * min, ScreenTime.extend(g1, now + 90 * min).untilMs)
    }

    @Test fun derogation_pour_aujourd_hui_jusqu_a_minuit_sans_avertissement() {
        val now = ms("2026-11-16T21:00")
        val g = ScreenTime.forToday(now, paris)
        assertEquals(ms("2026-11-17T00:00"), g.untilMs)
        val v = ScreenTime.evaluate(monday, 99999, ms("2026-11-16T23:58"), paris, g) as Verdict.Allowed
        assertFalse(v.warn)
        // Le lendemain, les règles reprennent (mardi sans plage : libre ; lundi suivant : bloqué hors plage).
        assertTrue(ScreenTime.evaluate(monday, 0, ms("2026-11-23T21:00"), paris, g) is Verdict.Blocked)
    }

    // ── Minuit, changement d'heure, cumul ──

    @Test fun cumul_a_cheval_sur_minuit_est_reparti_par_jour_local() {
        val m = ScreenTime.splitByDay(ms("2026-11-16T23:50"), ms("2026-11-17T00:20"), paris)
        assertEquals(mapOf("2026-11-16" to 10 * min, "2026-11-17" to 20 * min), m)
    }

    @Test fun cumul_plusieurs_jours() {
        val m = ScreenTime.splitByDay(ms("2026-11-16T22:00"), ms("2026-11-18T01:00"), paris)
        assertEquals(mapOf("2026-11-16" to 2 * 60 * min, "2026-11-17" to 24 * 60 * min, "2026-11-18" to 60 * min), m)
    }

    @Test fun cumul_intervalle_vide_ou_inverse() {
        assertTrue(ScreenTime.splitByDay(1000, 1000, paris).isEmpty())
        assertTrue(ScreenTime.splitByDay(2000, 1000, paris).isEmpty())
    }

    @Test fun cumul_le_jour_du_passage_a_l_heure_d_ete_dure_23_heures() {
        // 2026-03-29 : 02:00 -> 03:00 en France, la journée locale ne dure que 23 h réelles.
        val from = ms("2026-03-29T00:00"); val to = ms("2026-03-30T00:00")
        assertEquals(23 * 60 * min, to - from)
        assertEquals(mapOf("2026-03-29" to 23 * 60 * min), ScreenTime.splitByDay(from, to, paris))
        assertEquals(to, ScreenTime.nextMidnight(from, paris))
    }

    @Test fun cumul_le_jour_du_retour_a_l_heure_d_hiver_dure_25_heures() {
        // 2026-10-25 : 03:00 -> 02:00.
        val from = ms("2026-10-25T00:00"); val to = ms("2026-10-26T00:00")
        assertEquals(25 * 60 * min, to - from)
        assertEquals(mapOf("2026-10-25" to 25 * 60 * min), ScreenTime.splitByDay(from, to, paris))
    }

    @Test fun fin_de_plage_correcte_apres_le_changement_d_heure() {
        // Dimanche 29 mars 2026 (passage à l'heure d'été) : plage 00:00-23:59 heure du mur, durée réelle calculée sur les instants.
        val r = ScreenTimeRules(days = mapOf(7 to listOf(w(0, 1440 - 1))))
        val until = ScreenTime.allowedUntil(r, ms("2026-03-29T08:00"), paris)!!
        assertEquals(ms("2026-03-29T23:59"), until)
        // 08:00 CEST (06:00Z) -> 23:59 CEST (21:59Z) : 15 h 59 réelles.
        assertEquals(15 * 3600L + 59 * 60, (until - ms("2026-03-29T08:00")) / 1000)
    }

    @Test fun plage_dans_le_trou_d_heure_d_ete_ne_plante_pas() {
        // 02:30 n'existe pas le 29 mars : l'instant est décalé à 03:30, aucune exception.
        val r = ScreenTimeRules(days = mapOf(7 to listOf(w(150, 200))))
        val next = ScreenTime.nextAllowed(r, ms("2026-03-29T00:30"), paris)
        assertNotNull(next)
        assertEquals(ms("2026-03-29T03:30"), next)
    }

    @Test fun jour_local_et_jour_de_la_semaine_suivent_le_fuseau() {
        val t = ms("2026-11-16T23:30", ZoneId.of("UTC"))
        assertEquals("2026-11-16", ScreenTime.dayKey(t, ZoneId.of("UTC")))
        assertEquals("2026-11-17", ScreenTime.dayKey(t, ZoneId.of("Asia/Tokyo")))
        assertEquals(2, ScreenTime.dayOfWeek(t, ZoneId.of("Asia/Tokyo")))
        assertEquals(1, ScreenTime.dayOfWeek(t, ZoneId.of("UTC")))
        assertEquals(23 * 60 + 30, ScreenTime.minuteOfDay(t, ZoneId.of("UTC")))
    }

    @Test fun usage_ajoute_prune_et_json() {
        var u = ScreenTime.addUsage(emptyMap(), ms("2026-11-16T10:00"), ms("2026-11-16T10:30"), paris)
        u = ScreenTime.addUsage(u, ms("2026-11-16T11:00"), ms("2026-11-16T11:10"), paris)
        assertEquals(40 * 60L, ScreenTime.usedToday(u, ms("2026-11-16T18:00"), paris))
        assertEquals(0L, ScreenTime.usedToday(u, ms("2026-11-17T08:00"), paris))
        assertEquals(u, ScreenTime.usageFromJson(ScreenTime.usageToJson(u)))
        assertTrue(ScreenTime.usageFromJson("pas du json").isEmpty())
        val many = (1..30).associate { "2026-10-%02d".format(it) to 1L }
        val kept = ScreenTime.prune(many)
        assertEquals(ScreenTime.KEEP_DAYS, kept.size)
        assertTrue("2026-10-30" in kept && "2026-10-01" !in kept)
    }

    @Test fun credit_plafonne_et_jamais_negatif() {
        assertEquals(1000L, ScreenTime.creditMs(0, 1000))
        assertEquals(5000L, ScreenTime.creditMs(0, 600_000))
        assertEquals(0L, ScreenTime.creditMs(5000, 1000))
    }
}
