package com.ultratv.tv.nativeapp.data.kairo

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.FavoriteDao
import com.ultratv.tv.nativeapp.data.db.WatchHistoryDao
import com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore
import com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.tv.WatchNextPlan
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fournisseur « en cours / suivant » pour le launcher Kairo (contrat docs/superpowers/specs/2026-11-21-integration-kairo.md).
 * Autorité `{applicationId}.kairo` ; `content://…/now` (une ligne par chaîne, colonnes [KairoNowPlan.COLUMNS]) et
 * `content://…/profile` (colonne `kids`). Lecture seule, exporté, réservé à Kairo signé par une clé de
 * [KairoAccess.ALLOWED_SHA256] (vérifié à CHAQUE requête) ; tout autre appelant reçoit un curseur vide.
 */
class KairoNowProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun reader(): KairoNowReader }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = context!!
        val path = uri.pathSegments.firstOrNull()
        val columns = if (path == PATH_PROFILE) arrayOf(KairoNowPlan.PROFILE_COLUMN) else KairoNowPlan.COLUMNS
        val cursor = MatrixCursor(columns)
        if (!callerAllowed(ctx)) return cursor
        cursor.setNotificationUri(ctx.contentResolver, uri)
        val reader = EntryPointAccessors.fromApplication(ctx.applicationContext, Deps::class.java).reader()
        runCatching {
            when (path) {
                PATH_NOW -> reader.now().forEach { cursor.addRow(it.values()) }
                PATH_PROFILE -> cursor.addRow(arrayOf<Any?>(if (reader.kids()) 1 else 0))
            }
        }.onFailure { android.util.Log.w(TAG, "query failed: ${it.javaClass.simpleName}") }
        return cursor
    }

    /** Paquet appelant ET signature : refus journalisé sans aucune donnée (seulement le nom du paquet appelant). */
    private fun callerAllowed(ctx: Context): Boolean {
        val pkg = runCatching { callingPackage }.getOrNull()
        val pm = ctx.packageManager
        val uidPackages = runCatching { pm.getPackagesForUid(Binder.getCallingUid())?.toList() }.getOrNull().orEmpty()
        val digests = if (pkg == KairoAccess.PACKAGE) signerDigests(pm, pkg) else emptyList()
        val ok = KairoAccess.isAllowed(pkg, uidPackages, digests)
        if (!ok) android.util.Log.i(TAG, "caller refused: ${pkg ?: "?"}")
        return ok
    }

    override fun getType(uri: Uri): String = when (uri.pathSegments.firstOrNull()) {
        PATH_PROFILE -> "vnd.android.cursor.item/vnd.ultratv.kairo.profile"
        else -> "vnd.android.cursor.dir/vnd.ultratv.kairo.now"
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("read-only")

    companion object {
        const val TAG = "UltraKairo"
        const val PATH_NOW = "now"
        const val PATH_PROFILE = "profile"
        fun authority(ctx: Context) = "${ctx.packageName}.kairo"
        fun nowUri(ctx: Context): Uri = Uri.parse("content://${authority(ctx)}/$PATH_NOW")
        fun profileUri(ctx: Context): Uri = Uri.parse("content://${authority(ctx)}/$PATH_PROFILE")

        /** Empreintes SHA-256 des signataires ACTUELS du paquet (sans les anciennes clés d'une rotation). */
        @Suppress("DEPRECATION")
        fun signerDigests(pm: PackageManager, pkg: String): List<String> = runCatching {
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            }
            sigs.orEmpty().map { KairoAccess.sha256Hex(it.toByteArray()) }
        }.getOrDefault(emptyList())
    }
}

/**
 * Calcule les lignes à la demande (rien tant que personne ne lit), hors fil principal (fils Binder), borné à 1,5 s et
 * mis en cache 30 s : un accueil Kairo qui revient au premier plan plusieurs fois ne relance pas les requêtes.
 */
