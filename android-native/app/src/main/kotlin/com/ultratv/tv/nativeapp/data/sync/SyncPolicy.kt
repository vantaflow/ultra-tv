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
        enabled: Set<SyncPart> = SyncPart.entries.toSet(),
        heavyAllowed: Boolean = true,
    ): List<SyncPart> {
        fun stale(last: Long, ttlMs: Long) = last <= 0L || now - last >= ttlMs || now < last
        val parts = mutableListOf<SyncPart>()
        when (p.kind) {
            "M3U_LOCAL" -> return emptyList()
            "M3U" -> if (force || liveCount == 0 || stale(p.lastLiveSyncAt, ttl.liveMs)) parts += SyncPart.LIVE
            "XTREAM" -> {
                if (force || liveCount == 0 || stale(p.lastLiveSyncAt, ttl.liveMs)) parts += SyncPart.LIVE
                if (force || stale(p.lastVodSyncAt, ttl.vodMs)) parts += SyncPart.VOD
                if (force || stale(p.lastSeriesSyncAt, ttl.seriesMs)) parts += SyncPart.SERIES
                if (force || stale(p.lastEpgSyncAt, ttl.epgMs)) parts += SyncPart.EPG
            }
        }
        // Contenus désactivés par l'utilisateur ; VOD / séries / guide seulement si le réseau le permet (non facturé) sauf demande explicite.
        return parts.filter { it in enabled && (heavyAllowed || force || it == SyncPart.LIVE) }
    }

    /**
     * Guide à recharger au RETOUR sur l'application (sortie de veille comprise) : une box TV garde l'appli en mémoire
     * des jours sans la relancer, et le guide de la source ne couvre que −2 h / +24 h — au-delà, plus aucun programme
     * « en cours ». Rechargé si la dernière synchro a plus de 12 h, si l'horloge a reculé, ou si le guide ne couvre
     * plus les 6 prochaines heures ([lastEndMs] = fin du dernier programme, null si guide vide).
     */
    fun epgNeedsRefresh(lastSyncAt: Long, lastEndMs: Long?, now: Long): Boolean =
        lastSyncAt <= 0L || now < lastSyncAt || now - lastSyncAt >= 12 * HOUR || lastEndMs == null || lastEndMs < now + 6 * HOUR
}
