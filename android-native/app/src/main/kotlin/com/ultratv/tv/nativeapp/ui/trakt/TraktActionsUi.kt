package com.ultratv.tv.nativeapp.ui.trakt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.tmdb.TmdbDao
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.data.trakt.EpisodeRef
import com.ultratv.tv.nativeapp.data.trakt.TraktActions
import com.ultratv.tv.nativeapp.data.trakt.TraktLibrary
import com.ultratv.tv.nativeapp.data.trakt.TraktLibraryRepository
import com.ultratv.tv.nativeapp.data.trakt.TraktPrefs
import com.ultratv.tv.nativeapp.data.trakt.TraktPrefsStore
import com.ultratv.tv.nativeapp.data.trakt.TraktWork
import com.ultratv.tv.nativeapp.data.trakt.TraktWriteResult
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.traktAddWatchlist
import com.ultratv.tv.nativeapp.i18n.traktAutoWatched
import com.ultratv.tv.nativeapp.i18n.traktAutoWatchedHint
import com.ultratv.tv.nativeapp.i18n.traktAutoWatchedHintOn
import com.ultratv.tv.nativeapp.i18n.traktButton
import com.ultratv.tv.nativeapp.i18n.traktEpisodesHint
import com.ultratv.tv.nativeapp.i18n.traktFailed
import com.ultratv.tv.nativeapp.i18n.traktInWatchlist
import com.ultratv.tv.nativeapp.i18n.traktListsHome
import com.ultratv.tv.nativeapp.i18n.traktListsHomeHint
import com.ultratv.tv.nativeapp.i18n.traktMarkUnwatched
import com.ultratv.tv.nativeapp.i18n.traktMarkWatched
import com.ultratv.tv.nativeapp.i18n.traktNotLinked
import com.ultratv.tv.nativeapp.i18n.traktRate
import com.ultratv.tv.nativeapp.i18n.traktRateTitle
import com.ultratv.tv.nativeapp.i18n.traktRemoveRating
import com.ultratv.tv.nativeapp.i18n.traktRemoveWatchlist
import com.ultratv.tv.nativeapp.i18n.traktSeasonUnwatched
import com.ultratv.tv.nativeapp.i18n.traktSeasonWatched
import com.ultratv.tv.nativeapp.i18n.traktSettings
import com.ultratv.tv.nativeapp.i18n.traktUnmatched
import com.ultratv.tv.nativeapp.i18n.trackingHint
import com.ultratv.tv.nativeapp.i18n.trackingNone
import com.ultratv.tv.nativeapp.i18n.trackingNotLinked
import com.ultratv.tv.nativeapp.i18n.trackingTitle
import com.ultratv.tv.nativeapp.i18n.trackingUnsupported
import com.ultratv.tv.nativeapp.i18n.traktWatched
import com.ultratv.tv.nativeapp.i18n.traktYourRating
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.GroupLabel
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Œuvre affichée par la fiche (titre du catalogue, déjà nettoyé). */
data class TraktTarget(val isShow: Boolean, val providerId: Long, val remoteId: String, val title: String, val year: Int?)

/** Ce que Trakt sait de l'œuvre affichée. [linked] faux : aucun geste Trakt n'est proposé. */
data class TraktDetailState(
    val linked: Boolean = false,
    val inWatchlist: Boolean = false,
    val watched: Boolean = false,
    val rating: Int? = null,
    val episodes: Set<String> = emptySet(),
)

/** État Trakt d'une œuvre (pur, testé) : lu dans la bibliothèque du compte, rien si non lié. */
fun traktDetailState(t: TraktTarget?, lib: TraktLibrary): TraktDetailState =
    if (t == null || !lib.linked) TraktDetailState()
    else TraktDetailState(
        linked = true,
        inWatchlist = lib.isInWatchlist(t.isShow, t.title, t.year),
        watched = !t.isShow && lib.isMovieWatched(t.title, t.year),
        rating = lib.ratingFor(t.isShow, t.title, t.year),
        episodes = if (t.isShow) lib.watchedEpisodes(t.title, t.year) else emptySet(),
    )

