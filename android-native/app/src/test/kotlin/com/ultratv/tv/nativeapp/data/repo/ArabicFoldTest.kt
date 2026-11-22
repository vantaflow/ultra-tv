package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recherche en arabe (#247) : même normalisation pour la saisie et pour les titres. */
class ArabicFoldTest {
    @Test fun fold_alefHamza_taMarbuta_alefMaqsura_tatweel_tashkeel() {
        assertEquals("احمد", ArabicFold.fold("أحمد"))
        assertEquals("اسلام", ArabicFold.fold("إسلام"))
        assertEquals("امال", ArabicFold.fold("آمال"))
        assertEquals("الوصل", ArabicFold.fold("ٱلوصل"))
        assertEquals("مدرسه", ArabicFold.fold("مدرسة"))
        assertEquals("مصطفي", ArabicFold.fold("مصطفى"))
        assertEquals("مسلسل", ArabicFold.fold("مُسَلْسَل"))
        assertEquals("مسلسل", ArabicFold.fold("مســلسل"))
        // Hamza posée sur l'alef en forme décomposée (alef + U+0654).
        assertEquals("احمد", ArabicFold.fold("أحمد"))
    }

    @Test fun fold_latin_seulementEnMinuscules() {
        assertEquals("mbc 1 hd", ArabicFold.fold("MBC 1 HD"))
        assertEquals("élodie", ArabicFold.fold("Élodie"))
    }

    @Test fun hasArabic() {
        assertTrue(ArabicFold.hasArabic("mbc مصر"))
        assertFalse(ArabicFold.hasArabic("Spider-Man"))
        assertFalse(ArabicFold.hasArabic(""))
    }

    @Test fun matches_memeNormalisationDesDeuxCotes() {
        assertTrue(ArabicFold.matches("احمد حلمي", "AR - أحمد حلمي (2010)"))
        assertTrue(ArabicFold.matches("مدرسه", "مدرسة المشاغبين"))
        assertTrue(ArabicFold.matches("مُسلسل", "مسلسل الهيبة"))
        assertTrue(ArabicFold.matches("مسلسل", "مُسَلْسَل الهيبة"))
        assertTrue(ArabicFold.matches("الاسطوره", "الأسطورة"))
        assertTrue(ArabicFold.matches("mbc مصر", "MBC مصر HD"))
        assertFalse(ArabicFold.matches("احمد", "محمود"))
    }

    @Test fun likePattern_lettresAmbigues_deviennentUnJoker_motsDansLOrdre() {
        // ا ه ي (après pliage) peuvent être أ إ آ / ة / ى dans le titre : un caractère quelconque « _ ».
        assertEquals("%_حمد%حلم_%", ArabicFold.likePattern("أحمد حلمي"))
        assertEquals("%مدرس_%", ArabicFold.likePattern("مدرسة"))
        // Jokers LIKE de la saisie : séparateurs de mots, jamais recopiés.
        assertEquals("%سر%ب_ت%", ArabicFold.likePattern("سر_بيت%"))
        // Trop court ou vide : pas de balayage.
        assertNull(ArabicFold.likePattern("ب"))
        assertNull(ArabicFold.likePattern("  "))
    }

    @Test fun loosePattern_tolereLesVoyellesDuTitre() {
        assertEquals("%م%س%ل%س%ل%", ArabicFold.loosePattern("مسلسل"))
        assertEquals("%_%ح%م%د%ح%ل%م%_%", ArabicFold.loosePattern("أحمد حلمي"))
        assertNull(ArabicFold.loosePattern("ب"))
    }
}
