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
import com.ultratv.tv.nativeapp.data.xmltv.EmptyGuideException
import com.ultratv.tv.nativeapp.data.xmltv.EpgErrors
import com.ultratv.tv.nativeapp.data.xmltv.EpgProgress
import com.ultratv.tv.nativeapp.data.xmltv.EpgState
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val stalker: com.ultratv.tv.nativeapp.data.stalker.StalkerClient,
    private val langDisabled: com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore,
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
    /**
     * Lecture EN AVANCE : l'analyse (JSON / XML) et la conversion des lignes tournent dans un coroutine d'arrière-plan, qui
     * prépare les lots (au plus 2 d'avance) pendant que l'appelant écrit le lot précédent dans Room. Avant, analyse et
     * écriture s'enchaînaient sur le même thread (la somme des deux) ; elles se chevauchent désormais sur deux cœurs.
     * Une erreur d'analyse remonte chez l'appelant ; si l'appelant échoue, la lecture est annulée.
     */
    private suspend fun <T> Sequence<T>.forEachBatchAhead(size: Int, consume: suspend (List<T>) -> Unit) {
        val batches = kotlinx.coroutines.channels.Channel<List<T>>(2)
        kotlinx.coroutines.coroutineScope {
            launch(kotlinx.coroutines.Dispatchers.IO) {
                try { com.ultratv.tv.nativeapp.data.sync.SyncThread.background { for (b in this@forEachBatchAhead.chunked(size)) batches.send(b) }; batches.close() } catch (t: Throwable) { batches.close(t) }
            }
            for (b in batches) consume(b)
        }
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
        limits.setEpgUrls(pid, res.epgUrls)
        val pinSet = parental.isSet()
        val cats = if (!pinSet) res.categories
        else res.categories.map { it.copy(locked = adultRegex.containsMatchIn(it.name)) }
        categoryDao.deleteForProviderKind(pid, "LIVE")
        categoryDao.upsertAll(cats)
        channelDao.deleteForProvider(pid)
        // Rang de catégorie lu APRÈS l'écriture des catégories : les non placées se départagent par leur nouvel id.
        channelDao.upsertAll(LiveOrder.ranks(categoryDao.forProviderKind(pid, LiveOrder.KIND)).stamp(res.channels))
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
            limits.setEpgUrls(p.id, res.epgUrls)   // url-tvg / x-tvg-url de l'en-tête : guide de cette playlist
            val pinSet = parental.isSet()
            val locked = if (!pinSet) res.categories
            else res.categories.map { it.copy(locked = adultRegex.containsMatchIn(it.name)) }
            // Langues masquées : catégories désactivées (état de l'utilisateur conservé) et titres sautés avant l'écriture.
            val u = prefs.flow.first()
            val before = categoryDao.forProviderKind(p.id, "LIVE")
            val cats = CatalogPlan.merge(locked, before, LangSelection.of(u.languages, u.includeMulti, u.includeUnknownLang).selected, u.includeMulti, u.includeUnknownLang)
            noteLangDisabled(p.id, "LIVE", cats, before)
            val enabledCats = cats.filter { it.enabled }.map { it.remoteId }.toHashSet()
            val langByCat = cats.associate { it.remoteId to it.lang }
            val channels = res.channels
                .filter { it.categoryId == null || it.categoryId in enabledCats }
                .map { c -> c.copy(lang = LanguageDetector.forItem(c.name, c.categoryId?.let { langByCat[it] }.orEmpty())) }
            // Suppression + réinsertion dans UNE transaction : atomique (jamais de catalogue vide
            // si on coupe au milieu) et une seule invalidation Room au lieu d'une par lot.
            db.withTransaction {
                categoryDao.deleteForProviderKind(p.id, "LIVE")
                categoryDao.upsertAll(cats)
                channelDao.deleteForProvider(p.id)
                step("Saving ${channels.size} channels…", 90)
                val ranks = LiveOrder.ranks(categoryDao.forProviderKind(p.id, LiveOrder.KIND))
                insertChunked(channels) { channelDao.upsertAll(ranks.stamp(it)) }
            }
            step("Done — ${channels.size} channels", 100)
            return channels.size
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
        // Xtream : xmltv.php. Playlists M3U (distantes ou locales) : seulement si leur en-tête annonce un guide (url-tvg).
        if (!hasGuideFeed(p)) return 0
        fun step(s: String, pct: Int?) {
            onProgress(s); syncStatus.set(SyncStatusBus.Status(p.name, s, pct))
        }
        try {
            step("Fetching xmltv…", 10)
            val total = syncXmltvInternal(p) { pr -> step("EPG: ${pr.programmes} programmes", pr.percent) }
            providerDao.markSynced(p.id, SyncPart.EPG, System.currentTimeMillis())
            purgePastEpg()
            step("Done — $total programmes", 100)
            return total
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            // Le détail est dans les journaux (nettoyés) et dans l'état du guide ; jamais de message brut (adresse, identifiants).
            syncStatus.set(SyncStatusBus.Status(p.name, "EPG fetch failed", null))
            return 0
        } finally {
            syncStatus.clear()
        }
    }

    private fun hasGuideFeed(p: ProviderEntity): Boolean = when (p.kind) {
        "XTREAM" -> true
        "M3U", "M3U_LOCAL" -> limits.epgUrls(p.id).isNotEmpty()
        else -> false
    }

    /**
     * Guide de la source PUIS guide complémentaire (chaînes restées sans programme). Le complément est tenté même si
     * le guide de la source échoue ; l'erreur de la source ne remonte que si rien n'a pu être ajouté.
     * Publie l'état du guide (chargement + progression, échec + raison) sur le [SyncStatusBus] : l'écran de première
     * synchro et le guide ne restent jamais sur un chargement sans fin. Le détail technique part dans les journaux nettoyés.
     */
    private suspend fun syncXmltvInternal(p: ProviderEntity, onProgress: (EpgProgress) -> Unit): Int {
        var bytes = 0L; var totalBytes = -1L; var count = 0; var lastEmit = 0L
        fun emit(force: Boolean = false) {
            val now = System.nanoTime()
            if (!force && now - lastEmit < 250_000_000L) return
            lastEmit = now
            val pr = EpgProgress(bytes, totalBytes, count)
            syncStatus.setEpg(EpgState.Loading(p.id, pr))
            onProgress(pr)
        }
        val report = { b: Long?, t: Long?, c: Int? -> if (b != null) bytes = b; if (t != null) totalBytes = t; if (c != null) count = c; emit() }
        syncStatus.setEpg(EpgState.Loading(p.id, EpgProgress(0, -1, 0)))
        try {
            val own = runCatching { if (p.kind == "XTREAM") syncProviderXmltv(p, report) else syncTvgFeeds(p, report) }
            // Le complément ne compte que le programme de la source (pas de barre d'octets propre).
            val extra = runCatching { syncExtraEpg(p) { c -> report(null, null, (own.getOrNull() ?: 0) + c) } }.getOrDefault(0)
            val total = (own.getOrNull() ?: 0) + extra
            emit(force = true)
            if (own.isFailure && extra == 0) throw own.exceptionOrNull()!!
            if (total == 0) throw EmptyGuideException()
            syncStatus.setEpg(EpgState.Idle)
            return total
        } catch (c: kotlinx.coroutines.CancellationException) {
            syncStatus.setEpg(EpgState.Idle)
            throw c
        } catch (t: Throwable) {
            val attempts = (t as? XmltvParser.FetchException)?.attempts ?: 1
            syncStatus.setEpg(EpgState.Failed(p.id, com.ultratv.tv.nativeapp.data.net.NetErrors.classify(t), EpgErrors.reason(t)))
            com.ultratv.tv.nativeapp.RemoteLog.warn("EPG", EpgErrors.logLine(t, attempts))
            throw t
        }
    }

    /** Écrit un lot de programmes en UNE transaction (commitée tout de suite : le guide l'affiche au fil de l'eau). */
    private suspend fun writeEpgBatch(batch: List<com.ultratv.tv.nativeapp.data.db.EpgEntity>, cleared: MutableSet<Long>) {
        // Les anciens programmes d'une chaîne sont supprimés au premier lot qui la contient (et non plus tout le guide d'un
        // coup au départ) : le guide déjà chargé reste visible pendant la mise à jour.
        val fresh = batch.asSequence().map { it.channelId }.distinct().filter { cleared.add(it) }.toList()
        db.withTransaction {
            fresh.chunked(500).forEach { epgDao.deleteForChannels(it) }
            epgDao.upsertAll(batch)
        }
    }

    /** Guide complémentaire (Réglages › Synchronisation) pour les chaînes sans programme ; 0 si désactivé. */
    private suspend fun syncExtraEpg(p: ProviderEntity, onCount: (Int) -> Unit): Int {
        val url = prefs.flow.first().extraEpg.takeIf { it.startsWith("https://") } ?: return 0
        val have = epgDao.channelsWithProgrammes(p.id, System.currentTimeMillis()).toHashSet()
        val targets = channelDao.epgTitles(p.id).filter { it.id !in have }
        if (targets.isEmpty()) return 0
        val resolver = com.ultratv.tv.nativeapp.data.xmltv.ExtraEpg.Resolver(targets)
        var total = 0
        xmltv.withExternal(url, resolver::resolve) { seq ->
            var run = 0
            seq.forEachBatchAhead(INSERT_BATCH) { batch ->
                db.withTransaction { epgDao.upsertAll(batch) }
                run += batch.size
                total = maxOf(total, run)
                onCount(total)
            }
        }
        return total
    }

    /**
     * Télécharge le XMLTV de la source en flux (fenêtre −2 h / +24 h) et l'insère par lots, chacun commité. Seules les
     * chaînes PRÉSENTES et NON MASQUÉES (catégorie active) sont lues (voir `epgMapping`). Les erreurs remontent.
     */
    private suspend fun syncProviderXmltv(p: ProviderEntity, report: (Long?, Long?, Int?) -> Unit): Int {
        // Build (xmltv channel id → local channel id) map for matching.
        // Plusieurs flux d'une même chaîne (HD, 4K, UHD…) partagent souvent le même identifiant EPG : TOUS reçoivent
        // le programme (avant : seul le dernier, les autres restaient « sans information »).
        val byEpg = channelDao.epgMapping(p.id).groupBy({ it.epgChannelId }, { it.id })
        val map = byEpg.mapValues { it.value.first() }
        val siblings = byEpg.values.filter { it.size > 1 }.associate { it.first() to it.drop(1) }
        // « TF1 +1 » sans identifiant EPG : programme de TF1 décalé d'une heure.
        val shifted = TimeshiftChannels.plan(channelDao.epgTitles(p.id), map)
        if (map.isEmpty()) return 0
        var total = 0
        val cleared = HashSet<Long>()
        xmltv.withProgrammes(p, map, onBytes = { b, t -> report(b, t, null) }) { seq ->
            // Reprise après une erreur : le flux repart du début ; les lignes déjà écrites sont remplacées (index unique).
            var run = 0
            com.ultratv.tv.nativeapp.data.sync.SyncThread.background {
                seq.forEachBatchAhead(INSERT_BATCH) { raw ->
                    val batch = if (siblings.isEmpty() && shifted.isEmpty()) raw else raw.flatMap { e ->
                        listOf(e) + siblings[e.channelId].orEmpty().map { id -> e.copy(id = 0, channelId = id) } +
                            shifted[e.channelId].orEmpty().map { (id, h) -> e.copy(id = 0, channelId = id, startMs = e.startMs + h * 3_600_000L, endMs = e.endMs + h * 3_600_000L) }
                    }
                    writeEpgBatch(batch, cleared)
                    run += batch.size
                    total = maxOf(total, run)
                    report(null, null, total)
                }
            }
        }
        return total
    }

    /**
     * Guides annoncés par l'en-tête de la playlist M3U (`url-tvg` / `x-tvg-url`, une ou plusieurs adresses). Chaque chaîne est
     * rattachée par son `tvg-id`. Un guide en échec n'empêche pas les suivants ; l'erreur ne remonte que si TOUS échouent.
     */
    private suspend fun syncTvgFeeds(p: ProviderEntity, report: (Long?, Long?, Int?) -> Unit): Int {
        val byEpg = channelDao.epgMapping(p.id).groupBy({ it.epgChannelId }, { it.id })
        if (byEpg.isEmpty()) return 0
        val resolve = { id: String, _: List<String> -> byEpg[id].orEmpty().map { it to 0 } }
        var total = 0
        val cleared = HashSet<Long>()
        var firstError: Throwable? = null
        var okCount = 0
        for (url in limits.epgUrls(p.id)) {
            try {
                var base = total
                xmltv.withExternal(url, resolve, onBytes = { b, t -> report(b, t, null) }) { seq ->
                    var run = 0
                    com.ultratv.tv.nativeapp.data.sync.SyncThread.background {
                        seq.forEachBatchAhead(INSERT_BATCH) { batch ->
                            writeEpgBatch(batch, cleared)
                            run += batch.size
                            total = maxOf(total, base + run)
                            report(null, null, total)
                        }
                    }
                }
                okCount++
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (firstError == null) firstError = t
            }
        }
        if (okCount == 0) firstError?.let { throw it }
        return total
    }

    /** Relance SEULEMENT le guide de la source (bouton « Réessayer » du guide) : ni catalogue, ni films, ni séries. */
    fun retryEpg(providerId: Long) {
        if (syncStatus.epg.value is EpgState.Loading) return
        syncStatus.setEpg(EpgState.Loading(providerId, EpgProgress(0, -1, 0)))
        epgScope.launch {
            try {
                syncMutex.withLock { syncXmltv(providerId) }
            } finally {
                // Source disparue ou sans guide : on ne laisse jamais un chargement sans fin.
                if ((syncStatus.epg.value as? EpgState.Loading)?.providerId == providerId) syncStatus.setEpg(EpgState.Idle)
            }
        }
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
     * Portail Stalker / Ministra : adresse du portail telle que saisie, MAC normalisée dans `username`, identifiant et
     * mot de passe facultatifs empaquetés dans `password` (chiffré au repos, voir [com.ultratv.tv.nativeapp.data.stalker.StalkerCreds]).
     * [probe] : vérifie d'abord la connexion (point d'entrée sondé et mémorisé) ; une erreur remonte à l'appelant.
     */
    suspend fun addStalker(name: String, portal: String, mac: String, login: String = "", password: String = "", probe: Boolean = false): Long {
        val m = com.ultratv.tv.nativeapp.data.stalker.StalkerMac.normalize(mac) ?: throw IllegalArgumentException("mac")
        val url = portal.trim().trimEnd('/')
        providerDao.findByIdentity("STALKER", url, m)?.let { return it.id }
        val packed = com.ultratv.tv.nativeapp.data.stalker.StalkerCreds.pack(login, password)
        if (probe) stalker.probe(com.ultratv.tv.nativeapp.data.stalker.StalkerClient.Account(url, m, login, password))
        return providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { hostOf(url) ?: "Stalker" },
                kind = "STALKER",
                baseUrl = url,
                username = m,
                password = packed,
                active = false,    // explicit default is set in Settings; see setDefault
            ),
        )
    }

    private fun hostOf(url: String): String? = runCatching { java.net.URI(if (url.contains("://")) url else "http://$url").host }.getOrNull()

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
                    syncStatus.clearPartial(providerId)
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
        if (p.kind == "M3U_LOCAL") {
            // Playlist locale : catalogue importé une fois ; son guide (url-tvg) est téléchargé s'il est périmé.
            if (prefs.flow.first().syncEpg && limits.epgUrls(p.id).isNotEmpty() &&
                SyncPolicy.epgNeedsRefresh(p.lastEpgSyncAt, epgDao.lastEndForProvider(p.id), System.currentTimeMillis())) syncXmltv(p.id, onProgress)
            return channelDao.count(p.id)
        }
        val now = System.currentTimeMillis()
        val u = prefs.flow.first()
        val a = adaptive.state.value
        val ttl = SyncPolicy.ttl(if (u.syncMode == "launch") 1 else u.syncIntervalHours)
        val enabledParts = buildSet { if (u.syncLive) add(SyncPart.LIVE); if (u.syncEpg) add(SyncPart.EPG); if (u.syncVod) add(SyncPart.VOD); if (u.syncSeries) add(SyncPart.SERIES) }
        val heavyOk = !(u.syncUnmeteredOnly && a.net.metered)
        val due = SyncPolicy.dueParts(p, now, ttl, channelDao.count(p.id), force || u.syncMode == "launch", enabledParts, heavyOk)
        if (due.isEmpty()) return 0

        if (p.kind == "M3U") return syncM3u(providerId, onProgress).also {
            providerDao.markSynced(p.id, SyncPart.LIVE, System.currentTimeMillis())
            // Guide annoncé par la playlist (url-tvg) : après les chaînes, sans jamais faire échouer la synchro.
            if (SyncPart.EPG in due) runCatching { syncXmltv(p.id, onProgress) }
        }
        // Portail Stalker : même moteur (catégories, langues, purge, ordre du Direct), autres requêtes ; pas de guide XMLTV.

        // Connexions simultanées autorisées (user_info) : alimente les avertissements enregistrement/lecture et le timeshift.
        // En parallèle du reste : une requête de plus en série, c'était un aller-retour réseau avant le premier octet utile.
        if (p.kind == "XTREAM") syncScope.launch { xtream.fetchMaxConnections(p)?.let { limits.setMaxConnections(p.id, it) } }

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

        // Xtream : les catégories des films et des séries (petites listes) sont demandées tout de suite, en parallèle du direct.
        val catsAhead = HashMap<SyncPart, kotlinx.coroutines.Deferred<List<CategoryEntity>>>()
        if (p.kind == "XTREAM") {
            if (SyncPart.VOD in due) catsAhead[SyncPart.VOD] = syncScope.async { xtream.fetchVodCategories(p) }
            if (SyncPart.SERIES in due) catsAhead[SyncPart.SERIES] = syncScope.async { xtream.fetchSeriesCategories(p) }
        }
        // Pré-téléchargement des grosses réponses (films, séries, guide) PENDANT l'écriture du direct, puis des films pendant
        // celle des séries : le réseau ne reste plus au repos. Lancé une fois le direct téléchargé (il garde la priorité sur
        // la bande passante, donc « Regarder le direct » reste aussi rapide) ; seulement quand la partie lira la liste
        // complète (pas en mode « une requête par catégorie », où ces téléchargements seraient inutiles).
        val prefetchStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        suspend fun startPrefetch() {
            if (p.kind != "XTREAM" || !prefetchStarted.compareAndSet(false, true)) return
            val langs = u.languages.split(',').filter { it.isNotBlank() }.toSet()
            suspend fun wantsFullList(part: SyncPart, kind: String): Boolean {
                val fetched = runCatching { catsAhead[part]?.await() }.getOrNull() ?: return false
                val merged = CatalogPlan.merge(fetched, categoryDao.forProviderKind(p.id, kind), langs, u.includeMulti, u.includeUnknownLang)
                return CatalogPlan.strategy(merged.size, merged.count { it.enabled }, p.categoryFilter) != SyncStrategy.PER_CATEGORY
            }
            if (SyncPart.VOD in due && wantsFullList(SyncPart.VOD, "MOVIE")) xtream.prefetch(syncScope, p, "get_vod_streams")
            if (SyncPart.SERIES in due && wantsFullList(SyncPart.SERIES, "SERIES")) xtream.prefetch(syncScope, p, "get_series")
            if (SyncPart.EPG in due) xmltv.prefetch(syncScope, p)
        }
        if (SyncPart.LIVE !in due) startPrefetch()

        var written = 0
        if (p.kind == "STALKER") stalkerSkips[p.id] = StalkerSkips()
        try {
            for (part in due) {
                when (part) {
                    SyncPart.LIVE -> {
                        written += syncXtreamPart(p, livePart(p.kind), ::maybeLock, { c, n -> step(part, "Live channels: $n", n, c) }, null, { syncScope.launch { startPrefetch() } })
                        startPrefetch()   // direct par catégories (pas de liste complète) ou déjà lancé : sans effet si déjà fait
                    }
                    SyncPart.VOD -> {
                        val n = stalkerTolerant(p, "MOVIE") { syncXtreamPart(p, vodPart(p.kind), ::maybeLock, { c, n -> step(part, "Movies: $n", n, c) }, catsAhead[part]) }
                        if (n == null) { doneBefore += weights.getValue(part); continue }   // réessayée à la prochaine synchro
                        written += n
                    }
                    SyncPart.SERIES -> {
                        val n = stalkerTolerant(p, "SERIES") { syncXtreamPart(p, seriesPart(p.kind), ::maybeLock, { c, n -> step(part, "Series: $n", n, c) }, catsAhead[part]) }
                        if (n == null) { doneBefore += weights.getValue(part); continue }
                        written += n
                    }
                    SyncPart.EPG -> {
                        // Le guide ne doit jamais faire échouer le reste : erreur isolée, réessayée au TTL suivant.
                        val ok = runCatching { syncXmltvInternal(p) { pr -> step(part, "EPG: ${pr.programmes}", pr.programmes, pr.percent?.div(100f) ?: (pr.programmes / 400_000f)) } }
                        if (ok.isFailure) { doneBefore += weights.getValue(part); continue }
                    }
                }
                providerDao.markSynced(p.id, part, System.currentTimeMillis())
                doneBefore += weights.getValue(part)
            }
            syncStatus.set(SyncStatusBus.Status(p.name, "Done", 100))
            // Catégories sautées : la synchro a réussi, le message final le dit (« X catégories ignorées »).
            stalkerSkips[p.id]?.count?.takeIf { it > 0 }?.let { syncStatus.setPartial(SyncStatusBus.Partial(p.id, p.name, it)) }
            return written
        } finally {
            syncStatus.clear()
            // Téléchargements d'avance non consommés (synchro échouée ou annulée) : fichiers temporaires supprimés.
            catsAhead.values.forEach { it.cancel() }
            xtream.discardPrefetch(p); xmltv.discardPrefetch(p)
            // Pages VOD Stalker gardées pour la partie « Séries » : libérées en fin de synchro.
            if (p.kind == "STALKER") { stalkerVodCache.keys.removeIf { it.startsWith("${p.id}:") }; stalkerSkips.remove(p.id) }
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
        /** Films / séries : écriture incrémentale (voir [IncrementalTable]) au lieu de tout supprimer et réinsérer. */
        val incremental: IncrementalTable<T>? = null,
        /** Catégories sautées pendant la lecture (portail Stalker) : leur contenu actuel est gardé par l'écriture incrémentale. */
        val keepCats: (Long) -> Collection<String> = { emptyList() },
    )

    private fun livePart(kind: String = "XTREAM") = PartSpec<com.ultratv.tv.nativeapp.data.db.ChannelEntity>(
        "LIVE",
        { p -> if (kind == "STALKER") stalker.genres(p) else xtream.fetchLiveCategories(p) },
        { p, blk -> if (kind == "STALKER") stalker.withAllChannels(p, blk) else xtream.withLiveStreams(p, blk) },
        { p, c -> if (kind == "STALKER") stalker.channelsOfGenre(p, c) else xtream.liveOfCategory(p, c) },
        { it.categoryId }, { c, lang -> c.copy(lang = LanguageDetector.forItem(c.name, lang)) },
        { channelDao.deleteForProvider(it) }, { pid, ids -> channelDao.deleteForCategories(pid, ids) }, { pid, id -> channelDao.deleteForCategory(pid, id) },
        // Identifiant CONSERVÉ d'une synchro à l'autre : le guide est rattaché aux identifiants de chaînes ; les réécrire
        // (suppression + insertion) laissait tous les programmes orphelins jusqu'au guide de la nuit.
        // Rang de catégorie (ordre du Direct, voir LiveOrder) posé à l'insertion : aucune réécriture après coup.
        { rows -> val keep = liveIds; channelDao.upsertAll(liveRanks.stamp(if (keep.isEmpty()) rows else rows.map { c -> keep[c.remoteId]?.let { c.copy(id = it) } ?: c })) },
    )

    /** remoteId → identifiant des chaînes AVANT la synchro en cours (voir [livePart]). */
    @Volatile private var liveIds: Map<String, Long> = emptyMap()

    /** Rangs des catégories du Direct, lus après l'écriture de la table des catégories (voir [livePart] et [LiveOrder]). */
    @Volatile private var liveRanks: LiveOrder.Ranks = LiveOrder.NONE

    private fun vodPart(kind: String = "XTREAM") = PartSpec<com.ultratv.tv.nativeapp.data.db.MovieEntity>(
        "MOVIE",
        { p -> if (kind == "STALKER") stalker.vodCategories(p, "MOVIE") else xtream.fetchVodCategories(p) },
        { p, blk -> if (kind == "STALKER") blk(stalkerVodAll(p, "MOVIE") { it.movies }.asSequence()) else xtream.withVodStreams(p, blk) },
        { p, c -> if (kind == "STALKER") stalkerVod(p, c).movies else xtream.vodOfCategory(p, c) },
        { it.categoryId }, { m, lang -> m.copy(lang = LanguageDetector.forItem(m.name, lang)) },
        { movieDao.deleteForProvider(it) }, { pid, ids -> movieDao.deleteForCategories(pid, ids) }, { pid, id -> movieDao.deleteForCategory(pid, id) },
        { movieDao.upsertAll(it) },
        incremental = IncrementalTable.MOVIES,
        keepCats = { pid -> stalkerFailedCats(pid, "MOVIE") },
    )

    private fun seriesPart(kind: String = "XTREAM") = PartSpec<com.ultratv.tv.nativeapp.data.db.SeriesEntity>(
        "SERIES",
        { p -> if (kind == "STALKER") stalkerSeriesCategories(p) else xtream.fetchSeriesCategories(p) },
        { p, blk -> if (kind == "STALKER") blk(stalkerVodAll(p, "SERIES") { s -> s.series.map { it.series } }.asSequence()) else xtream.withSeries(p, blk) },
        { p, c -> if (kind == "STALKER") stalkerVod(p, c).series.map { it.series } else xtream.seriesOfCategory(p, c) },
        { it.categoryId }, { s2, lang -> s2.copy(lang = LanguageDetector.forItem(s2.name, lang)) },
        { seriesDao.deleteForProvider(it) }, { pid, ids -> seriesDao.deleteForCategories(pid, ids) }, { pid, id -> seriesDao.deleteForCategory(pid, id) },
        { seriesDao.upsertAll(it) },
        incremental = IncrementalTable.SERIES,
        keepCats = { pid -> stalkerFailedCats(pid, "SERIES") },
    )

    // ───────────── Portail Stalker : films et séries partagent la même liste VOD ─────────────

    /**
     * Dernière lecture VOD de chaque catégorie Stalker (films ET séries) : la partie « Séries », qui suit la partie
     * « Films », réutilise ces pages au lieu de les retélécharger (une catégorie de 2 000 titres = 140 requêtes).
     */
    private val stalkerVodCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, com.ultratv.tv.nativeapp.data.stalker.StalkerClient.VodSplit>>()

    private suspend fun stalkerVod(p: ProviderEntity, categoryId: String): com.ultratv.tv.nativeapp.data.stalker.StalkerClient.VodSplit {
        val key = "${p.id}:$categoryId"
        stalkerVodCache[key]?.let { (at, v) -> if (System.currentTimeMillis() - at < 30 * 60_000L) return v }
        // Catégorie du module `type=series` (identifiant préfixé) : séries seulement, demandées à ce module.
        val v = if (categoryId.startsWith(com.ultratv.tv.nativeapp.data.stalker.StalkerClient.SERIES_PREFIX))
            com.ultratv.tv.nativeapp.data.stalker.StalkerClient.VodSplit(emptyList(), fetchWithBackoff { stalker.seriesOfCategory(p, categoryId) })
        else fetchWithBackoff { stalker.vodOfCategory(p, categoryId) }
        stalkerVodCache[key] = System.currentTimeMillis() to v
        return v
    }

    /**
     * Toutes les catégories VOD ACTIVES de [kind] (déjà fusionnées et écrites par le moteur), 3 à la fois. Une catégorie
     * en échec (erreur, délai, réponse vide) est journalisée et sautée ; son contenu précédent est gardé (voir [PartSpec.keepCats]).
     */
    private suspend fun <T> stalkerVodAll(p: ProviderEntity, kind: String, pick: (com.ultratv.tv.nativeapp.data.stalker.StalkerClient.VodSplit) -> List<T>): List<T> {
        val ids = categoryDao.forProviderKind(p.id, kind).filter { it.enabled }.map { it.remoteId }
        val gate = kotlinx.coroutines.sync.Semaphore(3)
        val failed = java.util.concurrent.atomic.AtomicInteger()
        val out = kotlinx.coroutines.coroutineScope {
            ids.map { id ->
                async {
                    gate.withPermit {
                        try {
                            pick(stalkerVod(p, id))
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (e: Exception) {
                            failed.incrementAndGet()
                            stalkerSkip(p.id, kind, id, com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.STEP_LIST, stalkerType(kind, id), e)
                            emptyList()
                        }
                    }
                }
            }.map { it.await() }
        }.flatten()
        com.ultratv.tv.nativeapp.RemoteLog.info("StalkerSync", com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.summary(if (kind == "MOVIE") "vod" else "series", ids.size - failed.get(), failed.get(), out.size))
        return out
    }

    /**
     * Catégories des séries. Portail qui propose le module `type=series` (Ministra récent) : ses catégories. Sinon (ou
     * module en erreur) : celles de la VOD qui contiennent des séries (si la partie Films vient de les lire), sinon toutes.
     */
    private suspend fun stalkerSeriesCategories(p: ProviderEntity): List<CategoryEntity> {
        val module = try {
            stalker.seriesCategories(p)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            com.ultratv.tv.nativeapp.RemoteLog.info("StalkerSync", com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.skipped(com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.STEP_CATEGORIES, "series", e))
            emptyList()
        }
        com.ultratv.tv.nativeapp.RemoteLog.info("StalkerSync", com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.seriesModule(module.isNotEmpty(), module.size))
        if (module.isNotEmpty()) return module
        val all = stalker.vodCategories(p, "SERIES")
        val now = System.currentTimeMillis()
        val read = all.associate { c -> c.remoteId to stalkerVodCache["${p.id}:${c.remoteId}"]?.takeIf { now - it.first < 30 * 60_000L }?.second }
        if (read.values.all { it == null }) return all
        // Catégories lues : gardées seulement si elles contiennent des séries ; jamais lues (désactivées côté films) : gardées.
        return all.filter { c -> read[c.remoteId]?.series?.isNotEmpty() ?: true }
    }

    // ───────────── Portail Stalker : catégories sautées pendant une synchro ─────────────

    private class StalkerSkips {
        /** kind (MOVIE / SERIES) → catégories sautées : leur contenu précédent est gardé. */
        val failed = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
        /** Catégories distinctes sautées (une catégorie VOD sautée en Films puis en Séries ne compte qu'une fois). */
        val distinct: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
        val extra = java.util.concurrent.atomic.AtomicInteger()
        val lines = java.util.concurrent.atomic.AtomicInteger()
        val count: Int get() = distinct.size + extra.get()
    }

    private val stalkerSkips = java.util.concurrent.ConcurrentHashMap<Long, StalkerSkips>()

    /** « vod » ou « series » pour le journal, selon la partie et l'origine (module `type=series`) de la catégorie. */
    private fun stalkerType(kind: String, id: String?): String =
        if (kind == "SERIES" && id?.startsWith(com.ultratv.tv.nativeapp.data.stalker.StalkerClient.SERIES_PREFIX) == true) "series" else "vod"

    /** Note une catégorie (ou, [catId] null, une partie entière valant [count] catégories) sautée, et la journalise (nettoyé, plafonné). */
    private fun stalkerSkip(pid: Long, kind: String, catId: String?, step: String, type: String, e: Throwable?, count: Int = 1) {
        val s = stalkerSkips.getOrPut(pid) { StalkerSkips() }
        if (catId != null) {
            s.failed.getOrPut(kind) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(catId)
            if (!s.distinct.add(catId)) return
        } else s.extra.addAndGet(count)
        if (s.lines.incrementAndGet() <= com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.MAX_LINES)
            com.ultratv.tv.nativeapp.RemoteLog.info("StalkerSync", com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.skipped(step, type, e))
    }

    private fun stalkerFailedCats(pid: Long, kind: String): List<String> = stalkerSkips[pid]?.failed?.get(kind)?.toList().orEmpty()

    /**
     * Portail Stalker : une partie Films / Séries dont même la liste des catégories est illisible est sautée (null), au
     * lieu de faire échouer toute la synchro (le direct, déjà écrit, reste utilisable). Autres sources : inchangé.
     */
    private suspend fun stalkerTolerant(p: ProviderEntity, kind: String, block: suspend () -> Int): Int? {
        if (p.kind != "STALKER") return block()
        return try {
            block()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            val known = categoryDao.forProviderKind(p.id, kind).count { it.enabled }.coerceAtLeast(1)
            stalkerSkip(p.id, kind, null, com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.STEP_PART, if (kind == "MOVIE") "vod" else "series", e, known)
            null
        }
    }

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
        catsAhead: kotlinx.coroutines.Deferred<List<CategoryEntity>>? = null,
        onDownloaded: () -> Unit = {},
    ): Int = partLock(spec.kind).withLock {
        syncXtreamPartLocked(p, spec, lock, progress, catsAhead, onDownloaded).also {
            // Filet de sécurité du Direct : les chaînes restées en place (catégorie disparue du serveur, synchro par
            // catégorie) reprennent le rang de l'ordre courant ; ne réécrit que ce qui change (pas de coût sinon).
            if (spec.kind == LiveOrder.KIND) db.withTransaction { LiveOrder.apply(channelDao, p.id, liveRanks) }
        }
    }

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
        catsAhead: kotlinx.coroutines.Deferred<List<CategoryEntity>>?,
        onDownloaded: () -> Unit,
    ): Int {
        val u = prefs.flow.first()
        val langs = u.languages.split(',').filter { it.isNotBlank() }.toSet()
        if (spec.kind == "LIVE") liveIds = channelDao.idsByRemote(p.id).associate { it.remoteId to it.id }
        val fetched = lock(catsAhead?.await() ?: spec.fetchCats(p))
        val before = categoryDao.forProviderKind(p.id, spec.kind)
        val merged = CatalogPlan.merge(fetched, before, langs, u.includeMulti, u.includeUnknownLang)
        noteLangDisabled(p.id, spec.kind, merged, before)
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
        // Les catégories viennent d'être réécrites (nouveaux id) : rangs du Direct relus avant d'insérer les chaînes.
        if (spec.kind == LiveOrder.KIND) liveRanks = LiveOrder.ranks(categoryDao.forProviderKind(p.id, spec.kind))
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
                    val items = fetchCat(p, spec, id) ?: continue   // Stalker : catégorie sautée, contenu précédent gardé
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
                                val items = fetchCat(p, spec, id)
                                val n = if (items == null) 0 else insertCategory(p, spec, id, items, langByCat)
                                progress(done.incrementAndGet().toFloat() / total, count.addAndGet(n))
                            }
                        }
                    }.forEach { it.await() }
                }
                return count.get()
            }
        }

        // Requête globale en flux ; les éléments des catégories désactivées ne sont pas stockés.
        // Transaction unique : une liste coupée en route est annulée (l'ancien catalogue reste) au lieu d'être gardée à moitié.
        // Serveur qui filtre par catégorie : la coupure est rattrapée par une requête par catégorie active.
        return try {
            streamAllInto(p, spec, strategy == SyncStrategy.GLOBAL, enabledSet, langByCat, progress, onDownloaded)
        } catch (e: Exception) {
            val cut = e is java.io.IOException || e is kotlinx.serialization.SerializationException
            if (!cut || p.categoryFilter != 1 || enabledIds.isEmpty()) throw e
            var count = 0
            for ((i, id) in enabledIds.withIndex()) {
                fetchCat(p, spec, id)?.let { count += insertCategory(p, spec, id, it, langByCat) }
                progress((i + 1f) / enabledIds.size, count)
            }
            count
        }
    }

    /** Catégories NOUVELLES désactivées par la langue : la raison est retenue (réactivées avec leur langue, pas les autres). */
    private fun noteLangDisabled(pid: Long, kind: String, merged: List<CategoryEntity>, before: List<CategoryEntity>) {
        val known = before.map { it.remoteId }.toHashSet()
        langDisabled.add(pid, kind, merged.filter { !it.enabled && it.remoteId !in known }.map { it.remoteId })
    }

    private suspend fun <T : Any> streamAllInto(
        p: ProviderEntity, spec: PartSpec<T>, all: Boolean, enabledSet: Set<String>, langByCat: Map<String, String>,
        progress: (Float, Int) -> Unit,
        onDownloaded: () -> Unit = {},
    ): Int {
        var n = 0
        val inc = spec.incremental
        if (inc != null) {
            spec.streamAll(p) { seq ->
                onDownloaded()
                db.withTransaction {
                  com.ultratv.tv.nativeapp.data.sync.SyncThread.background {
                    val pass = inc.begin(db.openHelper.writableDatabase, p.id)
                    // Catégories désactivées (langue masquée ou choix de l'utilisateur) : titres sautés ICI, avant toute
                    // écriture (ni base, ni index plein texte, ni guide). Une catégorie active est chargée EN ENTIER.
                    seq.filter { all || spec.catOf(it) == null || spec.catOf(it) in enabledSet }
                        .map { spec.withLang(it, langByCat[spec.catOf(it)].orEmpty()) }
                        .forEachBatchAhead(adaptive.state.value.auto.insertBatch) { batch ->
                            pass.write(batch)
                            n += batch.size
                            progress((n / 60_000f).coerceAtMost(0.95f), n)
                        }
                    pass.keepCategories(spec.keepCats(p.id))
                    val removed = pass.finish()
                    android.util.Log.i("UltraSync", "${spec.kind}: ${pass.inserted} new, ${pass.updated} changed, $removed removed, ${n - pass.inserted - pass.updated} unchanged")
                  }
                }
            }
            return n
        }
        spec.streamAll(p) { seq ->
            onDownloaded()
            db.withTransaction {
                spec.deleteAll(p.id)
                val seqState = ChannelSeq()
                seq.filter { all || spec.catOf(it) == null || spec.catOf(it) in enabledSet }
                    .map { spec.withLang(it, langByCat[spec.catOf(it)].orEmpty()) }
                    .forEachBatchAhead(adaptive.state.value.auto.insertBatch) { batch ->
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

    /**
     * Éléments d'une catégorie. Portail Stalker : une catégorie en échec (erreur, délai dépassé, réponse vide) est
     * journalisée et sautée (null) — les autres continuent et son contenu précédent reste en place.
     */
    private suspend fun <T : Any> fetchCat(p: ProviderEntity, spec: PartSpec<T>, id: String): List<T>? {
        if (p.kind != "STALKER" || spec.kind == "LIVE") return fetchWithBackoff { spec.fetchOne(p, id) }
        return try {
            fetchWithBackoff { spec.fetchOne(p, id) }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            stalkerSkip(p.id, spec.kind, id, com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.STEP_LIST, stalkerType(spec.kind, id), e)
            null
        }
    }

    /** Respecte le serveur : nouvelle tentative avec délai croissant sur 429 / 5xx / coupure (y compris JSON tronqué). */
    private suspend fun <R> fetchWithBackoff(block: suspend () -> R): R {
        var delayMs = 1_000L
        repeat(2) {
            try { return block() }
            catch (e: com.ultratv.tv.nativeapp.data.net.HttpStatusException) { if (e.code != 429 && e.code < 500) throw e }
            catch (e: java.io.IOException) { /* réessaie */ }
            catch (e: kotlinx.serialization.SerializationException) { /* réponse coupée en plein tableau : réessaie */ }
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
        // M3U : une seule liste, pas de requête par catégorie ; la liste est relue en entier (les langues masquées n'y sont plus écrites).
        if (p.kind == "M3U" && ids.isNotEmpty()) { syncAll(providerId, force = true); return }
        if ((p.kind != "XTREAM" && p.kind != "STALKER") || ids.isEmpty()) return
        val part = when (kind) { "LIVE" -> SyncPart.LIVE; "MOVIE" -> SyncPart.VOD; else -> SyncPart.SERIES }
        // Serveur qui ignore `category_id` : pas de chargement ciblé possible, synchro complète (verrou global).
        if (p.categoryFilter == 0) { syncAll(providerId, force = true); return }
        // Synchro complète en cours : on ne touche pas à son indicateur de progression (ni ne l'efface).
        val ownStatus = !syncMutex.isLocked
        partLock(kind).withLock {
            if (kind == "LIVE") liveIds = channelDao.idsByRemote(p.id).associate { it.remoteId to it.id }
            if (ownStatus) syncStatus.set(SyncStatusBus.Status(p.name, "Categories", 0, part, 0))
            try {
                var n = 0
                val catList = categoryDao.forProviderKind(p.id, kind)
                val cats = catList.associateBy { it.remoteId }
                val langOf: (String) -> String = { cats[it]?.lang.orEmpty() }
                if (kind == LiveOrder.KIND) liveRanks = LiveOrder.ranks(catList)
                for ((i, id) in ids.withIndex()) {
                    n += when (kind) {
                        "LIVE" -> { val sp = livePart(p.kind); insertCategory(p, sp, id, fetchWithBackoff { sp.fetchOne(p, id) }, mapOf(id to langOf(id))) }
                        "MOVIE" -> { val sp = vodPart(p.kind); insertCategory(p, sp, id, fetchWithBackoff { sp.fetchOne(p, id) }, mapOf(id to langOf(id))) }
                        else -> { val sp = seriesPart(p.kind); insertCategory(p, sp, id, fetchWithBackoff { sp.fetchOne(p, id) }, mapOf(id to langOf(id))) }
                    }
                    if (ownStatus) syncStatus.set(SyncStatusBus.Status(p.name, "Categories", ((i + 1) * 100) / ids.size, part, n))
                }
            } finally { if (ownStatus) syncStatus.clear() }
        }
        // Nouvelles chaînes du direct : leur guide tout de suite (regroupé), pas à la synchro de la nuit.
        if (kind == "LIVE" && p.kind == "XTREAM") scheduleEpgRefresh(providerId)
    }

    private val epgScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    @Volatile private var epgJob: kotlinx.coroutines.Job? = null

    /** Guide re-téléchargé 60 s après la DERNIÈRE activation de catégorie (plusieurs activations = un seul téléchargement). */
    private fun scheduleEpgRefresh(providerId: Long) {
        epgJob?.cancel()
        epgJob = epgScope.launch {
            kotlinx.coroutines.delay(60_000)
            runCatching { syncXmltv(providerId) }
        }
    }

    @Volatile private var epgCheckAt = 0L
    @Volatile private var epgRefreshJob: kotlinx.coroutines.Job? = null

    /**
     * Au retour sur l'application : recharge en arrière-plan le guide des sources Xtream périmé ou qui ne couvre plus
     * les prochaines heures (voir [SyncPolicy.epgNeedsRefresh]). Au plus une vérification toutes les 15 min, jamais
     * deux téléchargements en parallèle ; ne fait rien si le guide est désactivé dans les réglages.
     */
    fun refreshEpgIfStale() {
        val now = System.currentTimeMillis()
        if (now - epgCheckAt < 15 * 60_000L || epgRefreshJob?.isActive == true) return
        epgCheckAt = now
        epgRefreshJob = epgScope.launch {
            purgePastEpg()
            if (!prefs.flow.first().syncEpg) return@launch
            // Synchro en cours : elle s'occupe du guide (et effacer sa bannière de progression serait une gêne).
            if (syncMutex.isLocked) return@launch
            syncMutex.withLock {
                for (p in providerDao.observeAll().first().filter { hasGuideFeed(it) }) {
                    if (SyncPolicy.epgNeedsRefresh(p.lastEpgSyncAt, epgDao.lastEndForProvider(p.id), System.currentTimeMillis()))
                        runCatching { syncXmltv(p.id) }
                }
            }
        }
    }

    /** Programmes terminés depuis plus de 3 h supprimés (le guide ne retenait jamais rien : la base grossissait). */
    private suspend fun purgePastEpg() {
        runCatching { epgDao.deletePast(System.currentTimeMillis() - 3 * 3_600_000L) }
    }

    /** Langues détectées dans les catégories du serveur (3 requêtes légères, aucun contenu téléchargé). */
    suspend fun previewLanguages(providerId: Long): List<Pair<String, Int>> {
        val p = providerDao.byId(providerId) ?: return emptyList()
        val all = when (p.kind) {
            "XTREAM" -> runCatching { fetchWithBackoff { xtream.fetchLiveCategories(p) } + fetchWithBackoff { xtream.fetchVodCategories(p) } + fetchWithBackoff { xtream.fetchSeriesCategories(p) } }.getOrDefault(emptyList())
            "STALKER" -> runCatching { fetchWithBackoff { stalker.genres(p) } + fetchWithBackoff { stalker.vodCategories(p) } }.getOrDefault(emptyList()) +
                runCatching { stalker.seriesCategories(p) }.getOrDefault(emptyList())
            else -> return emptyList()
        }
        return all.map { LanguageDetector.forCategory(it.name) }
            .filter { it.isNotEmpty() && it != LanguageDetector.MULTI }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
