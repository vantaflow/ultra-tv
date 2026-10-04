package com.ultratv.tv.nativeapp.nav

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Liens profonds de l'application (rappels, Google TV : Watch Next, chaîne Favoris, recherche vocale).
 *   ultratv://live/{providerId}/{remoteId}      lecture d'une chaîne
 *   ultratv://movie/{providerId}/{remoteId}     lecture d'un film
 *   ultratv://episode/{providerId}/{remoteId}   lecture d'un épisode
 *   ultratv://series/{providerId}/{remoteId}    fiche d'une série
 *   ultratv://search?q=…                        écran Recherche
 * Les identifiants distants sont encodés (ils peuvent contenir « / », espaces…). Un lien ne désigne
 * jamais une URL de flux : il est résolu dans le catalogue local de l'utilisateur.
 */
sealed interface DeepLink {
    data class PlayLive(val providerId: Long, val remoteId: String) : DeepLink
    data class PlayMovie(val providerId: Long, val remoteId: String) : DeepLink
    data class PlayEpisode(val providerId: Long, val remoteId: String) : DeepLink
    data class OpenSeries(val providerId: Long, val remoteId: String) : DeepLink
    data class Search(val query: String) : DeepLink

    companion object {
        const val SCHEME = "ultratv"

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        private fun dec(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

        fun live(providerId: Long, remoteId: String) = "$SCHEME://live/$providerId/${enc(remoteId)}"
        fun movie(providerId: Long, remoteId: String) = "$SCHEME://movie/$providerId/${enc(remoteId)}"
        fun episode(providerId: Long, remoteId: String) = "$SCHEME://episode/$providerId/${enc(remoteId)}"
        fun series(providerId: Long, remoteId: String) = "$SCHEME://series/$providerId/${enc(remoteId)}"
        fun search(query: String) = "$SCHEME://search?q=${enc(query)}"

        fun parse(raw: String?): DeepLink? {
            if (raw.isNullOrBlank()) return null
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            if (uri.scheme != SCHEME) return null
            val kind = uri.host ?: return null
            if (kind == "search") {
                val q = (uri.rawQuery ?: return null).split('&')
                    .map { it.substringBefore('=') to it.substringAfter('=', "") }
                    .firstOrNull { it.first == "q" }?.second?.let(::dec)?.trim()
                return q?.takeIf { it.isNotEmpty() }?.let(::Search)
            }
            val parts = (uri.rawPath ?: return null).trim('/').split('/')
            if (parts.size != 2) return null
            val pid = parts[0].toLongOrNull() ?: return null
            val rid = runCatching { dec(parts[1]) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            return when (kind) {
                "live" -> PlayLive(pid, rid)
                "movie" -> PlayMovie(pid, rid)
                "episode" -> PlayEpisode(pid, rid)
                "series" -> OpenSeries(pid, rid)
                else -> null
            }
        }
    }
}
