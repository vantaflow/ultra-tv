package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.net.EmptyCatalogException
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
import com.ultratv.tv.nativeapp.data.mediaserver.MediaServer
import com.ultratv.tv.nativeapp.data.nas.Nas
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
    private val hiddenChannels: com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore,
    private val mediaServer: com.ultratv.tv.nativeapp.data.mediaserver.MediaServerClient = com.ultratv.tv.nativeapp.data.mediaserver.MediaServerClient(okhttp3.OkHttpClient(), { "ultra-tv" }),
    private val nas: com.ultratv.tv.nativeapp.data.nas.NasClient = com.ultratv.tv.nativeapp.data.nas.NasClient(com.ultratv.tv.nativeapp.data.nas.NasSmb()) { null },
    private val guardMemory: SyncGuardMemory = SyncGuardMemory.inMemory(),
    /** Nouvelles catégories masquées d'office (ligne de Réglages › Catégories) ; absent dans la plupart des tests. */
    private val newCategories: com.ultratv.tv.nativeapp.data.prefs.NewCategoriesStore? = null,
) {
    /**
     * Garde anti-effacement d'une partie de la source [p] (voir [CatalogGuard]) : liste acceptée, ou refus qui lève
     * [EmptyCatalogException] ; une liste réduite mais non vide qui revient identique depuis plus de 24 h est acceptée.
     */
    private fun guard(p: ProviderEntity, part: String, before: Int, received: Int) {
        val key = "${p.id}:$part"
        if (!CatalogGuard.refuses(before, received)) { guardMemory.clear(key); return }
        if (guardMemory.refusedIsDurable(key, received)) {
            com.ultratv.tv.nativeapp.RemoteLog.warn("SyncGuard", "liste réduite acceptée (stable depuis 24 h) partie=$part avant=$before reçu=$received")
            return
        }
        CatalogGuard.check(part, before, received)
    }

    /** Retire les chaînes masquées par l'utilisateur d'un lot à écrire (voir HiddenChannelsStore). */
    private fun withoutHidden(rows: List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>): List<com.ultratv.tv.nativeapp.data.db.ChannelEntity> {
        val pid = rows.firstOrNull()?.providerId ?: return rows
        val h = hiddenChannels.ids(pid)
        return if (h.isEmpty()) rows else rows.filter { it.remoteId !in h }
    }

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
    /** Identifiant de la source active, SANS déchiffrer ses identifiants (Keystore, lent) : recherche, favoris. */
    suspend fun firstActiveId(): Long? = providerDao.firstActiveId()
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
        importLocalText(pid, text)
        return pid
    }

    /**
     * Lit une playlist locale dans la source [pid]. Réimport (« Modifier › Choisir un autre fichier ») : les chaînes déjà
     * connues gardent leur identifiant (favoris, historique et guide restent rattachés), l'état des catégories est fusionné.
     */
    private suspend fun importLocalText(pid: Long, text: String) {
        val res = m3u.parse(text, pid)
        limits.setEpgUrls(pid, res.epgUrls)
        val pinSet = parental.isSet()
        val before = categoryDao.forProviderKind(pid, "LIVE").associateBy { it.remoteId }
        val cats = (if (!pinSet) res.categories
        else res.categories.map { it.copy(locked = adultRegex.containsMatchIn(it.name)) })
            .map { c -> before[c.remoteId]?.let { o -> c.copy(enabled = o.enabled, locked = o.locked || c.locked) } ?: c }
        val keep = channelDao.idsByRemote(pid).associate { it.remoteId to it.id }
        categoryDao.deleteForProviderKind(pid, "LIVE")
        categoryDao.upsertAll(cats)
        channelDao.deleteForProvider(pid)
        val rows = withoutHidden(res.channels).map { c -> keep[c.remoteId]?.let { c.copy(id = it) } ?: c }
        // Rang de catégorie lu APRÈS l'écriture des catégories : les non placées se départagent par leur nouvel id.
        channelDao.upsertAll(LiveOrder.ranks(categoryDao.forProviderKind(pid, LiveOrder.KIND)).stamp(rows))
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
            // Synchros suivantes : une catégorie inconnue arrive masquée (désactivée), voir CatalogPlan.mergeDetailed.
            val md = CatalogPlan.mergeDetailed(locked, before, LangSelection.of(u.languages, u.includeMulti, u.includeUnknownLang).selected, u.includeMulti, u.includeUnknownLang, CatalogPlan.shouldMaskNew(p.kind, before))
            val cats = md.categories
            noteLangDisabled(p.id, "LIVE", cats, before, md.newMasked)
            val enabledCats = cats.filter { it.enabled }.map { it.remoteId }.toHashSet()
            val langByCat = cats.associate { it.remoteId to it.lang }
            val channels = res.channels
                .filter { it.categoryId == null || it.categoryId in enabledCats }
                .map { c -> c.copy(lang = LanguageDetector.forItem(c.name, c.categoryId?.let { langByCat[it] }.orEmpty())) }
                .let { withoutHidden(it) }
                // Sans tvg-chno, numéro séquentiel par catégorie APRÈS le retrait des masquées (sinon aucun numéro au zapping).
                // Une playlist qui numérote ses chaînes garde ses numéros.
                .let { if (it.any { c -> c.num > 0 }) it else ChannelSeq().assign(it) }
            // Page vide, page HTML d'erreur (compte expiré) ou liste effondrée : rien n'est effacé (voir CatalogGuard).
            guard(p, "LIVE", channelDao.countProvided(p.id), channels.size)
            // Suppression + réinsertion dans UNE transaction : atomique (jamais de catalogue vide
            // si on coupe au milieu) et une seule invalidation Room au lieu d'une par lot.
            db.withTransaction {
                categoryDao.deleteForProviderKind(p.id, "LIVE")
                categoryDao.upsertAll(cats)
                channelDao.deleteForProvider(p.id)
                step("Saving ${channels.size} channels…", 90)
                val ranks = LiveOrder.ranks(categoryDao.forProviderKind(p.id, LiveOrder.KIND))
                insertChunked(channels) { channelDao.upsertAll(ranks.stamp(it)) }
                // Chaînes ajoutées à la main : la table `channel` vient d'être vidée, on les y remet (même transaction).
                ManualChannels.reinject(db, p.id)
            }
            // Liste acceptée par la garde et écrite : les nouvelles catégories masquées sont signalées (jamais en cas de refus).
            newCategories?.add(p.id, "LIVE", md.newMasked)
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
        if (!hasGuideFeed(p)) return 0   // y compris le guide complémentaire seul (source sans guide propre)
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

    /** Adresse du guide complémentaire global (Réglages), ou null s'il est désactivé ou invalide. */
    private suspend fun globalExtraUrl(): String? =
        prefs.flow.first().extraEpg.trim().takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

    /**
     * Le guide complémentaire seul (source sans guide propre) n'est lu que si l'utilisateur l'a CHOISI (adresse personnalisée ou
     * option enregistrée) : la valeur par défaut (XMLTV France) ne doit pas être téléchargée pour toute playlist sans url-tvg.
     */
    private suspend fun guidePlan(p: ProviderEntity): com.ultratv.tv.nativeapp.data.xmltv.GuidePlan {
        val u = prefs.flow.first()
        val chosen = u.extraEpgChosen && globalExtraUrl() != null
        return com.ultratv.tv.nativeapp.data.xmltv.GuidePlan.of(p.kind, limits.epgUrls(p.id), limits.customEpg(p.id), globalExtra = chosen)
    }

    /**
     * Xtream : xmltv.php. M3U : url-tvg de l'en-tête. Toute source (Stalker compris) : son « guide de cette source » s'il est
     * défini, sinon le guide complémentaire des Réglages (une playlist sans url-tvg y trouve ses programmes).
     */
    private suspend fun hasGuideFeed(p: ProviderEntity): Boolean = guidePlan(p).hasFeed

    /**
     * Fin du dernier programme à comparer à [SyncPolicy.epgNeedsRefresh]. Source servie par le seul guide complémentaire :
     * pas de relecture horaire d'un gros guide quand rien ne correspond (seulement le délai ordinaire de 12 h).
     */
    private suspend fun epgLastEnd(p: ProviderEntity): Long? =
        if (guidePlan(p).extraOnly) Long.MAX_VALUE else epgDao.lastEndForProvider(p.id)

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
            val plan = guidePlan(p)
            val own = runCatching {
                when {
                    plan.replaceUrl != null -> syncCustomReplace(p, plan.replaceUrl, report)   // « Guide de cette source » en mode remplacer
                    p.kind == "XTREAM" -> syncProviderXmltv(p, report)
                    else -> syncTvgFeeds(p, report)
                }
            }
            // Le complément ne compte que le programme de la source (pas de barre d'octets propre).
            var extraError: Throwable? = null
            val extra = runCatching {
                syncExtraEpg(p, plan.completeUrl, allChannels = plan.extraOnly, onError = { extraError = it }) { c -> report(null, null, (own.getOrNull() ?: 0) + c) }
            }.getOrDefault(0)
            val total = (own.getOrNull() ?: 0) + extra
            emit(force = true)
            if (own.isFailure && extra == 0) throw own.exceptionOrNull()!!
            // Guide complémentaire seul : un échec (réseau, délai) remonte pour être réessayé ; un guide lu mais sans aucune
            // chaîne rapprochée n'est pas une panne (chaînes « sans programme », pas de relecture en boucle).
            if (plan.extraOnly && extra == 0) extraError?.let { throw it }
            if (total == 0 && !plan.extraOnly) throw EmptyGuideException()
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

    /**
     * Guides complémentaires pour les chaînes sans programme : d'abord « Guide de cette source » (mode compléter, [sourceUrl]),
     * puis le guide complémentaire des Réglages (http ou https) ; 0 si aucun. Un guide en échec n'empêche pas le suivant.
     */
    private suspend fun syncExtraEpg(
        p: ProviderEntity, sourceUrl: String?, allChannels: Boolean = false, onError: (Throwable) -> Unit = {}, onCount: (Int) -> Unit,
    ): Int {
        val global = globalExtraUrl()
        var total = 0
        for (url in listOfNotNull(sourceUrl, global).distinct()) {
            try {
                total += syncExtraEpgFrom(p, url, allChannels) { c -> onCount(total + c) }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) { onError(t) /* guide complémentaire : jamais bloquant pour la source */ }
        }
        return total
    }

    /**
     * « Guide de cette source » en mode remplacer : seul ce guide est lu, pour TOUTES les chaînes. Rattachement par
     * identifiant EPG (`epgChannelId`, `xmltv_id` pour Stalker), sinon par nom (même résolveur que le guide complémentaire).
     */
    private suspend fun syncCustomReplace(p: ProviderEntity, url: String, report: (Long?, Long?, Int?) -> Unit): Int {
        val targets = channelDao.epgTitles(p.id)
        if (targets.isEmpty()) return 0
        val resolver = com.ultratv.tv.nativeapp.data.xmltv.ExtraEpg.Resolver(targets)
        var total = 0
        val cleared = HashSet<Long>()
        val plan = archivePlan(p.id)
        xmltv.withExternal(url, resolver::resolve, window = plan.window, onBytes = { b, t -> report(b, t, null) }) { seq ->
            var run = 0
            com.ultratv.tv.nativeapp.data.sync.SyncThread.background {
                seq.forEachBatchAhead(INSERT_BATCH) { raw ->
                    val batch = plan.apply(raw)
                    if (batch.isNotEmpty()) writeEpgBatch(batch, cleared)
                    run += batch.size
                    total = maxOf(total, run)
                    report(null, null, total)
                }
            }
        }
        return total
    }

    /** [allChannels] : source sans guide propre, toutes ses chaînes sont visées (et rafraîchies à chaque synchro). */
    private suspend fun syncExtraEpgFrom(p: ProviderEntity, url: String, allChannels: Boolean, onCount: (Int) -> Unit): Int {
        val have = if (allChannels) emptySet() else epgDao.channelsWithProgrammes(p.id, System.currentTimeMillis()).toHashSet()
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
        val plan = archivePlan(p.id)
        xmltv.withProgrammes(p, map, window = plan.window, onBytes = { b, t -> report(b, t, null) }) { seq ->
            // Reprise après une erreur : le flux repart du début ; les lignes déjà écrites sont remplacées (index unique).
            var run = 0
            com.ultratv.tv.nativeapp.data.sync.SyncThread.background {
                seq.forEachBatchAhead(INSERT_BATCH) { raw ->
                    val batch = plan.apply(if (siblings.isEmpty() && shifted.isEmpty()) raw else raw.flatMap { e ->
                        listOf(e) + siblings[e.channelId].orEmpty().map { id -> e.copy(id = 0, channelId = id) } +
                            shifted[e.channelId].orEmpty().map { (id, h) -> e.copy(id = 0, channelId = id, startMs = e.startMs + h * 3_600_000L, endMs = e.endMs + h * 3_600_000L) }
                    })
                    if (batch.isNotEmpty()) writeEpgBatch(batch, cleared)
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
        val plan = archivePlan(p.id)
        for (url in limits.epgUrls(p.id)) {
            try {
                var base = total
                xmltv.withExternal(url, resolve, window = plan.window, onBytes = { b, t -> report(b, t, null) }) { seq ->
                    var run = 0
                    com.ultratv.tv.nativeapp.data.sync.SyncThread.background {
                        seq.forEachBatchAhead(INSERT_BATCH) { raw ->
                            val batch = plan.apply(raw)
                            if (batch.isNotEmpty()) writeEpgBatch(batch, cleared)
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
    suspend fun addStalker(name: String, portal: String, mac: String, login: String = "", password: String = "", probe: Boolean = false, identity: com.ultratv.tv.nativeapp.data.stalker.StalkerIdentity = com.ultratv.tv.nativeapp.data.stalker.StalkerIdentity()): Long {
        val m = com.ultratv.tv.nativeapp.data.stalker.StalkerMac.normalize(mac) ?: throw IllegalArgumentException("mac")
        val url = portal.trim().trimEnd('/')
        providerDao.findByIdentity("STALKER", url, m)?.let { return it.id }
        val packed = com.ultratv.tv.nativeapp.data.stalker.StalkerCreds.pack(login, password, identity)
        if (probe) stalker.probe(com.ultratv.tv.nativeapp.data.stalker.StalkerClient.Account(url, m, login, password, identity))
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

    // ───────────── « Modifier » une source (Réglages › Sources) ─────────────
    //
    // La source garde son identifiant local : favoris, historique, catégories masquées ou verrouillées, numéros et profils
    // restent attachés. Les identifiants sont vérifiés quand l'ajout les vérifie (portail Stalker, NAS, serveur multimédia) ;
    // une erreur remonte à l'appelant et RIEN n'est enregistré. La synchro est relancée par l'appelant ; la garde
    // anti-effacement ([CatalogGuard]) s'applique au nouveau catalogue comme à toute synchro.

    /** Résultat d'une modification : [providerChanged] = autre fournisseur (catalogue remplacé à la synchro). */
    data class EditResult(val id: Long, val providerChanged: Boolean)

    private suspend fun editable(id: Long, vararg kinds: String): ProviderEntity =
        providerDao.byId(id)?.takeIf { it.kind in kinds } ?: throw IllegalArgumentException("source")

    /** Une AUTRE source a déjà cette identité : refus (deux lignes pour le même compte se marcheraient dessus). */
    private suspend fun noDuplicate(p: ProviderEntity, kind: String, url: String, user: String) {
        providerDao.findByIdentity(kind, url, user)?.let { if (it.id != p.id) throw SourceExistsException() }
    }

    /** Enregistre [next] (même id). Autre fournisseur : dates de synchro remises à zéro et filtre par catégorie à redécouvrir. */
    private suspend fun saveEdit(old: ProviderEntity, next: ProviderEntity, providerChanged: Boolean): EditResult {
        val row = if (providerChanged) next.copy(categoryFilter = -1) else next
        if (row != old) providerDao.upsert(row)
        if (providerChanged) providerDao.resetSyncAt(old.id)
        return EditResult(old.id, providerChanged)
    }

    private fun editedName(old: ProviderEntity, name: String) = name.trim().take(64).ifEmpty { old.name }

    suspend fun editXtream(id: Long, name: String, baseUrl: String, username: String, password: String): EditResult {
        val p = editable(id, "XTREAM")
        val url = baseUrl.trim().trimEnd('/')
        val user = username.trim()
        noDuplicate(p, "XTREAM", url, user)
        return saveEdit(p, p.copy(name = editedName(p, name), baseUrl = url, username = user, password = password), SourceEdit.changesProvider(p, url, user))
    }

    /** Liste M3U par lien. Une adresse Xtream (get.php…) est convertie en Xtream Codes à la synchro, comme à l'ajout. */
    suspend fun editM3u(id: Long, name: String, url: String): EditResult {
        val p = editable(id, "M3U")
        val u = url.trim()
        noDuplicate(p, "M3U", u, "")
        return saveEdit(p, p.copy(name = editedName(p, name), baseUrl = u), SourceEdit.changesProvider(p, u, ""))
    }

    /** Playlist locale : nom seulement (le fichier se remplace avec [replaceM3uLocal]). */
    suspend fun editM3uLocal(id: Long, name: String): EditResult {
        val p = editable(id, "M3U_LOCAL")
        return saveEdit(p, p.copy(name = editedName(p, name)), false)
    }

    /** Playlist locale : nouveau fichier lu dans la MÊME source (chaînes connues gardées, voir [importLocalText]). */
    suspend fun replaceM3uLocal(id: Long, label: String, text: String) {
        val p = editable(id, "M3U_LOCAL")
        if (label.isNotBlank() && label != p.baseUrl) providerDao.upsert(p.copy(baseUrl = label))
        importLocalText(p.id, text)
    }

    /** Portail Stalker : connexion vérifiée d'abord (comme à l'ajout) ; l'identité de boîtier saisie est reprise. */
    suspend fun editStalker(id: Long, name: String, portal: String, mac: String, login: String, password: String, identity: com.ultratv.tv.nativeapp.data.stalker.StalkerIdentity): EditResult {
        val p = editable(id, "STALKER")
        val m = com.ultratv.tv.nativeapp.data.stalker.StalkerMac.normalize(mac) ?: throw IllegalArgumentException("mac")
        val url = portal.trim().trimEnd('/')
        noDuplicate(p, "STALKER", url, m)
        stalker.probe(com.ultratv.tv.nativeapp.data.stalker.StalkerClient.Account(url, m, login, password, identity))
        val packed = com.ultratv.tv.nativeapp.data.stalker.StalkerCreds.pack(login, password, identity)
        return saveEdit(p, p.copy(name = editedName(p, name), baseUrl = url, username = m, password = packed), SourceEdit.changesProvider(p, url, m))
    }

    /** Partage NAS : connexion vérifiée d'abord. */
    suspend fun editNas(id: Long, name: String, address: String, username: String, password: String): EditResult {
        val p = editable(id, Nas.KIND)
        val a = Nas.parseAddress(address) ?: throw IllegalArgumentException("url")
        val user = username.trim()
        nas.test(a, user, password)
        noDuplicate(p, Nas.KIND, a.url(), user)
        return saveEdit(p, p.copy(name = editedName(p, name), baseUrl = a.url(), username = user, password = password), SourceEdit.changesProvider(p, a.url(), user))
    }

    /**
     * Serveur Jellyfin / Emby. Identifiant, mot de passe et clé vides : la connexion actuelle (jeton) est gardée, seuls le nom
     * et l'adresse changent. Sinon : nouvelle connexion vérifiée, nouvel utilisateur et nouveau jeton.
     */
    suspend fun editMediaServer(id: Long, name: String, server: String, username: String, password: String, apiKey: String = ""): EditResult {
        val p = editable(id, MediaServer.JELLYFIN, MediaServer.EMBY)
        val base = MediaServer.normalizeServerUrl(server) ?: throw IllegalArgumentException("url")
        if (username.isBlank() && password.isEmpty() && apiKey.isBlank()) {
            return saveEdit(p, p.copy(name = editedName(p, name), baseUrl = base), SourceEdit.changesProvider(p, base, p.username))
        }
        val login = mediaServer.login(base, username.trim(), password, apiKey)
        noDuplicate(p, p.kind, base, login.userId)
        val changed = SourceEdit.changesProvider(p, base, p.username) || login.userId != p.username
        return saveEdit(p, p.copy(name = editedName(p, name), baseUrl = base, username = login.userId, password = login.token), changed)
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
    /** Renomme une source sans toucher à son accès (serveurs multimédias reçus du site). */
    suspend fun renameProvider(id: Long, name: String) {
        val n = name.trim().take(64).ifEmpty { return }
        val p = providerDao.byId(id) ?: return
        if (p.name != n) providerDao.upsert(p.copy(name = n))
    }

    suspend fun updateFromCloud(id: Long, name: String?, url: String, username: String, password: String) {
        val p = providerDao.byId(id) ?: return
        // Stalker : l'identité de boîtier saisie sur cet appareil ne part pas au cloud ; on la garde.
        val pw = if (p.kind == "STALKER") com.ultratv.tv.nativeapp.data.stalker.StalkerCreds.keepIdentity(password, p.password) else password
        providerDao.upsert(p.copy(name = name ?: p.name, baseUrl = url.trim().trimEnd('/'), username = username, password = pw))
        providerDao.resetSyncAt(id)
    }

    suspend fun setDefault(id: Long) {
        providerDao.deactivateAll()
        providerDao.activate(id)
    }

    suspend fun delete(id: Long) {
        channelDao.deleteForProvider(id)
        db.manualChannelDao().deleteForProvider(id)
        movieDao.deleteForProvider(id)
        seriesDao.deleteForProvider(id)
        categoryDao.deleteForProviderKind(id, "LIVE")
        categoryDao.deleteForProviderKind(id, "MOVIE")
        categoryDao.deleteForProviderKind(id, "SERIES")
        providerDao.delete(id)
        newCategories?.forgetProvider(id)
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
                val t0 = System.nanoTime()
                val kind0 = providerDao.byId(providerId)?.kind.orEmpty()
                com.ultratv.tv.nativeapp.UtvLog.i(com.ultratv.tv.nativeapp.UtvLog.SYNC, "début $kind0 '$name' force=$force")
                try {
                    syncStatus.clearPartial(providerId)
                    syncAllInternal(providerId, onProgress, force).also {
                        syncStatus.clearFailure(providerId)
                        val lib = if (MediaServer.isMediaKind(kind0) || Nas.isNasKind(kind0)) " films=${movieDao.observeCount(providerId).first()} séries=${seriesDao.observeCount(providerId).first()}" else ""
                        if (MediaServer.isMediaKind(kind0) || Nas.isNasKind(kind0)) {
                            val perLib = listOf("MOVIE", "SERIES").flatMap { k -> categoryDao.forProviderKind(providerId, k).map { c -> "${c.name}=${if (k == "MOVIE") movieDao.countInCategory(providerId, c.remoteId) else seriesDao.countInCategory(providerId, c.remoteId)}" } }
                            com.ultratv.tv.nativeapp.UtvLog.i(com.ultratv.tv.nativeapp.UtvLog.MEDIA, "indexé '$name': ${perLib.joinToString(", ").ifEmpty { "aucune bibliothèque" }}")
                        }
                        com.ultratv.tv.nativeapp.UtvLog.i(com.ultratv.tv.nativeapp.UtvLog.SYNC, "fin $kind0 '$name': compte=$it$lib ${com.ultratv.tv.nativeapp.UtvLog.ms(t0)} ms")
                    }
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    com.ultratv.tv.nativeapp.UtvLog.e(com.ultratv.tv.nativeapp.UtvLog.SYNC, "échec $kind0 '$name' après ${com.ultratv.tv.nativeapp.UtvLog.ms(t0)} ms: ${com.ultratv.tv.nativeapp.UtvLog.why(t)}")
                    syncStatus.fail(SyncStatusBus.Failure(providerId, name, com.ultratv.tv.nativeapp.data.net.NetErrors.classifyFor(t, runCatching { providerDao.byId(providerId)?.baseUrl }.getOrNull())))
                    throw t
                }
            }
        }.await()
    }

    private val syncScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO + com.ultratv.tv.nativeapp.BackgroundTasks.handler("sync"))
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
            if (prefs.flow.first().syncEpg && hasGuideFeed(p) &&
                SyncPolicy.epgNeedsRefresh(p.lastEpgSyncAt, epgLastEnd(p), System.currentTimeMillis())) syncXmltv(p.id, onProgress)
            return channelDao.count(p.id)
        }
        val now = System.currentTimeMillis()
        val u = prefs.flow.first()
        val a = adaptive.state.value
        val ttl = SyncPolicy.ttl(if (u.syncMode == "launch") 1 else u.syncIntervalHours)
        val enabledParts = buildSet { if (u.syncLive) add(SyncPart.LIVE); if (u.syncEpg) add(SyncPart.EPG); if (u.syncVod) add(SyncPart.VOD); if (u.syncSeries) add(SyncPart.SERIES) }
        val heavyOk = !(u.syncUnmeteredOnly && a.net.metered)
        val due = SyncPolicy.dueParts(p, now, ttl, channelDao.count(p.id), force || u.syncMode == "launch", enabledParts, heavyOk)
        com.ultratv.tv.nativeapp.UtvLog.i(com.ultratv.tv.nativeapp.UtvLog.SYNC, "parties dues ${p.kind}: ${due.joinToString(",").ifEmpty { "aucune" }} (heavyOk=$heavyOk)")
        if (due.isEmpty()) return 0

        if (p.kind == "M3U") return syncM3u(providerId, onProgress).also {
            providerDao.markSynced(p.id, SyncPart.LIVE, System.currentTimeMillis())
            // Guide annoncé par la playlist (url-tvg) : après les chaînes, sans jamais faire échouer la synchro.
            // `due` ne contient jamais EPG pour une M3U : le guide est relu s'il est périmé (ou jamais lu : premier ajout), y compris
            // le guide complémentaire seul d'une playlist sans url-tvg.
            if (u.syncEpg && (heavyOk || force)) {
                val fresh = providerDao.byId(p.id) ?: p
                if (SyncPart.EPG in due || SyncPolicy.epgNeedsRefresh(fresh.lastEpgSyncAt, epgLastEnd(fresh), System.currentTimeMillis()))
                    runCatching { syncXmltv(p.id, onProgress) }
            }
        }
        // Portail Stalker : même moteur (catégories, langues, purge, ordre du Direct), autres requêtes ; pas de guide XMLTV.

        // Connexions simultanées autorisées (user_info) : alimente les avertissements enregistrement/lecture et le timeshift.
        // En parallèle du reste : une requête de plus en série, c'était un aller-retour réseau avant le premier octet utile.
        if (p.kind == "XTREAM") syncScope.launch { xtream.fetchMaxConnections(p)?.let { limits.setMaxConnections(p.id, it) } }
        // Fuseau du serveur : l'heure de départ des URL de rattrapage s'écrit dans ce fuseau, pas celui de l'appareil.
        if (p.kind == "XTREAM") syncScope.launch { xtream.fetchServerTimezone(p)?.let { limits.setServerTimezone(p.id, it) } }

        // Pondération de la barre de progression : seules les parties dues comptent.
        val weights = mapOf(SyncPart.LIVE to 20, SyncPart.VOD to 35, SyncPart.SERIES to 20, SyncPart.EPG to 25)
        val total = due.sumOf { weights.getValue(it) }.toFloat()
        var doneBefore = 0
        fun pct(part: SyncPart, fraction: Float): Int =
            ((doneBefore + weights.getValue(part) * fraction.coerceIn(0f, 1f)) / total * 100).toInt().coerceIn(0, 99)

        // Le pourcentage ne recule jamais (la lecture d'une partie puis son écriture ont chacune leur estimation).
        // Appelé en parallèle par les lectures de catégories : calcul et émission sous verrou, sinon un fil
        // qui a lu l'ancien maximum publie après un autre et la barre recule (40 puis 33).
        var shown = 0
        val stepLock = Any()
        fun step(part: SyncPart, label: String, count: Int?, fraction: Float) {
            onProgress(label)
            synchronized(stepLock) {
                shown = maxOf(shown, pct(part, fraction))
                syncStatus.set(SyncStatusBus.Status(provider = p.name, step = label, percent = shown, part = part, count = count))
            }
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
                val known = categoryDao.forProviderKind(p.id, kind)
                val merged = CatalogPlan.merge(fetched, known, langs, u.includeMulti, u.includeUnknownLang, CatalogPlan.shouldMaskNew(p.kind, known))
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
            // Partie refusée par la garde (liste vide ou effondrée) : son catalogue est gardé, elle n'est pas marquée à jour,
            // et les AUTRES parties continuent (le guide ne dépend jamais des films). L'échec est publié à la fin.
            var refused: EmptyCatalogException? = null
            suspend fun <R> orRefused(block: suspend () -> R): R? = try { block() } catch (e: EmptyCatalogException) { if (refused == null) refused = e; null }
            for (part in due) {
                when (part) {
                    SyncPart.LIVE -> {
                        val n = orRefused { syncXtreamPart(p, livePart(p.kind), ::maybeLock, { c, n -> step(part, "Live channels: $n", n, c) }, null, { syncScope.launch { startPrefetch() } }) }
                        startPrefetch()   // direct par catégories (pas de liste complète) ou déjà lancé : sans effet si déjà fait
                        if (n == null) { doneBefore += weights.getValue(part); continue }
                        written += n
                    }
                    SyncPart.VOD -> {
                        if (p.kind == "STALKER") { step(part, "Movies", null, 0f); stalkerProgress[p.id] = { c, n -> step(part, "Movies: $n", n, c) } }
                        val n = try { orRefused { stalkerTolerant(p, "MOVIE") { syncXtreamPart(p, vodPart(p.kind), ::maybeLock, { c, n -> step(part, "Movies: $n", n, c) }, catsAhead[part]) } } } finally { stalkerProgress.remove(p.id) }
                        if (n == null) { doneBefore += weights.getValue(part); continue }   // réessayée à la prochaine synchro
                        written += n
                    }
                    SyncPart.SERIES -> {
                        if (p.kind == "STALKER") { step(part, "Series", null, 0f); stalkerProgress[p.id] = { c, n -> step(part, "Series: $n", n, c) } }
                        val n = try { orRefused { stalkerTolerant(p, "SERIES") { syncXtreamPart(p, seriesPart(p.kind), ::maybeLock, { c, n -> step(part, "Series: $n", n, c) }, catsAhead[part]) } } } finally { stalkerProgress.remove(p.id) }
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
            refused?.let { throw it }
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
        /** Éléments en base venus du fournisseur : référence de la garde anti-effacement ([CatalogGuard]). */
        val count: suspend (Long) -> Int = { 0 },
        /** Éléments encore rattachés à une catégorie : une catégorie absente de la liste reçue n'est retirée qu'à 0. */
        val countIn: suspend (Long, String) -> Int = { _, _ -> 0 },
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
        { all -> val rows = withoutHidden(all); val keep = liveIds; channelDao.upsertAll(liveRanks.stamp(if (keep.isEmpty()) rows else rows.map { c -> keep[c.remoteId]?.let { c.copy(id = it) } ?: c })) },
        count = { channelDao.countProvided(it) },
        countIn = { pid, c -> channelDao.countInCategory(pid, c) },
    )

    /** remoteId → identifiant des chaînes AVANT la synchro en cours (voir [livePart]). */
    @Volatile private var liveIds: Map<String, Long> = emptyMap()

    /** Rangs des catégories du Direct, lus après l'écriture de la table des catégories (voir [livePart] et [LiveOrder]). */
    @Volatile private var liveRanks: LiveOrder.Ranks = LiveOrder.NONE

    private fun vodPart(kind: String = "XTREAM") = PartSpec<com.ultratv.tv.nativeapp.data.db.MovieEntity>(
        "MOVIE",
        { p -> if (Nas.isNasKind(kind)) nas.categories(p, "MOVIE") else if (MediaServer.isMediaKind(kind)) mediaCategories(p, "MOVIE") else if (kind == "STALKER") stalker.vodCategories(p, "MOVIE") else xtream.fetchVodCategories(p) },
        { p, blk -> if (Nas.isNasKind(kind)) blk(nas.categories(p, "MOVIE").flatMap { nas.movies(p, it.remoteId) }.asSequence()) else if (MediaServer.isMediaKind(kind)) blk(mediaCategories(p, "MOVIE").flatMap { mediaServer.movies(p, it.remoteId) }.asSequence()) else if (kind == "STALKER") blk(stalkerVodAll(p, "MOVIE") { it.movies }.asSequence()) else xtream.withVodStreams(p, blk) },
        { p, c -> if (Nas.isNasKind(kind)) nas.movies(p, c) else if (MediaServer.isMediaKind(kind)) mediaServer.movies(p, c) else if (kind == "STALKER") stalkerVod(p, c).movies else xtream.vodOfCategory(p, c) },
        { it.categoryId }, { m, lang -> m.copy(lang = LanguageDetector.forItem(m.name, lang)) },
        { movieDao.deleteForProvider(it) }, { pid, ids -> movieDao.deleteForCategories(pid, ids) }, { pid, id -> movieDao.deleteForCategory(pid, id) },
        { movieDao.upsertAll(it) },
        incremental = IncrementalTable.MOVIES,
        keepCats = { pid -> stalkerFailedCats(pid, "MOVIE") },
        count = { movieDao.observeCount(it).first() },
        countIn = { pid, c -> movieDao.countInCategory(pid, c) },
    )

    private fun seriesPart(kind: String = "XTREAM") = PartSpec<com.ultratv.tv.nativeapp.data.db.SeriesEntity>(
        "SERIES",
        { p -> if (Nas.isNasKind(kind)) nas.categories(p, "SERIES") else if (MediaServer.isMediaKind(kind)) mediaCategories(p, "SERIES") else if (kind == "STALKER") stalkerSeriesCategories(p) else xtream.fetchSeriesCategories(p) },
        { p, blk -> if (Nas.isNasKind(kind)) blk(nas.categories(p, "SERIES").flatMap { nas.series(p, it.remoteId) }.asSequence()) else if (MediaServer.isMediaKind(kind)) blk(mediaCategories(p, "SERIES").flatMap { mediaServer.series(p, it.remoteId) }.asSequence()) else if (kind == "STALKER") blk(stalkerVodAll(p, "SERIES") { s -> s.series.map { it.series } }.asSequence()) else xtream.withSeries(p, blk) },
        { p, c -> if (Nas.isNasKind(kind)) nas.series(p, c) else if (MediaServer.isMediaKind(kind)) mediaServer.series(p, c) else if (kind == "STALKER") stalkerVod(p, c).series.map { it.series } else xtream.seriesOfCategory(p, c) },
        { it.categoryId }, { s2, lang -> s2.copy(lang = LanguageDetector.forItem(s2.name, lang)) },
        { seriesDao.deleteForProvider(it) }, { pid, ids -> seriesDao.deleteForCategories(pid, ids) }, { pid, id -> seriesDao.deleteForCategory(pid, id) },
        { seriesDao.upsertAll(it) },
        incremental = IncrementalTable.SERIES,
        keepCats = { pid -> stalkerFailedCats(pid, "SERIES") },
        count = { seriesDao.observeCount(it).first() },
        countIn = { pid, c -> seriesDao.countInCategory(pid, c) },
    )

    // ───────────── Serveurs Jellyfin / Emby : bibliothèques = catégories ─────────────

    /** Bibliothèques de films (`MOVIE`) ou de séries (`SERIES`) du serveur, sous forme de catégories du catalogue. */
    private suspend fun mediaCategories(p: ProviderEntity, kind: String): List<CategoryEntity> {
        val type = if (kind == "MOVIE") "movies" else "tvshows"
        return mediaServer.libraries(p).filter { it.type == type }.map { CategoryEntity(providerId = p.id, kind = kind, remoteId = it.id, name = it.name) }
    }

    /**
     * Ajoute un serveur Jellyfin ou Emby : se connecte (identifiant + mot de passe, ou clé API), puis enregistre l'adresse, l'identifiant
     * interne de l'utilisateur (`username`) et le jeton (`password`, chiffré au repos). Une erreur de connexion remonte à l'appelant
     * et RIEN n'est enregistré. Un serveur déjà ajouté pour le même utilisateur renvoie sa source (jeton rafraîchi).
     */
    suspend fun addMediaServer(kind: String, name: String, server: String, username: String, password: String, apiKey: String = ""): Long {
        require(MediaServer.isMediaKind(kind))
        val base = MediaServer.normalizeServerUrl(server) ?: throw IllegalArgumentException("url")
        val t0 = System.nanoTime()
        val login = try { mediaServer.login(base, username.trim(), password, apiKey) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (t: Throwable) {
            com.ultratv.tv.nativeapp.UtvLog.w(com.ultratv.tv.nativeapp.UtvLog.MEDIA, "$kind ${com.ultratv.tv.nativeapp.UtvLog.maskUrl(base)}: ${if (t is java.net.SocketTimeoutException) "injoignable (délai dépassé)" else "connexion refusée"} (${com.ultratv.tv.nativeapp.UtvLog.why(t)})"); throw t
        }
        com.ultratv.tv.nativeapp.UtvLog.i(com.ultratv.tv.nativeapp.UtvLog.MEDIA, "$kind ${com.ultratv.tv.nativeapp.UtvLog.maskUrl(base)}: connecté (${com.ultratv.tv.nativeapp.UtvLog.ms(t0)} ms)")
        providerDao.findByIdentity(kind, base, login.userId)?.let { existing ->
            providerDao.upsert(existing.copy(password = login.token)); providerDao.resetSyncAt(existing.id); return existing.id
        }
        return providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { hostOf(base) ?: if (kind == MediaServer.EMBY) "Emby" else "Jellyfin" },
                kind = kind, baseUrl = base, username = login.userId, password = login.token,
                active = false,    // explicit default is set in Settings; see setDefault
            ),
        )
    }

    /** Bibliothèques (sections) de chaque serveur multimédia / NAS, par nom de source : choix des bibliothèques visibles d'un profil. */
    suspend fun mediaLibraryNames(): Map<String, List<String>> =
        observeProviders().first().filter { MediaServer.isMediaKind(it.kind) || Nas.isNasKind(it.kind) }.associate { p ->
            p.name to listOf("MOVIE", "SERIES").flatMap { k -> categoryDao.forProviderKind(p.id, k).map { it.name } }.distinct()
        }

    /** Nombre de films et de séries indexés pour une source (ligne de la source dans Réglages › Sources). */
    suspend fun mediaCounts(providerId: Long): Pair<Int, Int> =
        movieDao.observeCount(providerId).first() to seriesDao.observeCount(providerId).first()

    // ───────────── Plex : connexion par code PIN ─────────────

    /** Demande un code à saisir sur plex.tv/link. */
    suspend fun plexCreatePin() = mediaServer.plex.createPin()

    /** Jeton du compte une fois le code validé, null avant. */
    suspend fun plexPollPin(id: Long) = mediaServer.plex.pollPin(id)

    /** Serveurs du compte Plex. */
    suspend fun plexServers(accountToken: String) = mediaServer.plex.servers(accountToken)

    /**
     * Ajoute un serveur Plex : choisit la meilleure connexion qui répond (locale, puis distante, puis relais) et enregistre
     * adresse, identifiant de machine (`username`) et jeton DU SERVEUR (`password`, chiffré au repos). Aucune connexion
     * joignable : [java.io.IOException] et RIEN n'est enregistré. Un serveur déjà ajouté renvoie sa source (jeton rafraîchi).
     */
    suspend fun addPlexServer(server: com.ultratv.tv.nativeapp.data.mediaserver.Plex.Server): Long {
        val t0 = System.nanoTime()
        val conn = mediaServer.plex.bestConnection(server)
        if (conn == null) {
            com.ultratv.tv.nativeapp.UtvLog.w(com.ultratv.tv.nativeapp.UtvLog.MEDIA, "plex '${server.name}': aucune connexion joignable sur ${server.connections.size} (${com.ultratv.tv.nativeapp.UtvLog.ms(t0)} ms)")
            throw java.io.IOException("plex: no reachable connection")
        }
        com.ultratv.tv.nativeapp.UtvLog.i(com.ultratv.tv.nativeapp.UtvLog.MEDIA, "plex '${server.name}': connexion ${if (conn.relay) "relais" else if (conn.local) "locale" else "distante"} ${com.ultratv.tv.nativeapp.UtvLog.maskUrl(conn.uri)} (${com.ultratv.tv.nativeapp.UtvLog.ms(t0)} ms)")
        val kind = com.ultratv.tv.nativeapp.data.mediaserver.Plex.KIND
        providerDao.findByIdentity(kind, conn.uri, server.machineId)?.let { existing ->
            providerDao.upsert(existing.copy(password = server.accessToken)); providerDao.resetSyncAt(existing.id); return existing.id
        }
        return providerDao.upsert(
            ProviderEntity(name = server.name.ifBlank { "Plex" }, kind = kind, baseUrl = conn.uri, username = server.machineId, password = server.accessToken, active = false),
        )
    }

    /**
     * Ajoute un partage NAS (SMB) : adresse `smb://hôte/partage[/dossier]`, identifiant et mot de passe (vides = invité). La connexion
     * est vérifiée d'abord : une erreur remonte à l'appelant et RIEN n'est enregistré. Le mot de passe est chiffré au repos comme
     * celui de toute source ; l'adresse stockée n'en contient jamais.
     */
    suspend fun addNas(name: String, address: String, username: String, password: String): Long {
        val a = Nas.parseAddress(address) ?: throw IllegalArgumentException("url")
        nas.test(a, username.trim(), password)
        providerDao.findByIdentity(Nas.KIND, a.url(), username.trim())?.let { existing ->
            providerDao.upsert(existing.copy(password = password)); providerDao.resetSyncAt(existing.id); return existing.id
        }
        return providerDao.upsert(
            ProviderEntity(
                name = name.ifBlank { a.rootName }, kind = Nas.KIND, baseUrl = a.url(), username = username.trim(), password = password,
                active = false,    // explicit default is set in Settings; see setDefault
            ),
        )
    }

    // ───────────── Assistant « Ma bibliothèque » : étape 3 (choix des bibliothèques) ─────────────

    /**
     * Bibliothèques de films et de séries d'un serveur Jellyfin / Emby / Plex qui vient d'être ajouté, pour l'étape 3 de l'assistant.
     * Null pour un partage NAS (dossiers connus seulement après le parcours du partage) ou si le serveur ne répond pas : l'appelant
     * lance alors directement la synchro.
     */
    suspend fun mediaLibraryChoices(providerId: Long): List<com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.LibraryChoice>? {
        val p = providerDao.byId(providerId) ?: return null
        if (!MediaServer.isMediaKind(p.kind)) return null
        return runCatching {
            mediaServer.libraries(p).map {
                com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.LibraryChoice(if (it.type == "movies") "MOVIE" else "SERIES", it.id, it.name)
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Désactive les bibliothèques décochées AVANT la première synchro : la fusion des catégories ([CatalogPlan.merge]) conserve
     * l'état `enabled` déjà enregistré, donc leurs titres ne sont jamais téléchargés.
     */
    suspend fun disableLibraries(providerId: Long, libs: List<com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.LibraryChoice>) {
        if (libs.isEmpty()) return
        categoryDao.upsertAll(libs.map { CategoryEntity(providerId = providerId, kind = it.kind, remoteId = it.remoteId, name = it.name, enabled = false) })
    }

    // ───────────── Portail Stalker : films et séries partagent la même liste VOD ─────────────

    /**
     * Dernière lecture VOD de chaque catégorie Stalker (films ET séries) : la partie « Séries », qui suit la partie
     * « Films », réutilise ces pages au lieu de les retélécharger (une catégorie de 2 000 titres = 140 requêtes).
     */
    private val stalkerVodCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, com.ultratv.tv.nativeapp.data.stalker.StalkerClient.VodSplit>>()

    private suspend fun stalkerVod(p: ProviderEntity, categoryId: String): com.ultratv.tv.nativeapp.data.stalker.StalkerClient.VodSplit {
        val key = "${p.id}:$categoryId"
        stalkerVodCache[key]?.let { (at, v) -> if (System.currentTimeMillis() - at < STALKER_VOD_CACHE_MS) return v }
        // Catégorie du module `type=series` (identifiant préfixé) : séries seulement, demandées à ce module.
        val v = if (categoryId.startsWith(com.ultratv.tv.nativeapp.data.stalker.StalkerClient.SERIES_PREFIX))
            com.ultratv.tv.nativeapp.data.stalker.StalkerClient.VodSplit(emptyList(), fetchWithBackoff { stalker.seriesOfCategory(p, categoryId) })
        else stalker.vodOfCategory(p, categoryId)   // chaque page a déjà ses nouvelles tentatives (StalkerClient.pageWithRetry)
        stalkerVodCache[key] = System.currentTimeMillis() to v
        return v
    }

    /**
     * Durée de vie des pages VOD gardées : couvre toute une synchro (la partie Séries relit les catégories lues par la
     * partie Films). À 30 min, un gros portail dont les films prenaient plus longtemps faisait tout retélécharger.
     */
    private val STALKER_VOD_CACHE_MS = 3 * 3_600_000L

    /** Progression de la partie en cours (fraction 0..1, éléments lus) pendant la lecture des catégories Stalker. */
    private val stalkerProgress = java.util.concurrent.ConcurrentHashMap<Long, (Float, Int) -> Unit>()

    /** Pause avant la seconde lecture des catégories sautées (portail surchargé) ; réduite dans les tests. */
    @Volatile internal var stalkerRetryPauseMs = 2_000L

    /**
     * Toutes les catégories VOD ACTIVES de [kind] (déjà fusionnées et écrites par le moteur), 3 à la fois. Une catégorie
     * en échec (erreur, délai, réponse vide) est journalisée et sautée ; son contenu précédent est gardé (voir [PartSpec.keepCats]).
     */
    private suspend fun <T> stalkerVodAll(p: ProviderEntity, kind: String, pick: (com.ultratv.tv.nativeapp.data.stalker.StalkerClient.VodSplit) -> List<T>): List<T> {
        val ids = categoryDao.forProviderKind(p.id, kind).filter { it.enabled }.map { it.remoteId }
        val gate = kotlinx.coroutines.sync.Semaphore(3)
        val failures = java.util.concurrent.ConcurrentHashMap<String, Exception>()
        val done = java.util.concurrent.atomic.AtomicInteger()
        val items = java.util.concurrent.atomic.AtomicInteger()
        val total = ids.size.coerceAtLeast(1)
        // La progression avance à chaque catégorie lue (avant : figée sur la valeur de fin du Direct pendant toute la
        // lecture des films, une heure sur un gros portail — « bloquée vers 23 % »). 90 % pour la lecture, le reste à l'écriture.
        fun tick(n: Int) { stalkerProgress[p.id]?.invoke(done.incrementAndGet().toFloat() / total * 0.9f, items.addAndGet(n)) }
        val byId = java.util.concurrent.ConcurrentHashMap<String, List<T>>()
        kotlinx.coroutines.coroutineScope {
            ids.map { id ->
                async {
                    gate.withPermit {
                        try {
                            val got = pick(stalkerVod(p, id)); byId[id] = got; tick(got.size)
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (e: Exception) {
                            failures[id] = e; tick(0)
                        }
                    }
                }
            }.forEach { it.await() }
        }
        // Seconde lecture des catégories sautées, UNE à la fois après une pause : un portail lent ou surchargé (3 catégories
        // × 3 pages en parallèle) répond souvent à la relecture. Avant, films et séries n'arrivaient qu'à une 2e synchro.
        // Bornée : au plus [STALKER_RETRY_MAX] catégories, arrêt après 3 nouveaux échecs de suite (portail en panne).
        val toRetry = ids.filter { failures.containsKey(it) }.take(STALKER_RETRY_MAX)
        if (toRetry.isNotEmpty()) {
            kotlinx.coroutines.delay(stalkerRetryPauseMs)
            var streak = 0
            var recovered = 0
            for (id in toRetry) {
                if (streak >= 3) break
                try {
                    byId[id] = pick(stalkerVod(p, id)); failures.remove(id); recovered++; streak = 0
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (e: Exception) {
                    failures[id] = e; streak++
                }
            }
            com.ultratv.tv.nativeapp.RemoteLog.info("StalkerSync", com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.retried(if (kind == "MOVIE") "vod" else "series", recovered, failures.size))
        }
        ids.filter { failures.containsKey(it) }.forEach { id ->
            stalkerSkip(p.id, kind, id, com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.STEP_LIST, stalkerType(kind, id), failures[id])
        }
        val out = ids.flatMap { byId[it].orEmpty() }
        com.ultratv.tv.nativeapp.RemoteLog.info("StalkerSync", com.ultratv.tv.nativeapp.data.stalker.StalkerSyncLog.summary(if (kind == "MOVIE") "vod" else "series", ids.size - failures.size, failures.size, out.size))
        return out
    }

    /** Catégories relues au plus pendant la seconde lecture d'une partie Stalker. */
    private val STALKER_RETRY_MAX = 20

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
        val read = all.associate { c -> c.remoteId to stalkerVodCache["${p.id}:${c.remoteId}"]?.takeIf { now - it.first < STALKER_VOD_CACHE_MS }?.second }
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
            if (spec.kind == LiveOrder.KIND) db.withTransaction {
                LiveOrder.apply(channelDao, p.id, liveRanks)
                // Chaînes ajoutées à la main : supprimées avec le reste par la synchro, réinjectées ici (voir ManualChannels).
                ManualChannels.reinject(db, p.id)
            }
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
        // Garde anti-effacement : plus AUCUNE catégorie alors que la base en a (compte expiré qui répond `[]`) → rien n'est
        // touché, la synchro échoue avec une cause claire. « Mes chaînes » (locale) ne compte pas.
        if (fetched.isEmpty()) CatalogGuard.check(spec.kind, before.count { it.remoteId != ManualChannels.CUSTOM_CAT }, 0)
        // Synchros suivantes (Xtream, Stalker ; jamais les bibliothèques) : une catégorie inconnue arrive masquée.
        val md = CatalogPlan.mergeDetailed(fetched, before, langs, u.includeMulti, u.includeUnknownLang, CatalogPlan.shouldMaskNew(p.kind, before))
        val merged = md.categories
        noteLangDisabled(p.id, spec.kind, merged, before, md.newMasked)
        val enabledIds = merged.filter { it.enabled }.map { it.remoteId }
        val disabledIds = merged.filter { !it.enabled }.map { it.remoteId }
        val langByCat = merged.associate { it.remoteId to it.lang }
        val enabledSet = enabledIds.toHashSet()
        // Catégories ABSENTES de la liste reçue (liste partielle d'un panneau surchargé, liste de démonstration d'un compte
        // expiré) : gardées telles quelles (masquage, verrou, ordre) tant que la partie n'est pas acceptée par la garde.
        val fetchedIds = merged.mapTo(HashSet()) { it.remoteId }
        val absent = before.filter { it.remoteId !in fetchedIds }
        db.withTransaction {
            categoryDao.deleteForProviderKind(p.id, spec.kind)
            // Les absentes d'abord, avec leur identifiant : les nouvelles (id 0) prennent les numéros suivants.
            categoryDao.upsertAll(absent + merged)
            // SQLite plafonne les paramètres à 999 : purge par paquets.
            disabledIds.chunked(500).forEach { spec.deleteCats(p.id, it) }
        }
        // Les catégories viennent d'être réécrites (nouveaux id) : rangs du Direct relus avant d'insérer les chaînes.
        if (spec.kind == LiveOrder.KIND) liveRanks = LiveOrder.ranks(categoryDao.forProviderKind(p.id, spec.kind))
        val n = try {
            syncPartItems(p, spec, merged, enabledIds, enabledSet, langByCat, progress, onDownloaded)
        } catch (e: EmptyCatalogException) {
            // Partie refusée : la table des catégories redevient EXACTEMENT celle d'avant (réglages de l'utilisateur compris).
            db.withTransaction {
                categoryDao.deleteForProviderKind(p.id, spec.kind)
                categoryDao.upsertAll(before)
                if (spec.kind == LiveOrder.KIND) LiveOrder.apply(channelDao, p.id, LiveOrder.ranks(before))
            }
            throw e
        }
        // Partie acceptée : une catégorie absente n'est retirée que si plus aucun élément n'y renvoie (jamais d'orphelins).
        // Partie acceptée : les nouvelles catégories masquées sont signalées (une liste refusée n'a rien créé).
        newCategories?.add(p.id, spec.kind, md.newMasked)
        val gone = absent.filter { it.remoteId != ManualChannels.CUSTOM_CAT && spec.countIn(p.id, it.remoteId) == 0 }.map { it.remoteId }
        if (gone.isNotEmpty()) db.withTransaction { gone.chunked(500).forEach { categoryDao.deleteRemoteIds(p.id, spec.kind, it) } }
        return n
    }

    /** Éléments d'une partie, catégories déjà écrites (voir [syncXtreamPartLocked]). Lève [EmptyCatalogException] si refusée. */
    private suspend fun <T : Any> syncPartItems(
        p: ProviderEntity,
        spec: PartSpec<T>,
        merged: List<CategoryEntity>,
        enabledIds: List<String>,
        enabledSet: Set<String>,
        langByCat: Map<String, String>,
        progress: (Float, Int) -> Unit,
        onDownloaded: () -> Unit,
    ): Int {
        // Référence de la garde anti-effacement : éléments des catégories actives (les désactivées viennent d'être purgées).
        val itemsBefore = spec.count(p.id)
        // Catégories reçues vides : effacées seulement à la fin, si au moins une catégorie a renvoyé quelque chose.
        val emptyCats = java.util.concurrent.ConcurrentLinkedQueue<String>()
        var strategy = CatalogPlan.strategy(merged.size, enabledIds.size, p.categoryFilter)
        var written = 0
        // Catégories qui ont renvoyé au moins un élément (règle des catégories vides, voir CatalogGuard.mayEmptyCategories).
        val responded = java.util.concurrent.atomic.AtomicInteger(0)
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
                    if (items.isEmpty()) { emptyCats += id; continue }       // vide : ne prouve rien, on essaie la suivante
                    if (items.any { spec.catOf(it) != null && spec.catOf(it) != id }) {
                        providerDao.setCategoryFilter(p.id, 0); strategy = SyncStrategy.GLOBAL_FILTERED
                    } else {
                        providerDao.setCategoryFilter(p.id, 1); verified = true
                        written += insertCategory(p, spec, id, items, langByCat); responded.incrementAndGet()
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
                                val n = when {
                                    items == null -> 0
                                    items.isEmpty() -> { emptyCats += id; 0 }
                                    else -> insertCategory(p, spec, id, items, langByCat).also { responded.incrementAndGet() }
                                }
                                progress(done.incrementAndGet().toFloat() / total, count.addAndGet(n))
                            }
                        }
                    }.forEach { it.await() }
                }
                emptyThenGuard(p, spec, itemsBefore, count.get(), emptyCats.toList(), responded.get())
                return count.get()
            }
        }

        // Requête globale en flux ; les éléments des catégories désactivées ne sont pas stockés.
        // Transaction unique : une liste coupée en route est annulée (l'ancien catalogue reste) au lieu d'être gardée à moitié.
        // Serveur qui filtre par catégorie : la coupure est rattrapée par une requête par catégorie active.
        return try {
            // Catégories vides notées pendant une vérification abandonnée (serveur sans filtre) : la liste complète les réécrit.
            streamAllInto(p, spec, strategy == SyncStrategy.GLOBAL, enabledSet, langByCat, progress, onDownloaded, itemsBefore)
        } catch (e: Exception) {
            val cut = e is java.io.IOException || e is kotlinx.serialization.SerializationException
            if (!cut || p.categoryFilter != 1 || enabledIds.isEmpty()) throw e
            // Repli catégorie par catégorie : mêmes règles que le mode « une requête par catégorie » (rien n'est vidé avant
            // la garde ; une majorité de catégories vides n'en vide aucune).
            var count = 0
            val empties = ArrayList<String>()
            for ((i, id) in enabledIds.withIndex()) {
                val items = fetchCat(p, spec, id)
                when {
                    items == null -> Unit
                    items.isEmpty() -> empties += id
                    else -> { count += insertCategory(p, spec, id, items, langByCat); responded.incrementAndGet() }
                }
                progress((i + 1f) / enabledIds.size, count)
            }
            emptyThenGuard(p, spec, itemsBefore, count, empties, responded.get())
            count
        }
    }

    /**
     * Fin d'une partie lue catégorie par catégorie : garde sur le TOTAL reçu (refus → rien n'est vidé), puis les catégories
     * revenues vides ne sont vidées que si elles restent minoritaires (panneau qui limite le débit : `[]` partout).
     */
    private suspend fun <T : Any> emptyThenGuard(p: ProviderEntity, spec: PartSpec<T>, itemsBefore: Int, received: Int, empties: List<String>, responded: Int) {
        guard(p, spec.kind, itemsBefore, received)
        if (empties.isEmpty()) return
        if (!CatalogGuard.mayEmptyCategories(empties.size, responded)) {
            com.ultratv.tv.nativeapp.RemoteLog.warn("SyncGuard", "catégories vides gardées partie=${spec.kind} vides=${empties.size} répondu=$responded")
            return
        }
        db.withTransaction { empties.chunked(500).forEach { spec.deleteCats(p.id, it) } }
    }

    /** Catégories NOUVELLES désactivées par la langue : la raison est retenue (réactivées avec leur langue, pas les autres). */
    private fun noteLangDisabled(pid: Long, kind: String, merged: List<CategoryEntity>, before: List<CategoryEntity>, newMasked: List<String> = emptyList()) {
        // Les nouvelles catégories masquées d'office ne sont PAS coupées par la langue : réactiver une langue ne les rouvre pas.
        val known = before.map { it.remoteId }.toHashSet().apply { addAll(newMasked) }
        langDisabled.add(pid, kind, merged.filter { !it.enabled && it.remoteId !in known }.map { it.remoteId })
    }

    private suspend fun <T : Any> streamAllInto(
        p: ProviderEntity, spec: PartSpec<T>, all: Boolean, enabledSet: Set<String>, langByCat: Map<String, String>,
        progress: (Float, Int) -> Unit,
        onDownloaded: () -> Unit = {},
        itemsBefore: Int = 0,
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
                    // Liste vide ou effondrée : exception DANS la transaction, rien n'est retiré (voir CatalogGuard).
                    guard(p, spec.kind, itemsBefore, n)
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
                val seqState = ChannelSeq(hiddenChannels.ids(p.id))   // masquées : aucun numéro
                seq.filter { all || spec.catOf(it) == null || spec.catOf(it) in enabledSet }
                    .map { spec.withLang(it, langByCat[spec.catOf(it)].orEmpty()) }
                    .forEachBatchAhead(adaptive.state.value.auto.insertBatch) { batch ->
                        val rows = @Suppress("UNCHECKED_CAST") (if (batch.firstOrNull() is com.ultratv.tv.nativeapp.data.db.ChannelEntity) seqState.assign(batch as List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>) as List<T> else batch)
                        spec.insert(rows)
                        n += rows.size
                        progress((n / 60_000f).coerceAtMost(0.95f), n)
                    }
                // Liste vide ou effondrée : l'exception annule la transaction, suppression comprise (voir CatalogGuard).
                guard(p, spec.kind, itemsBefore, n)
            }
        }
        return n
    }

    private suspend fun <T : Any> insertCategory(p: ProviderEntity, spec: PartSpec<T>, id: String, items: List<T>, langByCat: Map<String, String>): Int {
        val withLang = items.map { spec.withLang(it, langByCat[id].orEmpty()) }
        val rows = @Suppress("UNCHECKED_CAST") (if (withLang.firstOrNull() is com.ultratv.tv.nativeapp.data.db.ChannelEntity) ChannelSeq(hiddenChannels.ids(p.id)).assign(withLang as List<com.ultratv.tv.nativeapp.data.db.ChannelEntity>) as List<T> else withLang)
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
            catch (e: java.io.IOException) { if (e is com.ultratv.tv.nativeapp.data.stalker.StalkerListTooLongException) throw e /* sinon réessaie */ }
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
        // « Mes chaînes » n'existe pas côté serveur : jamais interrogée, ses chaînes sont réinjectées depuis la table locale.
        if (kind == "LIVE" && ManualChannels.CUSTOM_CAT in ids) {
            if (ids.size == 1) { partLock(kind).withLock { ManualChannels.reinject(db, p.id) }; return }
            return syncCategoriesNow(providerId, kind, ids.filter { it != ManualChannels.CUSTOM_CAT }).also { partLock(kind).withLock { ManualChannels.reinject(db, p.id) } }
        }
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
                // Chaînes ajoutées à la main de ces catégories : supprimées par insertCategory, remises en place.
                if (kind == "LIVE") ManualChannels.reinject(db, p.id)
            } finally { if (ownStatus) syncStatus.clear() }
        }
        // Nouvelles chaînes du direct : leur guide tout de suite (regroupé), pas à la synchro de la nuit.
        if (kind == "LIVE" && p.kind == "XTREAM") scheduleEpgRefresh(providerId)
    }

    private val epgScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO + com.ultratv.tv.nativeapp.BackgroundTasks.handler("epg"))
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
                    if (SyncPolicy.epgNeedsRefresh(p.lastEpgSyncAt, epgLastEnd(p), System.currentTimeMillis()))
                        runCatching { syncXmltv(p.id) }
                }
            }
        }
    }

    /**
     * Programmes terminés depuis plus de 3 h supprimés (le guide ne retenait jamais rien : la base grossissait), sauf ceux
     * des chaînes avec archive, dans leur archive (rattrapage des jours passés, #248).
     */
    private suspend fun purgePastEpg() {
        val now = System.currentTimeMillis()
        runCatching { epgDao.deletePast(now - EpgRetention.PAST_KEEP_MS, now) }
    }

    /** Plan d'import du guide : fenêtre normale, élargie au passé pour les seules chaînes avec archive (#248). */
    private suspend fun archivePlan(pid: Long): EpgRetention.Plan {
        val now = System.currentTimeMillis()
        val days = runCatching { channelDao.archiveDays(pid) }.getOrDefault(emptyList()).associate { it.id to it.catchupDays }
        return EpgRetention.Plan(now, XmltvParser.defaultWindow(now), days)
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
