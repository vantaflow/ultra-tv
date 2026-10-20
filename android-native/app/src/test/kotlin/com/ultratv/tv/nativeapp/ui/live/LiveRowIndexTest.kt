package com.ultratv.tv.nativeapp.ui.live

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRowIndexTest {
    private var nextId = 1L
    private fun ch(title: String, quality: Int = 2, flags: Int = 0) = ChannelEntity(
        id = nextId++, providerId = 1, remoteId = "r$nextId", name = title, logo = null, categoryId = "10", streamUrl = "http://x/$nextId",
        title = title, isSeparator = false, quality = quality, flags = flags, junk = false,
    )
    private fun sep(title: String) = ChannelEntity(
        id = nextId++, providerId = 1, remoteId = "s$nextId", name = "##### $title #####", logo = null, categoryId = "10", streamUrl = "",
        title = title, isSeparator = true, quality = 0, flags = 0, junk = false,
    )

    /** Référence : les boucles d'origine de LiveScreen (remontée par peek), pour vérifier l'équivalence. */
    private fun oldSticky(items: List<ChannelEntity?>, first: Int): String? {
        var i = first; var label: String? = null
        while (i >= 0 && label == null) { if (i < items.size) items[i]?.takeIf { it.isSeparator }?.let { label = it.title }; i-- }
        return label
    }
    private fun oldVariant(items: List<ChannelEntity?>, i: Int): Int {
        val c = items[i] ?: return 1
        var n = 1; var j = i - 1
        while (j >= 0 && n < 9) { val p = items[j] ?: break; if (!sameStream(p, c)) break; n++; j-- }
        return n
    }

    @Test
    fun sectionLabelAt_sansSeparateur_estNul() {
        val idx = LiveRowIndex.build(List(500) { ch("C$it") })
        assertNull(idx.sectionLabelAt(0))
        assertNull(idx.sectionLabelAt(499))
    }

    @Test
    fun sectionLabelAt_dernierSeparateurAuDessus_estRetenu() {
        val items = listOf(sep("Sport"), ch("A"), ch("B"), sep("Info"), ch("C"))
        val idx = LiveRowIndex.build(items)
        assertEquals("Sport", idx.sectionLabelAt(0))
        assertEquals("Sport", idx.sectionLabelAt(2))
        assertEquals("Info", idx.sectionLabelAt(3))
        assertEquals("Info", idx.sectionLabelAt(4))
    }

    @Test
    fun sectionLabelAt_auDelaDeLaListeChargee_prendLaDerniereLigne() {
        val idx = LiveRowIndex.build(listOf(sep("Sport"), ch("A")))
        assertEquals("Sport", idx.sectionLabelAt(50))
        assertNull(LiveRowIndex.build(emptyList()).sectionLabelAt(0))
        assertNull(idx.sectionLabelAt(-1))
    }

    @Test
    fun sectionLabelAt_ligneNonChargee_neCoupePasLaRecherche() {
        val idx = LiveRowIndex.build(listOf(sep("Sport"), null, ch("A")))
        assertEquals("Sport", idx.sectionLabelAt(2))
    }

    @Test
    fun variantAt_doublonsConsecutifs_numerotes() {
        val items = listOf(ch("TF1"), ch("tf1 "), ch("TF1"), ch("M6"), ch("TF1"))
        val idx = LiveRowIndex.build(items)
        assertEquals(listOf(1, 2, 3, 1, 1), items.indices.map { idx.variantAt(it) })
    }

    @Test
    fun variantAt_qualiteOuMentionsDifferentes_neSontPasDesDoublons() {
        val idx = LiveRowIndex.build(listOf(ch("TF1", quality = 2), ch("TF1", quality = 3), ch("TF1", quality = 3, flags = 1)))
        assertEquals(listOf(1, 1, 1), (0..2).map { idx.variantAt(it) })
    }

    @Test
    fun variantAt_longueSerie_plafonneeA9() {
        val idx = LiveRowIndex.build(List(15) { ch("BeIN") })
        assertEquals(9, idx.variantAt(8))
        assertEquals(9, idx.variantAt(14))
    }

    @Test
    fun variantAt_ligneNonChargeeOuSeparateur_coupeLaSerie() {
        val items = listOf(ch("A"), null, ch("A"), sep("X"), ch("A"))
        val idx = LiveRowIndex.build(items)
        assertEquals(listOf(1, 1, 1, 1, 1), items.indices.map { idx.variantAt(it) })
        assertEquals(1, idx.variantAt(99))
    }

    @Test
    fun build_listeProlongee_reutiliseEtDonneLeMemeResultat() {
        val page1 = listOf(sep("Sport"), ch("A"), ch("A"))
        val page2 = page1 + listOf(ch("A"), sep("Info"), ch("B"))
        val incr = LiveRowIndex.build(page2, LiveRowIndex.build(page1))
        val full = LiveRowIndex.build(page2)
        for (i in page2.indices) {
            assertEquals(full.variantAt(i), incr.variantAt(i))
            assertEquals(full.sectionLabelAt(i), incr.sectionLabelAt(i))
        }
        assertEquals(3, incr.variantAt(3))   // A, A, A après « Sport »
    }

    @Test
    fun build_teteDifferente_recalculeTout() {
        val old = LiveRowIndex.build(listOf(ch("A"), ch("A")))
        val fresh = listOf(sep("Info"), ch("B"), ch("B"))
        val idx = LiveRowIndex.build(fresh, old)
        assertEquals("Info", idx.sectionLabelAt(2))
        assertEquals(2, idx.variantAt(2))
    }

    @Test
    fun build_listeAleatoire_equivalenteAuxAnciennesBoucles() {
        val rnd = java.util.Random(42)
        val titles = listOf("TF1", "M6", "Arte")
        val items = List(400) {
            when (rnd.nextInt(12)) { 0 -> sep("S$it"); 1 -> null; else -> ch(titles[rnd.nextInt(3)], quality = 1 + rnd.nextInt(2)) }
        }
        val idx = LiveRowIndex.build(items, LiveRowIndex.build(items.subList(0, 150)))
        for (i in items.indices) {
            assertEquals("variant $i", oldVariant(items, i), idx.variantAt(i))
            assertEquals("section $i", oldSticky(items, i), idx.sectionLabelAt(i))
        }
        assertEquals(oldSticky(items, 1_000), idx.sectionLabelAt(1_000))
    }

    @Test
    fun visibleChannelIds_fenetreSansSeparateursNiLignesNonChargees() {
        val a = ch("A"); val b = ch("B"); val c = ch("C")
        val items = listOf(sep("S"), a, null, b, c)
        assertEquals(listOf(a.id, b.id), visibleChannelIds(items, 0, 4))
        assertEquals(listOf(b.id, c.id), visibleChannelIds(items, 3, 14))
        assertEquals(emptyList<Long>(), visibleChannelIds(items, 9, 14))
        assertEquals(emptyList<Long>(), visibleChannelIds(emptyList(), 0, 14))
    }

    @Test
    fun hasSectionOtherThan_seulementSiUneSectionDifferenteEtNonVide() {
        assertEquals(false, LiveRowIndex.build(listOf(ch("A"), ch("B"))).hasSectionOtherThan("Cat"))
        assertEquals(false, LiveRowIndex.build(listOf(sep("Cat"), ch("A"))).hasSectionOtherThan("cat "))
        assertEquals(false, LiveRowIndex.build(listOf(sep("  "), ch("A"))).hasSectionOtherThan("Cat"))
        assertEquals(true, LiveRowIndex.build(listOf(sep("Cat"), ch("A"), sep("Sport"), ch("B"))).hasSectionOtherThan("Cat"))
    }

    @Test
    fun isSeparatorAt_horsLimitesEtLignesNonChargees_faux() {
        val idx = LiveRowIndex.build(listOf(sep("S"), null, ch("A")))
        assertEquals(true, idx.isSeparatorAt(0))
        assertEquals(false, idx.isSeparatorAt(1))
        assertEquals(false, idx.isSeparatorAt(2))
        assertEquals(false, idx.isSeparatorAt(-1))
        assertEquals(false, idx.isSeparatorAt(9))
    }
}
