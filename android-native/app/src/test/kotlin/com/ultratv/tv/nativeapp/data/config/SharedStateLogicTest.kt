package com.ultratv.tv.nativeapp.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedStateLogicTest {
    @Test fun profilSansNom_devientPrincipal_commeLeBureau() {
        val ps = listOf(1L to "", 2L to "  Enfants ")
        assertEquals("Principal", SharedStateLogic.nameOf(ps).getValue(1L))
        assertEquals("Enfants", SharedStateLogic.nameOf(ps).getValue(2L))
        assertEquals(1L, SharedStateLogic.idOf(ps)["principal"])
        assertEquals(2L, SharedStateLogic.idOf(ps)["enfants"])
    }
    private fun h(t: String, img: String?) = SharedHist("Khalil", "LIVE", "42", t, img, 0, 0, 1_000, null)

    /** Box rc9 : « Geo National Wild » avec le logo (et l'adresse) de « La chaîne météo », ligne locale de même identifiant. */
    @Test fun histRecu_ligneLocaleDUneAutreChaine_niLogoNiAdresseRepris() {
        val local = SharedStateLogic.LocalRow("La chaîne météo", "http://logo/meteo.png", "http://s/meteo")
        val (t, img, url) = SharedStateLogic.mergeHist(h("Geo National Wild", null), local, null)
        assertEquals("Geo National Wild", t)
        assertEquals(null, img)
        assertEquals("", url)
    }

    @Test fun histRecu_catalogueDeMemeNom_logoEtAdresseDuCatalogue() {
        val cat = SharedStateLogic.LocalItem("Geo National Wild", "http://logo/geo.png", "http://s/geo")
        val local = SharedStateLogic.LocalRow("La chaîne météo", "http://logo/meteo.png", "http://s/meteo")
        assertEquals(Triple("Geo National Wild", "http://logo/geo.png", "http://s/geo"), SharedStateLogic.mergeHist(h("Geo National Wild", null), local, cat))
    }

    @Test fun histRecu_catalogueDUnAutreNom_ignore_imageDistante() {
        val cat = SharedStateLogic.LocalItem("La chaîne météo", "http://logo/meteo.png", "http://s/meteo")
        assertEquals(Triple("Geo National Wild", "http://logo/geo-remote.png", ""), SharedStateLogic.mergeHist(h("Geo National Wild", "http://logo/geo-remote.png"), null, cat))
    }

    @Test fun histRecu_ligneLocaleDeMemeNom_reprendSonImageEtSonAdresse() {
        val local = SharedStateLogic.LocalRow("SERIE-CLUB", "http://logo/sc.png", "http://s/sc")
        assertEquals(Triple("Serie Club", "http://logo/sc.png", "http://s/sc"), SharedStateLogic.mergeHist(h("Serie Club", null), local, null))
    }

    private fun f(r: String, on: Boolean = true, at: Long = 100) = SharedFav("Principal", "LIVE", r, on, at)

    @Test fun local_ajout_devientOnMaintenant() {
        val out = SharedStateLogic.localFavorites(emptyMap(), setOf(Triple("Principal", "LIVE", "1")), 500)
        assertEquals(f("1", true, 500), out["Principal|LIVE|1"])
    }
    @Test fun local_retrait_devientTombe() {
        val out = SharedStateLogic.localFavorites(mapOf(f("1").key to f("1")), emptySet(), 500)
        assertFalse(out.getValue("Principal|LIVE|1").on)
        assertEquals(500L, out.getValue("Principal|LIVE|1").at)
    }
    @Test fun local_inchange_garderLaDate() {
        val out = SharedStateLogic.localFavorites(mapOf(f("1").key to f("1", at = 100)), setOf(Triple("Principal", "LIVE", "1")), 500)
        assertEquals(100L, out.getValue("Principal|LIVE|1").at)
    }
    @Test fun distant_plusRecent_aAppliquer_plusAncien_ignore() {
        val known = mapOf(f("1").key to f("1", at = 100), f("2").key to f("2", at = 300))
        val changes = SharedStateLogic.remoteFavChanges(known, listOf(f("1", false, 200), f("2", false, 250), f("3", true, 50)))
        assertEquals(listOf("1", "3"), changes.map { it.r })
    }
    @Test fun json_allerRetour() {
        val h = SharedHist("Principal", "EPISODE", "9", "Ep", null, 60_000, 1_800_000, 42, "S1")
        assertEquals(h, SharedStateLogic.parseHist(SharedStateLogic.histToJson(h)))
        assertTrue(SharedStateLogic.parseFav(SharedStateLogic.favToJson(f("1")))!!.on)
    }
}
