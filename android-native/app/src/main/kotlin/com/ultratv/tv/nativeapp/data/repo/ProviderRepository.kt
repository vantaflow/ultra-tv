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
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
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
    private val parental: ParentalStore,
    private val syncStatus: SyncStatusBus,
    private val xmltv: XmltvParser,
    private val epgDao: com.ultratv.tv.nativeapp.data.db.EpgDao,
    private val db: com.ultratv.tv.nativeapp.data.db.UltraDb,
    private val prefs: UserPreferencesStore,
    private val adaptive: com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile,
    private val limits: com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore,
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
        // Adresse get.php / player_api.php avec identifiants : c'est une source Xtream Codes (get.php est souvent bloqué).
        com.ultratv.tv.nativeapp.data.net.XtreamUrl.parse(url)?.let { return addXtream(name.ifBlank { "Xtream" }, it.server, it.username, it.password) }
        providerDao.findByIdentity("M3U", url, "")?.let { return it.id }
        val pid = providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { "M3U" },
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
        // Local M3U can't fetch xmltv ; seuls les Xtream ont un guide XMLTV.
        if (p.kind != "XTREAM") return 0
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
            syncStatus.set(SyncStatusBus.Status(p.name, "EPG fetch failed: ${com.ultratv.tv.nativeapp.ui.common.UserText.safe(t.message.orEmpty())}", null))
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

    suspend fun addXtream(name: String, baseUrl: String, username: String, password: String): Long {
        val normalised = baseUrl.trimEnd('/')
        // Idempotent: if the (kind, baseUrl, username) tuple already exists,
        // reuse its id rather than creating a duplicate row. Callers that
        // sync after add() will simply re-pull catalogs into the same record.
        providerDao.findByIdentity("XTREAM", normalised, username)?.let { return it.id }
        return providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { "Xtream" },
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
    /**
     * Met à jour une source reçue du cloud (le cloud gagne pour l'adresse et les identifiants). Les préférences locales
     * (source par défaut, favoris, langues) sont conservées ; le catalogue est relu à la prochaine synchro.
     */
    suspend fun updateFromCloud(id: Long, name: String?, url: String, username: String, password: String) {
        val p = providerDao.byId(id) ?: return
        providerDao.upsert(p.copy(name = name ?: p.name, baseUrl = url.trim().trimEnd('/'), username = username, password = password))
        providerDao.resetSyncAt(id)
    }

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

    /** Une source M3U dont l'adresse est de type Xtream (get.php…) ? Alors elle peut passer en Xtream Codes. */
    suspend fun canConvertToXtream(providerId: Long): Boolean {
        val p = providerDao.byId(providerId) ?: return false
        return p.kind == "M3U" && com.ultratv.tv.nativeapp.data.net.XtreamUrl.parse(p.baseUrl) != null
    }

    /** Migration de données (sans changement de schéma) : M3U « get.php » → Xtream Codes ; les dates de synchro repartent à zéro. */
    suspend fun convertToXtream(providerId: Long): Boolean {
        val p = providerDao.byId(providerId) ?: return false
        providerDao.upsert(com.ultratv.tv.nativeapp.data.net.XtreamUrl.convert(p) ?: return false)
        return true
    }

    private suspend fun syncAllInternal(providerId: Long, onProgress: (String) -> Unit, force: Boolean): Int {
        // Source M3U enregistrée avec une adresse Xtream : conversion automatique au prochain lancement / « Réessayer ».
        if (canConvertToXtream(providerId)) convertToXtream(providerId)
        // Source Xtream abîmée par une ancienne fusion cloud (adresse get.php, identifiants vides) : on la reconstruit.
        providerDao.byId(providerId)?.let { b ->
            if (b.kind == "XTREAM") com.ultratv.tv.nativeapp.data.net.XtreamUrl.parse(b.baseUrl)?.let { c ->
                providerDao.upsert(b.copy(baseUrl = c.server.trimEnd('/'), username = c.username, password = c.password))
            }
        }
        val p = providerDao.byId(providerId) ?: return 0
        // Local M3U is parsed once at import — re-syncing requires picking the file again.
        if (p.kind == "M3U_LOCAL") return channelDao.count(p.id)
        val now = System.currentTimeMillis()
        val u = prefs.flow.first()
        val a = adaptive.state.value
        val ttl = SyncPolicy.ttl(if (u.syncMode == "launch") 1 else u.syncIntervalHours)
        val enabledParts = buildSet { if (u.syncLive) add(SyncPart.LIVE); if (u.syncEpg) add(SyncPart.EPG); if (u.syncVod) add(SyncPart.VOD); if (u.syncSeries) add(SyncPart.SERIES) }
        val heavyOk = !(u.syncUnmeteredOnly && a.net.metered)
        val due = SyncPolicy.dueParts(p, now, ttl, channelDao.count(p.id), force || u.syncMode == "launch", enabledParts, heavyOk)
        if (due.isEmpty()) return 0

        if (p.kind == "M3U") return syncM3u(providerId, onProgress).also { providerDao.markSynced(p.id, SyncPart.LIVE, System.currentTimeMillis()) }
        // Les types hérités (ex. Stalker, retiré en 1.1.1) ne sont plus synchronisés : SyncPolicy ne renvoie aucune partie due.

        // Connexions simultanées autorisées (user_info) : alimente les avertissements enregistrement/lecture et le timeshift.
        xtream.fetchMaxConnections(p)?.let { limits.setMaxConnections(p.id, it) }

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
                    SyncPart.LIVE -> written += syncXtreamPart(p, livePart(), ::maybeLock) { c, n -> step(part, "Live channels: $n", n, c) }
                    SyncPart.VOD -> written += syncXtreamPart(p, vodPart(), ::maybeLock) { c, n -> step(part, "Movies: $n", n, c) }
                    SyncPart.SERIES -> written += syncXtreamPart(p, seriesPart(), ::maybeLock) { c, n -> step(part, "Series: $n", n, c) }
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

    // ───────────── Téléchargement par catégorie active (stratégie adaptative) ─────────────

    /** Description d'une partie du catalogue Xtream (direct / films / séries) pour le moteur de synchro générique. */
    private class PartSpec<T : Any>(
        val kind: String,
        val fetchCats: suspend (ProviderEntity) -> List<CategoryEntity>,
        val streamAll: suspend (ProviderEntity, suspend (Sequence<T>) -> Unit) -> Unit,
        val fetchOne: suspend (ProviderEntity, String) -> List<T>,
        val catOf: (T) -> String?,
        val withLang: (T, String) -> T,
        val deleteAll: suspend (Long) -> Unit,
        val deleteCats: suspend (Long, List<String>) -> Unit,
        val deleteCat: suspend (Long, String) -> Unit,
        val insert: suspend (List<T>) -> Unit,
        val postBatch: (List<T>) -> List<T> = { it },
    )

    private fun livePart() = PartSpec<com.ultratv.tv.nativeapp.data.db.ChannelEntity>(
        "LIVE", { xtream.fetchLiveCategories(it) }, { p, blk -> xtream.withLiveStreams(p, blk) }, { p, c -> xtream.liveOfCategory(p, c) },
        { it.categoryId }, { c, lang -> c.copy(lang = LanguageDetector.forItem(c.name, lang)) },
        { channelDao.deleteForProvider(it) }, { pid, ids -> channelDao.deleteForCategories(pid, ids) }, { pid, id -> channelDao.deleteForCategory(pid, id) },
        { channelDao.upsertAll(it) },
    )

    private fun vodPart() = PartSpec<com.ultratv.tv.nativeapp.data.db.MovieEntity>(
        "MOVIE", { xtream.fetchVodCategories(it) }, { p, blk -> xtream.withVodStreams(p, blk) }, { p, c -> xtream.vodOfCategory(p, c) },
        { it.categoryId }, { m, lang -> m.copy(lang = LanguageDetector.forItem(m.name, lang)) },
        { movieDao.deleteForProvider(it) }, { pid, ids -> movieDao.deleteForCategories(pid, ids) }, { pid, id -> movieDao.deleteForCategory(pid, id) },
        { movieDao.upsertAll(it) },
    )

    private fun seriesPart() = PartSpec<com.ultratv.tv.nativeapp.data.db.SeriesEntity>(
        "SERIES", { xtream.fetchSeriesCategories(it) }, { p, blk -> xtream.withSeries(p, blk) }, { p, c -> xtream.seriesOfCategory(p, c) },
        { it.categoryId }, { s2, lang -> s2.copy(lang = LanguageDetector.forItem(s2.name, lang)) },
        { seriesDao.deleteForProvider(it) }, { pid, ids -> seriesDao.deleteForCategories(pid, ids) }, { pid, id -> seriesDao.deleteForCategory(pid, id) },
        { seriesDao.upsertAll(it) },
    )

    /**
     * Synchronise une partie du catalogue en ne téléchargeant que les catégories ACTIVES.
     * Les listes de catégories (légères) sont toujours rechargées et fusionnées avec l'état de l'utilisateur ;
     * les éléments des catégories désactivées sont purgés. Stratégie : voir [CatalogPlan].
     * [progress] reçoit (fraction 0..1, nombre d'éléments déjà écrits).
     */
    private suspend fun <T : Any> syncXtreamPart(
        p: ProviderEntity,
        spec: PartSpec<T>,
        lock: (List<CategoryEntity>) -> List<CategoryEntity>,
        progress: (Float, Int) -> Unit,
    ): Int = partLock(spec.kind).withLock { syncXtreamPartLocked(p, spec, lock, progress) }

    /**
     * Un verrou PAR PARTIE (direct, films, séries) en plus du verrou global : activer une catégorie du Direct
     * pendant que la synchro complète traite les films la charge tout de suite, au lieu d'attendre la fin
     * de toute la synchro (plusieurs minutes sur une grosse source → « Chargement… » interminable).
     */
    private val partLocks = mapOf("LIVE" to kotlinx.coroutines.sync.Mutex(), "MOVIE" to kotlinx.coroutines.sync.Mutex(), "SERIES" to kotlinx.coroutines.sync.Mutex())
    private fun partLock(kind: String) = partLocks[kind] ?: partLocks.getValue("SERIES")

    private suspend fun <T : Any> syncXtreamPartLocked(
        p: ProviderEntity,
        spec: PartSpec<T>,
        lock: (List<CategoryEntity>) -> List<CategoryEntity>,
        progress: (Float, Int) -> Unit,
    ): Int {
        val u = prefs.flow.first()
        val langs = u.languages.split(',').filter { it.isNotBlank() }.toSet()
        val fetched = lock(spec.fetchCats(p))
        val merged = CatalogPlan.merge(fetched, categoryDao.forProviderKind(p.id, spec.kind), langs, u.includeMulti, u.includeUnknownLang)
        val enabledIds = merged.filter { it.enabled }.map { it.remoteId }
        val disabledIds = merged.filter { !it.enabled }.map { it.remoteId }
        val langByCat = merged.associate { it.remoteId to it.lang }
        val enabledSet = enabledIds.toHashSet()
        db.withTransaction {
            categoryDao.deleteForProviderKind(p.id, spec.kind)
            categoryDao.upsertAll(merged)
            // SQLite plafonne les paramètres à 999 : purge par paquets.
            disabledIds.chunked(500).forEach { spec.deleteCats(p.id, it) }
        }
        var strategy = CatalogPlan.strategy(merged.size, enabledIds.size, p.categoryFilter)
        var written = 0
        val tier = adaptive.state.value.device.tier

        if (strategy == SyncStrategy.PER_CATEGORY) {
            // Vérifie au PREMIER appel que le serveur respecte `category_id` (sinon repli sur la requête globale).
            var verified = p.categoryFilter == 1
            var idx = 0
            val queue = enabledIds.toMutableList()
            if (!verified) {
                while (queue.isNotEmpty() && !verified && strategy == SyncStrategy.PER_CATEGORY) {
                    val id = queue.removeAt(0); idx++
                    val items = fetchWithBackoff { spec.fetchOne(p, id) }
                    if (items.isEmpty()) { db.withTransaction { spec.deleteCat(p.id, id) }; continue }       // vide : ne prouve rien, on essaie la suivante
                    if (items.any { spec.catOf(it) != null && spec.catOf(it) != id }) {
                        providerDao.setCategoryFilter(p.id, 0); strategy = SyncStrategy.GLOBAL_FILTERED
                    } else {
                        providerDao.setCategoryFilter(p.id, 1); verified = true
                        written += insertCategory(p, spec, id, items, langByCat)
                    }
                }
            }
            if (strategy == SyncStrategy.PER_CATEGORY) {
                val total = (idx + queue.size).coerceAtLeast(1)
                val done = java.util.concurrent.atomic.AtomicInteger(idx)
                val count = java.util.concurrent.atomic.AtomicInteger(written)
                val gate = kotlinx.coroutines.sync.Semaphore(CatalogPlan.parallelism(tier))
                kotlinx.coroutines.coroutineScope {
                    queue.map { id ->
                        async {
                            gate.withPermit {
                                val items = fetchWithBackoff { spec.fetchOne(p, id) }
                                val n = insertCategory(p, spec, id, items, langByCat)
                                progress(done.incrementAndGet().toFloat() / total, count.addAndGet(n))
                            }
                        }
                    }.forEach { it.await() }
                }
                return count.get()
            }
        }

        // Requête globale en flux ; les éléments des catégories désactivées ne sont pas stockés.
        val all = strategy == SyncStrategy.GLOBAL
        var n = 0
        spec.streamAll(p) { seq ->
            db.withTransaction {
                spec.deleteAll(p.id)
                val seqState = ChannelSeq()
                for (batch in seq.filter { all || spec.catOf(it) == null || spec.catOf(it) in enabledSet }
                    .map { spec.withLang(it, langByCat[spec.catOf(it)].orEmpty()) }.chunked(adaptive.state.value.auto.insertBatch)) {
                    val rows = @Suppress("UNCHECKED_CAST") (if (batch.firstOrNull() is com.ultratv.tv.nativeapp.data.db.ChannelEntity) seqState.assign(batch as List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>) as List<T> else batch)
                    spec.insert(rows)
                    n += rows.size
                    progress((n / 60_000f).coerceAtMost(0.95f), n)
                }
            }
        }
        return n
    }

    private suspend fun <T : Any> insertCategory(p: ProviderEntity, spec: PartSpec<T>, id: String, items: List<T>, langByCat: Map<String, String>): Int {
        val withLang = items.map { spec.withLang(it, langByCat[id].orEmpty()) }
        val rows = @Suppress("UNCHECKED_CAST") (if (withLang.firstOrNull() is com.ultratv.tv.nativeapp.data.db.ChannelEntity) ChannelSeq().assign(withLang as List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>) as List<T> else withLang)
        db.withTransaction { spec.deleteCat(p.id, id); rows.chunked(1_000).forEach { spec.insert(it) } }
        return rows.size
    }

    /** Respecte le serveur : nouvelle tentative avec délai croissant sur 429 / 5xx / coupure. */
    private suspend fun <R> fetchWithBackoff(block: suspend () -> R): R {
        var delayMs = 1_000L
        repeat(2) {
            try { return block() }
            catch (e: com.ultratv.tv.nativeapp.data.net.HttpStatusException) { if (e.code != 429 && e.code < 500) throw e }
            catch (e: java.io.IOException) { /* réessaie */ }
            kotlinx.coroutines.delay(delayMs); delayMs *= 2
        }
        return block()
    }

    /**
     * Télécharge uniquement les catégories données (réactivation d'une catégorie) : une requête ciblée par catégorie
     * si le serveur filtre, sinon une synchro globale de la partie concernée.
     */
    suspend fun syncCategoriesNow(providerId: Long, kind: String, ids: List<String>) {
        val p = providerDao.byId(providerId) ?: return
        if (p.kind != "XTREAM" || ids.isEmpty()) return
        val part = when (kind) { "LIVE" -> SyncPart.LIVE; "MOVIE" -> SyncPart.VOD; else -> SyncPart.SERIES }
        // Serveur qui ignore `category_id` : pas de chargement ciblé possible, synchro complète (verrou global).
        if (p.categoryFilter == 0) { syncAll(providerId, force = true); return }
        // Synchro complète en cours : on ne touche pas à son indicateur de progression (ni ne l'efface).
        val ownStatus = !syncMutex.isLocked
        partLock(kind).withLock {
            if (ownStatus) syncStatus.set(SyncStatusBus.Status(p.name, "Categories", 0, part, 0))
            try {
                var n = 0
                val cats = categoryDao.forProviderKind(p.id, kind).associateBy { it.remoteId }
                val langOf: (String) -> String = { cats[it]?.lang.orEmpty() }
                for ((i, id) in ids.withIndex()) {
                    n += when (kind) {
                        "LIVE" -> { val sp = livePart(); insertCategory(p, sp, id, fetchWithBackoff { sp.fetchOne(p, id) }, mapOf(id to langOf(id))) }
                        "MOVIE" -> { val sp = vodPart(); insertCategory(p, sp, id, fetchWithBackoff { sp.fetchOne(p, id) }, mapOf(id to langOf(id))) }
                        else -> { val sp = seriesPart(); insertCategory(p, sp, id, fetchWithBackoff { sp.fetchOne(p, id) }, mapOf(id to langOf(id))) }
                    }
                    if (ownStatus) syncStatus.set(SyncStatusBus.Status(p.name, "Categories", ((i + 1) * 100) / ids.size, part, n))
                }
            } finally { if (ownStatus) syncStatus.clear() }
        }
    }

    /** Langues détectées dans les catégories du serveur (3 requêtes légères, aucun contenu téléchargé). */
    suspend fun previewLanguages(providerId: Long): List<Pair<String, Int>> {
        val p = providerDao.byId(providerId) ?: return emptyList()
        if (p.kind != "XTREAM") return emptyList()
        val all = runCatching { fetchWithBackoff { xtream.fetchLiveCategories(p) } + fetchWithBackoff { xtream.fetchVodCategories(p) } + fetchWithBackoff { xtream.fetchSeriesCategories(p) } }.getOrDefault(emptyList())
        return all.map { LanguageDetector.forCategory(it.name) }
            .filter { it.isNotEmpty() && it != LanguageDetector.MULTI }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
