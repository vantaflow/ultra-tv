package com.ultratv.tv.nativeapp.data.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPublishPlanTest {
    @Test fun mode_sansChaineParDefaut_publieLaChaineParDefaut() =
        assertEquals(ChannelPublishPlan.Mode.DEFAULT, ChannelPublishPlan.mode(false))

    @Test fun mode_avecChaineParDefaut_publieUneChaineOrdinaire() =
        assertEquals(ChannelPublishPlan.Mode.NORMAL, ChannelPublishPlan.mode(true))

    @Test fun mustRecreate_chaineDisparue_vrai() = assertTrue(ChannelPublishPlan.mustRecreate(12, false))
    @Test fun mustRecreate_chaineExistante_faux() = assertFalse(ChannelPublishPlan.mustRecreate(12, true))
    @Test fun mustRecreate_jamaisCreee_faux() = assertFalse(ChannelPublishPlan.mustRecreate(-1, false))

    @Test fun mustRewrite_memeSignatureEtMemeNombre_faux() = assertFalse(ChannelPublishPlan.mustRewrite(true, 20, 20))
    @Test fun mustRewrite_signatureChangee_vrai() = assertTrue(ChannelPublishPlan.mustRewrite(false, 20, 20))
    @Test fun mustRewrite_programmesEffacesParLeSysteme_vrai() = assertTrue(ChannelPublishPlan.mustRewrite(true, 0, 20))

    @Test fun shouldAskBrowsable_chaineMasqueePremiereFois_vrai() = assertTrue(ChannelPublishPlan.shouldAskBrowsable(5, false, false))
    @Test fun shouldAskBrowsable_dejaAffichee_faux() = assertFalse(ChannelPublishPlan.shouldAskBrowsable(5, true, false))
    @Test fun shouldAskBrowsable_dejaDemande_faux() = assertFalse(ChannelPublishPlan.shouldAskBrowsable(5, false, true))
    @Test fun shouldAskBrowsable_sansChaine_faux() = assertFalse(ChannelPublishPlan.shouldAskBrowsable(-1, false, false))

    @Test fun telemetryGate_messageIdentique_emisUneSeuleFois() {
        val g = TelemetryGate()
        assertTrue(g.shouldEmit("k", "a"))
        assertFalse(g.shouldEmit("k", "a"))
        assertTrue(g.shouldEmit("k", "b"))
        assertTrue(g.shouldEmit("autre", "b"))
    }
}
