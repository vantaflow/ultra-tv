package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CatCountRow
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import com.ultratv.tv.nativeapp.data.profile.ProfilePrefs
import com.ultratv.tv.nativeapp.ui.player.zap.ZapLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Numérotation des chaînes (automatique / source / continue), tri et saisie d'un numéro à la télécommande. */
class ChannelNumberModesTest {
    private fun ch(id: Long, cat: String?, seq: Int, num: Int, pid: Long = 1) =
        ChannelEntity(id = id, providerId = pid, remoteId = "r$id", name = "n$id", logo = null, categoryId = cat, streamUrl = "x", seq = seq, num = num)

    // « a » : 3 chaînes, « b » : 2 chaînes, « c » : 4 chaînes (dans cet ordre de catégories).
    private val rows = listOf(CatCountRow(1, "a", 3), CatCountRow(1, "b", 2), CatCountRow(1, "c", 4))
    private val a1 = ch(1, "a", 1, 101)
    private val a3 = ch(3, "a", 3, 103)
    private val b1 = ch(4, "b", 1, 201)
    private val b2 = ch(5, "b", 2, 202)
    private val c1 = ch(6, "c", 1, 301)

    private fun mode(m: ChannelNumberMode, hidden: List<Triple<Long, String?, Int>> = emptyList(), counts: List<CatCountRow> = rows, hiddenCats: Set<String> = emptySet()) =
        ChannelNumbering.of(hidden, m, ChannelNumbering.spansOf(counts, hiddenCats))

    @Test fun automatique_estLeComportementActuel() {
        val n = mode(ChannelNumberMode.AUTO)
        assertTrue(n.isIdentity)
        assertEquals(1, n.numberOf(a1)); assertEquals(3, n.numberOf(a3)); assertEquals(2, n.numberOf(b2))
        assertEquals(77, n.numberOf(ch(9, "a", 0, 77)))   // sans seq : numéro de la source
        assertEquals(ChannelNumbering.NONE.numberOf(b2), n.numberOf(b2))
    }

    @Test fun source_afficheLeNumeroDuFournisseur() {
        val n = mode(ChannelNumberMode.SOURCE)
        assertFalse(n.isIdentity)
        assertEquals(101, n.numberOf(a1)); assertEquals(202, n.numberOf(b2))
        assertEquals(0, n.numberOf(ch(9, "a", 4, 0)))     // pas de numéro fourni : inconnu
    }

    @Test fun source_ignoreLesChainesMasquees() {
        val n = mode(ChannelNumberMode.SOURCE, hidden = listOf(Triple(1L, "a", 1)))
        assertEquals(103, n.numberOf(a3))   // le numéro du fournisseur ne se décale jamais
    }

    @Test fun continue_numerotePar_dessusLesCategories() {
        val n = mode(ChannelNumberMode.CONTINUOUS)
        assertEquals(1, n.numberOf(a1)); assertEquals(3, n.numberOf(a3))
        assertEquals(4, n.numberOf(b1)); assertEquals(5, n.numberOf(b2))
        assertEquals(6, n.numberOf(c1))
    }

    @Test fun continue_chaineMasqueeRacourcitSaCategorieEtDecaleLesSuivantes() {
        // Le profil masque a1 : « a » compte 2 chaînes visibles (la requête de comptage les exclut déjà).
        val n = mode(ChannelNumberMode.CONTINUOUS, hidden = listOf(Triple(1L, "a", 1)), counts = listOf(CatCountRow(1, "a", 2), CatCountRow(1, "b", 2), CatCountRow(1, "c", 4)))
        assertEquals(2, n.numberOf(a3))   // seq 3 -> 2e de « a »
        assertEquals(3, n.numberOf(b1)); assertEquals(5, n.numberOf(c1))
    }

    @Test fun continue_categorieMasqueeEstSautee() {
        val n = mode(ChannelNumberMode.CONTINUOUS, hiddenCats = setOf("LIVE:1:b"))
        assertEquals(4, n.numberOf(c1))   // « b » (2 chaînes) retirée de la suite
    }

    @Test fun continue_sourcesIndependantes() {
        val n = mode(ChannelNumberMode.CONTINUOUS, counts = rows + CatCountRow(2, "a", 5))
        assertEquals(1, n.numberOf(ch(20, "a", 1, 0, pid = 2)))
        assertEquals(4, n.numberOf(b1))
    }

    @Test fun continue_chaineSansSeqGardeSonNumeroSource() {
        assertEquals(88, mode(ChannelNumberMode.CONTINUOUS).numberOf(ch(9, "a", 0, 88)))
    }

    @Test fun continue_spansIgnoreVidesEtSeqZero() {
        val s = ChannelNumbering.spansOf(listOf(CatCountRow(1, "a", 0), CatCountRow(1, "b", 2), CatCountRow(1, null, 1)))
        assertEquals(listOf(CategorySpan("b", 0, 2), CategorySpan(null, 2, 1)), s[1L])
    }

    @Test fun continue_retrouveLaChaineParSonNumeroSaisi() {
        val n = mode(ChannelNumberMode.CONTINUOUS)
        assertEquals("a" to 2, n.locateContinuous(1, 2))
        assertEquals("b" to 1, n.locateContinuous(1, 4))
        assertEquals("c" to 4, n.locateContinuous(1, 9))
        assertNull(n.locateContinuous(1, 10)); assertNull(n.locateContinuous(1, 0)); assertNull(n.locateContinuous(2, 1))
        assertNull(mode(ChannelNumberMode.AUTO).locateContinuous(1, 2))
    }