/** Gestes Trakt d'une fiche : watchlist, vu / non vu (film, épisodes, saison), note. Tout passe par le Worker. */
@HiltViewModel
class TraktActionsViewModel @Inject constructor(
    repo: TraktLibraryRepository,
    private val actions: TraktActions,
    private val tmdb: TmdbDao,
) : ViewModel() {
    private val target = MutableStateFlow<TraktTarget?>(null)
    val state: StateFlow<TraktDetailState> = combine(target, repo.library, ::traktDetailState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TraktDetailState())
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun bind(t: TraktTarget) { if (target.value != t) target.value = t }

    private suspend fun work(t: TraktTarget) = TraktWork(
        t.isShow, t.title, t.year,
        runCatching { tmdb.get(if (t.isShow) TmdbKind.TV.name else TmdbKind.MOVIE.name, t.providerId, t.remoteId)?.tmdbId }.getOrNull(),
    )

    private fun run(D: DesignStrings, op: suspend (TraktWork) -> TraktWriteResult) {
        val t = target.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (op(work(t))) {
                    TraktWriteResult.OK -> Unit
                    TraktWriteResult.UNMATCHED -> Toaster.err(D.traktUnmatched)
                    TraktWriteResult.NOT_LINKED -> Toaster.err(D.traktNotLinked)
                    TraktWriteResult.FAILED -> Toaster.err(D.traktFailed)
                    TraktWriteResult.UNSUPPORTED -> Toaster.err(D.trackingUnsupported)
                }
            } finally { _busy.value = false }
        }
    }

    fun toggleWatchlist(D: DesignStrings) { val add = !state.value.inWatchlist; run(D) { actions.setWatchlist(it, add) } }
    fun toggleMovieWatched(D: DesignStrings) { val add = !state.value.watched; run(D) { actions.setMovieWatched(it, add) } }
    fun rate(D: DesignStrings, rating: Int) = run(D) { actions.rate(it, rating) }
    fun setEpisodes(D: DesignStrings, eps: List<EpisodeRef>, watched: Boolean) = run(D) { actions.setEpisodesWatched(it, eps, watched) }
}

private const val BOOKMARK = "M6 3h12v18l-6-4-6 4z"
private const val STAR = "M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z"

/** Choix de la note : 10 → 1, et « Retirer ma note » si une note existe. */
@Composable
private fun RatingDialog(current: Int?, D: DesignStrings, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = (10 downTo 1).map { it to "★ $it" } + (if (current != null) listOf(0 to D.traktRemoveRating) else emptyList())
    ChoiceDialog(D.traktRateTitle, options, current, onPick = { onPick(it); onDismiss() }, onDismiss = onDismiss)
}

private fun ratingLabel(D: DesignStrings, r: Int?) = if (r != null) D.traktYourRating(r) else D.traktRate

/** Fiche film (TV) : rangée de gestes Trakt sous les boutons de lecture. Rien si le compte n'est pas lié. */
@Composable
fun TraktMovieActions(movie: MovieEntity, vm: TraktActionsViewModel = hiltViewModel()) {
    LaunchedEffect(movie.id, movie.title, movie.year) { vm.bind(TraktTarget(false, movie.providerId, movie.remoteId, movie.title, movie.year)) }
    val st by vm.state.collectAsState()
    if (!st.linked) return
    val D = LocalDs.current
    var rating by remember { mutableStateOf(false) }
    TraktMovieActionsRow(
        watchlistLabel = if (st.inWatchlist) D.traktRemoveWatchlist else D.traktAddWatchlist, inWatchlist = st.inWatchlist, onWatchlist = { vm.toggleWatchlist(D) },
        watchedLabel = if (st.watched) D.traktMarkUnwatched else D.traktMarkWatched, onWatched = { vm.toggleMovieWatched(D) },
        rateLabel = ratingLabel(D, st.rating), rated = st.rating != null, onRate = { rating = true },
    )
    if (rating) RatingDialog(st.rating, D, { vm.rate(D, it) }, { rating = false })
}

