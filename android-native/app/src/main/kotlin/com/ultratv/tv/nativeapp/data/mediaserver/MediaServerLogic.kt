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
 * Serveurs multimédias personnels Jellyfin et Emby (API REST quasi identique) : toute la LOGIQUE sans réseau —
 * adresse du serveur, en-têtes d'authentification, analyse des réponses, conversion en entrées du catalogue local,
 * adresses de lecture et corps des rapports de lecture. Le client HTTP ([MediaServerClient]) n'en est qu'une fine couche.
 *
 * Stockage d'une source (`ProviderEntity`) : `kind` = `JELLYFIN` ou `EMBY`, `baseUrl` = adresse du serveur, `username` =
 * identifiant interne de l'utilisateur, `password` = jeton d'accès (chiffré au repos comme tout mot de passe de source).
 * Le jeton n'apparaît JAMAIS dans une adresse stockée ni dans un journal : les adresses de lecture sont stockées sous forme
 * de lien interne ([MediaLink]) et le jeton part dans un en-tête à l'ouverture du flux.
 */
object MediaServer {
    const val JELLYFIN = "JELLYFIN"
    const val EMBY = "EMBY"

    fun isMediaKind(kind: String?): Boolean = kind == JELLYFIN || kind == EMBY

    /** Nom de client annoncé au serveur (apparaît dans son tableau de bord, rubrique Appareils). */
    const val CLIENT_NAME = "Ultra TV"

    /** Page de lecture des listes : assez grosse pour limiter les allers-retours, assez petite pour un NAS modeste. */
    const val PAGE_SIZE = 300

    /** 1 tick = 100 ns (format du serveur) : 10 000 ticks par milliseconde. */
    private const val TICKS_PER_MS = 10_000L

    /**
     * Adresse saisie → adresse de base du serveur : schéma ajouté (http par défaut), « / » final et page web
     * (`/web/index.html`, `/web/`, `#!/home`) retirés. Null si ce n'est pas une adresse exploitable.
     */
    fun normalizeServerUrl(input: String): String? {
        var s = input.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        if (!s.contains("://")) s = "http://$s"
        if (!(s.startsWith("http://", true) || s.startsWith("https://", true))) return null
        s = s.substringBefore('#').substringBefore('?')
        s = s.trimEnd('/')
        for (tail in listOf("/web/index.html", "/web/index.htm", "/web")) {
            if (s.endsWith(tail, ignoreCase = true)) { s = s.dropLast(tail.length).trimEnd('/'); break }
        }
        // Hôte obligatoire (« http:// » seul n'est pas une adresse).
        val host = s.substringAfter("://", "").substringBefore('/').substringAfterLast('@')
        if (host.isBlank() || host.startsWith(":")) return null
        return s
    }

    /**
     * En-tête d'identification d'appareil attendu par les deux serveurs (« MediaBrowser … »). [token] s'ajoute une fois
     * connecté. Les valeurs sont entre guillemets, ceux qu'elles contiendraient sont retirés.
     */
    fun authorization(deviceId: String, deviceName: String = "Android", version: String = "1", token: String? = null): String {
        fun q(v: String) = v.replace("\"", "")
        return "MediaBrowser Client=\"${q(CLIENT_NAME)}\", Device=\"${q(deviceName)}\", DeviceId=\"${q(deviceId)}\", Version=\"${q(version)}\"" +
            (token?.takeIf { it.isNotEmpty() }?.let { ", Token=\"${q(it)}\"" } ?: "")
    }

    /** Corps de `POST /Users/AuthenticateByName`. */
    fun authBody(username: String, password: String): String =
        JsonObject(mapOf("Username" to JsonPrimitive(username), "Pw" to JsonPrimitive(password))).toString()

    // ───────────────────────── Analyse des réponses ─────────────────────────

    /** Connexion réussie : jeton d'accès, identifiant et nom de l'utilisateur, identifiant du serveur si fourni. */
    data class Auth(val token: String, val userId: String, val userName: String, val serverId: String?)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray

    /** Réponse de `AuthenticateByName` ; null si le jeton ou l'utilisateur manque. */
    fun parseAuth(text: String): Auth? {
        val o = parse(text) as? JsonObject ?: return null
        val token = o.str("AccessToken") ?: return null
        val user = o.obj("User") ?: return null
        val id = user.str("Id") ?: return null
        return Auth(token, id, user.str("Name").orEmpty(), o.str("ServerId") ?: user.str("ServerId"))
    }

