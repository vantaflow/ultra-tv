package com.ultratv.tv.nativeapp.data.stalker

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Faux portail Stalker EN MÉMOIRE (intercepteur OkHttp, aucun réseau, données fictives) pour la synchro Films / Séries :
 *  - séries servies UNIQUEMENT par le module `type=series` (la VOD n'a aucune catégorie) quand [vodCategories] est vide ;
 *  - une catégorie listée dans [failingCategories] répond toujours 500 ;
 *  - jeton qui expire en pleine synchro : après [expireAfterListRequests] pages, tous les jetons existants sont refusés
 *    (« Authorization failed. ») et une nouvelle poignée de main est exigée ;
 *  - pagination à [perPage] éléments par page, `total_items` annoncé (ou omis si [omitTotal]).
 * Seul `…/stalker_portal/server/load.php` répond (les autres points d'entrée sondés sont des 404).
 */
class FakeVodPortal(
    /** Catégories des séries (module type=series) : id → (titre, nombre de séries). */
    val seriesCategories: Map<String, Pair<String, Int>> = mapOf("1" to ("FR Séries" to 30), "2" to ("FR Drames" to 5), "3" to ("FR Cassée" to 4)),
    /** Catégories VOD : id → (titre, nombre de films). */
    val vodCategories: Map<String, Pair<String, Int>> = emptyMap(),
    val perPage: Int = 14,
) : Interceptor {
    val failingCategories: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile var omitTotal = false
    @Volatile var expireAfterListRequests = -1
    @Volatile var vodCreateLinkFails = false
    val handshakes = AtomicInteger()
    val listRequests = AtomicInteger()
    val urls = CopyOnWriteArrayList<okhttp3.HttpUrl>()
    @Volatile private var minValidToken = 0

    private fun body(code: Int, text: String, req: okhttp3.Request) = Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
        .code(code).message("x").body(text.toResponseBody("text/javascript".toMediaType())).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val u = req.url
        urls += u
        if (u.encodedPath != "/stalker_portal/server/load.php") return body(404, "", req)
        val type = u.queryParameter("type"); val action = u.queryParameter("action")
        if (type == "stb" && action == "handshake") return body(200, """{"js":{"token":"TOKEN${handshakes.incrementAndGet()}"}}""", req)
        val tok = req.header("Authorization")?.removePrefix("Bearer TOKEN")?.toIntOrNull() ?: return body(200, "Authorization failed.", req)
        if (tok < minValidToken) return body(200, "Authorization failed.", req)
        return when ("$type/$action") {
            "stb/get_profile" -> body(200, """{"js":{"id":"1","status":0}}""", req)
            "itv/get_genres" -> body(200, """{"js":[{"id":"*","title":"All"},{"id":"10","title":"FR Info"}]}""", req)
            "itv/get_all_channels" -> body(200, """{"js":{"data":[{"id":"100","name":"FR Info 1","cmd":"ffmpeg http://flux.invalid/ch/100","tv_genre_id":"10"}]}}""", req)
            "vod/get_categories" -> body(200, cats(vodCategories), req)
            "series/get_categories" -> body(200, cats(seriesCategories), req)
            "vod/get_ordered_list", "series/get_ordered_list" -> list(req, type!!)
            "vod/create_link" -> if (vodCreateLinkFails) body(200, """{"js":[]}""", req) else body(200, """{"js":{"cmd":"ffmpeg http://flux.invalid/vod/${u.queryParameter("series")}.mkv"}}""", req)
            "series/create_link" -> body(200, """{"js":{"cmd":"auto http://flux.invalid/series/${u.queryParameter("series")}.mkv"}}""", req)
            else -> body(200, """{"js":[]}""", req)
        }
    }

    private fun cats(m: Map<String, Pair<String, Int>>) =
        """{"js":[{"id":"*","title":"All"}""" + m.entries.joinToString("") { (id, v) -> ",{\"id\":\"$id\",\"title\":\"${v.first}\"}" } + "]}"

    private fun list(req: okhttp3.Request, type: String): Response {
        val u = req.url
        val n = listRequests.incrementAndGet()
        if (expireAfterListRequests in 1..n) { expireAfterListRequests = -1; minValidToken = handshakes.get() + 1; return body(200, "Authorization failed.", req) }
        val page = u.queryParameter("p")?.toIntOrNull() ?: 1
        val movieId = u.queryParameter("movie_id")
        if (movieId != null) return body(200, seasonsOrEpisodes(movieId, u.queryParameter("season_id") ?: "0"), req)
        val cat = u.queryParameter("category") ?: return body(200, """{"js":[]}""", req)
        if (cat in failingCategories) return body(500, "", req)
        val count = (if (type == "series") seriesCategories else vodCategories)[cat]?.second ?: 0
        val from = (page - 1) * perPage
        val items = (from until minOf(count, from + perPage)).joinToString(",") { i ->
            if (type == "series") """{"id":"$cat-$i","name":"FR Série $cat-$i","category_id":"$cat","added":"2026-01-0${1 + i % 9} 10:00:00"}"""
            else """{"id":"$cat-$i","name":"FR Film $cat-$i","cmd":"/media/$cat-$i.mpg","category_id":"$cat"}"""
        }
        val total = if (omitTotal) "" else """"total_items":$count,"""
        return body(200, """{"js":{$total"max_page_items":$perPage,"data":[$items]}}""", req)
    }

    /** Série → 2 saisons (`is_season`, tableau `series`) ; saison → 3 épisodes (`is_episode`, commande propre). */
    private fun seasonsOrEpisodes(movieId: String, seasonId: String): String {
        if (seasonId == "0") return """{"js":{"total_items":2,"max_page_items":14,"data":[""" +
            """{"id":"$movieId:1","name":"Saison 1","is_season":true,"series":[1,2,3],"cmd":"eyJzZWFzb24iOjF9"},""" +
            """{"id":"$movieId:2","name":"Saison 2","is_season":true,"series":[1,2,3],"cmd":"eyJzZWFzb24iOjJ9"}]}}"""
        return """{"js":{"total_items":3,"max_page_items":14,"data":[""" + (1..3).joinToString(",") { e ->
            """{"id":"$seasonId:$e","name":"Épisode $e","is_episode":true,"series_number":"$e","cmd":"eyJlcCI6$e}"}"""
        } + "]}}"
    }
}
