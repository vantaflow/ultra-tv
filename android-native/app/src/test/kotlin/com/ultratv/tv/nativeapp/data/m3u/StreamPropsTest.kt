package com.ultratv.tv.nativeapp.data.m3u

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lecture des #KODIPROP / #EXTVLCOPT : tous les formats de licence, et les cas invalides. */
class StreamPropsTest {
    private val parser = M3uParser(OkHttpClient())
    private val kid = "4060a865887842679cbf91ae5bae1e72"
    private val key = "fc35340837310cc0fb53de97e22a69e0"

    private fun props(vararg lines: String): StreamProps? {
        val r = parser.parse("#EXTM3U\n#EXTINF:-1,Test\n" + lines.joinToString("\n") + "\nhttp://serveur-fictif.invalid/a.mpd\n", 1)
        return StreamProps.fromJson(r.channels.single().props)
    }

    @Test fun widevine_urlSimple() {
        val p = props("#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha", "#KODIPROP:inputstream.adaptive.license_key=https://lic.invalid/wv")!!
        assertEquals(DrmScheme.WIDEVINE, p.scheme)
        assertEquals("https://lic.invalid/wv", p.licenseUrl)
        assertTrue(p.licenseHeaders.isEmpty())
        assertTrue(p.isProtected)
    }

    @Test fun widevine_formatKodiAvecEnTetes() {
        val p = props(
            "#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha",
            "#KODIPROP:inputstream.adaptive.license_key=https://lic.invalid/wv?a=1|User-Agent=Mon%20Agent&X-Token=abc==&Referer=https%3A%2F%2Fsite.invalid%2F|R{SSM}|",
        )!!
        assertEquals("https://lic.invalid/wv?a=1", p.licenseUrl)
        assertEquals(mapOf("User-Agent" to "Mon Agent", "X-Token" to "abc==", "Referer" to "https://site.invalid/"), p.licenseHeaders)
    }

    @Test fun widevine_plusResteUnPlus() {
        val p = props("#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha", "#KODIPROP:inputstream.adaptive.license_key=https://l.invalid/x|X-T=a+b")!!
        assertEquals("a+b", p.licenseHeaders["X-T"])
    }

    @Test fun clearKey_pairesHexSepareesParVirgules() {
        val p = props("#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey", "#KODIPROP:inputstream.adaptive.license_key=$kid:$key, ${"11".repeat(16)}:${"22".repeat(16).uppercase()}")!!
        assertEquals(DrmScheme.CLEARKEY, p.scheme)
        assertEquals(listOf(ClearKeyPair(kid, key), ClearKeyPair("11".repeat(16), "22".repeat(16))), p.clearKeys)
    }

    @Test fun clearKey_typeAbrege() {
        val p = props("#KODIPROP:inputstream.adaptive.license_type=clearkey", "#KODIPROP:inputstream.adaptive.license_key=$kid:$key")!!
        assertEquals(DrmScheme.CLEARKEY, p.scheme)
        assertEquals(1, p.clearKeys.size)
    }

    @Test fun clearKey_jsonPlat_hexOuBase64url() {
        val b64kid = "QGCoZYh4Qmecv5GuW64ecg"       // = kid en base64url
        val p = props("#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey", """#KODIPROP:inputstream.adaptive.license_key={"$kid":"$key","$b64kid":"_DU0CDcxDMD7U96X4ipp4A"}""")!!
        assertEquals(listOf(ClearKeyPair(kid, key), ClearKeyPair(kid, key)), p.clearKeys)
    }

    @Test fun clearKey_jwk() {
        val p = props("#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey", """#KODIPROP:inputstream.adaptive.license_key={"keys":[{"kty":"oct","k":"_DU0CDcxDMD7U96X4ipp4A","kid":"QGCoZYh4Qmecv5GuW64ecg"}],"type":"temporary"}""")!!
        assertEquals(listOf(ClearKeyPair(kid, key)), p.clearKeys)
    }

