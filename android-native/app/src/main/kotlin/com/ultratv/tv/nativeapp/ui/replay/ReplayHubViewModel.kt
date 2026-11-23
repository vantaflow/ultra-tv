package com.ultratv.tv.nativeapp.ui.replay

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.parental.ParentalLocks
import com.ultratv.tv.nativeapp.data.parental.ParentalUnlocks
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.replay.ReplayCategory
import com.ultratv.tv.nativeapp.data.replay.ReplayDay
import com.ultratv.tv.nativeapp.data.replay.ReplayEmpty
import com.ultratv.tv.nativeapp.data.replay.ReplayRow
import com.ultratv.tv.nativeapp.data.replay.ReplayRubric
import com.ultratv.tv.nativeapp.data.replay.ReplayService
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Liste de gauche de la rubrique : catégories et chaînes à archive affichables pour le profil courant. */
@Immutable
data class ReplayHubState(
    val loading: Boolean = true,
    val empty: ReplayEmpty? = null,
    val categories: List<ReplayCategory> = emptyList(),
    val category: String = ReplayRubric.ALL,
    val channels: List<ChannelEntity> = emptyList(),
)

/** Volet de droite : chaîne choisie, jours proposés, programmes passés du jour choisi. */
@Immutable
data class ReplayChannelState(
    val channel: ChannelEntity? = null,
    val days: List<ReplayDay> = emptyList(),
    val day: Int = 0,
    val rows: List<ReplayRow> = emptyList(),
    val loading: Boolean = false,
)

