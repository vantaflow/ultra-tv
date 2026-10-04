package com.ultratv.tv.nativeapp.data.timeshift

import com.ultratv.tv.nativeapp.adaptive.Tier
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.Socket

class TimeshiftTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun bytes(from: Int, n: Int) = ByteArray(n) { (from + it).toByte() }

    @Test fun tampon_ecrit_et_relit_sans_depasser_la_capacite() {
        val b = CircularFileBuffer(tmp.newFile(), 100)
        b.write(bytes(0, 60), 0, 60)
        assertEquals(0, b.start); assertEquals(60, b.end)
        val dst = ByteArray(60)
        assertEquals(60, b.read(0, dst, 0, 60)); assertArrayEquals(bytes(0, 60), dst)
        assertEquals(0, b.read(60, dst, 0, 10))     // rien de neuf
    }

    @Test fun tampon_circulaire_ecrase_les_plus_anciens_et_boucle() {
        val b = CircularFileBuffer(tmp.newFile(), 100)
        b.write(bytes(0, 70), 0, 70)
        b.write(bytes(70, 60), 0, 60)                // 130 octets écrits : les 30 premiers sont perdus
        assertEquals(30, b.start); assertEquals(130, b.end)
        val dst = ByteArray(100)
        assertEquals(-1, b.read(10, dst, 0, 10))     // écrasé
        assertEquals(100, b.read(30, dst, 0, 100))   // traverse la frontière de bouclage
        assertArrayEquals(bytes(30, 100), dst)
    }

    @Test fun fermeture_supprime_le_fichier() {
        val f = tmp.newFile(); val b = CircularFileBuffer(f, 10)
        b.write(bytes(0, 5), 0, 5); b.close()
        assertFalse(f.exists()); assertEquals(-1, b.read(0, ByteArray(5), 0, 5))
    }

    @Test fun planner_trente_minutes_minimum_si_lespace_le_permet() {
        val free = 20L * 1024 * 1024 * 1024
        val p = TimeshiftPlanner.plan(free, Tier.LOW, 1080)!!
        assertEquals(30, p.minutes)
        assertEquals(60, TimeshiftPlanner.plan(free, Tier.HIGH, 1080)!!.minutes)
    }

    @Test fun planner_borne_par_lespace_libre_et_refuse_sous_le_plancher() {
        val p = TimeshiftPlanner.plan(2L * 1024 * 1024 * 1024, Tier.HIGH, 1080)!!
        assertTrue(p.capacityBytes <= (2L * 1024 * 1024 * 1024 * 0.4).toLong())
        assertNull(TimeshiftPlanner.plan(100L * 1024 * 1024, Tier.HIGH, 1080))
    }

    @Test fun planner_4k_demande_plus_doctets_par_seconde() {
        assertTrue(TimeshiftPlanner.assumedBytesPerSec(2160) > TimeshiftPlanner.assumedBytesPerSec(1080))
    }

    @Test fun saut_borne_a_la_fenetre_et_aligne_sur_188() {
        val bps = 1_000_000L
        assertEquals(0L, TimeshiftMath.jump(10_000, -30, bps, 0, 5_000_000))                    // borné au début
        assertEquals(5_000_000L - 5_000_000L % 188, TimeshiftMath.jump(4_900_000, 30, bps, 0, 5_000_000))   // borné au direct
        assertEquals(0L, TimeshiftMath.jump(1_000_000, -30, bps, 0, 5_000_000) % 188)
    }

    @Test fun retard_et_fenetre_en_secondes() {
        assertEquals(12, TimeshiftMath.behindLiveSec(8_000_000, 20_000_000, 1_000_000))
        assertEquals(30, TimeshiftMath.windowSec(0, 30_000_000, 1_000_000))
        assertEquals(0, TimeshiftMath.behindLiveSec(1, 2, 0))
    }

    @Test fun synchronisation_ts_ignore_loctets_de_tete() {
        val data = ByteArray(188 * 4) { 0 }
        for (p in 0 until 4) data[10 + p * 188] = 0x47
        assertEquals(10, TimeshiftMath.firstSync(data, data.size))
        assertEquals(-1, TimeshiftMath.firstSync(ByteArray(500), 500))
    }

    @Test fun relais_local_copie_le_flux_et_le_ressert_depuis_une_position() {
        // Flux de 300 paquets de 188 octets, chacun marqué par son index ; hôte local fictif.
        val ts = ByteArray(188 * 300).also { for (p in 0 until 300) { it[p * 188] = 0x47; it[p * 188 + 1] = (p % 250).toByte() } }
        val up = MockWebServer().apply { enqueue(MockResponse().setBody(Buffer().write(ts))); start() }
        val proxy = TimeshiftProxy(OkHttpClient(), up.url("/flux.ts").toString(), "test", tmp.newFile(), 188L * 1000, 1_000_000)
        try {
            proxy.start()
            val deadline = System.currentTimeMillis() + 5_000
            while (proxy.buffer.end < ts.size && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(ts.size.toLong(), proxy.buffer.end)

            val from = 188L * 100
            val url = java.net.URI(proxy.localUrl(from))
            Socket(url.host, url.port).use { s ->
                s.soTimeout = 3_000
                s.getOutputStream().write("GET ${url.rawPath}?${url.rawQuery} HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                val inp = s.getInputStream()
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) head.append(inp.read().toChar())
                val got = ByteArray(188 * 5); var n = 0
                while (n < got.size) { val r = inp.read(got, n, got.size - n); if (r < 0) break; n += r }
                assertEquals(188 * 5, n)
                assertArrayEquals(ts.copyOfRange(188 * 100, 188 * 105), got)
            }
        } finally { proxy.close(); up.shutdown() }
    }
}
