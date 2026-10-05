package com.ultratv.tv.nativeapp.data.tv

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.FavoriteDao
import com.ultratv.tv.nativeapp.data.db.WatchHistoryDao
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Intégration Google TV / Android TV : « Continuer à regarder » (Watch Next) et chaîne de prévisualisation
 * « Favoris » de l'écran d'accueil. Elle observe la base (historique, favoris) : la lecture n'a rien à appeler,
 * la fin d'une lecture met l'historique à jour, donc Watch Next aussi. Sans effet hors appareil Android TV.
 */
@Singleton
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GoogleTvSync @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val providers: ProviderRepository,
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val channels: ChannelDao,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
) {
    private val isTv = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val prefs = ctx.getSharedPreferences("google_tv", Context.MODE_PRIVATE)
    private var started = false

    private val activeProvider = providers.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    /** (profil courant, source active) : Watch Next et la chaîne Favoris suivent le profil, pas seulement la source. */
    private val scope_ = combine(profiles.currentId, activeProvider) { prof, pid -> prof to pid }.distinctUntilChanged()

    fun start(scope: CoroutineScope) {
        if (!isTv || started) return
        started = true
        scope.launch {
            scope_.flatMapLatest { (prof, pid) -> if (pid == null) flowOf(emptyList()) else history.observeRecent(prof, pid, 40) }
                .debounce(3_000).collect { runCatching { syncWatchNext(WatchNextPlan.select(it) + WatchNextPlan.selectLive(it)) }.onFailure { e -> android.util.Log.w("UltraGoogleTv", "watch next", e) } }
        }
        scope.launch {
            scope_.flatMapLatest { (prof, pid) ->
                if (pid == null) flowOf(emptyList())
                else combine(favorites.observeForKind(prof, pid, "LIVE"), history.observeRecent(prof, pid, 40)) { favs, hist ->
                    val fav = orderedByRemote(pid, favs.map { it.remoteId })
                    val recent = orderedByRemote(pid, WatchNextPlan.selectLive(hist).map { it.remoteId })
                    FavoritesChannelPlan.select(fav, recent)
                }
            }.debounce(3_000).collect { runCatching { syncFavoritesChannel(it) }.onFailure { e -> android.util.Log.w("UltraGoogleTv", "home channel", e) } }
        }
    }

    /** Synchro ponctuelle (demande du système à l'initialisation des programmes). */
    suspend fun syncOnce() {
        if (!isTv) return
        val pid = activeProvider.first() ?: return
        val prof = profiles.currentIdNow
        val hist = history.observeRecent(prof, pid, 40).first()
        runCatching { syncWatchNext(WatchNextPlan.select(hist) + WatchNextPlan.selectLive(hist)) }
        runCatching {
            val favs = favorites.observeForKind(prof, pid, "LIVE").first()
            syncFavoritesChannel(FavoritesChannelPlan.select(orderedByRemote(pid, favs.map { it.remoteId }), orderedByRemote(pid, WatchNextPlan.selectLive(hist).map { it.remoteId })))
        }
    }

    /** Chaînes dans l'ordre des identifiants donnés (la requête par lot ne garantit pas l'ordre). */
    private suspend fun orderedByRemote(pid: Long, ids: List<String>): List<com.ultratv.tv.nativeapp.data.db.ChannelEntity> {
        if (ids.isEmpty()) return emptyList()
        val byId = channels.byRemoteIds(pid, ids).associateBy { it.remoteId }
        return ids.mapNotNull { byId[it] }
    }

    // ---- Watch Next -----------------------------------------------------------------

    private fun syncWatchNext(selected: List<com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity>) {
        val cr = ctx.contentResolver
        // Lignes déjà publiées par l'application : identifiant interne -> identifiant de ligne.
        val existing = HashMap<String, Long>()
        cr.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, WatchNextProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = WatchNextProgram.fromCursor(c)
                p.internalProviderId?.let { existing[it] = p.id }
            }
        }
        val keep = HashSet<String>()
        android.util.Log.i("UltraGoogleTv", "watch next: ${selected.size} (${selected.count { it.kind == "LIVE" }} live)")
        for (h in selected) {
            val key = WatchNextPlan.internalId(h)
            keep += key
            if (h.kind == "LIVE") {
                // Chaîne du direct : vignette 16:9 (logo), marquée « en direct », sans progression.
                val live = WatchNextProgram.Builder()
                    .setType(TvContractCompat.WatchNextPrograms.TYPE_CHANNEL)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(h.watchedAt)
                    .setTitle(h.title)
                    .setLive(true)
                    .setPosterArtUri(Uri.parse(h.poster?.takeIf { it.isNotBlank() } ?: "android.resource://${ctx.packageName}/drawable/banner"))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setInternalProviderId(key)
                    .setIntentUri(Uri.parse(WatchNextPlan.deepLink(h)))
                    .build()
                val rowId = existing[key]
                if (rowId == null) cr.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, live.toContentValues())
                else cr.update(TvContractCompat.buildWatchNextProgramUri(rowId), live.toContentValues(), null, null)
                continue
            }
            val b = WatchNextProgram.Builder()
                .setType(if (h.kind == "EPISODE") TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE else TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(h.watchedAt)
                .setTitle(h.title)
                .setPosterArtUri(Uri.parse(h.poster))
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                .setLastPlaybackPositionMillis(h.positionMs.toInt())
                .setInternalProviderId(key)
                .setIntentUri(Uri.parse(WatchNextPlan.deepLink(h)))
            if (h.durationMs > 0) b.setDurationMillis(h.durationMs.toInt())
            val program = b.build()
            val rowId = existing[key]
            if (rowId == null) cr.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            else cr.update(TvContractCompat.buildWatchNextProgramUri(rowId), program.toContentValues(), null, null)
        }
        // Terminé, supprimé de l'historique ou éclipsé par un épisode plus récent : on le retire de l'accueil.
        existing.filterKeys { it !in keep }.values.forEach { cr.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null) }
    }

    // ---- Chaîne de prévisualisation « Favoris » -------------------------------------------

    private fun syncFavoritesChannel(selected: List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>) {
        val helper = PreviewChannelHelper(ctx)
        var channelId = prefs.getLong(K_CHANNEL, -1L)
        if (selected.isEmpty() && channelId < 0) return
        if (channelId < 0) {
            val logo = ContextCompat.getDrawable(ctx, com.ultratv.tv.nativeapp.R.mipmap.ic_launcher)!!.toBitmap(160, 160)
            val channel = PreviewChannel.Builder()
                .setDisplayName(ctx.getString(com.ultratv.tv.nativeapp.R.string.tv_favorites_channel))
                .setAppLinkIntentUri(Uri.parse("${com.ultratv.tv.nativeapp.nav.DeepLink.SCHEME}://home"))
                .setLogo(logo)
                .build()
            channelId = helper.publishDefaultChannel(channel)
            prefs.edit().putLong(K_CHANNEL, channelId).remove(K_SIGNATURE).apply()
        }
        val signature = FavoritesChannelPlan.signature(selected)
        if (prefs.getString(K_SIGNATURE, null) == signature) return
        ctx.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null)
        for (c in selected) {
            val b = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(c.title)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setInternalProviderId("LIVE:${c.providerId}:${c.remoteId}")
                .setIntentUri(Uri.parse(FavoritesChannelPlan.deepLink(c)))
            b.setPosterArtUri(Uri.parse(c.logo?.takeIf { it.isNotBlank() } ?: "android.resource://${ctx.packageName}/drawable/banner"))
            helper.publishPreviewProgram(b.build())
        }
        prefs.edit().putString(K_SIGNATURE, signature).apply()
    }

    private companion object {
        const val K_CHANNEL = "favorites_channel_id"
        const val K_SIGNATURE = "favorites_signature"
    }
}

/** Le lanceur Android TV demande aux applications de publier leurs programmes (premier lancement, chaîne approuvée). */
@AndroidEntryPoint
class TvInitializeReceiver : BroadcastReceiver() {
    @Inject lateinit var sync: GoogleTvSync

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        scope.launch { try { sync.syncOnce() } finally { pending.finish(); scope.cancel() } }
    }
}
