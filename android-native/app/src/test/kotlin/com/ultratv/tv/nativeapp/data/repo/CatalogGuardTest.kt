package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seuils de la garde anti-effacement (voir [CatalogGuard]). */
class CatalogGuardTest {
    @Test fun premiere_synchro_ou_base_vide_jamais_refusee() {
        assertFalse(CatalogGuard.refuses(0, 0))
        assertFalse(CatalogGuard.refuses(0, 5))
    }

    @Test fun liste_entierement_vide_refusee_des_un_element_en_base() {
        assertTrue(CatalogGuard.refuses(1, 0))
        assertTrue(CatalogGuard.refuses(10_000, 0))
    }

    @Test fun baisse_brutale_refusee_au_dessus_de_1000_seulement() {
        assertTrue(CatalogGuard.refuses(10_000, 12))
        assertTrue(CatalogGuard.refuses(10_000, 199))     // < 2 %
        assertFalse(CatalogGuard.refuses(10_000, 200))    // = 2 %
        assertFalse(CatalogGuard.refuses(10_000, 2_000))  // un bouquet retiré
        assertTrue(CatalogGuard.refuses(1_000, 19))
        assertFalse(CatalogGuard.refuses(1_000, 20))
        assertFalse(CatalogGuard.refuses(999, 1))         // petite source : seule la liste vide est refusée
    }
}