/**
 * Les trois gestes Trakt d'une fiche film TV, sans ViewModel (rendu testable). Rangée qui PASSE À LA LIGNE : dans la colonne
 * de gauche de la fiche (largeur bornée), trois pilules côte à côte écrasaient la dernière jusqu'à ne laisser que l'étoile de
 * « Noter ». Chaque libellé reste entier, quelles que soient la langue et la taille du texte ; la télécommande suit la
 * géométrie (◀ ▶ dans une ligne, ▲ ▼ entre les lignes).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TraktMovieActionsRow(
    watchlistLabel: String, inWatchlist: Boolean, onWatchlist: () -> Unit,
    watchedLabel: String, onWatched: () -> Unit,
    rateLabel: String, rated: Boolean, onRate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(20.design), verticalArrangement = Arrangement.spacedBy(16.design),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton(watchlistLabel, onWatchlist, heightPx = 64, fontPx = 22, iconPath = BOOKMARK, iconFill = inWatchlist)
        PillButton(watchedLabel, onWatched, heightPx = 64, fontPx = 22, iconPath = Icons.Check)
        PillButton(rateLabel, onRate, heightPx = 64, fontPx = 22, iconPath = STAR, iconFill = rated)
    }
}

/** Fiche film (tactile) : trois tuiles Trakt. Rien si le compte n'est pas lié. */
@Composable
fun TraktMovieActionsMobile(movie: MovieEntity, vm: TraktActionsViewModel = hiltViewModel()) {
    LaunchedEffect(movie.id, movie.title, movie.year) { vm.bind(TraktTarget(false, movie.providerId, movie.remoteId, movie.title, movie.year)) }
    val st by vm.state.collectAsState()
    if (!st.linked) return
    val D = LocalDs.current
    var rating by remember { mutableStateOf(false) }
    TraktMovieTilesRow(
        if (st.inWatchlist) D.traktInWatchlist else D.traktAddWatchlist, st.inWatchlist, { vm.toggleWatchlist(D) },
        if (st.watched) D.traktWatched else D.traktMarkWatched, st.watched, { vm.toggleMovieWatched(D) },
        ratingLabel(D, st.rating), st.rating != null, { rating = true },
    )
    if (rating) RatingDialog(st.rating, D, { vm.rate(D, it) }, { rating = false })
}

/**
 * Les trois tuiles Trakt de la fiche tactile, sans ViewModel. Même hauteur pour les trois (celle de la plus haute) : un
 * libellé long passe sur plusieurs lignes au lieu d'être coupé (« Ajouter à ma watchlist… »).
 */
@Composable
internal fun TraktMovieTilesRow(
    watchlistLabel: String, inWatchlist: Boolean, onWatchlist: () -> Unit,
    watchedLabel: String, watched: Boolean, onWatched: () -> Unit,
    rateLabel: String, rated: Boolean, onRate: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        com.ultratv.tv.nativeapp.ui.mobile.MobileActionTile(BOOKMARK, watchlistLabel, onWatchlist, tile, active = inWatchlist, fill = inWatchlist, wrap = true)
        com.ultratv.tv.nativeapp.ui.mobile.MobileActionTile(Icons.Check, watchedLabel, onWatched, Modifier.weight(1f).fillMaxHeight(), active = watched, wrap = true)
        com.ultratv.tv.nativeapp.ui.mobile.MobileActionTile(STAR, rateLabel, onRate, Modifier.weight(1f).fillMaxHeight(), active = rated, fill = rated, wrap = true)
    }
}

/**
 * Fiche série : bouton « Trakt » ouvrant le panneau des gestes (watchlist, note, saison vue / non vue, épisodes un à
 * un). [season] = saison affichée par la fiche. Rien si le compte n'est pas lié.
 */
@Composable
fun TraktSeriesButton(series: SeriesEntity, episodes: List<EpisodeEntity>, season: Int?, touch: Boolean = false, vm: TraktActionsViewModel = hiltViewModel()) {
    LaunchedEffect(series.id, series.title, series.year) { vm.bind(TraktTarget(true, series.providerId, series.remoteId, series.title, series.year)) }
    val st by vm.state.collectAsState()
    if (!st.linked) return
    val D = LocalDs.current
    var open by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(false) }
    if (touch) com.ultratv.tv.nativeapp.ui.mobile.MobileSecondaryButton(D.traktButton, { open = true })
    // Bouton rond, icône seule (comme le cœur des favoris) : à côté de « Reprendre S2 É12 », la pilule « Trakt » n'avait
    // plus la place et s'affichait « Tra… ». Voulu : il ouvre le panneau des gestes, où chaque libellé est écrit en entier.
    else TraktSeriesIconButton(rated = st.rating != null, description = D.traktButton, onClick = { open = true })
    if (rating) RatingDialog(st.rating, D, { vm.rate(D, it) }, { rating = false })
    else if (open) TraktSeriesDialog(st, episodes.filter { it.season == season }, season, D, vm, onRate = { rating = true }, onDismiss = { open = false })
}

