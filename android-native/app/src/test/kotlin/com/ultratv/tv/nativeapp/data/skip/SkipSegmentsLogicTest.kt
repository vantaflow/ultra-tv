package com.ultratv.tv.nativeapp.data.skip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentsLogicTest {

    private val episodeJson = """
        {"tvdb_id":121361,"type":"tv","season":1,"episode":2,
         "intro":[{"start_ms":62000,"end_ms":151000,"confidence":0.9,"submission_count":12}],
         "recap":[{"start_ms":null,"end_ms":45000}],
         "credits":[{"start_ms":3300000,"end_ms":null}],
         "preview":[]}
    """.trimIndent()

    @Test fun urlCarriesEpisodeAndDuration() {
        assertEquals("https://api.theintrodb.org/v4/media?tmdb_id=1399&season=1&episode=2&duration_ms=3480000",
            SkipSegmentsLogic.url(1399, 1, 2, 3_480_000))
        assertEquals("https://api.theintrodb.org/v4/media?tmdb_id=27205", SkipSegmentsLogic.url(27205, null, null, 0))
    }

    @Test fun parseReadsAllKindsSortedAndNullStartIsZero() {
        val s = SkipSegmentsLogic.parse(episodeJson)
        assertEquals(listOf(SkipKind.RECAP, SkipKind.INTRO, SkipKind.CREDITS), s.map { it.kind })
        assertEquals(SkipSegment(SkipKind.RECAP, 0, 45_000), s[0])
        assertEquals(SkipSegment(SkipKind.CREDITS, 3_300_000, null), s[2])
    }

    @Test fun parseDropsInvalidAndSurvivesGarbage() {
        assertTrue(SkipSegmentsLogic.parse("not json").isEmpty())
        assertTrue(SkipSegmentsLogic.parse("""{"intro":[{"start_ms":50000,"end_ms":10000}]}""").isEmpty())
        assertTrue(SkipSegmentsLogic.parse("{}").isEmpty())
    }

    @Test fun offerIntroAndRecapSeekToTheirEnd() {
        val s = SkipSegmentsLogic.parse(episodeJson)
        assertEquals(SkipOffer(SkipKind.RECAP, 45_000, false), SkipSegmentsLogic.offerAt(s, 10_000, 3_480_000, true, true))
        assertEquals(SkipOffer(SkipKind.INTRO, 151_000, false), SkipSegmentsLogic.offerAt(s, 70_000, 3_480_000, true, true))
        assertNull(SkipSegmentsLogic.offerAt(s, 50_000, 3_480_000, true, true))      // entre deux passages
        assertNull(SkipSegmentsLogic.offerAt(s, 150_000, 3_480_000, true, true))     // marge de fin
    }

    @Test fun creditsOfEpisodeGoToNextEpisodeOnlyWhenOneExists() {
        val s = SkipSegmentsLogic.parse(episodeJson)
        assertEquals(SkipOffer(SkipKind.CREDITS, null, true), SkipSegmentsLogic.offerAt(s, 3_310_000, 3_480_000, true, true))
        assertNull(SkipSegmentsLogic.offerAt(s, 3_310_000, 3_480_000, true, false))  // dernier épisode, générique jusqu'au bout
    }

    @Test fun movieCreditsOnlyWhenAPostCreditsSceneFollows() {
        val toEnd = listOf(SkipSegment(SkipKind.CREDITS, 7_000_000, null))
        assertNull(SkipSegmentsLogic.offerAt(toEnd, 7_100_000, 7_400_000, false, false))
        val midCredits = listOf(SkipSegment(SkipKind.CREDITS, 7_000_000, 7_200_000))
        assertEquals(SkipOffer(SkipKind.CREDITS, 7_200_000, false), SkipSegmentsLogic.offerAt(midCredits, 7_100_000, 7_400_000, false, false))
    }
}
