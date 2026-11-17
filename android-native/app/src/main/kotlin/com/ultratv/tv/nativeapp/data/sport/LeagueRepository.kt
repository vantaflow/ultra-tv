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
 * Scores, calendriers, classements et feuilles de match, lus UNIQUEMENT via le Worker (jamais TheSportsDB en direct) et
 * mémorisés sur l'appareil : direct 60 s, calendrier 1 h, classement 6 h, feuille de match 2 min (1 h une fois terminé).
 * Rien d'identifiant n'est envoyé : une clé de ligue ou un identifiant de match. Après un échec, on ne rappelle pas la même
 * ressource avant [COOLDOWN_MS] (et la dernière valeur connue est servie, marquée « ancienne »).
 */
@Singleton
class LeagueRepository @Inject constructor(private val api: ExtMetaApi) {
    private class Entry(val at: Long, val value: Any)
    private val fresh = HashMap<String, Entry>()
    private val lastGood = HashMap<String, Any>()
    private val failedAt = HashMap<String, Long>()
    private val mutex = Mutex()

    private suspend fun <T : Any> load(key: String, path: String, ttlMs: Long, now: Long, parse: (String?) -> T?): LeagueResult<T> = mutex.withLock {
        if (!api.isEnabled()) return@withLock LeagueResult.NotPaired
        @Suppress("UNCHECKED_CAST")
        fresh[key]?.let { if (now - it.at in 0 until ttlMs) return@withLock LeagueResult.Ok(it.value as T) }
        @Suppress("UNCHECKED_CAST")
        val old = lastGood[key] as T?
        failedAt[key]?.let { if (now - it in 0 until COOLDOWN_MS) return@withLock if (old != null) LeagueResult.Ok(old, true) else LeagueResult.Unavailable }
        val body = try { api.get(path) } catch (e: java.io.IOException) { null }
        val v = if (body?.code == 200) parse(body.body) else null
        if (v == null) {
            failedAt[key] = now
            return@withLock if (old != null) LeagueResult.Ok(old, true) else LeagueResult.Unavailable
        }
        failedAt.remove(key)
        fresh[key] = Entry(now, v)
        lastGood[key] = v
        if (fresh.size > MAX_ENTRIES) { fresh.clear(); lastGood.clear() }
        LeagueResult.Ok(v)
    }

    suspend fun schedule(league: String, now: Long = System.currentTimeMillis()) =
        load("s/$league", "sports/league?id=$league&view=schedule", SCHEDULE_MS, now, LeagueParser::events)

    suspend fun live(league: String, now: Long = System.currentTimeMillis()) =
        load("l/$league", "sports/league?id=$league&view=live", LIVE_MS, now, LeagueParser::events)

    /** Tout le football en cours ou terminé du jour (sélections, qualifications…) : scores des directs hors ligues suivies. */
    suspend fun day(now: Long = System.currentTimeMillis()) =
        load("d/all", "sports/day", LIVE_MS, now, LeagueParser::events)

    suspend fun table(league: String, now: Long = System.currentTimeMillis()) =
        load("t/$league", "sports/league?id=$league&view=table", TABLE_MS, now, LeagueParser::table)

    suspend fun sheet(matchId: String, finished: Boolean, now: Long = System.currentTimeMillis()) =
        // L'identifiant est opaque (« 123 », « fd-123 », « jo-2026-12 »…) : jamais validé comme un nombre, seulement encodé.
        load("m/$matchId", "sports/match?id=${java.net.URLEncoder.encode(matchId, "UTF-8")}",if (finished) SHEET_DONE_MS else SHEET_LIVE_MS, now, LeagueParser::sheet)

    companion object {
        const val LIVE_MS = 60_000L
        const val SCHEDULE_MS = 3_600_000L
        const val TABLE_MS = 6 * 3_600_000L
        const val SHEET_LIVE_MS = 120_000L
        const val SHEET_DONE_MS = 3_600_000L
        const val COOLDOWN_MS = 30_000L
        const val MAX_ENTRIES = 200
    }
}
