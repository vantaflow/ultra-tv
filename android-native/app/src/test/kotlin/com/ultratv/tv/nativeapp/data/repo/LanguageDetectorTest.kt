package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.repo.LanguageDetector.MULTI
import com.ultratv.tv.nativeapp.data.repo.LanguageDetector.UNDETERMINED
import org.junit.Assert.assertEquals
import org.junit.Test

/** Motifs observés sur un vrai catalogue, réécrits en exemples synthétiques. */
class LanguageDetectorTest {
    private fun item(t: String, cat: String = UNDETERMINED) = LanguageDetector.forItem(t, cat)

    // ── Préfixes de titre ──
    @Test fun prefixe_pays_et_langues() {
        assertEquals("fr", item("FR - Un film (2020)")); assertEquals("de", item("DE - Ein Film (2020)")); assertEquals("hi", item("IN - Some Title (2021)"))
        assertEquals("fa", item("IR - Some Title (2021)")); assertEquals("sq", item("AL - Some Title")); assertEquals("el", item("GR - Some Title")); assertEquals("en", item("UK - Some Title"))
    }
    @Test fun prefixe_composes() { assertEquals("en", item("IN-EN - Some Title (2025)")); assertEquals("ar", item("AR-SUBS - Some Title (1948)")) }
    @Test fun ar_veut_dire_arabe_pas_argentine() = assertEquals("ar", item("AR - Some Title"))
    @Test fun etiquettes_qui_ne_sont_pas_des_langues() { for (p in listOf("4K", "TV", "GOLD", "VIP", "HBO", "OSN", "SKY", "TOP", "NF", "AMZ", "SC")) assertEquals(p, null, LanguageDetector.byPrefix("$p - Some Title")) }
    @Test fun prefixe_barre_et_deux_points() { assertEquals("fr", LanguageDetector.byPrefix("FR| Chaîne")); assertEquals("es", LanguageDetector.byPrefix("ES: Canal")) }

    // ── Tags de titre ──
    @Test fun tag_vostfr_french_vf() { for (t in listOf("Film VOSTFR", "Film FRENCH", "Film TRUEFRENCH", "Film VF", "Film VFF")) assertEquals(t, "fr", item(t)) }
    @Test fun tag_multi_dual() { assertEquals(MULTI, item("Film MULTI")); assertEquals(MULTI, item("Film DUAL")) }
    @Test fun tag_autres() { assertEquals("ar", item("Film ARA")); assertEquals("es", item("Film LAT")); assertEquals("tr", item("Film TUR")); assertEquals("de", item("Film DEU")); assertEquals("pt", item("Film POR")) }
    @Test fun tag_l_emporte_sur_le_prefixe() = assertEquals("fr", item("DE - Film VOSTFR"))

    // ── Alphabets et mots-clés ──
    @Test fun alphabet_arabe_cyrillique_grec_hebreu_hindi() {
        assertEquals("ar", LanguageDetector.byScript("مسلسل عربي")); assertEquals("ru", LanguageDetector.byScript("Спортивный канал")); assertEquals("el", LanguageDetector.byScript("Ελληνική ταινία"))
        assertEquals("he", LanguageDetector.byScript("סרט ישראלי")); assertEquals("hi", LanguageDetector.byScript("हिंदी फिल्म"))
    }
    @Test fun alphabet_persan_et_ukrainien_departages() { assertEquals("fa", LanguageDetector.byScript("پژمان")); assertEquals("uk", LanguageDetector.byScript("Українська її")) }
    @Test fun mot_cle_turc_en_alphabet_arabe() = assertEquals("tr", LanguageDetector.byScript("مسلسلات تركية"))
    @Test fun alphabet_latin_ne_dit_rien() = assertEquals(null, LanguageDetector.byScript("Plain Latin Title"))

    // ── Catégories ──
    @Test fun categorie_prefixee() { assertEquals("fr", LanguageDetector.forCategory("FR| FILMS")); assertEquals("ar", LanguageDetector.forCategory("AR| MOVIES")) }
    @Test fun categorie_en_alphabet_arabe() = assertEquals("ar", LanguageDetector.forCategory("مسلسلات عربية فائقة الوضوح"))
    @Test fun categorie_plateforme_est_multi() { for (c in listOf("NETFLIX MOVIES", "AMAZON SERIES", "DISNEY+ KIDS", "TOP MOVIES BLURAY (MULTI-SUBS)", "HBO MAX")) assertEquals(c, MULTI, LanguageDetector.forCategory(c)) }
    @Test fun categorie_sans_indice_est_indeterminee() = assertEquals(UNDETERMINED, LanguageDetector.forCategory("TOP MOVIES"))

    // ── Héritage et repli ──
    @Test fun element_sans_prefixe_dans_categorie_fr_est_fr() = assertEquals("fr", item("Un titre sans préfixe", cat = "fr"))
    @Test fun element_netflix_sans_prefixe_est_multi() = assertEquals(MULTI, item("Some Title", cat = MULTI))
    @Test fun element_multi_avec_prefixe_explicite_garde_sa_langue() = assertEquals("fr", item("FR - Some Title", cat = MULTI))
    @Test fun element_arabe_dans_categorie_indeterminee() = assertEquals("ar", item("فيلم جميل"))
    @Test fun element_sans_aucun_indice_reste_indetermine() = assertEquals(UNDETERMINED, item("Some Title"))
    @Test fun element_alphabet_dans_categorie_multi_prime_sur_multi() = assertEquals("ar", item("فيلم جميل", cat = MULTI))
}
