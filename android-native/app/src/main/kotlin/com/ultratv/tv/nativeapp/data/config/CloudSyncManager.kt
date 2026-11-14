package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.profile.applySharedProfiles
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Une source liée au cloud, à retirer localement : en attente d'une confirmation si des données en dépendent. */
data class PendingRemoval(val localId: Long, val name: String, val dependents: Int)

data class CloudSyncState(
    val syncing: Boolean = false,
    val lastSyncAt: Long = 0L,
    val devices: List<CloudDevice> = emptyList(),
    val selfId: String? = null,
    val pending: List<PendingRemoval> = emptyList(),
    val failed: Boolean = false,
    /** Appareil révoqué depuis le tableau de bord : appairage oublié, sources reçues du cloud retirées. */
    val revoked: Boolean = false,
    /** Résumé du dernier passage. */
    val added: Int = 0, val updated: Int = 0, val removed: Int = 0,
) {
    val deviceCount: Int get() = devices.size
    val selfName: String get() = devices.firstOrNull { it.id == selfId }?.let { CloudSyncLogic.deviceDisplayName(it) }.orEmpty()
}

/**
 * Synchro des sources avec le compte cloud : récupération conditionnelle (ETag), fusion (voir [CloudSyncLogic]),
 * envoi / partage d'une source locale, renommage de l'appareil. Une seule synchro à la fois.
 */
