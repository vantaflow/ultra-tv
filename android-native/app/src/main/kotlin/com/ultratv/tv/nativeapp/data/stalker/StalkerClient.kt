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
import kotlinx.coroutines.launch
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
 * Le portail n'a pas donné de lien exploitable pour la chaîne. [reason] (jamais de contenu de réponse) :
 * `empty_response` (corps vide, `js` vide ou faux), `no_cmd` (JSON sans `cmd`), `blank_cmd`, `not_url` (`cmd` qui n'est pas une
 * adresse), `provider_missing` (source supprimée), `unresolved` (adresse restée `stalker://`).
 */
class StalkerLinkException(val reason: String) : java.io.IOException("Stalker: no link ($reason)")

/**
 * Le portail a lié cette MAC à un AUTRE boîtier (« device conflict ») : il faut l'identité de ce boîtier (numéro de série,
 * Device ID). Levée seulement à l'ajout ([StalkerClient.probe]) ; ailleurs le comportement reste celui d'avant.
 */
class StalkerDeviceConflictException : java.io.IOException("Stalker: device conflict")

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

    /**
     * Petits appels du LECTEUR (poignée de main, profil et `do_auth` déclenchés par eux, create_link au zapping, set_played,
     * présence, programme court) : connexion 5 s, lecture 11 s, appel complet 12 s (le plafond de 12 s du lecteur borne
     * l'attente). Un portail muet ne bloque plus « Chargement » des minutes. Enregistrements, téléchargements, différé et
     * listes du catalogue gardent le client ci-dessus : un portail lent (9 s) reste lisible sans surveillance.
     */
    private val okShort: OkHttpClient = ok.newBuilder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(11, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** Marque de contexte : les requêtes faites dessous (session comprise) passent par [okShort]. */
    internal object ShortCalls : kotlin.coroutines.AbstractCoroutineContextElement(ShortCallsKey)
    internal object ShortCallsKey : kotlin.coroutines.CoroutineContext.Key<ShortCalls>

    private suspend fun client(): OkHttpClient = if (kotlinx.coroutines.currentCoroutineContext()[ShortCallsKey] != null) okShort else ok

    /** Délais (ms) du client des petits appels de lecture : connexion, lecture, appel complet (vérifiés par les tests). */
    internal val shortTimeoutsMs: Triple<Int, Int, Int> get() = Triple(okShort.connectTimeoutMillis, okShort.readTimeoutMillis, okShort.callTimeoutMillis)

    /** Connexion à un portail : adresse saisie, MAC normalisée, identifiants facultatifs. */
    data class Account(
        val portal: String, val mac: String, val login: String = "", val password: String = "",
        val identity: StalkerIdentity = StalkerIdentity(), val device: StalkerDevice = StalkerDevice.of(mac, identity),
    ) {
        val key: String get() = portal.trim().trimEnd('/').lowercase() + "|" + mac
        override fun toString() = "Stalker(${StalkerPortal.referer(portal).substringBefore('?')})"   // jamais la MAC ni les identifiants
    }

    /**
     * Identité de boîtier envoyée au profil : [DERIVED] = calculée depuis la MAC ou saisie (historique), [BARE] = MAC
     * seule, [LOWER] = calculée avec les Device ID en minuscules (forme attendue par certains portails).
     */
    internal enum class IdMode { DERIVED, BARE, LOWER }

    private data class Session(val api: String, val token: String, val mode: IdMode = IdMode.DERIVED) {
        val bare get() = mode == IdMode.BARE
    }

    /** Mode d'identité accepté par un portail quand ce n'est pas [IdMode.DERIVED] : repris en premier la fois suivante. */
    private val preferredMode = java.util.concurrent.ConcurrentHashMap<String, IdMode>()

    private val sessions = java.util.concurrent.ConcurrentHashMap<String, Session>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    /** Réponse refusée par le portail (jeton périmé, MAC inconnue…) : classée « identifiants refusés » (401). */
    private fun denied() = HttpStatusException(401, "Stalker: authorization failed")
    private fun HttpStatusException.isAuth() = code == 401 || code == 403

    fun accountOf(p: ProviderEntity): Account {
        val (login, pass) = StalkerCreds.unpack(p.password)
        return Account(p.baseUrl, StalkerMac.normalize(p.username) ?: p.username.uppercase(), login, pass, StalkerCreds.identity(p.password))
    }

    // ───────────────────────── Session ─────────────────────────

    /**
     * Vérifie la connexion (formulaire d'ajout) : sonde le point d'entrée, fait la poignée de main et l'authentification.
     * Lève une exception explicite en cas d'échec ; le point d'entrée qui répond est mémorisé.
     */
    suspend fun probe(a: Account): String {
        val api = session(a, fresh = true).api
        if (a.key in conflicts) { invalidate(a, null); throw StalkerDeviceConflictException() }
        return api
    }

    /** Portails dont le dernier essai n'a abouti que sur « device conflict » (aucun mode d'identité accepté). */
    private val conflicts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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
        // Premier point d'entrée dont la poignée de main répond mais dont le profil est refusé : gardé en dernier recours.
        // Un même serveur peut répondre à plusieurs chemins (portal.php d'un autre panneau + le vrai portail sous
        // /stalker_portal/) : s'arrêter au premier jeton faisait refuser toute la suite (« identifiants refusés »).
        var refusedFirst: Session? = null
        var conflict = false
        // D'abord l'identité de boîtier calculée depuis la MAC (comportement historique : rien ne change pour les portails
        // qui l'acceptent), puis, si le profil est refusé partout, la MAC seule : un portail dont l'abonnement a été
        // enregistré sans identité (ou avec une autre) refuse des valeurs qu'il ne connaît pas. Identité saisie par
        // l'utilisateur : envoyée telle quelle, pas d'essai sans.
        // Puis les Device ID en minuscules. Un refus compte aussi quand le portail répond un profil JSON « bloqué »
        // (status > 0 avec un message, sans id : « device conflict »…), pas seulement « Authorization failed ».
        val modes = if (!a.identity.isEmpty) listOf(IdMode.DERIVED)
            else StalkerProfile.modeOrder(preferredMode[a.key])
        for (mode in modes) {
            for (api in candidates) {
                val hello = try { handshake(a, api) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) { last = t; null }
                    ?: continue
                val s = Session(api, hello.token, mode)
                // Profil : indispensable sur la plupart des portails pour « activer » le jeton.
                val profile = try { raw(a, s, profileParams(a, mode, hello)) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { null }
                if (profile != null && (refused(profile) || (a.login.isEmpty() && StalkerProfile.blocked(profile)))) {
                    if (StalkerProfile.conflict(profile)) conflict = true
                    if (refusedFirst == null) refusedFirst = s
                    continue
                }
                conflicts.remove(a.key)
                if (mode != IdMode.DERIVED) { if (preferredMode.put(a.key, mode) != mode) com.ultratv.tv.nativeapp.UtvLog.w("StalkerProbe", "profile accepted mode=${mode.name.lowercase()}") }
                else preferredMode.remove(a.key)
                return finishOpen(a, s, known)
            }
            // Aucun point d'entrée ne répond du tout : inutile de tout reprendre sans identité.
            if (refusedFirst == null) break
        }
        refusedFirst?.let {
            if (conflict) conflicts.add(a.key) else conflicts.remove(a.key)
            com.ultratv.tv.nativeapp.UtvLog.w("StalkerProbe", "profile refused on every endpoint candidates=${candidates.size} conflict=$conflict")
            return finishOpen(a, it, known)
        }
        // Le point d'entrée mémorisé ne répond plus : il sera sondé de nouveau la prochaine fois. Pas sur un délai court
        // dépassé (zap sur un portail lent) : le point d'entrée n'est pas en cause, seul le client long décide de l'oublier.
        if (known != null && kotlinx.coroutines.currentCoroutineContext()[ShortCallsKey] == null) endpoints.forget(a.portal)
        throw last ?: denied()
    }

    /** Point d'entrée retenu : mémorisé, puis `do_auth` si un identifiant est fourni. */
    private suspend fun finishOpen(a: Account, s: Session, known: String?): Session {
        if (s.api != known) endpoints.put(a.portal, s.api)
        if (a.login.isNotEmpty()) {
            val ok = jsOf(StalkerJson.parse(raw(a, s, listOf(
                "type" to "stb", "action" to "do_auth", "login" to a.login, "password" to a.password,
            ) + when (s.mode) {
                IdMode.BARE -> emptyList()
                IdMode.LOWER -> listOf("device_id" to a.device.deviceId.lowercase(), "device_id2" to a.device.deviceId2.lowercase())
                IdMode.DERIVED -> listOf("device_id" to a.device.deviceId, "device_id2" to a.device.deviceId2)
            })))
            if (ok == false || ok == "false") throw denied()
            // Le protocole prévoit de redemander le profil une fois authentifié ; son résultat ne bloque jamais.
            try { raw(a, s, profileParams(a, s.mode, secondStep = true)) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { }
        }
        return s
    }

    /** Réponse utile de la poignée de main : jeton, valeur `random` et indicateur « jeton non valide » du portail. */
    private data class Hello(val token: String, val random: String, val notValid: Boolean)

    /** `stb/handshake` → [Hello] ; null si la réponse ne contient pas de jeton (mauvais point d'entrée). */
    private suspend fun handshake(a: Account, api: String): Hello? {
        val body = get(a, api, null, listOf("type" to "stb", "action" to "handshake", "token" to "", "prehash" to "0"))
        val js = jsOf(StalkerJson.parse(body)) as? JMap ?: return null
        val token = js.s("token") ?: return null
        return Hello(token, js.s("random").orEmpty(), js["not_valid"]?.toString()?.toDoubleOrNull() == 1.0)
    }

    /** Paramètres de `get_profile` selon [mode] (voir [IdMode]). */
    private fun profileParams(a: Account, mode: IdMode = IdMode.DERIVED, hello: Hello? = null, secondStep: Boolean = false): List<Pair<String, String>> {
        val bare = mode == IdMode.BARE
        // Essai « MAC seule » : forme standard du protocole (première étape, bloc `metrics` avec la valeur `random` de la
        // poignée de main, indicateur de jeton repris). Les autres modes gardent la forme historique, inchangée.
        if (bare) return listOf(
            "type" to "stb", "action" to "get_profile", "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Thu Sep 13 11:31:16 EEST 2018; PORTAL version: 5.6.1; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
            "num_banks" to "2", "stb_type" to a.device.model, "client_type" to "STB", "image_version" to "218", "video_out" to "hdmi",
            "auth_second_step" to (if (secondStep) "1" else "0"), "hw_version" to "1.7-BD-00",
            "not_valid_token" to (if (hello?.notValid == true) "1" else "0"),
            "metrics" to StalkerProfile.metrics(a.mac, a.device.model, hello?.random.orEmpty()),
            "api_signature" to "262", "prehash" to "0",
            "timestamp" to (System.currentTimeMillis() / 1000).toString(),
        )
        val d = if (mode == IdMode.LOWER) a.device.copy(deviceId = a.device.deviceId.lowercase(), deviceId2 = a.device.deviceId2.lowercase()) else a.device
        return listOf(
        "type" to "stb", "action" to "get_profile", "hd" to "1",
        "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Thu Sep 13 11:31:16 EEST 2018; PORTAL version: 5.6.1; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
        "num_banks" to "2",
    ) + (if (bare) emptyList() else listOf("sn" to d.sn)) + listOf(
        "stb_type" to d.model, "client_type" to "STB", "image_version" to "218", "video_out" to "hdmi",
    ) + (if (bare) emptyList() else listOf("device_id" to d.deviceId, "device_id2" to d.deviceId2, "signature" to d.signature)) + listOf(
        "auth_second_step" to "1", "hw_version" to "1.7-BD-00", "not_valid_token" to "0", "api_signature" to "262", "prehash" to "0",
        "timestamp" to (System.currentTimeMillis() / 1000).toString(),
    )
    }

    // ───────────────────────── HTTP ─────────────────────────

    private fun request(a: Account, api: String, token: String?, params: List<Pair<String, String>>): Request {
        val url = api + "?" + (params + ("JsHttpRequest" to "1-xml")).joinToString("&") { (k, v) -> "$k=${enc(v)}" }
        return Request.Builder().url(url)
            .header("User-Agent", StalkerDevice.USER_AGENT)
            .header("X-User-Agent", StalkerDevice.X_USER_AGENT)
            .header("Cookie", cookie(a))
            .header("Referer", StalkerPortal.referer(a.portal))
            .header("Accept", "*/*")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
    }

    private suspend fun get(a: Account, api: String, token: String?, params: List<Pair<String, String>>): String {
      val http = client()
      return withContext(Dispatchers.IO) {
        http.newCall(request(a, api, token, params)).execute().use { resp ->
            // Pas d'URL dans le message : elle contient la MAC.
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            resp.body?.string().orEmpty()
        }
      }
    }

    private suspend fun raw(a: Account, s: Session, params: List<Pair<String, String>>): String = get(a, s.api, s.token, params)

    /** Une réponse « qui n'est pas du portail » (texte « Authorization failed. », HTML…) : jeton à refaire. */
    private fun refused(body: String): Boolean {
        val t = body.trimStart()
        return !t.startsWith("{") || body.contains("Authorization failed", ignoreCase = true)
    }

    /** Appel authentifié → racine `js` ; une seule nouvelle poignée de main si le portail refuse le jeton. */
    internal suspend fun call(a: Account, params: List<Pair<String, String>>, blankIsEmpty: Boolean = false): Any? {
        repeat(2) { attempt ->
            val s = session(a)
            val body = try { raw(a, s, params) } catch (e: HttpStatusException) { if (e.isAuth() && attempt == 0) { invalidate(a, s); return@repeat } else throw e }
            if (refused(body)) { if (attempt == 0) { invalidate(a, s); return@repeat } else throw (if (blankIsEmpty && body.isBlank()) StalkerLinkException("empty_response") else denied()) }
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
     * sont lues à 3 en parallèle. Plafond de sécurité : [MAX_PAGES] pages (14 éléments par page sur la plupart des
     * portails : 50 000 titres = 3 572 pages). Portail qui ne donne pas `total_items` : pages lues une à une jusqu'à une
     * page vide ou déjà vue. Chaque page a ses propres nouvelles tentatives ([pageWithRetry]) ; une page toujours en échec
     * fait échouer la liste (la synchro saute alors la catégorie et garde son contenu précédent).
     */
    internal suspend fun pagedItems(a: Account, params: List<Pair<String, String>>): List<JMap> {
        val first = pageWithRetry(a, params + ("p" to "1")) ?: return emptyList()
        if (first.items.isEmpty()) return emptyList()
        val idOf = { o: JMap -> o.s("id") ?: o.hashCode().toString() }
        if (!first.totalKnown) {
            val out = LinkedHashMap<String, JMap>()
            first.items.forEach { out.putIfAbsent(idOf(it), it) }
            var n = 2
            while (n <= MAX_PAGES) {
                val items = pageWithRetry(a, params + ("p" to n.toString()))?.items.orEmpty()
                // Page vide, ou portail qui ignore `p` et renvoie toujours la même page : fin de la liste.
                if (items.isEmpty() || items.all { idOf(it) in out }) break
                items.forEach { out.putIfAbsent(idOf(it), it) }
                n++
            }
            return out.values.toList()
        }
        val pages = first.pages.coerceIn(1, MAX_PAGES)
        if (pages == 1) return first.items
        val gate = Semaphore(PAGE_PARALLELISM)
        val rest = coroutineScope {
            (2..pages).map { n -> async { gate.withPermit { pageWithRetry(a, params + ("p" to n.toString()))?.items.orEmpty() } } }.awaitAll()
        }
        return (listOf(first.items) + rest).flatten().distinctBy(idOf)
    }

    /**
     * Une page de liste, avec [PAGE_ATTEMPTS] essais au plus sur délai dépassé, coupure, 429 ou 5xx (délai croissant).
     * Le jeton périmé est déjà traité par [call] (nouvelle poignée de main) ; un refus persistant n'est pas réessayé ici.
     */
    internal suspend fun pageWithRetry(a: Account, params: List<Pair<String, String>>): StalkerPage? {
        val type = params.firstOrNull { it.first == "type" }?.second ?: "-"
        var wait = retryDelayMs
        for (attempt in 1 until PAGE_ATTEMPTS) {
            try {
                return StalkerParse.page(call(a, params))
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: HttpStatusException) {
                if (e.code != 429 && e.code < 500) throw e
                com.ultratv.tv.nativeapp.RemoteLog.info("StalkerSync", StalkerSyncLog.retry(type, attempt, e))
            } catch (e: StalkerLinkException) {
                throw e
            } catch (e: java.io.IOException) {
                com.ultratv.tv.nativeapp.RemoteLog.info("StalkerSync", StalkerSyncLog.retry(type, attempt, e))
            }
            kotlinx.coroutines.delay(wait); wait *= 2
        }
        return StalkerParse.page(call(a, params))
    }

    /** Délai avant la 2e tentative d'une page (doublé ensuite) ; réduit dans les tests. */
    @Volatile internal var retryDelayMs = 700L

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

    // ───────────────────────── Module « séries » (type=series) ─────────────────────────

    /**
     * Catégories du module `type=series` (portails Ministra récents, où les séries ne sont plus dans la VOD). Liste vide
     * si le portail ne propose pas ce module ; erreur réseau propagée (la synchro décide alors de se replier sur la VOD).
     * Identifiants préfixés par [SERIES_PREFIX].
     */
    suspend fun seriesCategories(p: ProviderEntity): List<CategoryEntity> =
        StalkerParse.categories(p.id, "SERIES", call(accountOf(p), listOf("type" to "series", "action" to "get_categories")))
            .map { it.copy(remoteId = SERIES_PREFIX + it.remoteId) }

    /** Séries d'une catégorie du module `type=series` ([categoryId] avec ou sans préfixe). */
    suspend fun seriesOfCategory(p: ProviderEntity, categoryId: String): List<StalkerParse.VodSeries> {
        val raw = categoryId.removePrefix(SERIES_PREFIX)
        val items = pagedItems(accountOf(p), listOf("type" to "series", "action" to "get_ordered_list", "category" to raw, "fav" to "0", "sortby" to "added"))
        return items.mapNotNull { StalkerParse.seriesModuleItem(p.id, p.baseUrl, it, SERIES_PREFIX + raw) }.onEach { rememberSeries(p.id, it) }
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
        // Série du module `type=series` : saisons et épisodes demandés à ce module, liens de type « series ».
        val module = s.remoteId.startsWith(SERIES_PREFIX)
        val apiType = if (module) "series" else "vod"
        val movieId = s.remoteId.removePrefix(SERIES_PREFIX)
        val base = listOf("type" to apiType, "action" to "get_ordered_list", "movie_id" to movieId, "season_id" to "0", "episode_id" to "0", "fav" to "0", "sortby" to "added")
        val listed = runCatching { pagedItems(a, base) }.getOrDefault(emptyList())
        // Module séries : certains portails ne posent pas `is_season` ; un élément qui porte un tableau `series` en est une.
        val seasons = listed.filter { it.flag("is_season") || (module && !it.flag("is_episode") && it["series"] is List<*>) }
        if (seasons.isNotEmpty()) {
            return seasons.withIndex().flatMap { (i, season) ->
                val no = StalkerParse.seasonNumber(season, i)
                val sid = season.s("id") ?: return@flatMap emptyList()
                val eps = runCatching { pagedItems(a, base.map { if (it.first == "season_id") "season_id" to sid else it }) }.getOrDefault(emptyList())
                    .filter { it.flag("is_episode") || (it.s("cmd") != null && !it.flag("is_season")) }
                if (eps.isNotEmpty()) eps.withIndex().mapNotNull { (j, e) -> StalkerParse.seasonEpisode(p.id, s.id, s.remoteId, no, e, j, apiType) }
                else {
                    // Saison sans liste d'épisodes : sa commande + son tableau `series`.
                    @Suppress("UNCHECKED_CAST")
                    val nums = (season["series"] as? List<Any?>).orEmpty().mapNotNull { (it as? String)?.toIntOrNull() }
                    season.s("cmd")?.let { StalkerParse.simpleEpisodes(p.id, s.id, s.remoteId, it, nums, no, apiType) }.orEmpty()
                }
            }
        }
        // Module séries sans saison : épisodes listés directement sous la série.
        if (module) {
            val eps = listed.filter { it.flag("is_episode") || it.s("cmd") != null }
            if (eps.isNotEmpty()) return eps.withIndex().mapNotNull { (j, e) -> StalkerParse.seasonEpisode(p.id, s.id, s.remoteId, 1, e, j, apiType) }
        }
        val hint = seriesHints["${p.id}:${s.remoteId}"]?.takeIf { it.first.isNotEmpty() && it.second.isNotEmpty() }
            ?: listed.firstOrNull { it.s("id") == movieId }?.let { o -> (StalkerParse.vod(p.id, p.baseUrl, o, s.categoryId) as? StalkerParse.VodSeries)?.let { it.cmd to it.episodes } }
            ?: s.categoryId?.let { cat ->
                val inCat = if (module) runCatching { seriesOfCategory(p, cat) }.getOrNull() else runCatching { vodOfCategory(p, cat) }.getOrNull()?.series
                inCat?.firstOrNull { it.series.remoteId == s.remoteId }?.let { it.cmd to it.episodes }
            }
            ?: return emptyList()
        if (hint.first.isEmpty()) return emptyList()
        return StalkerParse.simpleEpisodes(p.id, s.id, s.remoteId, hint.first, hint.second, linkType = apiType)
    }

    // ───────────────────────── Lecture, guide, compte ─────────────────────────

    /**
     * Lien de lecture à durée de vie courte (`create_link`), demandé au moment de jouer. Le préfixe de lecteur
     * (« ffmpeg », « auto ») est retiré. Échec du portail : la commande elle-même si elle est déjà une adresse jouable.
     */
    // Délais courts seulement au zapping : l'appelant du lecteur se place sous [ShortCalls] (voir StreamUrls.openForZap).
    suspend fun createLink(p: ProviderEntity, type: String, cmd: String, series: Int? = null, chId: String? = null): String =
        createLinkShort(p, type, cmd, series, chId)

    private suspend fun createLinkShort(p: ProviderEntity, type: String, cmd: String, series: Int?, chId: String?): String {
        if (type != "series") return createLinkAs(p, if (type == "vod") "vod" else "itv", cmd, series, chId)
        // Épisode du module `type=series` : la plupart des portails le servent par `vod/create_link`, les autres par
        // `series/create_link`. Premier refus : second module essayé ; échec des deux : la première erreur.
        return try {
            createLinkAs(p, "vod", cmd, series, null)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (first: Exception) {
            try { createLinkAs(p, "series", cmd, series, null) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Exception) { throw first }
        }
    }

    private suspend fun createLinkAs(p: ProviderEntity, type: String, cmd: String, series: Int?, chId: String?): String {
        val params = buildList {
            add("type" to type); add("action" to "create_link"); add("cmd" to cmd)
            if (type != "itv") add("series" to (series?.toString() ?: ""))   // pas de `series` pour une chaîne
            add("forced_storage" to "undefined"); add("disable_ad" to "0"); add("download" to "0")
        }
        val a = accountOf(p)
        // Deux demandes au plus : un `stream=` vide sans identifiant de chaîne connu est redemandé une fois.
        repeat(2) { attempt ->
            val js = runCatching { call(a, params, blankIsEmpty = true) }.getOrElse { e -> StalkerLink.directUrlOf(cmd)?.let { return it }; throw e }
            val rawCmd = (js as? JMap)?.s("cmd")
            val link = rawCmd?.let(StalkerLink::cleanCmd)?.takeIf { it.isNotBlank() } ?: return StalkerLink.directUrlOf(cmd) ?: throw StalkerLinkException(
                if (js == null || js == false || (js is List<*> && js.isEmpty()) || (js is Map<*, *> && js.isEmpty())) "empty_response" else if (rawCmd == null) "no_cmd" else "blank_cmd")
            // Une réponse qui n'est pas une adresse (nom de chaîne, chemin relatif) ne se lit pas : jamais passée au lecteur.
            if (!link.contains("://")) throw StalkerLinkException("not_url")
            if (type != "itv" || !StalkerLink.hasEmptyStream(link)) return link
            // Réponse incomplète (`stream=` vide, bug courant pour itv) : identifiant de la chaîne mémorisé, de la commande, ou de la réponse.
            val id = chId?.takeIf { it.isNotBlank() } ?: StalkerLink.channelIdOfCmd(cmd) ?: (js as? JMap)?.s("id")?.takeIf { it.isNotBlank() }
            if (id != null) return StalkerLink.fillEmptyStream(link, id)
            if (attempt == 1) return link
        }
        throw StalkerLinkException("empty_response")
    }

    /** Lien jouable + en-têtes que le lecteur doit envoyer avec (mêmes que le client : un portail refuse le flux sinon). */
    data class Play(val url: String, val headers: Map<String, String>, val userAgent: String)

    /** Envoi « en route, sans attendre » de `itv/set_played` : jamais bloquant pour la lecture, erreur ignorée (journal nettoyé). */
    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /**
     * Ce que fait un boîtier MAG au démarrage d'une chaîne (`itv/set_played&itv_id=<id>`), mêmes en-têtes et même jeton que
     * les autres appels. Certains portails n'activent la session de lecture qu'après cet appel.
     */
    suspend fun setPlayed(p: ProviderEntity, channelId: String) {
        withContext(ShortCalls) { call(accountOf(p), listOf("type" to "itv", "action" to "set_played", "itv_id" to channelId)) }
    }

    /** [createLink], puis les en-têtes de lecture (jeton à jour : la session existe après l'appel). */
    suspend fun openLink(p: ProviderEntity, type: String, cmd: String, series: Int? = null, chId: String? = null): Play {
        val url = createLink(p, type, cmd, series, chId)
        if (type != "vod" && type != "series") {
            val id = chId?.takeIf { it.isNotBlank() } ?: StalkerLink.channelIdOfCmd(cmd)
            if (id != null) background.launch {
                try { setPlayed(p, id) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (t: Throwable) {
                    com.ultratv.tv.nativeapp.RemoteLog.info("StalkerPlay", StalkerPlayLog.setPlayedFailed(t))
                }
            }
        }
        return Play(url, playbackHeaders(accountOf(p)), StalkerDevice.USER_AGENT)
    }

    /** En-têtes MAG pour un flux : X-User-Agent, Cookie `mac=…`, jeton Bearer s'il y en a un (le User-Agent se règle à part). */
    fun playbackHeaders(a: Account): Map<String, String> = linkedMapOf<String, String>().apply {
        put("X-User-Agent", StalkerDevice.X_USER_AGENT)
        put("Cookie", cookie(a))
        sessions[a.key]?.token?.takeIf { it.isNotEmpty() }?.let { put("Authorization", "Bearer $it") }
    }

    /**
     * Signal de présence d'un boîtier MAG qui regarde une chaîne (`watchdog/get_events`, toutes les ~2 min sur un vrai
     * boîtier) : certains portails coupent le direct d'un boîtier qui ne se manifeste plus. Mêmes en-têtes et même jeton
     * que les autres appels. Le résultat (événements du portail) est ignoré.
     */
    suspend fun watchdog(p: ProviderEntity) {
        withContext(ShortCalls) { call(accountOf(p), listOf("type" to "watchdog", "action" to "get_events", "cur_play_type" to "1", "event_active_id" to "0")) }
    }

    private fun cookie(a: Account) = "mac=${enc(a.mac)}; stb_lang=en; timezone=UTC"

    /** Programme court d'une chaîne (`itv/get_short_epg`). */
    suspend fun shortEpg(p: ProviderEntity, channelRemoteId: String, channelLocalId: Long): List<EpgEntity> =
        StalkerParse.shortEpg(channelLocalId, withContext(ShortCalls) { call(accountOf(p), listOf("type" to "itv", "action" to "get_short_epg", "ch_id" to channelRemoteId, "size" to "10")) })

    /** Abonnement (`account_info/get_main_info`) ; null si le portail est injoignable. */
    suspend fun fetchAccount(p: ProviderEntity): XtreamAccount? = runCatching {
        val a = accountOf(p)
        val info = runCatching { call(a, listOf("type" to "account_info", "action" to "get_main_info")) }.getOrNull()
        val profile = runCatching { call(a, profileParams(a)) }.getOrNull()
        StalkerParse.account(info, profile)
    }.getOrNull()

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        internal const val MAX_PAGES = 20_000
        private const val PAGE_PARALLELISM = 3
        internal const val PAGE_ATTEMPTS = 3
        /** Préfixe des catégories et séries venues du module `type=series` (les identifiants du module VOD peuvent coïncider). */
        const val SERIES_PREFIX = "s:"
    }
}

/** Lecture de la réponse à `get_profile` (règles pures, testées sans réseau). */
internal object StalkerProfile {
    /** Ordre des essais d'identité : le mode déjà accepté par ce portail d'abord, sinon l'historique en premier. */
    fun modeOrder(preferred: StalkerClient.IdMode?): List<StalkerClient.IdMode> {
        val base = listOf(StalkerClient.IdMode.DERIVED, StalkerClient.IdMode.BARE, StalkerClient.IdMode.LOWER)
        return if (preferred == null) base else listOf(preferred) + (base - preferred)
    }

    /** Bloc `metrics` du profil : JSON compact (MAC, modèle, type, valeur `random` de la poignée de main). */
    fun metrics(mac: String, model: String, random: String): String =
        org.json.JSONObject().put("mac", mac).put("model", model).put("type", "STB").put("random", random).toString()

    /**
     * Profil « bloqué » : JSON lisible mais sans `id`, avec `status` > 0 et un message (« device conflict », « serial
     * mismatch », « account blocked »…). Un profil avec `id` reste accepté même si `status` > 0.
     */
    fun blocked(body: String): Boolean {
        val js = jsOf(StalkerJson.parse(body)) as? JMap ?: return false
        val status = js["status"].toString().toDoubleOrNull() ?: return false
        val id = js["id"]?.toString().orEmpty()
        val msg = js["msg"]?.toString().orEmpty().replace(Regex("<[^>]*>"), " ").trim()
        return status > 0 && msg.isNotEmpty() && (id.isEmpty() || id == "null" || id == "0")
    }

    /** Profil bloqué parce que la MAC est liée à un autre boîtier (« device conflict », « serial mismatch »…). */
    fun conflict(body: String): Boolean {
        if (!blocked(body)) return false
        val js = jsOf(StalkerJson.parse(body)) as? JMap ?: return false
        val text = (js["msg"]?.toString().orEmpty() + " " + js["block_msg"]?.toString().orEmpty()).lowercase()
        return ("device" in text || "serial" in text || "stb" in text) && ("conflict" in text || "mismatch" in text || "damaged" in text)
    }
}
