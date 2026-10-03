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
                is UnknownHostException -> return SyncErrorKind.HOST_NOT_FOUND
                is SocketTimeoutException -> return SyncErrorKind.TIMEOUT
                is ConnectException, is NoRouteToHostException -> return SyncErrorKind.UNREACHABLE
                is SSLException -> return SyncErrorKind.TLS
                is InterruptedIOException -> if (cur.message?.contains("timeout", ignoreCase = true) == true) return SyncErrorKind.TIMEOUT
            }
            cur = cur.cause
        }
        return SyncErrorKind.OTHER
    }

    fun fromHttp(code: Int): SyncErrorKind = when {
        code == 401 || code == 403 -> SyncErrorKind.UNAUTHORIZED
        code == 404 || code == 410 -> SyncErrorKind.NOT_FOUND
        code in 500..599 -> SyncErrorKind.SERVER_ERROR
        code >= 600 || code in 400..499 -> SyncErrorKind.PROVIDER_BLOCKED
        else -> SyncErrorKind.OTHER
    }
}
