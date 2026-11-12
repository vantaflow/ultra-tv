package com.ultratv.tv.nativeapp.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalIdsLogicTest {
    @Test fun encodeDecode_allerRetour() {
        val ids = ExternalIds("tt0944947", 121361)
        assertEquals(ids, ExternalIdsLogic.decode(ExternalIdsLogic.encode(ids, 1_000), 2_000))
    }

    @Test fun decode_reponseVide_connueMaisSansIdentifiant() {
        val d = ExternalIdsLogic.decode(ExternalIdsLogic.encode(ExternalIds(null, null), 0), 10)!!
        assertTrue(d.isEmpty)
    }

    @Test fun decode_perimeOuIllisible_null() {
        assertNull(ExternalIdsLogic.decode(ExternalIdsLogic.encode(ExternalIds("tt0944947", null), 0), ExternalIdsLogic.TTL_MS + 1))
        assertNull(ExternalIdsLogic.decode("n'importe quoi", 0))
        assertNull(ExternalIdsLogic.decode(null, 0))
    }

    @Test fun key_distingueFilmEtSerie() {
        assertEquals("movie:5", ExternalIdsLogic.key(TmdbKind.MOVIE, 5))
        assertEquals("tv:5", ExternalIdsLogic.key(TmdbKind.TV, 5))
    }
}
