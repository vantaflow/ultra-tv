package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.extmeta.ExtMetaApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat d'une lecture des scores : valeur (éventuellement ancienne), service indisponible, ou appareil non appairé. */
sealed interface LeagueResult<out T> {
    data class Ok<T>(val value: T, val stale: Boolean = false) : LeagueResult<T>
    data object Unavailable : LeagueResult<Nothing>
    data object NotPaired : LeagueResult<Nothing>
}

/**
 * Scores, calendriers, classements et feuilles de match, lus via le Worker (jamais TheSportsDB en direct), sauf le football du jour et les feuilles « es-N » lus directement chez ESPN et
 * mémorisés sur l'appareil : direct 60 s, calendrier 1 h, classement 6 h, feuille de match 2 min (1 h une fois terminé).
 * Rien d'identifiant n'est envoyé : une clé de ligue ou un identifiant de match. Après un échec, on ne rappelle pas la même
 * ressource avant [COOLDOWN_MS] (et la dernière valeur connue est servie, marquée « ancienne »).
 */
@Singleton
class LeagueRepository @Inject constructor(private val api: ExtMetaApi, private val espn: EspnApi) {
    /** Sans accès direct à ESPN : tout passe par le Worker (tests). */
    constructor(api: ExtMetaApi) : this(api, EspnApi.None)

    private class Entry(val at: Long, val value: Any)
    private val fresh = HashMap<String, Entry>()
    private val lastGood = HashMap<String, Any>()
    private val failedAt = HashMap<String, Long>()
    private val mutex = Mutex()
    // ESPN direct : dernier échec et nombre d'échecs de suite par ressource (recul 30 s, 60 s, 2 min… jusqu'à 5 min).
    private val espnFailedAt = HashMap<String, Long>()
    private val espnFails = HashMap<String, Int>()

    private suspend fun <T : Any> load(key: String, path: String, ttlMs: Long, now: Long, parse: (String?) -> T?): LeagueResult<T> =
        mutex.withLock { loadLocked(key, path, ttlMs, now, parse) }

    private fun espnBackoffMs(key: String) = minOf(ESPN_BACKOFF_MAX_MS, COOLDOWN_MS shl ((espnFails[key] ?: 1) - 1).coerceIn(0, 4))

    /**
     * Lecture directe d'ESPN (sans Worker, sans jeton) : cache mémoire [ttlMs], au plus une requête par ressource et par
     * [ttlMs], recul après un échec. Null = échec ou recul en cours : l'appelant se replie sur le Worker.
     */
    private suspend fun <T : Any> viaEspn(key: String, path: String, ttlMs: Long, now: Long, parse: (String?) -> T?): LeagueResult<T>? {
        @Suppress("UNCHECKED_CAST")
        fresh[key]?.let { if (now - it.at in 0 until ttlMs) return LeagueResult.Ok(it.value as T) }
        espnFailedAt[key]?.let { if (now - it in 0 until espnBackoffMs(key)) return null }
        val t0 = System.nanoTime()
        var code = -1
        val v = try {
            val r = espn.get(path)
            if (r == null) return null // accès direct indisponible : pas un échec d'ESPN
            code = r.code
            if (r.code == 200) parse(r.body) else null
        } catch (e: java.io.IOException) {
            com.ultratv.tv.nativeapp.UtvLog.w(com.ultratv.tv.nativeapp.UtvLog.SPORT, "$key ESPN: réseau ${com.ultratv.tv.nativeapp.UtvLog.why(e)}")
            null
        }
        com.ultratv.tv.nativeapp.UtvLog.i(com.ultratv.tv.nativeapp.UtvLog.SPORT, "$key ESPN direct: HTTP $code ${com.ultratv.tv.nativeapp.UtvLog.ms(t0)} ms éléments=${(v as? Collection<*>)?.size ?: if (v != null) 1 else 0}")
        if (v == null) {
            espnFailedAt[key] = now
            espnFails[key] = (espnFails[key] ?: 0) + 1
            return null
        }
        espnFailedAt.remove(key); espnFails.remove(key)
        fresh[key] = Entry(now, v)
        lastGood[key] = v
        failedAt.remove(key)
        if (fresh.size > MAX_ENTRIES) { fresh.clear(); lastGood.clear() }
        return LeagueResult.Ok(v)
    }

