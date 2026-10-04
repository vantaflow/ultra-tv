package com.ultratv.tv.nativeapp.data.xtream

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xtream-Codes player_api.php client. Endpoints used:
 *   action= get_live_categories | get_live_streams
 *           get_vod_categories  | get_vod_streams | get_vod_info
 *           get_series_categories | get_series   | get_series_info
 *           get_short_epg (channel-level EPG)
 *
 * Stream URLs:
 *   Live:    {base}/live/{user}/{pass}/{stream_id}.ts
 *   Movies:  {base}/movie/{user}/{pass}/{stream_id}.{container_extension}
 *   Episode: {base}/series/{user}/{pass}/{episode_id}.{container_extension}
 */
@Singleton
class XtreamClient @Inject constructor(okBase: OkHttpClient) {
    // Catalogues de 20 à 70 Mo : pas de délai global d'appel (le téléchargement peut
    // légitimement durer des minutes), mais connexion 10 s et 30 s d'inactivité en lecture.
    private val ok: OkHttpClient = okBase.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // ---- Live ----

    suspend fun fetchLiveCategories(p: ProviderEntity): List<CategoryEntity> = arrAt(p, "get_live_categories") { o ->
        val rid = o["category_id"]?.str() ?: return@arrAt null
        val name = o["category_name"]?.str() ?: return@arrAt null
        CategoryEntity(providerId = p.id, kind = "LIVE", remoteId = rid, name = name)
    }

    /**
     * Flux des chaînes en lecture incrémentale : la réponse (20 Mo+) n'est JAMAIS
     * matérialisée en String ni en arbre JSON. [block] consomme la séquence paresseuse
     * (insertion Room par lots) ; la connexion est ouverte AVANT, donc un serveur
     * injoignable échoue avant toute suppression de données locales.
     */
    suspend fun <R> withLiveStreams(p: ProviderEntity, block: suspend (Sequence<ChannelEntity>) -> R): R =
        withStream(p, "get_live_streams", ::liveOf, null, block)

    /** Une seule catégorie (le serveur filtre : ~15 Ko au lieu de 20 Mo pour tout le direct). */
    suspend fun liveOfCategory(p: ProviderEntity, categoryId: String): List<ChannelEntity> =
        withStream(p, "get_live_streams", ::liveOf, categoryId) { it.toList() }

    private fun liveOf(p: ProviderEntity, o: JsonObject): ChannelEntity? {
        val sid = o["stream_id"]?.str() ?: return null
        val name = o["name"]?.str() ?: return null
        val url = "${p.baseUrl}/live/${p.username.urlEnc()}/${p.password.urlEnc()}/$sid.ts"
        val parsed = com.ultratv.tv.nativeapp.data.repo.ChannelNameParser.parse(name)
        val tvArchive = o["tv_archive"]?.let { e -> e.str()?.toIntOrNull() ?: 0 } ?: 0
        val archiveDuration = o["tv_archive_duration"]?.let { e -> e.str()?.toIntOrNull() ?: 0 } ?: 0
        return ChannelEntity(
            providerId = p.id,
            remoteId = sid,
            name = name,
            logo = o["stream_icon"]?.str(),
            categoryId = o["category_id"]?.str(),
            streamUrl = url,
            epgChannelId = o["epg_channel_id"]?.str()?.takeIf { it.isNotBlank() },
            catchupSource = null,
            catchupDays = if (tvArchive >= 1) archiveDuration.coerceAtLeast(1) else 0,
            num = o["num"]?.str()?.toIntOrNull() ?: 0,
            title = parsed.displayName,
            junk = !parsed.isSeparator && com.ultratv.tv.nativeapp.data.repo.JunkFilter.isJunk(name),
            isSeparator = parsed.isSeparator, country = parsed.country, quality = parsed.quality, flags = parsed.flags,
        )
    }

    // ---- VOD (Movies) ----

    suspend fun fetchVodCategories(p: ProviderEntity): List<CategoryEntity> = arrAt(p, "get_vod_categories") { o ->
        val rid = o["category_id"]?.str() ?: return@arrAt null
        val name = o["category_name"]?.str() ?: return@arrAt null
        CategoryEntity(providerId = p.id, kind = "MOVIE", remoteId = rid, name = name)
    }

    suspend fun <R> withVodStreams(p: ProviderEntity, block: suspend (Sequence<MovieEntity>) -> R): R =
        withStream(p, "get_vod_streams", ::vodOf, null, block)