    /** Utilisateur d'une liste `GET /Users` (mode clé API). */
    data class UserRef(val id: String, val name: String)

    /** Celui dont le nom correspond, sinon un administrateur, sinon le premier. */
    fun pickUser(text: String, wantedName: String): UserRef? {
        val arr = parse(text) as? JsonArray ?: return null
        val users = arr.mapNotNull { e ->
            val u = e as? JsonObject ?: return@mapNotNull null
            val id = u.str("Id") ?: return@mapNotNull null
            val admin = (u.obj("Policy")?.get("IsAdministrator") as? JsonPrimitive)?.booleanOrNull == true
            Triple(id, u.str("Name").orEmpty(), admin)
        }
        if (users.isEmpty()) return null
        val w = wantedName.trim()
        val pick = users.firstOrNull { w.isNotEmpty() && it.second.equals(w, ignoreCase = true) } ?: users.firstOrNull { it.third } ?: users.first()
        return UserRef(pick.first, pick.second)
    }

    /** Bibliothèque du serveur (dossier racine : Films, Séries…). [type] = `movies` | `tvshows` ; les autres sont ignorées. */
    data class Library(val id: String, val name: String, val type: String)

    /** `GET /Users/{id}/Views` : seules les bibliothèques de films et de séries sont gardées. */
    fun parseViews(text: String): List<Library> {
        val o = parse(text) as? JsonObject ?: return emptyList()
        return o.arr("Items").orEmpty().mapNotNull { e ->
            val v = e as? JsonObject ?: return@mapNotNull null
            val id = v.str("Id") ?: return@mapNotNull null
            val type = v.str("CollectionType")?.lowercase() ?: return@mapNotNull null
            if (type != "movies" && type != "tvshows") return@mapNotNull null
            Library(id, v.str("Name") ?: id, type)
        }
    }

    /** Élément brut d'une liste d'éléments (film, série ou épisode) : champs utiles seulement. */
    data class Item(
        val id: String, val name: String, val type: String, val year: Int?, val rating: Double?, val overview: String?,
        val genres: List<String>, val cast: List<String>, val runtimeTicks: Long?, val container: String?, val mediaSourceId: String?,
        val primaryTag: String?, val backdropTag: String?, val seriesId: String?, val season: Int?, val episode: Int?, val addedAt: String?,
        val positionTicks: Long, val played: Boolean,
    )

    data class Page(val total: Int, val items: List<Item>)

    /** Réponse paginée `{"Items":[…],"TotalRecordCount":N}`. Une réponse illisible donne une page vide (total 0). */
    fun parseItems(text: String): Page {
        val o = parse(text) as? JsonObject ?: return Page(0, emptyList())
        val items = o.arr("Items").orEmpty().mapNotNull { (it as? JsonObject)?.let(::item) }
        val total = (o["TotalRecordCount"] as? JsonPrimitive)?.intOrNull ?: items.size
        return Page(total, items)
    }

    /** Un seul élément (`GET /Users/{id}/Items/{id}`). */
    fun parseItem(text: String): Item? = (parse(text) as? JsonObject)?.let(::item)

