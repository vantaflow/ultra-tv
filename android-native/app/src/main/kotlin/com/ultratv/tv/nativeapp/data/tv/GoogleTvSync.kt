package com.ultratv.tv.nativeapp.data.tv

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.FavoriteDao
import com.ultratv.tv.nativeapp.data.db.WatchHistoryDao
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.ultratv.tv.nativeapp.i18n.tvChannelPublishedName

/**
 * Intégration Google TV / Android TV : « Continuer à regarder » (Watch Next) et chaîne de prévisualisation
 * « Favoris », « Chaînes récentes » et « Nouveautés » de l'écran d'accueil. Elle observe la base (historique, favoris) : la lecture n'a rien à appeler,
 * la fin d'une lecture met l'historique à jour, donc Watch Next aussi. Sans effet hors appareil Android TV, ni sur un
 * appareil leanback dont le fournisseur TV ne répond pas (voir [TvProviderGate]).
 */
@Singleton
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GoogleTvSync @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val providers: ProviderRepository,
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val channels: ChannelDao,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
    private val movies: com.ultratv.tv.nativeapp.data.db.MovieDao,
    private val seriesDao: com.ultratv.tv.nativeapp.data.db.SeriesDao,
    private val prefsStore: com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore,
    private val providerDao: com.ultratv.tv.nativeapp.data.db.ProviderDao,
    private val categoryDao: com.ultratv.tv.nativeapp.data.db.CategoryDao,
    private val lockedStore: com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore,
    private val sportRepo: dagger.Lazy<com.ultratv.tv.nativeapp.data.sport.SportRepository>,
    private val traktMatcher: dagger.Lazy<com.ultratv.tv.nativeapp.data.trakt.TraktCatalogMatcher>,
) {
    private val isTv = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val prefs = ctx.getSharedPreferences("google_tv", Context.MODE_PRIVATE)
    private var started = false
    @Volatile private var lastWatchNextSync = 0L
    /** Fournisseur TV du système : sondé une fois par processus, indisponible au premier « Unknown URL ». */
    private val providerGate = TvProviderGate()
    /** Collecteurs lancés par [start] : annulés si le fournisseur se révèle indisponible (plus de travail de base pour rien). */
    private val jobs = ArrayList<kotlinx.coroutines.Job>()

    private val activeProvider = providers.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    /** (profil courant, source active) : Watch Next et la chaîne Favoris suivent le profil, pas seulement la source. */
    private val scope_ = combine(profiles.currentId, activeProvider) { prof, pid -> prof to pid }.distinctUntilChanged()

    private val gate = TelemetryGate()

    /** Chaînes activées (Réglages › Affichage) et rubrique Sport : (choix, sport activé). */
    private val choice = prefsStore.flow.map { TvChannelsChoice.parse(it.tvHomeChannels) to it.sportSection }.distinctUntilChanged()

    /** La chaîne [kind] est-elle à publier ? Faux : elle est supprimée de l'écran d'accueil. */
    private fun enabled(kind: TvHomeChannel) = choice.map { (c, sport) -> c.isEffective(kind, sport) }.distinctUntilChanged()

    /**
     * Ce que les rangées de l'accueil montrent au profil actif ([com.ultratv.tv.nativeapp.ui.home.HomeGuard.of]) : suit les
     * masquages du profil, les verrous et désactivations de catégories et les chaînes verrouillées (republication à chaque
     * changement de verrou).
     */
    private fun rawRowGuard(pid: Long): kotlinx.coroutines.flow.Flow<com.ultratv.tv.nativeapp.ui.home.HomeGuard> = combine(
        profiles.hiddenCategoryKeys,
        categoryDao.observeForProviderKind(pid, "LIVE"),
        categoryDao.observeForProviderKind(pid, "MOVIE"),
        categoryDao.observeForProviderKind(pid, "SERIES"),
        lockedStore.locked,
    ) { hidden, live, ms, ss, locked -> com.ultratv.tv.nativeapp.ui.home.HomeGuard.of(hidden, live + ms + ss, locked) }.distinctUntilChanged()

    /**
     * Choix de l'utilisateur (Favoris, Ma liste, Chaînes récentes, Continuer à regarder) : [com.ultratv.tv.nativeapp.ui.home.HomeGuard.forHistory]
     * sur toutes les catégories — verrou parental, et adulte pour un profil Enfants ; JAMAIS le masquage ni la désactivation.
     */
    private fun rawHistoryGuard(pid: Long): kotlinx.coroutines.flow.Flow<com.ultratv.tv.nativeapp.ui.home.HomeGuard> = combine(
        categoryDao.observeForProviderKind(pid, "LIVE"),
        categoryDao.observeForProviderKind(pid, "MOVIE"),
        categoryDao.observeForProviderKind(pid, "SERIES"),
        lockedStore.locked,
        profiles.current.map { it?.isKids == true }.distinctUntilChanged(),
    ) { live, ms, ss, locked, kids -> com.ultratv.tv.nativeapp.ui.home.HomeGuard.forHistory(live + ms + ss, locked, kids) }.distinctUntilChanged()

    /**
     * Filtres partagés par tous les collecteurs une fois [start] lancé (une seule lecture des catégories par changement,
     * pas une par chaîne) ; avant [start] (demande ponctuelle du système), lus directement.
     */
    @Volatile private var sharedRowGuard: kotlinx.coroutines.flow.Flow<Pair<Long?, com.ultratv.tv.nativeapp.ui.home.HomeGuard>>? = null
    @Volatile private var sharedHistoryGuard: kotlinx.coroutines.flow.Flow<Pair<Long?, com.ultratv.tv.nativeapp.ui.home.HomeGuard>>? = null

    private fun shareGuards(scope: CoroutineScope) {
        fun share(raw: (Long) -> kotlinx.coroutines.flow.Flow<com.ultratv.tv.nativeapp.ui.home.HomeGuard>) =
            activeProvider.flatMapLatest { pid -> if (pid == null) flowOf(null to com.ultratv.tv.nativeapp.ui.home.HomeGuard.NONE) else raw(pid).map { pid to it } }
                .shareIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), replay = 1)
        sharedRowGuard = share(::rawRowGuard)
        sharedHistoryGuard = share(::rawHistoryGuard)
    }

    private fun rowGuard(pid: Long): kotlinx.coroutines.flow.Flow<com.ultratv.tv.nativeapp.ui.home.HomeGuard> =
        sharedRowGuard?.let { f -> f.filter { it.first == pid }.map { it.second }.distinctUntilChanged() } ?: rawRowGuard(pid)

    private fun historyGuard(pid: Long): kotlinx.coroutines.flow.Flow<com.ultratv.tv.nativeapp.ui.home.HomeGuard> =
        sharedHistoryGuard?.let { f -> f.filter { it.first == pid }.map { it.second }.distinctUntilChanged() } ?: rawHistoryGuard(pid)

    /** Invites « Afficher sur l'accueil » (chaîne activée dans Réglages) : présentées par MainActivity. */
    private val _browsablePrompts = kotlinx.coroutines.flow.MutableSharedFlow<Intent>(extraBufferCapacity = 4)
    val browsablePrompts: kotlinx.coroutines.flow.SharedFlow<Intent> = _browsablePrompts

    /** Télémétrie « googletv » : émise seulement quand le message change pour sa clé (échecs et changements d'état). */
    private fun report(key: String, message: String, warn: Boolean = false) {
        if (!gate.shouldEmit(key, message)) return
        if (warn) com.ultratv.tv.nativeapp.RemoteLog.warn("googletv", message) else com.ultratv.tv.nativeapp.RemoteLog.info("googletv", message)
    }

    /**
     * Échec d'une opération. Erreur de résolveur (« Unknown URL » : plus aucun fournisseur pour l'autorité) : la synchro
     * est rétrogradée en « fournisseur indisponible » pour le reste du processus — un seul événement info (par jour), fin
     * des collecteurs, plus d'avertissement à chaque lancement. Toute autre erreur : avertissement, comme avant.
     */
    private fun fail(key: String, what: String, e: Throwable) {
        if (providerGate.failed(what, e)) {
            android.util.Log.w("UltraGoogleTv", "tv provider unavailable: ${providerGate.reason}")
            providerAvailable()
            synchronized(jobs) { jobs.forEach { it.cancel() }; jobs.clear() }
            return
        }
        android.util.Log.w("UltraGoogleTv", what, e)
        report("fail:$key", "$what failed: ${e.javaClass.simpleName} ${e.message?.take(160)}", warn = true)
    }

    /**
     * Le fournisseur TV répond-il ? Sondé une fois par processus (voir [probeProvider]). Indisponible : un seul événement
     * info par jour (préférence [K_PROVIDER_NA_AT]), envoyé directement à RemoteLog — hors [gate], qui bloquerait le
     * même message le lendemain dans un processus qui vit longtemps. Jamais d'avertissement : rien à corriger côté appli.
     */
    private fun providerAvailable(): Boolean {
        val now = System.currentTimeMillis()
        var decision = providerGate.decide(prefs.getLong(K_PROVIDER_NA_AT, 0L), now)
        if (decision == TvProviderGate.Decision.PROBE) {
            providerGate.probed(probeProvider(), "authority ${TvContractCompat.AUTHORITY} not resolved")
            decision = providerGate.decide(prefs.getLong(K_PROVIDER_NA_AT, 0L), now)
        }
        return when (decision) {
            TvProviderGate.Decision.PUBLISH -> true
            TvProviderGate.Decision.SKIP_REPORT -> {
                prefs.edit().putLong(K_PROVIDER_NA_AT, now).apply()
                android.util.Log.i("UltraGoogleTv", "sync skipped: tv provider unavailable (${providerGate.reason})")
                com.ultratv.tv.nativeapp.RemoteLog.info("googletv", "sync skipped: tv provider unavailable (${providerGate.reason})")
                false
            }
            TvProviderGate.Decision.SKIP_SILENT, TvProviderGate.Decision.PROBE -> false
        }
    }

    /**
     * Le fournisseur de l'autorité `android.media.tv` existe-t-il pour cette appli ? PackageManager d'abord (sans réveiller
     * le fournisseur), puis, s'il dit non, un client instable relâché aussitôt : il suit exactement le chemin des écritures
     * (un fournisseur désactivé ou invisible ne répond ni à l'un ni à l'autre). Toute exception = absent.
     */
    @Suppress("DEPRECATION")
    private fun probeProvider(): Boolean {
        if (runCatching { ctx.packageManager.resolveContentProvider(TvContractCompat.AUTHORITY, 0) }.getOrNull() != null) return true
        return runCatching {
            val client = ctx.contentResolver.acquireUnstableContentProviderClient(TvContractCompat.AUTHORITY)
            client?.close()
            client != null
        }.getOrDefault(false)
    }

    fun start(scope: CoroutineScope) {
        if (!isTv) { report("skip", "sync skipped: not a leanback device"); return }
        if (started) return
        started = true
        if (!providerAvailable()) return
        report("start", "sync started")
        shareGuards(scope)
        // Collecteurs retenus pour être annulés si le fournisseur se révèle indisponible en cours de route.
        fun track(block: suspend CoroutineScope.() -> Unit) { val job = scope.launch(block = block); synchronized(jobs) { jobs += job } }
        track {
            scope_.flatMapLatest { (prof, pid) ->
                if (pid == null) { report("skip:wn", "watch next skipped: no active provider"); flowOf(Triple(emptyList<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>(), com.ultratv.tv.nativeapp.ui.home.HomeGuard.NONE, com.ultratv.tv.nativeapp.ui.home.HomeGuard.NONE)) }
                // Contrôle parental : aucun élément verrouillé (ni masqué pour le profil, comme « Continuer à regarder » de
                // l'accueil), republié à chaque changement de verrou.
                else combine(history.observeRecent(prof, pid, 40), historyGuard(pid)) { h, hg -> Triple(h, hg, hg) }
            }
                // 1.2.42 avait mis un debounce de 45 s : tant que l'historique bouge (position enregistrée toutes les 30 s,
                // synchro d'appareils), RIEN n'était publié, et rien non plus si l'appli était quittée / tuée dans les 45 s
                // suivant la fin d'une lecture — « Continuer à regarder » restait vide. Maintenant : 2 s de calme, puis
                // publication immédiate si la dernière écriture date de plus de 45 s, sinon au terme de ce délai (au plus
                // une écriture toutes les 45 s dans Google TV, mais la première ne se fait jamais attendre).
                .debounce(2_000).collectLatest { plan ->
                    delay(WatchNextPlan.waitMs(lastWatchNextSync, System.currentTimeMillis()))
                    runCatching {
                        val allowed = guardHistory(plan)
                        val sel = WatchNextPlan.select(allowed) + WatchNextPlan.selectLive(allowed)
                        syncWatchNext(sel, landscapes(sel))
                    }.onFailure { e -> fail("watchnext", "watch next", e) }
                    lastWatchNextSync = System.currentTimeMillis()
                }
        }
        track {
            enabled(TvHomeChannel.FAVORITES).flatMapLatest { on ->
                if (!on) flowOf<List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>?>(null)
                else scope_.flatMapLatest { (prof, pid) ->
                    if (pid == null) flowOf(emptyList())
                    else combine(favorites.observeForKind(prof, pid, "LIVE"), history.observeRecent(prof, pid, 40), historyGuard(pid)) { favs, hist, hg ->
                        favoritesSelection(pid, favs.map { it.remoteId }, hist, hg)
                    }
                }
            }.debounce(3_000).collect { sel ->
                runCatching {
                    if (sel == null) removeLegacy(TvHomeChannel.FAVORITES)
                    else { syncFavoritesChannel(sel); askBrowsableIfPending(TvHomeChannel.FAVORITES.key, prefs.getLong(K_CHANNEL, -1L)) }
                }.onFailure { e -> fail("favorites", "favorites channel", e) }
            }
        }
        // Chaîne « Chaînes récentes » : dernières chaînes du direct du profil, distincte de « Favoris ».
        track {
            enabled(TvHomeChannel.RECENT).flatMapLatest { on ->
                if (!on) flowOf<List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>?>(null)
                else scope_.flatMapLatest { (prof, pid) ->
                    if (pid == null) flowOf(emptyList())
                    else combine(history.observeRecentByKind(prof, pid, "LIVE", RecentChannelsPlan.FETCH), historyGuard(pid)) { hist, g ->
                        RecentChannelsPlan.select(orderedByRemote(pid, hist.map { it.remoteId }.distinct()), g)
                    }
                }
            }.debounce(3_000).collect { sel ->
                runCatching {
                    if (sel == null) removeLegacy(TvHomeChannel.RECENT)
                    else { syncRecentChannel(sel); askBrowsableIfPending(TvHomeChannel.RECENT.key, prefs.getLong(K_RECENT_CHANNEL, -1L)) }
                }.onFailure { e -> fail("recent", "recent channel", e) }
            }
        }
        // Chaîne « Nouveautés » : suit la source active ET le profil (catégories masquées, profil enfant). Les tables film /
        // série bougent pendant toute la synchro du catalogue : on attend 5 s de calme (fin de synchro), puis la signature
        // évite toute réécriture inutile.
        track {
            enabled(TvHomeChannel.NEWS).flatMapLatest { on ->
                if (!on) flowOf<List<NewsChannelPlan.Item>?>(null)
                else scope_.flatMapLatest { (_, pid) -> if (pid == null) { report("skip:news", "news channel skipped: no active provider"); flowOf(emptyList()) } else latestFlow(pid) }
            }.debounce(5_000).collect { sel ->
                runCatching {
                    if (sel == null) removeLegacy(TvHomeChannel.NEWS)
                    else { syncNewsChannel(sel); askBrowsableIfPending(TvHomeChannel.NEWS.key, prefs.getLong(K_NEWS_CHANNEL, -1L)) }
                }.onFailure { e -> fail("news", "news channel", e) }
            }
        }
        // Chaîne activée dans Réglages : l'invite système « Afficher sur l'accueil » sera présentée une fois la chaîne publiée.
        track {
            var last: TvChannelsChoice? = null
            choice.map { it.first }.distinctUntilChanged().collect { c ->
                val before = last
                last = c
                if (before != null) {
                    val added = TvChannelsChoice.newlyEnabled(before, c)
                    if (added.isNotEmpty()) prefs.edit().putStringSet(K_ASK_BROWSABLE, (prefs.getStringSet(K_ASK_BROWSABLE, emptySet()).orEmpty() + added).toSet()).apply()
                }
            }
        }
        startOptionalChannels(::track)
    }

    /**
     * Chaînes activables (Nouveaux films, Nouvelles séries, Top 10, mieux notés, Ma liste, Sport en direct, catégories) :
     * désactivées par défaut. Rien de lourd juste après le démarrage de la box : premier travail au plus tôt
     * [TvChannelPlan.BOOT_SETTLE_MS] après le démarrage de l'appareil.
     */
    private fun startOptionalChannels(track: (suspend CoroutineScope.() -> Unit) -> Unit) {
        suspend fun settle() = delay(TvChannelPlan.bootWaitMs(android.os.SystemClock.elapsedRealtime()))
        fun optional(kind: TvHomeChannel, debounceMs: Long, data: (prof: Long, pid: Long) -> kotlinx.coroutines.flow.Flow<List<TvProgramItem>>) = track {
            settle()
            enabled(kind).flatMapLatest { on ->
                if (!on) flowOf<List<TvProgramItem>?>(null)
                else scope_.flatMapLatest { (prof, pid) -> if (pid == null) flowOf(emptyList()) else data(prof, pid) }
            }.debounce(debounceMs).collect { items ->
                runCatching {
                    if (items == null) removeOptional(kind.key)
                    else syncOptional(kind.key, com.ultratv.tv.nativeapp.i18n.DesignStrings(appLang()).tvChannelPublishedName(kind), optionalAppLink(kind), items)
                }.onFailure { e -> fail(kind.key, "${kind.key} channel", e) }
            }
        }
        fun stamp(pid: Long) = providerDao.observeCatalogStamp(pid).distinctUntilChanged()
        optional(TvHomeChannel.NEW_MOVIES, 5_000) { _, pid ->
            combine(stamp(pid), rowGuard(pid)) { _, g -> g }.map { g ->
                TvChannelPlan.selectVod(movies.latest(pid, TvChannelPlan.FETCH).map(::vodOf), g).map { TvChannelPlan.vodProgram("NEWMOVIES", it) }
            }
        }
        optional(TvHomeChannel.NEW_SERIES, 5_000) { _, pid ->
            combine(stamp(pid), rowGuard(pid)) { _, g -> g }.map { g ->
                TvChannelPlan.selectVod(seriesDao.latest(pid, TvChannelPlan.FETCH).map(::vodOf), g).map { TvChannelPlan.vodProgram("NEWSERIES", it) }
            }
        }
        optional(TvHomeChannel.TOP_RATED_MOVIES, 5_000) { _, pid ->
            combine(stamp(pid), rowGuard(pid)) { _, g -> g }.map { g ->
                TvChannelPlan.selectVod(movies.observeTopRated(pid, TvChannelPlan.FETCH).first().map(::vodOf), g).map { TvChannelPlan.vodProgram("TOPMOVIES", it) }
            }
        }
        optional(TvHomeChannel.TOP10, 5_000) { _, pid ->
            combine(traktMatcher.get().rows(flowOf(pid)), stamp(pid), rowGuard(pid)) { rows, _, g -> rows to g }.map { (rows, g) ->
                val trending = trendingVods(rows.trending)
                val top = movies.observeTopRated(pid, TvChannelPlan.FETCH).first().map(::vodOf)
                TvChannelPlan.top10(trending, top, g).map { TvChannelPlan.vodProgram("TOP10", it) }
            }
        }
        optional(TvHomeChannel.MY_LIST, 3_000) { prof, pid ->
            combine(favorites.observeForKind(prof, pid, "MOVIE"), favorites.observeForKind(prof, pid, "SERIES"), historyGuard(pid)) { fm, fs, g ->
                val ms = orderedMovies(pid, fm.map { it.remoteId }).map(::vodOf)
                val ss = orderedSeries(pid, fs.map { it.remoteId }).map(::vodOf)
                TvChannelPlan.selectVod(TvChannelPlan.interleave(ms, ss), g).map { TvChannelPlan.vodProgram("MYLIST", it) }
            }
        }
        optional(TvHomeChannel.SPORT_LIVE, 3_000) { _, pid ->
            val tick = kotlinx.coroutines.flow.flow { while (true) { emit(Unit); delay(TvChannelPlan.SPORT_REFRESH_MS) } }
            combine(tick, rowGuard(pid)) { _, g -> g }.map { g -> sportPrograms(pid, g) }
        }
        // Catégories de chaînes choisies (au plus 3) : une chaîne chacune, catégories de la source active seulement.
        track {
            settle()
            combine(choice.map { it.first.categories }.distinctUntilChanged(), scope_) { cats, sc -> cats to sc.second }
                .flatMapLatest { (cats, pid) ->
                    val mine = if (pid == null) emptyList() else cats.filter { it.providerId == pid }
                    if (pid == null || mine.isEmpty()) flowOf(emptyMap<String, Pair<TvCategoryRef, List<TvProgramItem>>>())
                    else combine(mine.map { ref ->
                        combine(channels.observeForCategory(pid, ref.categoryId), rowGuard(pid)) { list, g ->
                            ref to TvChannelPlan.selectChannels(list, g).map { TvChannelPlan.channelProgram("CAT", it, bannerUri()) }
                        }
                    }) { arr -> arr.associate { it.first.key to it } }
                }
                .debounce(3_000).collect { wanted ->
                    runCatching {
                        val published = prefs.getStringSet(K_OPTIONAL_KEYS, emptySet()).orEmpty().filter { it.startsWith("cat:") }.toSet()
                        TvChannelPlan.stale(published, wanted.keys).forEach { removeOptional(it) }
                        for ((key, v) in wanted) {
                            val (ref, items) = v
                            val name = categoryDao.forProviderKind(ref.providerId, "LIVE").firstOrNull { it.remoteId == ref.categoryId }?.name ?: continue
                            syncOptional(key, name, com.ultratv.tv.nativeapp.nav.DeepLink.liveCategory(ref.categoryId), items)
                        }
                    }.onFailure { e -> fail("categories", "category channels", e) }
                }
        }
    }

    private fun bannerUri() = "android.resource://${ctx.packageName}/drawable/banner"

    private fun optionalAppLink(kind: TvHomeChannel): String = when (kind) {
        TvHomeChannel.NEW_MOVIES, TvHomeChannel.TOP_RATED_MOVIES -> com.ultratv.tv.nativeapp.nav.DeepLink.section(com.ultratv.tv.nativeapp.nav.Routes.MOVIES)
        TvHomeChannel.NEW_SERIES -> com.ultratv.tv.nativeapp.nav.DeepLink.section(com.ultratv.tv.nativeapp.nav.Routes.SERIES)
        TvHomeChannel.SPORT_LIVE, TvHomeChannel.RECENT, TvHomeChannel.FAVORITES -> com.ultratv.tv.nativeapp.nav.DeepLink.section(com.ultratv.tv.nativeapp.nav.Routes.LIVE)
        else -> "${com.ultratv.tv.nativeapp.nav.DeepLink.SCHEME}://home"
    }

    private fun vodOf(m: com.ultratv.tv.nativeapp.data.db.MovieEntity) =
        TvChannelPlan.Vod(NewsChannelPlan.Kind.MOVIE, m.providerId, m.remoteId, m.title, m.poster, m.backdrop, m.categoryId)

    private fun vodOf(s: com.ultratv.tv.nativeapp.data.db.SeriesEntity) =
        TvChannelPlan.Vod(NewsChannelPlan.Kind.SERIES, s.providerId, s.remoteId, s.title, s.poster, s.backdrop, s.categoryId)

    /** Films dans l'ordre des identifiants donnés (lots de 900, limite SQLite). */
    private suspend fun orderedMovies(pid: Long, rids: List<String>): List<com.ultratv.tv.nativeapp.data.db.MovieEntity> {
        if (rids.isEmpty()) return emptyList()
        val by = rids.distinct().take(TvChannelPlan.FETCH * 5).chunked(900).flatMap { movies.byRemoteIds(pid, it) }.associateBy { it.remoteId }
        return rids.mapNotNull { by[it] }
    }

    private suspend fun orderedSeries(pid: Long, rids: List<String>): List<com.ultratv.tv.nativeapp.data.db.SeriesEntity> {
        if (rids.isEmpty()) return emptyList()
        val by = rids.distinct().take(TvChannelPlan.FETCH * 5).chunked(900).flatMap { seriesDao.byRemoteIds(pid, it) }.associateBy { it.remoteId }
        return rids.mapNotNull { by[it] }
    }

    /** Tendances Trakt (identifiants locaux, déjà rapprochées de la source) dans leur ordre. */
    private suspend fun trendingVods(cards: List<com.ultratv.tv.nativeapp.data.trakt.TraktCard>): List<TvChannelPlan.Vod> {
        if (cards.isEmpty()) return emptyList()
        val ms = cards.filter { !it.isShow }.map { it.id }.distinct().chunked(900).flatMap { movies.byIds(it) }.associateBy { it.id }
        val ss = cards.filter { it.isShow }.map { it.id }.distinct().chunked(900).flatMap { seriesDao.byIds(it) }.associateBy { it.id }
        return cards.mapNotNull { c -> if (c.isShow) ss[c.id]?.let(::vodOf) else ms[c.id]?.let(::vodOf) }
    }

    /** Matchs en cours (rubrique Sport) sur des chaînes que l'accueil peut montrer : une tuile par chaîne. */
    private suspend fun sportPrograms(pid: Long, g: com.ultratv.tv.nativeapp.ui.home.HomeGuard): List<TvProgramItem> {
        val now = System.currentTimeMillis()
        val live = com.ultratv.tv.nativeapp.data.sport.SportLogic.liveNow(sportRepo.get().load(pid, now).entries, now)
        return live.filter { g.allowsChannel(it.channel) }.distinctBy { it.channel.providerId to it.channel.remoteId }.take(TvChannelPlan.MAX).map { e ->
            val title = e.prog.title.takeIf { it.isNotBlank() } ?: e.channel.title
            TvChannelPlan.channelProgram("SPORT", e.channel, bannerUri(), title = title, description = e.channel.title)
        }
    }

    /** Favoris complétés par les dernières chaînes regardées : choix de l'utilisateur, seuls le verrou et l'adulte (Enfants) les écartent. */
    private suspend fun favoritesSelection(
        pid: Long, favRemoteIds: List<String>, hist: List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>,
        hg: com.ultratv.tv.nativeapp.ui.home.HomeGuard,
    ): List<com.ultratv.tv.nativeapp.data.db.ChannelEntity> {
        val fav = orderedByRemote(pid, favRemoteIds).filter(hg::allowsChannel)
        val recent = orderedByRemote(pid, WatchNextPlan.selectLive(hist).map { it.remoteId }).filter(hg::allowsChannel)
        return FavoritesChannelPlan.select(fav, recent)
    }

    /** Historique filtré pour « Continuer à regarder » (voir [HistoryGuardPlan]) ; catégorie relue dans le catalogue local. */
    private suspend fun guardHistory(plan: Triple<List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>, com.ultratv.tv.nativeapp.ui.home.HomeGuard, com.ultratv.tv.nativeapp.ui.home.HomeGuard>): List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity> {
        val (list, live, vod) = plan
        if (list.isEmpty()) return list
        val cats = HashMap<String, String?>()
        for ((pk, rows) in list.groupBy { it.providerId to it.kind }) {
            val (p, kind) = pk
            val rids = rows.map(HistoryGuardPlan::titleRemoteId).distinct().take(900)
            val found = runCatching {
                when (kind) {
                    "LIVE" -> channels.categoriesByRemote(p, rids)
                    "MOVIE" -> movies.categoriesByRemote(p, rids)
                    "EPISODE" -> seriesDao.categoriesByRemote(p, rids)
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
            for (f in found) cats["$p:$kind:${f.remoteId}"] = f.categoryId
        }
        return list.filter { h -> HistoryGuardPlan.allows(h, cats["${h.providerId}:${h.kind}:${HistoryGuardPlan.titleRemoteId(h)}"], live, vod) }
    }

    /**
     * Nouveautés : relues quand l'horodatage de synchro du catalogue CHANGE (fin d'une synchro de films / séries), plus à
     * chaque lot inséré. Avant, observer les tables movie / series relançait deux requêtes (et la sélection) à chaque lot
     * d'une synchro de dizaines de milliers de lignes, sur le même fichier de base que l'interface — le debounce en aval
     * ne limitait que la publication, pas ce travail. Relues aussi à chaque changement de verrou, de masquage ou de profil.
     */
    private fun latestFlow(pid: Long): kotlinx.coroutines.flow.Flow<List<NewsChannelPlan.Item>> =
        combine(providerDao.observeCatalogStamp(pid).distinctUntilChanged(), rowGuard(pid)) { _, g -> g }.map { g -> latestNow(pid, g) }

    /** Derniers ajouts que les rangées de l'accueil montrent au profil actif (ni masqués, ni verrouillés, ni adultes). */
    private suspend fun latestNow(pid: Long, guard: com.ultratv.tv.nativeapp.ui.home.HomeGuard): List<NewsChannelPlan.Item> {
        val ms = movies.latest(pid, NewsChannelPlan.FETCH)
        val ss = seriesDao.latest(pid, NewsChannelPlan.FETCH)
        return NewsChannelPlan.select(
            NewsChannelPlan.visible(ms.map { NewsChannelPlan.Item(NewsChannelPlan.Kind.MOVIE, it.providerId, it.remoteId, it.title, it.poster, it.addedKey, it.backdrop, it.categoryId) }, guard),
            NewsChannelPlan.visible(ss.map { NewsChannelPlan.Item(NewsChannelPlan.Kind.SERIES, it.providerId, it.remoteId, it.title, it.poster, it.addedKey, it.backdrop, it.categoryId) }, guard),
        )
    }

    /** Fonds (images paysage) des films et séries de « Continuer à regarder », lus dans le catalogue local. */
    private suspend fun landscapes(selected: List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>): Map<WatchNextPlan.LandscapeKey, String> {
        val out = HashMap<WatchNextPlan.LandscapeKey, String>()
        for (h in selected) {
            val key = WatchNextPlan.landscapeKey(h) ?: continue
            if (key in out) continue
            val bg = runCatching {
                when (key.kind) {
                    NewsChannelPlan.Kind.MOVIE -> movies.byRemoteId(h.providerId, key.remoteId)?.backdrop
                    NewsChannelPlan.Kind.SERIES -> seriesDao.byRemoteId(h.providerId, key.remoteId)?.backdrop
                }
            }.getOrNull()
            if (!bg.isNullOrBlank()) out[key] = bg
        }
        return out
    }

    /** Nom de la chaîne dans la langue de l'appli (« système » : langue de l'appareil). */
    private suspend fun newsChannelName(): String = com.ultratv.tv.nativeapp.i18n.DesignStrings(appLang()).newsChannelName

    private suspend fun appLang(): com.ultratv.tv.nativeapp.i18n.AppLang {
        val code = prefsStore.flow.first().language
        return com.ultratv.tv.nativeapp.i18n.AppLang.fromCode(code).let { l ->
            if (l != com.ultratv.tv.nativeapp.i18n.AppLang.System) l
            else com.ultratv.tv.nativeapp.i18n.AppLang.entries.firstOrNull { it.code == java.util.Locale.getDefault().language } ?: com.ultratv.tv.nativeapp.i18n.AppLang.English
        }
    }

    /** Synchro ponctuelle (demande du système à l'initialisation des programmes). */
    suspend fun syncOnce() {
        if (!isTv || !providerAvailable()) return
        val pid = activeProvider.first() ?: return
        val prof = profiles.currentIdNow
        val hist = history.observeRecent(prof, pid, 40).first()
        report("once", "initialize-programs broadcast received")
        // Mêmes filtres que la synchro continue : aucun élément verrouillé, chaînes désactivées dans Réglages non publiées.
        val g = rowGuard(pid).first()
        val hg = historyGuard(pid).first()
        val (c, sport) = choice.first()
        runCatching {
            val allowed = guardHistory(Triple(hist, hg, hg))
            val sel = WatchNextPlan.select(allowed) + WatchNextPlan.selectLive(allowed)
            syncWatchNext(sel, landscapes(sel))
        }.onFailure { fail("watchnext", "watch next", it) }
        if (c.isEffective(TvHomeChannel.FAVORITES, sport)) runCatching {
            val favs = favorites.observeForKind(prof, pid, "LIVE").first()
            syncFavoritesChannel(favoritesSelection(pid, favs.map { it.remoteId }, hist, hg))
        }.onFailure { fail("favorites", "favorites channel", it) }
        if (c.isEffective(TvHomeChannel.RECENT, sport)) runCatching {
            val recent = history.observeRecentByKind(prof, pid, "LIVE", RecentChannelsPlan.FETCH).first()
            syncRecentChannel(RecentChannelsPlan.select(orderedByRemote(pid, recent.map { it.remoteId }.distinct()), hg))
        }.onFailure { fail("recent", "recent channel", it) }
        if (c.isEffective(TvHomeChannel.NEWS, sport)) runCatching { syncNewsChannel(latestNow(pid, g)) }.onFailure { fail("news", "news channel", it) }
    }

    /** Chaînes dans l'ordre des identifiants donnés (la requête par lot ne garantit pas l'ordre). */
    private suspend fun orderedByRemote(pid: Long, ids: List<String>): List<com.ultratv.tv.nativeapp.data.db.ChannelEntity> {
        if (ids.isEmpty()) return emptyList()
        val byId = channels.byRemoteIds(pid, ids).associateBy { it.remoteId }
        return ids.mapNotNull { byId[it] }
    }

    // ---- Watch Next -----------------------------------------------------------------

    private fun syncWatchNext(selected: List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>, landscapes: Map<WatchNextPlan.LandscapeKey, String> = emptyMap()) {
        if (!providerAvailable()) return
        val cr = ctx.contentResolver
        // Lignes déjà publiées par l'application : identifiant interne -> identifiant de ligne.
        val existing = HashMap<String, Long>()
        cr.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = WatchNextProgram.fromCursor(c)
                p.internalProviderId?.let { existing[it] = p.id }
            }
        }
        val keep = HashSet<String>()
        var failed = 0
        android.util.Log.i("UltraGoogleTv", "watch next: ${selected.size} (${selected.count { it.kind == "LIVE" }} live)")
        for (h in selected) {
            val key = WatchNextPlan.internalId(h)
            keep += key
            if (h.kind == "LIVE") {
                // Chaîne du direct : vignette 16:9 (logo), marquée « en direct », sans progression.
                val live = WatchNextProgram.Builder()
                    .setType(TvContractCompat.WatchNextPrograms.TYPE_CHANNEL)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(h.watchedAt)
                    .setTitle(h.title)
                    .setLive(true)
                    .setPosterArtUri(Uri.parse(h.poster?.takeIf { it.isNotBlank() } ?: "android.resource://${ctx.packageName}/drawable/banner"))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setInternalProviderId(key)
                    .setIntentUri(Uri.parse(WatchNextPlan.deepLink(h)))
                    .build()
                val rowId = existing[key]
                val ok = if (rowId == null) cr.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, live.toContentValues()) != null
                else cr.update(TvContractCompat.buildWatchNextProgramUri(rowId), live.toContentValues(), null, null) > 0
                if (!ok) failed++
                continue
            }
            val art = WatchNextPlan.art(h, WatchNextPlan.landscapeKey(h)?.let { landscapes[it] })
            val b = WatchNextProgram.Builder()
                .setType(if (h.kind == "EPISODE") TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(h.watchedAt)
                // Titre nettoyé, comme « Reprendre la lecture » : « Captain Marvel », pas « TOP - Captain Marvel (2019) ».
                .setTitle(com.ultratv.tv.nativeapp.data.repo.TitleCleaner.historyTitle(h.title))
                // Fond du titre en 16:9 s'il existe (film, ou série de l'épisode), sinon l'affiche en 2:3.
                .setPosterArtUri(Uri.parse(art.uri))
                .setPosterArtAspectRatio(art.aspectRatio)
                .setLastPlaybackPositionMillis(h.positionMs.toInt())
                .setInternalProviderId(key)
                .setIntentUri(Uri.parse(WatchNextPlan.deepLink(h)))
            if (h.durationMs > 0) b.setDurationMillis(h.durationMs.toInt())
            val program = b.build()
            val rowId = existing[key]
            val ok = if (rowId == null) cr.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues()) != null
            else cr.update(TvContractCompat.buildWatchNextProgramUri(rowId), program.toContentValues(), null, null) > 0
            if (!ok) failed++
        }
        // Terminé, supprimé de l'historique ou éclipsé par un épisode plus récent : on le retire de l'accueil.
        existing.filterKeys { it !in keep }.values.forEach { cr.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
        report("watchnext", "watch next published ${selected.size - failed}/${selected.size} (${selected.count { it.kind == "LIVE" }} live, ${existing.size} existing)", warn = failed > 0)
    }

    // ---- Chaîne de prévisualisation « Favoris » -------------------------------------------

    private fun syncFavoritesChannel(selected: List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>) {
        if (!providerAvailable()) return
        val helper = PreviewChannelHelper(ctx)
        var channelId = prefs.getLong(K_CHANNEL, -1L)
        if (ChannelPublishPlan.mustRecreate(channelId, channelExists(channelId))) {
            report("favorites:gone", "favorites channel $channelId missing in provider: recreating", warn = true)
            prefs.edit().remove(K_CHANNEL).remove(K_SIGNATURE).apply()
            channelId = -1L
        }
        if (selected.isEmpty() && channelId < 0) { report("skip:fav", "favorites channel skipped: no favorites nor recent live"); return }
        if (channelId < 0) {
            val logo = ContextCompat.getDrawable(ctx, com.ultratv.tv.nativeapp.R.mipmap.ic_launcher)!!.toBitmap(160, 160)
            val channel = PreviewChannel.Builder()
                .setDisplayName(ctx.getString(com.ultratv.tv.nativeapp.R.string.tv_favorites_channel))
                .setAppLinkIntentUri(Uri.parse("${com.ultratv.tv.nativeapp.nav.DeepLink.SCHEME}://home"))
                .setLogo(logo)
                .build()
            channelId = publishChannel(helper, channel)
            if (channelId < 0) { report("favorites:publish", "favorites channel publish refused by system (id=$channelId)", warn = true); return }
            prefs.edit().putLong(K_CHANNEL, channelId).remove(K_SIGNATURE).apply()
            report("favorites:created", "favorites channel created id=$channelId default=${prefs.getBoolean(K_DEFAULT_DONE, false)}")
        }
        val signature = FavoritesChannelPlan.signature(selected)
        if (prefs.getString(K_SIGNATURE, null) == signature) return
        ctx.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null)
        for (c in selected) {
            val b = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(c.title)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setInternalProviderId("LIVE:${c.providerId}:${c.remoteId}")
                .setIntentUri(Uri.parse(FavoritesChannelPlan.deepLink(c)))
            b.setPosterArtUri(Uri.parse(c.logo?.takeIf { it.isNotBlank() } ?: "android.resource://${ctx.packageName}/drawable/banner"))
            helper.publishPreviewProgram(b.build())
        }
        prefs.edit().putString(K_SIGNATURE, signature).apply()
        report("favorites:programs", "favorites channel id=$channelId programs=${selected.size}")
    }

    // ---- Chaîne de prévisualisation « Chaînes récentes » -------------------------------------

    private val recentLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun syncRecentChannel(selected: List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>) = recentLock.withLock {
        if (!providerAvailable()) return@withLock
        val helper = PreviewChannelHelper(ctx)
        var channelId = prefs.getLong(K_RECENT_CHANNEL, -1L)
        if (ChannelPublishPlan.mustRecreate(channelId, channelExists(channelId))) {
            report("recent:gone", "recent channel $channelId missing in provider: recreating", warn = true)
            prefs.edit().remove(K_RECENT_CHANNEL).remove(K_RECENT_SIGNATURE).apply()
            channelId = -1L
        }
        if (selected.isEmpty() && channelId < 0) { report("skip:recent", "recent channel skipped: no recent live"); return@withLock }
        val name = com.ultratv.tv.nativeapp.i18n.DesignStrings(appLang()).recentChannelsName
        if (channelId < 0) {
            val logo = ContextCompat.getDrawable(ctx, com.ultratv.tv.nativeapp.R.mipmap.ic_launcher)!!.toBitmap(160, 160)
            val channel = PreviewChannel.Builder()
                .setDisplayName(name)
                .setAppLinkIntentUri(Uri.parse(com.ultratv.tv.nativeapp.nav.DeepLink.section(com.ultratv.tv.nativeapp.nav.Routes.LIVE)))
                .setLogo(logo)
                .build()
            channelId = publishChannel(helper, channel)
            if (channelId < 0) { report("recent:publish", "recent channel publish refused by system (id=$channelId)", warn = true); return@withLock }
            prefs.edit().putLong(K_RECENT_CHANNEL, channelId).putString(K_RECENT_NAME, name).remove(K_RECENT_SIGNATURE).apply()
            report("recent:created", "recent channel created id=$channelId items=${selected.size}")
        } else if (prefs.getString(K_RECENT_NAME, null) != name) {
            runCatching {
                val current = helper.getPreviewChannel(channelId)
                if (current != null) helper.updatePreviewChannel(channelId, PreviewChannel.Builder(current).setDisplayName(name).build())
            }
            prefs.edit().putString(K_RECENT_NAME, name).apply()
        }
        val signature = RecentChannelsPlan.signature(selected)
        if (prefs.getString(K_RECENT_SIGNATURE, null) == signature) return@withLock
        ctx.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null)
        var failed = 0
        selected.forEachIndexed { index, c ->
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(c.title)
                .setPosterArtUri(Uri.parse(c.logo?.takeIf { it.isNotBlank() } ?: "android.resource://${ctx.packageName}/drawable/banner"))
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setLive(true)
                .setWeight(NewsChannelPlan.weight(index, selected.size))
                .setInternalProviderId(RecentChannelsPlan.internalId(c))
                .setIntentUri(Uri.parse(RecentChannelsPlan.deepLink(c)))
                .build()
            if (helper.publishPreviewProgram(program) < 0) failed++
        }
        if (failed == 0) prefs.edit().putString(K_RECENT_SIGNATURE, signature).apply()
        report("recent:programs", "recent channel id=$channelId programs=${selected.size} failed=$failed", warn = failed > 0)
    }

    /**
     * La chaîne existe-t-elle encore dans le fournisseur du système ? Requête directe sur son identifiant, jamais
     * `PreviewChannelHelper.getPreviewChannel` : il lève « Unknown URI …/channel/-1 » (Xiaomi) pour un identifiant
     * absent, et plante (NullPointerException) sur une chaîne dont un champ est vide (Google TV). Toute erreur = absente.
     */
    private fun channelExists(channelId: Long): Boolean {
        if (channelId < 0) return false
        return runCatching {
            ctx.contentResolver.query(TvContractCompat.buildChannelUri(channelId), arrayOf(TvContractCompat.Channels._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    /** La première chaîne publiée par l'appli est la chaîne par défaut (affichée d'office) ; les suivantes sont ordinaires. */
    private fun publishChannel(helper: PreviewChannelHelper, channel: PreviewChannel): Long {
        val hasDefault = prefs.getBoolean(K_DEFAULT_DONE, false) || prefs.getLong(K_CHANNEL, -1L) >= 0
        return when (ChannelPublishPlan.mode(hasDefault)) {
            ChannelPublishPlan.Mode.DEFAULT -> helper.publishDefaultChannel(channel).also { if (it >= 0) prefs.edit().putBoolean(K_DEFAULT_DONE, true).apply() }
            ChannelPublishPlan.Mode.NORMAL -> helper.publishChannel(channel)
        }
    }

    /**
     * Intent de l'invite système « Afficher la chaîne Nouveautés sur l'accueil ? » (à lancer avec startActivityForResult
     * depuis une Activity), ou null : pas de chaîne, déjà affichée, déjà demandée une fois, invite indisponible, ou
     * fournisseur TV indisponible.
     */
    fun browsableRequest(): Intent? {
        if (!isTv || !providerAvailable()) return null
        val channelId = prefs.getLong(K_NEWS_CHANNEL, -1L)
        if (channelId < 0) return null
        val browsable = runCatching {
            ctx.contentResolver.query(TvContractCompat.buildChannelUri(channelId), arrayOf(TvContractCompat.Channels.COLUMN_BROWSABLE), null, null, null)
                ?.use { if (it.moveToFirst()) it.getInt(0) == 1 else null }
        }.getOrNull() ?: return null
        if (!ChannelPublishPlan.shouldAskBrowsable(channelId, browsable, prefs.getBoolean(K_BROWSABLE_ASKED, false))) return null
        val intent = Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE).putExtra(TvContractCompat.EXTRA_CHANNEL_ID, channelId)
        if (intent.resolveActivity(ctx.packageManager) == null) {
            // Système sans l'invite (Skyworth) : au plus une fois par jour (préférence), pas à chaque lancement, et en info,
            // l'appli n'y pouvant rien. Hors [gate] : un message identique le lendemain y serait bloqué.
            val now = System.currentTimeMillis()
            if (TvProviderGate.shouldReport(prefs.getLong(K_BROWSABLE_NA_AT, 0L), now)) {
                prefs.edit().putLong(K_BROWSABLE_NA_AT, now).apply()
                android.util.Log.i("UltraGoogleTv", "browsable prompt unavailable on this system")
                com.ultratv.tv.nativeapp.RemoteLog.info("googletv", "browsable prompt unavailable on this system")
            }
            return null
        }
        prefs.edit().putBoolean(K_BROWSABLE_ASKED, true).apply()
        report("browsable:ask", "browsable prompt shown for news channel $channelId")
        return intent
    }

    fun onBrowsableResult(resultOk: Boolean) = report("browsable:result", "browsable prompt result=${if (resultOk) "accepted" else "declined"}")

    // ---- Chaîne de prévisualisation « Nouveautés » ------------------------------------------

    /** Les écritures dans le fournisseur de contenu sont sérialisées (collecte continue + demande du système). */
    private val newsLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun syncNewsChannel(selected: List<NewsChannelPlan.Item>) = newsLock.withLock {
        if (!providerAvailable()) return@withLock
        val helper = PreviewChannelHelper(ctx)
        var channelId = prefs.getLong(K_NEWS_CHANNEL, -1L)
        if (ChannelPublishPlan.mustRecreate(channelId, channelExists(channelId))) {
            report("news:gone", "news channel $channelId missing in provider: recreating", warn = true)
            prefs.edit().remove(K_NEWS_CHANNEL).remove(K_NEWS_SIGNATURE).apply()
            channelId = -1L
        }
        if (selected.isEmpty() && channelId < 0) { report("skip:news0", "news channel skipped: no movie/series with poster yet"); return@withLock }
        val name = newsChannelName()
        if (channelId < 0) {
            val logo = ContextCompat.getDrawable(ctx, com.ultratv.tv.nativeapp.R.mipmap.ic_launcher)!!.toBitmap(160, 160)
            val channel = PreviewChannel.Builder()
                .setDisplayName(name)
                .setAppLinkIntentUri(Uri.parse("${com.ultratv.tv.nativeapp.nav.DeepLink.SCHEME}://home"))
                .setLogo(logo)
                .build()
            // Chaîne ordinaire : l'utilisateur l'ajoute à l'accueil depuis « Personnaliser les chaînes » (seule la
            // première chaîne de l'application est affichée d'office par le système).
            channelId = publishChannel(helper, channel)
            if (channelId < 0) { report("news:publish", "news channel publish refused by system (id=$channelId)", warn = true); return@withLock }
            prefs.edit().putLong(K_NEWS_CHANNEL, channelId).putString(K_NEWS_NAME, name).remove(K_NEWS_SIGNATURE).apply()
            report("news:created", "news channel created id=$channelId default=${prefs.getBoolean(K_DEFAULT_DONE, false)} items=${selected.size}")
        } else if (prefs.getString(K_NEWS_NAME, null) != name) {
            // Langue de l'appli changée : on renomme la chaîne.
            runCatching {
                val current = helper.getPreviewChannel(channelId)
                if (current != null) helper.updatePreviewChannel(channelId, PreviewChannel.Builder(current).setDisplayName(name).build())
            }
            prefs.edit().putString(K_NEWS_NAME, name).apply()
        }
        val signature = NewsChannelPlan.signature(selected)
        val cr = ctx.contentResolver
        val existing = HashMap<String, Long>()
        cr.query(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), PreviewProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = PreviewProgram.fromCursor(c)
                p.internalProviderId?.let { existing[it] = p.id }
            }
        }
        if (!ChannelPublishPlan.mustRewrite(prefs.getString(K_NEWS_SIGNATURE, null) == signature, existing.size, selected.size)) return@withLock
        fun program(i: NewsChannelPlan.Item, index: Int) = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(if (i.kind == NewsChannelPlan.Kind.MOVIE) TvContractCompat.PreviewPrograms.TYPE_MOVIE else TvContractCompat.PreviewPrograms.TYPE_TV_SERIES)
            .setTitle(i.title)
            .setPosterArtUri(Uri.parse(NewsChannelPlan.art(i).uri))
            .setPosterArtAspectRatio(NewsChannelPlan.art(i).aspectRatio)
            .setWeight(NewsChannelPlan.weight(index, selected.size))
            .setInternalProviderId(NewsChannelPlan.internalId(i))
            .setIntentUri(Uri.parse(NewsChannelPlan.deepLink(i)))
            .build()
        val diff = NewsChannelPlan.diff(existing, selected)
        var failed = 0
        diff.deleteRowIds.forEach { cr.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
        selected.forEachIndexed { index, i ->
            val rowId = diff.update.firstOrNull { it.second === i }?.first
            if (rowId != null) cr.update(TvContractCompat.buildPreviewProgramUri(rowId), program(i, index).toContentValues(), null, null)
            else if (helper.publishPreviewProgram(program(i, index)) < 0) failed++
        }
        // Signature mémorisée seulement si tout est passé : un échec partiel est retenté à la synchro suivante.
        if (failed == 0) prefs.edit().putString(K_NEWS_SIGNATURE, signature).apply()
        report("news:programs", "news channel id=$channelId programs=${selected.size} (+${diff.insert.size} -${diff.deleteRowIds.size}) failed=$failed", warn = failed > 0)
        android.util.Log.i("UltraGoogleTv", "news channel: ${selected.size} (+${diff.insert.size} -${diff.deleteRowIds.size})")
    }

    // ---- Activation / désactivation (Réglages › Affichage › Chaînes sur l'écran d'accueil Android TV) ----------------

    /** Supprime une chaîne publiée de l'écran d'accueil (ses programmes partent avec elle). Toute erreur : ignorée. */
    private fun deleteChannel(channelId: Long) {
        if (channelId < 0) return
        runCatching { PreviewChannelHelper(ctx).deletePreviewChannel(channelId) }
            .onFailure { runCatching { ctx.contentResolver.delete(TvContractCompat.buildChannelUri(channelId), null, null) } }
    }

    /** Chaîne historique désactivée : supprimée, ses préférences effacées (recréée à la réactivation). */
    private suspend fun removeLegacy(kind: TvHomeChannel) {
        val (lock, keys) = when (kind) {
            TvHomeChannel.FAVORITES -> null to listOf(K_CHANNEL, K_SIGNATURE)
            TvHomeChannel.RECENT -> recentLock to listOf(K_RECENT_CHANNEL, K_RECENT_SIGNATURE, K_RECENT_NAME)
            TvHomeChannel.NEWS -> newsLock to listOf(K_NEWS_CHANNEL, K_NEWS_SIGNATURE, K_NEWS_NAME)
            else -> return
        }
        suspend fun doRemove() {
            val id = prefs.getLong(keys.first(), -1L)
            if (id < 0) return
            if (!providerAvailable()) return
            deleteChannel(id)
            prefs.edit().apply { keys.forEach { remove(it) } }.apply()
            report("${kind.key}:removed", "${kind.key} channel $id removed (disabled in settings)")
        }
        if (lock != null) lock.withLock { doRemove() } else doRemove()
    }

    /**
     * Chaîne activée à l'instant dans Réglages : invite système « Afficher cette chaîne sur l'accueil ? » (une fois). Sans
     * invite sur le système, TvContractCompat.requestChannelBrowsable. Déjà affichée : rien.
     */
    private fun askBrowsableIfPending(key: String, channelId: Long) {
        if (channelId < 0) return
        val pending = prefs.getStringSet(K_ASK_BROWSABLE, emptySet()).orEmpty()
        if (key !in pending) return
        val browsable = runCatching {
            ctx.contentResolver.query(TvContractCompat.buildChannelUri(channelId), arrayOf(TvContractCompat.Channels.COLUMN_BROWSABLE), null, null, null)
                ?.use { if (it.moveToFirst()) it.getInt(0) == 1 else null }
        }.getOrNull()
        fun done() = prefs.edit().putStringSet(K_ASK_BROWSABLE, (pending - key).toSet()).apply()
        if (browsable == true) { done(); return }
        val intent = Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE).putExtra(TvContractCompat.EXTRA_CHANNEL_ID, channelId)
        if (intent.resolveActivity(ctx.packageManager) != null) {
            // Invite présentée par l'activité au premier plan ; sans elle (appli fermée), elle attend la prochaine publication.
            if (_browsablePrompts.subscriptionCount.value == 0 || !_browsablePrompts.tryEmit(intent)) return
            report("browsable:ask:$key", "browsable prompt requested for $key channel $channelId")
        } else if (android.os.Build.VERSION.SDK_INT >= 26) {
            runCatching { TvContractCompat.requestChannelBrowsable(ctx, channelId) }
                .onFailure { android.util.Log.w("UltraGoogleTv", "requestChannelBrowsable $key", it) }
        }
        done()
    }

    /** Écritures des chaînes activables sérialisées (collecteurs + suppression). */
    private val optionalLock = kotlinx.coroutines.sync.Mutex()

    private fun kId(key: String) = "opt_id_$key"
    private fun kSig(key: String) = "opt_sig_$key"
    private fun kName(key: String) = "opt_name_$key"

    /** Chaîne activable désactivée (ou catégorie retirée) : supprimée de l'écran d'accueil. */
    private suspend fun removeOptional(key: String) = optionalLock.withLock {
        val id = prefs.getLong(kId(key), -1L)
        val keys = prefs.getStringSet(K_OPTIONAL_KEYS, emptySet()).orEmpty()
        if (id < 0 && key !in keys) return@withLock
        if (!providerAvailable()) return@withLock
        deleteChannel(id)
        prefs.edit().remove(kId(key)).remove(kSig(key)).remove(kName(key)).putStringSet(K_OPTIONAL_KEYS, (keys - key).toSet()).apply()
        report("$key:removed", "$key channel $id removed")
    }

    /** Publie (ou met à jour par différence) une chaîne activable ; même logique que « Nouveautés ». */
    private suspend fun syncOptional(key: String, name: String, appLink: String, items: List<TvProgramItem>) = optionalLock.withLock {
        if (!providerAvailable()) return@withLock
        val helper = PreviewChannelHelper(ctx)
        var channelId = prefs.getLong(kId(key), -1L)
        if (ChannelPublishPlan.mustRecreate(channelId, channelExists(channelId))) {
            report("$key:gone", "$key channel $channelId missing in provider: recreating", warn = true)
            prefs.edit().remove(kId(key)).remove(kSig(key)).apply()
            channelId = -1L
        }
        if (items.isEmpty() && channelId < 0) { report("skip:$key", "$key channel skipped: nothing to show"); return@withLock }
        if (channelId < 0) {
            val logo = ContextCompat.getDrawable(ctx, com.ultratv.tv.nativeapp.R.mipmap.ic_launcher)!!.toBitmap(160, 160)
            val channel = PreviewChannel.Builder().setDisplayName(name).setAppLinkIntentUri(Uri.parse(appLink)).setLogo(logo).build()
            channelId = publishChannel(helper, channel)
            if (channelId < 0) { report("$key:publish", "$key channel publish refused by system (id=$channelId)", warn = true); return@withLock }
            val keys = prefs.getStringSet(K_OPTIONAL_KEYS, emptySet()).orEmpty()
            prefs.edit().putLong(kId(key), channelId).putString(kName(key), name).remove(kSig(key)).putStringSet(K_OPTIONAL_KEYS, (keys + key).toSet()).apply()
            report("$key:created", "$key channel created id=$channelId items=${items.size}")
        } else if (prefs.getString(kName(key), null) != name) {
            runCatching {
                val current = helper.getPreviewChannel(channelId)
                if (current != null) helper.updatePreviewChannel(channelId, PreviewChannel.Builder(current).setDisplayName(name).build())
            }
            prefs.edit().putString(kName(key), name).apply()
        }
        val cr = ctx.contentResolver
        val existing = HashMap<String, Long>()
        cr.query(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), PreviewProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = PreviewProgram.fromCursor(c)
                p.internalProviderId?.let { existing[it] = p.id }
            }
        }
        val signature = TvChannelPlan.signature(items)
        if (ChannelPublishPlan.mustRewrite(prefs.getString(kSig(key), null) == signature, existing.size, items.size)) {
            fun program(i: TvProgramItem, index: Int) = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(i.type)
                .setTitle(i.title)
                .apply { i.description?.let { setDescription(it) } }
                .setPosterArtUri(Uri.parse(i.image))
                .setPosterArtAspectRatio(i.aspectRatio)
                .setLive(i.live)
                .setWeight(NewsChannelPlan.weight(index, items.size))
                .setInternalProviderId(i.internalId)
                .setIntentUri(Uri.parse(i.link))
                .build()
            val diff = TvChannelPlan.diff(existing, items)
            var failed = 0
            diff.deleteRowIds.forEach { cr.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
            val updates = diff.update.associate { it.second.internalId to it.first }
            items.forEachIndexed { index, i ->
                val rowId = updates[i.internalId]
                if (rowId != null) cr.update(TvContractCompat.buildPreviewProgramUri(rowId), program(i, index).toContentValues(), null, null)
                else if (helper.publishPreviewProgram(program(i, index)) < 0) failed++
            }
            if (failed == 0) prefs.edit().putString(kSig(key), signature).apply()
            report("$key:programs", "$key channel id=$channelId programs=${items.size} (+${diff.insert.size} -${diff.deleteRowIds.size}) failed=$failed", warn = failed > 0)
        }
        askBrowsableIfPending(key, channelId)
    }

    private companion object {
        const val K_ASK_BROWSABLE = "ask_browsable_keys"
        const val K_OPTIONAL_KEYS = "optional_channel_keys"
        const val K_NEWS_CHANNEL = "news_channel_id"
        const val K_NEWS_SIGNATURE = "news_signature"
        const val K_NEWS_NAME = "news_name"
        const val K_DEFAULT_DONE = "default_channel_published"
        const val K_BROWSABLE_ASKED = "news_browsable_asked"
        const val K_BROWSABLE_NA_AT = "news_browsable_unavailable_reported_at"
        const val K_PROVIDER_NA_AT = "provider_unavailable_reported_at"
        const val K_RECENT_CHANNEL = "recent_channel_id"
        const val K_RECENT_SIGNATURE = "recent_signature"
        const val K_RECENT_NAME = "recent_name"
        const val K_CHANNEL = "favorites_channel_id"
        const val K_SIGNATURE = "favorites_signature"
    }
}

/** Le lanceur Android TV demande aux applications de publier leurs programmes (premier lancement, chaîne approuvée). */
@AndroidEntryPoint
class TvInitializeReceiver : BroadcastReceiver() {
    @Inject lateinit var sync: GoogleTvSync

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        scope.launch { try { sync.syncOnce() } finally { pending.finish(); scope.cancel() } }
    }
}
