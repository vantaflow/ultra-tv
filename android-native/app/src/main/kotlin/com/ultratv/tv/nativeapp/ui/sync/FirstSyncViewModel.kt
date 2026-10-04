package com.ultratv.tv.nativeapp.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SyncPart
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class Resolved(val ui: FirstSyncUi?)

enum class StepState { DONE, ACTIVE, WAIT, ERROR }
data class StepUi(val part: SyncPart, val state: StepState, val count: Int?, val total: Int? = null)

data class FirstSyncUi(
    val providerId: Long,
    val providerName: String,
    val kind: String,
    val steps: List<StepUi>,
    val percent: Int,
    val etaSeconds: Int?,
    val liveReady: Boolean,
    val failure: SyncErrorKind?,
)

/** Parties à afficher selon le type de source (M3U : le direct seul ; Xtream : tout). */
fun requiredParts(kind: String): List<SyncPart> = when (kind) {
    "M3U", "M3U_LOCAL" -> listOf(SyncPart.LIVE)
    "XTREAM" -> listOf(SyncPart.LIVE, SyncPart.VOD, SyncPart.SERIES, SyncPart.EPG)
    else -> emptyList()   // type de source hérité non pris en charge (ex. Stalker, retiré en 1.1.1) : rien à attendre
}

fun isDone(p: ProviderEntity, part: SyncPart): Boolean = when (part) {
    SyncPart.LIVE -> p.lastLiveSyncAt > 0
    SyncPart.VOD -> p.lastVodSyncAt > 0
    SyncPart.SERIES -> p.lastSeriesSyncAt > 0
    SyncPart.EPG -> p.lastEpgSyncAt > 0
}

/**
 * Fin de la première synchro : toutes les parties faites, OU catalogue (direct, films, séries) fait et plus aucune
 * synchro en cours. Le guide ne doit pas retenir l'écran : s'il échoue (flux XMLTV absent, réseau limité), il est
 * retenté au TTL suivant au lieu de remontrer « Préparation du catalogue » à chaque démarrage.
 */
fun firstSyncFinished(parts: List<SyncPart>, done: (SyncPart) -> Boolean, syncRunning: Boolean): Boolean =
    parts.all(done) || (!syncRunning && parts.filter { it != SyncPart.EPG }.all(done))

/**
 * Écran de première synchronisation : visible tant que la source active n'a pas fini sa PREMIÈRE
 * synchro (toutes parties) et que l'utilisateur ne l'a pas quitté via « Regarder le direct ».
 * Les synchros suivantes n'utilisent PAS cet écran (pastille discrète dans le rail).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class FirstSyncViewModel @Inject constructor(
    provider: ProviderRepository,
    bus: SyncStatusBus,
    private val sync: SyncCoordinator,
    channelDao: com.ultratv.tv.nativeapp.data.db.ChannelDao,
    movieDao: com.ultratv.tv.nativeapp.data.db.MovieDao,
    seriesDao: com.ultratv.tv.nativeapp.data.db.SeriesDao,
) : ViewModel() {

    private val dismissed = MutableStateFlow(false)
    private val eta = EtaEstimator()

    /** Progression lissée à 4 images par seconde au plus : pas de recomposition continue sur box d'entrée de gamme. */
    private val statusThrottled = bus.status.sample(250)

    /** `null` = pas encore résolu (Room n'a pas répondu) ; `Resolved(null)` = aucun écran de chargement à montrer. */
    private val counts = provider.observeProviders().flatMapLatest { ps ->
        val id = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id ?: return@flatMapLatest kotlinx.coroutines.flow.flowOf(Triple(0, 0, 0))
        combine(channelDao.observeCount(id), movieDao.observeCount(id), seriesDao.observeCount(id)) { c, m, s -> Triple(c, m, s) }
    }

    val state: StateFlow<Resolved?> = combine(provider.observeProviders(), statusThrottled, bus.failure, dismissed, counts) { ps, st, fail, gone, cnt ->
        val p = ps.firstOrNull { it.active } ?: ps.firstOrNull() ?: return@combine Resolved(null)
        val parts = requiredParts(p.kind)
        val allDone = firstSyncFinished(parts, { isDone(p, it) }, syncRunning = st != null)
        // Première synchro déjà faite : un échec de rafraîchissement n'est qu'une bannière fine (données locales intactes).
        if (gone || allDone) return@combine Resolved(null)
        // M3U local : tout est importé d'un coup, jamais d'écran de chargement.
        if (p.kind == "M3U_LOCAL") return@combine Resolved(null)
        val failure = fail?.takeIf { it.providerId == p.id }?.kind
        val firstUndone = parts.firstOrNull { !isDone(p, it) }
        val steps = parts.map { part ->
            val state = when {
                isDone(p, part) -> StepState.DONE
                failure != null && part == firstUndone -> StepState.ERROR
                st?.part == part -> StepState.ACTIVE
                // M3U n'annonce pas de partie : la première non terminée est active.
                st != null && st.part == null && part == firstUndone -> StepState.ACTIVE
                else -> StepState.WAIT
            }
            val total = when (part) { SyncPart.LIVE -> cnt.first; SyncPart.VOD -> cnt.second; SyncPart.SERIES -> cnt.third; SyncPart.EPG -> null }
            StepUi(part, state, if (state == StepState.ACTIVE) st?.count else null, if (state == StepState.DONE) total else null)
        }
        val doneCount = steps.count { it.state == StepState.DONE }
        val percent = when {
            st?.percent != null -> st.percent
            else -> doneCount * 100 / steps.size
        }.coerceIn(0, 100)
        if (st != null) eta.add(System.currentTimeMillis(), percent) else eta.reset()
        Resolved(FirstSyncUi(p.id, p.name, p.kind, steps, percent, eta.etaSeconds(), isDone(p, SyncPart.LIVE), failure))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun leaveToLive() { dismissed.value = true }
    fun dismiss() { dismissed.value = true }
    fun retry(providerId: Long) { sync.request(providerId, force = false) }
}