@Singleton
class CloudSyncManager @Inject constructor(
    private val repo: ProviderRepository,
    private val source: CloudConfigSource,
    private val client: CloudSyncClient,
    private val links: CloudLinkStore,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
    private val db: UltraDb,
    private val limits: ProviderLimitsStore,
    private val sync: SyncCoordinator,
    private val categories: com.ultratv.tv.nativeapp.data.repo.CategoryManager,
    private val languages: com.ultratv.tv.nativeapp.data.repo.LanguageManager,
    private val statusBus: com.ultratv.tv.nativeapp.data.repo.SyncStatusBus,
    private val profileRepo: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
    private val profileLog: com.ultratv.tv.nativeapp.data.profile.ProfileSyncLog,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(CloudSyncState(lastSyncAt = links.lastSyncAt, devices = links.devices(), selfId = links.selfId()))
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    val isPaired: Boolean get() = tokens.isPaired

    private suspend fun workerBase(): String? {
        val raw = WorkerUrl.resolve(prefs.flow.first().workerBaseUrl)
        return WorkerUrl.normalize(raw, allowCleartext = com.ultratv.tv.nativeapp.BuildConfig.DEBUG)
    }

    /** Identifiant cloud d'une source locale (null = source locale privée, jamais envoyée). */
    fun cloudIdOf(localId: Long): String? = links.cloudIdOf(localId)
    fun sharedWith(localId: Long): Int = links.cloudIdOf(localId)?.let { links.sharedWith(it) } ?: 0

    /** Au moins une source liée n'autorise qu'une connexion et est reçue par plusieurs appareils. */
    suspend fun connectionWarning(): Boolean {
        val s = _state.value
        if (s.deviceCount < 2) return false
        return repo.observeProviders().first().any { p ->
            val cid = links.cloudIdOf(p.id) ?: return@any false
            CloudSyncLogic.connectionWarning(limits.maxConnections(p.id), links.sharedWith(cid))
        }
    }

    /**
     * Relit la configuration cloud et fusionne. [force] ignore l'ETag. Ne lève jamais : un échec réseau laisse l'état intact
     * (`failed`), un jeton révoqué est signalé par `NotPaired` dans le résultat.
     */
    suspend fun sync(force: Boolean = false): Result = mutex.withLock {
        if (!tokens.isPaired) {
            // Jeton déjà effacé par un refus du serveur (version précédente, ou appel TMDB / notes refusé avant la synchro)
            // alors que des sources liées au cloud sont encore là : c'est une révocation jamais appliquée. Un
            // dé-appairage depuis les Réglages efface aussi les liens (forget), il n'est donc pas concerné.
            if (hasCloudLinkedSources()) onRevoked()
            return Result.NotPaired
        }
        val base = workerBase() ?: return Result.Failed
        _state.value = _state.value.copy(syncing = true, failed = false)
        try {
            val fetched = source.withToken(base) { t -> client.fetch(base, t, if (force) null else links.etag()) }
            if (fetched.notModified) {
                links.lastSyncAt = System.currentTimeMillis()
                _state.value = _state.value.copy(syncing = false, lastSyncAt = links.lastSyncAt, added = 0, updated = 0, removed = 0)
                return Result.Done
            }
            val cfg = CloudSyncLogic.parseConfig(fetched.body)
            links.saveDevices(cfg.selfId, cfg.devices)
            val summary = apply(cfg)
            links.setEtag(fetched.etag)
            links.lastSyncAt = System.currentTimeMillis()
            stateScope.launch { syncSharedState() }
            _state.value = CloudSyncState(
                syncing = false, lastSyncAt = links.lastSyncAt, devices = cfg.devices, selfId = cfg.selfId,
                pending = summary.pending, added = summary.added, updated = summary.updated, removed = summary.removed,
            )
            Result.Done
        } catch (e: TokenRejectedException) {
            // Le serveur ne connaît plus ce jeton : l'appareil a été révoqué (perdu, prêté, vendu). Les sources reçues du
            // compte cloud (adresses et identifiants de l'abonnement) ne doivent pas rester dessus ; les sources ajoutées
            // à la main sur l'appareil (jamais partagées) sont gardées.
            onRevoked()
            Result.NotPaired
        } catch (e: RateLimitedException) {
            _state.value = _state.value.copy(syncing = false, failed = true); Result.Failed
        } catch (t: Throwable) {
            com.ultratv.tv.nativeapp.RemoteLog.warn("cloud", "sync failed: ${t.javaClass.simpleName} ${t.message?.take(160)}")
            android.util.Log.w("UltraCloud", "sync failed", t)
            _state.value = _state.value.copy(syncing = false, failed = true); Result.Failed
        }
    }

    sealed interface Result { data object Done : Result; data object NotPaired : Result; data object Failed : Result }

    // ── État partagé : favoris, positions de reprise, derniers vus (par source du compte, profils par NOM) ──

    private val stateMutex = Mutex()

    /** Échange l'état partagé de chaque source liée. Ne lève jamais. */
    suspend fun syncSharedState() {
        if (!tokens.isPaired) return
        val base = workerBase() ?: return
        stateMutex.withLock {
            // Profils d'abord : les favoris et l'historique reçus sont rattachés au NOM du profil.
            runCatching { syncProfilesLocked(base) }
                .onFailure { android.util.Log.w("UltraCloud", "profiles failed: ${it.javaClass.simpleName}") }
            for (p in repo.observeProviders().first()) {
                val cid = links.cloudIdOf(p.id) ?: continue
                runCatching { syncStateOf(base, p.id, cid) }
                    .onFailure { android.util.Log.w("UltraCloud", "shared state failed: ${it.javaClass.simpleName}") }
            }
        }
    }

    /** Profils partagés entre les appareils du compte (nom, couleur, profil Enfants ; jamais le PIN). */
    private suspend fun syncProfilesLocked(base: String) {
        val dao = db.profileDao()
        val local = com.ultratv.tv.nativeapp.data.profile.ProfileSyncLogic.local(dao.observeAll().first(), profileLog.all())
        val raw = source.withToken(base) { t -> client.syncProfiles(base, t, com.ultratv.tv.nativeapp.data.profile.ProfileSyncLogic.toJson(local)) } ?: return
        val remote = com.ultratv.tv.nativeapp.data.profile.ProfileSyncLogic.parse(raw)
        val actions = com.ultratv.tv.nativeapp.data.profile.ProfileSyncLogic.plan(dao.observeAll().first(), profileLog.all(), remote)
        if (actions.isNotEmpty()) with(profileRepo) { applySharedProfiles(dao, profileLog, actions) }
    }

    private suspend fun syncStateOf(base: String, pid: Long, cid: String) {
        val profiles = db.profileDao().observeAll().first().ifEmpty { listOf(com.ultratv.tv.nativeapp.data.profile.ProfileEntity(id = 1, name = "Principal", color = 0, initial = "P")) }
        val nameOf = profiles.associate { it.id to it.name.trim() }
        val idOf = profiles.associate { it.name.trim().lowercase() to it.id }
        val now = System.currentTimeMillis()
        // Favoris : état connu (dernier échange) mis à jour par l'état local (ajouts, retraits = tombes).
        val known = links.stateFavs(cid)?.let { SharedStateLogic.parseFavs(runCatching { org.json.JSONArray(it) }.getOrNull()) }.orEmpty().associateBy { it.key }
        val localFavs = db.favoriteDao().allForProvider(pid).mapNotNull { f -> nameOf[f.profileId]?.let { Triple(it, f.kind, f.remoteId) } }.toSet()
        val updated = SharedStateLogic.localFavorites(known, localFavs, now)
        // Historique : lignes modifiées depuis le dernier envoi.
        val since = links.stateHistSince(cid)
        val hist = db.watchHistoryDao().changedSince(pid, since).mapNotNull { h ->
            val pn = nameOf[h.profileId] ?: return@mapNotNull null
            SharedHist(pn, h.kind, h.remoteId, h.title, h.poster?.takeIf { it.startsWith("http") }, h.positionMs, h.durationMs, h.watchedAt, h.parentRemoteId)
        }
        val body = org.json.JSONObject()
            .put("fav", SharedStateLogic.favsToJson(updated.values))
            .put("hist", org.json.JSONArray().apply { hist.forEach { put(SharedStateLogic.histToJson(it)) } })
            .toString()
        val raw = source.withToken(base) { t -> client.syncState(base, t, cid, body) } ?: return
        val res = org.json.JSONObject(raw)
        val remoteFavs = SharedStateLogic.parseFavs(res.optJSONArray("fav"))
        // Favoris distants plus récents : appliqués aux profils du même nom.
        for (e in SharedStateLogic.remoteFavChanges(updated, remoteFavs)) {
            val prof = idOf[e.p.lowercase()] ?: continue
            if (e.on) db.favoriteDao().add(com.ultratv.tv.nativeapp.data.db.FavoriteEntity(pid, e.k, e.r, prof))
            else db.favoriteDao().remove(prof, pid, e.k, e.r)
        }
        links.setStateFavs(cid, SharedStateLogic.favsToJson((updated + remoteFavs.associateBy { it.key }).values).toString())
        // Reprises / derniers vus distants plus récents que la ligne locale.
        var maxAt = maxOf(since, hist.maxOfOrNull { it.at } ?: 0L)
        for (e in SharedStateLogic.parseHists(res.optJSONArray("hist"))) {
            val prof = idOf[e.p.lowercase()] ?: continue
            val local = db.watchHistoryDao().get(prof, pid, e.k, e.r)
            if (local != null && local.watchedAt >= e.at) continue
            val url = local?.streamUrl ?: when (e.k) {
                "LIVE" -> db.channelDao().byRemoteId(pid, e.r)?.streamUrl
                "MOVIE" -> db.movieDao().byRemoteId(pid, e.r)?.streamUrl
                "EPISODE" -> db.episodeDao().byRemoteId(pid, e.r)?.streamUrl
                else -> null
            } ?: ""
            db.watchHistoryDao().upsert(com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity(
                providerId = pid, kind = e.k, remoteId = e.r, title = e.t.ifBlank { local?.title.orEmpty() },
                poster = e.img ?: local?.poster, streamUrl = url, positionMs = e.pos, durationMs = e.dur, watchedAt = e.at,
                parentRemoteId = e.par ?: local?.parentRemoteId, profileId = prof,
            ))
            maxAt = maxOf(maxAt, e.at)
        }
        // Les lignes reçues ne doivent pas être renvoyées : le repère avance au plus récent vu.
        links.setStateHistSince(cid, maxAt)
    }

    private val stateScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    @Volatile private var watchingState = false

    /** Au démarrage : échange 60 s après chaque changement local de favoris ou d'historique, et toutes les 10 min. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun watchSharedState() {
        if (watchingState) return
        watchingState = true
        stateScope.launch {
            // 60 s de calme : pendant une lecture la position est enregistrée toutes les 30 s, on n'envoie qu'après.
            kotlinx.coroutines.flow.merge(db.favoriteDao().observeAll().map { 0L }, db.watchHistoryDao().observeLatestAt().map { it ?: 0L }, db.profileDao().observeAll().map { 1L })
                .debounce(60_000).collect { syncSharedState() }
        }
        // Changements venus des autres appareils : toutes les 10 min, seulement quand l'appli est affichée (box en veille :
        // plus de requête réseau toutes les 10 min ; le travail périodique de 6 h et le retour sur l'appli suffisent).
        stateScope.launch { while (true) { kotlinx.coroutines.delay(10 * 60_000L); if (com.ultratv.tv.nativeapp.ui.common.AppForeground.visible) { syncSharedState(); runCatching { syncOnForeground() } } } }
    }

    private val prefsScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    @Volatile private var watching = false

    /** À appeler une fois au démarrage : publie (anti-rebond 1,5 s) les changements faits par l'utilisateur. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun watchDisplayPrefs() {
        if (watching) return
        watching = true
        // Fin d'une synchro de catalogue : applique les réglages reçus qui attendaient des catégories.
        prefsScope.launch {
            statusBus.status.map { it != null }.distinctUntilChanged().collect { running ->
                if (running) return@collect
                val pendingIds = links.pendingPrefsIds().toSet()
                for (cid in pendingIds) runCatching {
                    val localId = links.localIdOf(cid) ?: run { links.setPendingPrefs(cid, null); null } ?: return@runCatching
                    val remote = DisplayPrefs.parse(org.json.JSONObject(links.pendingPrefs(cid) ?: return@runCatching)) ?: return@runCatching
                    if (remote.updatedAt > links.prefsAt(cid)) applyRemote(localId, cid, remote, isActive = repo.firstActiveId() == localId)
                    else links.setPendingPrefs(cid, null)
                }
                // Réconciliation : un état local qui diffère de la dernière version échangée (changement jamais publié,
                // catégories arrivées après coup…) est publié. Sans cela, la TV et le Mac divergeaient en silence.
                for (p in repo.observeProviders().first()) {
                    val cid = links.cloudIdOf(p.id) ?: continue
                    if (cid in links.pendingPrefsIds() || links.prefsAt(cid) <= 0L) continue
                    publishPrefs(p.id)
                }
            }
        }
        prefsScope.launch {
            DisplayPrefsEvents.changes.debounce(1_500).collect { pid ->
                val id = if (pid == DisplayPrefsEvents.ACTIVE) repo.firstActiveId() else pid
                if (id != null) publishPrefs(id)
            }
        }
    }

    // ── Réglages d'affichage partagés (langues + catégories désactivées) ──────────────────────────────

    /** État local d'une source, au format du protocole. */
    private suspend fun localPrefs(localId: Long): LocalDisplayPrefs {
        val p = prefs.flow.first()
        val selected = p.languages.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val dao = db.categoryDao()
        val disabled = DisplayPrefs.KINDS.keys.associateWith { k -> dao.disabledIds(localId, k).toSet() }
        val order = DisplayPrefs.KINDS.keys.associateWith { k -> dao.orderedIds(localId, k) }
        val names = db.profileDao().observeAll().first().associate { it.id to it.name.trim() }
        val chOrder = db.channelOrderDao().allForProvider(localId).groupBy { it.profileId to it.scope }
            .mapNotNull { (key, rows) -> names[key.first]?.let { n -> ChannelOrderShare(n, key.second, rows.sortedBy { it.position }.map { it.remoteId }) } }
        return LocalDisplayPrefs(selected, p.includeMulti, p.includeUnknownLang, disabled, order, categories.hiddenOf(localId), chOrder)
    }

    /** Applique les réglages cloud plus récents que ceux déjà appliqués/publiés (source par source). */
    private suspend fun applyPrefs(cfg: CloudConfig) {
        if (!prefs.flow.first().syncDisplayPrefs) return
        val active = repo.firstActiveId()
        for (c in cfg.providers) {
            val remote = c.prefs ?: continue
            val localId = links.localIdOf(c.id) ?: continue
            if (remote.updatedAt <= links.prefsAt(c.id)) continue
            applyRemote(localId, c.id, remote, isActive = localId == active)
        }
    }

    private suspend fun applyRemote(localId: Long, cloudId: String, remote: CloudPrefs, isActive: Boolean) {
        val dao = db.categoryDao()
        // Catalogue pas encore chargé (appairage tout neuf) : on garde ces réglages et on les applique à la fin de la synchro.
        if (DisplayPrefs.KINDS.keys.all { dao.remoteIds(localId, it).isEmpty() }) {
            links.setPendingPrefs(cloudId, DisplayPrefs.toJson(remote))
            // Les langues, elles, valent tout de suite : la première synchro ne charge pas les langues masquées.
            if (isActive) DisplayPrefs.langsFromCloud(remote.langs).let { (sel, multi, unknown) -> languages.change(sel, multi, unknown) }
            return
        }
        links.setPendingPrefs(cloudId, null)
        // Langues d'abord (réglage du profil, appliqué pour la source affichée) : les listes de catégories désactivées reçues,
        // plus précises, passent ensuite par-dessus.
        if (isActive) {
            val (sel, multi, unknown) = DisplayPrefs.langsFromCloud(remote.langs)
            // Même chemin que l'écran Langues : une langue masquée est purgée, une langue réactivée relance la synchro de la source.
            languages.change(sel, multi, unknown)
        }
        var incomplete = false
        // Même chemin que l'écran Catégories : désactiver retire le contenu, réactiver recharge ces catégories.
        for ((kind, key) in DisplayPrefs.KINDS) {
            val off = remote.disabled[key].orEmpty().toSet()
            val all = dao.remoteIds(localId, kind)
            // Type pas encore chargé (séries après le direct…) : ces réglages restent EN ATTENTE pour lui, sinon ses
            // catégories arrivaient ensuite toutes actives alors que le réglage était marqué « appliqué ».
            if (all.isEmpty()) { if (off.isNotEmpty()) incomplete = true; continue }
            val wasOff = dao.disabledIds(localId, kind).toSet()
            categories.setEnabled(localId, kind, all.filter { it in off && it !in wasOff }, false)
            categories.setEnabled(localId, kind, all.filter { it !in off && it in wasOff }, true)
        }
        // Ordre des catégories (seulement s'il a été transmis) : les catégories connues reprennent l'ordre reçu, les
        // autres (absentes de l'appareil qui l'a envoyé) gardent leur place relative, à la suite.
        remote.order?.let { ord ->
            for ((kind, key) in DisplayPrefs.KINDS) {
                val wanted = ord[key].orEmpty()
                if (wanted.isEmpty()) continue
                val cur = dao.orderedIds(localId, kind)
                if (cur.isEmpty()) continue
                val known = cur.toSet()
                val next = (wanted.filter { it in known } + cur.filter { it !in wanted.toSet() }).distinct()
                if (next != cur) categories.saveOrder(localId, kind, next)
            }
        }
        // Chaînes masquées (seulement si transmises) : masquer les nouvelles, réafficher celles qui ne le sont plus.
        remote.hidden?.let { want ->
            val have = categories.hiddenOf(localId)
            val wantIds = want.map { it.id }.toSet(); val haveIds = have.map { it.id }.toSet()
            categories.hideChannels(localId, want.filter { it.id !in haveIds })
            categories.showChannels(localId, haveIds - wantIds)
        }
        // Ordre personnel des chaînes (seulement s'il a été transmis) : profils rattachés par NOM ; listes absentes = ordre naturel.
        remote.chOrder?.let { want ->
            val ids = db.profileDao().observeAll().first().associate { it.name.trim().lowercase() to it.id }
            val od = db.channelOrderDao()
            val have = od.allForProvider(localId).groupBy { it.profileId to it.scope }
            val wanted = want.mapNotNull { c -> ids[c.p.trim().lowercase()]?.let { pid -> (pid to c.s) to c.k } }.toMap()
            for ((key, _) in have) if (key !in wanted) od.clear(key.first, localId, key.second)
            for ((key, keys) in wanted) {
                val cur = have[key]?.sortedBy { it.position }?.map { it.remoteId }
                if (cur == keys) continue
                od.clear(key.first, localId, key.second)
                od.upsertAll(keys.mapIndexed { i, k -> com.ultratv.tv.nativeapp.data.db.ChannelOrderEntity(key.first, localId, key.second, k, i + 1) })
            }
        }
        if (incomplete) { links.setPendingPrefs(cloudId, DisplayPrefs.toJson(remote)); return }
        links.setPrefs(cloudId, remote.updatedAt, DisplayPrefs.fingerprint(DisplayPrefs.toCloud(localPrefs(localId), 0)))
    }

    /**
     * Publie les réglages de la source affichée s'ils ont changé depuis la dernière version appliquée/publiée.
     * Appelé (avec anti-rebond) quand les catégories ou les langues changent. Ne lève jamais.
     */
    suspend fun publishPrefs(localId: Long) {
        runCatching {
            if (!tokens.isPaired || !prefs.flow.first().syncDisplayPrefs) return
            val cloudId = links.cloudIdOf(localId) ?: return
            val now = System.currentTimeMillis()
            val local = DisplayPrefs.toCloud(localPrefs(localId), now)
            val print = DisplayPrefs.fingerprint(local)
            if (print == links.prefsPrint(cloudId) || DisplayPrefs.tooLarge(local)) return
            val base = workerBase() ?: return
            val winner = source.withToken(base) { t -> client.putPrefs(base, t, cloudId, DisplayPrefs.toJson(local)) }
            if (winner == null) links.setPrefs(cloudId, now, print)
            else applyRemote(localId, cloudId, winner, isActive = repo.firstActiveId() == localId)
        }
    }

    private data class Summary(val added: Int, val updated: Int, val removed: Int, val pending: List<PendingRemoval>)

    private suspend fun apply(cfg: CloudConfig): Summary {
        val locals = repo.observeProviders().first()
        val localModels = locals.filter { it.kind == "XTREAM" || it.kind == "M3U" || it.kind == "STALKER" }.map { localModel(it) }
        val dependents = localModels.filter { it.cloudId != null }.associate { it.localId to (db.favoriteDao().countForProvider(it.localId) + db.recordingDao().countForProvider(it.localId)) }
        val applied = cfg.providers.mapNotNull { c -> links.appliedName(c.id)?.let { c.id to it } }.toMap() +
            localModels.mapNotNull { l -> l.cloudId?.let { id -> links.appliedName(id)?.let { id to it } } }.toMap()
        var added = 0; var updated = 0; var removed = 0
        val pending = mutableListOf<PendingRemoval>()
        val deviceCount = cfg.devices.size
        for (a in CloudSyncLogic.plan(localModels, cfg.providers, dependents, applied)) when (a) {
            is SyncAction.Add -> {
                val id = when (a.cloud.kind) {
                    "XTREAM" -> repo.addXtream(a.cloud.name, a.cloud.url, a.cloud.username, a.cloud.password)
                    // MAC invalide reçue du cloud : la source est ignorée (aucune action sur les autres).
                    "STALKER" -> runCatching { repo.addStalker(a.cloud.name, a.cloud.url, a.cloud.mac, a.cloud.username, a.cloud.password) }.getOrNull() ?: continue
                    else -> repo.addM3u(a.cloud.name, a.cloud.url)
                }
                links.link(id, a.cloud.id, a.cloud.name, a.cloud.sharedWith(deviceCount))
                if (repo.firstActive() == null) repo.setDefault(id)
                // Appairage depuis l'assistant : une source Xtream attend l'étape Langues (sinon une
                // box modeste synchronisait d'emblée tout le catalogue — 50 000 chaînes, 180 000 films).
                if (!(CloudOnboarding.deferXtreamSync && a.cloud.kind == "XTREAM")) sync.request(id, force = true)
                added++
            }
            is SyncAction.Link -> links.link(a.localId, a.cloud.id, a.cloud.name, a.cloud.sharedWith(deviceCount))
            is SyncAction.Update -> {
                // Locale encore en M3U « get.php » face à sa version cloud normalisée en Xtream : on la convertit d'abord.
                if (a.cloud.kind == "XTREAM" && repo.canConvertToXtream(a.localId)) repo.convertToXtream(a.localId)
                if (a.cloud.kind == "STALKER") {
                    val mac = com.ultratv.tv.nativeapp.data.stalker.StalkerMac.normalize(a.cloud.mac) ?: continue
                    repo.updateFromCloud(a.localId, if (a.renameLocal) a.cloud.name else null, a.cloud.url, mac, com.ultratv.tv.nativeapp.data.stalker.StalkerCreds.pack(a.cloud.username, a.cloud.password))
                } else repo.updateFromCloud(a.localId, if (a.renameLocal) a.cloud.name else null, a.cloud.url, a.cloud.username, a.cloud.password)
                val keepName = if (a.renameLocal) a.cloud.name else (links.appliedName(a.cloud.id) ?: a.cloud.name)
                links.link(a.localId, a.cloud.id, keepName, a.cloud.sharedWith(deviceCount))
                sync.request(a.localId, force = true)
                updated++
            }
            is SyncAction.Remove ->
                if (a.needsConfirm) pending += PendingRemoval(a.localId, a.name, a.dependents)
                else { removeLocal(a.localId); removed++ }
        }
        // Partage affiché : mis à jour pour les sources liées déjà à jour.
        cfg.providers.forEach { c -> links.localIdOf(c.id)?.let { lid -> links.link(lid, c.id, links.appliedName(c.id) ?: c.name, c.sharedWith(deviceCount)) } }
        runCatching { applyPrefs(cfg) }
        return Summary(added, updated, removed, pending)
    }

    /** Source locale vue par la fusion ; portail Stalker : MAC à part, identifiant et mot de passe du portail dépaquetés. */
    private suspend fun localModel(p: com.ultratv.tv.nativeapp.data.db.ProviderEntity): LocalProvider {
        if (p.kind != "STALKER") return LocalProvider(p.id, p.kind, p.name, p.baseUrl, p.username, p.password, "", links.cloudIdOf(p.id))
        val (login, pass) = com.ultratv.tv.nativeapp.data.stalker.StalkerCreds.unpack(p.password)
        return LocalProvider(p.id, p.kind, p.name, p.baseUrl, login, pass, p.username, links.cloudIdOf(p.id))
    }

    private suspend fun removeLocal(localId: Long) {
        val wasDefault = repo.byId(localId)?.active == true
        repo.delete(localId)
        links.unlink(localId)
        if (wasDefault) repo.observeProviders().first().firstOrNull()?.let { repo.setDefault(it.id) }
    }

    /** L'utilisateur a confirmé le retrait de sources dont dépendent des données locales. */
    suspend fun confirmRemovals(ids: Set<Long>) = mutex.withLock {
        val pending = _state.value.pending
        pending.filter { it.localId in ids }.forEach { removeLocal(it.localId) }
        _state.value = _state.value.copy(pending = pending.filter { it.localId !in ids })
    }

    /** Garde la source localement : elle devient locale (le lien est rompu, elle n'est plus synchronisée). */
    suspend fun keepLocal(ids: Set<Long>) = mutex.withLock {
        ids.forEach { links.unlink(it) }
        _state.value = _state.value.copy(pending = _state.value.pending.filter { it.localId !in ids })
    }

    /**
     * Partage une source LOCALE (« Envoyer vers le cloud » / « Partager cette source »). [shareWith] : null = tous les appareils,
     * sinon la liste d'identifiants (l'appareil courant est toujours inclus par le Worker). Rien n'est envoyé sans cet appel.
     */
    suspend fun share(localId: Long, shareWith: List<String>?): Boolean = mutex.withLock {
        val base = workerBase() ?: return false
        val p = repo.byId(localId) ?: return false
        if (p.kind != "XTREAM" && p.kind != "M3U" && p.kind != "STALKER") return false
        val local = localModel(p)
        return try {
            val resp = source.withToken(base) { t -> client.put(base, t, CloudSyncLogic.uploadBody(local, shareWith, local.cloudId)) }
            val cid = JSONObject(resp).getJSONObject("provider").getString("id")
            val n = shareWith?.let { (it + listOfNotNull(links.selfId())).toSet().size } ?: _state.value.deviceCount
            links.link(localId, cid, p.name, n)
            links.resetVersion()
            true
        } catch (t: Throwable) { false }
    }

    /** Retire cet appareil de la source cloud (la source locale reste, redevient privée). */
    suspend fun stopSharing(localId: Long): Boolean = mutex.withLock {
        val base = workerBase() ?: return false
        val cid = links.cloudIdOf(localId) ?: return true
        return try {
            source.withToken(base) { t -> client.delete(base, t, cid) }
            links.unlink(localId); links.resetVersion(); true
        } catch (t: Throwable) { false }
    }

    suspend fun renameThisDevice(name: String): Boolean = mutex.withLock {
        val base = workerBase() ?: return false
        return try {
            source.withToken(base) { t -> client.renameSelf(base, t, name.trim()) }
            links.resetVersion(); true
        } catch (t: Throwable) { false }
    }

    private suspend fun hasCloudLinkedSources(): Boolean =
        runCatching { repo.observeProviders().first().any { links.cloudIdOf(it.id) != null } }.getOrDefault(false)

    private suspend fun onRevoked() {
        val linked = repo.observeProviders().first().filter { links.cloudIdOf(it.id) != null }.map { it.id }
        linked.forEach { runCatching { removeLocal(it) } }
        tokens.clear()
        links.clearAll()
        _state.value = CloudSyncState(revoked = true, removed = linked.size)
        CloudRevoked.flag.value = true
        com.ultratv.tv.nativeapp.RemoteLog.warn("cloud", "device revoked: ${linked.size} cloud source(s) removed")
    }

    /** Vérification au retour au premier plan : au plus une toutes les 2 min (révocation vue sans attendre 6 h). */
    @Volatile private var lastForegroundCheck = 0L
    suspend fun syncOnForeground() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundCheck < 2 * 60_000L) return
        lastForegroundCheck = now
        sync()
    }

    /** À appeler après un dé-appairage ou un changement de Worker : les liens ne valent plus rien. */
    fun forget() { links.clearAll(); _state.value = CloudSyncState() }
}

/** Révocation constatée pendant ce lancement : l'interface affiche le message une fois. */
object CloudRevoked { val flag = kotlinx.coroutines.flow.MutableStateFlow(false) }

/** Drapeau posé par l'assistant de première source pendant l'appairage cloud. */
object CloudOnboarding {
    @Volatile var deferXtreamSync: Boolean = false
}
