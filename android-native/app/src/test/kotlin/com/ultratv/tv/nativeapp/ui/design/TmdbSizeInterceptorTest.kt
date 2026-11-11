package com.ultratv.tv.nativeapp.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbSizeInterceptorTest {
    private val base = "https://image.tmdb.org/t/p/"

    @Test fun original_devientLaTailleStandardQuiCouvreLeCadre() =
        assertEquals(base + "w342/abc.jpg", TmdbSizeInterceptor.rewrite(base + "original/abc.jpg", 200))

    @Test fun formatLongueDuFournisseur_estRemplace() =
        assertEquals(base + "w342/abc.jpg", TmdbSizeInterceptor.rewrite(base + "w600_and_h900_bestv2/abc.jpg", 300))

    @Test fun fond_choisitW780() =
        assertEquals(base + "w780/f.jpg", TmdbSizeInterceptor.rewrite(base + "original/f.jpg", 760))

    @Test fun cadreTresGrand_plafonneAW1280() =
        assertEquals(base + "w1280/f.jpg", TmdbSizeInterceptor.rewrite(base + "original/f.jpg", 3000))

    @Test fun dejaALaBonneTaille_neChangePas() = assertNull(TmdbSizeInterceptor.rewrite(base + "w342/abc.jpg", 200))

    @Test fun autreHebergeur_neChangePas() = assertNull(TmdbSizeInterceptor.rewrite("https://exemple.org/p/original/abc.jpg", 200))
}
