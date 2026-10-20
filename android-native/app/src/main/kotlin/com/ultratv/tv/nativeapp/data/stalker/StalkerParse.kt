package com.ultratv.tv.nativeapp.data.stalker

import android.util.JsonReader
import android.util.JsonToken
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.repo.ChannelNameParser
import com.ultratv.tv.nativeapp.data.repo.JunkFilter
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import com.ultratv.tv.nativeapp.data.xtream.XtreamAccount

/** Valeur JSON lue en arbre léger : Map / List / String (nombres compris) / Boolean / null. */
typealias JMap = Map<String, Any?>

/**
 * Lecture JSON en FLUX (android.util.JsonReader) : les réponses `get_all_channels` font plusieurs Mo, elles ne sont
 * jamais matérialisées en String. Les nombres sont rendus en texte (les portails mélangent "12" et 12).
 */
object StalkerJson {
    fun reader(r: java.io.Reader): JsonReader = JsonReader(r).apply { isLenient = true }

    fun parse(text: String): Any? = runCatching { reader(java.io.StringReader(text)).use { readAny(it) } }.getOrNull()

    fun readAny(r: JsonReader): Any? = when (r.peek()) {
        JsonToken.BEGIN_OBJECT -> {
            val m = LinkedHashMap<String, Any?>()
            r.beginObject()
            while (r.hasNext()) m[r.nextName()] = readAny(r)
            r.endObject()
            m
        }
        JsonToken.BEGIN_ARRAY -> {
            val l = ArrayList<Any?>()
            r.beginArray()
            while (r.hasNext()) l += readAny(r)
            r.endArray()
            l
        }
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean()
        JsonToken.NULL -> { r.nextNull(); null }
        else -> { r.skipValue(); null }
    }

    /**
     * Parcourt `{"js":{"data":[…]}}` (ou `{"js":[…]}`) en flux et passe chaque objet de `data` à [onItem].
     * Renvoie false si la réponse n'a pas cette forme (portail qui refuse : « Authorization failed. », page HTML…).
     */
    fun forEachDataItem(input: java.io.Reader, onItem: (JMap) -> Unit): Boolean {
        val r = reader(input)
        if (r.peek() != JsonToken.BEGIN_OBJECT) return false
        r.beginObject()
        var found = false
        while (r.hasNext()) {
            if (r.nextName() != "js") { r.skipValue(); continue }
            when (r.peek()) {
                JsonToken.BEGIN_ARRAY -> { found = true; readArrayItems(r, onItem) }
                JsonToken.BEGIN_OBJECT -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        if (r.nextName() == "data" && r.peek() == JsonToken.BEGIN_ARRAY) { found = true; readArrayItems(r, onItem) } else r.skipValue()
                    }
                    r.endObject()
                }
                else -> r.skipValue()
            }
        }
        return found
    }

    private fun readArrayItems(r: JsonReader, onItem: (JMap) -> Unit) {
        r.beginArray()
        while (r.hasNext()) {
            @Suppress("UNCHECKED_CAST")
            (readAny(r) as? JMap)?.let(onItem)
        }
        r.endArray()
    }
}

/** Champ texte d'un objet : null si absent, vide ou « null ». */
fun JMap.s(key: String): String? = when (val v = this[key]) {
    is String -> v.trim().takeIf { it.isNotEmpty() && it != "null" }
    is Boolean -> v.toString()
    else -> null
}

fun JMap.flag(key: String): Boolean = when (val v = this[key]) {
    is Boolean -> v
    is String -> v == "1" || v.equals("true", true)
    else -> false
}

/** Racine `js` d'une réponse (objet ou tableau) ; null si la réponse n'est pas une réponse du portail. */
fun jsOf(root: Any?): Any? = (root as? JMap)?.takeIf { it.containsKey("js") }?.get("js")

/** Page d'une liste `get_ordered_list`. */
data class StalkerPage(val items: List<JMap>, val total: Int, val perPage: Int) {
    val pages: Int get() = if (perPage <= 0 || total <= 0) 1 else (total + perPage - 1) / perPage
}

/** Conversion des réponses du portail en lignes Room (pure : testée sans réseau). */
object StalkerParse {

    fun page(js: Any?): StalkerPage? {
        val o = js as? JMap ?: return null
        @Suppress("UNCHECKED_CAST")
        val data = (o["data"] as? List<Any?>).orEmpty().mapNotNull { it as? JMap }
        return StalkerPage(data, o.s("total_items")?.toIntOrNull() ?: data.size, o.s("max_page_items")?.toIntOrNull() ?: data.size)
    }

