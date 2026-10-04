package com.ultratv.tv.nativeapp.data.recording

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.RecordingDao
import com.ultratv.tv.nativeapp.data.db.RecordingEntity
import com.ultratv.tv.nativeapp.data.reminders.ExactAlarm
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.ui.common.Toaster
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed interface ScheduleResult {
    data class Scheduled(val count: Int) : ScheduleResult
    data object NoSpace : ScheduleResult
    data object NothingToSchedule : ScheduleResult
}

/**
 * Enregistrements programmés depuis le guide. La base est la source de vérité (status = "scheduled") ;
 * une alarme exacte réveille [RecordingAlarmReceiver] au début (moins la marge), qui confie le travail
 * à WorkManager via [RecordingRepository] — donc le même enregistreur HLS/TS que l'enregistrement
 * immédiat. Les alarmes sont ré-armées au démarrage de l'appareil.
 */
@Singleton
class RecordingScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: RecordingDao,
    private val epgDao: EpgDao,
    private val repo: RecordingRepository,
    private val provider: ProviderRepository,
    private val playback: PlaybackContext,
) {
    private val am = ctx.getSystemService<AlarmManager>()!!
    private val nm = ctx.getSystemService<NotificationManager>()!!

    /** Programme [prog] de [channel] ; [wholeSeries] ajoute les prochaines diffusions du même titre sur la chaîne. */
    suspend fun schedule(channel: ChannelEntity, prog: EpgEntity, wholeSeries: Boolean, nowMs: Long = System.currentTimeMillis()): ScheduleResult {
        val targets = buildList {
            add(prog)
            if (wholeSeries) addAll(RecordingPlan.seriesMatches(prog.title, epgDao.forChannelInRange(channel.id, nowMs, nowMs + SERIES_HORIZON_MS), nowMs))
        }.distinctBy { it.startMs }.filter { it.endMs > nowMs }
        if (targets.isEmpty()) return ScheduleResult.NothingToSchedule
        val longest = targets.maxOf { RecordingPlan.paddedWindow(it.startMs, it.endMs).let { (s, e) -> e - s } }
        if (!RecordingPlan.hasRoom(repo.freeBytes(), longest)) return ScheduleResult.NoSpace
        var count = 0
        for (p in targets) {
            if (dao.countScheduled(channel.providerId, channel.remoteId, p.startMs) > 0) continue
            val id = dao.upsert(
                RecordingEntity(
                    providerId = channel.providerId, kind = "LIVE", remoteId = channel.remoteId,
                    title = p.title, sourceUrl = channel.streamUrl, filePath = repo.newFilePath(p.title, channel.streamUrl),
                    status = "scheduled", scheduledStartMs = p.startMs, scheduledEndMs = p.endMs, channelName = channel.title,
                ),
            )
            arm(id, p.startMs, nowMs)
            count++
        }
        return if (count > 0) ScheduleResult.Scheduled(count) else ScheduleResult.NothingToSchedule
    }

    suspend fun cancel(id: Long) {
        am.cancel(pendingFor(id))
        dao.delete(id)
    }

    suspend fun rearmAll(nowMs: Long = System.currentTimeMillis()) {
        dao.expireMissed(nowMs)
        dao.scheduledPending(nowMs).forEach { arm(it.id, it.scheduledStartMs, nowMs) }
    }

    private fun arm(id: Long, programmeStartMs: Long, nowMs: Long) {
        val at = (programmeStartMs - RecordingPlan.PAD_BEFORE_MS).coerceAtLeast(nowMs + 1_000)
        ExactAlarm.set(am, at, pendingFor(id))
    }

    private fun pendingFor(id: Long): PendingIntent {
        val intent = Intent(ctx, RecordingAlarmReceiver::class.java).apply { action = ACTION_START; putExtra(EXTRA_ID, id) }
        return PendingIntent.getBroadcast(ctx, id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Appelé par l'alarme : vérifie l'espace et la connexion puis lance l'enregistrement. */
    suspend fun onAlarm(id: Long, nowMs: Long = System.currentTimeMillis()) {
        val r = dao.byId(id) ?: return
        if (r.status != "scheduled") return
        val (_, endPadded) = RecordingPlan.paddedWindow(r.scheduledStartMs, r.scheduledEndMs)
        val remaining = endPadded - nowMs
        if (remaining <= 0) { dao.setStatus(id, "failed", "missed"); return }
        if (!RecordingPlan.hasRoom(repo.freeBytes(), remaining)) {
            dao.setStatus(id, "failed", "no_space")
            notify(id, r.title, "Espace insuffisant : enregistrement annulé")
            Toaster.err("Espace insuffisant pour enregistrer « ${r.title} »")
            return
        }
        if (ConnectionPolicy.onRecordingStart(playing = playback.current.value != null) == ConnectionWarning.RECORDING_TAKES_CONNECTION) {
            val msg = "Un enregistrement démarre sur votre seule connexion : la lecture peut être interrompue"
            notify(id, r.title, msg)
            Toaster.show(msg)
        }
        val url = provider.resolveStalkerUrl(r.providerId, r.sourceUrl)
        dao.upsert(r.copy(sourceUrl = url, status = "queued"))
        repo.enqueueWork(id, remaining)
    }

    private fun notify(id: Long, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Recordings", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(ctx.applicationInfo.icon).setContentTitle(title).setContentText(text)
            .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_STATUS).build()
        runCatching { nm.notify(NOTIF_BASE + id.toInt(), n) }
    }

    companion object {
        const val CHANNEL_ID = "recordings"
        const val ACTION_START = "com.ultratv.tv.nativeapp.RECORDING_START"
        const val EXTRA_ID = "id"
        private const val NOTIF_BASE = 50_000
        private const val SERIES_HORIZON_MS = 14L * 24 * 3_600_000
    }
}

/** Cible de l'alarme : délègue à [RecordingScheduler.onAlarm] sans bloquer le thread principal. */
@AndroidEntryPoint
class RecordingAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: RecordingScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RecordingScheduler.ACTION_START) return
        val id = intent.getLongExtra(RecordingScheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try { scheduler.onAlarm(id) } finally { pending.finish(); scope.cancel() }
        }
    }
}