    private suspend fun <T : Any> loadLocked(key: String, path: String, ttlMs: Long, now: Long, parse: (String?) -> T?): LeagueResult<T> {
        if (!api.isEnabled()) return LeagueResult.NotPaired
        @Suppress("UNCHECKED_CAST")
        fresh[key]?.let { if (now - it.at in 0 until ttlMs) return LeagueResult.Ok(it.value as T) }
        @Suppress("UNCHECKED_CAST")
        val old = lastGood[key] as T?
        failedAt[key]?.let { if (now - it in 0 until COOLDOWN_MS) return if (old != null) LeagueResult.Ok(old, true) else LeagueResult.Unavailable }
        val t0 = System.nanoTime()
        val body = try { api.get(path) } catch (e: java.io.IOException) { com.ultratv.tv.nativeapp.UtvLog.w(com.ultratv.tv.nativeapp.UtvLog.SPORT, "$key: réseau ${com.ultratv.tv.nativeapp.UtvLog.why(e)}"); null }
        val v = if (body?.code == 200) parse(body.body) else null
        if (body != null) com.ultratv.tv.nativeapp.UtvLog.i(com.ultratv.tv.nativeapp.UtvLog.SPORT, "$key: HTTP ${body.code} ${com.ultratv.tv.nativeapp.UtvLog.ms(t0)} ms éléments=${(v as? Collection<*>)?.size ?: if (v != null) 1 else 0}")
        if (body != null && body.code != 200) com.ultratv.tv.nativeapp.UtvLog.w(com.ultratv.tv.nativeapp.UtvLog.SPORT, "$key: HTTP ${body.code}")
        if (v == null) {
            failedAt[key] = now
            return if (old != null) LeagueResult.Ok(old, true) else LeagueResult.Unavailable
        }
        failedAt.remove(key)
        fresh[key] = Entry(now, v)
        lastGood[key] = v
        if (fresh.size > MAX_ENTRIES) { fresh.clear(); lastGood.clear() }
        return LeagueResult.Ok(v)
    }

    suspend fun schedule(league: String, now: Long = System.currentTimeMillis()) =
        load("s/$league", "sports/league?id=$league&view=schedule", SCHEDULE_MS, now, LeagueParser::events)

    suspend fun live(league: String, now: Long = System.currentTimeMillis()) =
        load("l/$league", "sports/league?id=$league&view=live", LIVE_MS, now, LeagueParser::events)

    /** Tout le football en cours ou terminé du jour (sélections, qualifications…) : scores des directs hors ligues suivies. */
    suspend fun day(now: Long = System.currentTimeMillis()): LeagueResult<List<LeagueMatch>> = mutex.withLock {
        // D'abord ESPN en direct depuis l'appareil (le Worker est bloqué par ESPN) ; sinon la route du Worker en secours.
        viaEspn("d/all", "scoreboard", LIVE_MS, now) { EspnParser.day(it, now) }
            ?: loadLocked("d/all", "sports/day", LIVE_MS, now, LeagueParser::events)
    }

    suspend fun table(league: String, now: Long = System.currentTimeMillis()) =
        load("t/$league", "sports/league?id=$league&view=table", TABLE_MS, now, LeagueParser::table)

    suspend fun sheet(matchId: String, finished: Boolean, now: Long = System.currentTimeMillis()): LeagueResult<MatchSheet> = mutex.withLock {
        // L'identifiant est opaque (« 123 », « fd-123 », « jo-2026-12 »…) : jamais validé comme un nombre, seulement encodé.
        val ttl = if (finished) SHEET_DONE_MS else SHEET_LIVE_MS
        val workerPath = "sports/match?id=${java.net.URLEncoder.encode(matchId, "UTF-8")}"
        val espnId = ESPN_ID.matchEntire(matchId)?.groupValues?.get(1)
        // « es-N » : feuille lue en direct chez ESPN depuis l'appareil, le Worker en secours.
        (if (espnId != null) viaEspn("m/$matchId", "summary?event=$espnId", ttl, now, EspnParser::sheet) else null)
            ?: loadLocked("m/$matchId", workerPath, ttl, now, LeagueParser::sheet)
    }

    companion object {
        const val LIVE_MS = 60_000L
        const val SCHEDULE_MS = 3_600_000L
        const val TABLE_MS = 6 * 3_600_000L
        const val SHEET_LIVE_MS = 120_000L
        const val SHEET_DONE_MS = 3_600_000L
        const val COOLDOWN_MS = 30_000L
        const val MAX_ENTRIES = 200
        const val ESPN_BACKOFF_MAX_MS = 5 * 60_000L
        private val ESPN_ID = Regex("^es-([1-9][0-9]{0,9})$")
    }
}
