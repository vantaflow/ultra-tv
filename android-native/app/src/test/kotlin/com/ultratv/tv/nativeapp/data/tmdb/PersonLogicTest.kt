package com.ultratv.tv.nativeapp.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonLogicTest {
    @Test fun searchPrefersExactName() {
        val json = """{"results":[{"id":1,"name":"Cillian Murphy Jr"},{"id":2037,"name":"Cillian Murphy"}]}"""
        assertEquals(2037, PersonLogic.searchBest(json, "cillian murphy"))
        assertEquals(1, PersonLogic.searchBest("""{"results":[{"id":1,"name":"X"}]}""", "Y"))
    }

    @Test fun creditsMergeCastAndCrewNewestFirst() {
        val json = """{"cast":[
            {"media_type":"movie","title":"Inception","original_title":"Inception","release_date":"2010-07-15"},
            {"media_type":"tv","name":"Peaky Blinders","first_air_date":"2013-09-12"},
            {"media_type":"movie","title":"Oppenheimer","release_date":"2023-07-19"}],
          "crew":[{"media_type":"movie","title":"Oppenheimer","release_date":"2023-07-19"}]}"""
        val c = PersonLogic.credits(json)
        assertEquals(listOf("Oppenheimer", "Peaky Blinders", "Inception"), c.map { it.title })
        assertTrue(c[1].isTv)
    }

    @Test fun matchingIgnoresAccentsAndAllowsOneYear() {
        val c = PersonCredit(false, "Le Fabuleux Destin d'Amélie Poulain", "Le Fabuleux Destin d'Amélie Poulain", 2001)
        assertTrue(PersonLogic.matches(c, "Le fabuleux destin d amelie poulain", 2002))
        assertTrue(PersonLogic.matches(c, "Le Fabuleux Destin d'Amélie Poulain", null))
        assertFalse(PersonLogic.matches(c, "Le Fabuleux Destin d'Amélie Poulain", 2010))
        assertTrue(PersonLogic.matches(PersonCredit(false, "Spirited Away", "千と千尋の神隠し", 2001), "千と千尋の神隠し", 2001))
    }
}
