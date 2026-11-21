package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.data.db.EpgEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GuideProgrammeCacheTest {
    private val h = 3_600_000L
    private fun e(id: Long, ch: Long, s: Long, en: Long) = EpgEntity(id = id, channelId = ch, title = "p$id", description = null, startMs = s * h, endMs = en * h)

    @Test fun missing_tout_au_depart_puis_rien_apres_le_chargement() {
        val c = GuideProgrammeCache()
        assertEquals(listOf(1L, 2L), c.missing(listOf(1, 2), 0, 10 * h))
        c.put(listOf(1, 2), 0, 10 * h, listOf(e(1, 1, 0, 2), e(2, 2, 0, 3)))
        assertEquals(emptyList<Long>(), c.missing(listOf(1, 2), 2 * h, 9 * h))
    }

    @Test fun un_decalage_dans_la_plage_chargee_ne_demande_rien_et_ne_republie_pas() {
        val c = GuideProgrammeCache()
        c.put(listOf(1), 0, 15 * h, listOf(e(1, 1, 0, 2), e(2, 1, 2, 4)))
        val before = c.snapshot().getValue(1)
        // Le décalage de 90 min ne sort pas de la plage : plus rien à lire.
        assertEquals(emptyList<Long>(), c.missing(listOf(1), 1 * h, 12 * h))
        assertSame(before, c.snapshot().getValue(1))
    }

    @Test fun une_plage_voisine_etend_l_entree_et_garde_les_programmes_deja_la() {
        val c = GuideProgrammeCache()
        c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 2), e(2, 1, 2, 4)))
        assertEquals(listOf(1L), c.missing(listOf(1), 3 * h, 9 * h))
        assertTrue(c.put(listOf(1), 3 * h, 9 * h, listOf(e(2, 1, 2, 4), e(3, 1, 4, 7))))
        assertEquals(listOf(1L, 2L, 3L), c.snapshot().getValue(1).map { it.id })
        assertEquals(emptyList<Long>(), c.missing(listOf(1), 0, 9 * h))
    }

    @Test fun une_extension_qui_relit_un_programme_reinsere_le_remplace_par_son_debut() {
        val c = GuideProgrammeCache()
        c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 2), e(2, 1, 2, 4)))
        // Synchro en cours : le programme de 2 h a été réinséré (nouvel identifiant, même début).
        c.put(listOf(1), 3 * h, 9 * h, listOf(e(20, 1, 2, 4), e(3, 1, 4, 7)))
        assertEquals("un seul programme par début, le plus récent", listOf(1L, 20L, 3L), c.snapshot().getValue(1).map { it.id })
    }

    @Test fun une_plage_disjointe_remplace_au_lieu_de_declarer_le_trou_charge() {
        val c = GuideProgrammeCache()
        c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 2)))
        c.put(listOf(1), 40 * h, 46 * h, listOf(e(9, 1, 41, 43)))
        assertEquals(listOf(9L), c.snapshot().getValue(1).map { it.id })
        assertEquals("le trou entre les deux n'a jamais été lu", listOf(1L), c.missing(listOf(1), 10 * h, 20 * h))
    }

    @Test fun un_chargement_identique_garde_la_meme_liste_et_ne_republie_pas() {
        val c = GuideProgrammeCache()
        c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 2), e(2, 1, 2, 4)))
        val before = c.snapshot().getValue(1)
        assertFalse(c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 2), e(2, 1, 2, 4))))
        assertSame("même contenu : même instance, la ligne n'est pas recomposée", before, c.snapshot().getValue(1))
    }

    @Test fun les_autres_chaines_gardent_leur_liste_quand_une_chaine_arrive() {
        val c = GuideProgrammeCache()
        c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 2)))
        val one = c.snapshot().getValue(1)
        c.put(listOf(2), 0, 6 * h, listOf(e(5, 2, 0, 3)))
        assertSame(one, c.snapshot().getValue(1))
        assertEquals(setOf(1L, 2L), c.snapshot().keys)
    }

    @Test fun une_chaine_sans_programme_est_publiee_une_fois_comme_lue_et_vide() {
        val c = GuideProgrammeCache()
        assertTrue("première lecture : publiée (« lue, rien à cette heure »)", c.put(listOf(7), 0, 6 * h, emptyList()))
        assertEquals(emptyList<Long>(), c.missing(listOf(7), 0, 6 * h))
        assertEquals(mapOf(7L to emptyList<EpgEntity>()), c.snapshot())
        c.invalidate()
        assertFalse("relue toujours vide : rien à republier", c.put(listOf(7), 0, 6 * h, emptyList()))
    }

    @Test fun invalidate_marque_a_recharger_sans_effacer_les_listes_affichees() {
        val c = GuideProgrammeCache()
        c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 2)))
        val shown = c.snapshot().getValue(1)
        c.invalidate()
        assertSame("les lignes gardent leur liste jusqu'au remplacement", shown, c.snapshot().getValue(1))
        assertEquals(listOf(1L), c.missing(listOf(1), 0, 6 * h))
        assertTrue(c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 2), e(2, 1, 2, 3))))
        assertEquals(listOf(1L, 2L), c.snapshot().getValue(1).map { it.id })
    }

    @Test fun au_plus_maxChannels_chaines_les_moins_recentes_sortent() {
        val c = GuideProgrammeCache(maxChannels = 3)
        for (id in 1L..3L) c.put(listOf(id), 0, 6 * h, listOf(e(id, id, 0, 1)))
        c.missing(listOf(1), 0, 6 * h)   // la chaîne 1 redevient récente
        c.put(listOf(4), 0, 6 * h, listOf(e(4, 4, 0, 1)))
        assertEquals(setOf(1L, 3L, 4L), c.snapshot().keys)
    }

    @Test fun chevauchements_normalises_comme_avant() {
        val c = GuideProgrammeCache()
        c.put(listOf(1), 0, 6 * h, listOf(e(1, 1, 0, 3), e(2, 1, 1, 4)))
        val rows = c.snapshot().getValue(1)
        assertEquals(listOf(0L to 3 * h, 3 * h to 4 * h), rows.map { it.startMs to it.endMs })
    }

    // ── Plages ──

    private fun ms(day: Int, hour: Int, min: Int = 0) = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, hour, min, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun plage_des_chaines_stable_toute_la_journee_donc_aucune_reconstruction_au_decalage() {
        val first = guideChannelRange(ms(21, 0, 30))
        for (hh in 0..23) for (mm in listOf(0, 30)) assertEquals("$hh:$mm", first, guideChannelRange(ms(21, hh, mm)))
        assertNotSame(first, guideChannelRange(ms(22, 0, 0)))
        assertTrue(first != guideChannelRange(ms(22, 0, 0)))
    }

    @Test fun plage_des_chaines_contient_toute_fenetre_qui_commence_ce_jour_la_meme_apres_minuit() {
        for (hh in 0..23) for (mm in listOf(0, 30)) {
            val w = ms(21, hh, mm)
            val (from, to) = guideChannelRange(w)
            assertTrue(from <= w); assertTrue(w + GUIDE_WINDOW_MS <= to)
        }
    }

    @Test fun plages_de_programmes_la_plage_utile_tient_dans_la_plage_lue_pour_quatre_decalages_de_suite() {
        val w = ms(21, 1, 0)
        val (nf, nt) = guideNeededRange(w)
        val (ff, ft) = guideFetchRange(w)
        assertTrue(ff <= nf && ft >= nt)
        // Quatre décalages de 90 min dans un sens ou l'autre restent dans ce qui a été lu.
        for (k in -2..2) { val (a, b) = guideNeededRange(w + k * 90 * 60_000L); assertTrue("décalage $k", ff <= a && b <= ft) }
    }
}
