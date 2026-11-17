package com.ultratv.tv.nativeapp.data.mediaserver

import com.ultratv.tv.nativeapp.data.db.ProviderDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chargement des affiches Plex : les adresses stockées n'ont JAMAIS de jeton ; celui-ci est ajouté ici, en en-tête
 * `X-Plex-Token`, aux seules requêtes d'images (`/photo/:/transcode`) à destination d'un serveur Plex déjà ajouté
 * (jamais vers un autre hôte). Intercepteur du client réseau de Coil.
 */
@Singleton
class PlexImageAuth @Inject constructor(private val providers: ProviderDao) : Interceptor {
    private val tokens = ConcurrentHashMap<String, String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (!isPlexImage(req.url)) return chain.proceed(req)
        val origin = originOf(req.url)
        val first = tokens[origin] ?: run { refresh(); tokens[origin] } ?: return chain.proceed(req)
        val resp = chain.proceed(req.newBuilder().header("X-Plex-Token", first).build())
        if (resp.code != 401) return resp
        // Jeton renouvelé depuis (nouvelle connexion au compte) : une seule nouvelle tentative.
        refresh()
        val again = tokens[origin]?.takeIf { it != first } ?: return resp
        resp.close()
        return chain.proceed(req.newBuilder().header("X-Plex-Token", again).build())
    }

    private fun refresh() {
        runCatching {
            runBlocking { providers.observeAll().first() }.filter { it.kind == Plex.KIND }.forEach { p ->
                p.baseUrl.toHttpUrlOrNull()?.let { tokens[originOf(it)] = p.password }
            }
        }
    }

    companion object {
        fun isPlexImage(url: HttpUrl) = url.encodedPath.startsWith("/photo/:/transcode")
        fun originOf(url: HttpUrl) = "${url.scheme}://${url.host}:${url.port}"
    }
}
