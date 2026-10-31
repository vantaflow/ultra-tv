package com.ultratv.tv.nativeapp.data.announce

import android.content.Context
import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.config.WorkerUrl
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Alerte programmée par l'administrateur (tableau de bord › Administration › Alertes). */
data class Announcement(val id: String, val title: String, val message: String, val level: String)

/** Règles pures (testées sans Android). */
object AnnouncementPlan {
    const val URGENT = "urgent"

    /** Réponse de GET /api/announcements → alertes valides, dans l'ordre du serveur (la plus grave d'abord). */
    fun parse(body: String): List<Announcement> = runCatching {
        val arr = JSONObject(body).optJSONArray("items") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val msg = o.optString("message").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Announcement(id, o.optString("title").trim(), msg.trim(), o.optString("level", "info"))
        }
    }.getOrDefault(emptyList())

    /**
     * Prochaine alerte à montrer : « info » / « warning » une seule fois par appareil ([seen], gardé), « urgent » une
     * fois par lancement de l'appli ([shownThisLaunch]) tant qu'elle est diffusée.
     */
    fun next(items: List<Announcement>, seen: Set<String>, shownThisLaunch: Set<String>): Announcement? =
        items.firstOrNull { a -> if (a.level == URGENT) a.id !in shownThisLaunch else a.id !in seen }

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
    private val sp = ctx.getSharedPreferences("announcements", Context.MODE_PRIVATE)

    /** null : réseau ou Worker indisponible (on garde l'état, on réessaiera). */
    suspend fun fetch(audience: String): List<Announcement>? = withContext(Dispatchers.IO) {
        val base = WorkerUrl.normalize(prefs.flow.first().workerBaseUrl.ifBlank { BuildConfig.WORKER_URL }, BuildConfig.DEBUG) ?: return@withContext null
        runCatching {
            http.newCall(Request.Builder().url("$base/api/announcements?audience=$audience").get().build()).execute().use { r ->
                if (!r.isSuccessful) null else AnnouncementPlan.parse(r.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    fun seen(): Set<String> = sp.getStringSet("seen", emptySet()).orEmpty().toSet()
    fun saveSeen(ids: Set<String>) { sp.edit().putStringSet("seen", ids).apply() }
}
