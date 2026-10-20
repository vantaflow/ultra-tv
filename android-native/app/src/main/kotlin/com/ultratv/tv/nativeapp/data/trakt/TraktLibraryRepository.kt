package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.CloudSyncClient
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.db.CatalogLite
import com.ultratv.tv.nativeapp.data.db.EpisodeDao
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Transport vers le Worker (substituable dans les tests). */
interface TraktLibrarySource {
    val isPaired: Boolean
    /** Corps JSON de la bibliothèque, ou null pour tout échec (réseau, 401, 429, 5xx). */
    suspend fun fetch(lang: String): String?
}

@Singleton
class WorkerTraktLibrarySource @Inject constructor(
    private val client: CloudSyncClient,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
) : TraktLibrarySource {
    override val isPaired get() = tokens.isPaired

    override suspend fun fetch(lang: String): String? {
        val token = tokens.token() ?: return null
        val base = WorkerUrl.normalize(prefs.flow.first().workerBaseUrl.ifBlank { BuildConfig.WORKER_URL }, BuildConfig.DEBUG) ?: return null
        return client.traktLibrary(base, token, lang)
    }
}

/**
 * Bibliothèque Trakt en mémoire. Le Worker met la réponse en cache 15 min et limite à 30 requêtes / 10 min / appareil :
 * on ne la relit donc qu'au plus toutes les 15 min (ou si la langue change), et après un échec pas avant une minute.
 * Tout échec est ignoré en silence (la dernière valeur valide est conservée) ; un appareil non appairé ou un compte
 * non lié donne [TraktLibrary.EMPTY] : aucune rangée Trakt n'est alors affichée.
 */
