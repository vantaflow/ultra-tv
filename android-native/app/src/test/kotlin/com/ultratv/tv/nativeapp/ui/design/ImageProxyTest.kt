package com.ultratv.tv.nativeapp.ui.design

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageProxyTest {
    private val bg = "https://assets.fanart.tv/fanart/movies/603/moviebackground/matrix.jpg"
    private val logo = "https://assets.fanart.tv/fanart/movies/603/hdmovielogo/matrix.png"
    private val thumb = "https://assets.fanart.tv/fanart/movies/603/moviethumb/matrix.jpg"
    private val tvmaze = "https://static.tvmaze.com/uploads/images/original_untouched/1/2.jpg"

    private fun wsrv(bare: String, w: Int) =
        "https://wsrv.nl/?url=" + java.net.URLEncoder.encode(bare, "UTF-8") + "&w=$w&output=webp&q=75"

    @Test fun fond_fanart_w1280() =
        assertEquals(wsrv("assets.fanart.tv/fanart/movies/603/moviebackground/matrix.jpg", 1280), ImageProxy.rewrite(bg, true, false))

    @Test fun fond_modeEconomie_w960() =
        assertEquals(wsrv("assets.fanart.tv/fanart/movies/603/moviebackground/matrix.jpg", 960), ImageProxy.rewrite(bg, true, true))

    @Test fun logo_w500_memeEnEconomie() {
        assertTrue(ImageProxy.rewrite(logo, true, false)!!.contains("&w=500&"))
        assertTrue(ImageProxy.rewrite(logo, true, true)!!.contains("&w=500&"))
    }

    @Test fun vignette_w640() = assertTrue(ImageProxy.rewrite(thumb, true, false)!!.contains("&w=640&"))

    @Test fun tvmaze_vignette_w640_etFondSiCadreLarge() {
        assertEquals(wsrv("static.tvmaze.com/uploads/images/original_untouched/1/2.jpg", 640), ImageProxy.rewrite(tvmaze, true, false, 300))
        assertTrue(ImageProxy.rewrite(tvmaze, true, false, 1920)!!.contains("&w=1280&"))
    }

    @Test fun urlEncodee_sansSchema() {
        val r = ImageProxy.rewrite("https://static.tvmaze.com/a b/c.jpg?x=1&y=2", true, false)!!
        assertTrue(r.startsWith("https://wsrv.nl/?url=static.tvmaze.com%2Fa+b%2Fc.jpg%3Fx%3D1%26y%3D2&w="))
        assertFalse(r.contains("https%3A"))
    }

    @Test fun tmdb_neChangePas() = assertNull(ImageProxy.rewrite("https://image.tmdb.org/t/p/original/a.jpg", true, false))

    @Test fun autreHote_neChangePas() {
        assertNull(ImageProxy.rewrite("https://exemple.org/a.jpg", true, false))
        assertNull(ImageProxy.rewrite("https://fanart.tv.evil.org/a.jpg", true, false))
        assertNull(ImageProxy.rewrite("https://evil.org/?u=assets.fanart.tv/a.jpg", true, false))
    }

    @Test fun dejaWsrv_neChangePas() =
        assertNull(ImageProxy.rewrite("https://wsrv.nl/?url=assets.fanart.tv%2Fa.jpg&w=500", true, false))

    @Test fun preferenceDesactivee_urlOrigine() = assertNull(ImageProxy.rewrite(bg, false, false))

    @Test fun repli_surErreur() = runBlocking {
        val r = ImageProxy.withFallback<String>({ it == "erreur" }, { "erreur" }, { "origine" })
        assertEquals("origine", r)
    }

    @Test fun repli_surException() = runBlocking {
        val r = ImageProxy.withFallback<String>({ false }, { error("réseau") }, { "origine" })
        assertEquals("origine", r)
    }

    @Test fun repli_surDelaiDepasse() = runBlocking {
        val r = ImageProxy.withFallback<String>({ false }, { kotlinx.coroutines.delay(ImageProxy.TIMEOUT_MS + 2000); "proxy" }, { "origine" })
        assertEquals("origine", r)
    }

    @Test fun succes_gardeLeResultatProxy() = runBlocking {
        assertEquals("proxy", ImageProxy.withFallback<String>({ false }, { "proxy" }, { "origine" }))
    }
}
