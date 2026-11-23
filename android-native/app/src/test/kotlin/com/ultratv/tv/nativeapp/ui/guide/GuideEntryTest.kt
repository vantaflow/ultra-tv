package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Ouverture du Guide (constat box 1.3.73-rc8) : modèle de vue gardé par la navigation du menu (restoreState), la fenêtre de
 * 20:30 calculée plus tôt dans la soirée revenait à 23:54, focus sur un programme terminé. À l'entrée : fenêtre qui contient
 * l'heure actuelle, focus sur le programme EN COURS ; jours suivants calés sur 20:00 dans le fuseau de l'appli (EpgClock).
 */
class GuideEntryTest {
    private val h = 3_600_000L
    private val m = 60_000L
    private fun e(s: Long, en: Long) = EpgEntity(channelId = 1, title = "p", description = null, startMs = s, endMs = en)

    private val defaultTz = TimeZone.getDefault()
    @After fun restore() { TimeZone.setDefault(defaultTz); EpgClock.zoneId = "" }

    @Test fun entree_fenetrePerimeeDeLaSoiree_revientALaDemiHeureCourante() {
        val now = 23 * h + 54 * m
        assertEquals(23 * h + 30 * m, guideEntryWindow(current = 20 * h + 30 * m, nowMs = now))
    }

    @Test fun entree_fenetreQuiContientDejaMaintenant_gardee() {
        val now = 23 * h + 54 * m
        assertEquals(22 * h + 30 * m, guideEntryWindow(current = 22 * h + 30 * m, nowMs = now))
    }

    @Test fun entree_fenetreDansLAvenir_revientAMaintenant() {
        val now = 18 * h + 10 * m
        assertEquals(18 * h, guideEntryWindow(current = 21 * h, nowMs = now))
    }

    @Test fun focusInitial_programmeEnCours_pasLeTermineAfficheEnPremier() {
        // Fenêtre 23:30 : 23:00–23:40 (terminé à 23:54) affiché en premier, 23:40–01:00 en cours.
        val progs = listOf(e(23 * h, 23 * h + 40 * m), e(23 * h + 40 * m, 25 * h))
        val w = 23 * h + 30 * m
        val slots = progs.map { slotFor(it.startMs, it.endMs, w) }
        assertEquals(1, guideEntryIndex(progs, slots, firstShown = 0, nowMs = 23 * h + 54 * m))
    }

    @Test fun focusInitial_maintenantHorsFenetre_premiereCaseAffichee() {
        val progs = listOf(e(20 * h, 21 * h), e(21 * h, 22 * h))
        val w = 20 * h
        val slots = progs.map { slotFor(it.startMs, it.endMs, w) }
        assertEquals(0, guideEntryIndex(progs, slots, firstShown = 0, nowMs = 47 * h))
    }

    @Test fun focusInitial_trouAMaintenant_premiereCaseAffichee() {
        val progs = listOf(e(23 * h, 23 * h + 40 * m), e(24 * h + 10 * m, 25 * h))
        val w = 23 * h + 30 * m
        val slots = progs.map { slotFor(it.startMs, it.endMs, w) }
        assertEquals(0, guideEntryIndex(progs, slots, firstShown = 0, nowMs = 23 * h + 54 * m))
    }

    @Test fun jourSuivant_20h_dansLeFuseauDeLAppli_pasCeluiDuSysteme() {
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Casablanca"))
        EpgClock.zoneId = "GMT"
        val gmt = TimeZone.getTimeZone("GMT")
        val now = Calendar.getInstance(gmt).apply { clear(); set(2026, Calendar.NOVEMBER, 22, 23, 54) }.timeInMillis
        val c = Calendar.getInstance(gmt).apply { timeInMillis = guideWindowStart(now, 1) }
        assertEquals(23, c.get(Calendar.DAY_OF_MONTH))
        assertEquals("fenêtre de demain : 20:00 à l'heure affichée (GMT), comme la ligne « maintenant »", 20, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, c.get(Calendar.MINUTE))
    }
}
