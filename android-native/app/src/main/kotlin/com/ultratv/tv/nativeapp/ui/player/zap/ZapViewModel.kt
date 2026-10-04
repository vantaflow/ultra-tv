package com.ultratv.tv.nativeapp.ui.player.zap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.prefs.ChannelRef
import com.ultratv.tv.nativeapp.data.prefs.RecentChannelsStore
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Chaîne affichée dans la liste « Récentes » (maquette Zapping). */
data class RecentEntry(val channel: ChannelEntity, val number: Int, val now: String?)

/** Aperçu pendant la saisie : « 105 · Moteurs · Grand Prix » ; [channelTitle] null = numéro inconnu. */
data class NumberPreview(val digits: String, val channelTitle: String?, val nowTitle: String?)

/**
 * Zapping rapide : saisie du numéro, chaîne précédente, récentes. Séparé de PlayerViewModel pour limiter
 * l'empreinte sur PlayerScreen. L'appelant reçoit l'URL à (re)lancer ; l'ancien flux est fermé par PlaybackSession.start.
 */
@HiltViewModel
class ZapViewModel @Inject constructor(
    private val playback: PlaybackContext,
    private val queue: LivePlaybackQueue,
    private val provider: ProviderRepository,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val recents: RecentChannelsStore,
) : ViewModel() {
    private val entry = NumberEntry()
    private val _preview = MutableStateFlow<NumberPreview?>(null)
    val preview: StateFlow<NumberPreview?> = _preview
    private val _recent = MutableStateFlow<List<RecentEntry>>(emptyList())
    val recent: StateFlow<List<RecentEntry>> = _recent
    private var commitJob: Job? = null
    private var onUrl: (String) -> Unit = {}
    /** Clé de la chaîne regardée juste avant l'actuelle (Retour). */
    private var previous: ChannelRef? = null
    private var currentRef: ChannelRef? = null

    val isEntering: Boolean get() = entry.isActive

    /** À appeler à chaque changement de chaîne live : alimente les récentes et la « chaîne précédente ». */
    fun onPlaying(item: PlaybackContext.Item?) {
        if (item == null || item.kind != "LIVE") return
        val ref = ChannelRef(item.providerId, item.remoteId)
        if (ref == currentRef) return
        previous = currentRef ?: previous
        currentRef = ref
        viewModelScope.launch { recents.record(ref) }
    }

    fun hasPrevious(): Boolean = previous != null

    /** Chiffre tapé : met à jour l'aperçu et (re)lance le délai de validation de 1,5 s. */
    fun digit(d: Int, onUrl: (String) -> Unit) {
        this.onUrl = onUrl
        entry.append(d, System.currentTimeMillis())
        refreshPreview()
        commitJob?.cancel()
        commitJob = viewModelScope.launch { delay(entry.autoCommitMs); commit() }
    }

    fun cancelEntry() { commitJob?.cancel(); entry.clear(); _preview.value = null }

    /** OK : valide tout de suite. */
    fun commitNow() { commitJob?.cancel(); viewModelScope.launch { commit() } }

    private suspend fun commit() {
        val n = entry.value
        entry.clear()
        _preview.value = null
        if (n == null) return
        val ch = queue.jumpToNumber(n) ?: return
        switchTo(ch)
    }

    private fun refreshPreview() {
        val digits = entry.text
        val n = entry.value
        viewModelScope.launch {
            val ch = n?.let { queue.peekNumber(it) }
            _preview.value = NumberPreview(digits, ch?.title, ch?.let { nowTitle(it) })
        }
    }

    private suspend fun nowTitle(c: ChannelEntity): String? {
        val now = System.currentTimeMillis()
        return epgDao.rangeForChannels(listOf(c.id), now, now + 1).firstOrNull { it.startMs <= now && it.endMs > now }?.title
    }

    private suspend fun switchTo(target: ChannelEntity) {
        val url = target.streamUrl
        playback.set(PlaybackContext.Item(
            providerId = target.providerId, kind = "LIVE", remoteId = target.remoteId, title = target.title, poster = target.logo, streamUrl = url,
            badge = TitleCleaner.prefixBadge(target.name, TitleCleaner.clean(target.name, live = true).title),
        ))
        onUrl(url)
    }

    /** Retour : revient à la chaîne précédente. Faux s'il n'y en a pas. */
    fun recallPrevious(onUrl: (String) -> Unit): Boolean {
        val ref = previous ?: return false
        this.onUrl = onUrl
        viewModelScope.launch {
            val ch = channelDao.byRemoteId(ref.providerId, ref.remoteId) ?: return@launch
            queue.state.value?.let { queue.set(it.channels, ch) }
            switchTo(ch)
        }
        return true
    }

    /** Charge la liste « Récentes » (appelée à l'affichage de la surcouche). */
    fun loadRecent() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            _recent.value = recents.recent.first().mapNotNull { ref ->
                val c = channelDao.byRemoteId(ref.providerId, ref.remoteId) ?: return@mapNotNull null
                RecentEntry(c, ZapLogic.numberOf(c), nowTitle(c))
            }
        }
    }
}