@Singleton
class TraktLibraryRepository(
    private val source: TraktLibrarySource,
    private val store: TraktStore = NoTraktStore,
    private val clock: () -> Long,
) {
    @Inject constructor(source: WorkerTraktLibrarySource, store: FileTraktStore) : this(source, store, System::currentTimeMillis)

    private val _library = MutableStateFlow(TraktLibrary.EMPTY)
    val library: StateFlow<TraktLibrary> = _library.asStateFlow()

    private val mutex = Mutex()
    private var lastOkAt = 0L
    private var lastFailAt = 0L
    private var lastLang: String? = null
    private var restored = false

    /**
     * Recharge la dernière bibliothèque enregistrée sur disque (une fois) : les rangées peuvent ainsi être calculées
     * tout de suite au lancement, sans attendre le réseau. L'âge du fichier compte pour la règle des 15 min. Sans effet
     * si une valeur plus récente est déjà là, si l'appareil n'est pas appairé ou si le fichier est illisible.
     */
    suspend fun restore() {
        if (restored || !source.isPaired) return
        mutex.withLock {
            if (restored) return
            restored = true
            if (_library.value.linked) return
            val env = withContext(Dispatchers.IO) { store.readLibrary() } ?: return
            try {
                val o = JSONObject(env)
                val lib = TraktLibrary.parse(o.getString("body"))
                if (!lib.linked) return
                _library.value = lib
                lastOkAt = o.optLong("savedAt", 0L).takeIf { it in 1..clock() } ?: 0L
                lastLang = o.optString("lang", "").takeIf { it.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) { /* fichier abîmé : ignoré, le réseau reprendra */ }
        }
    }

    /** Relit la bibliothèque si elle a plus de 15 min (ou [force]). Ne lève jamais (hors annulation). */
    suspend fun refreshIfStale(lang: String, force: Boolean = false) {
        if (!source.isPaired) {
            // Appareil désappairé : on oublie le compte précédent.
            if (_library.value !== TraktLibrary.EMPTY) _library.value = TraktLibrary.EMPTY
            lastOkAt = 0L; lastLang = null; restored = false
            withContext(Dispatchers.IO) { store.clear() }
            return
        }
        restore()
        mutex.withLock {
            val now = clock()
            if (!force) {
                if (lastOkAt > 0 && lang == lastLang && now - lastOkAt < REFRESH_MS) return
                if (lastFailAt > lastOkAt && now - lastFailAt < RETRY_MS) return
            }
            try {
                val body = source.fetch(lang)
                val lib = body?.let { TraktLibrary.parse(it) }
                if (lib == null) { lastFailAt = now; return }
                _library.value = lib
                lastOkAt = now; lastLang = lang
                withContext(Dispatchers.IO) {
                    if (lib.linked) store.writeLibrary(JSONObject().put("savedAt", now).put("lang", lang).put("body", body).toString())
                    else store.clear()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                lastFailAt = now   // JSON illisible : on garde la dernière valeur valide
            }
        }
    }

    /**
     * Correction locale immédiate après une écriture réussie (watchlist, vu, note) : l'interface suit sans attendre la
     * relecture du Worker. Sans effet si le compte n'est pas (ou plus) lié.
     */
    fun patch(f: (TraktLibrary) -> TraktLibrary) {
        while (true) {
            val cur = _library.value
            if (!cur.linked) return
            if (_library.compareAndSet(cur, f(cur))) return
        }
    }

    companion object {
        const val REFRESH_MS = 15 * 60_000L
        const val RETRY_MS = 60_000L
        private val SUPPORTED = setOf("fr", "en", "es", "ar")

        /** Paramètre `lang` du Worker : langue de l'appli (« system » = celle de l'appareil), repli « en ». */
        fun langParam(appLanguage: String, systemLanguage: String): String {
            val code = if (appLanguage == "system" || appLanguage.isBlank()) systemLanguage else appLanguage
            return code.lowercase(java.util.Locale.ROOT).takeIf { it in SUPPORTED } ?: "en"
        }
    }
}

/**
 * Rangées Trakt de l'accueil : seulement les éléments DISPONIBLES dans la source active. Le catalogue (jusqu'à ~50 000
 * titres) est parcouru par tranches sur un thread de calcul ; seules les entrées dont la clé est demandée par Trakt sont
 * gardées. Le résultat est mémorisé tant que ni le catalogue (nombre de films / séries) ni la bibliothèque ne changent.
 *
 * Le même parcours sert à « À suivre » (prochain épisode disponible des séries regardées) et à l'import des reprises
 * Trakt (/sync/playback) dans l'historique local ([TraktProgressImporter]).
 */
@Singleton
class TraktCatalogMatcher(
    private val movies: MovieDao,
    private val series: SeriesDao,
    private val repo: TraktLibraryRepository,
    private val store: TraktStore,
    /** Change quand une synchro du catalogue se TERMINE (films + séries) ; null = source inconnue. */
    private val catalogStamp: (Long) -> Flow<Long?>,
    /** Épisodes connus d'une série (base locale) ; vide si la fiche n'a jamais été chargée. */
    private val episodesOf: suspend (seriesId: Long) -> List<EpisodeEntity> = { emptyList() },
    /** Charge les épisodes d'une série auprès de la source (get_series_info). */
    private val loadEpisodes: suspend (seriesId: Long) -> Unit = {},
    /** Reprises Trakt rapprochées du catalogue, à importer dans l'historique. */
    private val importer: suspend (List<PlaybackHit>) -> Unit = {},
    private val prefs: Flow<TraktPrefs> = flowOf(TraktPrefs()),
) {
    @Inject constructor(
        movies: MovieDao, series: SeriesDao, repo: TraktLibraryRepository, store: FileTraktStore,
        providers: com.ultratv.tv.nativeapp.data.db.ProviderDao, episodes: EpisodeDao,
        catalog: dagger.Lazy<com.ultratv.tv.nativeapp.data.repo.CatalogRepository>,
        importer: TraktProgressImporter, prefs: TraktPrefsStore,
    ) : this(
        movies, series, repo, store as TraktStore, { id -> providers.observeCatalogStamp(id) },
        { sid -> episodes.forSeries(sid) }, { sid -> catalog.get().loadEpisodes(sid) }, { hits -> importer.import(hits) }, prefs.state,
    )

    private data class Sig(val pid: Long, val stamp: Long, val lib: TraktLibrary)
    @Volatile private var cached: Pair<Sig, TraktRows>? = null
    private val computeLock = Mutex()
    // Dernières rangées calculées par source, aussi écrites sur disque : affichées instantanément au prochain lancement.
    private val persisted = java.util.concurrent.ConcurrentHashMap<Long, TraktRows>()
    @Volatile private var persistedLoaded = false
    // Séries dont les épisodes ont déjà été demandés à la source (une fois par lancement : jamais de boucle réseau).
    private val episodesRequested = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Long, Boolean>())

    /**
     * Démarre le calcul dès le lancement de l'appli pour la source active (au lieu d'attendre que l'accueil s'abonne) :
     * le résultat est mémorisé et l'accueil l'affiche aussitôt. Sans effet tant que la bibliothèque n'est pas liée.
     */
    fun warmUp(scope: CoroutineScope, pid: Flow<Long?>) {
        scope.launch { rows(pid).collect { } }
    }

    /** Rangées pour l'accueil : listes personnelles masquées dans les réglages retirées, au plus 5. */
    fun rows(pid: Flow<Long?>): Flow<TraktRows> =
        combine(allRows(pid), prefs.distinctUntilChanged()) { r, p -> visibleLists(r, p) }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun allRows(pid: Flow<Long?>): Flow<TraktRows> = flow {
        repo.restore()
        emitAll(
            combine(pid.distinctUntilChanged(), repo.library) { id, lib -> id to lib }.flatMapLatest { (id, lib) ->
                if (id == null || !lib.linked || lib.isNoList) {
                    if (!lib.linked) forget()
                    flowOf(TraktRows.EMPTY)
                } else {
                    // Recalcul seulement à la FIN d'une synchro du catalogue (horodatage), plus à chaque tranche insérée :
                    // pendant une synchro de 50 000 films, le parcours complet toutes les 3 s saturait les box modestes.
                    val stamps = catalogStamp(id).map { it ?: 0L }.distinctUntilChanged()
                    flow {
                        // Affichage immédiat : dernier calcul en mémoire, sinon celui du lancement précédent (sur disque).
                        instant(id)?.let { emit(it) }
                        var first = true
                        emitAll(stamps.transformLatest { st ->
                            if (first) first = false else delay(SETTLE_MS)
                            emit(compute(Sig(id, st, lib)))
                        })
                    }
                }
            },
        )
    }.distinctUntilChanged().flowOn(MATCH_DISPATCHER)

    private val TraktLibrary.isNoList get() = watchlist.isEmpty() && recommendations.isEmpty() && trending.isEmpty() && popular.isEmpty() &&
        lists.isEmpty() && playback.isEmpty() && watchedShows.isEmpty()

    /** Compte délié / appareil désappairé : aucune rangée de l'ancien compte ne doit réapparaître. */
    private suspend fun forget() {
        if (cached == null && persisted.isEmpty()) return
        cached = null
        persisted.clear()
        withContext(Dispatchers.IO) { store.writeRows("{}") }
    }

    private suspend fun instant(pid: Long): TraktRows? {
        cached?.let { (s, r) -> if (s.pid == pid) return r }
        if (!persistedLoaded) {
            val saved = withContext(Dispatchers.IO) { TraktRowsCodec.decode(store.readRows()) }
            persisted.putAll(saved)
            persistedLoaded = true
        }
        return persisted[pid]?.takeIf { !it.isEmpty }
    }

    private suspend fun compute(sig: Sig): TraktRows = computeLock.withLock {
        cached?.let { (s, r) -> if (s == sig) return r }
        val lib = sig.lib
        val candidates = TraktUpNext.candidates(lib)
        val lists = listOf(lib.watchlist, lib.recommendations, lib.trending, lib.popular) + lib.lists.map { it.items } +
            listOf(lib.playback.map { it.item }, candidates.map { it.item })
        val wantedMovies = TraktAvailability.wantedKeys(lists, shows = false)
        val wantedSeries = TraktAvailability.wantedKeys(lists, shows = true)
        val movieIdx = HashMap<String, MutableList<CatalogLite>>()
        val seriesIdx = HashMap<String, MutableList<CatalogLite>>()
        // L'un après l'autre, sur un seul fil de basse priorité : l'interface et la lecture passent toujours avant.
        if (wantedMovies.isNotEmpty()) scan(movieIdx, wantedMovies) { after -> movies.liteChunk(sig.pid, after, CHUNK) }
        if (wantedSeries.isNotEmpty()) scan(seriesIdx, wantedSeries) { after -> series.liteChunk(sig.pid, after, CHUNK) }
        val budget = intArrayOf(MAX_EPISODE_LOADS)
        val upNext = upNext(candidates, seriesIdx, budget)
        val rows = TraktAvailability.rows(lib, movieIdx, seriesIdx).copy(upNext = upNext)
        // Reprises Trakt → historique local (jamais au-dessus d'une position locale plus récente).
        runCatching { importer(playbackHits(sig.pid, lib.playback, movieIdx, seriesIdx, budget)) }
        cached = sig to rows
        if (sig.stamp > 0 && persisted[sig.pid] != rows) {
            persisted[sig.pid] = rows
            val snapshot = TraktRowsCodec.encode(persisted)
            withContext(Dispatchers.IO) { store.writeRows(snapshot) }
        }
        rows
    }

    /** Épisodes d'une série du catalogue ; demandés à la source une seule fois par lancement s'ils manquent (budget borné). */
    private suspend fun episodes(seriesId: Long, budget: IntArray): List<EpisodeEntity> {
        val known = runCatching { episodesOf(seriesId) }.getOrDefault(emptyList())
        if (known.isNotEmpty() || budget[0] <= 0 || !episodesRequested.add(seriesId)) return known
        budget[0]--
        runCatching { loadEpisodes(seriesId) }
        return runCatching { episodesOf(seriesId) }.getOrDefault(emptyList())
    }

    private suspend fun upNext(candidates: List<TraktWatchedShow>, seriesIdx: Map<String, List<CatalogLite>>, budget: IntArray): List<TraktUpNextCard> {
        val out = ArrayList<TraktUpNextCard>()
        val seen = HashSet<Long>()
        for (show in candidates) {
            if (out.size >= TraktUpNext.MAX_CARDS) break
            val card = TraktAvailability.resolve(listOf(show.item), emptyMap(), seriesIdx).firstOrNull() ?: continue
            if (!seen.add(card.id)) continue
            val eps = episodes(card.id, budget).map { EpisodeRef(it.season, it.episode) }
            val next = TraktUpNext.nextEpisode(show.episodes, eps) ?: continue
            out += TraktUpNextCard(card, next.season, next.episode)
        }
        return out
    }

    private suspend fun playbackHits(
        pid: Long, playback: List<TraktPlayback>, movieIdx: Map<String, List<CatalogLite>>, seriesIdx: Map<String, List<CatalogLite>>, budget: IntArray,
    ): List<PlaybackHit> {
        val out = ArrayList<PlaybackHit>()
        for (p in playback) {
            val card = TraktAvailability.resolve(listOf(p.item), movieIdx, seriesIdx).firstOrNull() ?: continue
            val runtimeMs = (p.runtimeMin ?: 0) * 60_000L
            if (!p.item.isShow) {
                val m = movies.byId(card.id) ?: continue
                out += PlaybackHit(pid, "MOVIE", m.remoteId, m.name, m.poster, m.streamUrl, null, p.progress, p.pausedAt,
                    runtimeMs.takeIf { it > 0 } ?: TraktResume.parseDurationMs(m.duration))
            } else {
                val s = series.byId(card.id) ?: continue
                val ep = episodes(card.id, budget).firstOrNull { it.season == p.season && it.episode == p.episode } ?: continue
                val tag = "S${"%02d".format(ep.season)}E${"%02d".format(ep.episode)}"
                out += PlaybackHit(pid, "EPISODE", ep.remoteId, "${s.name} · $tag · ${ep.title}", ep.image ?: s.poster ?: s.backdrop, ep.streamUrl, s.remoteId,
                    p.progress, p.pausedAt, runtimeMs.takeIf { it > 0 } ?: TraktResume.parseDurationMs(ep.duration))
            }
        }
        return out
    }

    private suspend fun scan(index: MutableMap<String, MutableList<CatalogLite>>, wanted: Set<String>, chunk: suspend (afterId: Long) -> List<CatalogLite>) {
        var after = 0L
        val firstWords = TraktAvailability.firstWordsOf(wanted)
        while (true) {
            val page = chunk(after)
            if (page.isEmpty()) break
            TraktAvailability.indexInto(index, wanted, page, firstWords)
            after = page.last().id
            if (page.size < CHUNK) break
            yield()   // laisse la main entre deux tranches : jamais de longue occupation du processeur
        }
    }

    companion object {
        private const val CHUNK = 2_000
        private const val SETTLE_MS = 3_000L
        /** Fiches de séries demandées à la source par calcul (« À suivre », reprises d'épisodes). */
        private const val MAX_EPISODE_LOADS = 3

        /** Un seul fil, priorité minimale : le rapprochement ne prend que le processeur laissé libre. */
        private val MATCH_DISPATCHER: kotlinx.coroutines.CoroutineDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "trakt-match").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }.asCoroutineDispatcher()

        /** Listes personnelles visibles (réglages) : masquées retirées, au plus [TraktPrefs.MAX_LIST_ROWS]. */
        fun visibleLists(r: TraktRows, p: TraktPrefs): TraktRows {
            val shown = r.lists.filter { it.id !in p.hiddenLists && it.cards.isNotEmpty() }.take(TraktPrefs.MAX_LIST_ROWS)
            return if (shown == r.lists) r else r.copy(lists = shown)
        }
    }
}

