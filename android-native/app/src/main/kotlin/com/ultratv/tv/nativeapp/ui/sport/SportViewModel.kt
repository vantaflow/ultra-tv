package com.ultratv.tv.nativeapp.ui.sport

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore
import com.ultratv.tv.nativeapp.data.recording.RecordingScheduler
import com.ultratv.tv.nativeapp.data.recording.ScheduleResult
import com.ultratv.tv.nativeapp.data.reminders.ReminderEntity
import com.ultratv.tv.nativeapp.data.reminders.RemindersScheduler
import com.ultratv.tv.nativeapp.data.repo.LiveZapContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.sport.SportDay
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportLogic
import com.ultratv.tv.nativeapp.data.sport.SportRepository
import com.ultratv.tv.nativeapp.data.xmltv.EpgState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Écussons d'équipes connus (clé repliée du nom → URL), fournis par l'écran Sport et la rangée de l'accueil. */
val LocalTeamBadges = androidx.compose.runtime.compositionLocalOf<Map<String, String>> { emptyMap() }

/** Rubrique Sport activée (Réglages › Affichage) : lue par les menus (rail TV, onglet « Plus » et rail tactiles). Faux par défaut. */
val LocalSportEnabled = staticCompositionLocalOf { false }

/** Icône « coupe » de la rubrique Sport. */
object SportIcons {
    const val Trophy = "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4v2a3 3 0 0 0 3 3M17 6h3v2a3 3 0 0 1-3 3"
}

/** État de l'écran Sport : listes déjà triées / regroupées (calculées hors du fil principal). */
@Immutable
data class SportState(
    val loading: Boolean = true,
    val hasSource: Boolean = true,
    val hasGuide: Boolean = true,
    val live: List<SportEntry> = emptyList(),
    val upcoming: List<SportDay> = emptyList(),
    val channels: List<ChannelEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SportViewModel @Inject constructor(
    provider: ProviderRepository,
    private val repo: SportRepository,
    syncBus: SyncStatusBus,
    private val liveZap: LiveZapContext,
    private val reminders: RemindersScheduler,
    private val recScheduler: RecordingScheduler,
    lockedStore: LockedChannelsStore,
    private val teamBadges: com.ultratv.tv.nativeapp.data.sport.TeamBadgeRepository,
) : ViewModel() {
    /** Source active (la première active, sinon la première) : même règle que l'accueil. */
    private val pid = provider.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    /** Une synchro (chaînes ou guide) est en cours : à sa fin, le cache de la rubrique est jeté. */
    private val syncing = combine(syncBus.epg.map { it is EpgState.Loading }, syncBus.status.map { it != null }) { a, b -> a || b }.distinctUntilChanged()

    private val minuteTick = flow { while (true) { emit(Unit); delay(TICK_MS) } }

    val state: StateFlow<SportState> = pid.flatMapLatest { id ->
        if (id == null) flowOf(SportState(loading = false, hasSource = false))
        else {
            var wasSyncing: Boolean? = null
            combine(syncing, minuteTick) { s, _ ->
                if (wasSyncing == true && !s) repo.invalidate()
                wasSyncing = s
                s
            }.mapLatest { _ ->
                val now = System.currentTimeMillis()
                runCatching {
                    val d = repo.load(id, now)
                    withContext(Dispatchers.Default) {
                        SportState(
                            loading = false, hasSource = true, hasGuide = d.hasGuide,
                            live = SportLogic.liveNow(d.entries, now),
                            upcoming = SportLogic.upcoming(d.entries, now),
                            channels = d.channels.take(MAX_CHANNELS_SHOWN),
                        )
                    }
                }.getOrElse { SportState(loading = false, hasSource = true, hasGuide = false) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportState())

    /**
     * Écussons des équipes des directs et de « À venir » (TheSportsDB via le Worker) : d'abord ceux déjà connus (mémoire,
     * préférences 30 jours), puis les autres, 4 à la fois, au plus 40 noms. Seuls des noms d'équipes partent vers le Worker.
     */
    val badges: StateFlow<Map<String, String>> = state
        .map { st -> com.ultratv.tv.nativeapp.data.sport.TeamBadgeLogic.namesOf(st.live + st.upcoming.flatMap { it.entries }) }
        .distinctUntilChanged()
        .flatMapLatest { names ->
            flow {
                val now = System.currentTimeMillis()
                val known = names.mapNotNull { n ->
                    val k = com.ultratv.tv.nativeapp.data.sport.TeamBadgeLogic.keyOf(n) ?: return@mapNotNull null
                    teamBadges.cached(n, now)?.badge?.let { k to it }
                }.toMap()
                emit(known)
                if (names.isNotEmpty()) emit(known + runCatching { teamBadges.badges(names, now) }.getOrDefault(emptyMap()))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val locked: StateFlow<Set<String>> = lockedStore.locked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Contexte « LIVE » + file de zapping (comme le Direct et le Guide) AVANT d'ouvrir le lecteur. */
    fun play(c: ChannelEntity, then: () -> Unit) {
        viewModelScope.launch {
            runCatching { liveZap.prepare(c.providerId, c.remoteId, c.title, c.logo, c.streamUrl) }
            then()
        }
    }

    fun addReminder(channel: ChannelEntity, prog: EpgEntity) {
        viewModelScope.launch {
            reminders.add(
                ReminderEntity(
                    providerId = channel.providerId, channelRemoteId = channel.remoteId, channelName = channel.name,
                    programmeTitle = prog.title, startMs = prog.startMs, endMs = prog.endMs,
                ),
            )
        }
    }

    fun record(channel: ChannelEntity, prog: EpgEntity, onResult: (ScheduleResult) -> Unit) {
        viewModelScope.launch { onResult(recScheduler.schedule(channel, prog, false)) }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val MAX_CHANNELS_SHOWN = 120
    }
}
