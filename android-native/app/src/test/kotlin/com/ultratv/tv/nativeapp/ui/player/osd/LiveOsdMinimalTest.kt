package com.ultratv.tv.nativeapp.ui.player.osd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Barre minimale du Direct tactile : titre et sous-titre affichés (#171). */
class LiveOsdMinimalTest {
    private fun state(programme: OsdProgrammeInfo?, number: Int? = 7) = LiveOsdState(
        channel = OsdChannelInfo(null, number, "Chaîne Test"), programme = programme, badge = OsdBadge("EN DIRECT", false),
        clock = "20:00", actions = emptyList(), recents = emptyList(), recentsTitle = "",
    )

    @Test fun avecProgramme_titreEstLeProgramme_sousTitreLaChaine() {
        val s = state(OsdProgrammeInfo("Le Journal", null, null, null, null))
        assertEquals("Le Journal", minimalTitle(s))
        assertEquals("7 · Chaîne Test", minimalSubtitle(s))
    }

    @Test fun sansProgramme_titreEstLaChaine_pasDeSousTitre() {
        val s = state(null)
        assertEquals("Chaîne Test", minimalTitle(s))
        assertNull(minimalSubtitle(s))
    }

    @Test fun numerosMasques_sousTitreSansNumero() {
        val s = state(OsdProgrammeInfo("Le Journal", null, null, null, null), number = null)
        assertEquals("Chaîne Test", minimalSubtitle(s))
    }
}