    @Test fun clearKey_serveurDeLicenceParUrl() {
        val p = props("#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey", "#KODIPROP:inputstream.adaptive.license_key=https://ck.invalid/lic|X-A=1")!!
        assertEquals("https://ck.invalid/lic", p.licenseUrl)
        assertEquals(mapOf("X-A" to "1"), p.licenseHeaders)
        assertTrue(p.clearKeys.isEmpty())
    }

    @Test fun clearKey_valeursInvalides_aucuneCle() {
        for (bad in listOf("zz:yy", "$kid", "$kid:abcd", "{pas du json", """{"keys":[{"kid":"x","k":"y"}]}""", """{"$kid":"court"}""", "$kid:$key,oups")) {
            val p = props("#KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey", "#KODIPROP:inputstream.adaptive.license_key=$bad")!!
            assertTrue("« $bad » doit être rejeté", p.clearKeys.isEmpty())
            assertEquals(DrmScheme.CLEARKEY, p.scheme)   // reste protégé : jamais lu en clair, erreur à la lecture
        }
    }

    @Test fun manifestTypeEtOptionsVlc() {
        val p = props(
            "#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha", "#KODIPROP:inputstream.adaptive.manifest_type=MPD",
            "#EXTVLCOPT:http-user-agent=Agent/1.0", "#EXTVLCOPT:http-referrer=https://ref.invalid/",
        )!!
        assertEquals("mpd", p.manifestType)
        assertEquals("Agent/1.0", p.userAgent)
        assertEquals("https://ref.invalid/", p.referrer)
        assertEquals("hls", props("#KODIPROP:inputstream.adaptive.license_type=clearkey", "#KODIPROP:inputstream.adaptive.manifest_type=hls")!!.manifestType)
        assertNull(props("#KODIPROP:inputstream.adaptive.license_type=clearkey", "#KODIPROP:inputstream.adaptive.manifest_type=ism")!!.manifestType)
    }

    @Test fun autreSysteme_estProtegeMaisNonPrisEnCharge() {
        assertEquals(DrmScheme.UNSUPPORTED, props("#KODIPROP:inputstream.adaptive.license_type=com.microsoft.playready")!!.scheme)
    }

    @Test fun sansLicenseType_fluxOrdinaire_memeAvecOptionsVlc() {
        assertNull(props("#EXTVLCOPT:http-user-agent=X", "#KODIPROP:inputstream.adaptive.manifest_type=mpd"))
        assertNull(props("#KODIPROP:inputstream.adaptive.license_type="))
        assertNull(props("#KODIPROP:license_type=clearkey"))
        assertNull(props("#KODIPROP:sans-egal"))
    }

    @Test fun proprietesAvantExtinf_etNeFuientPasSurLaChaineSuivante() {
        val r = parser.parse(
            """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=$kid:$key
            #EXTINF:-1,Protégée
            http://s/1.mpd
            #EXTINF:-1,Ordinaire
            http://s/2.ts
            """.trimIndent(), 1,
        )
        assertTrue(StreamProps.fromJson(r.channels[0].props)!!.isProtected)
        assertNull(r.channels[1].props)
    }

    @Test fun jsonIllisible_donneNull_etToStringMasqueLesSecrets() {
        assertNull(StreamProps.fromJson("{pas json"))
        assertNull(StreamProps.fromJson(null))
        val p = StreamProps(DrmScheme.CLEARKEY, "https://lic.invalid/secret", mapOf("X-T" to "jeton"), listOf(ClearKeyPair(kid, key)))
        val txt = p.toString()
        assertFalse(txt.contains("lic.invalid")); assertFalse(txt.contains("jeton")); assertFalse(txt.contains(key)); assertFalse(txt.contains(kid))
        assertEquals(p, StreamProps.fromJson(p.toJson()))
    }
}
