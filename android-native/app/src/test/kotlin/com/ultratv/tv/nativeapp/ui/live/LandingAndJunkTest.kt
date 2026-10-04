package com.ultratv.tv.nativeapp.ui.live

import com.ultratv.tv.nativeapp.data.repo.JunkFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandingAndJunkTest {
    private val fav0 = DirectCategory(CATEGORY_FAVORITES, null, 0)
    private val fav3 = DirectCategory(CATEGORY_FAVORITES, null, 3)
    private val all = DirectCategory(CATEGORY_ALL, null, 100)
    private val sport = DirectCategory("10", "Sport", 40)
    private val news = DirectCategory("11", "Info", 20)

    @Test fun landing_derniereCategorieUtilisee() = assertEquals("11", pickLanding(listOf(fav3, all, sport, news), "11"))
    @Test fun landing_derniereDisparue_retombeSurFavoris() = assertEquals(CATEGORY_FAVORITES, pickLanding(listOf(fav3, all, sport), "99"))
    @Test fun landing_sansFavoris_premiereCategorieNonVide() = assertEquals("10", pickLanding(listOf(fav0, all, sport, news), null))
    @Test fun landing_neChoisitJamaisTout_siUneCategorieExiste() = assertEquals("10", pickLanding(listOf(all, sport), null))
    @Test fun landing_rienDeDisponible_estTout() = assertEquals(CATEGORY_ALL, pickLanding(emptyList(), null))

    @Test fun junk_separateursEtNomsVides() { for (s in listOf("", "   ", "-----", "###", "=====", "★★★")) assertTrue(s, JunkFilter.isJunk(s)) }
    @Test fun junk_nomsNumeriques() { for (s in listOf("0", "007", "01", "1", "1 (2026-10-03 12:00:00)")) assertTrue(s, JunkFilter.isJunk(s)) }
    @Test fun junk_evenementsDates() = assertTrue(JunkFilter.isJunk("01-18-2024 7:00pm"))
    @Test fun junk_vraiesChaines_sontConservees() { for (s in listOf("BBC One", "TF1", "AR: الكندوش", "Спорт ТВ", "24/7 CARTOON", "RTL 2", "13EME RUE")) assertFalse(s, JunkFilter.isJunk(s)) }
    @Test fun junk_ligneEvenementLongue_estConservee() = assertFalse(JunkFilter.isJunk("US (ESPN+ 241) | Columbia vs. Pennsylvania Oct 03 5:00PM ET (2026-10-03 17:01:00)"))
}
