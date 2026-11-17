package com.ultratv.tv.nativeapp.data.mediaserver

import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Plex : toute la LOGIQUE sans réseau — en-têtes d'identification, code PIN de connexion (plex.tv/link), liste des serveurs
 * du compte et choix de la meilleure connexion, analyse des bibliothèques / éléments, conversion en entrées du catalogue
 * local, adresses de lecture directe et rapports de lecture (`/:/timeline`). Le client HTTP ([PlexClient]) n'en est qu'une
 * fine couche.
 *
 * Stockage d'une source (`ProviderEntity`) : `kind` = `PLEX`, `baseUrl` = adresse de la connexion choisie du serveur,
 * `username` = identifiant de la machine du serveur, `password` = jeton d'accès DU SERVEUR (chiffré au repos comme tout mot
 * de passe de source). Le jeton n'apparaît JAMAIS dans une adresse stockée ni dans un journal : les affiches sont stockées
 * sans jeton (il part en en-tête), les adresses de lecture sont des liens internes ([MediaLink]).
 */
object Plex {
    const val KIND = "PLEX"
    const val PRODUCT = "Ultra TV"

    /** Page où l'utilisateur saisit le code affiché. */
    const val LINK_URL = "https://plex.tv/link"
    const val PINS_URL = "https://plex.tv/api/v2/pins?strong=false"
    const val RESOURCES_URL = "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1"

    /** Un code PIN expire au bout d'environ 15 minutes. */
    const val PIN_TTL_MS = 15 * 60 * 1000L
    const val PIN_POLL_MS = 2_500L

    const val PAGE_SIZE = 300
    fun pinCheckUrl(id: Long) = "https://plex.tv/api/v2/pins/$id"

    /** En-têtes d'identification de l'application (jeton ajouté quand il existe). */
    fun headers(clientId: String, token: String? = null, version: String = "1"): Map<String, String> = buildMap {
        put("X-Plex-Client-Identifier", clientId)
        put("X-Plex-Product", PRODUCT)
        put("X-Plex-Version", version)
        put("X-Plex-Platform", "Android")
        put("X-Plex-Device-Name", PRODUCT)
        put("Accept", "application/json")
        token?.takeIf { it.isNotEmpty() }?.let { put("X-Plex-Token", it) }
    }

    /** En-têtes de lecture : le jeton voyage dans un en-tête, jamais dans l'adresse. */
    fun playHeaders(token: String, clientId: String): Map<String, String> =
        mapOf("X-Plex-Token" to token, "X-Plex-Client-Identifier" to clientId)

    // ───────────────────────── Analyse (JSON) ─────────────────────────

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun parse(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true
    private fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray
    private fun JsonObject.container(): JsonObject? = this["MediaContainer"] as? JsonObject

    /** Code PIN : [code] à saisir sur plex.tv/link ; [authToken] arrive (non null) une fois le lien validé. */
    data class Pin(val id: Long, val code: String, val authToken: String?, val expiresInSec: Long?)

    fun parsePin(text: String): Pin? {
        val o = parse(text) as? JsonObject ?: return null
        val id = o.long("id") ?: return null
        val code = o.str("code") ?: return null
        return Pin(id, code, o.str("authToken"), o.long("expiresIn"))
    }

    /** Connexion possible à un serveur : adresse complète, locale ou non, relais Plex ou non. */
    data class Connection(val uri: String, val local: Boolean, val relay: Boolean, val https: Boolean, val ipv6: Boolean)

    /** Serveur du compte ; [accessToken] = jeton à utiliser POUR CE SERVEUR (celui du compte s'il en est propriétaire). */
    data class Server(val name: String, val machineId: String, val accessToken: String, val owned: Boolean, val connections: List<Connection>) {
        /** Jamais le jeton (un journal ou un message d'erreur pourrait afficher l'objet). */
        override fun toString() = "Server(name=$name, owned=$owned, connections=${connections.size})"
    }

    /**
     * `GET /api/v2/resources` : seuls les serveurs (`provides` contient `server`) avec un jeton et au moins une connexion.
     * Les connexions sont déjà classées par [orderConnections].
     */
    fun parseServers(text: String): List<Server> {
        val arr = parse(text) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val r = e as? JsonObject ?: return@mapNotNull null
            if (r.str("provides")?.split(',')?.map { it.trim() }?.contains("server") != true) return@mapNotNull null
            val id = r.str("clientIdentifier") ?: return@mapNotNull null
            val token = r.str("accessToken") ?: return@mapNotNull null
            val conns = r.arr("connections").orEmpty().mapNotNull { c ->
                val o = c as? JsonObject ?: return@mapNotNull null
                val uri = o.str("uri")?.trimEnd('/') ?: return@mapNotNull null
                Connection(uri, o.bool("local"), o.bool("relay"), (o.str("protocol") ?: uri.substringBefore("://")).equals("https", true), o.bool("IPv6"))
            }
            if (conns.isEmpty()) return@mapNotNull null
            Server(r.str("name") ?: id, id, token, r.bool("owned"), orderConnections(conns))
        }.sortedWith(compareByDescending<Server> { it.owned }.thenBy { it.name.lowercase() })
    }

