package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// ───────────────────────── Réglages Trakt de l'appareil ─────────────────────────

/**
 * Réglages Trakt locaux : [autoWatched] = « Marquer vu automatiquement à la fin » (le scrobble `stop` au-delà de 80 %
 * marque vu chez Trakt ; désactivé, la fin est envoyée en `pause`) ; [hiddenLists] = listes personnelles masquées
 * de l'accueil (par défaut toutes visibles, au plus [MAX_LIST_ROWS] rangées).
 */
data class TraktPrefs(val autoWatched: Boolean = true, val hiddenLists: Set<String> = emptySet()) {
    fun encode(): String = JSONObject().put("autoWatched", autoWatched).put("hiddenLists", JSONArray(hiddenLists.sorted())).toString()

    companion object {
        const val MAX_LIST_ROWS = 5

        /** Jamais d'exception : un fichier illisible donne les valeurs par défaut. */
        fun decode(json: String?): TraktPrefs {
            if (json.isNullOrBlank()) return TraktPrefs()
            return try {
                val o = JSONObject(json)
                val a = o.optJSONArray("hiddenLists")
                val hidden = HashSet<String>()
                if (a != null) for (i in 0 until a.length()) a.optString(i, "").takeIf { it.isNotEmpty() }?.let(hidden::add)
                TraktPrefs(o.optBoolean("autoWatched", true), hidden)
            } catch (_: Exception) { TraktPrefs() }
        }
    }
}

