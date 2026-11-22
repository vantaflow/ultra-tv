package com.ultratv.tv.nativeapp.ui.components

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sélecteur de source du menu latéral : quand il apparaît, quelles sources, laquelle est active. */
class SourceSwitchTest {
    private fun p(id: Long, name: String, kind: String = "XTREAM", active: Boolean = false) =
        ProviderEntity(id = id, name = name, kind = kind, baseUrl = "http://h$id", username = "u", password = "p", active = active)

    @Test fun uneSeuleSource_pasDEntree() {
        assertFalse(SourceSwitch.shown(SourceSwitch.choices(listOf(p(1, "A", active = true)), null)))
    }

    @Test fun deuxSources_entreeAvecLaSourceActive() {
        val c = SourceSwitch.choices(listOf(p(1, "A"), p(2, "B", active = true)), null)
        assertTrue(SourceSwitch.shown(c))
        assertEquals(listOf(1L to false, 2L to true), c.map { it.id to it.active })
    }

    @Test fun bibliothequesEtNas_exclus() {
        val c = SourceSwitch.choices(listOf(p(1, "A", active = true), p(2, "Jelly", kind = "JELLYFIN"), p(3, "Nas", kind = "NAS")), null)
        assertEquals(listOf(1L), c.map { it.id })
        assertFalse(SourceSwitch.shown(c))
    }

    @Test fun sansSourceActive_laPremiereEstActive() {
        assertEquals(listOf(true, false), SourceSwitch.choices(listOf(p(1, "A"), p(2, "B")), null).map { it.active })
    }
}
