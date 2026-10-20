package com.ultratv.tv.nativeapp.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationTest {
    private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)

    // ── Contraste WCAG ──

    @Test fun wcagRatio_blancSurNoir_vingtEtUn() {
        assertEquals(21.0, Wcag.ratio(Wcag.WHITE, Wcag.BLACK), 0.01)
    }

    @Test fun wcagRatio_memeCouleur_un() {
        assertEquals(1.0, Wcag.ratio(0xFFD91E2BL, 0xFFD91E2BL), 1e-9)
    }

    @Test fun wcagRatio_ordreDesCouleurs_symetrique() {
        assertEquals(Wcag.ratio(0xFF2563EBL, 0xFF141418L), Wcag.ratio(0xFF141418L, 0xFF2563EBL), 1e-12)
    }

    @Test fun wcagRatio_valeurDeReference_rougeSurBlanc() {
        // Valeur de référence (outil WebAIM) : #D91E2B sur blanc ≈ 5,03:1.
        assertEquals(5.03, Wcag.ratio(0xFFD91E2BL, Wcag.WHITE), 0.02)
    }

    @Test fun ensureContrast_couleurDejaLisible_inchangee() {
        assertEquals(0xFFFFFFFFL, Wcag.ensureContrast(0xFFFFFFFFL, UxSurfaces.DARK))
    }

    // ── Accent ──

    @Test fun accent_chaqueAccentEtChaqueFondDePage_anneauDeFocusAuMoinsTrois() {
        for (a in AccentChoice.entries) for (bg in UxSurfaces.PAGE_DARK + UxSurfaces.PAGE_LIGHT) {
            val r = Wcag.ratio(a.argb, bg)
            assertTrue("${a.name} sur ${hex(bg)} = $r", r >= Wcag.AA_UI)
        }
    }

    @Test fun accent_chaqueAccent_texteSurAccentAA() {
        for (a in AccentChoice.entries) {
            val r = Wcag.ratio(a.onAccent, a.argb)
            assertTrue("${a.name} : ${hex(a.onAccent)} sur ${hex(a.argb)} = $r", r >= Wcag.AA_TEXT)
        }
    }

    @Test fun accent_onAccent_choisitLeTexteLePlusContraste() {
        for (a in AccentChoice.entries) {
            val other = if (a.onAccent == Wcag.WHITE) Wcag.INK else Wcag.WHITE
            assertTrue(a.name, Wcag.ratio(a.onAccent, a.argb) >= Wcag.ratio(other, a.argb))
        }
    }

    @Test fun accent_rouge_texteBlanc_orange_texteEncre() {
        assertEquals(Wcag.WHITE, AccentChoice.RED.onAccent)
        assertEquals(Wcag.INK, AccentChoice.ORANGE.onAccent)
    }

    @Test fun accent_texteAccentSurSurfacesSombres_AA() {
        for (a in AccentChoice.entries) for (bg in UxSurfaces.DARK) {
            val r = Wcag.ratio(a.inkOnDark, bg)
            assertTrue("${a.name} ${hex(a.inkOnDark)} sur ${hex(bg)} = $r", r >= Wcag.AA_TEXT)
        }
    }

    @Test fun accent_texteAccentSurSurfacesClaires_AA() {
        for (a in AccentChoice.entries) for (bg in UxSurfaces.LIGHT) {
            val r = Wcag.ratio(a.inkOnLight, bg)
            assertTrue("${a.name} ${hex(a.inkOnLight)} sur ${hex(bg)} = $r", r >= Wcag.AA_TEXT)
        }
    }

    @Test fun accent_texteAccent_resteTeinte_pasBlancNiNoir() {
        for (a in AccentChoice.entries) {
            assertTrue(a.name, a.inkOnDark != Wcag.WHITE && a.inkOnLight != Wcag.BLACK)
        }
    }

    @Test fun accentParse_valeurInconnueOuVide_rougeParDefaut() {
        assertEquals(AccentChoice.RED, AccentChoice.parse(null))
        assertEquals(AccentChoice.RED, AccentChoice.parse("turquoise"))
        assertEquals(AccentChoice.BLUE, AccentChoice.parse("BLUE"))
    }

    @Test fun accentParse_cleDeChaqueAccent_allerRetour() {
        for (a in AccentChoice.entries) assertEquals(a, AccentChoice.parse(a.key))
    }

    // ── Pastilles de la liste Direct ──

    /** Lignes sombres : surfaces du thème sombre (repos) + focus du thème clair (#16151A). Lignes claires : blanc (focus sombre / repos clair), fond clair. */
    private val darkRows = UxSurfaces.PAGE_DARK + 0xFF16151AL
    private val lightRows = longArrayOf(0xFFFFFFFFL, 0xFFF4F3EFL, 0xFFECEAE5L)

    @Test fun badgeQualite_chaqueQualiteEtLigne_texteAA_etPastilleDistincte() {
        for (q in 1..4) for ((dark, rows) in listOf(true to darkRows, false to lightRows)) {
            val chip = BadgePalette.quality(q, dark)
            assertNotNull(chip); chip!!
            assertTrue("q$q dark=$dark texte", Wcag.ratio(chip.fg, chip.bg) >= Wcag.AA_TEXT)
            for (row in rows) assertTrue("q$q dark=$dark sur ${hex(row)}", Wcag.ratio(chip.bg, row) >= Wcag.AA_UI)
        }
    }

    @Test fun badgeQualite_inconnue_aucunePastille() {
        assertEquals(null, BadgePalette.quality(0, true))
        assertEquals(null, BadgePalette.quality(9, false))
    }

    @Test fun badgeQualite_quatreQualites_couleursDistinctes() {
        for (dark in listOf(true, false)) assertEquals(4, (1..4).map { BadgePalette.quality(it, dark)!!.bg }.toSet().size)
    }

    @Test fun badgeLangue_multiEtSimple_texteAA_etPastilleDistincte() {
        for (multi in listOf(true, false)) for ((dark, rows) in listOf(true to darkRows, false to lightRows)) {
            val chip = BadgePalette.lang(dark, multi)
            assertTrue("multi=$multi dark=$dark texte", Wcag.ratio(chip.fg, chip.bg) >= Wcag.AA_TEXT)
            for (row in rows) assertTrue("multi=$multi dark=$dark sur ${hex(row)}", Wcag.ratio(chip.bg, row) >= Wcag.AA_UI)
        }
    }

    // ── Taille du texte ──

    @Test fun textSizeParse_inconnu_normal_facteursCroissants() {
        assertEquals(TextSize.NORMAL, TextSize.parse("huge"))
        assertEquals(TextSize.XLARGE, TextSize.parse("xlarge"))
        assertTrue(TextSize.NORMAL.scale < TextSize.LARGE.scale && TextSize.LARGE.scale < TextSize.XLARGE.scale)
        assertEquals(1f, TextSize.NORMAL.scale)
    }
}
