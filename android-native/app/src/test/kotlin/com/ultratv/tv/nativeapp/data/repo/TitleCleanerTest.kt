package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Motifs observés sur un vrai catalogue (≈ 360 titres échantillonnés), réécrits en titres génériques. */
class TitleCleanerTest {
    private fun film(raw: String) = TitleCleaner.clean(raw)
    private fun tv(raw: String) = TitleCleaner.clean(raw, live = true)

    @Test fun film_prefixeLangueEtAnnee() {
        val c = film("IN-EN - Sample Movie Name (2025)")
        assertEquals("Sample Movie Name", c.title); assertEquals(2025, c.year)
    }
    @Test fun film_prefixeQualiteLangue() = assertEquals("Sample Movie", film("4K-ES - Sample Movie (2004)").title)
    @Test fun film_prefixeSimple() = assertEquals("Another Film", film("IL - Another Film (2023)").title)
    @Test fun film_prefixeAvecPlus() = assertEquals("Some Show", film("4K-A+ - Some Show (2021)").title)
    @Test fun film_prefixeTroisParties() = assertEquals("Anime Title", film("AR-ANM-S - Anime Title (2025) (JP)").title)
    @Test fun film_titreTraduitApresLAnneeEstIgnore() {
        val c = film("KU - Cartoon Pair in a Movie (2016) زینگو و رینگو")
        assertEquals("Cartoon Pair in a Movie", c.title); assertEquals(2016, c.year)
    }
    @Test fun film_doubleEspaceEtSuffixeDub() = assertEquals("Ruthless", film("IR - Ruthless (2023) بی رحم - دوبله").title)
    @Test fun film_sansAnnee_garde_letitre() { val c = film("IT - Sample Without Year"); assertEquals("Sample Without Year", c.title); assertNull(c.year) }
    @Test fun serie_paysEntreParentheses() { val c = film("IN - Sample Series (2024) (US)"); assertEquals("Sample Series", c.title); assertEquals(2024, c.year) }
    @Test fun serie_paysSeulSansAnnee() = assertEquals("Winx Like Saga", film("NF - Winx Like Saga (US)").title)
    @Test fun serie_titreArabeApresPrefixe() = assertEquals("داون تاون", film("4K-AR -  داون تاون").title)
    @Test fun film_apostropheEtPonctuationConserves() = assertEquals("That's Amor", film("DE - That's Amor  (2022)").title)
    @Test fun film_titreSansPrefixeInchange() = assertEquals("Casablanca", film("Casablanca (1942)").title)
    @Test fun film_titreCourtMajusculesNEstPasUnPrefixe() = assertEquals("WWE", film("WWE").title)
    @Test fun film_nom_vide_retombeSurLeBrut() = assertEquals("###", film("###").title)

    @Test fun live_prefixePaysDeuxPoints() = assertEquals("NBC 5 PUEBLO CO (KOAA)", tv("US: NBC 5 PUEBLO CO (KOAA) HD").title)
    @Test fun live_qualiteExtraite() = assertEquals("HD", tv("ES: LA LIGA 1 HD").quality)
    @Test fun live_lettresModificatrices() = assertEquals("TRAVEL CHANNEL", tv("PL VIP: TRAVEL CHANNEL ᴿᴬᵂ").title)
    @Test fun live_prefixeComposeEtDecorations() = assertEquals("SAMPLE CHANNEL", tv("IL: SAMPLE CHANNEL ᴴᴰ ◉").title)
    @Test fun live_prefixePipe() = assertEquals("Some Channel", tv("FR| Some Channel").title)
    @Test fun live_prefixeBarresEntourees() = assertEquals("Some Channel", tv("|AR| Some Channel").title)
    @Test fun live_prefixeCrochets() = assertEquals("Some Channel", tv("[VIP] Some Channel").title)
    @Test fun live_prefixeParentheses() = assertEquals("ESPN PLAY 25", tv("(AU) ESPN PLAY 25 (D)").title)
    @Test fun live_decorationDieses() = assertEquals("SAMPLE SERIES", tv("### SAMPLE SERIES RAW ###").title)
    @Test fun live_prefixeChiffres() = assertEquals("SHE'S GOTTA HAVE IT", tv("24/7: SHE'S GOTTA HAVE IT").title)
    @Test fun live_ligneEvenement_garde_ledernierSegment() =
        assertEquals("VIAPLAY PPV 16", tv("End | Some Grand Prix | Sprint | 2026-07-04 | 10:30 (GMT) | 8K EXCLUSIVE | DK: VIAPLAY PPV 16").title)
    @Test fun live_arabeInchange() = assertEquals("الكندوش", tv("AR: الكندوش").title)
    @Test fun live_cyrillique() = assertEquals("Спорт ТВ", tv("RU: Спорт ТВ HD").title)
    @Test fun live_nePerdJamaisLeTitre() = assertEquals("TV", tv("US: TV").title)
}

