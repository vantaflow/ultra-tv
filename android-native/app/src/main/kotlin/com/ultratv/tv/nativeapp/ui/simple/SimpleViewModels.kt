package com.ultratv.tv.nativeapp.ui.simple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.HistoryRepository
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Source active (la première active, sinon la première) : même règle que l'accueil. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun ProviderRepository.activeId(): Flow<Long?> =
    observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

/**
 * Écran des grandes tuiles : deux requêtes légères seulement (reprise et dernière chaîne), aucune des rangées de
 * l'accueil habituel — important sur une box lente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SimpleHomeViewModel @Inject constructor(
    provider: ProviderRepository,
    history: HistoryRepository,
    private val playback: PlaybackContext,
    private val channelDao: ChannelDao,
    private val queue: LivePlaybackQueue,
    lockedStore: LockedChannelsStore,
    private val profiles: ProfileRepository,
    private val liveZap: com.ultratv.tv.nativeapp.data.repo.LiveZapContext,
) : ViewModel() {
    private val pid = provider.activeId()

    /** Null tant que la base n'a pas répondu ; sinon : y a-t-il une source ? */
    val hasSource: StateFlow<Boolean?> = provider.observeProviders().map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val resume: StateFlow<WatchHistoryEntity?> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else history.continueWatching(id, 12) }
        .map { SimpleHome.resumeItem(it) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastLive: StateFlow<WatchHistoryEntity?> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else history.recentByKind(id, "LIVE", 3) }
        .map { SimpleHome.lastLive(it) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val locked: StateFlow<Set<String>> = lockedStore.locked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Plusieurs profils : le bouton « Changer de profil » n'est proposé que s'il a un sens. */
    val multipleProfiles: StateFlow<Boolean> = profiles.profiles.map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun requestProfileSwitch() = profiles.requestSwitch()

    /** Contexte de lecture d'un film / épisode à reprendre (le lecteur reprend à la position enregistrée). */
    fun prepareResume(h: WatchHistoryEntity) {
        queue.clear()
        playback.set(PlaybackContext.Item(
            providerId = h.providerId, kind = h.kind, remoteId = h.remoteId, title = h.title,
            poster = h.poster, streamUrl = h.streamUrl, parentRemoteId = h.parentRemoteId,
        ))
    }

    /**
     * Contexte de lecture de la dernière chaîne. File de zapping : les favoris si la chaîne en fait partie (haut / bas
     * du lecteur passe d'une chaîne favorite à l'autre), sinon SA catégorie dans l'ordre du Direct. Avant, une chaîne hors
     * favoris n'avait aucune file : haut / bas ne changeaient plus de chaîne (retour Reddit).
     */
    suspend fun prepareLastLive(h: WatchHistoryEntity) = liveZap.prepare(h.providerId, h.remoteId, h.title, h.poster, h.streamUrl)
}

/** Ligne de la liste de chaînes à gros caractères. */
data class SimpleChannelRow(val channel: ChannelEntity, val favorite: Boolean, val locked: Boolean)

/** Liste de chaînes du mode simple : favoris d'abord, puis les premières chaînes de la source. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SimpleChannelsViewModel @Inject constructor(
    provider: ProviderRepository,
    catalog: CatalogRepository,
    private val playback: PlaybackContext,
    private val queue: LivePlaybackQueue,
    lockedStore: LockedChannelsStore,
) : ViewModel() {
    private val pid = provider.activeId()

    /** Null tant que la lecture n'a pas répondu (aucun message « vide » prématuré). */
    val rows: StateFlow<List<SimpleChannelRow>?> = pid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList<ChannelEntity>() to emptyList())
        else combine(catalog.favoriteChannels(id, SimpleHome.MAX_CHANNELS), catalog.firstChannels(id, SimpleHome.MAX_CHANNELS)) { f, o -> f to o }
    }.combine(lockedStore.locked) { (favs, others), locked ->
        val favIds = favs.map { it.id }.toSet()
        SimpleHome.orderChannels(favs, others).map { c ->
            SimpleChannelRow(c, SimpleHome.isFavorite(c, favIds), "${c.providerId}:${c.remoteId}" in locked)
        }
    }.map<List<SimpleChannelRow>, List<SimpleChannelRow>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Prépare la lecture (contexte + file de zapping : la liste affichée) ; renvoie l'adresse du flux. */
    fun prepare(c: ChannelEntity, list: List<SimpleChannelRow>): String {
        queue.set(list.map { it.channel }, c)
        playback.set(PlaybackContext.Item(c.providerId, "LIVE", c.remoteId, c.title, c.logo, c.streamUrl))
        return c.streamUrl
    }
}
