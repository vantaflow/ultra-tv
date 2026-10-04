package com.ultratv.tv.nativeapp.data.config

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
    private val statusBus: com.ultratv.tv.nativeapp.data.repo.SyncStatusBus,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(CloudSyncState(lastSyncAt = links.lastSyncAt, devices = links.devices(), selfId = links.selfId()))
    val state: StateFlow<CloudSyncState> = _state.asStateFlow()

    val isPaired: Boolean get() = tokens.isPaired

    private suspend fun workerBase(): String? {
        val raw = prefs.flow.first().workerBaseUrl.ifBlank { com.ultratv.tv.nativeapp.BuildConfig.WORKER_URL }
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
        if (!tokens.isPaired) return Result.NotPaired
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
            _state.value = CloudSyncState(
                syncing = false, lastSyncAt = links.lastSyncAt, devices = cfg.devices, selfId = cfg.selfId,
                pending = summary.pending, added = summary.added, updated = summary.updated, removed = summary.removed,
            )
            Result.Done
        } catch (e: TokenRejectedException) {
            _state.value = _state.value.copy(syncing = false, failed = true)
            Result.NotPaired
        } catch (e: RateLimitedException) {
            _state.value = _state.value.copy(syncing = false, failed = true); Result.Failed
        } catch (t: Throwable) {
            _state.value = _state.value.copy(syncing = false, failed = true); Result.Failed
        }
    }

    sealed interface Result { data object Done : Result; data object NotPaired : Result; data object Failed : Result }

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
                for (cid in links.pendingPrefsIds()) runCatching {
                    val localId = links.localIdOf(cid) ?: run { links.setPendingPrefs(cid, null); null } ?: return@runCatching
                    val remote = DisplayPrefs.parse(org.json.JSONObject(links.pendingPrefs(cid) ?: return@runCatching)) ?: return@runCatching
                    if (remote.updatedAt > links.prefsAt(cid)) applyRemote(localId, cid, remote, isActive = repo.firstActive()?.id == localId)
                    else links.setPendingPrefs(cid, null)
                }
            }
        }
        prefsScope.launch {
            DisplayPrefsEvents.changes.debounce(1_500).collect { pid ->
                val id = if (pid == DisplayPrefsEvents.ACTIVE) repo.firstActive()?.id else pid
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
        return LocalDisplayPrefs(selected, p.includeMulti, p.includeUnknownLang, disabled)
    }

    /** Applique les réglages cloud plus récents que ceux déjà appliqués/publiés (source par source). */
    private suspend fun applyPrefs(cfg: CloudConfig) {
        if (!prefs.flow.first().syncDisplayPrefs) return
        val active = repo.firstActive()?.id
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
            return
        }
        links.setPendingPrefs(cloudId, null)
        // Même chemin que l'écran Catégories : désactiver retire le contenu, réactiver recharge ces catégories.
        for ((kind, key) in DisplayPrefs.KINDS) {
            val off = remote.disabled[key].orEmpty().toSet()
            val all = dao.remoteIds(localId, kind)
            if (all.isEmpty()) continue
            val wasOff = dao.disabledIds(localId, kind).toSet()
            categories.setEnabled(localId, kind, all.filter { it in off && it !in wasOff }, false)
            categories.setEnabled(localId, kind, all.filter { it !in off && it in wasOff }, true)
        }
        // Les langues sont un réglage du profil : appliquées pour la source affichée.
        if (isActive) {
            val (sel, multi, unknown) = DisplayPrefs.langsFromCloud(remote.langs)
            prefs.setLanguages(sel.sorted().joinToString(","))
            prefs.setIncludeMulti(multi)
            prefs.setIncludeUnknownLang(unknown)
        }
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
            else applyRemote(localId, cloudId, winner, isActive = repo.firstActive()?.id == localId)
        }
    }

    private data class Summary(val added: Int, val updated: Int, val removed: Int, val pending: List<PendingRemoval>)

    private suspend fun apply(cfg: CloudConfig): Summary {
        val locals = repo.observeProviders().first()
        val localModels = locals.filter { it.kind == "XTREAM" || it.kind == "M3U" }.map {
            LocalProvider(it.id, it.kind, it.name, it.baseUrl, it.username, it.password, "", links.cloudIdOf(it.id))
        }
        val dependents = localModels.filter { it.cloudId != null }.associate { it.localId to (db.favoriteDao().countForProvider(it.localId) + db.recordingDao().countForProvider(it.localId)) }
        val applied = cfg.providers.mapNotNull { c -> links.appliedName(c.id)?.let { c.id to it } }.toMap() +
            localModels.mapNotNull { l -> l.cloudId?.let { id -> links.appliedName(id)?.let { id to it } } }.toMap()
        var added = 0; var updated = 0; var removed = 0
        val pending = mutableListOf<PendingRemoval>()
        val deviceCount = cfg.devices.size
        for (a in CloudSyncLogic.plan(localModels, cfg.providers, dependents, applied)) when (a) {
            is SyncAction.Add -> {
                val id = if (a.cloud.kind == "XTREAM") repo.addXtream(a.cloud.name, a.cloud.url, a.cloud.username, a.cloud.password) else repo.addM3u(a.cloud.name, a.cloud.url)
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
                repo.updateFromCloud(a.localId, if (a.renameLocal) a.cloud.name else null, a.cloud.url, a.cloud.username, a.cloud.password)
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
        if (p.kind != "XTREAM" && p.kind != "M3U") return false
        val local = LocalProvider(p.id, p.kind, p.name, p.baseUrl, p.username, p.password, "", links.cloudIdOf(localId))
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

    /** À appeler après un dé-appairage ou un changement de Worker : les liens ne valent plus rien. */
    fun forget() { links.clearAll(); _state.value = CloudSyncState() }
}

/** Drapeau posé par l'assistant de première source pendant l'appairage cloud. */
object CloudOnboarding {
    @Volatile var deferXtreamSync: Boolean = false
}
