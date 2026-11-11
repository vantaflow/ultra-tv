package com.ultratv.tv.nativeapp.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingsLogicTest {
    @Test fun parseReadsWorkerResponse() {
        assertEquals(ExternalRatings(8.8, 87, 74), RatingsLogic.parse("""{"ratings":{"imdb":8.8,"imdbVotes":2512331,"rt":87,"mc":74,"imdbId":"tt1375666"}}"""))
        assertNull(RatingsLogic.parse("""{"ratings":null}"""))
        assertNull(RatingsLogic.parse("oops"))
    }

    @Test fun labelsPreferImdbThenFallBackToSourceStar() {
        assertEquals(listOf("IMDb 8.8", "RT 87%"), RatingsLogic.labels(ExternalRatings(8.8, 87), 7.1))
        assertEquals(listOf("★ 7.1", "RT 40%"), RatingsLogic.labels(ExternalRatings(rt = 40), 7.1))
        assertEquals(emptyList<String>(), RatingsLogic.labels(null, null))
    }
}
