package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.CategoryMaxRow
import com.ultratv.tv.nativeapp.data.db.ManualChannelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fusion / réinjection des chaînes manuelles : logique pure, sans base. */
class ManualChannelsTest {
    private fun cat(id: String, enabled: Boolean = true) = CategoryEntity(providerId = 1, kind = "LIVE", remoteId = id, name = "Cat $id", enabled = enabled)
    private fun man(id: String, cat: String, at: Long = 0, name: String = "M$id") =
        ManualChannelEntity(1, "custom:$id", name, "http://x.invalid/$id", null, null, cat, null, at)

    @Test fun apresLesChainesDuFournisseur_numerosContinus() {
        val p = ManualChannels.plan(1, listOf(man("a", "9", 1), man("b", "9", 2)), listOf(cat("9")), listOf(CategoryMaxRow("9", 40, 12)), emptyMap())
        assertEquals(listOf(41, 42), p.channels.map { it.num })
        assertEquals(listOf(13, 14), p.channels.map { it.seq })
        assertNull(p.categoryToAdd)
    }

    @Test fun ordreDeCreation_puisIdentifiantConserve() {
        val p = ManualChannels.plan(1, listOf(man("b", "9", 5), man("a", "9", 1)), listOf(cat("9")), emptyList(), mapOf("custom:a" to 77L))
        assertEquals(listOf("custom:a", "custom:b"), p.channels.map { it.remoteId })
        assertEquals(77L, p.channels[0].id)
        assertEquals(0L, p.channels[1].id)
    }

    @Test fun categorieDedieeCreeeUneSeuleFois_nomConserve() {
        val m = listOf(man("a", ManualChannels.CUSTOM_CAT).copy(categoryName = "Mes chaînes"), man("b", ManualChannels.CUSTOM_CAT, 1))
        val p = ManualChannels.plan(1, m, emptyList(), emptyList(), emptyMap())
        assertEquals(ManualChannels.CUSTOM_CAT, p.categoryToAdd?.remoteId)
        assertEquals("Mes chaînes", p.categoryToAdd?.name)
        assertEquals(listOf(1, 2), p.channels.map { it.seq })
        // Déjà présente : pas recréée.
        assertNull(ManualChannels.plan(1, m, listOf(cat(ManualChannels.CUSTOM_CAT)), emptyList(), emptyMap()).categoryToAdd)
    }

    @Test fun categorieDisparueDuServeur_basculeDansMesChaines() {
        val p = ManualChannels.plan(1, listOf(man("a", "inconnue")), listOf(cat("9")), emptyList(), emptyMap())
        assertEquals(ManualChannels.CUSTOM_CAT, p.channels.single().categoryId)
        assertNotNull(p.categoryToAdd)
    }

    @Test fun categorieDesactivee_chaineSautee() {
        val p = ManualChannels.plan(1, listOf(man("a", "9")), listOf(cat("9", enabled = false)), emptyList(), emptyMap())
        assertTrue(p.channels.isEmpty())
    }

    @Test fun nomNumeriqueOuSeparateur_resteAffiche() {
        val p = ManualChannels.plan(1, listOf(man("a", "9", name = "2"), man("b", "9", 1, name = "##### X #####")), listOf(cat("9")), emptyList(), emptyMap())
        assertTrue(p.channels.all { !it.junk && !it.isSeparator })
    }

    @Test fun aucuneChaine_rienAFaire() {
        val p = ManualChannels.plan(1, emptyList(), listOf(cat("9")), emptyList(), emptyMap())
        assertTrue(p.channels.isEmpty()); assertNull(p.categoryToAdd)
    }

    @Test fun validationAdresses() {
        assertTrue(ManualChannels.isValidStreamUrl("http://hote.tld:8080/live/u/p/1.ts"))
        assertTrue(ManualChannels.isValidStreamUrl(" rtmp://hote.tld/app/x "))
        assertFalse(ManualChannels.isValidStreamUrl("hote.tld/x"))
        assertFalse(ManualChannels.isValidStreamUrl("http://"))
        assertFalse(ManualChannels.isValidStreamUrl("javascript:alert(1)"))
        assertTrue(ManualChannels.isValidLogoUrl(""))
        assertTrue(ManualChannels.isValidLogoUrl("https://h.tld/l.png"))
        assertFalse(ManualChannels.isValidLogoUrl("ftp://h.tld/l.png"))
        assertTrue(ManualChannels.isManual(ManualChannels.newRemoteId()))
    }
}
