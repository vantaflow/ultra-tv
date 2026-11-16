package com.ultratv.tv.nativeapp.data.trakt

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.traktAvailable
import com.ultratv.tv.nativeapp.i18n.traktAvailableChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mémoire LOCALE de la watchlist d'une source : quels titres n'étaient pas encore disponibles dans le catalogue, et
 * lesquels le sont devenus récemment. Tout est calculé sur l'appareil à partir du catalogue (base Room) ; le catalogue
 * n'est jamais envoyé au serveur.
 *
 * [unavailable] = identifiants (voir [TraktWatchlistTracker.id]) vus indisponibles au dernier calcul ;
 * [recent] = identifiant → date (ms) à laquelle le titre est devenu disponible.
 */
data class WatchlistState(val unavailable: Set<String> = emptySet(), val recent: Map<String, Long> = emptyMap())

/** Logique pure (testée sans Android) : détecte les titres de la watchlist devenus disponibles entre deux synchros. */
object TraktWatchlistTracker {
    /** Durée pendant laquelle un titre reste dans « Disponible de ta liste ». */
    const val RECENT_MS = 14L * 24 * 3600 * 1000

    /** Identifiant stable d'un titre de la watchlist : type + première clé de rapprochement (ou titre). */
    fun id(item: TraktItem): String = (if (item.isShow) "s:" else "m:") + (item.keys.firstOrNull() ?: item.title.lowercase())

    class Result(val state: WatchlistState, val newly: List<String>)

    /**
     * @param prev état précédent de cette source, null à la toute première fois (référence posée sans rien signaler :
     *   une watchlist déjà disponible ne déclenche pas une avalanche de notifications)
     * @param ids identifiants de la watchlist actuelle, dans l'ordre de Trakt
     * @param available ceux qui existent aujourd'hui dans le catalogue
     */
    fun update(prev: WatchlistState?, ids: List<String>, available: Set<String>, now: Long): Result {
        val onList = ids.toSet()
        val unavailable = LinkedHashSet<String>()
        val recent = LinkedHashMap<String, Long>()
        val newly = ArrayList<String>()
        for (id in ids) {
            if (id in available) {
                val at = prev?.recent?.get(id)
                if (at != null) { if (now - at < RECENT_MS) recent[id] = at }
                else if (prev != null && id in prev.unavailable) { recent[id] = now; newly += id }
            } else {
                unavailable += id
            }
        }
        // Retiré de la liste entre-temps : oublié (pas de ligne fantôme).
        recent.keys.retainAll(onList)
        return Result(WatchlistState(unavailable, recent), newly)
    }

    fun encode(byProvider: Map<Long, WatchlistState>): String = JSONObject().also { root ->
        for ((pid, st) in byProvider) root.put(pid.toString(), JSONObject()
            .put("u", JSONArray(st.unavailable.toList()))
            .put("r", JSONObject().also { o -> for ((k, v) in st.recent) o.put(k, v) }))
    }.toString()

    /** Jamais d'exception : fichier illisible = table vide (la référence sera reposée sans notification). */
    fun decode(json: String?): MutableMap<Long, WatchlistState> {
        val out = HashMap<Long, WatchlistState>()
        if (json.isNullOrBlank()) return out
        try {
            val root = JSONObject(json)
            for (k in root.keys()) {
                val pid = k.toLongOrNull() ?: continue
                val o = root.optJSONObject(k) ?: continue
                val u = LinkedHashSet<String>()
                o.optJSONArray("u")?.let { a -> for (i in 0 until a.length()) a.optString(i, "").takeIf { it.isNotEmpty() }?.let(u::add) }
                val r = LinkedHashMap<String, Long>()
                o.optJSONObject("r")?.let { ro -> for (key in ro.keys()) r[key] = ro.optLong(key, 0L) }
                out[pid] = WatchlistState(u, r)
            }
        } catch (_: Exception) { out.clear() }
        return out
    }

    /** Texte de la notification : au plus trois titres, puis « … » s'il y en a d'autres (aucun mot à traduire). */
    fun summary(titles: List<String>): String {
        val shown = titles.take(3).joinToString(", ")
        return if (titles.size > 3) "$shown…" else shown
    }
}

/**
 * Notification locale « Disponible de ta liste » : un titre de la watchlist (Trakt ou Simkl) vient d'apparaître dans le
 * catalogue de la source. Sans effet si les notifications sont refusées. Rien n'est envoyé au serveur.
 */
@Singleton
class WatchlistAvailabilityNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val prefs: UserPreferencesStore,
) {
    suspend fun notify(titles: List<String>) {
        if (titles.isEmpty()) return
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val d = runCatching { DesignStrings(AppLang.fromCode(prefs.flow.first().language)) }.getOrElse { DesignStrings(AppLang.System) }
        val title = d.traktAvailable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                androidx.core.app.NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName(d.traktAvailableChannel).build(),
            )
        }
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            android.app.PendingIntent.getActivity(ctx, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = TraktWatchlistTracker.summary(titles)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(ctx.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
            .setAutoCancel(true)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) { /* permission retirée entre-temps */ }
    }

    private companion object {
        const val CHANNEL_ID = "watchlist-available"
        const val NOTIF_ID = 0x7741
    }
}
