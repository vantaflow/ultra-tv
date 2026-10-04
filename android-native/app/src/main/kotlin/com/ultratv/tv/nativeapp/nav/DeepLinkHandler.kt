package com.ultratv.tv.nativeapp.nav

import com.ultratv.tv.nativeapp.StartupNav
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.EpisodeDao
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Exécute un [DeepLink] : prépare le contexte de lecture et demande à l'application d'ouvrir l'écran. */
@Singleton
class DeepLinkHandler @Inject constructor(
    private val channelDao: ChannelDao,
    private val provider: ProviderRepository,
    private val playback: PlaybackContext,
    private val zapQueue: LivePlaybackQueue,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
) {
    /** Renvoie false si la cible n'existe plus dans le catalogue (source supprimée, chaîne retirée). */
    suspend fun handle(link: DeepLink): Boolean = when (link) {
        is DeepLink.PlayLive -> playLive(link.providerId, link.remoteId)
        is DeepLink.PlayMovie -> playMovie(link.providerId, link.remoteId)
        is DeepLink.PlayEpisode -> playEpisode(link.providerId, link.remoteId)
        is DeepLink.OpenSeries -> {
            val s = seriesDao.byRemoteId(link.providerId, link.remoteId)
            if (s != null) StartupNav.pendingRoute.value = Routes.seriesDetail(s.id)
            s != null
        }
        // Recherche vocale / globale : l'écran Recherche ouvre avec la requête déjà saisie.
        is DeepLink.Search -> { StartupNav.debugQuery.value = link.query; StartupNav.pendingRoute.value = Routes.SEARCH; true }
    }

    private suspend fun playMovie(providerId: Long, remoteId: String): Boolean {
        val m = movieDao.byRemoteId(providerId, remoteId) ?: return false
        val url = provider.resolveStalkerUrl(m.providerId, m.streamUrl)
        playback.set(PlaybackContext.Item(m.providerId, "MOVIE", m.remoteId, m.name, m.poster, url))
        StartupNav.pending.value = StartupNav.Pending(url, m.name)
        return true
    }

    private suspend fun playEpisode(providerId: Long, remoteId: String): Boolean {
        val e = episodeDao.byRemoteId(providerId, remoteId) ?: return false
        val series = seriesDao.byId(e.seriesId)
        val url = provider.resolveStalkerUrl(providerId, e.streamUrl)
        playback.set(PlaybackContext.Item(providerId, "EPISODE", e.remoteId, e.title, e.image ?: series?.poster, url, parentRemoteId = series?.remoteId))
        StartupNav.pending.value = StartupNav.Pending(url, e.title)
        return true
    }

    suspend fun playLive(providerId: Long, remoteId: String): Boolean {
        val channel = channelDao.byRemoteId(providerId, remoteId) ?: return false
        val url = provider.resolvePlayUrl(channel.id, channel.streamUrl)
        zapQueue.set(listOf(channel), channel)
        playback.set(PlaybackContext.Item(channel.providerId, "LIVE", channel.remoteId, channel.title, channel.logo, url))
        StartupNav.pending.value = StartupNav.Pending(url, channel.title)
        return true
    }
}
