package com.ultratv.tv.nativeapp.data.remote

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom

/** Réseau local seulement : choix de l'adresse d'écoute et filtrage des clients. */
object RemoteNet {
    /** Adresse privée (192.168/16, 10/8, 172.16/12), lien local, boucle locale ou IPv6 privée (fc00::/7). */
    fun isLocalPeer(a: InetAddress): Boolean {
        if (a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress) return true
        val b = a.address
        if (b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC) return true
        // IPv4 mappée en IPv6 (::ffff:a.b.c.d) : java la ramène en Inet4Address ; rien de plus à faire.
        return false
    }

    /** Première adresse IPv4 privée (Wi-Fi / Ethernet) : la socket n'écoute QUE dessus, jamais sur une interface publique. */
    fun pickLocalAddress(candidates: List<InetAddress>): Inet4Address? =
        candidates.filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }

    fun interfaceAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && !it.isVirtual }.flatMap { it.inetAddresses.toList() }
    }.getOrDefault(emptyList())

    /** Jeton aléatoire de 128 bits en hexadécimal (32 caractères). */
    fun newToken(random: SecureRandom = SecureRandom()): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun url(ip: String, port: Int, token: String) = "http://$ip:$port/r?k=$token"

    /** Langue de la page : celle de l'appli (fr / en / es / ar) ; « system » suit la langue de l'appareil, anglais par défaut. */
    fun pageLang(appLang: String, systemLang: String): String {
        val c = if (appLang == "system") systemLang else appLang
        return if (c in setOf("fr", "en", "es", "ar")) c else "en"
    }
}
