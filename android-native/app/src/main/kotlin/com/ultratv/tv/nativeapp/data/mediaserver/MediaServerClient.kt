package com.ultratv.tv.nativeapp.data.mediaserver

import com.ultratv.tv.nativeapp.data.config.InstallIdStore
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client HTTP de Jellyfin / Emby : connexion, bibliothèques, films, séries, épisodes, élément (reprise) et rapports de
 * lecture. Toute la logique (URL, analyse, conversion) est dans [MediaServer] ; ici, seulement l'envoi des requêtes.
 *
 * Le jeton (mot de passe de la source) n'est JAMAIS journalisé : les exceptions ne portent que le code HTTP, jamais l'adresse.
 */
@Singleton
class MediaServerClient(okBase: OkHttpClient, private val deviceId: () -> String) {
    @Inject constructor(okBase: OkHttpClient, install: InstallIdStore) : this(okBase, { install.get() })

    // Listes volumineuses sur un NAS parfois lent : pas de délai global, 10 s de connexion et 45 s d'inactivité.
    private val ok: OkHttpClient = okBase.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val jsonType = "application/json".toMediaType()

    /** Plex (connexion par code PIN, jeton dans un en-tête) : même interface, autre protocole. */
    val plex = PlexClient(okBase, deviceId)

    private fun base(p: ProviderEntity) = p.baseUrl.trimEnd('/')

