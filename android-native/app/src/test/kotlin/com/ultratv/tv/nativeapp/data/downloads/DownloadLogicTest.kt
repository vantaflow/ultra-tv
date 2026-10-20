package com.ultratv.tv.nativeapp.data.downloads

import com.ultratv.tv.nativeapp.data.downloads.DownloadState.Event
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Machine d'états, règles de fichier / d'espace, ménage et lecture hors ligne (logique pure). */
class DownloadLogicTest {
    @get:Rule val tmp = TemporaryFolder()

    // ── Machine d'états ──
    @Test fun etat_cycleNominal_fileEnCoursTermine() {
        val running = DownloadState.next(DownloadState.QUEUED, Event.START)
        assertEquals(DownloadState.RUNNING, running)
        assertEquals(DownloadState.COMPLETED, DownloadState.next(running!!, Event.COMPLETE))
    }

    @Test fun etat_pauseEtReprise_repasseEnFile() {
        assertEquals(DownloadState.PAUSED, DownloadState.next(DownloadState.RUNNING, Event.PAUSE))
        assertEquals(DownloadState.PAUSED, DownloadState.next(DownloadState.QUEUED, Event.PAUSE))
        assertEquals(DownloadState.QUEUED, DownloadState.next(DownloadState.PAUSED, Event.RESUME))
    }

    @Test fun etat_echecPuisNouvelEssai_repasseEnFile() {
        assertEquals(DownloadState.FAILED, DownloadState.next(DownloadState.RUNNING, Event.FAIL))
        assertEquals(DownloadState.QUEUED, DownloadState.next(DownloadState.FAILED, Event.RETRY))
    }

    @Test fun etat_interruption_neRepasseEnFileQueDepuisEnCours() {
        assertEquals(DownloadState.QUEUED, DownloadState.next(DownloadState.RUNNING, Event.INTERRUPT))
        assertNull(DownloadState.next(DownloadState.PAUSED, Event.INTERRUPT))
    }

    @Test fun etat_transitionsAbsurdes_ignorees() {
        assertNull(DownloadState.next(DownloadState.COMPLETED, Event.START))
        assertNull(DownloadState.next(DownloadState.COMPLETED, Event.PAUSE))
        assertNull(DownloadState.next(DownloadState.PAUSED, Event.COMPLETE))   // une pause n'est jamais « terminée » par le travailleur
        assertNull(DownloadState.next(DownloadState.QUEUED, Event.RETRY))
        assertNull(DownloadState.next(DownloadState.RUNNING, Event.RESUME))
    }

    @Test fun etat_actif_seulementEnFileOuEnCours() {
        assertTrue(DownloadState.isActive(DownloadState.QUEUED))
        assertTrue(DownloadState.isActive(DownloadState.RUNNING))
        assertFalse(DownloadState.isActive(DownloadState.PAUSED))
        assertFalse(DownloadState.isActive(DownloadState.COMPLETED))
    }

    // ── Règles de fichier ──
    @Test fun telechargeable_fichiersDirectsOui_hlsEtSchemasExotiquesNon() {
        assertTrue(DownloadPlan.isDownloadable("http://h:8080/movie/u/p/12.mkv"))
        assertTrue(DownloadPlan.isDownloadable("https://h/series/u/p/34.mp4?token=x"))
        assertTrue(DownloadPlan.isDownloadable("stalker://3/vod?cmd=abc"))
        assertFalse(DownloadPlan.isDownloadable("http://h/vod/12.m3u8"))
        assertFalse(DownloadPlan.isDownloadable("http://h/get.php?output=m3u8&id=1"))
        assertFalse(DownloadPlan.isDownloadable("http://h/manifest.mpd"))
        assertFalse(DownloadPlan.isDownloadable("rtmp://h/live/1"))
        assertFalse(DownloadPlan.isDownloadable("file:///sdcard/x.mp4"))
    }

    @Test fun extension_conteneurPuisUrlPuisMp4() {
        assertEquals("mkv", DownloadPlan.extensionOf("http://h/movie/u/p/1.mp4", "MKV"))
        assertEquals("avi", DownloadPlan.extensionOf("http://h/movie/u/p/1.avi?x=1", null))
        assertEquals("mp4", DownloadPlan.extensionOf("http://h/movie/u/p/1", null))
        assertEquals("mp4", DownloadPlan.extensionOf("http://h/a.b/c", "../../etc"))
    }

    @Test fun nomDeFichier_neContientNiIdentifiantsNiTitre() {
        val name = DownloadPlan.fileName(7, DownloadKind.MOVIE, "12345", "mkv")
        assertTrue(name, Regex("^movie-[0-9a-f]{20}\\.mkv$").matches(name))
        assertFalse(name.contains("12345"))
        assertEquals(name, DownloadPlan.fileName(7, DownloadKind.MOVIE, "12345", "mkv"))
        assertFalse(name == DownloadPlan.fileName(8, DownloadKind.MOVIE, "12345", "mkv"))
    }

