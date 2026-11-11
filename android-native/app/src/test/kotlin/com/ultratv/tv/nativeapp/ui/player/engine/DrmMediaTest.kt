package com.ultratv.tv.nativeapp.ui.player.engine

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.ultratv.tv.nativeapp.data.m3u.ClearKeyPair
import com.ultratv.tv.nativeapp.data.m3u.DrmScheme
import com.ultratv.tv.nativeapp.data.m3u.StreamProps
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Configuration Media3 des flux protégés (aucun réseau). */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class DrmMediaTest {
    // Flux ClearKey DASH public (Axinom, clé de test publique) : essai manuel sur appareil.
    private val axinomUrl = "https://media.axprod.net/TestVectors/Dash/protected_dash_1080p_h264_singlekey/manifest.mpd"
    private val kid = "4060a865887842679cbf91ae5bae1e72"
    private val key = "fc35340837310cc0fb53de97e22a69e0"

    @Test fun widevine_urlEtEnTetes() {
        val p = StreamProps(DrmScheme.WIDEVINE, "https://lic.invalid/wv", mapOf("X-Token" to "abc"), manifestType = "mpd")
        val item = DrmMedia.mediaItem("http://s.invalid/live", p, null)
        val drm = item.localConfiguration!!.drmConfiguration!!
        assertEquals(C.WIDEVINE_UUID, drm.scheme)
        assertEquals("https://lic.invalid/wv", drm.licenseUri.toString())
        assertEquals(mapOf("X-Token" to "abc"), drm.licenseRequestHeaders)
        assertEquals(MimeTypes.APPLICATION_MPD, item.localConfiguration!!.mimeType)
        assertNull(item.localConfiguration!!.tag)
    }

    @Test fun clearKeyHex_versJsonBase64url() {
        val json = JSONObject(DrmMedia.clearKeyLicenseJson(listOf(ClearKeyPair(kid, key))))
        assertEquals("temporary", json.getString("type"))
        val k = json.getJSONArray("keys").getJSONObject(0)
        assertEquals("oct", k.getString("kty"))
        assertEquals("QGCoZYh4Qmecv5GuW64ecg", k.getString("kid"))
        assertEquals("_DU0CDcxDMD7U96X4ipp4A", k.getString("k"))
    }

    @Test fun clearKeyLocal_axinomDash_configurationDuMediaItem() {
        val p = StreamProps(DrmScheme.CLEARKEY, clearKeys = listOf(ClearKeyPair(kid, key)))
        val item = DrmMedia.mediaItem(axinomUrl, p, null)
        val lc = item.localConfiguration!!
        assertEquals(C.CLEARKEY_UUID, lc.drmConfiguration!!.scheme)
        assertNull(lc.drmConfiguration!!.licenseUri)            // pas de serveur : réponse locale
        assertEquals(MimeTypes.APPLICATION_MPD, lc.mimeType)     // d'après l'extension
        val local = lc.tag as LocalClearKey
        assertEquals(DrmMedia.clearKeyLicenseJson(p.clearKeys), String(local.licenseJson, Charsets.UTF_8))
        assertTrue(!local.toString().contains(key))
    }

    @Test fun clearKeyAvecServeur_aUneUrlDeLicence_etPasDeCleLocale() {
        val item = DrmMedia.mediaItem("http://s.invalid/x.m3u8", StreamProps(DrmScheme.CLEARKEY, licenseUrl = "https://ck.invalid/l", manifestType = "hls"), 8000)
        val lc = item.localConfiguration!!
        assertEquals("https://ck.invalid/l", lc.drmConfiguration!!.licenseUri.toString())
        assertNull(lc.tag)
        assertEquals(MimeTypes.APPLICATION_M3U8, lc.mimeType)
        assertEquals(8000L, item.liveConfiguration.targetOffsetMs)
    }

    @Test fun typeMime_manifestTypePrimeSurExtension() {
        assertEquals(MimeTypes.APPLICATION_M3U8, DrmMedia.mimeType(StreamProps(DrmScheme.WIDEVINE, manifestType = "hls"), "http://a/x.mpd"))
        assertEquals(MimeTypes.APPLICATION_MPD, DrmMedia.mimeType(StreamProps(DrmScheme.WIDEVINE), "http://a/x.mpd?t=1"))
        assertEquals(MimeTypes.APPLICATION_M3U8, DrmMedia.mimeType(StreamProps(DrmScheme.WIDEVINE), "http://a/x.m3u8#f"))
        assertNull(DrmMedia.mimeType(StreamProps(DrmScheme.WIDEVINE), "http://a/live"))
    }

    @Test fun enTetesDeFlux() {
        assertEquals(mapOf("User-Agent" to "A", "Referer" to "R"), DrmMedia.streamHeaders(StreamProps(DrmScheme.WIDEVINE, userAgent = "A", referrer = "R")))
        assertTrue(DrmMedia.streamHeaders(StreamProps(DrmScheme.WIDEVINE)).isEmpty())
    }

    @Test fun precheck_systemeAbsent_proprietesIncompletes_ok() {
        val yes = { _: java.util.UUID -> true }; val no = { _: java.util.UUID -> false }
        val wv = StreamProps(DrmScheme.WIDEVINE, "https://l.invalid/")
        assertNull(DrmMedia.precheck(wv, yes))
        assertEquals(PlayErrorKind.DRM_UNSUPPORTED, DrmMedia.precheck(wv, no))
        assertEquals(PlayErrorKind.DRM_INVALID, DrmMedia.precheck(StreamProps(DrmScheme.WIDEVINE), yes))
        assertEquals(PlayErrorKind.DRM_INVALID, DrmMedia.precheck(StreamProps(DrmScheme.CLEARKEY), yes))
        assertNull(DrmMedia.precheck(StreamProps(DrmScheme.CLEARKEY, clearKeys = listOf(ClearKeyPair(kid, key))), yes))
        assertEquals(PlayErrorKind.DRM_UNSUPPORTED, DrmMedia.precheck(StreamProps(DrmScheme.UNSUPPORTED), yes))
        assertNotNull(DrmMedia.precheck(wv, no))
    }

    @Test fun erreursMedia3_classement() {
        assertEquals(PlayErrorKind.DRM_UNSUPPORTED, PlaybackPlanner.classifyExo(6001, null))
        assertEquals(PlayErrorKind.DRM_UNSUPPORTED, PlaybackPlanner.classifyExo(6007, null))
        assertEquals(PlayErrorKind.DRM_LICENSE, PlaybackPlanner.classifyExo(6004, null))
        assertEquals(PlayErrorKind.DRM_LICENSE, PlaybackPlanner.classifyExo(6008, null))
    }
}
