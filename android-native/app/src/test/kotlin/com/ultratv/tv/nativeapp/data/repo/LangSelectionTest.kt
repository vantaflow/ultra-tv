package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règles des langues masquées : ce qui est chargé, ce qui est purgé, ce qui redemande une synchro. */
class LangSelectionTest {
    private fun sel(csv: String, multi: Boolean = true, unknown: Boolean = true) = LangSelection.of(csv, multi, unknown)

    @Test fun aucuneSelection_toutEstCharge() {
        val all = LangSelection()
        assertTrue(all.keepsItem("tr", "tr")); assertTrue(all.keepsItem("", null)); assertTrue(all.keepsItem("MULTI", ""))
    }

    @Test fun langueMasquee_titreSaute_langueChoisie_gardee() {
        val fr = sel("fr")
        assertTrue(fr.keepsItem("fr", "fr"))
        assertFalse(fr.keepsItem("tr", "fr"))        // « [TR] » dans une catégorie française : sauté
        assertFalse(fr.keepsItem("tr", ""))          // catégorie sans langue, titre turc : sauté
        assertFalse(fr.keepsItem("tr", null))        // sans catégorie
    }

    @Test fun titresSansLangueDetectee_conserves() {
        assertTrue(sel("fr").keepsItem("", ""))
        assertTrue(sel("fr").keepsItem("", "fr"))
        assertTrue(sel("fr").keepsItem("", null))
        assertFalse(sel("fr", unknown = false).keepsItem("", null))   // sauf si l'utilisateur les a retirés
        assertTrue(sel("fr").keepsItem(LanguageDetector.MULTI, ""))
        assertFalse(sel("fr", multi = false).keepsItem(LanguageDetector.MULTI, ""))
    }

    @Test fun categorieMasqueeMaisReactiveeALaMain_chargeeEnEntier() {
        assertTrue(sel("fr").keepsItem("tr", "tr"))
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
