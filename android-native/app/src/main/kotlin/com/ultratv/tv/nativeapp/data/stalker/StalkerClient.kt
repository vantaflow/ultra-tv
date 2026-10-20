package com.ultratv.tv.nativeapp.data.stalker

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import com.ultratv.tv.nativeapp.data.xtream.XtreamAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée de l'API retenu pour chaque portail (sondé à l'ajout, puis réutilisé) : `…/portal.php` ou
 * `…/server/load.php`. Clé = adresse du portail telle que saisie ; aucune donnée secrète.
 */
@Singleton
class StalkerEndpointStore internal constructor(
    private val read: (String) -> String?,
    private val write: (String, String?) -> Unit,
) {
    @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext ctx: android.content.Context) : this(
        { k -> ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(k, null) },
        { k, v -> ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().apply { if (v == null) remove(k) else putString(k, v) }.apply() },
    )

    fun get(portal: String): String? = read(key(portal))
    fun put(portal: String, endpoint: String) = write(key(portal), endpoint)
    fun forget(portal: String) = write(key(portal), null)

    private fun key(portal: String) = "ep:" + portal.trim().trimEnd('/').lowercase()

    companion object {
        private const val PREFS = "stalker_endpoints"
        fun inMemory(): StalkerEndpointStore {
            val m = java.util.concurrent.ConcurrentHashMap<String, String>()
            return StalkerEndpointStore({ m[it] }, { k, v -> if (v == null) m.remove(k) else m[k] = v })
        }
    }
}

/**
 * Client du middleware Stalker / Ministra (requêtes HTTP GET « boîtier MAG »).
 *
 * Session : sonde du point d'entrée (une fois par portail), `stb/handshake` → jeton, `stb/get_profile`, puis
 * `stb/do_auth` si un identifiant est fourni. Le jeton est gardé en mémoire ; une réponse « Authorization failed »,
 * un 401/403 ou une réponse qui n'est pas du JSON du portail provoque UNE nouvelle poignée de main puis un nouvel essai.
 * Aucun identifiant, MAC ou jeton n'est jamais journalisé.
 */
@Singleton
class StalkerClient @Inject constructor(okBase: OkHttpClient, private val endpoints: StalkerEndpointStore) {
    // Listes de plusieurs Mo : pas de délai global d'appel, connexion 10 s et 30 s d'inactivité en lecture.
    private val ok: OkHttpClient = okBase.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Connexion à un portail : adresse saisie, MAC normalisée, identifiants facultatifs. */
    data class Account(val portal: String, val mac: String, val login: String = "", val password: String = "", val device: StalkerDevice = StalkerDevice(mac)) {
        val key: String get() = portal.trim().trimEnd('/').lowercase() + "|" + mac
        override fun toString() = "Stalker(${StalkerPortal.referer(portal).substringBefore('?')})"   // jamais la MAC ni les identifiants
    }

    private data class Session(val api: String, val token: String)

    private val sessions = java.util.concurrent.ConcurrentHashMap<String, Session>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /** Réponse refusée par le portail (jeton périmé, MAC inconnue…) : classée « identifiants refusés » (401). */
    private fun denied() = HttpStatusException(401, "Stalker: authorization failed")
    private fun HttpStatusException.isAuth() = code == 401 || code == 403

    fun accountOf(p: ProviderEntity): Account {
        val (login, pass) = StalkerCreds.unpack(p.password)
        return Account(p.baseUrl, StalkerMac.normalize(p.username) ?: p.username.uppercase(), login, pass)
    }

    // ───────────────────────── Session ─────────────────────────

    /**
     * Vérifie la connexion (formulaire d'ajout) : sonde le point d'entrée, fait la poignée de main et l'authentification.
     * Lève une exception explicite en cas d'échec ; le point d'entrée qui répond est mémorisé.
     */
    suspend fun probe(a: Account): String = session(a, fresh = true).api