/**
 * Bouton rond à étoile de la fiche série TV (sans ViewModel) : même taille que le cœur des favoris (64 px de maquette), nom
 * lu par le lecteur d'écran ([description]), étoile pleine quand l'œuvre est notée.
 */
@Composable
internal fun TraktSeriesIconButton(rated: Boolean, description: String, onClick: () -> Unit) {
    FocusSurface(
        onClick = onClick, shape = androidx.compose.foundation.shape.CircleShape, bg = Ux.Surface2,
        modifier = Modifier.size(64.design).semantics { contentDescription = description },
    ) { f ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DIcon(STAR, 30.design, if (f) Ux.TextOnLight else if (rated) Ux.Accent else Ux.Text, fill = rated)
        }
    }
}

@Composable
private fun TraktSeriesDialog(
    st: TraktDetailState, eps: List<EpisodeEntity>, season: Int?, D: DesignStrings, vm: TraktActionsViewModel,
    onRate: () -> Unit, onDismiss: () -> Unit,
) {
    val refs = remember(eps) { eps.map { EpisodeRef(it.season, it.episode) }.distinct() }
    val seasonSeen = refs.isNotEmpty() && refs.all { it.key in st.episodes }
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(Modifier.widthIn(min = 560.design, max = 980.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design), verticalArrangement = Arrangement.spacedBy(16.design)) {
            Text("Trakt", color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1)
            ActionRow(if (st.inWatchlist) D.traktRemoveWatchlist else D.traktAddWatchlist, BOOKMARK, st.inWatchlist) { vm.toggleWatchlist(D) }
            ActionRow(ratingLabel(D, st.rating), STAR, st.rating != null, onClick = onRate)
            if (season != null && refs.isNotEmpty()) {
                ActionRow(if (seasonSeen) D.traktSeasonUnwatched(season) else D.traktSeasonWatched(season), Icons.Check, seasonSeen) { vm.setEpisodes(D, refs, !seasonSeen) }
                Text(D.traktEpisodesHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.design))
                LazyColumn(Modifier.heightIn(max = 520.design), verticalArrangement = Arrangement.spacedBy(10.design)) {
                    val sortedEps = eps.sortedBy { it.episode }
                    val epKeys = com.ultratv.tv.nativeapp.ui.common.uniqueKeys(sortedEps) { "${it.season}:${it.episode}:${it.remoteId}" }
                    itemsIndexed(sortedEps, key = { i, _ -> epKeys[i] }) { _, ep ->
                        val ref = EpisodeRef(ep.season, ep.episode)
                        val seen = ref.key in st.episodes
                        val label = com.ultratv.tv.nativeapp.i18n.LatinDigits.episodeTag(ep.season, ep.episode) + ep.title.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                        ActionRow(label, Icons.Check, seen, compact = true) { vm.setEpisodes(D, listOf(ref), !seen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, icon: String, on: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.design), bg = if (on) Ux.Surface2 else Ux.Surface, ringWidth = 5.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(if (compact) 64.design else 76.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 28.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            DIcon(icon, 26.design, if (f) Ux.TextOnLight else if (on) Ux.Accent else Ux.Text3, strokeWidth = 2.6f, fill = on && icon != Icons.Check)
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

// ───────────────────────── Réglages ─────────────────────────

/** Listes personnelles et réglages Trakt pour la rubrique « Lecture » des réglages. */
@HiltViewModel
class TraktSettingsViewModel @Inject constructor(repo: TraktLibraryRepository, private val prefs: TraktPrefsStore) : ViewModel() {
    data class ListUi(val id: String, val name: String)
    data class Ui(val linked: Boolean = false, val lists: List<ListUi> = emptyList(), val prefs: TraktPrefs = TraktPrefs())

    val state: StateFlow<Ui> = combine(repo.library.map { lib -> lib.linked to lib.lists.map { ListUi(it.id, it.name) } }, prefs.state) { (linked, lists), p -> Ui(linked, lists, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui())

    fun setAutoWatched(on: Boolean) = prefs.setAutoWatched(on)
    fun setListVisible(id: String, visible: Boolean) = prefs.setListVisible(id, visible)
}

/** Section Trakt des réglages : visible seulement si le compte est lié. */
@Composable
fun TraktSettingsSection(vm: TraktSettingsViewModel = hiltViewModel()) {
    val st by vm.state.collectAsState()
    if (!st.linked) return
    val D = LocalDs.current
    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        GroupLabel(D.traktSettings)
        // Le texte décrit l'état ACTUEL : activé, il affichait « Désactivé : la fin est enregistrée comme une pause ».
        com.ultratv.tv.nativeapp.ui.design.SettingsCard {
            SwitchPrefRow(D.traktAutoWatched, st.prefs.autoWatched, hint = if (st.prefs.autoWatched) D.traktAutoWatchedHintOn else D.traktAutoWatchedHint) { vm.setAutoWatched(it) }
        }
        if (st.lists.isNotEmpty()) {
            Text(D.traktListsHome + " · " + D.traktListsHomeHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.design))
            com.ultratv.tv.nativeapp.ui.design.SettingsCard {
                for (l in st.lists) SwitchPrefRow(l.name, l.id !in st.prefs.hiddenLists) { vm.setListVisible(l.id, it) }
            }
        }
    }
}

// ───────────────────────── Service de suivi (Trakt / Simkl / aucun) ─────────────────────────

/** Choix du service de suivi du compte : lu et changé auprès du Worker (la liaison se fait sur la page du compte). */
@HiltViewModel
class TrackingServiceViewModel @Inject constructor(
    private val client: com.ultratv.tv.nativeapp.data.config.CloudSyncClient,
    private val tokens: com.ultratv.tv.nativeapp.data.config.DeviceTokenStore,
    private val prefs: com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore,
    private val repo: TraktLibraryRepository,
) : ViewModel() {
    private val _status = MutableStateFlow<com.ultratv.tv.nativeapp.data.trakt.TrackingStatus?>(null)
    val status: StateFlow<com.ultratv.tv.nativeapp.data.trakt.TrackingStatus?> = _status.asStateFlow()

    init { load() }

    private suspend fun base(): String? =
        com.ultratv.tv.nativeapp.data.config.WorkerUrl.normalize(
            com.ultratv.tv.nativeapp.data.config.WorkerUrl.resolve(prefs.flow.first().workerBaseUrl), com.ultratv.tv.nativeapp.BuildConfig.DEBUG,
        )

    fun load() {
        viewModelScope.launch {
            val t = tokens.token() ?: return@launch
            val b = base() ?: return@launch
            _status.value = client.trackingStatus(b, t)
        }
    }

    fun choose(service: String, D: DesignStrings) {
        viewModelScope.launch {
            val t = tokens.token() ?: return@launch
            val b = base() ?: return@launch
            val st = client.setTracking(b, t, service)
            if (st == null) { Toaster.err(D.traktFailed); return@launch }
            _status.value = st
            // La bibliothèque affichée est celle du nouveau service : relue tout de suite (vide si « aucun »).
            val p = prefs.flow.first()
            repo.refreshIfStale(TraktLibraryRepository.langParam(p.language, java.util.Locale.getDefault().language), force = true)
            if (service != com.ultratv.tv.nativeapp.data.trakt.TrackingService.NONE && !st.isLinked(service)) {
                Toaster.err(D.trackingNotLinked(com.ultratv.tv.nativeapp.data.trakt.TrackingService.displayName(service)))
            }
        }
    }
}

/** Réglage « Service de suivi » : visible dès qu'un service est proposé par le Worker (appareil appairé). */
@Composable
fun TrackingServiceSection(vm: TrackingServiceViewModel = hiltViewModel()) {
    val st by vm.status.collectAsState()
    val s = st ?: return
    if (s.available.isEmpty()) return
    val D = LocalDs.current
    var pick by remember { mutableStateOf(false) }
    val options = s.available.map { it to com.ultratv.tv.nativeapp.data.trakt.TrackingService.displayName(it) } +
        (com.ultratv.tv.nativeapp.data.trakt.TrackingService.NONE to D.trackingNone)
    val current = options.firstOrNull { it.first == s.choice }?.second ?: D.trackingNone
    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        GroupLabel(D.trackingTitle)
        com.ultratv.tv.nativeapp.ui.design.SettingsCard {
            com.ultratv.tv.nativeapp.ui.design.PrefRow(D.trackingTitle, current, hint = D.trackingHint) { pick = true }
        }
    }
    if (pick) ChoiceDialog(D.trackingTitle, options, s.choice, { vm.choose(it, D); pick = false }, { pick = false })
}
