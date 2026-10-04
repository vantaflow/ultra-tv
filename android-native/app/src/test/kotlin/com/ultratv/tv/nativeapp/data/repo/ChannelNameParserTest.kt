package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.F_BACKUP
import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.F_HEVC
import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.F_RAW
import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.Q_4K
import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.Q_FHD
import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.Q_HD
import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.Q_NONE
import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.Q_SD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motifs mesurés sur un vrai catalogue (≈ 54 000 chaînes), réécrits en exemples synthétiques. */
class ChannelNameParserTest {
    private fun p(s: String) = ChannelNameParser.parse(s)

    // ── Séparateurs ──
    @Test fun separateur_dieses_quatreK_exposants_normalises() {
        val r = p("##### 4K ᵁᴴᴰ ³⁸⁴⁰ᴾ #####")
        assertTrue(r.isSeparator); assertEquals("4K UHD 3840P", r.displayName)
    }
    @Test fun separateur_pays_en_majuscules() { val r = p("###### AZERBAIJAN ######"); assertTrue(r.isSeparator); assertEquals("AZERBAIJAN", r.displayName) }
    @Test fun separateur_news_hevc() { val r = p("### UK NEWS HEVC/HD ###"); assertTrue(r.isSeparator); assertTrue(r.displayName.startsWith("UK NEWS")) }
    @Test fun separateur_tirets_egaux_etoiles_blocs() { for (s in listOf("-----", "=====", "★★★", "▬▬▬▬", "·····")) assertTrue(s, p(s).isSeparator) }
    @Test fun separateur_nomSansAucuneLettre() = assertTrue(p("• • •").isSeparator)
    @Test fun uneChaineNormale_nEstPasUnSeparateur() { for (s in listOf("BBC One", "US: CNN HD", "13EME RUE", "24/7 CARTOON")) assertFalse(s, p(s).isSeparator) }
    @Test fun tiretUnique_dansUnNom_nEstPasUnSeparateur() = assertFalse(p("Canal- Plus").isSeparator)

    // ── Pays ──
    @Test fun pays_prefixeDeuxPoints() { val r = p("US: NBC 5 HD"); assertEquals("US", r.country); assertEquals("NBC 5", r.displayName) }
    @Test fun pays_prefixeBarre() { val r = p("AR | Channel Name"); assertEquals("AR", r.country); assertEquals("Channel Name", r.displayName) }
    @Test fun pays_composeBeVip() { val r = p("BE-VIP: Sport Channel"); assertEquals("BE", r.country); assertEquals("Sport Channel", r.displayName) }
    @Test fun pays_royaumeUniAlias() = assertEquals("UK", p("UK: Sky Example").country)
    @Test fun etiquettesNonPays_nesontPasDesPays() { for (s in listOf("TV: Some Channel", "GOLD: Some Channel", "VIP: Some Channel", "4K: Some Channel")) assertNull(s, p(s).country) }
    @Test fun etiquette_retiree_dunNomAffiche() { assertEquals("Some Channel", p("GOLD: Some Channel").displayName); assertEquals("Some Channel", p("PRIME: Some Channel").displayName) }
    @Test fun pays_parentheses() { val r = p("(AU) ESPN PLAY"); assertEquals("AU", r.country); assertEquals("ESPN PLAY", r.displayName) }
    @Test fun pays_crochetsEtPrefixesMultiples() { val r = p("[VIP] FR| Channel One"); assertEquals("FR", r.country); assertEquals("Channel One", r.displayName) }
    @Test fun nomSansPrefixe_neChangePas() { val r = p("Canal Plus Sport"); assertNull(r.country); assertEquals("Canal Plus Sport", r.displayName) }
    @Test fun titreAvecDeuxPoints_pasUnePrefixe() = assertEquals("Match du jour: Lyon contre Nice", p("Match du jour: Lyon contre Nice").displayName)

    // ── Qualité ──
    @Test fun qualite_4k_uhd_2160_3840() { for (s in listOf("Chan 4K", "Chan UHD", "Chan 2160p", "Chan 3840P")) assertEquals(s, Q_4K, p(s).quality) }
    @Test fun qualite_fhd_1080() { assertEquals(Q_FHD, p("Chan FHD").quality); assertEquals(Q_FHD, p("Chan 1080p").quality) }
    @Test fun qualite_hd_720() { assertEquals(Q_HD, p("Chan HD").quality); assertEquals(Q_HD, p("Chan 720P").quality) }
    @Test fun qualite_sd_lq() { assertEquals(Q_SD, p("Chan SD").quality); assertEquals(Q_SD, p("Chan LQ").quality) }
    @Test fun qualite_aucune() = assertEquals(Q_NONE, p("Plain Channel").quality)
    @Test fun qualite_8k_estUneEtiquette_pasUneDefinition() { val r = p("8K: Some Sports"); assertEquals(Q_NONE, r.quality); assertEquals("Some Sports", r.displayName) }
    @Test fun qualite_la_plus_haute_gagne() = assertEquals(Q_4K, p("Chan HD UHD").quality)
    @Test fun qualite_exposants_normalises() = assertEquals(Q_HD, p("Some Channel ᴴᴰ").quality)

    // ── Drapeaux ──
    @Test fun drapeaux() {
        val r = p("Chan RAW HEVC BACKUP")
        assertTrue(r.flags and F_RAW != 0); assertTrue(r.flags and F_HEVC != 0); assertTrue(r.flags and F_BACKUP != 0); assertEquals("Chan", r.displayName)
    }
    @Test fun drapeaux_exposants() = assertTrue(p("Chan ᴿᴬᵂ").flags and F_RAW != 0)

    // ── Nom d'affichage ──
    @Test fun nom_arabe_cyrillique_inchanges() { assertEquals("الكندوش", p("AR: الكندوش").displayName); assertEquals("Спорт ТВ", p("RU: Спорт ТВ HD").displayName) }
    @Test fun nom_jamaisVide() { assertTrue(p("US: HD").displayName.isNotBlank()) }
    @Test fun nom_dollars_et_chiffres() = assertEquals("SKY SPORTS 1", p("UK: SKY SPORTS 1 FHD").displayName)
}
