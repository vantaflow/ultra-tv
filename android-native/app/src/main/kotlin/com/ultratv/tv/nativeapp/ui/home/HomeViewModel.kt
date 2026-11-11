package com.ultratv.tv.nativeapp.ui.home

import kotlinx.coroutines.flow.take
import com.ultratv.tv.nativeapp.data.repo.atMostEvery
import com.ultratv.tv.nativeapp.data.repo.snapshotWhile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.repo.CatalogRepository
import com.ultratv.tv.nativeapp.data.repo.HistoryRepository
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Élément « À la une » : uniquement des métadonnées RÉELLES de la source (jamais inventées). */
@androidx.compose.runtime.Immutable
data class HeroItem(
    val kind: Kind,
    val id: Long,
    val title: String,
    val year: Int?,
    val genre: String?,
    val rating: Double?,
    /** Image PAYSAGE si la source en fournit une (backdrop), sinon null. */
    val backdrop: String?,
    val poster: String?,
    /** Synopsis de la source (peut être vide : repli TMDB via [HeroExtras]). */
    val plot: String? = null,
    /** Durée en minutes (films seulement, si la source la donne). */
    val runtimeMin: Int? = null,
    val providerId: Long = 0,
    val remoteId: String = "",
    /** Adresse du film pour « Regarder » (lecture directe) ; null pour une série (ouvre la fiche). */
    val streamUrl: String? = null,
    /** « 2024 · Drame · ★ 7.8 » formaté UNE fois à la construction (dans le ViewModel), pas à chaque composition du hero. */
    val meta: String = heroMeta(year, genre, rating),
) {
    enum class Kind { SERIES, MOVIE }
    /** Clé stable (type + identifiant) : index des notes et des compléments. */
    val key: String get() = "${if (kind == Kind.SERIES) "s" else "m"}-$id"
}

