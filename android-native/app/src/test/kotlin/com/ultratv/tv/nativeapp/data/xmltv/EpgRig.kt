package com.ultratv.tv.nativeapp.data.xmltv

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.repo.SyncStatusBus
import com.ultratv.tv.nativeapp.data.stalker.StalkerClient
import com.ultratv.tv.nativeapp.data.stalker.StalkerEndpointStore
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File

/** Dépôt réel (Room sur fichier, WAL) branché sur un faux serveur : aucun réseau. Sert aux tests et au banc du guide. */
class EpgRig(
    val server: FakeFeedServer,
    dir: File,
    parserFactory: (OkHttpClient) -> XmltvParser = { XmltvParser(it) },
    kind: String = "XTREAM",
    channels: List<Pair<String, String?>> = emptyList(),
) {
    val ctx: Context get() = ApplicationProvider.getApplicationContext()
    val db: UltraDb
    val repo: ProviderRepository
    val bus = SyncStatusBus()
    val limitsStore: ProviderLimitsStore
    val providerId: Long

    init {
        dir.mkdirs()
        db = Room.databaseBuilder(ctx, UltraDb::class.java, File(dir, "db.sqlite").absolutePath)
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        val ok = OkHttpClient.Builder().addInterceptor(server.interceptor).build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        limitsStore = ProviderLimitsStore(ctx)
        repo = ProviderRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = bus, xmltv = parserFactory(ok), epgDao = db.epgDao(), db = db, prefs = UserPreferencesStore(ctx, profiles),
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = limitsStore,
            langDisabled = com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()),
        )
        providerId = runBlocking {
            val pid = db.providerDao().upsert(ProviderEntity(name = "fictif", kind = kind, baseUrl = "http://serveur-fictif.invalid", username = "test", password = "test", active = true))
            if (channels.isNotEmpty()) db.channelDao().upsertAll(channels.mapIndexed { i, (rid, epg) ->
                ChannelEntity(providerId = pid, remoteId = "r$i", name = "Chaine $i", logo = null, categoryId = null, streamUrl = "http://serveur-fictif.invalid/live/$i.ts", epgChannelId = epg)
            })
            pid
        }
    }

    fun close() { server.closeAll(); runCatching { db.close() } }

    fun count(table: String): Int = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    suspend fun addCategory(remoteId: String, enabled: Boolean) {
        db.categoryDao().upsertAll(listOf(CategoryEntity(providerId = providerId, kind = "LIVE", remoteId = remoteId, name = "Cat $remoteId", enabled = enabled)))
    }
}
