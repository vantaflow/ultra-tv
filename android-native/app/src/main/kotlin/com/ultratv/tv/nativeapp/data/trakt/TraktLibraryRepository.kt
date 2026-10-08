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
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
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
    private val clock: () -> Long,
) {
    @Inject constructor(source: WorkerTraktLibrarySource) : this(source, System::currentTimeMillis)

    private val _library = MutableStateFlow(TraktLibrary.EMPTY)
    val library: StateFlow<TraktLibrary> = _library.asStateFlow()

    private val mutex = Mutex()
    private var lastOkAt = 0L
    private var lastFailAt = 0L
    private var lastLang: String? = null

    /** Relit la bibliothèque si elle a plus de 15 min (ou [force]). Ne lève jamais (hors annulation). */
    suspend fun refreshIfStale(lang: String, force: Boolean = false) {
        if (!source.isPaired) {
            // Appareil désappairé : on oublie le compte précédent.
            if (_library.value !== TraktLibrary.EMPTY) _library.value = TraktLibrary.EMPTY
            lastOkAt = 0L; lastLang = null
            return
        }
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
class TraktCatalogMatcher @Inject constructor(
    private val movies: MovieDao,
    private val series: SeriesDao,
    private val repo: TraktLibraryRepository,
) {
    private data class Sig(val pid: Long, val movies: Int, val series: Int, val lib: TraktLibrary)
    @Volatile private var cached: Pair<Sig, TraktRows>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun rows(pid: Flow<Long?>): Flow<TraktRows> =
        combine(pid, repo.library) { id, lib -> id to lib }.flatMapLatest { (id, lib) ->
            if (id == null || !lib.linked || (lib.watchlist.isEmpty() && lib.recommendations.isEmpty())) flowOf(TraktRows.EMPTY)
            else {
                // Pendant une synchro les tables changent sans cesse : première valeur tout de suite, puis au plus un calcul / 3 s.
                val counts = combine(movies.observeCount(id), series.observeCount(id)) { m, s -> m to s }.distinctUntilChanged()
                flow {
                    var first = true
                    emitAll(counts.transformLatest { c ->
                        if (first) first = false else delay(SETTLE_MS)
                        emit(compute(Sig(id, c.first, c.second, lib)))
                    })
                }
            }
        }.distinctUntilChanged().flowOn(Dispatchers.Default)

    private suspend fun compute(sig: Sig): TraktRows {
        cached?.let { (s, r) -> if (s == sig) return r }
        val lib = sig.lib
        val lists = listOf(lib.watchlist, lib.recommendations)
        val movieIdx = HashMap<String, MutableList<CatalogLite>>()
        val seriesIdx = HashMap<String, MutableList<CatalogLite>>()
        val wantedMovies = TraktAvailability.wantedKeys(lists, shows = false)
        val wantedSeries = TraktAvailability.wantedKeys(lists, shows = true)
        if (wantedMovies.isNotEmpty() && sig.movies > 0) scan(movieIdx, wantedMovies) { after -> movies.liteChunk(sig.pid, after, CHUNK) }
        if (wantedSeries.isNotEmpty() && sig.series > 0) scan(seriesIdx, wantedSeries) { after -> series.liteChunk(sig.pid, after, CHUNK) }
        val rows = TraktRows(
            TraktAvailability.resolve(lib.watchlist, movieIdx, seriesIdx),
            TraktAvailability.resolve(lib.recommendations, movieIdx, seriesIdx),
        )
        cached = sig to rows
        return rows
    }

    private suspend fun scan(index: MutableMap<String, MutableList<CatalogLite>>, wanted: Set<String>, chunk: suspend (afterId: Long) -> List<CatalogLite>) {
        var after = 0L
        while (true) {
            val page = chunk(after)
            if (page.isEmpty()) break
            TraktAvailability.indexInto(index, wanted, page)
            after = page.last().id
            if (page.size < CHUNK) break
            yield()   // laisse la main entre deux tranches : jamais de longue occupation du processeur
        }
    }

    private companion object {
        const val CHUNK = 4_000
        const val SETTLE_MS = 3_000L
    }
}