/**
 * Rubrique Replay. Source active seulement ; chaînes masquées du profil (SQL), catégories masquées / désactivées et adultes
 * en profil Enfants exclues ([ReplayRubric.visibleChannels]) ; chaînes verrouillées (une à une ou par catégorie) affichées
 * avec un cadenas, code exigé avant de voir leurs programmes. Relu à la fin de chaque synchro, jamais pendant.
 * La sélection (catégorie, chaîne, jour) vit ici : elle survit à l'aller-retour vers le lecteur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReplayHubViewModel @Inject constructor(
    provider: ProviderRepository,
    syncBus: SyncStatusBus,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val catalog: CatalogRepository,
    hiddenStore: HiddenCategoriesStore,
    private val profiles: ProfileRepository,
    private val parentalLocks: ParentalLocks,
    private val replay: ReplayService,
    private val zapQueue: LivePlaybackQueue,
    private val playback: PlaybackContext,
) : ViewModel() {
    private val pid = provider.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()
    // Relu au début et à la fin de chaque synchro (jamais à chaque lot inséré).
    private val idle = syncBus.status.map { it == null }.distinctUntilChanged()
    private val kids = profiles.current.map { it?.isKids == true }.distinctUntilChanged()

    private data class Base(val providerId: Long?, val empty: ReplayEmpty?, val visible: List<ChannelEntity>, val categories: List<ReplayCategory>)

    private val base = combine(pid, hiddenStore.hidden, kids, idle) { id, hidden, k, _ -> Triple(id, hidden, k) }
        .mapLatest { (id, hidden, k) ->
            if (id == null) return@mapLatest Base(null, ReplayEmpty.NO_SOURCE, emptyList(), emptyList())
            val now = System.currentTimeMillis()
            val archived = runCatching { channelDao.archivedChannels(id) }.getOrDefault(emptyList()).filter { ReplayRubric.supported(it, now) }
            val cats = runCatching { catalog.categories(id, "LIVE").first() }.getOrDefault(emptyList())
            val disabled = cats.filter { !it.enabled }.map { it.remoteId }.toSet()
            val visible = ReplayRubric.visibleChannels(archived, id, hidden, disabled, k, now)
            Base(id, ReplayRubric.emptyReason(true, archived.size, visible.size), visible, ReplayRubric.categories(visible, cats.filter { it.enabled }))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _category = MutableStateFlow(ReplayRubric.ALL)
    fun selectCategory(id: String) { _category.value = id }

    val state: StateFlow<ReplayHubState> = combine(base, _category) { b, cat ->
        if (b == null) ReplayHubState()
        else {
            val effective = if (cat == ReplayRubric.ALL || b.categories.any { it.id == cat }) cat else ReplayRubric.ALL
            ReplayHubState(false, b.empty, b.categories, effective, ReplayRubric.filter(b.visible, effective))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReplayHubState())

    /** Chaînes qui exigent le code (verrouillées une à une ou par catégorie) : cadenas de la liste. */
    val locked: StateFlow<Set<String>> = parentalLocks.effective.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Code déjà saisi pendant cette visite de la rubrique (la chaîne reste ouverte jusqu'à la sortie de l'écran). */
    private val unlocked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val unlockRev = MutableStateFlow(0)

    /**
     * La chaîne exige-t-elle le code ? Lit la liste RÉELLE du magasin (pas la valeur initiale vide d'un StateFlow pas encore
     * alimenté, qui laisserait passer une chaîne verrouillée au premier accès).
     */
    suspend fun needsPin(c: ChannelEntity): Boolean = keyOf(c) !in unlocked && keyOf(c) in parentalLocks.effective.first()
    fun markUnlocked(c: ChannelEntity) { unlocked += keyOf(c); unlockRev.value++ }

    private val _channelId = MutableStateFlow<Long?>(null)
    private val _day = MutableStateFlow(0)
    val selectedChannelId: StateFlow<Long?> = _channelId

    /** Choisit la chaîne (le code a déjà été vérifié par l'écran) : on repart sur « Aujourd'hui » si elle change. */
    fun selectChannel(c: ChannelEntity) {
        if (_channelId.value != c.id) _day.value = 0
        _channelId.value = c.id
    }
    fun clearChannel() { _channelId.value = null }
    fun selectDay(offset: Int) { _day.value = offset }

    val channel: StateFlow<ReplayChannelState> = combine(_channelId, _day, base, unlockRev) { id, d, b, _ -> Triple(id, d, b) }
        .mapLatest { (id, d, b) ->
            val c = id?.let { cid -> b?.visible?.firstOrNull { it.id == cid } } ?: return@mapLatest ReplayChannelState()
            // Chaîne verrouillée (code jamais saisi pendant cette visite) : rien n'est montré.
            if (needsPin(c)) return@mapLatest ReplayChannelState()
            val now = System.currentTimeMillis()
            val days = ReplayRubric.days(c.catchupDays, now)
            val day = days.firstOrNull { it.offset == d } ?: days.firstOrNull() ?: return@mapLatest ReplayChannelState(c)
            val progs: List<EpgEntity> = runCatching { epgDao.forChannelInRange(c.id, day.startMs, day.endMs) }.getOrDefault(emptyList())
            ReplayChannelState(c, days, day.offset, ReplayRubric.rows(c, progs, day, now))
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReplayChannelState())

    /**
     * Lecture depuis le début par le rattrapage existant : contexte LIVE de la chaîne (comme le Guide), file de zapping de la
     * liste affichée, programme armé pour le repli d'URL du lecteur. Rien si le programme n'est plus rejouable.
     */
    fun play(c: ChannelEntity, prog: EpgEntity, onReady: (url: String, title: String) -> Unit) {
        viewModelScope.launch {
            val url = replay.urlFor(c, prog) ?: return@launch
            val list = state.value.channels
            val at = list.indexOfFirst { it.id == c.id }
            val window = if (at < 0) listOf(c) else list.subList((at - 200).coerceAtLeast(0), (at + 201).coerceAtMost(list.size))
            zapQueue.set(window.ifEmpty { listOf(c) }, c)
            playback.set(PlaybackContext.Item(c.providerId, "LIVE", c.remoteId, c.title, c.logo, c.streamUrl))
            if (keyOf(c) in parentalLocks.effective.first()) ParentalUnlocks.grant(c)
            replay.armFromGuide(prog, c.providerId)
            onReady(url, prog.title.ifBlank { c.title })
        }
    }

    private fun keyOf(c: ChannelEntity) = "${c.providerId}:${c.remoteId}"
}