/** Réglages Trakt persistés dans le magasin Trakt (petit fichier JSON), lus une fois au démarrage. */
@Singleton
class TraktPrefsStore(private val store: TraktStore, scope: CoroutineScope, io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO) {
    @Inject constructor(store: FileTraktStore) : this(store, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val _state = MutableStateFlow(TraktPrefs())
    val state: StateFlow<TraktPrefs> = _state.asStateFlow()
    private val writeScope = scope

    init {
        scope.launch(io) { _state.value = TraktPrefs.decode(store.readPrefs()) }
    }

    fun setAutoWatched(on: Boolean) = update { it.copy(autoWatched = on) }
    fun setListVisible(id: String, visible: Boolean) = update { it.copy(hiddenLists = if (visible) it.hiddenLists - id else it.hiddenLists + id) }

    private fun update(f: (TraktPrefs) -> TraktPrefs) {
        val next = f(_state.value)
        _state.value = next
        writeScope.launch { store.writePrefs(next.encode()) }
    }
}

// ───────────────────────── Écritures via le Worker ─────────────────────────

/** Issue d'une écriture Trakt. */
enum class TraktWriteResult { OK, NOT_LINKED, UNMATCHED, FAILED }

/** Œuvre du catalogue telle que décrite au Worker (il l'identifie lui-même si [tmdb] manque). */
data class TraktWork(val isShow: Boolean, val title: String, val year: Int?, val tmdb: Int?)

/** Transport des écritures (substituable dans les tests). `path` = « watchlist » | « history » | « rating ». */
interface TraktWriteTransport {
    suspend fun post(path: String, body: JSONObject): TraktWriteResult
}

@Singleton
class WorkerTraktWriteTransport @Inject constructor(
    okHttp: OkHttpClient,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
) : TraktWriteTransport {
    // Aucune redirection suivie (un 30x vers http:// ferait fuiter le jeton d'appareil).
    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = "application/json".toMediaType()

    override suspend fun post(path: String, body: JSONObject): TraktWriteResult = withContext(Dispatchers.IO) {
        try {
            val token = tokens.token() ?: return@withContext TraktWriteResult.NOT_LINKED
            val base = WorkerUrl.normalize(prefs.flow.first().workerBaseUrl.ifBlank { BuildConfig.WORKER_URL }, BuildConfig.DEBUG)
                ?: return@withContext TraktWriteResult.FAILED
            val req = Request.Builder().url("$base/api/device/trakt/$path").header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(json)).build()
            http.newCall(req).execute().use { resp -> parseWriteResponse(resp.code, resp.body?.string()) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            TraktWriteResult.FAILED
        }
    }

    companion object {
        /** Réponse du Worker → résultat (pur, testé). */
        fun parseWriteResponse(code: Int, body: String?): TraktWriteResult {
            if (code !in 200..299) return TraktWriteResult.FAILED
            val o = runCatching { JSONObject(body.orEmpty()) }.getOrNull() ?: return TraktWriteResult.FAILED
            return when {
                !o.optBoolean("linked", false) -> TraktWriteResult.NOT_LINKED
                !o.optBoolean("matched", true) -> TraktWriteResult.UNMATCHED
                o.optBoolean("ok", false) -> TraktWriteResult.OK
                else -> TraktWriteResult.FAILED
            }
        }
    }
}

/**
 * Gestes Trakt depuis les fiches : watchlist, vu / non vu, note. Tout passe par le Worker (aucun jeton Trakt sur
 * l'appareil). Après un succès, la bibliothèque en mémoire est corrigée tout de suite (l'interface suit sans attendre),
 * puis relue auprès du Worker (qui a oublié son cache).
 */
@Singleton
class TraktActions(
    private val transport: TraktWriteTransport,
    private val repo: TraktLibraryRepository,
    private val lang: suspend () -> String,
    private val scope: CoroutineScope,
) {
    @Inject constructor(transport: WorkerTraktWriteTransport, repo: TraktLibraryRepository, prefs: UserPreferencesStore) : this(
        transport, repo,
        { TraktLibraryRepository.langParam(prefs.flow.first().language, java.util.Locale.getDefault().language) },
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    suspend fun setWatchlist(w: TraktWork, add: Boolean): TraktWriteResult =
        write("watchlist", base(w).put("action", if (add) "add" else "remove").put("type", if (w.isShow) "show" else "movie")) {
            it.withWatchlist(w.isShow, w.title, w.year, add)
        }

    suspend fun setMovieWatched(w: TraktWork, watched: Boolean): TraktWriteResult =
        write("history", base(w).put("action", if (watched) "add" else "remove").put("type", "movie")) {
            it.withMovieWatched(w.title, w.year, watched)
        }

    /** Épisodes d'une série (une saison entière ou un seul) marqués vus / non vus. */
    suspend fun setEpisodesWatched(w: TraktWork, episodes: List<EpisodeRef>, watched: Boolean): TraktWriteResult {
        val eps = episodes.distinct().take(MAX_EPISODES)
        if (eps.isEmpty()) return TraktWriteResult.OK
        val arr = JSONArray().also { a -> for (e in eps) a.put(JSONObject().put("season", e.season).put("episode", e.episode)) }
        return write("history", base(w).put("action", if (watched) "add" else "remove").put("type", "episodes").put("episodes", arr)) {
            it.withEpisodes(w.title, w.year, eps.mapTo(HashSet()) { e -> e.key }, watched)
        }
    }

    /** Note 1..10, ou 0 pour la retirer. */
    suspend fun rate(w: TraktWork, rating: Int): TraktWriteResult {
        require(rating in 0..10)
        return write("rating", base(w).put("type", if (w.isShow) "show" else "movie").put("rating", rating)) {
            it.withRating(w.isShow, w.title, w.year, rating)
        }
    }

    private fun base(w: TraktWork) = JSONObject().put("title", w.title).also { o ->
        w.year?.let { o.put("year", it) }
        w.tmdb?.let { o.put("tmdb", it) }
    }

    private suspend fun write(path: String, body: JSONObject, patch: (TraktLibrary) -> TraktLibrary): TraktWriteResult {
        val r = transport.post(path, body)
        if (r == TraktWriteResult.OK) {
            repo.patch(patch)
            scope.launch { runCatching { repo.refreshIfStale(lang(), force = true) } }
        }
        return r
    }

    companion object {
        /** Même borne que le Worker (MAX_HISTORY_EPISODES). */
        const val MAX_EPISODES = 200
    }
}
