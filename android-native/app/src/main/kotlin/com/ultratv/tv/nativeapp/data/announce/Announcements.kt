package com.ultratv.tv.nativeapp.data.announce

import android.content.Context
import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Alerte programmée par l'administrateur (tableau de bord › Administration › Alertes). */
data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val level: String,
    /** Bouton d'action : [AnnouncementPlan.ACTION_UPDATE] ou null. */
    val action: String? = null,
)

/** Alerte déjà montrée, gardée sur l'appareil pour la rubrique « Messages » des Réglages. */
data class ReceivedAnnouncement(val id: String, val title: String, val message: String, val level: String, val at: Long)

/** Règles pures (testées sans Android). */
object AnnouncementPlan {
    const val URGENT = "urgent"
    const val INFO = "info"
    const val ACTION_UPDATE = "update"
    /** Alertes gardées dans « Messages ». */
    const val HISTORY_MAX = 20

    /** Bandeau discret (sans focus, disparaît seul) : une simple information sans bouton d'action. */
    fun isBanner(a: Announcement): Boolean = a.level == INFO && a.action == null

    /** Pendant la lecture plein écran, seules les urgentes s'affichent (en bandeau) ; les autres attendent. */
    fun showDuringPlayback(a: Announcement): Boolean = a.level == URGENT

    /** Version de l'application envoyée au Worker (ciblage) : « 1.2.79-debug » → « 1.2.79 ». */
    fun versionParam(versionName: String): String = Regex("^[0-9]+([.][0-9]+)*").find(versionName)?.value.orEmpty()

    /** Historique : l'alerte [a] ajoutée en tête (sans doublon), [HISTORY_MAX] au plus. */
    fun addToHistory(history: List<ReceivedAnnouncement>, a: Announcement, at: Long): List<ReceivedAnnouncement> =
        (listOf(ReceivedAnnouncement(a.id, a.title, a.message, a.level, at)) + history.filter { it.id != a.id }).take(HISTORY_MAX)

    /** Réponse de GET /api/announcements → alertes valides, dans l'ordre du serveur (la plus grave d'abord). */
    fun parse(body: String): List<Announcement> = runCatching {
        val arr = JSONObject(body).optJSONArray("items") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val msg = o.optString("message").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val action = o.optString("action").takeIf { it == ACTION_UPDATE }
            Announcement(id, o.optString("title").trim(), msg.trim(), o.optString("level", "info"), action)
        }
    }.getOrDefault(emptyList())

    /**
     * Prochaine alerte à montrer : « info » / « warning » une seule fois par appareil ([seen], gardé), « urgent » une
     * fois par lancement de l'appli ([shownThisLaunch]) tant qu'elle est diffusée.
     */
    fun next(items: List<Announcement>, seen: Set<String>, shownThisLaunch: Set<String>): Announcement? =
        items.firstOrNull { a -> if (a.level == URGENT) a.id !in shownThisLaunch else a.id !in seen }

    /**
     * Corps de la statistique d'alerte, ou null si la télémétrie est coupée (aucune statistique, donc aucun
     * identifiant d'installation, n'est alors envoyé).
     */
    fun statBody(kind: String, installId: () -> String, telemetryEnabled: Boolean): String? =
        if (!telemetryEnabled) null else JSONObject().put("kind", kind).put("device", installId()).toString()

    /** Identifiants vus encore utiles : ceux des alertes toujours diffusées (la liste ne grossit pas sans fin). */
    fun pruneSeen(seen: Set<String>, items: List<Announcement>, fetchedOk: Boolean): Set<String> =
        if (!fetchedOk) seen else seen.intersect(items.map { it.id }.toSet())
}

/** Lecture des alertes sur le Worker (route publique, sans appairage) et mémoire des alertes déjà vues. */
@Singleton
class AnnouncementRepository @Inject constructor(
    @ApplicationContext ctx: Context,
    private val http: OkHttpClient,
    private val prefs: UserPreferencesStore,
) {
    private val sp = ctx.getSharedPreferences(com.ultratv.tv.nativeapp.data.config.InstallId.PREFS, Context.MODE_PRIVATE)

    /** null : réseau ou Worker indisponible (on garde l'état, on réessaiera). */
    suspend fun fetch(audience: String): List<Announcement>? = withContext(Dispatchers.IO) {
        val base = WorkerUrl.normalize(prefs.flow.first().workerBaseUrl.ifBlank { BuildConfig.WORKER_URL }, BuildConfig.DEBUG) ?: return@withContext null
        runCatching {
            val version = AnnouncementPlan.versionParam(BuildConfig.VERSION_NAME)
            http.newCall(Request.Builder().url("$base/api/announcements?audience=$audience&version=$version").get().build()).execute().use { r ->
                if (!r.isSuccessful) null else AnnouncementPlan.parse(r.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    /**
     * Statistique de l'alerte [id] ([kind] : shown, dismissed, action) avec l'identifiant d'installation aléatoire de
     * l'appareil (compté une fois par appareil côté Worker). Sans effet si la télémétrie est coupée ou si le réseau manque : rien n'est rejoué.
     */
    suspend fun stat(id: String, kind: String) = withContext(Dispatchers.IO) {
        val p = prefs.flow.first()
        val json = AnnouncementPlan.statBody(kind, { installId() }, p.telemetryEnabled) ?: return@withContext
        val base = WorkerUrl.normalize(p.workerBaseUrl.ifBlank { BuildConfig.WORKER_URL }, BuildConfig.DEBUG) ?: return@withContext
        val body = json.toRequestBody("application/json".toMediaType())
        runCatching { http.newCall(Request.Builder().url("$base/api/announcements/$id/stat").post(body).build()).execute().close() }
    }

    /** Identifiant aléatoire propre à cette installation (aucun lien avec le compte ni le matériel). */
    private fun installId(): String = com.ultratv.tv.nativeapp.data.config.InstallId.of(sp)

    fun history(): List<ReceivedAnnouncement> = runCatching {
        val arr = org.json.JSONArray(sp.getString("history", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ReceivedAnnouncement(o.getString("id"), o.optString("title"), o.getString("message"), o.optString("level", "info"), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    fun remember(a: Announcement, at: Long = System.currentTimeMillis()) {
        val arr = org.json.JSONArray()
        AnnouncementPlan.addToHistory(history(), a, at).forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("message", it.message).put("level", it.level).put("at", it.at))
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    fun seen(): Set<String> = sp.getStringSet("seen", emptySet()).orEmpty().toSet()
    fun saveSeen(ids: Set<String>) { sp.edit().putStringSet("seen", ids).apply() }
}