class TitleCleanerTidyTest {
    @Test fun tidy_neAfficheJamaisNone() {
        assertEquals("Prediction", TitleCleaner.tidy("PREDICTION.None"))
        assertEquals("Onguenne", TitleCleaner.tidy("ONGUENNE.None"))
        assertEquals("Irrational Love", TitleCleaner.tidy("IRRATIONAL.LOVE.None"))
        assertEquals("Sample", TitleCleaner.tidy("Sample null"))
    }
    @Test fun tidy_titreEnMajusculesDevientCapitalise() {
        assertEquals("Sugar Daddy", TitleCleaner.tidy("SUGAR DADDY"))
        assertEquals("La Casa de Papel", TitleCleaner.tidy("LA CASA DE PAPEL").let { it.replaceFirst("la ", "La ") })
    }
    @Test fun tidy_titreMixteInchange() = assertEquals("That's Amor", TitleCleaner.tidy("That's Amor"))
    @Test fun tidy_idempotent() = assertEquals(TitleCleaner.tidy("SUGAR DADDY"), TitleCleaner.tidy(TitleCleaner.tidy("SUGAR DADDY")))
    @Test fun clean_prefixeLangueEtNone() {
        val c = TitleCleaner.clean("AF-FR - PREDICTION.None")
        assertEquals("Prediction", c.title)
    }
    @Test fun clean_anneeEnPointsExtraite() {
        val c = TitleCleaner.clean("AF-FR - Some.Movie.2021")
        assertEquals("Some Movie", c.title); assertEquals(2021, c.year)
    }
    @Test fun presentable_masqueLesAbsents() {
        assertNull(TitleCleaner.presentable(null)); assertNull(TitleCleaner.presentable("None")); assertNull(TitleCleaner.presentable("null"))
        assertNull(TitleCleaner.presentable("0")); assertNull(TitleCleaner.presentable("  "))
        assertEquals("Drame", TitleCleaner.presentable(" Drame "))
    }
}

class CategoryNameTest {
    private fun cat(raw: String) = com.ultratv.tv.nativeapp.ui.common.prettyCategoryName(raw)
    @Test fun separateurFinal_retire() { assertEquals("AFRICA", cat("AFRICA /")); assertEquals("CANAL+", cat("CANAL+ /")); assertEquals("DSTV", cat("DSTV /")) }
    @Test fun separateurFinalSansEspace_retire() = assertEquals("AFRICA", cat("AFRICA/"))
    @Test fun caractereDecoratif_retire() = assertEquals("RELAX", cat("RELAX ☼"))
    @Test fun decorationEtSeparateur_ensemble() = assertEquals("RELAX", cat("☼ RELAX ☼ /"))
    @Test fun nomOrdinaire_inchange() = assertEquals("Sport FR", cat("Sport FR"))
    @Test fun plusFinalDeNom_conserve() = assertEquals("CANAL+", cat("CANAL+"))
    @Test fun nomEntierDecoratif_retombeSurLeBrut() = assertEquals("###", cat("###"))
}
