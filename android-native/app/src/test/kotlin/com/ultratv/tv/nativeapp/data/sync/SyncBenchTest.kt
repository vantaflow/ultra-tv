package com.ultratv.tv.nativeapp.data.sync

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.stalker.StalkerClient
import com.ultratv.tv.nativeapp.data.stalker.StalkerEndpointStore
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Banc de mesure de la PREMIÈRE synchro Xtream (aucun réseau : faux serveur en mémoire, voir [SyntheticXtream]).
 *
 *  - [synchro_reduite_ecritToutesLesParties] : toujours exécuté, vérifie le comportement fonctionnel (mêmes données en base).
 *  - [banc_complet] : seulement avec `SYNC_BENCH=1` (3 500 directs, 40 000 films, 8 000 séries, guide 3 500 chaînes × 7 jours).
 *
 * Lancer le banc :
 *   cd android-native && SYNC_BENCH=1 JAVA_HOME=… ANDROID_HOME=… ./gradlew :app:testDebugUnitTest --tests '*SyncBenchTest*' -i
 * Le rapport est aussi écrit dans `app/build/sync-bench.txt`. Les chiffres sont ceux d'une JVM de poste de travail : seuls les
 * rapports entre étapes et avant/après comptent (une box Amlogic est 10 à 30 fois plus lente).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class SyncBenchTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val toClose = mutableListOf<UltraDb>()

    @After fun cleanup() { toClose.forEach { runCatching { it.close() } } }

    private class Rig(val db: UltraDb, val repo: ProviderRepository, val providerId: Long, val xtream: XtreamClient, val parser: XmltvParser, val provider: ProviderEntity)

    private fun rig(server: SyntheticXtream, dbFile: File): Rig {
        val db = Room.databaseBuilder(ctx, UltraDb::class.java, dbFile.absolutePath)
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback())   // profil Principal (sinon les préférences ne répondent jamais)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)   // comme la base de production
            .build().also { toClose += it }
        val ok = OkHttpClient.Builder().addInterceptor(server.interceptor).build()
        val xtream = XtreamClient(ok)
        val parser = XmltvParser(ok)
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val repo = ProviderRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = xtream, m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = SyncStatusBus(), xmltv = parser, epgDao = db.epgDao(), db = db, prefs = UserPreferencesStore(ctx, profiles),
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx),
            langDisabled = com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()),
        )
        val provider = ProviderEntity(name = "fictif", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid", username = "test", password = "test", active = true)
        val id = runBlocking { db.providerDao().upsert(provider) }
        return Rig(db, repo, id, xtream, parser, provider.copy(id = id))
    }

    private fun UltraDb.n(table: String): Int = openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun tmp(name: String) = File(System.getProperty("java.io.tmpdir"), "ultratv-bench-$name").also { it.deleteRecursively(); it.mkdirs() }

    // ───────────── Test fonctionnel réduit (toujours exécuté) ─────────────

    @Test
    fun synchro_reduite_ecritToutesLesParties() = runBlocking {
        val server = SyntheticXtream(tmp("reduit"), live = 300, movies = 2_000, series = 400, guideChannels = 120, guideDays = 2, perDay = 12).also { it.generate() }
        val r = rig(server, File(server.dir, "db.sqlite"))
        r.repo.syncAll(r.providerId, force = true)

        assertEquals(300, r.db.channelDao().count(r.providerId))
        assertEquals(2_000, r.db.n("movie"))
        assertEquals(400, r.db.n("series"))
        assertEquals(140, r.db.categoryDao().forProviderKind(r.providerId, "LIVE").size)
        assertEquals(260, r.db.categoryDao().forProviderKind(r.providerId, "MOVIE").size)
        assertEquals(110, r.db.categoryDao().forProviderKind(r.providerId, "SERIES").size)
        val p = r.db.providerDao().byId(r.providerId)!!
        assertTrue(p.lastLiveSyncAt > 0 && p.lastVodSyncAt > 0 && p.lastSeriesSyncAt > 0 && p.lastEpgSyncAt > 0)
        // Guide : seulement la fenêtre −2 h / +24 h, et pour les chaînes qui ont un identifiant EPG.
        val withEpg = r.db.epgDao().channelsWithProgrammes(r.providerId, System.currentTimeMillis() - 3_600_000L)
        assertTrue("des programmes ont été écrits", withEpg.isNotEmpty())
        // Recherche plein texte à jour (déclencheurs FTS) et rang du Direct posé.
        assertTrue(r.db.movieDao().searchFts(r.providerId, "imaginaire*", 5).isNotEmpty())
        assertTrue(r.db.channelDao().searchFts(r.providerId, "numero*", 5).isNotEmpty())
        val first = r.db.channelDao().byRemoteId(r.providerId, "100001")!!
        assertEquals("http://serveur-fictif.invalid/live/test/test/100001.ts", first.streamUrl)
        assertTrue("langue détectée par la synchro", first.lang.isNotEmpty())
        val idBefore = first.id

        // Deuxième synchro forcée : mêmes identifiants (le guide reste rattaché), mêmes comptes.
        r.repo.syncAll(r.providerId, force = true)
        assertEquals(idBefore, r.db.channelDao().byRemoteId(r.providerId, "100001")!!.id)
        assertEquals(300, r.db.channelDao().count(r.providerId))
        assertEquals(2_000, r.db.n("movie"))
        assertEquals(400, r.db.n("series"))
    }

    // ───────────── Banc complet (SYNC_BENCH=1) ─────────────

    private val report = StringBuilder()
    private fun say(s: String = "") { println(s); report.appendLine(s) }
    private inline fun <T> timed(label: String, block: () -> T): Pair<T, Long> {
        val t = System.nanoTime(); val v = block(); val ms = (System.nanoTime() - t) / 1_000_000
        say("  %-52s %8d ms".format(label, ms)); return v to ms
    }

    @Test
    fun banc_complet() = runBlocking {
        assumeTrue("banc désactivé (SYNC_BENCH=1 pour l'activer)", System.getenv("SYNC_BENCH") == "1")
        val server = SyntheticXtream(tmp("complet"), live = 3_500, movies = 40_000, series = 8_000, guideChannels = 3_500, guideDays = 7, perDay = 16)
        val (_, genMs) = timed("génération des données synthétiques (non mesurée)") { server.generate() }
        say("  tailles : direct %.1f Mo, films %.1f Mo, séries %.1f Mo, guide %.1f Mo".format(server.sizeMb("get_live_streams"), server.sizeMb("get_vod_streams"), server.sizeMb("get_series"), server.sizeMb("xmltv")))

        // Échauffement de la JVM (JIT) sur une petite synchro : les mesures suivantes comparent des JVM chaudes.
        say("\n== Étapes isolées (JVM chaude) ==")
        val warm = rig(server, File(server.dir, "warm.sqlite"))
        repeat(2) {
            warm.xtream.withLiveStreams(warm.provider) { s -> s.count() }
            warm.xtream.withVodStreams(warm.provider) { s -> s.take(10_000).count() }
        }
        val r0 = rig(server, File(server.dir, "iso.sqlite"))
        timed("analyse JSON + conversion : direct (3 500)") { r0.xtream.withLiveStreams(r0.provider) { s -> s.count() } }
        timed("analyse JSON + conversion : films (40 000)") { r0.xtream.withVodStreams(r0.provider) { s -> s.count() } }
        timed("analyse JSON + conversion : séries (8 000)") { r0.xtream.withSeries(r0.provider) { s -> s.count() } }
        timed("analyse XMLTV seule (fenêtre −2 h / +24 h)") {
            r0.parser.withProgrammes(r0.provider, (1..3_500).associate { "chaine$it.${listOf("FR", "AR", "EN", "ES", "DE", "IT", "TR", "PT", "NL", "RU")[it % 10]}" to it.toLong() }) { s -> s.count() }
        }

        for ((name, bps, lat) in listOf(Triple("réseau instantané", 0L, 0L), Triple("réseau 4 Mo/s + 200 ms par requête", 4L * 1_048_576, 200L))) {
            server.bytesPerSecond = bps; server.latencyMs = lat
            say("\n== Première synchro complète : $name ==")
            val r = rig(server, File(server.dir, "e2e-${bps}.sqlite"))
            val t0 = System.currentTimeMillis()
            server.requests.clear()
            r.repo.syncAll(r.providerId, force = true)
            val total = System.currentTimeMillis() - t0
            val p = r.db.providerDao().byId(r.providerId)!!
            say("  %-52s %8d ms".format("Direct utilisable (étape « Chaînes en direct » finie)", p.lastLiveSyncAt - t0))
            say("  %-52s %8d ms".format("Films écrits", p.lastVodSyncAt - t0))
            say("  %-52s %8d ms".format("Séries écrites", p.lastSeriesSyncAt - t0))
            say("  %-52s %8d ms".format("Guide TV écrit", p.lastEpgSyncAt - t0))
            say("  %-52s %8d ms".format("TOTAL première synchro", total))
            say("  lignes : %d chaînes, %d films, %d séries, %d programmes".format(
                r.db.channelDao().count(r.providerId), r.db.n("movie"), r.db.n("series"),
                r.db.n("epg")))
            say("  requêtes : ${server.requests.groupingBy { it }.eachCount()}")
            if (bps == 0L) {
                val t1 = System.currentTimeMillis()
                r.repo.syncAll(r.providerId, force = true)
                say("  %-52s %8d ms".format("2e synchro forcée (incrémentale, rien ne change)", System.currentTimeMillis() - t1))
            }
        }
        File("build").also { it.mkdirs() }; File("build/sync-bench.txt").writeText(report.toString())
        assertNotNull(report)
    }
}
