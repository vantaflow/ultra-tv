package com.ultratv.tv.nativeapp.ui.sport

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.sport.League
import com.ultratv.tv.nativeapp.data.sport.LeagueLogic
import com.ultratv.tv.nativeapp.data.sport.LeagueMatch
import com.ultratv.tv.nativeapp.data.sport.LeaguePrefs
import com.ultratv.tv.nativeapp.data.sport.LeagueRepository
import com.ultratv.tv.nativeapp.data.sport.LeagueResult
import com.ultratv.tv.nativeapp.data.sport.MatchSheet
import com.ultratv.tv.nativeapp.data.sport.MatchState
import com.ultratv.tv.nativeapp.data.sport.LiveCardModel
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportHubLogic
import com.ultratv.tv.nativeapp.data.sport.StandingRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LeagueStatus { LOADING, OK, UNAVAILABLE, NOT_PAIRED }

/** Feuille de match ouverte : chargement, puis contenu (null = indisponible). */
@Immutable
data class SheetState(
    val match: LeagueMatch,
    val loading: Boolean = true,
    val sheet: MatchSheet? = null,
    /** Nom de la compétition affiché dans le bandeau. */
    val league: String? = null,
    /** Chaînes déjà trouvées par l'accueil (meilleure qualité d'abord), complétées par celles du match. */
    val channels: List<SportEntry> = emptyList(),
    /** Programme du guide quand aucun match de la source de scores n'est relié (feuille « programme »). */
    val programme: SportEntry? = null,
)

@Immutable
data class LeaguesState(
    val prefs: LeaguePrefs = LeaguePrefs.DEFAULT,
    val noSpoiler: Boolean = false,
    val selected: String? = null,
    val status: LeagueStatus = LeagueStatus.LOADING,
    val stale: Boolean = false,
    val matches: List<LeagueMatch> = emptyList(),
    val table: List<StandingRow>? = null,
    val revealed: Set<String> = emptySet(),
    val revealedTables: Set<String> = emptySet(),
    /** Matchs en direct de TOUTES les ligues visibles (cartes « En direct » du filtre « Tout »), par clé de ligue. */
    val liveAll: Map<String, List<LeagueMatch>> = emptyMap(),
) {
    val visible: List<League> get() = prefs.visible()
}