    private suspend fun session(a: Account, fresh: Boolean = false): Session {
        if (!fresh) sessions[a.key]?.let { return it }
        val lock = locks.getOrPut(a.key) { Mutex() }
        return lock.withLock {
            if (!fresh) sessions[a.key]?.let { return@withLock it }
            val s = open(a)
            sessions[a.key] = s
            s
        }
    }

    private fun invalidate(a: Account, stale: Session?) {
        if (stale == null) sessions.remove(a.key) else sessions.remove(a.key, stale)
    }

    private suspend fun open(a: Account): Session {
        val known = endpoints.get(a.portal)
        val candidates = listOfNotNull(known) + StalkerPortal.endpointCandidates(a.portal).filter { it != known }
        var last: Throwable? = null
        for (api in candidates) {
            val token = try { handshake(a, api) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { last = t; null }
                ?: continue
            if (api != known) endpoints.put(a.portal, api)
            val s = Session(api, token)
            // Profil : indispensable sur la plupart des portails pour « activer » le jeton ; son contenu est sans importance ici.
            runCatching { raw(a, s, profileParams(a)) }
            if (a.login.isNotEmpty()) {
                val ok = jsOf(StalkerJson.parse(raw(a, s, listOf(
                    "type" to "stb", "action" to "do_auth", "login" to a.login, "password" to a.password,
                    "device_id" to a.device.deviceId, "device_id2" to a.device.deviceId2,
                ))))
                if (ok == false || ok == "false") throw denied()
            }
            return s
        }
        // Le point d'entrée mémorisé ne répond plus : il sera sondé de nouveau la prochaine fois.
        if (known != null) endpoints.forget(a.portal)
        throw last ?: denied()
    }

    /** `stb/handshake` → jeton ; null si la réponse n'en contient pas (mauvais point d'entrée). */
    private suspend fun handshake(a: Account, api: String): String? {
        val body = get(a, api, null, listOf("type" to "stb", "action" to "handshake", "token" to "", "prehash" to "0"))
        val js = jsOf(StalkerJson.parse(body)) as? JMap ?: return null
        return js.s("token")
    }

    private fun profileParams(a: Account): List<Pair<String, String>> = listOf(
        "type" to "stb", "action" to "get_profile", "hd" to "1",
        "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Thu Sep 13 11:31:16 EEST 2018; PORTAL version: 5.6.1; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
        "num_banks" to "2", "sn" to a.device.sn, "stb_type" to a.device.model, "client_type" to "STB", "image_version" to "218",
        "video_out" to "hdmi", "device_id" to a.device.deviceId, "device_id2" to a.device.deviceId2, "signature" to a.device.signature,
        "auth_second_step" to "1", "hw_version" to "1.7-BD-00", "not_valid_token" to "0", "api_signature" to "262", "prehash" to "0",
        "timestamp" to (System.currentTimeMillis() / 1000).toString(),
    )

    // ───────────────────────── HTTP ─────────────────────────

    private fun request(a: Account, api: String, token: String?, params: List<Pair<String, String>>): Request {
        val url = api + "?" + (params + ("JsHttpRequest" to "1-xml")).joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        return Request.Builder().url(url)
            .header("User-Agent", StalkerDevice.USER_AGENT)
            .header("X-User-Agent", StalkerDevice.X_USER_AGENT)
            .header("Cookie", "mac=${enc(a.mac)}; stb_lang=en; timezone=UTC")
            .header("Referer", StalkerPortal.referer(a.portal))
            .header("Accept", "*/*")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
    }

    private suspend fun get(a: Account, api: String, token: String?, params: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        ok.newCall(request(a, api, token, params)).execute().use { resp ->
            // Pas d'URL dans le message : elle contient la MAC.
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            resp.body?.string().orEmpty()
        }
    }

    private suspend fun raw(a: Account, s: Session, params: List<Pair<String, String>>): String = get(a, s.api, s.token, params)

    /** Une réponse « qui n'est pas du portail » (texte « Authorization failed. », HTML…) : jeton à refaire. */
    private fun refused(body: String): Boolean {
        val t = body.trimStart()
        return !t.startsWith("{") || body.contains("Authorization failed", ignoreCase = true)
    }

    /** Appel authentifié → racine `js` ; une seule nouvelle poignée de main si le portail refuse le jeton. */
    internal suspend fun call(a: Account, params: List<Pair<String, String>>): Any? {
        repeat(2) { attempt ->
            val s = session(a)
            val body = try { raw(a, s, params) } catch (e: HttpStatusException) { if (e.isAuth() && attempt == 0) { invalidate(a, s); return@repeat } else throw e }
            if (refused(body)) { if (attempt == 0) { invalidate(a, s); return@repeat } else throw denied() }
            val root = StalkerJson.parse(body)
            if (root is Map<*, *> && root.containsKey("js")) return root["js"]
            if (attempt == 0) invalidate(a, s) else throw denied()
        }
        throw denied()
    }

    /**
     * Grande liste lue en FLUX : la réponse est d'abord écrite dans un fichier temporaire (la transaction d'écriture de
     * l'appelant n'attend jamais le réseau), puis chaque objet de `js.data` est lu un à un.
     */
    private suspend fun spool(a: Account, params: List<Pair<String, String>>): java.io.File = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            val s = session(a)
            val f = java.io.File.createTempFile("stalker-", ".json")
            try {
                ok.newCall(request(a, s.api, s.token, params)).execute().use { resp ->
                    if (!resp.isSuccessful) throw HttpStatusException(resp.code)
                    f.outputStream().buffered().use { out -> resp.body?.byteStream()?.copyTo(out) }
                }
                val head = f.inputStream().use { ins -> ByteArray(256).let { b -> String(b, 0, ins.read(b).coerceAtLeast(0), Charsets.UTF_8) } }
                if (!refused(head)) return@withContext f
                f.delete()
                if (attempt == 0) invalidate(a, s) else throw denied()
            } catch (e: HttpStatusException) {
                f.delete()
                if (e.isAuth() && attempt == 0) invalidate(a, s) else throw e
            } catch (t: Throwable) {
                f.delete(); throw t
            }
        }
        throw denied()
    }

    // ───────────────────────── Direct ─────────────────────────

    suspend fun genres(p: ProviderEntity): List<CategoryEntity> =
        StalkerParse.categories(p.id, "LIVE", call(accountOf(p), listOf("type" to "itv", "action" to "get_genres")))

    /**
     * Toutes les chaînes en flux (`itv/get_all_channels`) ; si le portail ne la propose pas (ou la renvoie vide),
     * repli sur la liste paginée `get_ordered_list&genre=*`.
     */
    suspend fun <R> withAllChannels(p: ProviderEntity, block: suspend (Sequence<ChannelEntity>) -> R): R {
        val a = accountOf(p)
        val file = runCatching { spool(a, listOf("type" to "itv", "action" to "get_all_channels")) }.getOrNull()
        if (file != null) {
            try {
                val rows = ArrayList<ChannelEntity>()
                val ok = file.reader(Charsets.UTF_8).buffered().use { StalkerJson.forEachDataItem(it) { o -> StalkerParse.channel(p.id, p.baseUrl, o)?.let(rows::add) } }
                if (ok && rows.isNotEmpty()) return block(rows.asSequence())
            } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Exception) { /* repli paginé */ } finally { file.delete() }
        }
        return block(pagedItems(a, listOf("type" to "itv", "action" to "get_ordered_list", "genre" to "*", "fav" to "0", "sortby" to "number")).mapNotNull { StalkerParse.channel(p.id, p.baseUrl, it) }.asSequence())
    }

    /** Chaînes d'un genre (liste paginée). */
    suspend fun channelsOfGenre(p: ProviderEntity, genreId: String): List<ChannelEntity> =
        pagedItems(accountOf(p), listOf("type" to "itv", "action" to "get_ordered_list", "genre" to genreId, "fav" to "0", "sortby" to "number"))
            .mapNotNull { StalkerParse.channel(p.id, p.baseUrl, it) }

    /**
     * Toutes les pages d'une liste `get_ordered_list` : la page 1 donne `total_items` et `max_page_items`, les suivantes
     * sont lues à 3 en parallèle. Plafond de sécurité : 3 000 pages (un portail qui annonce n'importe quoi).
     */
    internal suspend fun pagedItems(a: Account, params: List<Pair<String, String>>): List<JMap> {
        val first = StalkerParse.page(call(a, params + ("p" to "1"))) ?: return emptyList()
        val pages = first.pages.coerceIn(1, MAX_PAGES)
        if (pages == 1 || first.items.isEmpty()) return first.items
        val gate = Semaphore(PAGE_PARALLELISM)
        val rest = coroutineScope {
            (2..pages).map { n -> async { gate.withPermit { StalkerParse.page(call(a, params + ("p" to n.toString())))?.items.orEmpty() } } }.awaitAll()
        }
        return (listOf(first.items) + rest).flatten().distinctBy { it.s("id") ?: it.hashCode().toString() }
    }

    // ───────────────────────── Films et séries ─────────────────────────

    suspend fun vodCategories(p: ProviderEntity, kind: String = "MOVIE"): List<CategoryEntity> =
        StalkerParse.categories(p.id, kind, call(accountOf(p), listOf("type" to "vod", "action" to "get_categories")))

    /** Films et séries d'une catégorie VOD (les séries sont les éléments `is_series` / à tableau `series`). */
    data class VodSplit(val movies: List<MovieEntity>, val series: List<StalkerParse.VodSeries>)

    suspend fun vodOfCategory(p: ProviderEntity, categoryId: String): VodSplit {
        val items = pagedItems(accountOf(p), listOf("type" to "vod", "action" to "get_ordered_list", "category" to categoryId, "fav" to "0", "sortby" to "added"))
        val movies = ArrayList<MovieEntity>()
        val series = ArrayList<StalkerParse.VodSeries>()
        for (o in items) when (val v = StalkerParse.vod(p.id, p.baseUrl, o, categoryId)) {
            is StalkerParse.VodMovie -> movies += v.movie
            is StalkerParse.VodSeries -> { series += v; rememberSeries(p.id, v) }
            null -> Unit
        }
        return VodSplit(movies, series)
    }

    /** Commande et numéros d'épisodes des séries vues à la dernière synchro : la fiche s'ouvre sans nouvel aller-retour. */
    private val seriesHints = java.util.concurrent.ConcurrentHashMap<String, Pair<String, List<Int>>>()

    private fun rememberSeries(pid: Long, v: StalkerParse.VodSeries) {
        if (seriesHints.size > 50_000) seriesHints.clear()
        seriesHints["$pid:${v.series.remoteId}"] = v.cmd to v.episodes
    }

    /**
     * Épisodes d'une série. Portail récent : saisons (`movie_id=…&season_id=0` → `is_season`) puis épisodes
     * (`season_id=…` → `is_episode`, chacun sa commande). Portail classique : la série porte un tableau `series`
     * (numéros d'épisodes) et sa commande ; on joue `create_link&series=N`.
     */
    suspend fun episodes(p: ProviderEntity, s: SeriesEntity): List<EpisodeEntity> {
        val a = accountOf(p)
        val base = listOf("type" to "vod", "action" to "get_ordered_list", "movie_id" to s.remoteId, "season_id" to "0", "episode_id" to "0", "fav" to "0", "sortby" to "added")
        val listed = runCatching { pagedItems(a, base) }.getOrDefault(emptyList())
        val seasons = listed.filter { it.flag("is_season") }
        if (seasons.isNotEmpty()) {
            return seasons.withIndex().flatMap { (i, season) ->
                val no = StalkerParse.seasonNumber(season, i)
                val sid = season.s("id") ?: return@flatMap emptyList()
                val eps = runCatching { pagedItems(a, base.map { if (it.first == "season_id") "season_id" to sid else it }) }.getOrDefault(emptyList())
                    .filter { it.flag("is_episode") || it.s("cmd") != null }
                if (eps.isNotEmpty()) eps.withIndex().mapNotNull { (j, e) -> StalkerParse.seasonEpisode(p.id, s.id, s.remoteId, no, e, j) }
                else {
                    // Saison sans liste d'épisodes : sa commande + son tableau `series`.
                    @Suppress("UNCHECKED_CAST")
                    val nums = (season["series"] as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.toIntOrNull() }
                    season.s("cmd")?.let { StalkerParse.simpleEpisodes(p.id, s.id, s.remoteId, it, nums, no) }.orEmpty()
                }
            }
        }
        val hint = seriesHints["${p.id}:${s.remoteId}"]
            ?: listed.firstOrNull { it.s("id") == s.remoteId }?.let { o -> (StalkerParse.vod(p.id, p.baseUrl, o, s.categoryId) as? StalkerParse.VodSeries)?.let { it.cmd to it.episodes } }
            ?: s.categoryId?.let { cat -> runCatching { vodOfCategory(p, cat) }.getOrNull()?.series?.firstOrNull { it.series.remoteId == s.remoteId }?.let { it.cmd to it.episodes } }
            ?: return emptyList()
        return StalkerParse.simpleEpisodes(p.id, s.id, s.remoteId, hint.first, hint.second)
    }

    // ───────────────────────── Lecture, guide, compte ─────────────────────────

    /**
     * Lien de lecture à durée de vie courte (`create_link`), demandé au moment de jouer. Le préfixe de lecteur
     * (« ffmpeg », « auto ») est retiré. Échec du portail : la commande elle-même si elle est déjà une adresse jouable.
     */
    suspend fun createLink(p: ProviderEntity, type: String, cmd: String, series: Int? = null): String {
        val params = buildList {
            add("type" to (if (type == "vod") "vod" else "itv")); add("action" to "create_link"); add("cmd" to cmd)
            add("series" to (series?.toString() ?: "")); add("forced_storage" to "undefined"); add("disable_ad" to "0"); add("download" to "0")
        }
        val js = runCatching { call(accountOf(p), params) }.getOrElse { e -> StalkerLink.directUrlOf(cmd)?.let { return it }; throw e }
        val link = (js as? JMap)?.s("cmd")?.let(StalkerLink::cleanCmd)?.takeIf { it.isNotBlank() }
        return link ?: StalkerLink.directUrlOf(cmd) ?: throw java.io.IOException("Stalker: empty link")
    }

    /** Programme court d'une chaîne (`itv/get_short_epg`). */
    suspend fun shortEpg(p: ProviderEntity, channelRemoteId: String, channelLocalId: Long): List<EpgEntity> =
        StalkerParse.shortEpg(channelLocalId, call(accountOf(p), listOf("type" to "itv", "action" to "get_short_epg", "ch_id" to channelRemoteId, "size" to "10")))

    /** Abonnement (`account_info/get_main_info`) ; null si le portail est injoignable. */
    suspend fun fetchAccount(p: ProviderEntity): XtreamAccount? = runCatching {
        val a = accountOf(p)
        val info = runCatching { call(a, listOf("type" to "account_info", "action" to "get_main_info")) }.getOrNull()
        val profile = runCatching { call(a, profileParams(a)) }.getOrNull()
        StalkerParse.account(info, profile)
    }.getOrNull()

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val MAX_PAGES = 3_000
        private const val PAGE_PARALLELISM = 3
    }
}
