package com.ultratv.tv.nativeapp.nav

import com.ultratv.tv.nativeapp.StartupNav
import com.ultratv.tv.nativeapp.data.db.ChannelDao
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
) {
    /** Renvoie false si la cible n'existe plus dans le catalogue (source supprimée, chaîne retirée). */
    suspend fun handle(link: DeepLink): Boolean = when (link) {
        is DeepLink.PlayLive -> playLive(link.providerId, link.remoteId)
        else -> false
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
