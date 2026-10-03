package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.EpisodeDao
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.SyncPart
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.sync.SyncPolicy
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.stalker.StalkerClient
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val providerDao: ProviderDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val categoryDao: CategoryDao,
    private val xtream: XtreamClient,
    private val m3u: M3uParser,
    private val stalker: StalkerClient,
    private val parental: ParentalStore,
    private val syncStatus: SyncStatusBus,
    private val xmltv: XmltvParser,
    private val epgDao: com.ultratv.tv.nativeapp.data.db.EpgDao,
    private val db: com.ultratv.tv.nativeapp.data.db.UltraDb,
    private val prefs: UserPreferencesStore,
) {
    private val adultRegex = Regex("xxx|adult|18\\+|porn|ero|adulte|للكبار", RegexOption.IGNORE_CASE)

    // Chunk size for bulk inserts. Room flushes the WAL on each call so very
    // large lists (50k+ channels on big playlists) cause memory + I/O spikes;
    // chunking keeps RAM flat and lets the UI repaint between batches.
    private val INSERT_CHUNK = 500
    private val INSERT_BATCH = 1_000

    private suspend inline fun <T> insertChunked(items: List<T>, crossinline block: suspend (List<T>) -> Unit) {
        if (items.isEmpty()) return
        items.chunked(INSERT_CHUNK).forEach { block(it) }
    }
    fun observeProviders(): Flow<List<ProviderEntity>> = providerDao.observeAll()
    suspend fun firstActive(): ProviderEntity? = providerDao.firstActive()
    suspend fun byId(id: Long): ProviderEntity? = providerDao.byId(id)

    suspend fun addM3u(name: String, url: String): Long {
        providerDao.findByIdentity("M3U", url, "")?.let { return it.id }
        val pid = providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { runCatching { java.net.URI(url).host }.getOrNull() ?: "M3U" },
                kind = "M3U",
                baseUrl = url,
                username = "",
                password = "",
                active = false,    // explicit default is set in Settings; see setDefault
            ),
        )
        return pid
    }

    /**
     * Imports an M3U playlist from raw text (no HTTP fetch). Used by the local
     * file picker — caller reads the URI content via ContentResolver and hands
     * us the bytes. `baseUrl` is just stored as a hint, no fetching ever happens.
     */
    suspend fun addM3uFromText(name: String, label: String, text: String): Long {
        val pid = providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { label.ifBlank { "Local playlist" } },
                kind = "M3U_LOCAL",
                baseUrl = label,        // displayed-only; we never fetch this
                username = "",
                password = "",
                active = false,    // explicit default is set in Settings; see setDefault
            ),
        )
        val res = m3u.parse(text, pid)
        val pinSet = parental.isSet()
        val cats = if (!pinSet) res.categories
        else res.categories.map { it.copy(locked = adultRegex.containsMatchIn(it.name)) }
        categoryDao.deleteForProviderKind(pid, "LIVE")
        categoryDao.upsertAll(cats)
        channelDao.deleteForProvider(pid)
        channelDao.upsertAll(res.channels)
        return pid
    }

    /** Syncs an M3U provider. Channels-only (M3U has no movies/series schema). */
    suspend fun syncM3u(providerId: Long, onProgress: (String) -> Unit = {}): Int {
        val p = providerDao.byId(providerId) ?: return 0
        if (p.kind != "M3U") return 0
        fun step(s: String, pct: Int?) {
            onProgress(s); syncStatus.set(SyncStatusBus.Status(p.name, s, pct))
        }
        try {
            step("Downloading playlist…", 20)
            val res = m3u.fetch(p.baseUrl, p.id)
            step("Parsed ${res.channels.size} channels…", 60)
            val pinSet = parental.isSet()
            val cats = if (!pinSet) res.categories
            else res.categories.map { it.copy(locked = adultRegex.containsMatchIn(it.name)) }
            // Suppression + réinsertion dans UNE transaction : atomique (jamais de catalogue vide
            // si on coupe au milieu) et une seule invalidation Room au lieu d'une par lot.
            db.withTransaction {
                categoryDao.deleteForProviderKind(p.id, "LIVE")
                categoryDao.upsertAll(cats)
                channelDao.deleteForProvider(p.id)
                step("Saving ${res.channels.size} channels…", 90)
                insertChunked(res.channels) { channelDao.upsertAll(it) }
            }
            step("Done — ${res.channels.size} channels", 100)
            return res.channels.size
        } finally {
            syncStatus.clear()
        }
    }

    suspend fun addStalker(name: String, portalUrl: String, mac: String): Long {
        val normalised = portalUrl.trimEnd('/')
        providerDao.findByIdentity("STALKER", normalised, mac.trim())?.let { return it.id }
        return providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { runCatching { java.net.URI(portalUrl).host }.getOrNull() ?: "Stalker" },
                kind = "STALKER",
                baseUrl = portalUrl.trimEnd('/'),
                username = mac.trim(),         // MAC address
                password = "",
                active = false,    // explicit default is set in Settings; see setDefault
            ),
        )
    }

    /** Syncs a Stalker portal. Channels-only — VOD/series are portal-specific add-ons. */
    suspend fun syncStalker(providerId: Long, onProgress: (String) -> Unit = {}): Int {
        val p = providerDao.byId(providerId) ?: return 0
        if (p.kind != "STALKER") return 0
        fun step(s: String, pct: Int?) {
            onProgress(s); syncStatus.set(SyncStatusBus.Status(p.name, s, pct))
        }
        try {
            step("Handshaking with portal…", 5)
            val s = stalker.handshake(p)

            val pinSet = parental.isSet()
            fun maybeLock(cats: List<CategoryEntity>): List<CategoryEntity> =
                if (!pinSet) cats
                else cats.map { it.copy(locked = adultRegex.containsMatchIn(it.name)) }

            step("Fetching live categories…", 10)
            val liveCats = stalker.fetchLiveCategories(p, s).let(::maybeLock)
            step("Fetching live channels…", 25)
            val chans = stalker.fetchLiveChannels(p, s)
            // Suppression + réinsertion dans UNE transaction : atomique (jamais de catalogue vide
            // si on coupe au milieu) et une seule invalidation Room au lieu d'une par lot.
            db.withTransaction {
                categoryDao.deleteForProviderKind(p.id, "LIVE")
                categoryDao.upsertAll(liveCats)
                channelDao.deleteForProvider(p.id)
                step("Saving ${chans.size} channels…", 40)
                insertChunked(chans) { channelDao.upsertAll(it) }
            }

            step("Fetching VOD categories…", 55)
            val vodCats = stalker.fetchVodCategories(p, s).let(::maybeLock)
            step("Fetching VOD…", 65)
            val movies = stalker.fetchVodMovies(p, s)
            // Suppression + réinsertion dans UNE transaction : atomique (jamais de catalogue vide
            // si on coupe au milieu) et une seule invalidation Room au lieu d'une par lot.
            db.withTransaction {
                categoryDao.deleteForProviderKind(p.id, "MOVIE")
                categoryDao.upsertAll(vodCats)
                movieDao.deleteForProvider(p.id)
                step("Saving ${movies.size} movies…", 75)
                insertChunked(movies) { movieDao.upsertAll(it) }
            }

            step("Fetching series categories…", 85)
            val serCats = stalker.fetchSeriesCategories(p, s).let(::maybeLock)
            step("Fetching series…", 90)
            val series = stalker.fetchSeries(p, s)
            // Suppression + réinsertion dans UNE transaction : atomique (jamais de catalogue vide
            // si on coupe au milieu) et une seule invalidation Room au lieu d'une par lot.
            db.withTransaction {
                categoryDao.deleteForProviderKind(p.id, "SERIES")
                categoryDao.upsertAll(serCats)
                seriesDao.deleteForProvider(p.id)
                step("Saving ${series.size} series…", 95)
                insertChunked(series) { seriesDao.upsertAll(it) }
            }

            step("Done — ${chans.size} live · ${movies.size} VOD · ${series.size} series", 100)
            return chans.size + movies.size + series.size
        } finally {
            syncStatus.clear()
        }
    }

    // (Xtream path uses insertChunked(chans) above — see syncAll.)

    /**
     * Pulls the full xmltv feed for a provider and overwrites EPG for all its
     * channels. Channels are matched by [ChannelEntity.epgChannelId] (`tvg-id`
     * for M3U, `epg_channel_id` for Xtream) — channels without that field are
     * silently skipped (the older per-channel `get_short_epg` path still works
     * for those).
     */
    suspend fun syncXmltv(providerId: Long, onProgress: (String) -> Unit = {}): Int {
        val p = providerDao.byId(providerId) ?: return 0
        // Local M3U can't fetch xmltv. Stalker has its own EPG path (TODO).
        if (p.kind == "M3U_LOCAL" || p.kind == "STALKER") return 0
        fun step(s: String, pct: Int?) {
            onProgress(s); syncStatus.set(SyncStatusBus.Status(p.name, s, pct))
        }
        try {
            step("Fetching xmltv…", 10)
            val total = syncXmltvInternal(p) { c -> step("EPG: $c programmes", null) }
            providerDao.markSynced(p.id, SyncPart.EPG, System.currentTimeMillis())
            step("Done — $total programmes", 100)
            return total
        } catch (t: Throwable) {
            syncStatus.set(SyncStatusBus.Status(p.name, "EPG fetch failed: ${t.message}", null))
            return 0
        } finally {
            syncStatus.clear()
        }
    }

    /** Télécharge le XMLTV en flux (fenêtre −2 h / +24 h) et l'insère par lots. Les erreurs remontent. */
    private suspend fun syncXmltvInternal(p: ProviderEntity, onCount: (Int) -> Unit): Int {
        // Build (xmltv channel id → local channel id) map for matching.
        val map = channelDao.epgMapping(p.id).associate { it.epgChannelId to it.id }
        if (map.isEmpty()) return 0
        var total = 0
        xmltv.withProgrammes(p, map) { seq ->
            db.withTransaction {
                epgDao.deleteForProvider(p.id)
                for (batch in seq.chunked(INSERT_BATCH)) {
                    epgDao.upsertAll(batch)
                    total += batch.size
                    onCount(total)
                }
            }
        }
        return total
    }

    /**
     * Resolves a stored stream URL into a playable URL. Only does work for
     * Stalker providers (URLs prefixed `stalker://`) — for everything else the
     * URL is already directly playable and we return it unchanged.
     */
    suspend fun resolvePlayUrl(channelId: Long, storedUrl: String): String {
        if (!storedUrl.startsWith("stalker://")) return storedUrl
        val ch = channelDao.byId(channelId) ?: return storedUrl
        val p = providerDao.byId(ch.providerId) ?: return storedUrl
        return runCatching { stalker.resolvePlayUrl(p, storedUrl) }.getOrElse { storedUrl }
    }

    /**
     * Same as [resolvePlayUrl] but indexed by providerId — used for movies /
     * episodes where we don't have a channel row to look up the provider on.
     */
    suspend fun resolveStalkerUrl(providerId: Long, storedUrl: String): String {
        if (!storedUrl.startsWith("stalker://")) return storedUrl
        val p = providerDao.byId(providerId) ?: return storedUrl
        return runCatching { stalker.resolvePlayUrl(p, storedUrl) }.getOrElse { storedUrl }
    }

    suspend fun addXtream(name: String, baseUrl: String, username: String, password: String): Long {
        val normalised = baseUrl.trimEnd('/')
        // Idempotent: if the (kind, baseUrl, username) tuple already exists,
        // reuse its id rather than creating a duplicate row. Callers that
        // sync after add() will simply re-pull catalogs into the same record.
        providerDao.findByIdentity("XTREAM", normalised, username)?.let { return it.id }
        return providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { runCatching { java.net.URI(normalised).host }.getOrNull() ?: "Xtream" },
                kind = "XTREAM",
                baseUrl = normalised,
                username = username,
                password = password,
                active = false,    // explicit default is set in Settings; see setDefault
            ),
        )
    }

    /**
     * Marks one provider as the default and deactivates the others. Every
     * screen that picks "the current provider" uses `firstOrNull { it.active }`,
     * so setting default = id atomically switches the whole app to that
     * provider's catalog.
     */
    suspend fun setDefault(id: Long) {
        providerDao.deactivateAll()
        providerDao.activate(id)
    }

    suspend fun delete(id: Long) {
        channelDao.deleteForProvider(id)
        movieDao.deleteForProvider(id)
        seriesDao.deleteForProvider(id)
        categoryDao.deleteForProviderKind(id, "LIVE")
        categoryDao.deleteForProviderKind(id, "MOVIE")
        categoryDao.deleteForProviderKind(id, "SERIES")
        providerDao.delete(id)
    }

    /**
     * Synchronise le catalogue de façon INCRÉMENTALE : seules les parties périmées (TTL, voir
     * [SyncPolicy]) ou vides sont rechargées, le direct en premier (commité avant de toucher
     * aux VOD, le temps que l'accueil et le Direct soient utilisables). [force] ignore les TTL.
     * Renvoie le nombre d'éléments écrits.
     */
    suspend fun syncAll(providerId: Long, onProgress: (String) -> Unit = {}, force: Boolean = false): Int {
        val name = providerDao.byId(providerId)?.name ?: return 0
        // La synchro (minutes, des dizaines de Mo) tourne dans un scope propre au dépôt :
        // quitter l'écran qui l'a lancée (ViewModel détruit) ne l'annule plus.
        return syncScope.async {
            syncMutex.withLock {
                try {
                    syncAllInternal(providerId, onProgress, force).also { syncStatus.clearFailure(providerId) }
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    syncStatus.fail(SyncStatusBus.Failure(providerId, name, com.ultratv.tv.nativeapp.data.net.NetErrors.classify(t)))
                    throw t
                }
            }
        }.await()
    }

    private val syncScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val syncMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun syncAllInternal(providerId: Long, onProgress: (String) -> Unit, force: Boolean): Int {
        val p = providerDao.byId(providerId) ?: return 0
        // Local M3U is parsed once at import — re-syncing requires picking the file again.
        if (p.kind == "M3U_LOCAL") return channelDao.count(p.id)
        val now = System.currentTimeMillis()
        val ttl = SyncPolicy.ttl(prefs.flow.first().syncIntervalHours)
        val due = SyncPolicy.dueParts(p, now, ttl, channelDao.count(p.id), force)
        if (due.isEmpty()) return 0

        if (p.kind == "M3U") return syncM3u(providerId, onProgress).also { providerDao.markSynced(p.id, SyncPart.LIVE, System.currentTimeMillis()) }
        if (p.kind == "STALKER") return syncStalker(providerId, onProgress).also {
            val t = System.currentTimeMillis()
            for (part in listOf(SyncPart.LIVE, SyncPart.VOD, SyncPart.SERIES)) providerDao.markSynced(p.id, part, t)
        }

        // Pondération de la barre de progression : seules les parties dues comptent.
        val weights = mapOf(SyncPart.LIVE to 20, SyncPart.VOD to 35, SyncPart.SERIES to 20, SyncPart.EPG to 25)
        val total = due.sumOf { weights.getValue(it) }.toFloat()
        var doneBefore = 0
        fun pct(part: SyncPart, fraction: Float): Int =
            ((doneBefore + weights.getValue(part) * fraction.coerceIn(0f, 1f)) / total * 100).toInt().coerceIn(0, 99)

        fun step(part: SyncPart, label: String, count: Int?, fraction: Float) {
            onProgress(label)
            syncStatus.set(SyncStatusBus.Status(provider = p.name, step = label, percent = pct(part, fraction), part = part, count = count))
        }

        val pinSet = parental.isSet()
        fun maybeLock(cats: List<CategoryEntity>): List<CategoryEntity> =
            if (!pinSet) cats
            else cats.map { it.copy(locked = adultRegex.containsMatchIn(it.name)) }

        var written = 0
        try {
            for (part in due) {
                when (part) {
                    SyncPart.LIVE -> {
                        step(part, "Live categories…", null, 0f)
                        val liveCats = xtream.fetchLiveCategories(p).let(::maybeLock)
                        var n = 0
                        xtream.withLiveStreams(p) { seq ->
                            // La connexion est déjà ouverte : un serveur injoignable a échoué plus haut,
                            // avant toute suppression. Suppression + insertion = UNE transaction.
                            db.withTransaction {
                                categoryDao.deleteForProviderKind(p.id, "LIVE")
                                categoryDao.upsertAll(liveCats)
                                channelDao.deleteForProvider(p.id)
                                for (batch in seq.chunked(INSERT_BATCH)) {
                                    channelDao.upsertAll(batch)
                                    n += batch.size
                                    step(part, "Live channels: $n", n, n / 60_000f)
                                }
                            }
                        }
                        written += n
                    }
                    SyncPart.VOD -> {
                        step(part, "Movie categories…", null, 0f)
                        val cats = xtream.fetchVodCategories(p).let(::maybeLock)
                        var n = 0
                        xtream.withVodStreams(p) { seq ->
                            db.withTransaction {
                                categoryDao.deleteForProviderKind(p.id, "MOVIE")
                                categoryDao.upsertAll(cats)
                                movieDao.deleteForProvider(p.id)
                                for (batch in seq.chunked(INSERT_BATCH)) {
                                    movieDao.upsertAll(batch)
                                    n += batch.size
                                    step(part, "Movies: $n", n, n / 200_000f)
                                }
                            }
                        }
                        written += n
                    }
                    SyncPart.SERIES -> {
                        step(part, "Series categories…", null, 0f)
                        val cats = xtream.fetchSeriesCategories(p).let(::maybeLock)
                        var n = 0
                        xtream.withSeries(p) { seq ->
                            db.withTransaction {
                                categoryDao.deleteForProviderKind(p.id, "SERIES")
                                categoryDao.upsertAll(cats)
                                seriesDao.deleteForProvider(p.id)
                                for (batch in seq.chunked(INSERT_BATCH)) {
                                    seriesDao.upsertAll(batch)
                                    n += batch.size
                                    step(part, "Series: $n", n, n / 60_000f)
                                }
                            }
                        }
                        written += n
                    }
                    SyncPart.EPG -> {
                        // Le guide ne doit jamais faire échouer le reste : erreur isolée, réessayée au TTL suivant.
                        val ok = runCatching { syncXmltvInternal(p) { c -> step(part, "EPG: $c", c, c / 400_000f) } }
                        if (ok.isFailure) { doneBefore += weights.getValue(part); continue }
                    }
                }
                providerDao.markSynced(p.id, part, System.currentTimeMillis())
                doneBefore += weights.getValue(part)
            }
            syncStatus.set(SyncStatusBus.Status(p.name, "Done", 100))
            return written
        } finally {
            syncStatus.clear()
        }
    }
}
