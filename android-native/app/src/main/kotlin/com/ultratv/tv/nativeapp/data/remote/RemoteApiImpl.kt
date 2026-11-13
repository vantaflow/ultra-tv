package com.ultratv.tv.nativeapp.data.remote

import com.ultratv.tv.nativeapp.StartupNav
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.nav.DeepLink
import com.ultratv.tv.nativeapp.nav.DeepLinkHandler
import com.ultratv.tv.nativeapp.ui.design.Ux
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation réelle : recherche = celle de l'appli (FTS + tolérance aux fautes, via [CatalogRepository.search]),
 * ouverture = mêmes chemins que les liens profonds ([DeepLinkHandler]) pour les films et séries.
 * Les identifiants échangés avec le téléphone sont les ids LOCAUX de la base ; aucune URL ne sort.
 */
@Singleton
class RemoteApiImpl @Inject constructor(
    private val provider: ProviderRepository,
    private val catalog: CatalogRepository,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val channelDao: ChannelDao,
    private val deepLinks: DeepLinkHandler,
    private val playback: PlaybackContext,
    private val zapQueue: LivePlaybackQueue,
    private val history: com.ultratv.tv.nativeapp.data.repo.HistoryRepository,
    private val bus: RemoteBus,
) : RemoteApi {
    /** Chaînes de la dernière recherche : elles forment la file de zapping (chaîne précédente / suivante). */
    @Volatile private var lastChannels: List<ChannelEntity> = emptyList()

    override suspend fun search(query: String, kind: String): List<RemoteHit> {
        val pid = provider.firstActiveId() ?: return emptyList()
        val r = catalog.search(pid, query, includePrograms = false)
        if (kind == RemoteKinds.LIVE) lastChannels = r.channels
        return RemoteHits.from(r, kind)
    }

    override suspend fun open(kind: String, id: Long): Boolean = when (kind) {
        // Film en cours : la lecture reprend où elle en était (le lecteur lit l'historique) ; sinon fiche.
        RemoteKinds.MOVIE -> movieDao.byId(id)?.let { m ->
            val hasResume = history.resumePositionMs(m.providerId, "MOVIE", m.remoteId) > 10_000
            deepLinks.handle(if (hasResume) DeepLink.PlayMovie(m.providerId, m.remoteId) else DeepLink.OpenMovie(m.providerId, m.remoteId))
        } ?: false
        RemoteKinds.SERIES -> seriesDao.byId(id)?.let { deepLinks.handle(DeepLink.OpenSeries(it.providerId, it.remoteId)) } ?: false
        RemoteKinds.LIVE -> channelDao.byId(id)?.let { playLive(it); true } ?: false
        else -> false
    }

    private fun playLive(c: ChannelEntity) {
        val queue = lastChannels.takeIf { l -> l.any { it.id == c.id } } ?: listOf(c)
        zapQueue.set(queue, c)
        playback.set(PlaybackContext.Item(c.providerId, "LIVE", c.remoteId, c.title, c.logo, c.streamUrl))
        StartupNav.pending.value = StartupNav.Pending(c.streamUrl, c.title)
    }

    override fun control(action: RemoteAction) = bus.send(action)

    override fun now(): RemoteNow {
        val item = playback.current.value
        val shown = Ux.playerActive
        return RemoteNow(if (shown) item?.title else null, if (shown) item?.kind?.lowercase() else null, if (shown) bus.playing.value else null, shown)
    }
}
