package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {
    @Test fun of_motUnique_devientPrefixe() = assertEquals("sport*", FtsQuery.of("sport"))
    @Test fun of_plusieursMots_tousEnPrefixe() = assertEquals("beIN* sports*", FtsQuery.of("  beIN   sports "))
    @Test fun of_operateursFts_deviennentDesPrefixesLitteraux() = assertEquals("sport* OR* news*", FtsQuery.of("sport OR news"))
    @Test fun of_caracteresSpeciaux_sontRetires() = assertEquals("sport* news*", FtsQuery.of("sport\" -news"))
    @Test fun of_guillemetsEtEtoiles_sontRetires() = assertEquals("a*", FtsQuery.of("\"a*\""))
    @Test fun of_saisieVideOuSymboles_estNulle() { assertNull(FtsQuery.of("   ")); assertNull(FtsQuery.of("*-\"()")) }
    @Test fun of_arabeEtCyrillique_sontConserves() {
        assertEquals("رياضة*", FtsQuery.of("رياضة"))
        assertEquals("спорт*", FtsQuery.of("спорт"))
    }
    @Test fun of_accents_sontConserves() = assertEquals("cinéma*", FtsQuery.of("cinéma"))
    @Test fun of_limiteLeNombreDeMots() = assertEquals(6, FtsQuery.of("a b c d e f g h")!!.split(" ").size)
}
