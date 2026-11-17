package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.BuildConfig
import com.ultratv.tv.nativeapp.data.extmeta.ExtResponse
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Accès DIRECT de l'appareil à l'API publique d'ESPN (football du jour et feuille de match). L'appareil fait sa propre
 * requête, avec un User-Agent honnête « UltraTV/<version> (Android) » : aucun jeton, aucun identifiant, rien d'autre que
 * l'adresse IP n'est visible d'ESPN. Interface : le dépôt se teste sans réseau.
 */
interface EspnApi {
    /** GET `https://site.api.espn.com/apis/site/v2/sports/soccer/all/[pathAndQuery]` ; IOException si le réseau échoue. */
    suspend fun get(pathAndQuery: String): ExtResponse?

    /** Aucun accès direct (tests, ou repli sur le Worker seul). */
    data object None : EspnApi {
        override suspend fun get(pathAndQuery: String): ExtResponse? = null
    }

    companion object {
        const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer/all"
    }
}

@Singleton
class EspnHttpApi @Inject constructor(okHttp: OkHttpClient) : EspnApi {
    private val http = okHttp.newBuilder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun get(pathAndQuery: String): ExtResponse? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${EspnApi.BASE}/$pathAndQuery")
            .header("User-Agent", "UltraTV/${BuildConfig.VERSION_NAME} (Android)")
            .header("Accept", "application/json")
            .get().build()
        http.newCall(req).execute().use { r -> ExtResponse(r.code, if (r.isSuccessful) r.body?.string() else null) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EspnModule {
    @Binds abstract fun api(impl: EspnHttpApi): EspnApi
}

/** Lecture des réponses d'ESPN : même modèle et mêmes règles que l'adaptateur du Worker (sportsespn.js : esEvent, esMatchBody). */
object EspnParser {
    private const val DAY_MAX = 150
    private const val RECENT_MS = 5 * 3_600_000L
    private val INT = Regex("^\\s*[+-]?[0-9]+")
    private val OFF = Regex("POSTPONED|CANCELED|CANCELLED|ABANDONED|SUSPENDED|DELAYED")

    // Comme `str` du Worker : texte rogné, borné, null si vide.
    private fun org.json.JSONObject.s(k: String, max: Int = 80): String? =
        if (isNull(k)) null else (opt(k) as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(max)

    // Comme `num` du Worker (parseInt) : entier de tête, « 55.3 » -> 55, « 4 » -> 4.
    private fun num(v: Any?): Int? = INT.find(v?.toString() ?: return null)?.value?.trim()?.removePrefix("+")?.toIntOrNull()

    private fun ms(iso: String?): Long? = runCatching { java.time.OffsetDateTime.parse(iso ?: return null).toInstant().toEpochMilli() }.getOrNull()

    private fun logo(t: org.json.JSONObject): String? =
        (t.s("logo", 400) ?: t.optJSONArray("logos")?.optJSONObject(0)?.s("href", 400))?.takeIf { it.startsWith("https://") }

    private fun name(t: org.json.JSONObject): String? = t.s("shortDisplayName") ?: t.s("displayName") ?: t.s("name")

    /** Un match ESPN (événement du tableau des scores, ou en-tête d'une feuille) ; null s'il manque l'identifiant ou une équipe. */
    internal fun event(id: String?, date: String?, status: org.json.JSONObject?, c: org.json.JSONObject): LeagueMatch? {
        if (id.isNullOrBlank()) return null
        val comps = c.optJSONArray("competitors") ?: return null
        val list = (0 until comps.length()).mapNotNull { comps.optJSONObject(it) }
        val home = list.firstOrNull { it.optString("homeAway") == "home" } ?: return null
        val away = list.firstOrNull { it.optString("homeAway") == "away" } ?: return null
        val type = status?.optJSONObject("type")
        var state = when (type?.optString("state")) { "in" -> MatchState.LIVE; "post" -> MatchState.POST; else -> MatchState.PRE }
        if (OFF.containsMatchIn(type?.optString("name").orEmpty())) state = MatchState.OFF
        val scored = state == MatchState.LIVE || state == MatchState.POST
        val progress = if (state != MatchState.LIVE) null
        else if (type?.optString("shortDetail") == "HT" || type?.optString("name") == "STATUS_HALFTIME") "HT"
        else status?.opt("displayClock")?.toString().orEmpty().replace("'", "").trim().take(12).ifEmpty { null }
        val ht = home.optJSONObject("team") ?: org.json.JSONObject()
        val at = away.optJSONObject("team") ?: org.json.JSONObject()
        val h = name(ht)
        val a = name(at)
        return LeagueMatch(
            "es-$id", ms(date ?: c.optString("date")), if (h != null && a != null) "$h vs $a" else null, h, a,
            logo(ht), logo(at), if (scored) num(home.opt("score")) else null, if (scored) num(away.opt("score")) else null,
            state, progress, null,
        )
    }

    internal fun scoreboardEvent(e: org.json.JSONObject): LeagueMatch? {
        val c = e.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val id = if (e.isNull("id")) null else e.opt("id")?.toString()
        return event(id, e.s("date") ?: c.s("date"), e.optJSONObject("status") ?: c.optJSONObject("status"), c)
    }

    /**
     * Football du jour (réponse de /scoreboard) : seuls les matchs en cours, ou terminés et commencés il y a moins de 5 h
     * (comme sportsDayProxy du Worker), 150 au plus. Null si le corps est illisible ou n'a pas de tableau « events ».
     */
    fun day(json: String?, now: Long): List<LeagueMatch>? = runCatching {
        val a = org.json.JSONObject(json ?: return null).optJSONArray("events") ?: return null
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::scoreboardEvent) }
            .filter { it.state == MatchState.LIVE || (it.state == MatchState.POST && it.startMs != null && now - it.startMs <= RECENT_MS) }
            .take(DAY_MAX)
    }.getOrNull()

    private fun kindOf(t: String): String? {
        val s = t.lowercase()
        return when {
            s.contains("goal") && !s.contains("kick") -> "goal"
            s == "yellow-card" || s == "red-card" || s == "yellow-red-card" -> "card"
            s.startsWith("substitution") -> "sub"
            else -> null
        }
    }

    private val STATS = linkedMapOf(
        "Possession" to "Possession", "SHOTS" to "Shots", "ON GOAL" to "Shots on target", "Corner Kicks" to "Corners",
        "Fouls" to "Fouls", "Offsides" to "Offsides", "Yellow Cards" to "Yellow cards", "Red Cards" to "Red cards", "Saves" to "Saves",
    )

    /** Feuille de match (réponse de /summary) : score, temps forts (60 au plus), statistiques, stade. Null si illisible. */
    fun sheet(json: String?): MatchSheet? = runCatching {
        val sum = org.json.JSONObject(json ?: return null)
        val header = sum.optJSONObject("header") ?: return null
        val hc = header.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val id = if (header.isNull("id")) null else header.opt("id")?.toString()
        val match = event(id, hc.s("date"), hc.optJSONObject("status"), hc) ?: return null
        val comps = hc.getJSONArray("competitors")
        val homeId = (0 until comps.length()).map { comps.getJSONObject(it) }.first { it.optString("homeAway") == "home" }
            .optJSONObject("team")?.opt("id")?.toString()
        val timeline = mutableListOf<TimelineItem>()
        val ke = sum.optJSONArray("keyEvents")
        for (n in 0 until (ke?.length() ?: 0)) {
            val k = ke!!.optJSONObject(n) ?: continue
            val kind = kindOf(k.optJSONObject("type")?.optString("type").orEmpty()) ?: continue
            val ppl = k.optJSONArray("participants")
            fun who(i: Int) = ppl?.optJSONObject(i)?.optJSONObject("athlete")?.s("displayName")
            val minute = num(k.optJSONObject("clock")?.optString("displayValue").orEmpty().replace(Regex("'.*$"), ""))
            val ty = k.optJSONObject("type")
            timeline += TimelineItem(
                kind, if (kind == "card") ty?.s("type", 40) else ty?.s("text", 40),
                k.optJSONObject("team")?.opt("id")?.toString() == homeId, minute, who(0), who(1),
            )
        }
        val teams = sum.optJSONObject("boxscore")?.optJSONArray("teams")
        val tl = (0 until (teams?.length() ?: 0)).mapNotNull { teams!!.optJSONObject(it) }
        val th = tl.firstOrNull { it.optString("homeAway") == "home" }
        val ta = tl.firstOrNull { it.optString("homeAway") == "away" }
        val stats = mutableListOf<StatItem>()
        if (th != null && ta != null) {
            fun v(t: org.json.JSONObject, label: String): Int? {
                val a = t.optJSONArray("statistics") ?: return null
                for (i in 0 until a.length()) { val o = a.optJSONObject(i) ?: continue; if (o.optString("label") == label) return num(o.opt("displayValue")) }
                return null
            }
            for ((label, k) in STATS) { val h = v(th, label); val a = v(ta, label); if (h != null && a != null) stats += StatItem(k, h, a) }
        }
        MatchSheet(match, sum.optJSONObject("gameInfo")?.optJSONObject("venue")?.s("fullName"), timeline.sortedBy { it.minute ?: 0 }.take(60), stats)
    }.getOrNull()
}