    suspend fun vodOfCategory(p: ProviderEntity, categoryId: String): List<MovieEntity> =
        withStream(p, "get_vod_streams", ::vodOf, categoryId) { it.toList() }

    private fun vodOf(p: ProviderEntity, o: JsonObject): MovieEntity? {
        val sid = o["stream_id"]?.str() ?: return null
        val name = o["name"]?.str() ?: return null
        val cont = o["container_extension"]?.str() ?: "mp4"
        val url = "${p.baseUrl}/movie/${p.username.urlEnc()}/${p.password.urlEnc()}/$sid.$cont"
        val cleaned = com.ultratv.tv.nativeapp.data.repo.TitleCleaner.clean(name)
        return MovieEntity(
            providerId = p.id,
            remoteId = sid,
            name = name,
            poster = o["stream_icon"]?.str(),
            categoryId = o["category_id"]?.str(),
            streamUrl = url,
            container = cont,
            year = o["releaseDate"]?.str()?.take(4)?.toIntOrNull() ?: o["year"]?.str()?.toIntOrNull() ?: cleaned.year,
            rating = normalizeRating(o["rating"]?.str()?.toDoubleOrNull()),
            plot = null,
            title = cleaned.title,
        )
    }

    /** Détails d'un film (get_vod_info) : synopsis, distribution, réalisateur, genre, durée, date, note, image paysage, bande-annonce… */
    data class VodInfo(
        val plot: String?, val cast: String?, val director: String?, val genre: String?, val duration: String?, val releaseDate: String?,
        val rating: Double?, val backdrop: String?, val trailer: String?, val tmdbId: String?, val country: String?, val originalName: String?,
        val poster: String?,
    )

    suspend fun fetchVodInfo(p: ProviderEntity, remoteId: String): VodInfo? {
        val body = get("${p.baseUrl}/player_api.php?username=${p.username.urlEnc()}&password=${p.password.urlEnc()}&action=get_vod_info&vod_id=$remoteId")
        val info = (runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()?.get("info") as? JsonObject) ?: return null
        return parseVodInfo(info)
    }

    internal fun parseVodInfo(info: JsonObject): VodInfo {
        fun s(k: String) = info[k]?.str()?.let { com.ultratv.tv.nativeapp.data.repo.TitleCleaner.presentable(it) }
        return VodInfo(
            plot = s("plot") ?: s("description"),
            cast = s("cast") ?: s("actors"),
            director = s("director"),
            genre = s("genre"),
            duration = s("duration"),
            releaseDate = s("releasedate") ?: s("release_date") ?: s("releaseDate"),
            rating = normalizeRating((s("rating") ?: s("rating_5based"))?.toDoubleOrNull()),
            backdrop = ((info["backdrop_path"] as? JsonArray)?.firstOrNull()?.str() ?: s("backdrop_path") ?: s("backdrop"))?.takeIf { it.isNotBlank() && it.startsWith("http") },
            trailer = s("youtube_trailer"),
            tmdbId = s("tmdb_id") ?: s("tmdb"),
            country = s("country"),
            originalName = s("o_name"),
            poster = s("movie_image") ?: s("cover_big"),
        )
    }

    // ---- Series ----

    suspend fun fetchSeriesCategories(p: ProviderEntity): List<CategoryEntity> = arrAt(p, "get_series_categories") { o ->
        val rid = o["category_id"]?.str() ?: return@arrAt null
        val name = o["category_name"]?.str() ?: return@arrAt null
        CategoryEntity(providerId = p.id, kind = "SERIES", remoteId = rid, name = name)
    }

    suspend fun <R> withSeries(p: ProviderEntity, block: suspend (Sequence<SeriesEntity>) -> R): R =
        withStream(p, "get_series", ::seriesOf, null, block)

    suspend fun seriesOfCategory(p: ProviderEntity, categoryId: String): List<SeriesEntity> =
        withStream(p, "get_series", ::seriesOf, categoryId) { it.toList() }

