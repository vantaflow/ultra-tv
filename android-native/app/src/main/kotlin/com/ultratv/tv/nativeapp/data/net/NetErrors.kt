package com.ultratv.tv.nativeapp.data.net

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Réponse HTTP non réussie d'un fournisseur. */
class HttpStatusException(val code: Int, message: String = "HTTP $code") : IOException(message)

/**
 * Le fournisseur répond (souvent HTTP 200) mais refuse le compte : Xtream `user_info.auth = 0` ou statut Expired / Banned /
 * Disabled. Pas une [IOException] : ni nouvelle tentative, ni repli « catégorie par catégorie » ; la synchro échoue et
 * le catalogue en base reste en place. Aucun identifiant dans le message.
 */
class AccountRefusedException(val expired: Boolean) : RuntimeException(if (expired) "account expired" else "account refused")

/**
 * Liste vide (ou presque, voir [com.ultratv.tv.nativeapp.data.repo.CatalogGuard]) alors que la base contient un
 * catalogue : la synchro est refusée et RIEN n'est effacé. [part] = LIVE / MOVIE / SERIES.
 */
class EmptyCatalogException(val part: String, val before: Int, val received: Int) : RuntimeException("empty list $part: $received/$before")

/** Catégories d'échec réseau présentées à l'utilisateur (message traduit + action « corriger la source »). */
enum class SyncErrorKind {
    HOST_NOT_FOUND,     // DNS : le domaine n'existe plus (NXDOMAIN)
    TIMEOUT,            // pas de réponse (IP non routable, serveur figé)
    UNREACHABLE,        // connexion refusée / pas de route
    UNAUTHORIZED,       // 401 / 403 : identifiants refusés
    NOT_FOUND,          // 404 : mauvaise URL
    PROVIDER_BLOCKED,   // code HTTP non standard (ex. 884) : le fournisseur refuse ce type d'accès
    SERVER_ERROR,       // 5xx
    TLS,                // certificat / handshake
    RATE_LIMITED,       // 429 : trop de demandes, le fournisseur limite l'accès
    INVALID_RESPONSE,   // réponse illisible : page web ou texte à la place de la liste attendue
    LOCAL_NETWORK,      // adresse du réseau local (192.168.x, 10.x, 172.16-31.x) injoignable : autre réseau ou VPN actif
    ACCOUNT_EXPIRED,    // le fournisseur répond, mais l'abonnement est expiré (Xtream `user_info.status` = Expired)
    EMPTY_LIST,         // liste vide (ou presque) alors que la base contient un catalogue : rien n'est effacé
    OTHER,
}

object NetErrors {
    /** Parcourt la chaîne de causes et renvoie la catégorie la plus précise. */
    fun classify(t: Throwable): SyncErrorKind {
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth++ < 8) {
            when (cur) {
                is HttpStatusException -> return fromHttp(cur.code)
                is AccountRefusedException -> return if (cur.expired) SyncErrorKind.ACCOUNT_EXPIRED else SyncErrorKind.UNAUTHORIZED
                is EmptyCatalogException -> return SyncErrorKind.EMPTY_LIST
                is UnknownHostException -> return SyncErrorKind.HOST_NOT_FOUND
                is SocketTimeoutException -> return SyncErrorKind.TIMEOUT
                is ConnectException, is NoRouteToHostException -> return SyncErrorKind.UNREACHABLE
                is SSLException -> return SyncErrorKind.TLS
                is InterruptedIOException -> if (cur.message?.contains("timeout", ignoreCase = true) == true) return SyncErrorKind.TIMEOUT
                // Le serveur a répondu, mais pas la liste attendue (page d'accueil, page d'erreur en HTML, JSON tronqué).
                is kotlinx.serialization.SerializationException, is org.xmlpull.v1.XmlPullParserException -> return SyncErrorKind.INVALID_RESPONSE
            }
            cur = cur.cause
        }
        return SyncErrorKind.OTHER
    }

    /**
     * Catégorie pour une source d'adresse [url] : une adresse du réseau local qui ne répond pas est signalée à part.
     * « Le serveur ne répond pas » ne dit pas quoi faire, alors que la cause est presque toujours la même : l'appareil
     * n'est pas sur ce réseau (autre Wi-Fi, données mobiles) ou un VPN détourne le trafic.
     */
    fun classifyFor(t: Throwable, url: String?): SyncErrorKind {
        val kind = classify(t)
        if (kind != SyncErrorKind.TIMEOUT && kind != SyncErrorKind.UNREACHABLE) return kind
        return if (isPrivateHost(url)) SyncErrorKind.LOCAL_NETWORK else kind
    }

    /** L'adresse pointe-t-elle une machine du réseau local (IPv4 privée) ? Faux pour un nom de domaine. */
    fun isPrivateHost(url: String?): Boolean {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return false
        val host = runCatching { java.net.URI(if ("://" in raw) raw else "http://$raw").host }.getOrNull() ?: return false
        val p = host.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || host.count { it == '.' } != 3 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }

    fun fromHttp(code: Int): SyncErrorKind = when {
        code == 429 -> SyncErrorKind.RATE_LIMITED
        code == 401 || code == 403 -> SyncErrorKind.UNAUTHORIZED
        code == 404 || code == 410 -> SyncErrorKind.NOT_FOUND
        code in 500..599 -> SyncErrorKind.SERVER_ERROR
        code >= 600 || code in 400..499 -> SyncErrorKind.PROVIDER_BLOCKED
        else -> SyncErrorKind.OTHER
    }
}