    @Test fun espaceLibre_margeToujoursGardee_tailleInconnueSupposee() {
        val m = DownloadPlan.SPACE_MARGIN_BYTES
        assertTrue(DownloadPlan.hasRoom(freeBytes = m + 1_000, remaining = 1_000))
        assertFalse(DownloadPlan.hasRoom(freeBytes = m + 999, remaining = 1_000))
        assertFalse(DownloadPlan.hasRoom(freeBytes = m + 1_000_000, remaining = -1))   // inconnue → 1 Go supposé
        assertTrue(DownloadPlan.hasRoom(freeBytes = m + DownloadPlan.UNKNOWN_SIZE_GUESS_BYTES, remaining = 0))
    }

    @Test fun connexion_avertitSeulementAvecUneSeuleConnexion() {
        assertTrue(DownloadConnectionPolicy.warnOnDownload(streamPlaying = true, maxConnections = 1))
        assertFalse(DownloadConnectionPolicy.warnOnDownload(streamPlaying = true, maxConnections = 2))
        assertFalse(DownloadConnectionPolicy.warnOnDownload(streamPlaying = false, maxConnections = 1))
        assertTrue(DownloadConnectionPolicy.warnOnPlay(downloadsRunning = true, maxConnections = 1))
        assertFalse(DownloadConnectionPolicy.warnOnPlay(downloadsRunning = false, maxConnections = 1))
    }

    // ── Ménage ──
    private fun row(id: Long, pid: Long, path: String) = DownloadEntity(
        id = id, providerId = pid, kind = DownloadKind.MOVIE, remoteId = "r$id", title = "T", urlHash = "h", filePath = path,
    )

    @Test fun menage_sourceSupprimee_sesLignesSontOrphelines() {
        val rows = listOf(row(1, 1, "/a"), row(2, 2, "/b"), row(3, 1, "/c"))
        assertEquals(listOf(2L), DownloadHousekeeping.orphanRows(rows, setOf(1L)).map { it.id })
        assertEquals(3, DownloadHousekeeping.orphanRows(rows, emptySet()).size)
    }

    @Test fun menage_fichiersInconnusAnciens_supprimes_partielsConnusGardes() {
        val known = File(tmp.root, "movie-1.mkv").apply { writeText("x") }
        val knownPart = DownloadPlan.partFile(known).apply { writeText("x") }
        val stray = File(tmp.root, "movie-2.mkv").apply { writeText("x") }
        val fresh = File(tmp.root, "movie-3.mkv.part").apply { writeText("x") }
        val now = System.currentTimeMillis()
        listOf(known, knownPart, stray).forEach { it.setLastModified(now - 120_000) }
        val out = DownloadHousekeeping.strayFiles(listOf(known, knownPart, stray, fresh), listOf(row(1, 1, known.absolutePath)), now)
        assertEquals(listOf(stray), out)
    }

    // ── Lecture hors ligne ──
    @Test fun horsLigne_elementTelecharge_luDepuisLeFichier() {
        val f = File(tmp.root, "movie-x.mkv").apply { writeText("video") }
        OfflineMedia.update(listOf(row(1, 4, f.absolutePath).copy(remoteId = "55", state = DownloadState.COMPLETED)))
        val item = PlaybackContext.Item(providerId = 4, kind = "MOVIE", remoteId = "55", title = "T", poster = null, streamUrl = "http://h/movie/u/p/55.mkv")
        assertEquals("file://${f.absolutePath}", OfflineMedia.resolve(item, item.streamUrl))
    }

    @Test fun horsLigne_directAutreUrlOuFichierAbsent_urlInchangee() {
        val f = File(tmp.root, "movie-y.mkv").apply { writeText("video") }
        OfflineMedia.update(listOf(row(1, 4, f.absolutePath).copy(remoteId = "55"), row(2, 4, File(tmp.root, "absent.mkv").absolutePath).copy(remoteId = "66")))
        val movie = PlaybackContext.Item(providerId = 4, kind = "MOVIE", remoteId = "55", title = "T", poster = null, streamUrl = "http://h/55.mkv")
        assertEquals("http://h/autre.ts", OfflineMedia.resolve(movie, "http://h/autre.ts"))
        val live = movie.copy(kind = "LIVE")
        assertEquals(live.streamUrl, OfflineMedia.resolve(live, live.streamUrl))
        val missing = movie.copy(remoteId = "66", streamUrl = "http://h/66.mkv")
        assertEquals(missing.streamUrl, OfflineMedia.resolve(missing, missing.streamUrl))
        assertEquals("http://h/x", OfflineMedia.resolve(null, "http://h/x"))
    }

    @Test fun emplacement_externeAbsent_repliSurLInterne() {
        val ext = tmp.newFolder("ext")
        val int = tmp.newFolder("int")
        assertEquals(File(ext, "downloads"), DownloadPrefs.resolveDirectory(DownloadPrefs.LOCATION_EXTERNAL, ext, int))
        assertEquals(File(int, "downloads"), DownloadPrefs.resolveDirectory(DownloadPrefs.LOCATION_EXTERNAL, null, int))
        assertEquals(File(int, "downloads"), DownloadPrefs.resolveDirectory(DownloadPrefs.LOCATION_INTERNAL, ext, int))
    }
}
