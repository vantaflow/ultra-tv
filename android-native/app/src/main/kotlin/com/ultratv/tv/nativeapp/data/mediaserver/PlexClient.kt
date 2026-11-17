package com.ultratv.tv.nativeapp.data.mediaserver

import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Client HTTP de Plex : code PIN, liste des serveurs du compte, bibliothèques, films, séries, épisodes, élément (reprise) et
 * rapports de lecture. Toute la logique est dans [Plex] ; ici, seulement l'envoi des requêtes.
 *
 * Le jeton n'est JAMAIS journalisé ni placé dans une adresse : il part dans l'en-tête `X-Plex-Token`, et les exceptions
 * ne portent que le code HTTP (jamais l'adresse).
 */
class PlexClient(okBase: OkHttpClient, private val deviceId: () -> String) {
    private val ok: OkHttpClient = okBase.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Sonde de connexion : courte, pour passer vite à l'adresse suivante. */
    private val probeClient: OkHttpClient = okBase.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()

    /** Durées connues par élément (ms), pour les rapports de lecture (le lecteur ne les transmet pas). */
    private val durations = ConcurrentHashMap<String, Long>()

    private fun base(p: ProviderEntity) = p.baseUrl.trimEnd('/')

    private fun request(url: String, token: String?, post: Boolean = false): Request =
        Request.Builder().url(url).apply {
            Plex.headers(deviceId(), token).forEach { (k, v) -> header(k, v) }
            if (post) post(ByteArray(0).toRequestBody(null))
        }.build()

    private suspend fun execute(client: OkHttpClient, req: Request): String = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            resp.body?.string().orEmpty()
        }
    }

    // ───────────────────────── Connexion par code PIN ─────────────────────────

    /** Demande un code à saisir sur plex.tv/link. */
    suspend fun createPin(): Plex.Pin =
        Plex.parsePin(execute(ok, request(Plex.PINS_URL, null, post = true))) ?: throw java.io.IOException("plex: bad pin response")

    /** Jeton du compte une fois le code validé ; null tant que l'utilisateur n'a pas saisi le code. */
    suspend fun pollPin(id: Long): String? = Plex.parsePin(execute(ok, request(Plex.pinCheckUrl(id), null)))?.authToken

    /** Serveurs du compte (propres d'abord), connexions classées. */
    suspend fun servers(accountToken: String): List<Plex.Server> =
        Plex.parseServers(execute(ok, request(Plex.RESOURCES_URL, accountToken)))

    /** Première connexion du serveur qui répond (locale d'abord, puis distante, puis relais) ; null si aucune. */
    suspend fun bestConnection(server: Plex.Server): Plex.Connection? {
        for (c in server.connections) {
            val up = try {
                execute(probeClient, request(c.uri + "/identity", server.accessToken)); true
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Throwable) { false }
            if (up) return c
        }
        return null
    }

    // ───────────────────────── Catalogue ─────────────────────────

    private suspend fun get(p: ProviderEntity, url: String): String = execute(ok, request(url, p.password))

    suspend fun libraries(p: ProviderEntity): List<MediaServer.Library> = Plex.parseSections(get(p, Plex.sectionsUrl(base(p))))

    private suspend fun allItems(p: ProviderEntity, sectionId: String, plexType: Int): List<Plex.Item> {
        val out = ArrayList<Plex.Item>()
        var start = 0
        while (true) {
            val page = Plex.parseItems(get(p, Plex.itemsUrl(base(p), sectionId, plexType, start)))
            out += page.items
            start += Plex.PAGE_SIZE
            if (page.items.isEmpty() || out.size >= page.total || start >= MAX_ITEMS) break
        }
        return out
    }

    suspend fun movies(p: ProviderEntity, sectionId: String): List<MovieEntity> =
        allItems(p, sectionId, 1).map { Plex.toMovie(p.id, base(p), it, sectionId) }

    suspend fun series(p: ProviderEntity, sectionId: String): List<SeriesEntity> =
        allItems(p, sectionId, 2).map { Plex.toSeries(p.id, base(p), it, sectionId) }

    /** Épisodes d'une série (toutes saisons) ; numérotés dans l'ordre du serveur si l'index manque. */
    suspend fun episodes(p: ProviderEntity, showKey: String, seriesRowId: Long): List<EpisodeEntity> =
        Plex.parseItems(get(p, Plex.episodesUrl(base(p), showKey))).items.filter { it.type.isEmpty() || it.type == "episode" }
            .mapIndexed { i, it -> Plex.toEpisode(p.id, base(p), seriesRowId, it, i + 1) }

    suspend fun item(p: ProviderEntity, ratingKey: String): Plex.Item? =
        Plex.parseItem(get(p, Plex.itemUrl(base(p), ratingKey)))?.also { i -> i.durationMs?.let { durations[key(p, ratingKey)] = it } }

    private fun key(p: ProviderEntity, ratingKey: String) = "${p.id}/$ratingKey"

    // ───────────────────────── Lecture ─────────────────────────

    /** Lecture directe (fichier d'origine) + en-têtes portant le jeton. Lève une [java.io.IOException] si l'élément n'a pas de fichier. */
    suspend fun openUrl(p: ProviderEntity, ratingKey: String): Pair<String, Map<String, String>> {
        val it = item(p, ratingKey)
        val url = Plex.playUrl(base(p), it?.partKey) ?: throw java.io.IOException("plex: no media part")
        return url to Plex.playHeaders(p.password, deviceId())
    }

    suspend fun resumeMs(p: ProviderEntity, ratingKey: String, localMs: Long): Long =
        Plex.resumeMs(runCatching { item(p, ratingKey) }.getOrNull(), localMs)

    /** Rapport de lecture (`/:/timeline`) ; ne lève jamais (hors annulation). */
    suspend fun report(p: ProviderEntity, kind: MediaServer.Report, ratingKey: String, positionMs: Long, paused: Boolean) {
        try {
            val dur = durations[key(p, ratingKey)] ?: item(p, ratingKey)?.durationMs ?: 0L
            execute(ok, request(Plex.timelineUrl(base(p), ratingKey, Plex.timelineState(kind, paused), positionMs, dur), p.password))
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Throwable) { /* un rapport perdu n'empêche rien : le suivant le rattrape */ }
    }

    private companion object {
        const val MAX_ITEMS = 200_000
    }
}
