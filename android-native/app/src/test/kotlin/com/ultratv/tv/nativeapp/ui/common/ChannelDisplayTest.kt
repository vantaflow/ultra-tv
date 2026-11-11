package com.ultratv.tv.nativeapp.ui.common

import com.ultratv.tv.nativeapp.data.profile.ProfilePrefs
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelDisplayTest {
    // ── Largeur de la colonne ──
    @Test fun largeur_defautEstNormale() = assertEquals(GuideNameWidth.NORMAL, GuideNameWidth.parse(null))
    @Test fun largeur_cleInconnueRetombeSurNormale() = assertEquals(GuideNameWidth.NORMAL, GuideNameWidth.parse("xxl"))
    @Test fun largeur_cleConnue() = assertEquals(GuideNameWidth.WIDE, GuideNameWidth.parse("wide"))
    @Test fun largeur_ordreEtroiteNormaleLarge() {
        assertTrue(GuideNameWidth.NARROW.tvPx < GuideNameWidth.NORMAL.tvPx && GuideNameWidth.NORMAL.tvPx < GuideNameWidth.WIDE.tvPx)
        assertTrue(GuideNameWidth.NARROW.touchDp < GuideNameWidth.NORMAL.touchDp && GuideNameWidth.NORMAL.touchDp < GuideNameWidth.WIDE.touchDp)
    }
    @Test fun largeur_parDefautPlusLargeQueAvant() {
        assertTrue(GuideNameWidth.NORMAL.tvPx > 260)
        assertTrue(GuideNameWidth.NORMAL.touchDp > 116)
    }
    @Test fun largeur_etroiteEstLAncienneColonne() { assertEquals(260, GuideNameWidth.NARROW.tvPx); assertEquals(116, GuideNameWidth.NARROW.touchDp) }
    @Test fun largeur_clesUniques() = assertEquals(GuideNameWidth.entries.size, GuideNameWidth.entries.map { it.key }.toSet().size)
    @Test fun zoneDuNom_logoMasqueRedonneLaPlaceDuLogo() {
        val avec = guideNameAreaPx(340, true)
        val sans = guideNameAreaPx(340, false)
        assertEquals(64 + 14, sans - avec)
        assertTrue(guideNameAreaPx(GuideNameWidth.NORMAL.tvPx, true) > guideNameAreaPx(GuideNameWidth.NARROW.tvPx, true))
    }

    // ── Numéro et nom sans logo ──
    @Test fun numero_seqPrioritaire() = assertEquals(7, channelNumber(7, 120))
    @Test fun numero_sinonNumeroSource() = assertEquals(120, channelNumber(0, 120))
    @Test fun numero_inconnu() = assertNull(channelNumber(0, 0))
    @Test fun libelle_numeroEtNom() = assertEquals("7 · TF1", channelHeading(7, "TF1"))
    @Test fun libelle_sansNumero_nomSeul() = assertEquals("TF1", channelHeading(null, "TF1"))
    @Test fun affichage_defautLogosVisibles() { assertTrue(ChannelDisplay().showLogos); assertEquals(GuideNameWidth.NORMAL, ChannelDisplay().guideNameWidth) }

    // ── Réglages par profil ──
    @Test fun profil_surchargeLogosEtLargeur() {
        val u = ProfilePrefs(showChannelLogos = false, guideNameWidth = "wide").applyTo(UserPrefs())
        assertFalse(u.showChannelLogos); assertEquals("wide", u.guideNameWidth)
    }
    @Test fun profil_sansSurchargeGardeLeGlobal() {
        val u = ProfilePrefs().applyTo(UserPrefs())
        assertTrue(u.showChannelLogos); assertEquals("normal", u.guideNameWidth)
    }
    @Test fun profil_lectureDepuisLesCles() {
        val p = ProfilePrefs.fromMap(mapOf(ProfilePrefs.CHANNEL_LOGOS to "false", ProfilePrefs.GUIDE_NAME_WIDTH to "narrow"))
        assertEquals(false, p.showChannelLogos); assertEquals("narrow", p.guideNameWidth)
    }
    @Test fun profil_valeurIllisibleIgnoree() = assertNull(ProfilePrefs.fromMap(mapOf(ProfilePrefs.CHANNEL_LOGOS to "peut-etre")).showChannelLogos)
}
