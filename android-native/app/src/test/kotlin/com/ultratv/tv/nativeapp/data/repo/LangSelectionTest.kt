package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règles des langues masquées : ce qui est chargé, ce qui est purgé, ce qui redemande une synchro. */
class LangSelectionTest {
    private fun sel(csv: String, multi: Boolean = true, unknown: Boolean = true) = LangSelection.of(csv, multi, unknown)

    @Test fun filtreParCategorie_allows() {
        val fr = sel("fr")
        assertTrue(fr.allows("fr")); assertFalse(fr.allows("tr"))
        assertTrue(fr.allows("")); assertTrue(fr.allows(LanguageDetector.MULTI))
        assertFalse(sel("fr", unknown = false).allows("")); assertFalse(sel("fr", multi = false).allows(LanguageDetector.MULTI))
        assertTrue(LangSelection().allows("tr") && LangSelection().all)
    }

    @Test fun masquer_purge_reactiver_resynchronise() {
        val fr = sel("fr"); val frAr = sel("fr,ar"); val all = LangSelection()
        assertTrue(LangSelection.hides(frAr, fr)); assertFalse(LangSelection.reveals(frAr, fr))
        assertTrue(LangSelection.reveals(fr, frAr)); assertFalse(LangSelection.hides(fr, frAr))
        assertTrue(LangSelection.reveals(fr, all)); assertFalse(LangSelection.hides(fr, all))
        assertTrue(LangSelection.hides(all, fr)); assertFalse(LangSelection.reveals(all, fr))
        assertTrue(LangSelection.hides(fr, sel("fr", unknown = false))); assertTrue(LangSelection.reveals(sel("fr", unknown = false), fr))
        assertTrue(LangSelection.hides(fr, sel("fr", multi = false)))
        assertFalse(LangSelection.hides(fr, fr)); assertFalse(LangSelection.reveals(fr, fr))
        // Échange fr → ar : les deux à la fois.
        assertTrue(LangSelection.hides(fr, sel("ar")) && LangSelection.reveals(fr, sel("ar")))
        assertEquals(setOf("fr", "ar"), sel(" FR, ar ,").selected)
    }
}
