package com.ultratv.tv.nativeapp.nav

import java.net.URI
import java.net.URLEncoder

/**
 * Liens profonds de l'application (rappels, Google TV : Watch Next, chaîne Favoris, recherche vocale).
 *   ultratv://live/{providerId}/{remoteId}      lecture d'une chaîne
 *   ultratv://movie/{providerId}/{remoteId}     lecture d'un film
 *   ultratv://episode/{providerId}/{remoteId}   lecture d'un épisode
 *   ultratv://series/{providerId}/{remoteId}    fiche d'une série
 *   ultratv://moviedetail/{providerId}/{remoteId} fiche d'un film (sans lancer la lecture)
 *   ultratv://search?q=…                        écran Recherche
 *   ultratv://home|live|guide|movies|series|replay rubrique (launcher Kairo) ; ultratv://live?category=… ouvre le direct
 *                                               sur cette catégorie si elle existe (et n'est ni masquée ni verrouillée)
 * Un lien `ultratv:` inconnu ou mal formé ouvre l'accueil ([resolve]), jamais un écran vide.
 * Les identifiants distants sont encodés (ils peuvent contenir « / », espaces…). Un lien ne désigne
 * jamais une URL de flux : il est résolu dans le catalogue local de l'utilisateur.
 */
sealed interface DeepLink {
    data class PlayLive(val providerId: Long, val remoteId: String) : DeepLink
    data class PlayMovie(val providerId: Long, val remoteId: String) : DeepLink
    data class PlayEpisode(val providerId: Long, val remoteId: String) : DeepLink
    data class OpenSeries(val providerId: Long, val remoteId: String) : DeepLink
    data class OpenMovie(val providerId: Long, val remoteId: String) : DeepLink
    data class Search(val query: String) : DeepLink
    /** Rubrique (route de navigation de [SECTIONS]) ; [category] = identifiant distant de catégorie du direct, facultatif. */
    data class OpenSection(val route: String, val category: String? = null) : DeepLink

    companion object {
        const val SCHEME = "ultratv"

        /** Rubriques ouvrables par lien : hôte du lien = route de navigation. */
        val SECTIONS = setOf(Routes.HOME, Routes.LIVE, Routes.GUIDE, Routes.MOVIES, Routes.SERIES, Routes.REPLAY)
        fun isSectionRoute(route: String) = route in SECTIONS

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        private fun dec(s: String) = SafeDecode.url(s.replace("+", "%2B"))

        fun live(providerId: Long, remoteId: String) = "$SCHEME://live/$providerId/${enc(remoteId)}"
        fun movie(providerId: Long, remoteId: String) = "$SCHEME://movie/$providerId/${enc(remoteId)}"
        fun episode(providerId: Long, remoteId: String) = "$SCHEME://episode/$providerId/${enc(remoteId)}"
        fun series(providerId: Long, remoteId: String) = "$SCHEME://series/$providerId/${enc(remoteId)}"
        fun movieDetail(providerId: Long, remoteId: String) = "$SCHEME://moviedetail/$providerId/${enc(remoteId)}"
        fun search(query: String) = "$SCHEME://search?q=${enc(query)}"
        fun section(route: String) = "$SCHEME://$route"
        fun liveCategory(category: String) = "$SCHEME://${Routes.LIVE}?category=${enc(category)}"

        /**
         * Comme [parse], mais un lien de notre schéma inconnu ou mal formé donne l'accueil (contrat Kairo) ;
         * null seulement pour un lien vide ou d'un autre schéma.
         */
        fun resolve(raw: String?): DeepLink? {
            if (raw.isNullOrBlank()) return null
            if (!raw.trim().startsWith("$SCHEME:", ignoreCase = true)) return null
            return runCatching { parse(raw) }.getOrNull() ?: OpenSection(Routes.HOME)
        }

        private fun queryParam(uri: URI, name: String): String? = uri.rawQuery?.split('&')
            ?.map { it.substringBefore('=') to it.substringAfter('=', "") }
            ?.firstOrNull { it.first == name }?.second?.let(::dec)?.trim()

        fun parse(raw: String?): DeepLink? {
            if (raw.isNullOrBlank()) return null
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            if (uri.scheme != SCHEME) return null
            val kind = uri.host ?: return null
            if (kind == "search") {
                val q = queryParam(uri, "q")
                return q?.takeIf { it.isNotEmpty() }?.let(::Search)
            }
            if (kind in SECTIONS && uri.rawPath.orEmpty().trim('/').isEmpty()) {
                val cat = if (kind == Routes.LIVE) queryParam(uri, "category")?.takeIf { it.isNotEmpty() } else null
                return OpenSection(kind, cat)
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
                "moviedetail" -> OpenMovie(pid, rid)
                else -> null
            }
        }
    }
}