    /** Locale d'abord, puis distante directe, puis relais Plex ; à égalité IPv4 avant IPv6 et HTTPS avant HTTP. */
    fun orderConnections(list: List<Connection>): List<Connection> =
        list.distinctBy { it.uri }.sortedWith(compareBy<Connection>({ it.relay }, { !it.local }, { it.ipv6 }, { !it.https }))

    /** Bibliothèque : [MediaServer.Library.type] = `movies` | `tvshows` (mêmes valeurs que Jellyfin / Emby, pour le catalogue). */
    fun parseSections(text: String): List<MediaServer.Library> {
        val c = (parse(text) as? JsonObject)?.container() ?: return emptyList()
        return c.arr("Directory").orEmpty().mapNotNull { e ->
            val d = e as? JsonObject ?: return@mapNotNull null
            val id = d.str("key") ?: return@mapNotNull null
            val type = when (d.str("type")) { "movie" -> "movies"; "show" -> "tvshows"; else -> return@mapNotNull null }
            MediaServer.Library(id, d.str("title") ?: id, type)
        }
    }

    /** Élément brut (film, série ou épisode) : champs utiles seulement. Durées et positions en millisecondes. */
    data class Item(
        val ratingKey: String, val title: String, val type: String, val year: Int?, val rating: Double?, val summary: String?,
        val genres: List<String>, val cast: List<String>, val durationMs: Long?, val container: String?, val partKey: String?,
        val thumb: String?, val art: String?, val season: Int?, val episode: Int?, val addedAtSec: Long,
        val viewOffsetMs: Long, val viewCount: Int,
    )

    data class Page(val total: Int, val items: List<Item>)

    /** Réponse `{"MediaContainer":{"totalSize":N,"Metadata":[…]}}`. Une réponse illisible donne une page vide. */
    fun parseItems(text: String): Page {
        val c = (parse(text) as? JsonObject)?.container() ?: return Page(0, emptyList())
        val items = c.arr("Metadata").orEmpty().mapNotNull { (it as? JsonObject)?.let(::item) }
        return Page(c.int("totalSize") ?: items.size, items)
    }

    fun parseItem(text: String): Item? = parseItems(text).items.firstOrNull()

    private fun tags(o: JsonObject, k: String) = o.arr(k).orEmpty().mapNotNull { (it as? JsonObject)?.str("tag") }

    private fun item(o: JsonObject): Item? {
        val key = o.str("ratingKey") ?: return null
        val title = o.str("title") ?: return null
        val media = o.arr("Media")?.firstOrNull() as? JsonObject
        val part = media?.arr("Part")?.firstOrNull() as? JsonObject
        return Item(
            ratingKey = key, title = title, type = o.str("type").orEmpty(), year = o.int("year"),
            rating = o.dbl("audienceRating") ?: o.dbl("rating"), summary = o.str("summary"),
            genres = tags(o, "Genre"), cast = tags(o, "Role"),
            durationMs = o.long("duration") ?: media?.long("duration"),
            container = (part?.str("container") ?: media?.str("container"))?.substringBefore(','),
            partKey = part?.str("key"), thumb = o.str("thumb"), art = o.str("art"),
            season = o.int("parentIndex"), episode = o.int("index"), addedAtSec = o.long("addedAt") ?: 0L,
            viewOffsetMs = o.long("viewOffset") ?: 0L, viewCount = o.int("viewCount") ?: 0,
        )
    }

    // ───────────────────────── Adresses ─────────────────────────

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** Affiche redimensionnée par le serveur. SANS jeton : il est ajouté en en-tête au chargement de l'image. */
    fun imageUrl(base: String, path: String, width: Int = 480, height: Int = 720): String =
        "$base/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1&url=${enc(path)}"

