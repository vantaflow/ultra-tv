package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.nav.DeepLink
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffTest {
    private fun header(vararg o: JSONObject) =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(JSONArray(o.toList()).toString().toByteArray(Charsets.UTF_8))
    private fun req(id: String = "r1", kind: String = "movie") =
        JSONObject().put("i", id).put("f", "Salon").put("s", "0a1b2c3d").put("t", kind).put("r", "42").put("o", 90_000).put("n", "Un film é").put("a", 1)

    @Test fun enTete_decodeLeJsonUtf8EnBase64Url() {
        val r = HandoffLogic.parseHeader(header(req())).single()
        assertEquals("Un film é", r.title); assertEquals("Salon", r.fromName); assertEquals(90_000L, r.positionMs)
        assertEquals("0a1b2c3d", r.cloudProviderId); assertNull(r.parentRemoteId)
    }

    @Test fun enTete_anomalies_listeVide() {
        assertTrue(HandoffLogic.parseHeader(null).isEmpty())
        assertTrue(HandoffLogic.parseHeader("%%%").isEmpty())
        assertTrue(HandoffLogic.parseHeader(header(req(kind = "stream"))).isEmpty())
        assertEquals(3, HandoffLogic.parseHeader(header(req("a"), req("b"), req("c"), req("d"))).size)
    }

    @Test fun corps_neContientNiUrlNiIdentifiants_etLeDirectSansPosition() {
        val item = PlaybackContext.Item(7, "LIVE", "55", "TF1", null, "http://user:pass@host/live/55.ts")
        val body = HandoffLogic.body("dev1", "0a1b2c3d", item, 12_345)!!
        assertFalse(body.contains("http")); assertFalse(body.contains("pass")); assertFalse(body.contains("host"))
        val ref = JSONObject(body).getJSONObject("ref")
        assertEquals(0L, ref.getLong("pos")); assertEquals("live", ref.getString("t")); assertEquals("dev1", JSONObject(body).getString("to"))
    }

    @Test fun corps_episodeAvecSerieEtPosition() {
        val item = PlaybackContext.Item(7, "EPISODE", "900", "S1 E2", null, "http://x/e.mkv", parentRemoteId = "12")
        val ref = JSONObject(HandoffLogic.body("dev1", "0a1b2c3d", item, 61_500)!!).getJSONObject("ref")
        assertEquals("12", ref.getString("par")); assertEquals(61_500L, ref.getLong("pos")); assertEquals("episode", ref.getString("t"))
        assertNull(HandoffLogic.body("dev1", "0a1b2c3d", item.copy(kind = "OTHER"), 0))
    }

    @Test fun lienProfond_resoluDansLeCatalogueLocal() {
        val r = HandoffLogic.parseHeader(header(req(kind = "episode"))).single()
        assertEquals(DeepLink.PlayEpisode(3, "42"), HandoffLogic.deepLink(3, r))
        assertEquals(DeepLink.PlayLive(3, "42"), HandoffLogic.deepLink(3, r.copy(kind = "live")))
        assertEquals(DeepLink.PlayMovie(3, "42"), HandoffLogic.deepLink(3, r.copy(kind = "movie")))
    }

    @Test fun boite_nePoseJamaisDeuxFoisLaMemeDemande_etExpireApresDeuxMinutes() {
        val box = HandoffInbox()
        box.note(header(req("a")), now = 1_000)
        box.note(header(req("a")), now = 2_000)
        assertEquals(1, box.pending.value.size)
        box.resolve("a")
        assertTrue(box.pending.value.isEmpty())
        box.note(header(req("a")), now = 3_000) // déjà traitée : l'en-tête reste 2 min, pas de nouvelle question
        assertTrue(box.pending.value.isEmpty())
        box.note(header(req("b")), now = 10_000)
        assertEquals(1, box.pending.value.size)
        box.note(null, now = 10_000 + HandoffLogic.TTL_MS + 1)
        assertTrue(box.pending.value.isEmpty())
    }
}
