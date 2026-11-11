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

    @Test fun birthdayFormatting() {
        assertEquals("30.07.1970", PersonLogic.birthdayLabel("1970-07-30"))
        assertEquals("07.1970", PersonLogic.birthdayLabel("1970-07"))
        assertEquals("1970", PersonLogic.birthdayLabel("1970"))
        org.junit.Assert.assertNull(PersonLogic.birthdayLabel(null))
        org.junit.Assert.assertNull(PersonLogic.birthdayLabel(""))
        org.junit.Assert.assertNull(PersonLogic.birthdayLabel("30/07/1970"))
        assertEquals("1970", PersonLogic.birthdayLabel("1970-13-45"))
    }

    @Test fun personInfoParsing() {
        val json = """{"name":"Christopher Nolan","known_for_department":"Directing","birthday":"1970-07-30","place_of_birth":"Westminster, London, England, UK","biography":"  Bio.  ","profile_path":"/x.jpg"}"""
        val i = PersonLogic.info(json)!!
        assertEquals("Directing", i.department)
        assertEquals("Bio.", i.bio)
        assertEquals("/x.jpg", i.photo)
        val empty = PersonLogic.info("""{"name":"A","biography":"","birthday":null}""")!!
        org.junit.Assert.assertNull(empty.bio)
        org.junit.Assert.assertNull(empty.birthday)
        org.junit.Assert.assertNull(PersonLogic.info("pas du json"))
        org.junit.Assert.assertNull(PersonLogic.info("""{"biography":"x"}"""))
    }
}
