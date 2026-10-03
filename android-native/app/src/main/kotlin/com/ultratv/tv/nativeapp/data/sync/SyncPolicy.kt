package com.ultratv.tv.nativeapp.data.sync

import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SyncPart

/**
 * Synchro INCRÉMENTALE : chaque partie du catalogue a son horodatage et son TTL. Au lancement
 * on ne retélécharge que ce qui est périmé (ou vide) : une box d'entrée de gamme ne reparse pas
 * 70 Mo de JSON à chaque démarrage.
 */
object SyncPolicy {
    private const val HOUR = 3_600_000L

    data class Ttl(val liveMs: Long, val vodMs: Long, val seriesMs: Long, val epgMs: Long)

    /** [intervalHours] = préférence « Fréquence de synchro » (0 = automatique). */
    fun ttl(intervalHours: Int): Ttl {
        val live = if (intervalHours > 0) intervalHours * HOUR else 6 * HOUR
        return Ttl(
            liveMs = live,
            vodMs = maxOf(24 * HOUR, live),
            seriesMs = maxOf(24 * HOUR, live),
            epgMs = maxOf(12 * HOUR, live),
        )
    }

    /** Ce que la synchro courante doit recharger, dans l'ordre de priorité (le direct d'abord). */
    fun dueParts(
        p: ProviderEntity,
        now: Long,
        ttl: Ttl,
        liveCount: Int,
        force: Boolean,
    ): List<SyncPart> {
        fun stale(last: Long, ttlMs: Long) = last <= 0L || now - last >= ttlMs || now < last
        val parts = mutableListOf<SyncPart>()
        when (p.kind) {
            "M3U_LOCAL" -> return emptyList()
            "M3U" -> if (force || liveCount == 0 || stale(p.lastLiveSyncAt, ttl.liveMs)) parts += SyncPart.LIVE
            "STALKER" -> {
                if (force || liveCount == 0 || stale(p.lastLiveSyncAt, ttl.liveMs)) parts += SyncPart.LIVE
            }
            else -> {
                if (force || liveCount == 0 || stale(p.lastLiveSyncAt, ttl.liveMs)) parts += SyncPart.LIVE
                if (force || stale(p.lastVodSyncAt, ttl.vodMs)) parts += SyncPart.VOD
                if (force || stale(p.lastSeriesSyncAt, ttl.seriesMs)) parts += SyncPart.SERIES
                if (force || stale(p.lastEpgSyncAt, ttl.epgMs)) parts += SyncPart.EPG
            }
        }
        return parts
    }
}