/**
 * Import des reprises Trakt dans l'historique local du profil courant : seulement là où l'historique n'a rien de plus
 * récent, et une seule fois par pause (une carte retirée par l'utilisateur ne revient pas au calcul suivant).
 */
@Singleton
class TraktProgressImporter @Inject constructor(
    private val history: com.ultratv.tv.nativeapp.data.db.WatchHistoryDao,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
    private val store: FileTraktStore,
) {
    private val lock = Mutex()

    suspend fun import(hits: List<PlaybackHit>) {
        if (hits.isEmpty()) return
        lock.withLock {
            withContext(Dispatchers.IO) {
                val done = decodeKeys(store.readImported())
                val profile = profiles.currentIdNow
                val now = System.currentTimeMillis()
                var changed = false
                for (h in hits) {
                    val key = "$profile|${h.importKey}"
                    if (key in done) continue
                    val row = TraktResume.decide(history.get(profile, h.providerId, h.kind, h.remoteId), h, profile, now)
                    if (row != null) history.upsert(row)
                    done += key
                    changed = true
                }
                if (changed) store.writeImported(encodeKeys(done))
            }
        }
    }

    companion object {
        private const val MAX_KEYS = 400

        fun decodeKeys(json: String?): LinkedHashSet<String> {
            val out = LinkedHashSet<String>()
            if (json.isNullOrBlank()) return out
            runCatching { val a = org.json.JSONArray(json); for (i in 0 until a.length()) a.optString(i, "").takeIf { it.isNotEmpty() }?.let(out::add) }
            return out
        }

        /** Les plus anciennes clés partent en premier au-delà de [MAX_KEYS]. */
        fun encodeKeys(keys: Collection<String>): String = org.json.JSONArray(keys.toList().takeLast(MAX_KEYS)).toString()
    }
}
