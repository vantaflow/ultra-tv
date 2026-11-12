package com.ultratv.tv.nativeapp.data.extmeta

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.DeviceTokenStore
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Réponse brute d'un proxy de métadonnées du Worker : code HTTP et corps. */
data class ExtResponse(val code: Int, val body: String?)

/**
 * Proxys de métadonnées complémentaires du Worker (Fanart.tv, TVmaze, TheSportsDB) : même jeton d'appareil, même adresse
 * (et même repli d'hôte) que le proxy TMDB. Interface : les dépôts se testent sans réseau.
 */
interface ExtMetaApi {
    /** Faux tant que l'appareil n'est pas appairé au Worker : aucune requête n'est alors émise. */
    fun isEnabled(): Boolean

    /** GET `/api/[pathAndQuery]` ; null si non appairé ou adresse invalide ; IOException si le réseau échoue. */
    suspend fun get(pathAndQuery: String): ExtResponse?
}

/** Client HTTP : jeton d'appareil en Bearer, pas de redirection suivie (comme [com.ultratv.tv.nativeapp.data.tmdb.TmdbHttpApi]). */
@Singleton
class ExtMetaHttpApi @Inject constructor(
    okHttp: OkHttpClient,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
) : ExtMetaApi {
    private val http = okHttp.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override fun isEnabled() = tokens.isPaired

    override suspend fun get(pathAndQuery: String): ExtResponse? = withContext(Dispatchers.IO) {
        val token = tokens.token() ?: return@withContext null
        val base = WorkerUrl.normalize(WorkerUrl.resolve(prefs.flow.first().workerBaseUrl), BuildConfig.DEBUG) ?: return@withContext null
        val req = Request.Builder().url("$base/api/$pathAndQuery").header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { r -> ExtResponse(r.code, if (r.isSuccessful) r.body?.string() else null) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtMetaModule {
    @Binds abstract fun api(impl: ExtMetaHttpApi): ExtMetaApi
}

/** Résultat à ne PAS mémoriser (réseau, service indisponible, réponse inattendue) : on retentera plus tard. */
class SkipCache : java.io.IOException("skip cache")

/**
 * Cache mémoire LRU à durée de vie, réponses « rien » comprises (valeur nulle), avec un verrou par clé : deux demandes
 * simultanées du même titre ne font qu'UN appel. Une [java.io.IOException] (dont [SkipCache]) n'est pas mémorisée.
 */
class MemoCache<V : Any>(private val max: Int, private val ttlMs: Long) {
    class Hit<V>(val value: V?)
    private class Entry<V>(val at: Long, val value: V?)

    private val map = object : LinkedHashMap<String, Entry<V>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<V>>?) = size > max
    }
    private val locks = HashMap<String, kotlinx.coroutines.sync.Mutex>()

    fun peek(key: String, nowMs: Long): Hit<V>? = synchronized(map) {
        val e = map[key] ?: return null
        if (nowMs - e.at !in 0 until ttlMs) { map.remove(key); return null }
        Hit(e.value)
    }

    fun put(key: String, nowMs: Long, value: V?) { synchronized(map) { map[key] = Entry(nowMs, value) } }

    suspend fun getOrLoad(key: String, nowMs: Long, load: suspend () -> V?): V? {
        peek(key, nowMs)?.let { return it.value }
        val m = synchronized(locks) { locks.getOrPut(key) { kotlinx.coroutines.sync.Mutex() } }
        try {
            m.lock()
            try {
                peek(key, nowMs)?.let { return it.value }
                val v = try { load() } catch (e: java.io.IOException) { return null }
                put(key, nowMs, v)
                return v
            } finally { m.unlock() }
        } finally {
            synchronized(locks) { if (!m.isLocked) locks.remove(key) }
        }
    }
}