    @Test fun continue_numeroSaisiEtNumeroAfficheConcordent() {
        val hidden = listOf(Triple(1L, "b", 1))
        val n = mode(ChannelNumberMode.CONTINUOUS, hidden = hidden, counts = listOf(CatCountRow(1, "a", 3), CatCountRow(1, "b", 1), CatCountRow(1, "c", 4)))
        // b2 est la 1re visible de « b » : numéro 4 ; la saisie de 4 doit viser (« b », rang 1) puis le seq 2.
        assertEquals(4, n.numberOf(b2))
        val (cat, inCat) = n.locateContinuous(1, 4)!!
        assertEquals(2, n.seqOf(1, cat, inCat))
    }

    // ── Saisie à la télécommande : ZapLogic.byNumber suit la numérotation choisie ──

    @Test fun saisie_modeSource_trouveLeNumeroDuFournisseur() {
        val list = listOf(a1, a3, b1)
        assertEquals(2, ZapLogic.byNumber(list, 201, mode(ChannelNumberMode.SOURCE)))
        assertNull(ZapLogic.byNumber(list, 2, mode(ChannelNumberMode.SOURCE)))
    }

    @Test fun saisie_modeContinu_trouveLeNumeroContinu() {
        val list = listOf(a1, a3, b1, b2, c1)
        val n = mode(ChannelNumberMode.CONTINUOUS)
        assertEquals(1, ZapLogic.byNumber(list, 3, n))   // a3
        assertEquals(4, ZapLogic.byNumber(list, 6, n))   // c1
    }

    @Test fun saisie_modeAutomatique_inchangee() {
        val list = listOf(a1, a3, b1)
        assertEquals(1, ZapLogic.byNumber(list, 3, mode(ChannelNumberMode.AUTO)))
        assertEquals(0, ZapLogic.byNumber(list, 1, ChannelNumbering.NONE))   // a1 (seq 1)
    }

    @Test fun saisie_sautePasLesSeparateursNiLesMasquees() {
        val sep = ChannelEntity(id = 50, providerId = 1, remoteId = "s", name = "##### X #####", logo = null, categoryId = "a", streamUrl = "x", seq = 1, num = 101)
        assertEquals(1, ZapLogic.byNumber(listOf(sep, a1), 101, mode(ChannelNumberMode.SOURCE)))   // séparateur ignoré, a1 trouvée
    }

    // ── Clés de réglage ──

    @Test fun cles_inconnuesOuAbsentesRetombentSurLeDefaut() {
        assertEquals(ChannelNumberMode.AUTO, ChannelNumberMode.parse(null))
        assertEquals(ChannelNumberMode.AUTO, ChannelNumberMode.parse("zz"))
        assertEquals(ChannelNumberMode.CONTINUOUS, ChannelNumberMode.parse("continuous"))
        assertEquals(ChannelSort.SOURCE, ChannelSort.parse(null))
        assertEquals(ChannelSort.AZ, ChannelSort.parse("az"))
        assertEquals(ChannelNumberMode.entries.size, ChannelNumberMode.entries.map { it.key }.toSet().size)
        assertEquals(ChannelSort.entries.size, ChannelSort.entries.map { it.key }.toSet().size)
    }

    @Test fun tri_sourceNEstPasActif_lesAutresSont() {
        assertFalse(ChannelOrderRepository.SortSettings(ChannelSort.SOURCE).active)
        assertEquals(0, ChannelSort.SOURCE.sql)
        for (s in listOf(ChannelSort.NUMBER, ChannelSort.AZ, ChannelSort.RECENT)) assertTrue(ChannelOrderRepository.SortSettings(s).active)
        assertEquals(setOf(0, 1, 2, 3), ChannelSort.entries.map { it.sql }.toSet())
    }

    @Test fun reglages_defautNeChangeRien_etSurchargeParProfil() {
        val d = UserPrefs()
        assertEquals("auto", d.channelNumbering); assertEquals("source", d.channelSort)
        val vide = ProfilePrefs().applyTo(d)
        assertEquals("auto", vide.channelNumbering); assertEquals("source", vide.channelSort)
        val p = ProfilePrefs.fromMap(mapOf(ProfilePrefs.CHANNEL_NUMBERING to "continuous", ProfilePrefs.CHANNEL_SORT to "recent")).applyTo(d)
        assertEquals("continuous", p.channelNumbering); assertEquals("recent", p.channelSort)
        assertNull(ProfilePrefs.fromMap(emptyMap()).channelSort)
    }

    @Test fun fenetre_centreeSurLaChaine_etBornee() {
        val list = (1L..1000L).map { ch(it, "a", it.toInt(), it.toInt()) }
        val w = ChannelOrderRepository.window(list, list[500], 401)
        assertEquals(401, w.size); assertTrue(list[500] in w); assertEquals(301L, w.first().id)
        assertEquals(1L, ChannelOrderRepository.window(list, list[2], 401).first().id)
        assertEquals(1000L, ChannelOrderRepository.window(list, list[999], 401).last().id)
        assertEquals(1L, ChannelOrderRepository.window(list, null, 401).first().id)
        assertEquals(3, ChannelOrderRepository.window(list.take(3), list[1], 401).size)
        assertEquals(0, ChannelOrderRepository.window(emptyList(), null, 401).size)
    }
}
