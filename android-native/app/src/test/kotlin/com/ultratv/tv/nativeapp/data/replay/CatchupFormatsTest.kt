package com.ultratv.tv.nativeapp.data.replay

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Hôtes et identifiants fictifs (example.test). Début = 2023-11-14 22:14:00 UTC = 1700000040 s. */
class CatchupFormatsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val start = 1_700_000_040_000L
    private val end = start + 45 * 60_000L
    private val now = 1_700_100_000_000L

    @Test fun types_reconnus() {
        assertEquals(CatchupType.DEFAULT, CatchupType.parse("default"))
        assertEquals(CatchupType.APPEND, CatchupType.parse(" Append "))
        assertEquals(CatchupType.SHIFT, CatchupType.parse("shift"))
        assertEquals(CatchupType.FLUSSONIC, CatchupType.parse("flussonic"))
        assertEquals(CatchupType.XC, CatchupType.parse("xc"))
        assertNull(CatchupType.parse("n'importe quoi"))
        assertNull(CatchupType.parse(null))
    }

    @Test fun gabarit_default_garde_la_source_telle_quelle() {
        assertEquals("http://c.example.test/{utc}", CatchupFormats.template(CatchupType.DEFAULT, "http://c.example.test/{utc}", "http://s.example.test/1.ts"))
    }

    @Test fun gabarit_append_et_source_relative_sans_type() {
        assertEquals("http://s.example.test/1.ts?utc={utc}", CatchupFormats.template(CatchupType.APPEND, "?utc={utc}", "http://s.example.test/1.ts"))
        assertEquals("http://s.example.test/1.ts?utc={utc}", CatchupFormats.template(null, "?utc={utc}", "http://s.example.test/1.ts"))
        assertNull(CatchupFormats.template(CatchupType.APPEND, null, "http://s.example.test/1.ts"))
        assertNull(CatchupFormats.template(null, null, "http://s.example.test/1.ts"))
    }

    @Test fun gabarit_shift_par_defaut_et_avec_requete_existante() {
        assertEquals("http://s.example.test/1.ts?utc={utc}&lutc={lutc}", CatchupFormats.template(CatchupType.SHIFT, null, "http://s.example.test/1.ts"))
        assertEquals("http://s.example.test/1.ts?tok=a&utc={utc}&lutc={lutc}", CatchupFormats.template(CatchupType.SHIFT, null, "http://s.example.test/1.ts?tok=a"))
    }

    @Test fun gabarit_flussonic() {
        assertEquals("http://s.example.test/ch1/timeshift_abs-{utc}.ts", CatchupFormats.template(CatchupType.FLUSSONIC, null, "http://s.example.test/ch1/mpegts"))
        assertEquals("http://s.example.test/ch1/archive-{utc}-{duration}.m3u8?t=1", CatchupFormats.template(CatchupType.FLUSSONIC, null, "http://s.example.test/ch1/index.m3u8?t=1"))
        assertNull(CatchupFormats.template(CatchupType.FLUSSONIC, null, "http://s.example.test/ch1/autre"))
    }

    @Test fun gabarit_xc_non_stocke() {
        assertNull(CatchupFormats.template(CatchupType.XC, "ignoré", "http://s.example.test/live/u/p/1.ts"))
    }

    @Test fun jetons_remplaces() {
        val u = CatchupFormats.fill("http://x/{utc}-{utcend}-{lutc}-{offset}-{duration}-{duration:60}-\${start}-\${end}-{Y}{m}{d}T{H}{M}{S}", start, end, now)
        assertEquals("http://x/1700000040-1700002740-1700100000-99960-2700-45-1700000040-1700002740-20231114T221400", u)
    }

    @Test fun jeton_inconnu_conserve() {
        assertEquals("http://x/{inconnu}", CatchupFormats.fill("http://x/{inconnu}", start, end, now))
    }

    @Test fun m3u_attributs_catchup_normalises() {
        val r = M3uParser(okhttp3.OkHttpClient()).parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="a" catchup="shift" catchup-days="2",A
            http://s.example.test/a.ts
            #EXTINF:-1 tvg-id="b" catchup="append" catchup-source="?u={utc}",B
            http://s.example.test/b.ts
            #EXTINF:-1 tvg-id="c" catchup="xc",C
            http://s.example.test/live/u/p/3.ts
            #EXTINF:-1 tvg-id="d",D
            http://s.example.test/d.ts
            """.trimIndent(),
            providerId = 1,
        )
        val by = r.channels.associateBy { it.remoteId }
        assertEquals("http://s.example.test/a.ts?utc={utc}&lutc={lutc}", by["a"]!!.catchupSource)
        assertEquals(2, by["a"]!!.catchupDays)
        assertEquals("http://s.example.test/b.ts?u={utc}", by["b"]!!.catchupSource)
        assertEquals(CatchupFormats.DEFAULT_DAYS, by["b"]!!.catchupDays)
        assertNull(by["c"]!!.catchupSource)
        assertEquals(CatchupFormats.DEFAULT_DAYS, by["c"]!!.catchupDays)
        assertNull(by["d"]!!.catchupSource)
        assertEquals(0, by["d"]!!.catchupDays)
    }

    // --- Construction d'URL (ReplayUrls) ---

    private fun ch(days: Int = 3, url: String = "http://example.test:8080/live/usr/pwd/42.ts", src: String? = null) =
        ChannelEntity(id = 1, providerId = 1, remoteId = "42", name = "Chaine", logo = null, categoryId = "c", streamUrl = url, catchupDays = days, catchupSource = src)

    @Test fun xtream_heure_ecrite_dans_le_fuseau_du_fournisseur() {
        val paris = TimeZone.getTimeZone("Europe/Paris")   // UTC+1 en novembre
        val u = ReplayUrls.buildAt(ch(days = 99999), start, end, now, ReplayStyle.PATH, paris)
        assertEquals("http://example.test:8080/timeshift/usr/pwd/45/2023-11-14:23-14/42.ts", u)
    }

    @Test fun modele_m3u_rempli_pour_un_programme() {
        val u = ReplayUrls.build(ch(days = 99999, src = "http://c.example.test/{utc}/{duration}"), EpgEntity(channelId = 1, title = "P", description = null, startMs = start, endMs = end), now, ReplayStyle.PATH, utc)
        assertEquals("http://c.example.test/1700000040/2700", u)
    }

    @Test fun bornes_de_jours_d_archive() {
        val day = 24L * 3_600_000L
        val c = ch(days = 2)
        assertEquals(now - 2 * day, ReplayUrls.earliestMs(c, now))
        assertEquals(ReplayAvailability.AVAILABLE, ReplayUrls.availabilityAt(c, now - 2 * day + 1, now))
        assertEquals(ReplayAvailability.OUT_OF_WINDOW, ReplayUrls.availabilityAt(c, now - 2 * day - 1, now))
        assertEquals(ReplayAvailability.NOT_STARTED, ReplayUrls.availabilityAt(c, now + 1, now))
        assertNull(ReplayUrls.buildAt(c, now - 3 * day, now - 3 * day + 60_000, now, ReplayStyle.PATH, utc))
    }

    @Test fun retour_arriere_borne_a_l_archive() {
        val c = ch(days = 1)
        assertEquals(now - 300_000, ReplayUrls.rewindStart(c, now, 300_000))
        assertEquals(now - 24L * 3_600_000L, ReplayUrls.rewindStart(c, now, 10L * 24 * 3_600_000L))
        assertNull(ReplayUrls.rewindStart(ch(days = 0), now, 300_000))
        assertNull(ReplayUrls.rewindStart(ch(days = 3, url = "http://example.test/a.m3u8"), now, 300_000))
        assertEquals(now - 300_000, ReplayUrls.rewindStart(ch(days = 3, url = "http://example.test/a.m3u8", src = "http://c.example.test/{utc}"), now, 300_000))
    }

    // --- Barre de replay ---

    private fun p(s: Long, e: Long) = EpgEntity(id = s, channelId = 1, title = "P$s", description = null, startMs = s, endMs = e)

    @Test fun heure_reelle_et_sauts() {
        assertEquals(start + 90_000, ReplayTransport.wallClockMs(start, 90_000))
        assertEquals(start, ReplayTransport.wallClockMs(start, -5))
        assertEquals(50_000, ReplayTransport.jumpTarget(60_000, -10, 0))
        assertEquals(0, ReplayTransport.jumpTarget(5_000, -10, 0))
        assertEquals(100_000, ReplayTransport.jumpTarget(95_000, 60, 100_000))
        assertEquals(120_000, ReplayTransport.jumpTarget(60_000, 60, 0))
    }

    @Test fun programme_precedent_et_suivant() {
        val a = p(0, 1000); val b = p(1000, 2000); val c = p(2000, 3000); val future = p(5000, 6000)
        val all = listOf(c, a, future, b)
        assertEquals(a, ReplayTransport.previous(all, b))
        assertNull(ReplayTransport.previous(all, a))
        assertEquals(c, ReplayTransport.next(all, b, 4000))
        assertNull(ReplayTransport.next(all, c, 4000))   // le suivant n'a pas commencé : retour au direct
        assertEquals(future, ReplayTransport.next(all, c, 5000))
    }

    @Test fun fuseau_du_serveur_xtream() {
        val ok = Json.parseToJsonElement("""{"server_info":{"timezone":"Europe/Paris"}}""").jsonObject
        val ko = Json.parseToJsonElement("""{"server_info":{"timezone":"Pas/UnFuseau"}}""").jsonObject
        assertEquals("Europe/Paris", ProviderLimitsStore.parseServerTimezone(ok))
        assertNull(ProviderLimitsStore.parseServerTimezone(ko))
        assertNull(ProviderLimitsStore.parseServerTimezone(Json.parseToJsonElement("{}").jsonObject))
        assertTrue(true)
    }
}
