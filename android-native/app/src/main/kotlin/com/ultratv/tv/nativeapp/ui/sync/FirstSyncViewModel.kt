package com.ultratv.tv.nativeapp.ui.sync

import com.ultratv.tv.nativeapp.data.repo.atMostEvery
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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

    /**
     * Progression lissée à 4 images par seconde au plus : pas de recomposition continue sur box d'entrée de gamme.
     * atMostEvery et non sample : sample n'émet qu'au premier « tic » (250 ms), et combine attend toutes ses sources —
     * chaque démarrage à froid restait 250 ms de plus sur « Chargement… » avant l'accueil.
     */
    private val statusThrottled = bus.status.atMostEvery(250)

    /** `null` = pas encore résolu (Room n'a pas répondu) ; `Resolved(null)` = aucun écran de chargement à montrer. */
    // Partagé (WhileSubscribed) : relu seulement tant que l'écran de première synchro est affiché.
    private val counts = provider.observeProviders().flatMapLatest { ps ->
        val id = (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id ?: return@flatMapLatest kotlinx.coroutines.flow.flowOf(Triple(0, 0, 0))
        combine(channelDao.observeCount(id), movieDao.observeCount(id), seriesDao.observeCount(id)) { c, m, s -> Triple(c, m, s) }.atMostEvery(500)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private data class Inputs(val p: ProviderEntity?, val st: SyncStatusBus.Status?, val fail: SyncStatusBus.Failure?, val gone: Boolean)

    /**
     * Les compteurs (COUNT(*) sur les chaînes, films — jusqu'à 180 000 — et séries) ne servent qu'à l'écran de première
     * synchro : avant, combine les attendait à CHAQUE démarrage avant de laisser passer l'accueil.
     */
    val state: StateFlow<Resolved?> = combine(provider.observeProviders(), statusThrottled, bus.failure, dismissed) { ps, st, fail, gone ->
        Inputs(ps.firstOrNull { it.active } ?: ps.firstOrNull(), st, fail, gone)
    }.flatMapLatest { i ->
        val p = i.p
        if (p == null || !needsScreen(p, i.st, i.gone)) kotlinx.coroutines.flow.flowOf(Resolved(null))
        else counts.map { cnt -> build(p, i.st, i.fail, cnt) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun needsScreen(p: ProviderEntity, st: SyncStatusBus.Status?, gone: Boolean): Boolean {
        val allDone = firstSyncFinished(requiredParts(p.kind), { isDone(p, it) }, syncRunning = st != null)
        // Première synchro déjà faite : un échec de rafraîchissement n'est qu'une bannière fine (données locales intactes).
        // M3U local : tout est importé d'un coup, jamais d'écran de chargement.
        return !gone && !allDone && p.kind != "M3U_LOCAL"
    }

    private fun build(p: ProviderEntity, st: SyncStatusBus.Status?, fail: SyncStatusBus.Failure?, cnt: Triple<Int, Int, Int>): Resolved {
        val parts = requiredParts(p.kind)
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
        return Resolved(FirstSyncUi(p.id, p.name, p.kind, steps, percent, eta.etaSeconds(), isDone(p, SyncPart.LIVE), failure))
    }

    fun leaveToLive() { dismissed.value = true }
    fun dismiss() { dismissed.value = true }
    fun retry(providerId: Long) { sync.request(providerId, force = false) }
}