/** Compléments d'un titre « à la une » résolus en arrière-plan (TMDB / OMDb, appareil appairé) : notes externes et synopsis de repli. */
@androidx.compose.runtime.Immutable
data class HeroExtras(val ratings: com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings? = null, val overview: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val syncCoordinator: SyncCoordinator,
    provider: ProviderRepository,
    private val catalog: CatalogRepository,
    private val history: HistoryRepository,
    private val playback: PlaybackContext,
    private val epgDao: EpgDao,
    bus: SyncStatusBus,
    private val traktLibrary: com.ultratv.tv.nativeapp.data.trakt.TraktLibraryRepository,
    traktMatcher: com.ultratv.tv.nativeapp.data.trakt.TraktCatalogMatcher,
    private val prefsStore: com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore,
    private val tmdb: com.ultratv.tv.nativeapp.data.tmdb.TmdbRepository,
    private val ratingsClient: com.ultratv.tv.nativeapp.data.tmdb.RatingsClient,
    private val liveZap: com.ultratv.tv.nativeapp.data.repo.LiveZapContext,
) : ViewModel() {

    val providers: StateFlow<List<ProviderEntity>> = provider.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** `null` tant que Room n'a pas répondu (évite de montrer « aucune source » une fraction de seconde). */
    // Dérivé de [providers] (même observateur Room) plutôt qu'une seconde souscription à la table.
    val providersLoaded: StateFlow<Boolean> = provider.observeProviders().take(1).map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val syncStatus = bus.status
    /** Seul le pourcentage est affiché : l'accueil n'est plus recomposé à chaque message de progression. */
    val syncPercent: StateFlow<Int?> = bus.status.map { it?.percent }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Synchro en cours : les requêtes de l'accueil ne sont lues qu'une fois (instantané) au lieu d'être relancées à chaque lot inséré. */
    private val syncing: Flow<Boolean> = bus.status.map { it != null }.distinctUntilChanged()

    private val pid: Flow<Long?> = providers.map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    val continueWatching: StateFlow<List<WatchHistoryEntity>> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else history.continueWatching(id, 12) }
        // Reçu d'un autre appareil sans adresse locale (épisode d'une série jamais ouverte ici) : pas de carte cliquable.
        // Épisode sans image (anciennes lignes) : affiche de la série.
        .map { l -> l.filter { it.streamUrl.isNotBlank() }.map { h ->
            if (h.poster.isNullOrBlank() && h.kind == "EPISODE" && h.parentRemoteId != null)
                catalog.seriesByRemote(h.providerId, h.parentRemoteId)?.let { s -> h.copy(poster = s.poster ?: s.backdrop) } ?: h
            else h
        } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Rangées Trakt (watchlist, recommandations) : UNIQUEMENT les éléments présents dans la source active, dans l'ordre
     * de Trakt. Vides si le compte n'est pas lié à Trakt, si l'appareil n'est pas appairé ou si rien n'est disponible.
     */
    val trakt: StateFlow<com.ultratv.tv.nativeapp.data.trakt.TraktRows> = traktMatcher.rows(pid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.ultratv.tv.nativeapp.data.trakt.TraktRows.EMPTY)

    init {
        // Changement de source active → relecture si la bibliothèque a plus de 15 min (sinon sans effet).
        viewModelScope.launch { pid.collect { refreshTrakt() } }
    }

    /** Appelé aussi au retour au premier plan : la bibliothèque n'est relue qu'au bout de 15 min. */
    fun refreshTrakt() {
        viewModelScope.launch {
            val lang = com.ultratv.tv.nativeapp.data.trakt.TraktLibraryRepository.langParam(
                prefsStore.flow.first().language, java.util.Locale.getDefault().language,
            )
            traktLibrary.refreshIfStale(lang)
        }
    }

    /** Derniers films / séries ajoutés par le fournisseur. */
    val latestMovies: StateFlow<List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else catalog.latestMovies(id, 15).atMostEvery(1_000).snapshotWhile(syncing) }
        .map { l -> l.map { com.ultratv.tv.nativeapp.ui.catalog.PosterItem(it.id, it.title, it.poster, it.year, it.rating) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val latestSeries: StateFlow<List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else catalog.latestSeries(id, 15).atMostEvery(1_000).snapshotWhile(syncing) }
        .map { l -> l.map { com.ultratv.tv.nativeapp.ui.catalog.PosterItem(it.id, it.title, it.poster, it.year, it.rating) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** « Films / séries les mieux notés » : note du fournisseur (ou de TMDB une fois la fiche ouverte). */
    val topMovies: StateFlow<List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else catalog.topRatedMovies(id, 15).atMostEvery(1_000).snapshotWhile(syncing) }
        .map { l -> l.map { com.ultratv.tv.nativeapp.ui.catalog.PosterItem(it.id, it.title, it.poster, it.year, it.rating) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val topSeries: StateFlow<List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else catalog.topRatedSeries(id, 15).atMostEvery(1_000).snapshotWhile(syncing) }
        .map { l -> l.map { com.ultratv.tv.nativeapp.ui.catalog.PosterItem(it.id, it.title, it.poster, it.year, it.rating) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** « Parce que vous avez regardé X » : films proches du dernier film regardé (calcul local, sans Trakt). */
    val because: StateFlow<Pair<String, List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>>> = pid
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else history.recentByKind(id, "MOVIE", 1).map { it.firstOrNull()?.remoteId }.distinctUntilChanged()
                .map { rid -> if (rid == null) null else runCatching { catalog.similarMovies(id, rid, 15) }.getOrNull() }
        }
        .map { r -> if (r == null) "" to emptyList() else r.first to r.second.map { com.ultratv.tv.nativeapp.ui.catalog.PosterItem(it.id, it.title, it.poster, it.year, it.rating) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "" to emptyList())

    /** Dernières chaînes regardées (historique du profil). */
    val recentChannels: StateFlow<List<WatchHistoryEntity>> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else history.recentByKind(id, "LIVE", 12) }
        .map { l -> l.filter { it.streamUrl.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * « À la une » (rotation) : séries les mieux notées avec image paysage, alternées avec les films les mieux notés.
     * Le premier élément est celui d'avant (série la mieux notée, à défaut le film le mieux noté).
     */
    val heroes: StateFlow<List<HeroItem>> = pid.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        // Pendant la synchro, les tables changent sans arrêt : au plus une relecture par seconde.
        else combine(catalog.heroSeriesList(id, HERO_POOL).atMostEvery(1_000).snapshotWhile(syncing), catalog.heroMovieList(id, HERO_CANDIDATES).atMostEvery(1_000).snapshotWhile(syncing)) { ss, ms -> ss to ms }
            .flatMapLatest { (ss, ms) -> heroFlow(ss, ms) }
    }.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default) // méta formatée hors fil principal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Sélection « À la une » : uniquement des titres avec image paysage (celle de la source, sinon le backdrop TMDB déjà
     * connu). Si besoin, le backdrop TMDB d'un petit lot de films ([HERO_RESOLVE_MAX]) est résolu en arrière-plan, un par un.
     * Sans aucun titre paysage, l'affiche seule reste affichée en dernier recours.
     */
    private fun heroFlow(ss: List<com.ultratv.tv.nativeapp.data.db.SeriesEntity>, ms: List<com.ultratv.tv.nativeapp.data.db.MovieEntity>): Flow<List<HeroItem>> = flow {
        val tmdbBackdrops = HashMap<Long, String>()
        val seriesItems = ss.map { s -> HeroItem(HeroItem.Kind.SERIES, s.id, s.title, s.year, s.genre?.substringBefore(','), s.rating, s.backdrop?.takeIf { it.isNotBlank() }, s.poster, plot = s.plot?.trim(), providerId = s.providerId, remoteId = s.remoteId) }
        fun movieItems() = ms.map { m -> HeroItem(HeroItem.Kind.MOVIE, m.id, m.title, m.year, m.genre?.substringBefore(','), m.rating, m.backdrop?.takeIf { it.isNotBlank() } ?: tmdbBackdrops[m.id], m.poster, plot = m.plot?.trim(), runtimeMin = durationMinutes(m.duration), providerId = m.providerId, remoteId = m.remoteId, streamUrl = m.streamUrl) }
        fun pick(fallback: Boolean) = pickHeroes(seriesItems, movieItems(), HERO_POOL, fallback) { !it.backdrop.isNullOrBlank() }
        for (m in ms) if (m.backdrop.isNullOrBlank()) runCatching { catalog.cachedTmdbBackdrop(m) }.getOrNull()?.let { tmdbBackdrops[m.id] = it }
        var current = pick(false)
        if (current.isNotEmpty()) emit(current)
        val toResolve = heroesToResolve(movieItems(), current.size, HERO_POOL, HERO_RESOLVE_MAX) { !it.backdrop.isNullOrBlank() }
        if (toResolve.isNotEmpty() && current.isNotEmpty()) delay(1_500) // ne concurrence pas le premier affichage
        for (h in toResolve) {
            if (pick(false).size >= HERO_POOL) break
            val m = ms.firstOrNull { it.id == h.id } ?: continue
            val b = runCatching { catalog.resolveTmdbBackdrop(m) }.getOrNull() ?: continue
            tmdbBackdrops[m.id] = b
            val next = pick(false)
            if (next != current) { current = next; emit(next) }
        }
        if (current.isEmpty()) emit(pick(true))
    }

    /**
     * Notes IMDb / Rotten Tomatoes (OMDb via le Worker) et synopsis TMDB de repli des titres « à la une », résolus UN PAR UN
     * en arrière-plan (IO), après 1,5 s pour ne pas concurrencer le premier affichage. Caches Room / mémoire déjà en place ;
     * appareil non appairé ou réseau absent : rien n'est émis et le hero garde la note habituelle.
     */
    val heroExtras: StateFlow<Map<String, HeroExtras>> = heroes.flatMapLatest { list ->
        flow {
            if (list.isEmpty()) return@flow
            delay(1_500)
            val acc = HashMap<String, HeroExtras>()
            for (h in list) {
                val e = runCatching { resolveExtras(h) }.getOrNull() ?: continue
                acc[h.key] = e
                emit(acc.toMap())
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private suspend fun resolveExtras(h: HeroItem): HeroExtras? {
        val isShow = h.kind == HeroItem.Kind.SERIES
        val info = if (isShow) catalog.seriesById(h.id)?.let { tmdb.forSeries(it) } else catalog.movieById(h.id)?.let { tmdb.forMovie(it) }
        val id = info?.tmdbId ?: return null
        val r = ratingsClient.forTmdb(if (isShow) com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.TV else com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE, id)
        return HeroExtras(r, info.overview?.trim()?.takeIf { it.isNotEmpty() }).takeIf { it.ratings != null || it.overview != null }
    }

    /** « Regarder » sur un film « à la une » : contexte de lecture comme la fiche film, puis l'appelant lance le lecteur. */
    fun playHero(h: HeroItem, title: String = h.title) {
        val url = h.streamUrl ?: return
        playback.set(PlaybackContext.Item(providerId = h.providerId, kind = "MOVIE", remoteId = h.remoteId, title = title, poster = h.poster, streamUrl = url))
    }

    /** Compteurs des raccourcis, espacés de 2 s et figés pendant une synchro (comme les autres rangées) ; calculés par Room hors fil principal. */
    val counts: StateFlow<HomeCounts> = pid.flatMapLatest { id ->
        if (id == null) flowOf(HomeCounts())
        else combine(catalog.channelCount(id), catalog.movieCount(id), catalog.seriesCount(id), catalog.allFavoritesCount(id)) { c, m, s, f -> HomeCounts(c, m, s, f) }
            .atMostEvery(2_000).snapshotWhile(syncing)
    }.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeCounts())

    val hero: StateFlow<HeroItem?> = heroes.map { it.firstOrNull() }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Favorites en premier ; sans favori, des chaînes avec logo. [favorite] dit lequel des deux. */
    val favoriteChannels: StateFlow<List<ChannelEntity>> = pid
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else catalog.favoriteChannels(id, 12).atMostEvery(1_000).snapshotWhile(syncing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Chaînes de repli observées SEULEMENT quand il n'y a aucun favori (avant : toujours abonnées).
    val channels: StateFlow<List<ChannelEntity>> = combine(pid, favoriteChannels) { id, fav -> id to fav }
        .flatMapLatest { (id, fav) ->
            if (fav.isNotEmpty() || id == null) flowOf(fav) else catalog.channelsWithLogo(id, 12).atMostEvery(1_000).snapshotWhile(syncing)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showingFavorites: StateFlow<Boolean> = favoriteChannels.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Programme en cours par chaîne affichée, rafraîchi toutes les minutes. */
    val nowPlaying: StateFlow<Map<Long, EpgEntity>> = channels.flatMapLatest { chans ->
        flow {
            while (true) {
                val now = System.currentTimeMillis()
                val rows = if (chans.isEmpty()) emptyList() else epgDao.rangeForChannels(chans.map { it.id }, now, now + 60_000)
                emit(rows.filter { it.startMs <= now && it.endMs > now }.associateBy { it.channelId })
                delay(60_000)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Toutes les rangées de l'accueil en UNE valeur immuable, construite hors du fil principal (titres nettoyés,
     * affiches converties, programme en cours résolu, ordre des rangées calculé). StateFlow : une valeur égale à la
     * précédente n'est pas réémise, donc l'écran ne recompose rien quand Room relit des données identiques.
     */
    /** Disposition de l'accueil (ordre / rangées masquées) : seule une modification du réglage réémet. */
    private val homeLayout = prefsStore.flow.map { HomeLayout.parse(it.homeOrder, it.homeHidden) }.distinctUntilChanged()

    val rows: StateFlow<HomeRows> = combine(
        listOf(continueWatching, trakt, recentChannels, latestMovies, latestSeries, channels, showingFavorites, nowPlaying, homeLayout, topMovies, topSeries, because, counts),
    ) { a ->
        @Suppress("UNCHECKED_CAST") val resume = a[0] as List<WatchHistoryEntity>
        val t = a[1] as com.ultratv.tv.nativeapp.data.trakt.TraktRows
        @Suppress("UNCHECKED_CAST") val recent = a[2] as List<WatchHistoryEntity>
        @Suppress("UNCHECKED_CAST") val movies = a[3] as List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>
        @Suppress("UNCHECKED_CAST") val series = a[4] as List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>
        @Suppress("UNCHECKED_CAST") val chans = a[5] as List<ChannelEntity>
        val fav = a[6] as Boolean
        @Suppress("UNCHECKED_CAST") val now = a[7] as Map<Long, EpgEntity>
        val homeCounts = a[12] as HomeCounts
        val clean: (String) -> String = { com.ultratv.tv.nativeapp.data.repo.TitleCleaner.historyTitle(it) }
        HomeRows(
            resume = resume.map { resumeCardOf(it, "", clean) }.distinctKeys { it.key }.oneCardPerTitle(),
            watchlist = t.watchlist.map(::traktPosterOf).distinctKeys { it.key },
            recommended = t.recommendations.map(::traktPosterOf).distinctKeys { it.key },
            trending = t.trending.map(::traktPosterOf).distinctKeys { it.key },
            popular = t.popular.map(::traktPosterOf).distinctKeys { it.key },
            upNext = t.upNext.map(::traktUpNextPosterOf).distinctKeys { it.key },
            traktLists = t.lists.map { l -> TraktListUi(l.id, l.name, l.cards.map(::traktPosterOf).distinctKeys { it.key }) },
            recentChannels = recent.map { resumeCardOf(it, "rc-", clean) }.distinctKeys { it.key },
            latestMovies = movies.map { catalogPosterOf(it, isShow = false) }.distinctKeys { it.key },
            latestSeries = series.map { catalogPosterOf(it, isShow = true) }.distinctKeys { it.key },
            channels = chans.distinctBy { it.id }.map { c -> val p = now[c.id]; ChannelCardUi(c, p?.title, p?.let { com.ultratv.tv.nativeapp.ui.common.EpgClock.range(it.startMs, it.endMs) }) },
            shortcuts = shortcutsOf(homeCounts, chans.mapNotNull { it.logo }, movies.firstOrNull()?.poster, series.firstOrNull()?.poster),
            top10 = top10Of(
                t.trending.map(::traktPosterOf).distinctKeys { it.key },
                (a[9] as List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>).map { catalogPosterOf(it, isShow = false) },
            ),
            showingFavorites = fav,
            layout = a[8] as HomeLayout,
            topMovies = (a[9] as List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>).map { catalogPosterOf(it, isShow = false) }.distinctKeys { it.key },
            topSeries = (a[10] as List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>).map { catalogPosterOf(it, isShow = true) }.distinctKeys { it.key },
            becauseTitle = (a[11] as Pair<String, List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>>).first,
            because = (a[11] as Pair<String, List<com.ultratv.tv.nativeapp.ui.catalog.PosterItem>>).second.map { catalogPosterOf(it, isShow = false) }.distinctKeys { it.key },
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeRows.EMPTY)

    /**
     * Chaîne de la rangée « Direct » : contexte « LIVE » + file de zapping AVANT d'ouvrir le lecteur. Sans lui, le lecteur
     * gardait le contexte précédent (commandes d'un film, OK = pause au lieu de la liste des chaînes).
     */
    fun playChannel(c: com.ultratv.tv.nativeapp.data.db.ChannelEntity, then: () -> Unit) {
        playback.set(PlaybackContext.Item(c.providerId, "LIVE", c.remoteId, c.title, c.logo, c.streamUrl))
        viewModelScope.launch {
            runCatching { liveZap.prepare(c.providerId, c.remoteId, c.title, c.logo, c.streamUrl) }
            then()
        }
    }

    /** Sets the playback context from a history entry so the player can record proper context. */
    fun playFromHistory(h: WatchHistoryEntity) {
        playback.set(PlaybackContext.Item(
            providerId = h.providerId, kind = h.kind, remoteId = h.remoteId, title = h.title,
            poster = h.poster, streamUrl = h.streamUrl, parentRemoteId = h.parentRemoteId,
        ))
    }

    fun dismiss(h: WatchHistoryEntity) { viewModelScope.launch { history.remove(h.providerId, h.kind, h.remoteId) } }

    fun refresh() {
        val id = providers.value.firstOrNull { it.active }?.id ?: providers.value.firstOrNull()?.id ?: return
        syncCoordinator.request(id, force = true)
    }
}
