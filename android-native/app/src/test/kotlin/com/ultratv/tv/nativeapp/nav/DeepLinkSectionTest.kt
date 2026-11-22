package com.ultratv.tv.nativeapp.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Liens profonds de rubrique (contrat Kairo) : ultratv://home|live|guide|movies|series, catégorie du direct facultative. */
class DeepLinkSectionTest {
    @Test fun rubriques_valides() {
        assertEquals(DeepLink.OpenSection(Routes.HOME), DeepLink.parse("ultratv://home"))
        assertEquals(DeepLink.OpenSection(Routes.LIVE), DeepLink.parse("ultratv://live"))
        assertEquals(DeepLink.OpenSection(Routes.GUIDE), DeepLink.parse("ultratv://guide"))
        assertEquals(DeepLink.OpenSection(Routes.MOVIES), DeepLink.parse("ultratv://movies"))
        assertEquals(DeepLink.OpenSection(Routes.SERIES), DeepLink.parse("ultratv://series"))
        // Barre oblique finale tolérée.
        assertEquals(DeepLink.OpenSection(Routes.MOVIES), DeepLink.parse("ultratv://movies/"))
    }

    @Test fun categorie_du_direct_decodee() {
        assertEquals(DeepLink.OpenSection(Routes.LIVE, "12"), DeepLink.parse("ultratv://live?category=12"))
        assertEquals(DeepLink.OpenSection(Routes.LIVE, "Sport & Info/FR"), DeepLink.parse(DeepLink.liveCategory("Sport & Info/FR")))
        // Catégorie vide ou paramètre étranger : la liste des chaînes, sans catégorie.
        assertEquals(DeepLink.OpenSection(Routes.LIVE), DeepLink.parse("ultratv://live?category="))
        assertEquals(DeepLink.OpenSection(Routes.LIVE), DeepLink.parse("ultratv://live?x=1"))
        assertEquals(DeepLink.OpenSection(Routes.LIVE, "100%"), DeepLink.parse("ultratv://live?category=100%25"))
    }

    @Test fun constructeurs_aller_retour() {
        assertEquals("ultratv://home", DeepLink.section(Routes.HOME))
        listOf(Routes.HOME, Routes.LIVE, Routes.GUIDE, Routes.MOVIES, Routes.SERIES).forEach {
            assertEquals(DeepLink.OpenSection(it), DeepLink.parse(DeepLink.section(it)))
        }
    }

    @Test fun liens_existants_inchanges() {
        assertEquals(DeepLink.PlayLive(1, "2"), DeepLink.parse("ultratv://live/1/2"))
        assertEquals(DeepLink.OpenSeries(1, "2"), DeepLink.parse("ultratv://series/1/2"))
        assertNull(DeepLink.parse("ultratv://live/1"))
    }

    @Test fun lien_inconnu_ou_mal_forme_ouvre_l_accueil() {
        val home = DeepLink.OpenSection(Routes.HOME)
        listOf(
            "ultratv://inconnu", "ultratv://inconnu/1/2", "ultratv://live/1", "ultratv://live/abc/2",
            "ultratv://search?q=", "ultratv://", "ultratv:", "ultratv://%zz", "ultratv://live/1/%", "ultratv:// espace",
            "ULTRATV://rien", "ultratv://settings", "ultratv://player?url=http%3A%2F%2Fx",
        ).forEach { assertEquals(it, home, DeepLink.resolve(it)) }
    }

    @Test fun lien_valide_resolu_tel_quel() {
        assertEquals(DeepLink.OpenSection(Routes.GUIDE), DeepLink.resolve("ultratv://guide"))
        assertEquals(DeepLink.PlayLive(3, "a b"), DeepLink.resolve(DeepLink.live(3, "a b")))
    }

    @Test fun autre_schema_ou_vide_ignore() {
        assertNull(DeepLink.resolve(null))
        assertNull(DeepLink.resolve(""))
        assertNull(DeepLink.resolve("https://exemple.test/home"))
    }

    @Test fun routes_de_rubrique_reconnues() {
        assertTrue(DeepLink.isSectionRoute(Routes.LIVE))
        assertTrue(!DeepLink.isSectionRoute(Routes.movieDetail(3)))
    }
}