    private fun item(o: JsonObject): Item? {
        val id = o.str("Id") ?: return null
        val name = o.str("Name") ?: return null
        val sources = o.arr("MediaSources")?.firstOrNull() as? JsonObject
        val ud = o.obj("UserData")
        return Item(
            id = id, name = name, type = o.str("Type").orEmpty(),
            year = (o["ProductionYear"] as? JsonPrimitive)?.intOrNull,
            rating = (o["CommunityRating"] as? JsonPrimitive)?.doubleOrNull,
            overview = o.str("Overview"),
            genres = o.arr("Genres").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) },
            cast = o.arr("People").orEmpty().mapNotNull { e -> (e as? JsonObject)?.takeIf { p -> p.str("Type").let { t -> t == null || t == "Actor" } }?.str("Name") },
            runtimeTicks = o.long("RunTimeTicks") ?: sources?.long("RunTimeTicks"),
            container = sources?.str("Container")?.substringBefore(',') ?: o.str("Container"),
            mediaSourceId = sources?.str("Id"),
            primaryTag = o.obj("ImageTags")?.str("Primary"),
            backdropTag = (o.arr("BackdropImageTags")?.firstOrNull() as? JsonPrimitive)?.contentOrNull,
            seriesId = o.str("SeriesId"),
            season = (o["ParentIndexNumber"] as? JsonPrimitive)?.intOrNull,
            episode = (o["IndexNumber"] as? JsonPrimitive)?.intOrNull,
            addedAt = o.str("DateCreated"),
            positionTicks = ud?.long("PlaybackPositionTicks") ?: 0L,
            played = (ud?.get("Played") as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    // ───────────────────────── Adresses ─────────────────────────

    /** Affiche du serveur. Aucune authentification n'est ajoutée : la valeur stockée ne contient jamais le jeton. */
    fun imageUrl(base: String, itemId: String, type: String = "Primary", tag: String? = null, maxWidth: Int = 480): String =
        "$base/Items/$itemId/Images/$type?maxWidth=$maxWidth&quality=90" + (tag?.takeIf { it.isNotBlank() }?.let { "&tag=$it" } ?: "")

    /**
     * Lecture directe (Direct Play : le fichier d'origine, sans transcodage) : `/Videos/{id}/stream.{conteneur}?static=true`.
     * L'extension aide le lecteur à choisir son extracteur ; elle est omise si le conteneur est inconnu ou suspect.
     */
    fun playUrl(base: String, itemId: String, mediaSourceId: String? = null, container: String? = null): String {
        val ext = container?.lowercase()?.takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() } && it.length <= 5 }
        return "$base/Videos/$itemId/stream" + (ext?.let { ".$it" } ?: "") + "?static=true" +
            (mediaSourceId?.takeIf { it.isNotBlank() }?.let { "&MediaSourceId=$it" } ?: "")
    }

    /** En-têtes de lecture : le jeton voyage dans un en-tête, jamais dans l'adresse. */
    fun playHeaders(token: String): Map<String, String> = mapOf("X-Emby-Token" to token)

    // ───────────────────────── Catalogue local ─────────────────────────

    /** « 01:23:45 » depuis des ticks ; null si inconnue ou nulle. */
    fun durationText(ticks: Long?): String? {
        val ms = ticks?.div(TICKS_PER_MS) ?: return null
        if (ms <= 0) return null
        val s = ms / 1000
        return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    /** Valeur croissante pour « derniers ajoutés » : date de création (ISO) en secondes depuis 1970, 0 si absente ou illisible. */
    fun addedKey(iso: String?): Long {
        val s = iso?.substringBefore('.')?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
        return runCatching { java.time.Instant.parse(if (s.endsWith("Z")) s else "${s}Z").epochSecond }.getOrDefault(0L)
    }

    fun toMovie(providerId: Long, base: String, it: Item, categoryId: String?): MovieEntity = MovieEntity(
        providerId = providerId, remoteId = it.id, name = it.name,
        poster = it.primaryTag?.let { t -> imageUrl(base, it.id, "Primary", t) },
        categoryId = categoryId, streamUrl = MediaLink.build(providerId, it.id),
        container = it.container, year = it.year, rating = it.rating, plot = it.overview,
        backdrop = it.backdropTag?.let { t -> imageUrl(base, it.id, "Backdrop", t, 1280) },
        genre = it.genres.joinToString(", ").ifEmpty { null }, cast = it.cast.take(8).joinToString(", ").ifEmpty { null },
        duration = durationText(it.runtimeTicks), addedKey = addedKey(it.addedAt),
    )

    fun toSeries(providerId: Long, base: String, it: Item, categoryId: String?): SeriesEntity = SeriesEntity(
        providerId = providerId, remoteId = it.id, name = it.name,
        poster = it.primaryTag?.let { t -> imageUrl(base, it.id, "Primary", t) },
        categoryId = categoryId, year = it.year, rating = it.rating, plot = it.overview,
        backdrop = it.backdropTag?.let { t -> imageUrl(base, it.id, "Backdrop", t, 1280) },
        genre = it.genres.joinToString(", ").ifEmpty { null }, cast = it.cast.take(8).joinToString(", ").ifEmpty { null },
        addedKey = addedKey(it.addedAt),
    )

    fun toEpisode(providerId: Long, base: String, seriesRowId: Long, it: Item, position: Int): EpisodeEntity = EpisodeEntity(
        seriesId = seriesRowId, remoteId = it.id, season = it.season ?: 1, episode = it.episode ?: position, title = it.name,
        streamUrl = MediaLink.build(providerId, it.id), container = it.container, plot = it.overview,
        image = it.primaryTag?.let { t -> imageUrl(base, it.id, "Primary", t, 640) }, duration = durationText(it.runtimeTicks),
    )

    // ───────────────────────── Requêtes ─────────────────────────

    /** Champs demandés pour les listes : de quoi remplir le catalogue sans second appel par titre. */
    const val LIST_FIELDS = "Overview,Genres,People,ProductionYear,CommunityRating,DateCreated,MediaSources,RunTimeTicks,BackdropImageTags,ImageTags"

    fun itemsUrl(base: String, userId: String, libraryId: String, type: String, start: Int, limit: Int = PAGE_SIZE): String =
        "$base/Users/$userId/Items?ParentId=$libraryId&IncludeItemTypes=$type&Recursive=true&Fields=$LIST_FIELDS&SortBy=SortName&SortOrder=Ascending&StartIndex=$start&Limit=$limit"

    fun episodesUrl(base: String, seriesId: String, userId: String): String =
        "$base/Shows/$seriesId/Episodes?UserId=$userId&Fields=Overview,MediaSources,RunTimeTicks,ImageTags"

    fun itemUrl(base: String, userId: String, itemId: String): String = "$base/Users/$userId/Items/$itemId"

    // ───────────────────────── Reprise et rapports de lecture ─────────────────────────

    fun ticksToMs(ticks: Long): Long = (ticks / TICKS_PER_MS).coerceAtLeast(0)
    fun msToTicks(ms: Long): Long = ms.coerceAtLeast(0) * TICKS_PER_MS

    /**
     * Position de reprise : celle du SERVEUR si elle existe (elle suit l'utilisateur d'un appareil à l'autre, y compris
     * hors de cette appli), sinon la position locale. Un élément vu en entier (`played`, position remise à zéro)
     * repart du début, sans retomber sur une position locale périmée.
     */
    fun resumeMs(server: Item?, localMs: Long): Long = when {
        server == null -> localMs
        server.positionTicks > 0 -> ticksToMs(server.positionTicks)
        server.played -> 0L
        else -> localMs
    }

    /** Type de rapport : début de lecture, progression, fin. */
    enum class Report(val path: String) { START("/Sessions/Playing"), PROGRESS("/Sessions/Playing/Progress"), STOP("/Sessions/Playing/Stopped") }

    /** Corps d'un rapport de lecture (`PlayMethod` = lecture directe, le seul mode de cette appli). */
    fun reportBody(itemId: String, mediaSourceId: String?, positionMs: Long, paused: Boolean): String = JsonObject(buildMap {
        put("ItemId", JsonPrimitive(itemId))
        mediaSourceId?.takeIf { it.isNotBlank() }?.let { put("MediaSourceId", JsonPrimitive(it)) }
        put("PositionTicks", JsonPrimitive(msToTicks(positionMs)))
        put("IsPaused", JsonPrimitive(paused))
        put("PlayMethod", JsonPrimitive("DirectPlay"))
        put("CanSeek", JsonPrimitive(true))
    }).toString()
}

/**
 * Lien interne `mediaserver://<source>/<élément>` stocké à la place d'une adresse de flux : l'adresse réelle (avec le
 * serveur) est reconstruite à l'ouverture, et le jeton ne quitte jamais la base chiffrée.
 */
object MediaLink {
    const val SCHEME = "mediaserver://"

    data class Parsed(val providerId: Long, val itemId: String)

    fun isMedia(url: String?): Boolean = url != null && url.startsWith(SCHEME)

    fun build(providerId: Long, itemId: String): String = "$SCHEME$providerId/$itemId"

    fun parse(url: String): Parsed? {
        if (!isMedia(url)) return null
        val rest = url.removePrefix(SCHEME)
        val slash = rest.indexOf('/')
        if (slash <= 0) return null
        val pid = rest.substring(0, slash).toLongOrNull() ?: return null
        val item = rest.substring(slash + 1).substringBefore('?').takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } } ?: return null
        return Parsed(pid, item)
    }
}