    /** Genres du direct / catégories VOD : `[{"id":"*","title":"All"},{"id":"5","title":"News"}]` (l'entrée « * » est ignorée). */
    fun categories(providerId: Long, kind: String, js: Any?): List<CategoryEntity> {
        @Suppress("UNCHECKED_CAST")
        val list = (js as? List<Any?>) ?: ((js as? JMap)?.get("data") as? List<Any?>) ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e as? JMap ?: return@mapNotNull null
            val id = o.s("id") ?: return@mapNotNull null
            if (id == "*") return@mapNotNull null
            val name = o.s("title") ?: o.s("name") ?: return@mapNotNull null
            CategoryEntity(providerId = providerId, kind = kind, remoteId = id, name = name)
        }.distinctBy { it.remoteId }
    }

    fun channel(providerId: Long, portal: String, o: JMap): ChannelEntity? {
        val id = o.s("id") ?: return null
        val name = o.s("name") ?: return null
        val cmd = o.s("cmd") ?: return null
        val parsed = ChannelNameParser.parse(name)
        return ChannelEntity(
            providerId = providerId,
            remoteId = id,
            name = name,
            logo = StalkerPortal.absolute(portal, o.s("logo")),
            categoryId = o.s("tv_genre_id"),
            streamUrl = StalkerLink.build(providerId, "itv", cmd),
            epgChannelId = o.s("xmltv_id"),
            // Le replay Stalker passe par un autre module (tv_archive) : non proposé plutôt que de mener à une erreur.
            catchupSource = null,
            catchupDays = 0,
            num = o.s("number")?.toIntOrNull() ?: 0,
            title = parsed.displayName,
            junk = !parsed.isSeparator && JunkFilter.isJunk(name),
            isSeparator = parsed.isSeparator, country = parsed.country, quality = parsed.quality, flags = parsed.flags,
            // Langue calculée par la synchro (withLang, avec la langue de la catégorie).
            lang = "",
        )
    }

    /** Élément VOD : film, ou série (`is_series` / tableau `series`). */
    sealed interface Vod
    data class VodMovie(val movie: MovieEntity) : Vod
    data class VodSeries(val series: SeriesEntity, val cmd: String, val episodes: List<Int>) : Vod

    fun vod(providerId: Long, portal: String, o: JMap, fallbackCategory: String?): Vod? {
        val id = o.s("id") ?: return null
        val name = o.s("name") ?: return null
        val cmd = o.s("cmd") ?: return null
        val cleaned = TitleCleaner.clean(name)
        val year = o.s("year")?.take(4)?.toIntOrNull() ?: cleaned.year
        val rating = (o.s("rating_imdb") ?: o.s("rating_kinopoisk"))?.toDoubleOrNull()?.takeIf { it > 0 && it <= 10 }
        val poster = StalkerPortal.absolute(portal, o.s("screenshot_uri") ?: o.s("cover_big") ?: o.s("pic"))
        val category = o.s("category_id") ?: fallbackCategory
        val added = addedKey(o.s("added"), id)
        val plot = o.s("description")?.let { TitleCleaner.presentable(it) }
        @Suppress("UNCHECKED_CAST")
        val episodes = (o["series"] as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.toIntOrNull() }
        if (o.flag("is_series") || episodes.isNotEmpty()) {
            return VodSeries(
                SeriesEntity(
                    providerId = providerId, remoteId = id, name = name, poster = poster, categoryId = category,
                    year = year, rating = rating, plot = plot, title = cleaned.title,
                    genre = o.s("genres_str"), cast = o.s("actors"), lang = "", addedKey = added,
                ),
                cmd, episodes,
            )
        }
        return VodMovie(
            MovieEntity(
                providerId = providerId, remoteId = id, name = name, poster = poster, categoryId = category,
                streamUrl = StalkerLink.build(providerId, "vod", cmd), container = null, year = year, rating = rating,
                plot = plot, title = cleaned.title, genre = o.s("genres_str"), cast = o.s("actors"),
                duration = o.s("time")?.takeIf { it != "0" }?.let { if (it.all(Char::isDigit)) "$it min" else it },
                lang = "", addedKey = added,
            ),
        )
    }

    /** Date d'ajout « yyyy-MM-dd HH:mm:ss » → secondes (tri « derniers ajoutés ») ; repli sur l'identifiant numérique. */
    fun addedKey(added: String?, id: String): Long =
        StalkerDates.parse(added)?.div(1000)?.takeIf { it > 0 } ?: id.toLongOrNull() ?: 0L

    /** Épisodes d'une série « simple » (tableau `series`) : saison 1, un épisode par numéro. */
    fun simpleEpisodes(providerId: Long, seriesLocalId: Long, seriesRemoteId: String, cmd: String, numbers: List<Int>, season: Int = 1): List<EpisodeEntity> =
        numbers.distinct().sorted().map { n ->
            EpisodeEntity(
                seriesId = seriesLocalId, remoteId = "$seriesRemoteId:$season:$n", season = season, episode = n,
                title = "", streamUrl = StalkerLink.build(providerId, "vod", cmd, n), container = null, plot = null,
            )
        }

    /** Saison (Ministra récent : `is_season`) → numéro tiré du nom (« Season 2 », « Saison 2 ») ou de l'ordre. */
    fun seasonNumber(o: JMap, index: Int): Int =
        o.s("season_number")?.toIntOrNull()
            ?: o.s("name")?.let { Regex("(\\d+)").find(it)?.value?.toIntOrNull() }
            ?: (index + 1)

    /** Épisode d'une saison (`is_episode`), avec sa propre commande. */
    fun seasonEpisode(providerId: Long, seriesLocalId: Long, seriesRemoteId: String, season: Int, o: JMap, index: Int): EpisodeEntity? {
        val cmd = o.s("cmd") ?: return null
        val n = o.s("series_number")?.toIntOrNull() ?: o.s("episode_number")?.toIntOrNull() ?: (index + 1)
        return EpisodeEntity(
            seriesId = seriesLocalId, remoteId = "$seriesRemoteId:$season:$n", season = season, episode = n,
            title = TitleCleaner.presentable(o.s("name")) ?: "", streamUrl = StalkerLink.build(providerId, "vod", cmd, n),
            container = null, plot = o.s("description")?.let { TitleCleaner.presentable(it) },
            image = o.s("screenshot_uri")?.takeIf { it.startsWith("http") },
        )
    }

    /** `get_short_epg` : `[{"name":…,"descr":…,"start_timestamp":…,"stop_timestamp":…}]`. */
    fun shortEpg(channelLocalId: Long, js: Any?): List<EpgEntity> {
        @Suppress("UNCHECKED_CAST")
        val list = (js as? List<Any?>) ?: ((js as? JMap)?.get("data") as? List<Any?>) ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e as? JMap ?: return@mapNotNull null
            val title = o.s("name") ?: return@mapNotNull null
            val start = o.s("start_timestamp")?.toLongOrNull()?.times(1000) ?: StalkerDates.parse(o.s("time")) ?: return@mapNotNull null
            val end = o.s("stop_timestamp")?.toLongOrNull()?.times(1000) ?: StalkerDates.parse(o.s("time_to")) ?: (start + 30 * 60_000L)
            EpgEntity(channelId = channelLocalId, title = title, description = o.s("descr")?.take(400), startMs = start, endMs = end)
        }
    }

    /** `account_info/get_main_info` (+ statut de `get_profile`) → compte affiché dans « Abonnement ». */
    fun account(mainInfo: Any?, profile: Any?, now: Long = System.currentTimeMillis()): XtreamAccount? {
        val info = mainInfo as? JMap
        val prof = profile as? JMap
        if (info == null && prof == null) return null
        val keys = listOf("end_date", "expire_billing_date", "tariff_expired_date", "expire_date", "exp_date", "account_expire")
        val expires = keys.firstNotNullOfOrNull { k -> StalkerDates.parse(info?.s(k)) ?: StalkerDates.parse(prof?.s(k)) }
            // Certains portails n'écrivent l'échéance que dans le champ « phone » (« Expire: 2026-01-31 »).
            ?: info?.s("phone")?.let { ph -> Regex("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}(:\\d{2})?)?").find(ph)?.value?.let(StalkerDates::parse) }
        val blocked = prof?.flag("blocked") == true || prof?.s("status") == "2"
        val status = when {
            blocked -> "Disabled"
            expires != null && expires < now -> "Expired"
            else -> "Active"
        }
        return XtreamAccount(status = status, expiresAt = expires, activeConnections = null, maxConnections = null, createdAt = null, trial = false, server = null)
    }
}