/**
 * Espace « Compétitions » : une ligue à la fois (donc quelques appels seulement, tous mémorisés par le dépôt). Le direct
 * n'est relu qu'à la minute, seulement tant que l'écran est affiché ET qu'un match de la ligue est (ou va être) en cours.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LeaguesViewModel @Inject constructor(
    private val repo: LeagueRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val revealed = MutableStateFlow(emptySet<String>())
    private val revealedTables = MutableStateFlow(emptySet<String>())

    private val prefs = profiles.currentSportLeagues.map { LeaguePrefs.decode(it) }.distinctUntilChanged()
    private val noSpoiler = profiles.currentSportNoSpoiler

    /** Ligue réellement affichée : le choix s'il est visible, sinon la première visible. */
    private val effective = combine(prefs, selected) { p, s -> p to (s?.takeIf { k -> p.visible().any { it.key == k } } ?: p.visible().firstOrNull()?.key) }
        .distinctUntilChanged()

    private data class Data(val status: LeagueStatus, val stale: Boolean, val matches: List<LeagueMatch>)

    private val data: StateFlow<Pair<String?, Data>> = effective.flatMapLatest { (_, key) ->
        if (key == null) kotlinx.coroutines.flow.flowOf<Pair<String?, Data>>(null to Data(LeagueStatus.OK, false, emptyList()))
        else flow<Pair<String?, Data>> {
            emit(key to Data(LeagueStatus.LOADING, false, emptyList()))
            var schedule: List<LeagueMatch> = emptyList()
            var live: List<LeagueMatch> = emptyList()
            var status = LeagueStatus.OK
            var stale = false
            while (true) {
                val now = System.currentTimeMillis()
                when (val s = repo.schedule(key, now)) {
                    is LeagueResult.Ok -> { schedule = s.value; stale = s.stale; status = LeagueStatus.OK }
                    LeagueResult.NotPaired -> status = LeagueStatus.NOT_PAIRED
                    LeagueResult.Unavailable -> status = if (schedule.isEmpty()) LeagueStatus.UNAVAILABLE else status
                }
                if (status == LeagueStatus.OK && LeagueLogic.mayBeLive(schedule + live, now)) {
                    (repo.live(key, now) as? LeagueResult.Ok)?.let { live = it.value }
                } else if (status == LeagueStatus.OK) live = emptyList()
                emit(key to Data(status, stale, LeagueLogic.merge(schedule, live)))
                if (status != LeagueStatus.OK) { delay(RETRY_MS); continue }
                delay(if (LeagueLogic.mayBeLive(schedule + live, System.currentTimeMillis())) LeagueRepository.LIVE_MS else IDLE_MS)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to Data(LeagueStatus.LOADING, false, emptyList()))

    /**
     * Directs de toutes les ligues visibles, relus toutes les 3 min hors du thread principal : le guide
     * du filtre « Tout » mélange les compétitions, leurs scores ne se trouvent pas dans la seule ligue affichée.
     */
    private val liveAll: StateFlow<Map<String, List<LeagueMatch>>> = prefs.flatMapLatest { p ->
        flow {
            val keys = p.visible().map { it.key }
            while (true) {
                val now = System.currentTimeMillis()
                val res = coroutineScope {
                    keys.map { k -> this@coroutineScope.async { k to ((repo.live(k, now) as? LeagueResult.Ok)?.value?.let(LeagueLogic::live).orEmpty()) } }
                        .map { it.await() }
                }.filter { it.second.isNotEmpty() }.toMap()
                emit(res)
                // Une requête par ligue visible : toutes les 3 min pour rester sous la limite du Worker (120 / 10 min / appareil).
                delay(ALL_LIVE_MS)
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Classement de la ligue affichée : lu (mémorisé 6 h) sauf, en mode sans spoiler, tant qu'on ne l'a pas affiché. */
    private val table = combine(effective, noSpoiler, revealedTables) { e, ns, rt -> Triple(e.second, ns, rt) }
        .distinctUntilChanged()
        .flatMapLatest { (key, ns, rt) ->
            flow {
                if (key == null || (ns && key !in rt)) { emit(key to null); return@flow }
                emit(key to null)
                emit(key to (repo.table(key) as? LeagueResult.Ok)?.value)
            }
        }

    val state: StateFlow<LeaguesState> = combine(prefs, noSpoiler, data, table, liveAll) { p, ns, d, tb, la ->
        LeaguesState(p, ns, d.first, d.second.status, d.second.stale, d.second.matches, tb.second.takeIf { tb.first == d.first }, liveAll = la)
    }.combine(revealed) { s, r -> s.copy(revealed = r) }
        .combine(revealedTables) { s, r -> s.copy(revealedTables = r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaguesState())

    private val _sheet = MutableStateFlow<SheetState?>(null)
    val sheet: StateFlow<SheetState?> = _sheet

    fun select(key: String) { selected.value = key }

    fun reveal(id: String) = revealed.update { it + id }
    fun revealTable(key: String) = revealedTables.update { it + key }
    /** « Afficher les résultats » : révèle les scores de tous les matchs de la ligue affichée. */
    fun revealAll(matches: List<LeagueMatch>) = revealed.update { it + matches.map { m -> m.id } }

    fun setNoSpoiler(on: Boolean) {
        viewModelScope.launch { profiles.setSportNoSpoiler(on) }
        if (on) { revealed.value = emptySet(); revealedTables.value = emptySet() }
    }

    fun move(key: String, delta: Int) { viewModelScope.launch { profiles.setSportLeagues(state.value.prefs.moved(key, delta).encode()) } }
    fun setHidden(key: String, hide: Boolean) { viewModelScope.launch { profiles.setSportLeagues(state.value.prefs.withHidden(key, hide).encode()) } }

    /** OK sur une carte « En direct » : feuille du match relié, sinon feuille du programme avec le choix de la chaîne. */
    fun openCard(c: LiveCardModel) {
        val m = c.match
        if (m != null) openMatch(m, c.league.ifEmpty { null }, c.channels)
        else _sheet.value = SheetState(SportHubLogic.guideMatch(c), loading = false, league = c.league.ifEmpty { null }, channels = c.channels, programme = c.channels.firstOrNull())
    }

    fun openMatch(m: LeagueMatch, league: String? = null, channels: List<SportEntry> = emptyList()) {
        _sheet.value = SheetState(m, league = league, channels = channels)
        viewModelScope.launch {
            val r = repo.sheet(m.id, finished = m.state == MatchState.POST)
            _sheet.update { cur -> if (cur?.match?.id == m.id) cur.copy(loading = false, sheet = (r as? LeagueResult.Ok)?.value) else cur }
        }
    }
    fun closeMatch() { _sheet.value = null }

    private companion object {
        const val IDLE_MS = 10 * 60_000L
        const val RETRY_MS = 60_000L
        const val ALL_LIVE_MS = 3 * 60_000L
    }
}
