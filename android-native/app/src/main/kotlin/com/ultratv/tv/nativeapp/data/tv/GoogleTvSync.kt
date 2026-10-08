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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

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
    private val movies: com.ultratv.tv.nativeapp.data.db.MovieDao,
    private val seriesDao: com.ultratv.tv.nativeapp.data.db.SeriesDao,
    private val prefsStore: com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore,
) {
    private val isTv = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val prefs = ctx.getSharedPreferences("google_tv", Context.MODE_PRIVATE)
    private var started = false
    @Volatile private var lastWatchNextSync = 0L

    private val activeProvider = providers.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()

    /** (profil courant, source active) : Watch Next et la chaîne Favoris suivent le profil, pas seulement la source. */
    private val scope_ = combine(profiles.currentId, activeProvider) { prof, pid -> prof to pid }.distinctUntilChanged()

    fun start(scope: CoroutineScope) {
        if (!isTv || started) return
        started = true
        scope.launch {
            scope_.flatMapLatest { (prof, pid) -> if (pid == null) flowOf(emptyList()) else history.observeRecent(prof, pid, 40) }
                // 1.2.42 avait mis un debounce de 45 s : tant que l'historique bouge (position enregistrée toutes les 30 s,
                // synchro d'appareils), RIEN n'était publié, et rien non plus si l'appli était quittée / tuée dans les 45 s
                // suivant la fin d'une lecture — « Continuer à regarder » restait vide. Maintenant : 2 s de calme, puis
                // publication immédiate si la dernière écriture date de plus de 45 s, sinon au terme de ce délai (au plus
                // une écriture toutes les 45 s dans Google TV, mais la première ne se fait jamais attendre).
                .debounce(2_000).collectLatest { plan ->
                    delay(WatchNextPlan.waitMs(lastWatchNextSync, System.currentTimeMillis()))
                    runCatching { syncWatchNext(WatchNextPlan.select(plan) + WatchNextPlan.selectLive(plan)) }.onFailure { e -> android.util.Log.w("UltraGoogleTv", "watch next", e) }
                    lastWatchNextSync = System.currentTimeMillis()
                }
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
        // Chaîne « Nouveautés » : suit la source active. Les tables film / série bougent pendant toute la synchro du
        // catalogue : on attend 5 s de calme (fin de synchro), puis la signature évite toute réécriture inutile.
        scope.launch {
            activeProvider.flatMapLatest { pid -> if (pid == null) flowOf(emptyList()) else latestFlow(pid) }
                .debounce(5_000).collect { runCatching { syncNewsChannel(it) }.onFailure { e -> android.util.Log.w("UltraGoogleTv", "news channel", e) } }
        }
    }

    private fun latestFlow(pid: Long) = combine(movies.observeLatest(pid, NewsChannelPlan.FETCH), seriesDao.observeLatest(pid, NewsChannelPlan.FETCH)) { ms, ss ->
        NewsChannelPlan.select(
            ms.map { NewsChannelPlan.Item(NewsChannelPlan.Kind.MOVIE, it.providerId, it.remoteId, it.title, it.poster, it.addedKey) },
            ss.map { NewsChannelPlan.Item(NewsChannelPlan.Kind.SERIES, it.providerId, it.remoteId, it.title, it.poster, it.addedKey) },
        )
    }

    /** Nom de la chaîne dans la langue de l'appli (« système » : langue de l'appareil). */
    private suspend fun newsChannelName(): String {
        val code = prefsStore.flow.first().language
        val lang = com.ultratv.tv.nativeapp.i18n.AppLang.fromCode(code).let { l ->
            if (l != com.ultratv.tv.nativeapp.i18n.AppLang.System) l
            else com.ultratv.tv.nativeapp.i18n.AppLang.entries.firstOrNull { it.code == java.util.Locale.getDefault().language } ?: com.ultratv.tv.nativeapp.i18n.AppLang.English
        }
        return com.ultratv.tv.nativeapp.i18n.DesignStrings(lang).newsChannelName
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
        runCatching { syncNewsChannel(latestFlow(pid).first()) }
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

    // ---- Chaîne de prévisualisation « Nouveautés » ------------------------------------------

    /** Les écritures dans le fournisseur de contenu sont sérialisées (collecte continue + demande du système). */
    private val newsLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun syncNewsChannel(selected: List<NewsChannelPlan.Item>) = newsLock.withLock {
        val helper = PreviewChannelHelper(ctx)
        var channelId = prefs.getLong(K_NEWS_CHANNEL, -1L)
        if (selected.isEmpty() && channelId < 0) return@withLock
        val name = newsChannelName()
        if (channelId < 0) {
            val logo = ContextCompat.getDrawable(ctx, com.ultratv.tv.nativeapp.R.mipmap.ic_launcher)!!.toBitmap(160, 160)
            val channel = PreviewChannel.Builder()
                .setDisplayName(name)
                .setAppLinkIntentUri(Uri.parse("${com.ultratv.tv.nativeapp.nav.DeepLink.SCHEME}://home"))
                .setLogo(logo)
                .build()
            // Chaîne ordinaire : l'utilisateur l'ajoute à l'accueil depuis « Personnaliser les chaînes » (seule la
            // première chaîne de l'application est affichée d'office par le système).
            channelId = helper.publishChannel(channel)
            prefs.edit().putLong(K_NEWS_CHANNEL, channelId).putString(K_NEWS_NAME, name).remove(K_NEWS_SIGNATURE).apply()
        } else if (prefs.getString(K_NEWS_NAME, null) != name) {
            // Langue de l'appli changée : on renomme la chaîne.
            runCatching {
                val current = helper.getPreviewChannel(channelId)
                if (current != null) helper.updatePreviewChannel(channelId, PreviewChannel.Builder(current).setDisplayName(name).build())
            }
            prefs.edit().putString(K_NEWS_NAME, name).apply()
        }
        val signature = NewsChannelPlan.signature(selected)
        if (prefs.getString(K_NEWS_SIGNATURE, null) == signature) return@withLock
        val cr = ctx.contentResolver
        val existing = HashMap<String, Long>()
        cr.query(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), PreviewProgram.PROJECTION, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val p = PreviewProgram.fromCursor(c)
                p.internalProviderId?.let { existing[it] = p.id }
            }
        }
        fun program(i: NewsChannelPlan.Item, index: Int) = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(if (i.kind == NewsChannelPlan.Kind.MOVIE) TvContractCompat.PreviewPrograms.TYPE_MOVIE else TvContractCompat.PreviewPrograms.TYPE_TV_SERIES)
            .setTitle(i.title)
            .setPosterArtUri(Uri.parse(i.poster))
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
            .setWeight(NewsChannelPlan.weight(index, selected.size))
            .setInternalProviderId(NewsChannelPlan.internalId(i))
            .setIntentUri(Uri.parse(NewsChannelPlan.deepLink(i)))
            .build()
        val diff = NewsChannelPlan.diff(existing, selected)
        diff.deleteRowIds.forEach { cr.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
        selected.forEachIndexed { index, i ->
            val rowId = diff.update.firstOrNull { it.second === i }?.first
            if (rowId != null) cr.update(TvContractCompat.buildPreviewProgramUri(rowId), program(i, index).toContentValues(), null, null)
            else helper.publishPreviewProgram(program(i, index))
        }
        prefs.edit().putString(K_NEWS_SIGNATURE, signature).apply()
        android.util.Log.i("UltraGoogleTv", "news channel: ${selected.size} (+${diff.insert.size} -${diff.deleteRowIds.size})")
    }

    private companion object {
        const val K_NEWS_CHANNEL = "news_channel_id"
        const val K_NEWS_SIGNATURE = "news_signature"
        const val K_NEWS_NAME = "news_name"
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
