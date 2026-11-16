package com.ultratv.tv.nativeapp.data.replay

import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Les deux formes de flux de timeshift d'un serveur Xtream. Le serveur n'en accepte souvent qu'une. */
enum class ReplayStyle { PATH, PHP }

/** Pourquoi un programme est (ou non) rejouable. Jamais d'URL ici. */
enum class ReplayAvailability { AVAILABLE, NOT_ARCHIVED, OUT_OF_WINDOW, NOT_STARTED, UNSUPPORTED_SOURCE }

/**
 * Construction PURE des URL de replay (Xtream, et gabarits M3U via [CatchupFormats]). L'URL contient les identifiants
 * de la source : elle n'est jamais affichée ni journalisée, seulement passée au moteur de lecture.
 */
object ReplayUrls {
    private val XTREAM_LIVE = Regex("^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/([^./?]+)(\\..+)?$")

    /** Format attendu par Xtream : `AAAA-MM-JJ:HH-MM`, à l'heure du serveur (à défaut, celle de [zone]). */
    fun startStamp(startMs: Long, zone: TimeZone): String =
        SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = zone }.format(Date(startMs))

    fun durationMinutes(prog: EpgEntity): Int = (((prog.endMs - prog.startMs) + 59_999) / 60_000).toInt().coerceAtLeast(1)

    fun availability(channel: ChannelEntity, prog: EpgEntity, nowMs: Long): ReplayAvailability =
        availabilityAt(channel, prog.startMs, nowMs)

    /** Disponibilité d'un instant de départ quelconque (programme ou retour arrière dans le direct). */
    fun availabilityAt(channel: ChannelEntity, startMs: Long, nowMs: Long): ReplayAvailability {
        if (channel.catchupDays <= 0) return ReplayAvailability.NOT_ARCHIVED
        if (startMs > nowMs) return ReplayAvailability.NOT_STARTED
        if (startMs < earliestMs(channel, nowMs)) return ReplayAvailability.OUT_OF_WINDOW
        if (channel.catchupSource.isNullOrBlank() && XTREAM_LIVE.matchEntire(channel.streamUrl) == null) return ReplayAvailability.UNSUPPORTED_SOURCE
        return ReplayAvailability.AVAILABLE
    }

    /** Plus ancien instant rejouable : [ChannelEntity.catchupDays] jours avant [nowMs]. */
    fun earliestMs(channel: ChannelEntity, nowMs: Long): Long = nowMs - channel.catchupDays.coerceAtLeast(0) * 24L * 3_600_000L

    /**
     * Départ d'un retour arrière de [behindMs] dans le direct, ramené à la borne d'archive de la chaîne ; null si la chaîne
     * n'a pas d'archive utilisable (ni gabarit, ni URL Xtream).
     */
    fun rewindStart(channel: ChannelEntity, nowMs: Long, behindMs: Long): Long? {
        if (channel.catchupDays <= 0) return null
        if (channel.catchupSource.isNullOrBlank() && XTREAM_LIVE.matchEntire(channel.streamUrl) == null) return null
        return (nowMs - behindMs).coerceAtLeast(earliestMs(channel, nowMs))
    }

    /** URL de replay, ou null si le programme n'est pas rejouable. */
    fun build(channel: ChannelEntity, prog: EpgEntity, nowMs: Long, style: ReplayStyle, zone: TimeZone = TimeZone.getDefault()): String? =
        buildAt(channel, prog.startMs, prog.endMs, nowMs, style, zone)

    /** URL de replay de [startMs]..[endMs] (l'heure de début est écrite dans le fuseau [zone] du fournisseur pour Xtream). */
    fun buildAt(channel: ChannelEntity, startMs: Long, endMs: Long, nowMs: Long, style: ReplayStyle, zone: TimeZone = TimeZone.getDefault()): String? {
        if (availabilityAt(channel, startMs, nowMs) != ReplayAvailability.AVAILABLE) return null
        val template = channel.catchupSource
        if (!template.isNullOrBlank()) return CatchupFormats.fill(template, startMs, endMs, nowMs)
        val (base, user, pass, sid) = (XTREAM_LIVE.matchEntire(channel.streamUrl) ?: return null).destructured
        val start = startStamp(startMs, zone)
        val dur = (((endMs - startMs) + 59_999) / 60_000).toInt().coerceAtLeast(1)
        return when (style) {
            ReplayStyle.PATH -> "$base/timeshift/$user/$pass/$dur/$start/$sid.ts"
            ReplayStyle.PHP -> "$base/streaming/timeshift.php?username=$user&password=$pass&stream=$sid&start=$start&duration=$dur"
        }
    }
}

/** Calculs purs de la barre de replay : heure réelle, sauts, programme voisin. */
object ReplayTransport {
    const val SHORT_JUMP_SEC = 10
    const val LONG_JUMP_SEC = 60
    /** Retour arrière proposé depuis le direct. */
    const val LIVE_REWIND_SEC = 300

    /** Heure réelle (murale) de la position [positionMs] d'un replay qui démarre à [startMs]. */
    fun wallClockMs(startMs: Long, positionMs: Long): Long = startMs + positionMs.coerceAtLeast(0)

    /** Nouvelle position après un saut de [deltaSec], bornée à la durée connue ([durationMs] <= 0 : inconnue, seul 0 borne). */
    fun jumpTarget(positionMs: Long, deltaSec: Int, durationMs: Long): Long {
        val t = (positionMs + deltaSec * 1000L).coerceAtLeast(0)
        return if (durationMs > 0) t.coerceAtMost(durationMs) else t
    }

