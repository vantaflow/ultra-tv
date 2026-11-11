package com.ultratv.tv.nativeapp.data.stalker

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Faux portail Stalker pour les tests (MockWebServer, rien de réel : MAC et adresses fictives). Comportement des
 * vrais portails qui posent problème en direct :
 *  - `live.php` répond 403 sans les en-têtes MAG (User-Agent MAG, Cookie `mac=…`, jeton Bearer) ;
 *  - `play_token` n'est valable que pour la PREMIÈRE ouverture (458 ensuite) ;
 *  - `create_link` peut renvoyer `stream=` vide ([emptyStreamResponses] fois) ;
 *  - un flux direct est COUPÉ si le boîtier ne se manifeste plus (`watchdog/get_events`) : le premier signal doit
 *    arriver dans les [FIRST_GRACE_MS] (10 s) après l'ouverture, les suivants dans les [REPEAT_GRACE_MS] ; le test
 *    interroge [isCut] avec une horloge virtuelle ([clock]) au lieu d'attendre ;
 *  - les films (`/vod/…`) sont servis sans aucun contrôle.
 */
class FakeStalkerPortal(val mac: String = "00:1A:79:AB:CD:EF") {
    val server = MockWebServer()
    val createLinks = AtomicInteger()
    val handshakes = AtomicInteger()
    val liveRequests = CopyOnWriteArrayList<RecordedRequest>()
    val liveStatuses = CopyOnWriteArrayList<Int>()
    val vodRequests = CopyOnWriteArrayList<RecordedRequest>()
    /** Nombre de réponses create_link (itv) à renvoyer avec `stream=` vide avant d'en donner une complète. */
    @Volatile var emptyStreamResponses = 0
    /** Horloge du portail (ms) : virtuelle dans les tests, pour ne pas attendre vraiment. */
    @Volatile var clock: () -> Long = { System.currentTimeMillis() }
    val watchdogs = CopyOnWriteArrayList<Long>()
    /** Nombre d'ouvertures de live.php à refuser en 403 quel que soit les en-têtes (portail capricieux). */
    @Volatile var forbidNextLive = 0
    /** Nombre d'ouvertures de live.php à refuser en 503 (panne passagère du serveur de flux). */
    @Volatile var failLive5xx = 0
    @Volatile var setPlayedStatus = 200
    val setPlayedIds = CopyOnWriteArrayList<String>()
    val setPlayedRequests = CopyOnWriteArrayList<RecordedRequest>()
    private val opened = ConcurrentHashMap<String, Long>()
    @Volatile private var lastWatchdog: Long? = null
    private val usedTokens = ConcurrentHashMap.newKeySet<String>()
    private val tokenSeq = AtomicInteger()

    fun start(): FakeStalkerPortal {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when {
                    url.encodedPath == "/stalker_portal/server/load.php" -> api(request)
                    url.encodedPath == "/play/live.php" -> live(request)
                    url.encodedPath.startsWith("/vod/") -> { vodRequests += request; MockResponse().setBody("MKV") }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        return this
    }

    /** Le flux ouvert avec ce `play_token` est-il coupé à l'instant [now] faute de signal de présence ? */
    fun isCut(playToken: String, now: Long): Boolean {
        val start = opened[playToken] ?: return false
        val seen = lastWatchdog   // la présence est suivie par MAC, pas par flux
        return now - maxOf(start, seen ?: 0L) > (if (seen == null) FIRST_GRACE_MS else REPEAT_GRACE_MS)
    }

    fun shutdown() = server.shutdown()

    fun portalUrl(): String = server.url("/stalker_portal/c/").toString()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "text/javascript").setBody(body)

    private fun api(r: RecordedRequest): MockResponse {
        val u = r.requestUrl!!
        val key = "${u.queryParameter("type")}/${u.queryParameter("action")}"
        if (key == "watchdog/get_events" && !r.getHeader("Authorization").isNullOrEmpty()) {
            val t = clock(); watchdogs += t; lastWatchdog = t
            return json("""{"js":{"data":{"msgs":0}}}""")
        }
        if (key == "stb/handshake") return json("""{"js":{"token":"TOKEN${handshakes.incrementAndGet()}","random":"x"}}""")
        if (r.getHeader("Authorization").isNullOrEmpty()) return json("Authorization failed.")
        return when (key) {
            "itv/set_played" -> if (setPlayedStatus != 200) MockResponse().setResponseCode(setPlayedStatus) else { setPlayedIds += u.queryParameter("itv_id").orEmpty(); setPlayedRequests += r; json("""{"js":true}""") }
            "stb/get_profile" -> json("""{"js":{"id":"1","status":0}}""")
            "itv/create_link" -> {
                createLinks.incrementAndGet()
                val id = Regex("""/ch/(\d+)""").find(u.queryParameter("cmd").orEmpty())?.groupValues?.get(1) ?: "0"
                val empty = emptyStreamResponses > 0
                if (empty) emptyStreamResponses--
                val token = "PT${tokenSeq.incrementAndGet()}"
                json("""{"js":{"id":"$id","cmd":"ffmpeg ${server.url("/play/live.php")}?mac=x&stream=${if (empty) "" else id}&extension=ts&play_token=$token"}}""")
            }
            "vod/create_link" -> { createLinks.incrementAndGet(); json("""{"js":{"cmd":"auto ${server.url("/vod/abc.mkv")}"}}""") }
            else -> json("""{"js":[]}""")
        }
    }

    private fun live(r: RecordedRequest): MockResponse {
        liveRequests += r
        val u = r.requestUrl!!
        val mag = r.getHeader("User-Agent") == StalkerDevice.USER_AGENT && r.getHeader("X-User-Agent") == StalkerDevice.X_USER_AGENT &&
            r.getHeader("Cookie")?.contains("mac=" + java.net.URLEncoder.encode(mac, "UTF-8")) == true &&
            r.getHeader("Authorization")?.startsWith("Bearer TOKEN") == true
        val code = when {
            !mag || forbidNextLive > 0 -> { if (forbidNextLive > 0) forbidNextLive--; 403 }
            failLive5xx > 0 -> { failLive5xx--; 503 }
            u.queryParameter("stream").isNullOrEmpty() -> 404
            !usedTokens.add(u.queryParameter("play_token").orEmpty()) -> 458   // lien à usage unique
            else -> 200
        }
        if (code == 200) opened[u.queryParameter("play_token").orEmpty()] = clock()
        liveStatuses += code
        return MockResponse().setResponseCode(code).setBody(if (code == 200) "TS" else "")
    }

    companion object {
        const val FIRST_GRACE_MS = 10_000L
        const val REPEAT_GRACE_MS = 150_000L
    }
}
