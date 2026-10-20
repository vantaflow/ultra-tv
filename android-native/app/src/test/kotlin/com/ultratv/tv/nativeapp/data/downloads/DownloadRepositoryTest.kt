package com.ultratv.tv.nativeapp.data.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.File

/** File d'attente en base : mise en file, refus (HLS, place), pause / reprise / nouvel essai, suppression, purge d'une source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadRepositoryTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: UltraDb
    private lateinit var repo: DownloadRepository
    private lateinit var prefs: DownloadPrefs

    private val movieUrl = "http://serveur-fictif.invalid:8080/movie/utilisateur/motdepasse/42.mkv"

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries().build()
        prefs = DownloadPrefs(ctx)
        repo = DownloadRepository(ctx, db.downloadDao(), db.providerDao(), db.movieDao(), db.episodeDao(), prefs)
        prefs.directory().mkdirs()
        freeSpace(4L * 1024 * 1024 * 1024)
        db.providerDao().upsert(ProviderEntity(id = 1, name = "S", kind = "XTREAM", baseUrl = "http://serveur-fictif.invalid:8080", username = "utilisateur", password = "motdepasse"))
        db.movieDao().upsertAll(listOf(movie("42", movieUrl), movie("43", "http://serveur-fictif.invalid:8080/vod/43.m3u8")))
    }

    @After fun tearDown() { db.close(); ShadowStatFs.reset() }

    private fun freeSpace(bytes: Long) {
        ShadowStatFs.reset()
        val blocks = (bytes / 4096).toInt()
        var d: File? = prefs.directory()
        while (d != null) { ShadowStatFs.registerStats(d, blocks, blocks, blocks); d = d.parentFile }
    }

    private fun movie(rid: String, url: String) = MovieEntity(
        providerId = 1, remoteId = rid, name = "Film $rid", poster = null, categoryId = null, streamUrl = url,
        container = "mkv", year = null, rating = null, plot = null,
    )

    private suspend fun movieRow(rid: String) = db.movieDao().byRemoteId(1, rid)!!

    @Test fun enqueueMovie_fichierDirect_ligneEnFileSansIdentifiantsNiUrl() = runBlocking {
        assertEquals(EnqueueResult.QUEUED, repo.enqueueMovie(movieRow("42")))
        val d = db.downloadDao().byItem(1, DownloadKind.MOVIE, "42")!!
        assertEquals(DownloadState.QUEUED, d.state)
        assertEquals(DownloadPlan.sha256(movieUrl), d.urlHash)
        assertTrue(d.filePath.endsWith(".mkv"))
        for (secret in listOf("utilisateur", "motdepasse", "serveur-fictif")) assertFalse(d.filePath, d.filePath.contains(secret))
        assertTrue(d.filePath.startsWith(prefs.directory().absolutePath))
        assertEquals(movieUrl, repo.currentUrl(d))   // l'URL est relue dans le catalogue, jamais stockée
    }

    @Test fun enqueueMovie_deuxFois_pasDeDoublon() = runBlocking {
        repo.enqueueMovie(movieRow("42"))
        assertEquals(EnqueueResult.ALREADY, repo.enqueueMovie(movieRow("42")))
        assertEquals(1, db.downloadDao().all().size)
    }

    @Test fun enqueueMovie_hls_refuseFormatNonTelechargeable() = runBlocking {
        assertEquals(EnqueueResult.NOT_DOWNLOADABLE, repo.enqueueMovie(movieRow("43")))
        assertTrue(db.downloadDao().all().isEmpty())
    }

    @Test fun enqueueMovie_volumePlein_refuseAvecMessageDePlace() = runBlocking {
        freeSpace(100L * 1024 * 1024)
        assertEquals(EnqueueResult.NO_SPACE, repo.enqueueMovie(movieRow("42")))
        assertTrue(db.downloadDao().all().isEmpty())
    }

    @Test fun pauseRepriseNouvelEssai_suiventLaMachineDEtats() = runBlocking {
        repo.enqueueMovie(movieRow("42"))
        val id = db.downloadDao().all().single().id
        repo.pause(id)
        assertEquals(DownloadState.PAUSED, db.downloadDao().byId(id)!!.state)
        repo.retry(id)   // sans effet : ce n'est pas un échec
        assertEquals(DownloadState.PAUSED, db.downloadDao().byId(id)!!.state)
        repo.resume(id)
        assertEquals(DownloadState.QUEUED, db.downloadDao().byId(id)!!.state)
        db.downloadDao().setState(id, DownloadState.FAILED, DownloadError.HTTP)
        repo.retry(id)
        val d = db.downloadDao().byId(id)!!
        assertEquals(DownloadState.QUEUED, d.state)
        assertNull(d.error)
    }

    @Test fun delete_supprimeLigneFichierEtPartiel() = runBlocking {
        repo.enqueueMovie(movieRow("42"))
        val d = db.downloadDao().all().single()
        File(d.filePath).writeText("x"); DownloadPlan.partFile(File(d.filePath)).writeText("x")
        repo.delete(d.id)
        assertNull(db.downloadDao().byId(d.id))
        assertFalse(File(d.filePath).exists())
        assertFalse(DownloadPlan.partFile(File(d.filePath)).exists())
    }

    @Test fun purgeOrphans_sourceSupprimee_lignesEtFichiersEffaces() = runBlocking {
        repo.enqueueMovie(movieRow("42"))
        val d = db.downloadDao().all().single()
        File(d.filePath).writeText("x")
        repo.purgeOrphans(setOf(1L))
        assertEquals(1, db.downloadDao().all().size)   // source toujours là : rien ne bouge
        repo.purgeOrphans(emptySet())
        assertTrue(db.downloadDao().all().isEmpty())
        assertFalse(File(d.filePath).exists())
    }
}