    /** Programme précédent : le dernier à commencer avant [current]. */
    fun previous(list: List<EpgEntity>, current: EpgEntity): EpgEntity? =
        list.filter { it.startMs < current.startMs }.maxByOrNull { it.startMs }

    /** Programme suivant déjà commencé à [nowMs] ; null = aucun (le lecteur revient au direct). */
    fun next(list: List<EpgEntity>, current: EpgEntity, nowMs: Long): EpgEntity? =
        list.filter { it.startMs > current.startMs && it.startMs >= current.endMs - 1_000 && it.startMs <= nowMs }.minByOrNull { it.startMs }
}

/**
 * Replay (rattrapage). API pour le guide et la surcouche du lecteur :
 *  - [availability] : état « replay disponible » d'un programme ;
 *  - [urlFor] : URL à passer au moteur (null si indisponible).
 * La forme d'URL Xtream (chemin / timeshift.php) est mémorisée par source ; [onReplayFailed] bascule sur l'autre une fois.
 */
@Singleton
class ReplayService @Inject constructor(
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val limits: ProviderLimitsStore,
) {
    private val styles = ConcurrentHashMap<Long, ReplayStyle>()
    private val triedBoth = ConcurrentHashMap.newKeySet<Long>()

    /** Programme dont le replay vient d'être lancé depuis le guide : le lecteur le reprend pour son repli (autre forme d'URL). */
    @Volatile private var pending: Pair<EpgEntity, Long>? = null
    fun armFromGuide(programme: EpgEntity, providerId: Long) { pending = programme to providerId }
    fun takePending(): Pair<EpgEntity, Long>? = pending.also { pending = null }

    /** Fuseau du serveur du fournisseur (`server_info.timezone`), à défaut celui de l'appareil. */
    fun zoneFor(providerId: Long): TimeZone =
        limits.serverTimezone(providerId)?.let { id -> TimeZone.getTimeZone(id).takeIf { it.id == id } } ?: TimeZone.getDefault()

    /** URL de replay (synchrone, pure) pour la chaîne et le programme déjà chargés par le guide. */
    fun urlFor(channel: ChannelEntity, programme: EpgEntity, nowMs: Long = System.currentTimeMillis()): String? =
        ReplayUrls.build(channel, programme, nowMs, styleFor(channel.providerId), zoneFor(channel.providerId))

    fun styleFor(providerId: Long): ReplayStyle = styles[providerId] ?: ReplayStyle.PATH

    suspend fun availability(programme: EpgEntity, nowMs: Long = System.currentTimeMillis()): ReplayAvailability {
        val ch = channelDao.byId(programme.channelId) ?: return ReplayAvailability.NOT_ARCHIVED
        return ReplayUrls.availability(ch, programme, nowMs)
    }

    suspend fun urlFor(programme: EpgEntity, nowMs: Long = System.currentTimeMillis()): String? {
        val ch = channelDao.byId(programme.channelId) ?: return null
        return urlFor(ch, programme, nowMs)
    }

    /**
     * Retour arrière dans le direct : programme « virtuel » commençant [behindSec] secondes plus tôt (borné à l'archive)
     * et finissant avec le programme en cours (au moins 30 min). null si la chaîne n'a pas d'archive.
     */
    suspend fun rewindProgramme(channelId: Long, current: EpgEntity?, behindSec: Int, nowMs: Long = System.currentTimeMillis()): EpgEntity? =
        programmeFrom(channelId, current, nowMs - behindSec * 1000L, nowMs)

    /** Comme [rewindProgramme], mais pour un instant de départ précis [startMs] (borné à l'archive de la chaîne). */
    suspend fun programmeFrom(channelId: Long, current: EpgEntity?, startMs: Long, nowMs: Long = System.currentTimeMillis()): EpgEntity? {
        val ch = channelDao.byId(channelId) ?: return null
        val start = ReplayUrls.rewindStart(ch, nowMs, (nowMs - startMs).coerceAtLeast(0)) ?: return null
        val end = maxOf(current?.endMs ?: 0L, nowMs + 30 * 60_000L)
        return (current?.copy(startMs = start, endMs = end)) ?: EpgEntity(channelId = channelId, title = "", description = null, startMs = start, endMs = end)
    }

    /** URL du direct de la chaîne (Xtream et M3U : l'URL stockée est lisible telle quelle). */
    suspend fun liveUrl(channelId: Long): String? = channelDao.byId(channelId)?.streamUrl

    /** Programme voisin ([forward] faux = précédent) dans le guide de la chaîne, ou null. */
    suspend fun neighbour(current: EpgEntity, forward: Boolean, nowMs: Long = System.currentTimeMillis()): EpgEntity? {
        val list = epgDao.forChannelInRange(current.channelId, current.startMs - 2 * 24L * 3_600_000L, nowMs)
        return if (forward) ReplayTransport.next(list, current, nowMs) else ReplayTransport.previous(list, current)
    }

    /** Le flux de replay a échoué : bascule sur l'autre forme d'URL ; faux si les deux ont déjà été essayées. */
    fun onReplayFailed(providerId: Long): Boolean {
        if (!triedBoth.add(providerId)) { triedBoth.remove(providerId); return false }
        styles[providerId] = if (styleFor(providerId) == ReplayStyle.PATH) ReplayStyle.PHP else ReplayStyle.PATH
        return true
    }

    /** Une forme a fonctionné : on la garde et on réarme l'essai alternatif. */
    fun onReplayWorked(providerId: Long) { triedBoth.remove(providerId) }
}