    private fun Request.Builder.auth(token: String?) = apply {
        val h = MediaServer.authorization(deviceId(), token = token)
        header("X-Emby-Authorization", h)
        header("Authorization", h)
        if (!token.isNullOrEmpty()) header("X-Emby-Token", token)
        header("Accept", "application/json")
    }

    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        ok.newCall(req).execute().use { resp ->
            // Pas d'adresse dans le message : elle peut contenir un identifiant.
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            resp.body?.string().orEmpty()
        }
    }

    private suspend fun get(p: ProviderEntity, url: String): String =
        execute(Request.Builder().url(url).auth(p.password).get().build())

    // ───────────────────────── Connexion ─────────────────────────

    /** Résultat de la connexion : identifiant interne de l'utilisateur et jeton à ranger dans la source. */
    data class Login(val userId: String, val userName: String, val token: String)

    /**
     * Connexion par identifiant et mot de passe (`AuthenticateByName`) ; ou, si [apiKey] est fourni, par clé API (l'utilisateur
     * est alors choisi dans la liste du serveur : celui de [username], sinon un administrateur). Lève [HttpStatusException]
     * (401 = identifiants refusés) ou une erreur réseau ; une réponse inattendue lève une [java.io.IOException].
     */
    suspend fun login(server: String, username: String, password: String, apiKey: String = ""): Login {
        if (apiKey.isNotBlank()) {
            val key = apiKey.trim()
            val users = execute(Request.Builder().url("$server/Users").auth(key).get().build())
            val u = MediaServer.pickUser(users, username) ?: throw java.io.IOException("media server: no user")
            return Login(u.id, u.name, key)
        }
        val body = MediaServer.authBody(username, password).toRequestBody(jsonType)
        val text = execute(Request.Builder().url("$server/Users/AuthenticateByName").auth(null).post(body).build())
        val a = MediaServer.parseAuth(text) ?: throw java.io.IOException("media server: bad auth response")
        return Login(a.userId, a.userName, a.token)
    }

    // ───────────────────────── Catalogue ─────────────────────────

    /** Bibliothèques de films et de séries de l'utilisateur. */
    suspend fun libraries(p: ProviderEntity): List<MediaServer.Library> =
        if (p.kind == Plex.KIND) plex.libraries(p) else MediaServer.parseViews(get(p, "${base(p)}/Users/${p.username}/Views"))

    /** Tous les éléments d'un type dans une bibliothèque, lus page par page. */
    private suspend fun allItems(p: ProviderEntity, libraryId: String, type: String): List<MediaServer.Item> {
        val out = ArrayList<MediaServer.Item>()
        var start = 0
        while (true) {
            val page = MediaServer.parseItems(get(p, MediaServer.itemsUrl(base(p), p.username, libraryId, type, start)))
            out += page.items
            start += MediaServer.PAGE_SIZE
            if (page.items.isEmpty() || out.size >= page.total || start >= MAX_ITEMS) break
        }
        return out
    }

    suspend fun movies(p: ProviderEntity, libraryId: String): List<MovieEntity> =
        if (p.kind == Plex.KIND) plex.movies(p, libraryId) else allItems(p, libraryId, "Movie").map { MediaServer.toMovie(p.id, base(p), it, libraryId) }

    suspend fun series(p: ProviderEntity, libraryId: String): List<SeriesEntity> =
        if (p.kind == Plex.KIND) plex.series(p, libraryId) else allItems(p, libraryId, "Series").map { MediaServer.toSeries(p.id, base(p), it, libraryId) }

    /** Épisodes d'une série (toutes saisons), numérotés dans l'ordre du serveur si l'index manque. */
    suspend fun episodes(p: ProviderEntity, seriesRemoteId: String, seriesRowId: Long): List<EpisodeEntity> {
        if (p.kind == Plex.KIND) return plex.episodes(p, seriesRemoteId, seriesRowId)
        val page = MediaServer.parseItems(get(p, MediaServer.episodesUrl(base(p), seriesRemoteId, p.username)))
        return page.items.filter { it.type.isEmpty() || it.type == "Episode" }
            .mapIndexed { i, it -> MediaServer.toEpisode(p.id, base(p), seriesRowId, it, i + 1) }
    }

    /** Fiche d'un élément avec l'état de lecture de l'utilisateur (conteneur, source, position de reprise) ; null si injoignable. */
    suspend fun item(p: ProviderEntity, itemId: String): MediaServer.Item? =
        MediaServer.parseItem(get(p, MediaServer.itemUrl(base(p), p.username, itemId)))

    // ───────────────────────── Lecture ─────────────────────────

    /** Adresse de lecture directe + en-têtes (jeton) d'un élément de la source [p]. */
    suspend fun openUrl(p: ProviderEntity, itemId: String): Pair<String, Map<String, String>> {
        if (p.kind == Plex.KIND) return plex.openUrl(p, itemId)
        val it = runCatching { item(p, itemId) }.getOrNull()
        val url = MediaServer.playUrl(base(p), itemId, it?.mediaSourceId, it?.container)
        return url to MediaServer.playHeaders(p.password)
    }

    /** Reprise stockée sur le serveur (ms) mêlée à la position locale ; en cas d'échec réseau, la position locale. */
    suspend fun resumeMs(p: ProviderEntity, itemId: String, localMs: Long): Long =
        if (p.kind == Plex.KIND) plex.resumeMs(p, itemId, localMs) else MediaServer.resumeMs(runCatching { item(p, itemId) }.getOrNull(), localMs)

    /** Rapport de lecture (début, progression, fin) ; ne lève jamais (hors annulation) : jamais bloquant pour le lecteur. */
    suspend fun report(p: ProviderEntity, kind: MediaServer.Report, itemId: String, positionMs: Long, paused: Boolean) {
        if (p.kind == Plex.KIND) return plex.report(p, kind, itemId, positionMs, paused)
        try {
            val body = MediaServer.reportBody(itemId, null, positionMs, paused).toRequestBody(jsonType)
            execute(Request.Builder().url(base(p) + kind.path).auth(p.password).post(body).build())
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Throwable) { /* un rapport perdu n'empêche rien : le suivant le rattrape */ }
    }

    private companion object {
        /** Garde-fou : un serveur qui ignore la pagination ne boucle pas indéfiniment. */
        const val MAX_ITEMS = 200_000
    }
}
