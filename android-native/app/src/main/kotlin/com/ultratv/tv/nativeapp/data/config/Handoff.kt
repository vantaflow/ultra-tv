package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.nav.DeepLink
import com.ultratv.tv.nativeapp.nav.DeepLinkHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reprise de la lecture d'un appareil à l'autre (« Envoyer vers… » / « Reprendre sur cet appareil ? »).
 * La demande ne contient JAMAIS d'URL de flux ni d'identifiants d'abonnement : une référence (source du compte, type,
 * identifiant chez le fournisseur, position) que l'appareil cible résout dans SON catalogue.
 */
data class HandoffRequest(
    val id: String,
    /** Nom de l'appareil émetteur. */
    val fromName: String,
    /** Identifiant cloud (8 hexadécimaux) de la source. */
    val cloudProviderId: String,
    /** "live" | "movie" | "episode". */
    val kind: String,
    val remoteId: String,
    /** Épisode : identifiant de la série chez le fournisseur. */
    val parentRemoteId: String?,
    val positionMs: Long,
    val title: String,
)

/** Logique pure (testée à part) : lecture de l'en-tête x-utv-handoff, corps d'envoi, lien profond de la cible. */
object HandoffLogic {
    /** Même durée de validité que la file du Worker. */
    const val TTL_MS = 120_000L
    private val KINDS = setOf("live", "movie", "episode")

