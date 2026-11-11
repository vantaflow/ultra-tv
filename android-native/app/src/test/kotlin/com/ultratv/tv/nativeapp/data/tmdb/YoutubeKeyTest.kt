package com.ultratv.tv.nativeapp.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Clé YouTube d'une valeur fournisseur ou TMDB. Mêmes cas que le test du bureau (web/src/lib/youtube.test.ts). */
class YoutubeKeyTest {
    private val k = "dQw4w9WgXcQ"

    @Test fun formatsDAdresse() {
        assertEquals(k, YoutubeKey.of("https://www.youtube.com/watch?v=$k"))
        assertEquals(k, YoutubeKey.of("https://www.youtube.com/watch?feature=share&v=$k&t=10"))
        assertEquals(k, YoutubeKey.of("https://youtu.be/$k"))
        assertEquals(k, YoutubeKey.of("https://youtu.be/$k?t=5"))
        assertEquals(k, YoutubeKey.of("https://www.youtube.com/embed/$k"))
        assertEquals(k, YoutubeKey.of("https://www.youtube-nocookie.com/embed/$k?rel=0"))
        assertEquals(k, YoutubeKey.of("https://youtube.com/shorts/$k"))
        assertEquals(k, YoutubeKey.of("https://m.youtube.com/watch?v=$k"))
        assertEquals(k, YoutubeKey.of("www.youtube.com/watch?v=$k"))
        assertEquals(k, YoutubeKey.of("//youtu.be/$k"))
    }

    @Test fun cleBrute_avecEspaces() {
        assertEquals(k, YoutubeKey.of(k))
        assertEquals(k, YoutubeKey.of("  $k\n"))
        assertEquals("a_b-c123456", YoutubeKey.of("a_b-c123456"))
    }

    @Test fun valeursVidesOuAbsentes() {
        for (v in listOf("", "   ", "null", "NULL", "None", "0", "N/A")) assertNull(v, YoutubeKey.of(v))
        assertNull(YoutubeKey.of(null))
    }

    @Test fun adressesQuiNeSontPasYoutube_ClesMalFormees() {
        assertNull(YoutubeKey.of("https://exemple.test/film.mp4"))
        assertNull(YoutubeKey.of("https://exemple.test/embed/$k"))
        assertNull(YoutubeKey.of("https://evilyoutube.com/watch?v=$k"))
        assertNull(YoutubeKey.of("https://www.youtube.com/watch?v=court"))
        assertNull(YoutubeKey.of("https://youtu.be/${k}AAA"))
        assertNull(YoutubeKey.of("abcdefghijkl"))     // 12 caractères
        assertNull(YoutubeKey.of("abc def ghij"))
    }

    @Test fun tmdbTrailer_keyOf_delegueALaMemeRegle() {
        assertEquals("trailer0000", TmdbTrailer.keyOf("https://www.youtube.com/watch?v=trailer0000"))
        assertEquals("trailer0000", TmdbTrailer.keyOf("https://youtu.be/trailer0000"))
        assertEquals("trailer0000", TmdbTrailer.keyOf("trailer0000"))
        assertNull(TmdbTrailer.keyOf("https://exemple.test/film.mp4"))
        assertEquals("https://www.youtube.com/watch?v=trailer0000", TmdbTrailer.webUrl("trailer0000"))
    }
}
