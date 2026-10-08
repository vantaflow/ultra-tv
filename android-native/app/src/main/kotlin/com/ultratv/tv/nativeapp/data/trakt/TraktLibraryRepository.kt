package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.CloudSyncClient
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.db.CatalogLite
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
 */
@Singleton
class TraktCatalogMatcher(
    private val movies: MovieDao,
    private val series: SeriesDao,
    private val repo: TraktLibraryRepository,
    private val store: TraktStore,
    /** Change quand une synchro du catalogue se TERMINE (films + séries) ; null = source inconnue. */
    private val catalogStamp: (Long) -> Flow<Long?>,
) {
    @Inject constructor(movies: MovieDao, series: SeriesDao, repo: TraktLibraryRepository, store: FileTraktStore, providers: com.ultratv.tv.nativeapp.data.db.ProviderDao) :
        this(movies, series, repo, store as TraktStore, { id -> providers.observeCatalogStamp(id) })

    private data class Sig(val pid: Long, val stamp: Long, val lib: TraktLibrary)
    @Volatile private var cached: Pair<Sig, TraktRows>? = null
    private val computeLock = Mutex()
    // Dernières rangées calculées par source, aussi écrites sur disque : affichées instantanément au prochain lancement.
    private val persisted = java.util.concurrent.ConcurrentHashMap<Long, TraktRows>()
    @Volatile private var persistedLoaded = false

    /**
     * Démarre le calcul dès le lancement de l'appli pour la source active (au lieu d'attendre que l'accueil s'abonne) :
     * le résultat est mémorisé et l'accueil l'affiche aussitôt. Sans effet tant que la bibliothèque n'est pas liée.
     */
    fun warmUp(scope: CoroutineScope, pid: Flow<Long?>) {
        scope.launch { rows(pid).collect { } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun rows(pid: Flow<Long?>): Flow<TraktRows> = flow {
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

    private val TraktLibrary.isNoList get() = watchlist.isEmpty() && recommendations.isEmpty() && trending.isEmpty() && popular.isEmpty()

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
        val lists = listOf(lib.watchlist, lib.recommendations, lib.trending, lib.popular)
        val wantedMovies = TraktAvailability.wantedKeys(lists, shows = false)
        val wantedSeries = TraktAvailability.wantedKeys(lists, shows = true)
        val movieIdx = HashMap<String, MutableList<CatalogLite>>()
        val seriesIdx = HashMap<String, MutableList<CatalogLite>>()
        // L'un après l'autre, sur un seul fil de basse priorité : l'interface et la lecture passent toujours avant.
        if (wantedMovies.isNotEmpty()) scan(movieIdx, wantedMovies) { after -> movies.liteChunk(sig.pid, after, CHUNK) }
        if (wantedSeries.isNotEmpty()) scan(seriesIdx, wantedSeries) { after -> series.liteChunk(sig.pid, after, CHUNK) }
        val rows = TraktAvailability.rows(lib, movieIdx, seriesIdx)
        cached = sig to rows
        if (sig.stamp > 0 && persisted[sig.pid] != rows) {
            persisted[sig.pid] = rows
            val snapshot = TraktRowsCodec.encode(persisted)
            withContext(Dispatchers.IO) { store.writeRows(snapshot) }
        }
        rows
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

    private companion object {
        const val CHUNK = 2_000
        const val SETTLE_MS = 3_000L

        /** Un seul fil, priorité minimale : le rapprochement ne prend que le processeur laissé libre. */
        val MATCH_DISPATCHER: kotlinx.coroutines.CoroutineDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "trakt-match").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }.asCoroutineDispatcher()
    }
}
