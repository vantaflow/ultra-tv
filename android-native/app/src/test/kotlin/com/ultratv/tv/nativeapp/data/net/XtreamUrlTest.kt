package com.ultratv.tv.nativeapp.data.net

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** URL fictives uniquement (.invalid). */
class XtreamUrlTest {
    @Test fun getPhp_avecPort_serveurIdentifiantMotDePasse() {
        val c = XtreamUrl.parse("http://serveur-fictif.invalid:8080/get.php?username=alice&password=secret&type=m3u_plus&output=ts")!!
        assertEquals(XtreamUrl.Credentials("http://serveur-fictif.invalid:8080", "alice", "secret"), c)
    }

    @Test fun getPhp_sansPort_etEnHttps() {
        assertEquals("http://serveur-fictif.invalid", XtreamUrl.parse("http://serveur-fictif.invalid/get.php?username=a&password=b")!!.server)
        assertEquals("https://serveur-fictif.invalid", XtreamUrl.parse("HTTPS://serveur-fictif.invalid/get.php?username=a&password=b")!!.server)
    }

    @Test fun parametresDansUnAutreOrdre_etAutresPhp() {
        assertEquals("u1" to "p1", XtreamUrl.parse("http://s.invalid:80/get.php?type=m3u&password=p1&output=ts&username=u1")!!.let { it.username to it.password })
        assertEquals("u2" to "p2", XtreamUrl.parse("http://s.invalid/player_api.php?password=p2&username=u2")!!.let { it.username to it.password })
        assertEquals("u3" to "p3", XtreamUrl.parse("http://s.invalid/xmltv.php?username=u3&password=p3")!!.let { it.username to it.password })
    }

    @Test fun encodagePourcent_decode_etPlusResteUnPlus() {
        val c = XtreamUrl.parse("http://s.invalid/get.php?username=al%40ice&password=pa%24%24+w%C3%A9")!!
        assertEquals("al@ice", c.username)
        assertEquals("pa\$\$+wé", c.password)
    }

    @Test fun prefixeDeChemin_estConserve() {
        assertEquals("http://s.invalid:8000/panel", XtreamUrl.parse("http://s.invalid:8000/panel/get.php?username=a&password=b")!!.server)
    }

    @Test fun lienM3uOrdinaire_ouInformationsManquantes_nEstPasXtream() {
        assertNull(XtreamUrl.parse("https://s.invalid/liste.m3u"))
        assertNull(XtreamUrl.parse("http://s.invalid/get.php?username=a"))
        assertNull(XtreamUrl.parse("http://s.invalid/get.php?username=&password=b"))
        assertNull(XtreamUrl.parse("http://s.invalid/autre.php?username=a&password=b"))
        assertNull(XtreamUrl.parse("ftp://s.invalid/get.php?username=a&password=b"))
        assertNull(XtreamUrl.parse(""))
    }

    @Test fun conversion_sourceM3uGetPhp_devientXtream_etRepartDeZero() {
        val m3u = ProviderEntity(id = 7, name = "Ma liste", kind = "M3U", baseUrl = "http://s.invalid:8080/get.php?username=alice&password=secret&type=m3u_plus&output=ts",
            username = "", password = "", active = true, lastLiveSyncAt = 123, lastVodSyncAt = 456)
        val x = XtreamUrl.convert(m3u)!!
        assertEquals(7L, x.id)
        assertEquals("Ma liste", x.name)
        assertEquals("XTREAM", x.kind)
        assertEquals("http://s.invalid:8080", x.baseUrl)
        assertEquals("alice", x.username)
        assertEquals("secret", x.password)
        assertEquals(0L, x.lastLiveSyncAt)
        assertEquals(0L, x.lastVodSyncAt)
        assertEquals(true, x.active)
    }

    @Test fun conversion_autresSources_neChangentPas() {
        assertNull(XtreamUrl.convert(ProviderEntity(name = "x", kind = "M3U", baseUrl = "https://s.invalid/liste.m3u", username = "", password = "")))
        assertNull(XtreamUrl.convert(ProviderEntity(name = "x", kind = "XTREAM", baseUrl = "http://s.invalid/get.php?username=a&password=b", username = "", password = "")))
    }
}