    private fun seriesOf(p: ProviderEntity, o: JsonObject): SeriesEntity? {
        val rid = o["series_id"]?.str() ?: return null
        val name = o["name"]?.str() ?: return null
        val cleaned = com.ultratv.tv.nativeapp.data.repo.TitleCleaner.clean(name)
        return SeriesEntity(
            providerId = p.id,
            remoteId = rid,
            name = name,
            poster = o["cover"]?.str(),
            categoryId = o["category_id"]?.str(),
            year = (o["releaseDate"]?.str() ?: o["release_date"]?.str())?.take(4)?.toIntOrNull() ?: cleaned.year,
            rating = normalizeRating(o["rating"]?.str()?.toDoubleOrNull()),
            plot = o["plot"]?.str()?.takeIf { it.isNotBlank() },
            title = cleaned.title,
            backdrop = (o["backdrop_path"] as? JsonArray)?.firstOrNull()?.str()?.takeIf { it.isNotBlank() },
            genre = o["genre"]?.str()?.takeIf { it.isNotBlank() },
            cast = o["cast"]?.str()?.takeIf { it.isNotBlank() },
        )
    }

    /** Fiche série + épisodes (get_series_info). */
    data class SeriesDetail(
        val plot: String?, val genre: String?, val cast: String?, val backdrop: String?, val year: Int?, val rating: Double?,
        val episodes: List<EpisodeEntity>,
    )

    /** Pull all episodes for one series. */
    suspend fun fetchSeriesEpisodes(p: ProviderEntity, seriesRemoteId: String, seriesLocalId: Long): List<EpisodeEntity> =
        fetchSeriesDetail(p, seriesRemoteId, seriesLocalId)?.episodes.orEmpty()

    suspend fun fetchSeriesDetail(p: ProviderEntity, seriesRemoteId: String, seriesLocalId: Long): SeriesDetail? {
        val body = get("${p.baseUrl}/player_api.php?username=${p.username.urlEnc()}&password=${p.password.urlEnc()}&action=get_series_info&series_id=$seriesRemoteId")
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val tc = com.ultratv.tv.nativeapp.data.repo.TitleCleaner
        val info = root["info"] as? JsonObject
        fun si(k: String) = info?.get(k)?.str()?.let { tc.presentable(it) }
        val episodes = root["episodes"] as? JsonObject
        val out = mutableListOf<EpisodeEntity>()
        // Certaines sources renvoient un tableau (saisons dans l'ordre) au lieu d'un objet indexé par saison.
        val seasons: List<Pair<String, JsonArray>> = when (val e = root["episodes"]) {
            is JsonObject -> e.mapNotNull { (k, v) -> (v as? JsonArray)?.let { k to it } }
            is JsonArray -> e.mapIndexedNotNull { i, v -> (v as? JsonArray)?.let { (i + 1).toString() to it } }
            else -> emptyList()
        }
        for ((seasonKey, list) in seasons) {
            val seasonNo = seasonKey.toIntOrNull() ?: 0
            list.forEach { ep ->
                val o = ep as? JsonObject ?: return@forEach
                val rid = o["id"]?.str() ?: return@forEach
                val episodeNo = o["episode_num"]?.str()?.toIntOrNull() ?: 0
                val einfo = o["info"] as? JsonObject
                val title = tc.presentable(o["title"]?.str()) ?: ""
                val cont = o["container_extension"]?.str() ?: "mkv"
                val url = "${p.baseUrl}/series/${p.username.urlEnc()}/${p.password.urlEnc()}/$rid.$cont"
                out += EpisodeEntity(
                    seriesId = seriesLocalId, remoteId = rid, season = seasonNo, episode = episodeNo,
                    title = title, streamUrl = url, container = cont,
                    plot = tc.presentable(einfo?.get("plot")?.str()),
                    image = einfo?.get("movie_image")?.str()?.takeIf { it.startsWith("http") },
                    duration = tc.presentable(einfo?.get("duration")?.str()),
                )
            }
        }
        if (episodes == null && out.isEmpty() && info == null) return null
        return SeriesDetail(
            plot = si("plot"), genre = si("genre"), cast = si("cast"),
            backdrop = ((info?.get("backdrop_path") as? JsonArray)?.firstOrNull()?.str())?.takeIf { it.startsWith("http") },
            year = (si("releaseDate") ?: si("release_date") ?: si("releasedate"))?.take(4)?.toIntOrNull(),
            rating = normalizeRating(si("rating")?.toDoubleOrNull()),
            episodes = out,
        )
    }

    // ---- EPG ----

