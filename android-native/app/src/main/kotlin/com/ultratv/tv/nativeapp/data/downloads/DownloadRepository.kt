package com.ultratv.tv.nativeapp.data.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'une demande de téléchargement (message adapté côté interface). */
enum class EnqueueResult { QUEUED, ALREADY, NOT_DOWNLOADABLE, NO_SPACE, SOURCE_MISSING }

/**
 * Téléchargements hors ligne (Android) : file d'attente en base, exécutée par [DownloadWorker] (un seul travail
 * WorkManager unique, 2 fichiers à la fois). Toutes les actions (pause, reprise, annulation, nouvel essai) passent par
 * la machine d'états [DownloadState] : le travailleur relit l'état de la ligne et s'arrête de lui-même.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: DownloadDao,
    private val providerDao: com.ultratv.tv.nativeapp.data.db.ProviderRawDao,
    private val movieDao: com.ultratv.tv.nativeapp.data.db.MovieDao,
    private val episodeDao: com.ultratv.tv.nativeapp.data.db.EpisodeDao,
    private val prefs: DownloadPrefs,
) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()
    fun observeItem(providerId: Long, kind: String, remoteId: String): Flow<DownloadEntity?> = dao.observeItem(providerId, kind, remoteId)
    fun observeForSeries(providerId: Long, seriesRemoteId: String): Flow<List<DownloadEntity>> = dao.observeForSeries(providerId, seriesRemoteId)
    fun observeRunning(): Flow<Boolean> = dao.observeRunningCount().map { it > 0 }.distinctUntilChanged()

    suspend fun enqueueMovie(m: MovieEntity): EnqueueResult = enqueue(
        providerId = m.providerId, kind = DownloadKind.MOVIE, remoteId = m.remoteId, parent = null,
        title = m.name, poster = m.poster, season = null, episode = null, url = m.streamUrl, container = m.container,
    )

    suspend fun enqueueEpisode(series: SeriesEntity, ep: EpisodeEntity): EnqueueResult = enqueue(
        providerId = series.providerId, kind = DownloadKind.EPISODE, remoteId = ep.remoteId, parent = series.remoteId,
        title = episodeTitle(series.name, ep), poster = ep.image ?: series.poster ?: series.backdrop,
        season = ep.season, episode = ep.episode, url = ep.streamUrl, container = ep.container,
    )

    /** « Télécharger la saison » : chaque épisode absent est mis en file ; renvoie (nombre ajouté, premier refus éventuel). */
    suspend fun enqueueEpisodes(series: SeriesEntity, eps: List<EpisodeEntity>): Pair<Int, EnqueueResult?> {
        var added = 0
        var refusal: EnqueueResult? = null
        for (ep in eps.sortedWith(compareBy({ it.season }, { it.episode }))) {
            when (val r = enqueueEpisode(series, ep)) {
                EnqueueResult.QUEUED -> added++
                EnqueueResult.ALREADY -> Unit
                else -> if (refusal == null) refusal = r
            }
            if (refusal == EnqueueResult.NO_SPACE) break
        }
        return added to refusal
    }

    private suspend fun enqueue(
        providerId: Long, kind: String, remoteId: String, parent: String?, title: String, poster: String?,
        season: Int?, episode: Int?, url: String, container: String?,
    ): EnqueueResult {
        dao.byItem(providerId, kind, remoteId)?.let { existing ->
            // Déjà connu : un échec ou une pause repart simplement en file.
            if (existing.state == DownloadState.FAILED) retry(existing.id)
            if (existing.state == DownloadState.PAUSED) resume(existing.id)
            return EnqueueResult.ALREADY
        }
        if (url.isBlank()) return EnqueueResult.SOURCE_MISSING
        if (!DownloadPlan.isDownloadable(url)) return EnqueueResult.NOT_DOWNLOADABLE
        val dir = prefs.directory()
        if (!dir.exists()) dir.mkdirs()
        // Taille encore inconnue ici : refus si le volume est déjà presque plein (contrôle exact au démarrage, avec Content-Length).
        if (freeBytes(dir) < DownloadPlan.SPACE_MARGIN_BYTES * 2) return EnqueueResult.NO_SPACE
        val file = File(dir, DownloadPlan.fileName(providerId, kind, remoteId, DownloadPlan.extensionOf(url, container)))
        val inserted = runCatching {
            dao.insert(
                DownloadEntity(
                    providerId = providerId, kind = kind, remoteId = remoteId, parentRemoteId = parent, title = title,
                    poster = poster, season = season, episode = episode, urlHash = DownloadPlan.sha256(url), filePath = file.absolutePath,
                ),
            )
        }.isSuccess
        if (!inserted) return EnqueueResult.ALREADY   // double appui : l'index unique a refusé le doublon
        kick()
        return EnqueueResult.QUEUED
    }

    suspend fun pause(id: Long) = transition(id, DownloadState.Event.PAUSE)

    suspend fun resume(id: Long) { if (transition(id, DownloadState.Event.RESUME)) kick() }

    suspend fun retry(id: Long) { if (transition(id, DownloadState.Event.RETRY)) kick() }

    /** Annuler (en cours) ou supprimer (terminé) : ligne ET fichiers ; le travailleur voit la ligne disparaître et s'arrête. */
    suspend fun delete(id: Long) {
        val d = dao.byId(id) ?: return
        dao.delete(id)
        deleteFiles(d.filePath)
    }

    private suspend fun transition(id: Long, event: DownloadState.Event): Boolean {
        val d = dao.byId(id) ?: return false
        val next = DownloadState.next(d.state, event) ?: return false
        dao.setState(id, next, if (next == DownloadState.QUEUED) null else d.error)
        return true
    }

    /** Lance (ou prolonge) le travail unique ; à la suite d'un travail en cours pour ne jamais en avoir deux en parallèle. */
    fun kick() = enqueueWork(ExistingWorkPolicy.APPEND_OR_REPLACE)

    /** Wi-Fi seulement modifié : le travail est remplacé avec la nouvelle contrainte (les partiels sont repris). */
    fun setWifiOnly(v: Boolean) {
        prefs.setWifiOnly(v)
        enqueueWork(ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueWork(policy: ExistingWorkPolicy) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (prefs.state.value.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(DownloadWorker.TAG)
            .build()
        runCatching { WorkManager.getInstance(ctx).enqueueUniqueWork(DownloadWorker.WORK_NAME, policy, req) }
    }

    fun freeBytes(dir: File = prefs.directory()): Long = runCatching {
        var d: File? = dir
        while (d != null && !d.exists()) d = d.parentFile
        android.os.StatFs((d ?: ctx.filesDir).path).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    /**
     * Au lancement de l'application (fil d'arrière-plan) : table de lecture hors ligne, purge des téléchargements des
     * sources supprimées, et reprise de la file si des téléchargements attendaient.
     */
    fun start(scope: CoroutineScope) {
        scope.launch { runCatching { dao.observeCompleted().collect { OfflineMedia.update(it) } } }
        scope.launch {
            runCatching {
                providerDao.observeAll().map { list -> list.map { it.id }.toSet() }.distinctUntilChanged().collect { ids -> purgeOrphans(ids) }
            }
        }
        scope.launch { runCatching { if (dao.pendingCount() > 0) kick() } }
    }

    /** Source supprimée → ses téléchargements (lignes + fichiers) ; puis fichiers du dossier qu'aucune ligne ne référence. */
    suspend fun purgeOrphans(providerIds: Set<Long>) {
        val rows = dao.all()
        for (d in DownloadHousekeeping.orphanRows(rows, providerIds)) { dao.delete(d.id); deleteFiles(d.filePath) }
        val kept = dao.all()
        val dirs = listOfNotNull(
            ctx.getExternalFilesDir(null)?.let { File(it, DownloadPrefs.DIR_NAME) },
            File(ctx.filesDir, DownloadPrefs.DIR_NAME),
        )
        for (dir in dirs) {
            val files = dir.listFiles()?.toList().orEmpty()
            DownloadHousekeeping.strayFiles(files, kept).forEach { runCatching { it.delete() } }
        }
    }

    /** Résout l'URL ACTUELLE du flux dans le catalogue (jamais stockée avec le téléchargement). */
    suspend fun currentUrl(d: DownloadEntity): String? = when (d.kind) {
        DownloadKind.MOVIE -> movieDao.byRemoteId(d.providerId, d.remoteId)?.streamUrl
        DownloadKind.EPISODE -> episodeDao.byRemoteId(d.providerId, d.remoteId)?.streamUrl
        else -> null
    }

    companion object {
        fun deleteFiles(path: String) {
            val f = File(path)
            runCatching { f.delete() }
            runCatching { DownloadPlan.partFile(f).delete() }
        }

        fun episodeTitle(seriesName: String, ep: EpisodeEntity): String {
            val tag = "S${"%02d".format(java.util.Locale.ROOT, ep.season)}E${"%02d".format(java.util.Locale.ROOT, ep.episode)}"
            return listOf(seriesName, tag, ep.title).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }
}

/** Ménage (fonctions pures, testées) : lignes des sources disparues, fichiers que plus aucune ligne ne référence. */
object DownloadHousekeeping {
    fun orphanRows(rows: List<DownloadEntity>, providerIds: Set<Long>): List<DownloadEntity> = rows.filter { it.providerId !in providerIds }

    /** Fichiers inconnus de la base ; ceux modifiés depuis moins d'une minute sont épargnés (téléchargement qui démarre). */
    fun strayFiles(files: List<File>, rows: List<DownloadEntity>, nowMs: Long = System.currentTimeMillis()): List<File> {
        val known = rows.flatMap { listOf(File(it.filePath).absolutePath, DownloadPlan.partFile(File(it.filePath)).absolutePath) }.toSet()
        return files.filter { it.isFile && it.absolutePath !in known && nowMs - it.lastModified() > 60_000 }
    }
}
