package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.CloudSyncClient
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.db.EpisodeDao
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.tmdb.TmdbDao
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Identité Trakt d'un film ou d'un épisode (titre de la SÉRIE pour un épisode, tmdb de la série). */
data class TraktIdentity(
    val kind: String,          // "movie" | "episode"
    val title: String,
    val year: Int? = null,
    val tmdb: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/** Une requête de scrobble : `action` = start | pause | stop, `progress` en pourcentage 0..100. */
data class ScrobbleRequest(val action: String, val progress: Double, val identity: TraktIdentity)

/** Issue d'un envoi : le compte est-il lié à Trakt ? (`null` = inconnu : échec réseau, 401, 429…). */
enum class ScrobbleResult { LINKED, NOT_LINKED, FAILED }

/** Transport vers le Worker (substituable dans les tests). */
interface TraktTransport {
    val isPaired: Boolean
    suspend fun send(req: ScrobbleRequest): ScrobbleResult
}

/** Retrouve l'identité d'un élément lu (base locale + cache TMDB). null = élément non scrobblable. */
interface TraktIdentityResolver {
    suspend fun resolve(item: PlaybackContext.Item): TraktIdentity?
}

/**
 * Booléen coûteux à lire (Keystore, disque) exposé SANS jamais bloquer l'appelant : `get()` renvoie la dernière valeur
 * connue (`false` tant que rien n'a été lu) et relance une lecture en arrière-plan quand elle a plus de [ttlMs].
 * Avant : le tick du lecteur (fil principal, toutes les 500 ms) déchiffrait le jeton par le Keystore à chaque appel.
 */
class CachedFlag(
    private val ttlMs: Long,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val load: () -> Boolean,
) {
    @Volatile private var value = false
    @Volatile private var loadedAt = -1L
    private val refreshing = java.util.concurrent.atomic.AtomicBoolean(false)

    fun get(): Boolean {
        if ((loadedAt < 0 || clock() - loadedAt >= ttlMs) && refreshing.compareAndSet(false, true)) {
            scope.launch {
                try { value = runCatching(load).getOrDefault(false); loadedAt = clock() } finally { refreshing.set(false) }
            }
        }
        return value
    }
}

@Singleton
class WorkerTraktTransport @Inject constructor(
    private val client: CloudSyncClient,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
) : TraktTransport {
    private val paired = CachedFlag(ttlMs = 30_000, scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { tokens.isPaired }

    /** Lue depuis le fil principal (tick du lecteur) : valeur en cache, jamais d'accès Keystore ici. */
    override val isPaired get() = paired.get()

    override suspend fun send(req: ScrobbleRequest): ScrobbleResult {
        val token = tokens.token() ?: return ScrobbleResult.FAILED
        val base = WorkerUrl.normalize(prefs.flow.first().workerBaseUrl.ifBlank { BuildConfig.WORKER_URL }, BuildConfig.DEBUG)
            ?: return ScrobbleResult.FAILED
        return client.traktScrobble(base, token, req)
    }
}

@Singleton
class DbTraktIdentityResolver @Inject constructor(
    private val movies: MovieDao,
    private val series: SeriesDao,
    private val episodes: EpisodeDao,
    private val tmdb: TmdbDao,
) : TraktIdentityResolver {
    override suspend fun resolve(item: PlaybackContext.Item): TraktIdentity? = when (item.kind) {
        "MOVIE" -> {
            val m = movies.byRemoteId(item.providerId, item.remoteId)
            TraktIdentity(
                kind = "movie", title = (m?.title ?: item.title).ifBlank { item.title }, year = m?.year,
                tmdb = tmdb.get(TmdbKind.MOVIE.name, item.providerId, item.remoteId)?.tmdbId,
            )
        }
        "EPISODE" -> {
            val ep = episodes.byRemoteId(item.providerId, item.remoteId)
            val parent = item.parentRemoteId
            val s = parent?.let { series.byRemoteId(item.providerId, it) }
            if (ep == null || s == null || s.title.isBlank()) null
            else TraktIdentity(
                kind = "episode", title = s.title, year = s.year, season = ep.season, episode = ep.episode,
                tmdb = tmdb.get(TmdbKind.TV.name, item.providerId, s.remoteId)?.tmdbId,
            )
        }
        else -> null
    }
}

/**
 * Scrobbling Trakt via le Worker. Logique d'états pure (appelée depuis le thread UI, jamais bloquante) :
 * `start` à la première lecture et à la reprise, `pause` en pause, `stop` en fin / sortie / épisode suivant.
 * Les requêtes partent dans l'ordre, une seule à la fois, sur une portée applicative (un `stop` envoyé à la
 * fermeture du lecteur n'est donc pas annulé). Les échecs réseau sont ignorés.
 */
@Singleton
class TraktScrobbler(
    private val transport: TraktTransport,
    private val resolver: TraktIdentityResolver,
    dispatcher: CoroutineDispatcher,
    /** Un arrêt de lecture (non-lecture) n'est signalé en `pause` qu'après ce délai : évite le bruit du buffering. */
    private val pauseDelayMs: Long = 3_000,
) {
    @Inject constructor(transport: WorkerTraktTransport, resolver: DbTraktIdentityResolver) :
        this(transport, resolver, Dispatchers.IO)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Channel<Pending>(Channel.UNLIMITED)
    private val lock = Any()

    private class Pending(val key: String, val item: PlaybackContext.Item, val action: String, val progress: Double)

    // État (protégé par lock).
    private var key: String? = null
    private var lastAction: String? = null
    private var notPlayingSince = -1L
    private var skip = false              // élément ignoré (LIVE, durée inconnue, non appairé, compte non lié)

    // État du consommateur (même verrou) : identité résolue une fois par élément, et clé « compte non lié ».
    private var resolvedKey: String? = null
    private var resolvedIdentity: TraktIdentity? = null
    private var unlinkedKey: String? = null

    init {
        scope.launch { for (p in queue) process(p) }
    }

    private fun keyOf(i: PlaybackContext.Item) = "${i.providerId}|${i.kind}|${i.remoteId}"

    /** Appelé à chaque tick de lecture (≈ 500 ms) : détecte les passages lecture / pause. */
    fun onTick(item: PlaybackContext.Item?, playing: Boolean, positionMs: Long, durationMs: Long, nowMs: Long = System.currentTimeMillis()) {
        emit(item, playing, positionMs, durationMs, nowMs, immediate = false, stop = false)
    }

    /** Pause immédiate (application passée en arrière-plan). */
    fun onPause(item: PlaybackContext.Item?, positionMs: Long, durationMs: Long) {
        emit(item, false, positionMs, durationMs, 0L, immediate = true, stop = false)
    }

    /** Fin de lecture : épisode terminé, fermeture du lecteur ou passage à l'épisode suivant. */
    fun onStop(item: PlaybackContext.Item?, positionMs: Long, durationMs: Long) {
        emit(item, false, positionMs, durationMs, 0L, immediate = true, stop = true)
    }

    private fun emit(item: PlaybackContext.Item?, playing: Boolean, pos: Long, dur: Long, now: Long, immediate: Boolean, stop: Boolean) {
        if (item == null) return
        val send: Pending? = synchronized(lock) {
            val k = keyOf(item)
            if (k != key) { key = k; lastAction = null; notPlayingSince = -1L; skip = false }   // nouvel élément : on repart à neuf
            if (skip) return@synchronized null
            if (item.kind != "MOVIE" && item.kind != "EPISODE") { skip = true; return@synchronized null }
            if (dur <= 0 || !transport.isPaired) return@synchronized null   // durée pas encore connue : on réessaie au tick suivant
            val progress = (pos.toDouble() / dur * 100).coerceIn(0.0, 100.0)
            val action = when {
                stop -> if (lastAction == null || lastAction == "stop") null else "stop"
                playing -> { notPlayingSince = -1L; if (lastAction == "start") null else "start" }
                lastAction != "start" -> null
                immediate -> "pause"
                else -> {
                    if (notPlayingSince < 0) notPlayingSince = now
                    if (now - notPlayingSince >= pauseDelayMs) "pause" else null
                }
            } ?: return@synchronized null
            lastAction = action
            if (action != "start") notPlayingSince = -1L
            Pending(k, item, action, progress)
        }
        if (send != null) queue.trySend(send)
    }

    private suspend fun process(p: Pending) {
        try {
            val (known, cached) = synchronized(lock) { (resolvedKey == p.key) to resolvedIdentity }
            if (synchronized(lock) { unlinkedKey == p.key }) return
            val identity = if (known) cached else resolveOnce(p)
            identity ?: return
            if (transport.send(ScrobbleRequest(p.action, p.progress, identity)) == ScrobbleResult.NOT_LINKED) {
                synchronized(lock) { unlinkedKey = p.key }   // compte non lié : inutile d'insister pour cet élément
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Réseau / base : le scrobble est un confort, jamais une erreur visible.
        }
    }

    private suspend fun resolveOnce(p: Pending): TraktIdentity? {
        val id = resolver.resolve(p.item)
        synchronized(lock) { resolvedKey = p.key; resolvedIdentity = id }
        return id
    }
}
