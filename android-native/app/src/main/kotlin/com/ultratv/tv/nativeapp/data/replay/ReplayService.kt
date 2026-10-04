package com.ultratv.tv.nativeapp.data.replay

import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.repo.Catchup
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
 * Construction PURE des URL de replay Xtream. L'URL contient les identifiants de la source : elle n'est
 * jamais affichée ni journalisée, seulement passée au moteur de lecture.
 */
object ReplayUrls {
    private val XTREAM_LIVE = Regex("^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/([^./?]+)(\\..+)?$")

    /** Format attendu par Xtream : `AAAA-MM-JJ:HH-MM`, à l'heure du serveur (à défaut, celle de [zone]). */
    fun startStamp(startMs: Long, zone: TimeZone): String =
        SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = zone }.format(Date(startMs))

    fun durationMinutes(prog: EpgEntity): Int = (((prog.endMs - prog.startMs) + 59_999) / 60_000).toInt().coerceAtLeast(1)

    fun availability(channel: ChannelEntity, prog: EpgEntity, nowMs: Long): ReplayAvailability {
        if (channel.catchupDays <= 0) return ReplayAvailability.NOT_ARCHIVED
        if (prog.startMs > nowMs) return ReplayAvailability.NOT_STARTED
        val windowMs = channel.catchupDays * 24L * 3_600_000L
        if (nowMs - prog.startMs > windowMs) return ReplayAvailability.OUT_OF_WINDOW
        if (channel.catchupSource.isNullOrBlank() && XTREAM_LIVE.matchEntire(channel.streamUrl) == null) return ReplayAvailability.UNSUPPORTED_SOURCE
        return ReplayAvailability.AVAILABLE
    }

    /** URL de replay, ou null si le programme n'est pas rejouable. */
    fun build(channel: ChannelEntity, prog: EpgEntity, nowMs: Long, style: ReplayStyle, zone: TimeZone = TimeZone.getDefault()): String? {
        if (availability(channel, prog, nowMs) != ReplayAvailability.AVAILABLE) return null
        val template = channel.catchupSource
        if (!template.isNullOrBlank()) return Catchup.fillTemplate(template, prog)
        val (base, user, pass, sid) = XTREAM_LIVE.matchEntire(channel.streamUrl)!!.destructured
        val start = startStamp(prog.startMs, zone)
        val dur = durationMinutes(prog)
        return when (style) {
            ReplayStyle.PATH -> "$base/timeshift/$user/$pass/$dur/$start/$sid.ts"
            ReplayStyle.PHP -> "$base/streaming/timeshift.php?username=$user&password=$pass&stream=$sid&start=$start&duration=$dur"
        }
    }
}

/**
 * Replay (catch-up Xtream). API pour le guide et la surcouche du lecteur :
 *  - [availability] : état « replay disponible » d'un programme ;
 *  - [urlFor] : URL à passer au moteur (null si indisponible).
 * La forme d'URL (chemin / timeshift.php) est mémorisée par source ; [onReplayFailed] bascule sur l'autre une fois.
 */
@Singleton
class ReplayService @Inject constructor(private val channelDao: ChannelDao) {
    private val styles = ConcurrentHashMap<Long, ReplayStyle>()
    private val triedBoth = ConcurrentHashMap.newKeySet<Long>()

    /** Programme dont le replay vient d'être lancé depuis le guide : le lecteur le reprend pour son repli (autre forme d'URL). */
    @Volatile private var pending: Pair<EpgEntity, Long>? = null
    fun armFromGuide(programme: EpgEntity, providerId: Long) { pending = programme to providerId }
    fun takePending(): Pair<EpgEntity, Long>? = pending.also { pending = null }

    /** URL de replay (synchrone, pure) pour la chaîne et le programme déjà chargés par le guide. */
    fun urlFor(channel: ChannelEntity, programme: EpgEntity, nowMs: Long = System.currentTimeMillis()): String? =
        ReplayUrls.build(channel, programme, nowMs, styleFor(channel.providerId))

    fun styleFor(providerId: Long): ReplayStyle = styles[providerId] ?: ReplayStyle.PATH

    suspend fun availability(programme: EpgEntity, nowMs: Long = System.currentTimeMillis()): ReplayAvailability {
        val ch = channelDao.byId(programme.channelId) ?: return ReplayAvailability.NOT_ARCHIVED
        return ReplayUrls.availability(ch, programme, nowMs)
    }

    suspend fun urlFor(programme: EpgEntity, nowMs: Long = System.currentTimeMillis()): String? {
        val ch = channelDao.byId(programme.channelId) ?: return null
        return ReplayUrls.build(ch, programme, nowMs, styleFor(ch.providerId))
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