@Singleton
class KairoNowReader @Inject constructor(
    private val providers: ProviderRepository,
    private val profiles: ProfileRepository,
    private val favorites: FavoriteDao,
    private val history: WatchHistoryDao,
    private val channels: ChannelDao,
    private val categories: CategoryDao,
    private val epg: EpgDao,
    private val hidden: HiddenCategoriesStore,
    private val locked: LockedChannelsStore,
) {
    private var cached: Pair<Long, List<KairoNowPlan.Row>>? = null

    fun kids(): Boolean = profiles.current.value?.isKids == true

    @Synchronized
    fun now(): List<KairoNowPlan.Row> {
        val t = System.currentTimeMillis()
        cached?.let { (at, rows) -> if (t - at in 0 until CACHE_MS) return rows }
        val rows = runBlocking { withTimeoutOrNull(TIMEOUT_MS) { compute(t) } } ?: return cached?.second.orEmpty()
        cached = t to rows
        return rows
    }

    @Synchronized
    fun invalidate() { cached = null }

    private suspend fun compute(nowMs: Long): List<KairoNowPlan.Row> {
        val ps = providers.observeProviders().first()
        val p = ps.firstOrNull { it.active } ?: ps.firstOrNull() ?: return emptyList()
        val prof = profiles.currentIdNow
        val favIds = favorites.observeForKind(prof, p.id, "LIVE").first().map { it.remoteId }
        val recentIds = history.observeRecentByKind(prof, p.id, "LIVE", 40).first().map { it.remoteId }.distinct()
        val selected = KairoNowPlan.select(ordered(p.id, favIds), ordered(p.id, recentIds), hidden.hidden.first())
        if (selected.isEmpty()) return emptyList()
        val lockedCats = categories.forProviderKind(p.id, "LIVE").filter { it.locked }.map { it.remoteId }.toSet()
        val byChannel = epg.rangeForChannels(selected.map { it.id }, nowMs - 30 * 60_000, nowMs + 6 * 60 * 60_000).groupBy { it.channelId }
        val nowNext = selected.associate { it.id to com.ultratv.tv.nativeapp.ui.common.EpgProgress.currentAndNext(byChannel[it.id].orEmpty(), nowMs) }
        return KairoNowPlan.rows(selected, locked.locked.first(), lockedCats, nowNext, KairoNowPlan.Secrets.of(p.baseUrl, p.username, p.password))
    }

    private suspend fun ordered(pid: Long, ids: List<String>): List<ChannelEntity> {
        if (ids.isEmpty()) return emptyList()
        val byId = channels.byRemoteIds(pid, ids).associateBy { it.remoteId }
        return ids.mapNotNull { byId[it] }
    }

    private companion object {
        const val CACHE_MS = 30_000L
        const val TIMEOUT_MS = 1_500L
    }
}

/**
 * Prévient Kairo (notifyChange) quand favoris, chaînes récentes, verrous, profil ou source changent ; au plus une fois par
 * minute et par URI. Sans effet si Kairo n'est pas installé.
 */
@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KairoNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val providers: ProviderRepository,
    private val profiles: ProfileRepository,
    private val favorites: FavoriteDao,
    private val history: WatchHistoryDao,
    private val hidden: HiddenCategoriesStore,
    private val locked: LockedChannelsStore,
    private val reader: KairoNowReader,
) {
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        val installed = runCatching { ctx.packageManager.getPackageInfo(KairoAccess.PACKAGE, 0) }.isSuccess
        if (!installed) return
        val pid = providers.observeProviders().map { ps -> (ps.firstOrNull { it.active } ?: ps.firstOrNull())?.id }.distinctUntilChanged()
        val signature = combine(profiles.currentId, pid) { prof, p -> prof to p }.distinctUntilChanged().flatMapLatest { (prof, p) ->
            if (p == null) flowOf("none")
            else combine(
                favorites.observeForKind(prof, p, "LIVE"), history.observeRecentByKind(prof, p, "LIVE", 40), hidden.hidden, locked.locked,
            ) { f, h, hid, l -> "$prof|$p|${f.map { it.remoteId }}|${h.map { it.remoteId }.distinct()}|${hid.hashCode()}|${l.hashCode()}" }
        }.distinctUntilChanged()
        // Cache vidé tout de suite à chaque changement ; la notification, elle, attend la minute.
        scope.launch { throttled(signature.drop(1).onEach { reader.invalidate() }) { notify(KairoNowProvider.nowUri(ctx)) } }
        scope.launch {
            val kids = profiles.current.map { it?.isKids == true }.distinctUntilChanged()
            throttled(kids.drop(1)) { notify(KairoNowProvider.profileUri(ctx)) }
        }
    }

    private suspend fun <T> throttled(changes: kotlinx.coroutines.flow.Flow<T>, action: () -> Unit) {
        var last = 0L
        changes.collectLatest {
            delay(WatchNextPlan.waitMs(last, System.currentTimeMillis(), MIN_INTERVAL_MS))
            action()
            last = System.currentTimeMillis()
        }
    }

    private fun notify(uri: Uri) = runCatching { ctx.contentResolver.notifyChange(uri, null) }

    private companion object { const val MIN_INTERVAL_MS = 60_000L }
}