    /** En-tête = JSON UTF-8 en base64url ; toute anomalie donne une liste vide (jamais d'exception). */
    fun parseHeader(header: String?): List<HandoffRequest> {
        if (header.isNullOrBlank() || header.length > 8192) return emptyList()
        return runCatching {
            val arr = JSONArray(String(java.util.Base64.getUrlDecoder().decode(header), Charsets.UTF_8))
            (0 until minOf(arr.length(), 3)).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("i"); val src = o.optString("s"); val rid = o.optString("r"); val t = o.optString("t")
                if (id.isEmpty() || src.isEmpty() || rid.isEmpty() || t !in KINDS) return@mapNotNull null
                HandoffRequest(
                    id = id, fromName = o.optString("f").take(64), cloudProviderId = src, kind = t, remoteId = rid,
                    parentRemoteId = o.optString("p").takeIf { it.isNotEmpty() },
                    positionMs = o.optLong("o", 0L).coerceAtLeast(0L), title = o.optString("n").take(100),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Type du contenu en cours de lecture (« LIVE » / « MOVIE » / « EPISODE ») → type de la demande. */
    fun wireKind(kind: String): String? = when (kind) { "LIVE" -> "live"; "MOVIE" -> "movie"; "EPISODE" -> "episode"; else -> null }

    /** Corps de POST /api/device/handoff. Le direct n'a pas de position. */
    fun body(toDeviceId: String, cloudProviderId: String, item: PlaybackContext.Item, positionMs: Long): String? {
        val kind = wireKind(item.kind) ?: return null
        val ref = JSONObject().put("src", cloudProviderId).put("t", kind).put("id", item.remoteId)
            .put("pos", if (kind == "live") 0L else positionMs.coerceAtLeast(0L)).put("n", item.title.take(100))
        if (kind == "episode") item.parentRemoteId?.let { ref.put("par", it) }
        return JSONObject().put("to", toDeviceId).put("ref", ref).toString()
    }

    /** Lien profond de la demande dans le catalogue local (providerId local de la source). */
    fun deepLink(localProviderId: Long, r: HandoffRequest): DeepLink = when (r.kind) {
        "live" -> DeepLink.PlayLive(localProviderId, r.remoteId)
        "movie" -> DeepLink.PlayMovie(localProviderId, r.remoteId)
        else -> DeepLink.PlayEpisode(localProviderId, r.remoteId)
    }
}

/**
 * Demandes reçues en attente de réponse. Alimentée par l'en-tête de la synchro (aucune interrogation de plus) ; une demande
 * ne démarre jamais seule et disparaît après 2 minutes sans réponse.
 */
@Singleton
class HandoffInbox @Inject constructor() {
    private val _pending = MutableStateFlow<List<Pair<HandoffRequest, Long>>>(emptyList())
    val pending: StateFlow<List<Pair<HandoffRequest, Long>>> = _pending.asStateFlow()
    /** Demandes déjà traitées : l'en-tête reste présent 2 minutes, on ne repose pas la question. */
    private val handled = LinkedHashSet<String>()

    fun note(header: String?, now: Long = System.currentTimeMillis()) {
        val items = HandoffLogic.parseHeader(header)
        val cur = _pending.value.filter { now - it.second < HandoffLogic.TTL_MS }
        val fresh = items.filter { r -> r.id !in handled && cur.none { it.first.id == r.id } }.map { it to now }
        if (fresh.isNotEmpty() || cur.size != _pending.value.size) _pending.value = (fresh + cur).take(3)
    }

    /** Retire la demande (réponse donnée ou expirée) et la mémorise comme traitée. */
    fun resolve(id: String) {
        handled.add(id)
        while (handled.size > 60) handled.remove(handled.first())
        _pending.value = _pending.value.filter { it.first.id != id }
    }

    fun expire(now: Long = System.currentTimeMillis()) {
        val keep = _pending.value.filter { now - it.second < HandoffLogic.TTL_MS }
        if (keep.size != _pending.value.size) _pending.value = keep
    }
}

/** « Oui » : résout la demande dans le catalogue de cet appareil et lance la lecture à la position reçue. */
@Singleton
class HandoffAccept @Inject constructor(
    private val links: CloudLinkStore,
    private val deepLinks: DeepLinkHandler,
    private val playback: PlaybackContext,
) {
    /** Faux si la source n'existe pas ici ou si le titre n'est plus dans le catalogue. */
    suspend fun accept(r: HandoffRequest): Boolean {
        val local = links.localIdOf(r.cloudProviderId) ?: return false
        if (r.kind != "live" && r.positionMs > 1000) playback.pendingStartMs = r.positionMs
        val ok = deepLinks.handle(HandoffLogic.deepLink(local, r))
        if (!ok) playback.pendingStartMs = null
        return ok
    }
}

sealed interface HandoffSend {
    data object Sent : HandoffSend
    /** La source en cours n'est pas liée au compte : l'autre appareil ne pourrait pas la retrouver. */
    data object NoSource : HandoffSend
    data object Failed : HandoffSend
}

/** « Envoyer vers… » : appareils du compte (hors celui-ci) et dépôt de la demande dans la file du Worker. */
@Singleton
class HandoffSender @Inject constructor(
    private val client: CloudSyncClient,
    private val source: CloudConfigSource,
    private val links: CloudLinkStore,
    private val tokens: DeviceTokenStore,
    private val prefs: UserPreferencesStore,
) {
    /** Autres appareils du compte (liste reçue avec la dernière synchro) ; vide si l'appareil n'est pas appairé. */
    fun otherDevices(): List<CloudDevice> {
        if (!tokens.isPaired) return emptyList()
        val self = links.selfId()
        return links.devices().filter { it.id != self }
    }

    suspend fun send(to: CloudDevice, item: PlaybackContext.Item, positionMs: Long): HandoffSend {
        val cloud = links.cloudIdOf(item.providerId) ?: return HandoffSend.NoSource
        val body = HandoffLogic.body(to.id, cloud, item, positionMs) ?: return HandoffSend.NoSource
        val base = WorkerUrl.normalize(WorkerUrl.resolve(prefs.flow.first().workerBaseUrl), allowCleartext = com.ultratv.tv.nativeapp.BuildConfig.DEBUG)
            ?: return HandoffSend.Failed
        return try {
            source.withToken(base) { t -> client.sendHandoff(base, t, body) }
            HandoffSend.Sent
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            HandoffSend.Failed
        }
    }
}
