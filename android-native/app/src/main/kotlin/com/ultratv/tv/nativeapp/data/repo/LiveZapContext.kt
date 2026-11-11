package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contexte de lecture d'une chaîne lancée hors de l'écran Direct (accueil, mode simple) : élément « LIVE » (le lecteur
 * affiche alors l'OSD du direct et OK ouvre la liste) et file de zapping. File : les favoris si la chaîne en fait partie,
 * sinon SA catégorie dans l'ordre du Direct (fenêtre centrée sur la chaîne).
 */
@Singleton
class LiveZapContext @Inject constructor(
    private val channelDao: ChannelDao,
    private val queue: LivePlaybackQueue,
    private val playback: PlaybackContext,
    private val profiles: ProfileRepository,
    private val order: ChannelOrderRepository,
) {
    suspend fun prepare(providerId: Long, remoteId: String, title: String, logo: String?, streamUrl: String) {
        val favs = runCatching { order.favorites(providerId) }.getOrDefault(emptyList())
        val cur = favs.firstOrNull { it.remoteId == remoteId }
        if (cur != null) queue.set(favs, cur)
        else {
            val ch = runCatching { channelDao.byRemoteId(providerId, remoteId) }.getOrNull()
            val cat = ch?.categoryId
            val list = if (ch == null || cat == null) emptyList() else runCatching {
                // Une catégorie peut compter des milliers de chaînes : fenêtre autour de la chaîne.
                order.categoryWindow(providerId, cat, ch, QUEUE_WINDOW)
            }.getOrDefault(emptyList())
            if (ch != null && list.any { it.id == ch.id }) queue.set(list, ch) else queue.clear()
        }
        playback.set(PlaybackContext.Item(providerId, "LIVE", remoteId, title, logo, streamUrl))
    }

    private companion object { const val QUEUE_WINDOW = 300 }
}
