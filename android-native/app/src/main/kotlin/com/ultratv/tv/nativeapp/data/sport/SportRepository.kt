package com.ultratv.tv.nativeapp.data.sport

import androidx.sqlite.db.SimpleSQLiteQuery
import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Résultat de la rubrique Sport : candidats (programmes de la fenêtre now..now+48 h), chaînes de sport, présence d'un guide. */
data class SportData(val entries: List<SportEntry>, val channels: List<ChannelEntity>, val hasGuide: Boolean)

/**
 * Données de la rubrique Sport, calculées UNIQUEMENT à partir de la base (guide + chaînes de la source). Aucune requête
 * réseau. Pensé pour une box d'entrée de gamme :
 *  - les chaînes de sport sont détectées une fois (catégories par nom, puis noms de chaînes pré-filtrés en SQL) et gardées
 *    jusqu'à la prochaine synchro ([invalidate]) ;
 *  - les programmes sont lus par fenêtre de temps (maintenant .. +48 h) sur l'index (channelId, startMs) pour ces chaînes,
 *    et par un pré-filtre LIKE borné (800 lignes) pour les autres ; le filtre exact tourne ensuite sur cet ensemble borné ;
 *  - tout est mis en cache [CACHE_MS] : ouvrir l'écran ou recomposer ne relit rien.
 */
@Singleton
class SportRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val categoryDao: CategoryDao,
) {
    private class ChannelsCache(val pid: Long, val epoch: Int, val channels: List<ChannelEntity>)
    private class DataCache(val pid: Long, val epoch: Int, val atMs: Long, val data: SportData)

    private val mutex = Mutex()
    @Volatile private var epoch = 0
    private var channelsCache: ChannelsCache? = null
    private var dataCache: DataCache? = null

    /** À appeler quand une synchro (chaînes ou guide) se termine : les détections et les programmes sont relus. */
    fun invalidate() { epoch++ }

    suspend fun load(pid: Long, nowMs: Long): SportData = mutex.withLock {
        val ep = epoch
        dataCache?.let { if (it.pid == pid && it.epoch == ep && (nowMs - it.atMs).let { d -> d >= 0 && d < CACHE_MS }) return@withLock it.data }
        withContext(Dispatchers.IO) {
            val sport = sportChannels(pid, ep)
            val to = nowMs + SportLogic.UPCOMING_MS
            val hasGuide = (epgDao.lastEndForProvider(pid) ?: 0L) > nowMs
            val byId = sport.associateBy { it.id }
            // Chaînes de sport : tout leur guide de la fenêtre (bornées à MAX_PROG_CHANNELS pour la mémoire).
            val progIds = sport.take(MAX_PROG_CHANNELS).map { it.id }
            val onSport = progIds.chunked(CHUNK).flatMap { epgDao.sportRange(it, nowMs, to) }
                .mapNotNull { p -> byId[p.channelId]?.let { c -> SportEntry(c, p, true, SportDetector.isSportProgramme(p.title)) } }
            // Autres chaînes : pré-filtre LIKE sur le titre, puis filtre exact.
            val seen = progIds.toHashSet()
            val others = if (!hasGuide) emptyList() else otherCandidates(pid, nowMs, to).filter { it.channelId !in seen && SportDetector.isSportProgramme(it.title) }
            val otherChannels = others.map { it.channelId }.distinct().chunked(CHUNK).flatMap { channelDao.byIds(it) }.associateBy { it.id }
            val onOthers = others.mapNotNull { p -> otherChannels[p.channelId]?.let { c -> SportEntry(c, p, false, true) } }
            val data = SportData(entries = onSport + onOthers, channels = sport, hasGuide = hasGuide)
            dataCache = DataCache(pid, ep, nowMs, data)
            data
        }
    }

    private suspend fun otherCandidates(pid: Long, nowMs: Long, toMs: Long): List<com.ultratv.tv.nativeapp.data.db.EpgEntity> {
        val like = SportDetector.TITLE_LIKE
        val sql = "SELECT e.id, e.channelId, e.title, NULL AS description, e.startMs, e.endMs FROM epg e JOIN channel c ON c.id = e.channelId " +
            "WHERE c.providerId = ? AND c.junk = 0 AND c.isSeparator = 0 AND e.endMs > ? AND e.startMs <= ? AND (" +
            like.joinToString(" OR ") { "e.title LIKE ?" } + ") ORDER BY e.startMs LIMIT $MAX_OTHER_ROWS"
        val args = (listOf<Any?>(pid, nowMs, toMs) + like).toTypedArray()
        return epgDao.epgRaw(SimpleSQLiteQuery(sql, args))
    }

    private suspend fun sportChannels(pid: Long, ep: Int): List<ChannelEntity> {
        channelsCache?.let { if (it.pid == pid && it.epoch == ep) return it.channels }
        val cats = categoryDao.observeForProviderKind(pid, "LIVE").first()
        val catIds = cats.filter { it.enabled && SportDetector.isSportChannel(null, it.name) }.map { it.remoteId }
        val byCat = catIds.chunked(CHUNK).flatMap { channelDao.byCategories(pid, it) }
        val like = SportDetector.CHANNEL_LIKE
        val sql = "SELECT * FROM channel WHERE providerId = ? AND junk = 0 AND isSeparator = 0 AND (" + like.joinToString(" OR ") { "name LIKE ?" } + ")"
        val byName = channelDao.channelsRaw(SimpleSQLiteQuery(sql, (listOf<Any?>(pid) + like).toTypedArray()))
            .filter { SportDetector.isSportChannel(it.name, null) }
        val all = SportLogic.sportChannels(byCat + byName, MAX_CHANNELS)
        channelsCache = ChannelsCache(pid, ep, all)
        return all
    }

    private companion object {
        const val CACHE_MS = 5 * 60_000L
        const val CHUNK = 400
        const val MAX_PROG_CHANNELS = 400
        const val MAX_CHANNELS = 800
        const val MAX_OTHER_ROWS = 800
    }
}
