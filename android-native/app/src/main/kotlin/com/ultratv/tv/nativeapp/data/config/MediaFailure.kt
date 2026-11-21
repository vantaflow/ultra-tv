package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.net.NetErrors
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind

/**
 * Cause lisible d'un serveur multimédia du compte (Jellyfin, Emby, NAS, Plex) que l'appareil n'a pas pu ajouter.
 * Pure : la liste des adresses de l'appareil est fournie par l'appelant.
 */
object MediaFailure {
    enum class Reason { OTHER_NETWORK, NO_ANSWER, REFUSED, AUTH, HOST_NOT_FOUND, NOT_FOUND, TLS, OTHER }

    /** « hôte:port » du serveur, pour le message (jamais d'identifiants : l'URL n'en contient pas). */
    fun hostOf(url: String): String {
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull()
        val host = u?.host ?: return url.substringAfter("://").substringBefore('/').take(60)
        return if (u.port > 0) "$host:${u.port}" else host
    }

    /**
     * Cause. Serveur sur une adresse LOCALE (192.168.x, 10.x, 172.16-31.x) qui ne répond pas, alors qu'aucune adresse de
     * l'appareil n'est sur le même réseau (/24) : l'appareil est ailleurs (autre réseau, VPN) — cas le plus fréquent.
     */
    fun reason(t: Throwable, url: String, deviceIps: List<String>): Reason = when (NetErrors.classify(t)) {
        SyncErrorKind.TIMEOUT, SyncErrorKind.UNREACHABLE -> {
            val host = runCatching { java.net.URI(url.trim()).host }.getOrNull().orEmpty()
            when {
                isPrivateIpv4(host) && deviceIps.none { sameLan(it, host) } -> Reason.OTHER_NETWORK
                NetErrors.classify(t) == SyncErrorKind.TIMEOUT -> Reason.NO_ANSWER
                else -> Reason.REFUSED
            }
        }
        SyncErrorKind.UNAUTHORIZED, SyncErrorKind.ACCOUNT_EXPIRED -> Reason.AUTH
        SyncErrorKind.HOST_NOT_FOUND -> Reason.HOST_NOT_FOUND
        SyncErrorKind.NOT_FOUND -> Reason.NOT_FOUND
        SyncErrorKind.TLS -> Reason.TLS
        else -> Reason.OTHER
    }

    fun isPrivateIpv4(h: String): Boolean {
        val p = h.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }

    /** Même réseau local : mêmes trois premiers octets (masque /24, le cas des box et routeurs domestiques). */
    fun sameLan(a: String, b: String): Boolean {
        val x = a.split('.'); val y = b.split('.')
        return x.size == 4 && y.size == 4 && x.take(3) == y.take(3)
    }

    /** Adresses IPv4 actuelles de l'appareil (interfaces actives, hors boucle locale). */
    fun deviceIpv4(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<java.net.Inet4Address>().mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())
}
