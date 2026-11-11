package com.ultratv.tv.nativeapp.data.xmltv

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Banc de mesure du GUIDE (XMLTV) : 2 000 chaînes × 7 jours (~150 Mo décompressé), gzip ou non, instantané, lent, et
 * connexion qui « cale ». Faux serveur en mémoire (voir [FakeFeedServer]) : aucun réseau.
 *
 * Désactivé par défaut. Pour le lancer : variable `EPG_BENCH=1` ou fichier `<tmp>/epg-bench.flag`, par exemple
 *   ia-build.sh <dépôt> epg testDebugUnitTest --tests '*EpgBenchTest*' -PtestHeap=192m -PtestCpus=2
 * (`testHeap` / `testCpus` simulent un appareil faible). Rapport : `app/build/epg-bench.txt`.
 * Les durées sont celles d'une JVM de poste ; seuls les écarts avant/après comptent.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class EpgBenchTest {
    private val report = StringBuilder()
    private fun say(s: String = "") { println(s); report.appendLine(s) }

    private class Gen(val xml: File, val gz: File, val programmes: Int)

    private val channels = 2_000
    private val days = 7
    private val perDay = 32

    private fun generate(dir: File): Gen {
        val xml = File(dir, "xmltv.xml"); val gz = File(dir, "xmltv.xml.gz")
        val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val dayMs = 86_400_000L
        val t0 = (System.currentTimeMillis() / dayMs - 1) * dayMs
        val slot = dayMs / perDay
        val desc = "Résumé inventé du programme pour le banc de mesure, de la taille d'une description de guide ordinaire, avec accents et une phrase supplémentaire pour la vraisemblance."
        var n = 0
        GZIPOutputStream(gz.outputStream().buffered(1 shl 16)).bufferedWriter().use { wg ->
            xml.bufferedWriter().use { w ->
                fun out(s: String) { w.write(s); wg.write(s) }
                out("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"synthetic\">\n")
                for (c in 1..channels) out("<channel id=\"chaine$c.xx\"><display-name>Chaine Numero $c</display-name></channel>\n")
                for (c in 1..channels) for (d in 0 until days) for (k in 0 until perDay) {
                    val s = t0 + d * dayMs + k * slot
                    out("<programme start=\"${fmt.format(java.util.Date(s))} +0000\" stop=\"${fmt.format(java.util.Date(s + slot))} +0000\" channel=\"chaine$c.xx\"><title lang=\"fr\">Programme $c-$d-$k</title><desc lang=\"fr\">$desc</desc></programme>\n")
                    n++
                }
                out("</tv>\n")
            }
        }
        return Gen(xml, gz, n)
    }

    private class Result(val label: String, val ms: Long, val firstRowsMs: Long, val peakMb: Long, val rows: Int, val outcome: String, val lastStep: String)

    private fun scenario(label: String, gen: Gen, capMs: Long, configure: (FakeFeedServer) -> Unit): Result {
        val server = FakeFeedServer()
        configure(server)
        val channelList = (1..channels).map { "r$it" to "chaine$it.xx" }
        val rig = EpgRig(server, File(gen.xml.parentFile, "db-${label.hashCode()}"), parserFactory = { benchParser(it) }, channels = channelList)
        System.gc(); Thread.sleep(200)
        val rt = Runtime.getRuntime()
        val base = rt.totalMemory() - rt.freeMemory()
        val st = object { @Volatile var peak = base; @Volatile var firstRows = -1L; @Volatile var lastStep = ""; @Volatile var stop = false; @Volatile var done = false }
        val t0 = System.currentTimeMillis()
        val sampler = Thread {
            var tick = 0
            while (!st.stop) {
                val used = rt.totalMemory() - rt.freeMemory(); if (used > st.peak) st.peak = used
                rig.bus.status.value?.let { st.lastStep = it.step }
                if (st.firstRows < 0 && tick % 10 == 0 && runCatching { rig.count("epg") }.getOrDefault(0) > 0) st.firstRows = System.currentTimeMillis() - t0
                tick++; Thread.sleep(20)
            }
        }.apply { isDaemon = true; start() }
        val worker = Thread { try { runBlocking { rig.repo.syncXmltv(rig.providerId) } } catch (_: Throwable) {} finally { st.done = true } }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
        worker.join(capMs)
        val ms = System.currentTimeMillis() - t0
        st.stop = true; sampler.join(1000)
        val outcome = if (st.done) "terminé" else "TOUJOURS BLOQUÉ après ${capMs / 1000} s"
        val rows = runCatching { rig.count("epg") }.getOrDefault(-1)
        server.closeAll(); worker.join(5_000)
        runCatching { rig.close() }
        return Result(label, ms, st.firstRows, (st.peak - base) / 1_048_576, rows, outcome, st.lastStep)
    }

    @Test
    fun banc_guide() {
        assumeTrue("banc désactivé (EPG_BENCH=1 ou fichier epg-bench.flag)", System.getenv("EPG_BENCH") == "1" || File(System.getProperty("java.io.tmpdir"), "epg-bench.flag").exists())
        val dir = File(System.getProperty("java.io.tmpdir"), "ultratv-epg-bench").also { it.deleteRecursively(); it.mkdirs() }
        val gen = generate(dir)
        val rt = Runtime.getRuntime()
        say("Tas max de la JVM de test : ${rt.maxMemory() / 1_048_576} Mo ; cœurs visibles : ${rt.availableProcessors()}")
        say("Guide synthétique : $channels chaînes × $days jours = ${gen.programmes} programmes ; %.1f Mo en XML, %.1f Mo en gzip".format(gen.xml.length() / 1_048_576.0, gen.gz.length() / 1_048_576.0))
        val mb = 1_048_576L
        val results = listOf(
            scenario("XML non compressé, instantané", gen, 600_000) { it.add("xmltv.php", gen.xml) },
            scenario("gzip, instantané", gen, 600_000) { it.add("xmltv.php", gen.gz) },
            scenario("XML non compressé à 2 Mo/s", gen, 600_000) { it.add("xmltv.php", gen.xml, bytesPerSecond = 2 * mb) },
            scenario("gzip à 1 Mo/s", gen, 600_000) { it.add("xmltv.php", gen.gz, bytesPerSecond = 1 * mb) },
            scenario("XML qui cale après 3 Mo (connexion ouverte, aucune donnée)", gen, 130_000) { it.add("xmltv.php", gen.xml, stallAfterBytes = 3 * mb) },
            scenario("gzip qui cale après 1 Mo", gen, 130_000) { it.add("xmltv.php", gen.gz, stallAfterBytes = 1 * mb) },
        )
        say()
        say("%-62s %9s %12s %9s %9s  %s".format("scénario", "durée", "1res lignes", "pic tas", "lignes", "issue"))
        for (r in results) say("%-62s %7d s %10s %6d Mo %9d  %s %s".format(r.label, r.ms / 1000, if (r.firstRowsMs < 0) "jamais" else "${r.firstRowsMs / 1000} s", r.peakMb, r.rows, r.outcome, if (r.lastStep.isNotEmpty()) "[dernier état : ${r.lastStep}]" else ""))
        File("build").also { it.mkdirs() }; File("build/epg-bench.txt").writeText(report.toString())
        dir.deleteRecursively()
    }

    /** Point de variation avant / après : le banc construit le parseur ici. */
    private fun benchParser(ok: okhttp3.OkHttpClient) = XmltvParser(ok)
}