    /** Short EPG (next ~5 programmes) for a single channel. */
    suspend fun fetchShortEpg(p: ProviderEntity, channelRemoteId: String, channelLocalId: Long): List<EpgEntity> {
        val body = get("${p.baseUrl}/player_api.php?username=${p.username.urlEnc()}&password=${p.password.urlEnc()}&action=get_short_epg&stream_id=$channelRemoteId")
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val listings = root["epg_listings"] as? JsonArray ?: return emptyList()
        return listings.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val title = decodeBase64(o["title"]?.str()) ?: return@mapNotNull null
            val desc = decodeBase64(o["description"]?.str())
            val start = o["start_timestamp"]?.str()?.toLongOrNull()?.times(1000)
                ?: o["start"]?.str()?.let { parseDate(it) } ?: return@mapNotNull null
            val end = o["stop_timestamp"]?.str()?.toLongOrNull()?.times(1000)
                ?: o["end"]?.str()?.let { parseDate(it) } ?: (start + 30 * 60_000)
            EpgEntity(channelId = channelLocalId, title = title, description = desc, startMs = start, endMs = end)
        }
    }

    /** `user_info.max_connections` de la source (null si injoignable ou absent). Aucun identifiant n'est journalisé. */
    suspend fun fetchMaxConnections(p: ProviderEntity): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${p.baseUrl}/player_api.php?username=${p.username.urlEnc()}&password=${p.password.urlEnc()}"
            ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return@use null
                com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore.parseMaxConnections(root)
            }
        }.getOrNull()
    }

    // ---- Helpers ----

    /** Petite liste (catégories) : les erreurs réseau REMONTENT (avant : avalées => catalogue vidé). */
    private suspend inline fun <T : Any> arrAt(p: ProviderEntity, action: String, crossinline transform: (JsonObject) -> T?): List<T> =
        withStream(p, action, { _, o -> transform(o) }, null) { seq -> seq.toList() }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T : Any, R> withStream(
        p: ProviderEntity,
        action: String,
        transform: (ProviderEntity, JsonObject) -> T?,
        categoryId: String? = null,
        block: suspend (Sequence<T>) -> R,
    ): R = withContext(Dispatchers.IO) {
        val url = "${p.baseUrl}/player_api.php?username=${p.username.urlEnc()}&password=${p.password.urlEnc()}&action=$action" +
            (categoryId?.let { "&category_id=${it.urlEnc()}" } ?: "")
        ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            val input = resp.body?.byteStream()?.let { ControlCharFilter(it) }?.buffered() ?: return@use block(emptySequence())
            // Un objet à la fois : la mémoire reste plate quelle que soit la taille de la réponse.
            val items = json.decodeToSequence(input, JsonObject.serializer(), DecodeSequenceMode.AUTO_DETECT)
                .mapNotNull { transform(p, it) }
            block(items)
        }
    }

    /** Note sur 10 ; les fournisseurs envoient parfois 0, une note sur 100 ou n'importe quoi (608) : sinon rien. */
    private fun normalizeRating(r: Double?): Double? = when {
        r == null || r <= 0.0 -> null
        r <= 10.0 -> r
        r <= 100.0 -> r / 10.0
        else -> null
    }

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull

    /** Petites réponses JSON (détail d'une série, EPG court d'une chaîne). */
    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            // Pas d'URL dans le message : elle contient identifiant et mot de passe.
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            resp.body?.string().orEmpty().let(::stripControlChars)
        }
    }

    private fun String.urlEnc(): String = java.net.URLEncoder.encode(this, "UTF-8")

    private fun decodeBase64(s: String?): String? = s?.let {
        runCatching { String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
    }

    private fun parseDate(s: String): Long? = runCatching {
        // Xtream sends "yyyy-MM-dd HH:mm:ss" in UTC.
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.parse(s)?.time
    }.getOrNull()
}

/**
 * Certaines sources renvoient des caractères de contrôle BRUTS (tabulation, saut de ligne, U+0001…) dans les chaînes JSON,
 * ce qui est invalide et faisait échouer toute la réponse. Chaque octet de contrôle devient une espace : hors chaîne c'est
 * un blanc équivalent, dans une chaîne c'est le seul remplacement qui garde le JSON valide. Les octets UTF-8 multi-octets
 * (>= 0x80) ne sont jamais touchés.
 */
internal class ControlCharFilter(input: java.io.InputStream) : java.io.FilterInputStream(input) {
    override fun read(): Int = super.read().let { if (it in 0..31 || it == 127) 0x20 else it }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        for (i in off until off + n.coerceAtLeast(0)) if (b[i] in 0..31 || b[i].toInt() == 127) b[i] = 0x20
        return n
    }
}

internal fun stripControlChars(s: String): String = if (s.none { it.code < 32 || it.code == 127 }) s else s.map { if (it.code < 32 || it.code == 127) ' ' else it }.joinToString("")
