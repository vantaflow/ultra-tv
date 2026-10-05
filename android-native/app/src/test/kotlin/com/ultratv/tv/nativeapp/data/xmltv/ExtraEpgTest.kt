package com.ultratv.tv.nativeapp.data.xmltv

import com.ultratv.tv.nativeapp.data.db.EpgTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraEpgTest {
    private val targets = listOf(
        EpgTitle(1, "6TER", "6ter.fr", "FR"),
        EpgTitle(2, "AB1", null, "FR"),
        EpgTitle(3, "TF1 +1", null, "FR"),
        EpgTitle(4, "TVA", null, "CA"),
        EpgTitle(5, "13EME RUE", "13emeRue.mu", "FR"),
    )
    private val r = ExtraEpg.Resolver(targets)

    @Test fun norm_sansAccentsNiQualite() = assertEquals("13emerue", ExtraEpg.norm("13ème Rue HD"))
    @Test fun resolve_parIdentifiant() = assertEquals(listOf(1L to 0), r.resolve("6ter.fr", listOf("6ter")))
    @Test fun resolve_parNom() = assertEquals(listOf(2L to 0), r.resolve("AB1.fr", listOf("AB 1")))
    @Test fun resolve_parNom_identifiantDifferent() = assertEquals(listOf(5L to 0), r.resolve("13eRue.fr", listOf("13ème rue")))
    @Test fun resolve_chaineDecalee() = assertEquals(listOf(3L to 1), r.resolve("TF1.fr", listOf("TF1")))
    @Test fun resolve_paysIncompatible_ignore() = assertEquals(emptyList<Pair<Long, Int>>(), r.resolve("TVA.fr", listOf("TVA")))
}