    /** Lecture directe : le fichier d'origine `/library/parts/{id}/{ts}/file.ext` ; null si l'élément n'a pas de fichier. */
    fun playUrl(base: String, partKey: String?): String? = partKey?.takeIf { it.startsWith("/") }?.let { base + it }

    fun sectionsUrl(base: String) = "$base/library/sections"

    /** [plexType] : 1 = films, 2 = séries. */
    fun itemsUrl(base: String, sectionId: String, plexType: Int, start: Int, size: Int = PAGE_SIZE): String =
        "$base/library/sections/${enc(sectionId)}/all?type=$plexType&X-Plex-Container-Start=$start&X-Plex-Container-Size=$size"

    fun episodesUrl(base: String, showKey: String) = "$base/library/metadata/${enc(showKey)}/allLeaves"
    fun itemUrl(base: String, ratingKey: String) = "$base/library/metadata/${enc(ratingKey)}"

    // ───────────────────────── Catalogue local ─────────────────────────

    fun durationText(ms: Long?): String? {
        if (ms == null || ms <= 0) return null
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    fun toMovie(providerId: Long, base: String, it: Item, categoryId: String?): MovieEntity = MovieEntity(
        providerId = providerId, remoteId = it.ratingKey, name = it.title,
        poster = it.thumb?.let { t -> imageUrl(base, t) }, categoryId = categoryId, streamUrl = MediaLink.build(providerId, it.ratingKey),
        container = it.container, year = it.year, rating = it.rating, plot = it.summary,
        backdrop = it.art?.let { t -> imageUrl(base, t, 1280, 720) },
        genre = it.genres.joinToString(", ").ifEmpty { null }, cast = it.cast.take(8).joinToString(", ").ifEmpty { null },
        duration = durationText(it.durationMs), addedKey = it.addedAtSec,
    )

    fun toSeries(providerId: Long, base: String, it: Item, categoryId: String?): SeriesEntity = SeriesEntity(
        providerId = providerId, remoteId = it.ratingKey, name = it.title,
        poster = it.thumb?.let { t -> imageUrl(base, t) }, categoryId = categoryId, year = it.year, rating = it.rating, plot = it.summary,
        backdrop = it.art?.let { t -> imageUrl(base, t, 1280, 720) },
        genre = it.genres.joinToString(", ").ifEmpty { null }, cast = it.cast.take(8).joinToString(", ").ifEmpty { null },
        addedKey = it.addedAtSec,
    )

    fun toEpisode(providerId: Long, base: String, seriesRowId: Long, it: Item, position: Int): EpisodeEntity = EpisodeEntity(
        seriesId = seriesRowId, remoteId = it.ratingKey, season = it.season ?: 1, episode = it.episode ?: position, title = it.title,
        streamUrl = MediaLink.build(providerId, it.ratingKey), container = it.container, plot = it.summary,
        image = it.thumb?.let { t -> imageUrl(base, t, 640, 360) }, duration = durationText(it.durationMs),
    )

    // ───────────────────────── Reprise et rapports de lecture ─────────────────────────

    /**
     * Position de reprise : celle du SERVEUR (`viewOffset`) si elle existe, sinon la position locale. Un élément vu en entier
     * (`viewCount` > 0, plus de `viewOffset`) repart du début.
     */
    fun resumeMs(server: Item?, localMs: Long): Long = when {
        server == null -> localMs
        server.viewOffsetMs > 0 -> server.viewOffsetMs
        server.viewCount > 0 -> 0L
        else -> localMs
    }

    /** État du rapport `/:/timeline` selon le type de rapport de l'appli et la pause. */
    fun timelineState(kind: MediaServer.Report, paused: Boolean): String = when (kind) {
        MediaServer.Report.STOP -> "stopped"
        else -> if (paused) "paused" else "playing"
    }

    fun timelineUrl(base: String, ratingKey: String, state: String, positionMs: Long, durationMs: Long): String =
        "$base/:/timeline?ratingKey=${enc(ratingKey)}&key=${enc("/library/metadata/$ratingKey")}&identifier=com.plexapp.plugins.library" +
            "&state=$state&time=${positionMs.coerceAtLeast(0)}&duration=${durationMs.coerceAtLeast(0)}&hasMDE=1"
}
