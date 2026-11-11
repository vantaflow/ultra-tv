package com.ultratv.tv.nativeapp.data.xmltv

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Dépôt réel (Room) + faux serveur : écriture par lots, chaînes masquées, url-tvg, échec visible, « Réessayer ». */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class EpgRepositoryTest {
    private val server = FakeFeedServer()
    private var rig: EpgRig? = null
    private val fast = EpgLimits(connectMs = 1_000, stallMs = 500, totalMs = 60_000, minBytesPerSecond = 0, maxAttempts = 2, backoffMs = listOf(5))

    @After fun tearDown() { rig?.close() }

    private fun rig(kind: String = "XTREAM", ids: List<String> = listOf("a", "b", "c")): EpgRig {
        val dir = File(System.getProperty("java.io.tmpdir"), "ultratv-epg-test-${System.nanoTime()}")
        return EpgRig(server, dir, parserFactory = { XmltvParser(it, fast) { } }, kind = kind, channels = ids.mapIndexed { i, id -> "r$i" to id }).also { rig = it }
    }

    private fun programmes(channels: List<String>, perChannel: Int, stepMin: Int = 20): ByteArray {
        val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val now = System.currentTimeMillis()
        return buildString {
            append("<tv>\n")
            for (c in channels) for (i in 0 until perChannel) {
                val s = now - 60 * 60_000L + i * stepMin * 60_000L
                append("<programme start=\"${fmt.format(java.util.Date(s))} +0000\" stop=\"${fmt.format(java.util.Date(s + stepMin * 60_000L))} +0000\" channel=\"$c\"><title>$c-$i</title><desc>Une description assez longue pour peser dans le flux.</desc></programme>\n")
            }
            append("</tv>\n")
        }.toByteArray()
    }

    private fun epgChannels(r: EpgRig): Set<String> = r.db.openHelper.readableDatabase.query(
        "SELECT DISTINCT c.epgChannelId FROM epg e JOIN channel c ON c.id = e.channelId").use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet() }

    @Test fun chainesMasquees_etAbsentes_sontExclues() = runBlocking {
        val r = rig()
        // « b » est dans une catégorie désactivée ; « z » est dans le guide mais ne correspond à aucune chaîne ; « c » est présente.
        r.addCategory("cat-off", enabled = false); r.addCategory("cat-on", enabled = true)
        r.db.openHelper.writableDatabase.execSQL("UPDATE channel SET categoryId='cat-off' WHERE epgChannelId='b'")
        r.db.openHelper.writableDatabase.execSQL("UPDATE channel SET categoryId='cat-on' WHERE epgChannelId='c'")
        server.add("xmltv.php", programmes(listOf("a", "b", "c", "z"), 5))
        assertTrue(r.repo.syncXmltv(r.providerId) > 0)
        assertEquals(setOf("a", "c"), epgChannels(r))
        assertEquals(EpgState.Idle, r.bus.epg.value)
    }

    @Test fun lesLotsSontCommitesAuFilDeLEau_memeSiLeFluxCaleEnMilieu() = runBlocking {
        val r = rig(ids = listOf("a", "b", "c", "d"))
        val body = programmes(listOf("a", "b", "c", "d"), 800, stepMin = 1)   // 3 200 programmes > un lot de 1 000
        server.add("xmltv.php", body, stallAfterBytes = body.size * 3L / 4)
        r.repo.syncXmltv(r.providerId)
        // Le flux a calé aux 3/4 : ce qui était déjà lu est resté dans le guide (avant : une seule transaction, rien du tout).
        assertTrue("des lots commités avant le calage : ${r.count("epg")}", r.count("epg") >= 1_000)
        val st = r.bus.epg.value
        assertTrue("échec visible, pas un chargement infini : $st", st is EpgState.Failed && st.reason == EpgFailReason.TIMEOUT)
    }

    @Test fun uneSynchroReussie_remplaceLesAnciensProgrammesDeLaChaine() = runBlocking {
        val r = rig(ids = listOf("a"))
        server.add("xmltv.php", programmes(listOf("a"), 6))
        r.repo.syncXmltv(r.providerId)
        val n1 = r.count("epg")
        r.repo.syncXmltv(r.providerId)
        assertEquals(n1, r.count("epg"))
    }

    @Test fun m3u_urlTvg_plusieursAdresses_uneEnEchec_lAutreSuffit() = runBlocking {
        val r = rig(kind = "M3U", ids = listOf("a", "b"))
        r.limitsStore.setEpgUrls(r.providerId, listOf("http://guide1.invalid/ko.xml", "http://guide2.invalid/ok.xml.gz"))
        val gz = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(programmes(listOf("a", "b"), 4)) } }.toByteArray()
        server.add("ko.xml", ByteArray(0)).apply { failFirst = 99; failCode = 404 }
        server.add("ok.xml.gz", gz)
        assertTrue(r.repo.syncXmltv(r.providerId) > 0)
        assertEquals(setOf("a", "b"), epgChannels(r))
        assertEquals(EpgState.Idle, r.bus.epg.value)
    }

    @Test fun m3u_sansUrlTvg_nAUcunGuide() = runBlocking {
        val r = rig(kind = "M3U", ids = listOf("a"))
        assertEquals(0, r.repo.syncXmltv(r.providerId))
        assertTrue(server.requests.isEmpty())
    }

    @Test fun echecTotal_publieGuideIndisponible_avecRaison() = runBlocking {
        val r = rig()
        server.add("xmltv.php", ByteArray(0)).apply { failFirst = 99; failCode = 503 }
        r.repo.syncXmltv(r.providerId)
        val st = r.bus.epg.value as EpgState.Failed
        assertEquals(EpgFailReason.SERVER, st.reason)
        assertEquals(r.providerId, st.providerId)
    }

    @Test fun guideSansProgrammePourNosChaines_estUnEchecVisible() = runBlocking {
        val r = rig()
        server.add("xmltv.php", programmes(listOf("autre"), 3))
        r.repo.syncXmltv(r.providerId)
        assertEquals(EpgFailReason.EMPTY, (r.bus.epg.value as EpgState.Failed).reason)
    }

    @Test fun reessayer_relanceSeulementLeGuide() = runBlocking {
        val r = rig()
        server.add("xmltv.php", ByteArray(0)).apply { failFirst = 99; failCode = 503 }
        r.repo.syncXmltv(r.providerId)
        assertTrue(r.bus.epg.value is EpgState.Failed)
        server.routes.clear(); server.requests.clear()
        server.add("xmltv.php", programmes(listOf("a", "b", "c"), 4))
        r.repo.retryEpg(r.providerId)
        val limit = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < limit && !(r.bus.epg.value == EpgState.Idle && r.count("epg") > 0)) Thread.sleep(50)
        assertEquals(EpgState.Idle, r.bus.epg.value)
        assertTrue(r.count("epg") > 0)
        // Seulement le guide : aucune requête de catalogue (player_api.php).
        assertTrue(server.requests.toString(), server.requests.all { it.contains("xmltv.php") })
        assertFalse(server.requests.any { it.contains("player_api") })
    }
}
