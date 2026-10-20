package com.ultratv.tv.nativeapp.data.net.dns

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.DataInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom

/** Résultat d'une résolution : adresses et TTL annoncé (null = inconnu, le cache applique sa valeur par défaut). */
class Resolved(val addresses: List<InetAddress>, val ttlSeconds: Long?)

/** Un résolveur « non système ». Lève une exception en cas d'échec (le repli est décidé par [SwitchableDns]). */
fun interface DnsResolver {
    fun resolve(host: String): Resolved
}

/**
 * Client DNS classique (UDP 53, repli TCP si la réponse est tronquée), sans dépendance. Les questions A et AAAA
 * partent ensemble sur la même socket ; les serveurs sont essayés dans l'ordre, dans un budget total de [timeoutMs].
 * Identifiant de requête aléatoire et port source éphémère : une réponse falsifiée doit deviner les deux.
 */
class UdpDnsResolver(
    private val servers: List<InetAddress>,
    private val timeoutMs: Long = 1_800,
    private val port: Int = 53,
) : DnsResolver {

    override fun resolve(host: String): Resolved {
        if (servers.isEmpty()) throw UnknownHostException("aucun serveur DNS")
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        var last: Exception? = null
        for ((i, server) in servers.withIndex()) {
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            if (remaining <= 0) break
            // Le premier serveur n'accapare pas tout le budget : le second doit encore avoir sa chance.
            val budget = remaining / (servers.size - i)
            try {
                val r = queryServer(server, host, budget.coerceAtLeast(1))
                if (r.addresses.isNotEmpty()) return r
                last = UnknownHostException("$host : aucune adresse")
            } catch (e: Exception) {
                last = e
            }
        }
        throw (last as? UnknownHostException) ?: UnknownHostException("$host : ${last?.javaClass?.simpleName ?: "délai dépassé"}").apply { last?.let { initCause(it) } }
    }

    private fun queryServer(server: InetAddress, host: String, budgetMs: Long): Resolved {
        val deadline = System.nanoTime() + budgetMs * 1_000_000
        val idA = random.nextInt(0x10000)
        val idAaaa = (idA + 1 + random.nextInt(0xFFFE)) and 0xFFFF
        val pending = mutableMapOf(idA to DnsWire.TYPE_A, idAaaa to DnsWire.TYPE_AAAA)
        val v4 = ArrayList<InetAddress>(); val v6 = ArrayList<InetAddress>()
        var ttl: Long? = null
        DatagramSocket().use { sock ->
            sock.connect(server, port)
            for ((id, type) in pending) {
                val q = DnsWire.query(id, host, type)
                sock.send(DatagramPacket(q, q.size))
            }
            val buf = ByteArray(4096)
            while (pending.isNotEmpty()) {
                val left = (deadline - System.nanoTime()) / 1_000_000
                if (left <= 0) break
                sock.soTimeout = left.toInt().coerceAtLeast(1)
                val pkt = DatagramPacket(buf, buf.size)
                try { sock.receive(pkt) } catch (_: SocketTimeoutException) { break }
                val ans = runCatching { DnsWire.parse(buf, pkt.length, host) }.getOrNull() ?: continue
                val type = pending.remove(ans.id) ?: continue   // réponse étrangère ou dupliquée : ignorée
                val full = if (ans.truncated) tcpQuery(server, host, type, deadline) else ans
                if (full.rcode == DnsWire.RCODE_NXDOMAIN) throw UnknownHostException("$host : NXDOMAIN")
                (if (type == DnsWire.TYPE_A) v4 else v6) += full.addresses
                full.minTtlSeconds?.let { t -> ttl = minOf(ttl ?: t, t) }
            }
        }
        // AAAA perdue mais A reçue (ou l'inverse) : on se contente de ce qu'on a.
        if (v4.isEmpty() && v6.isEmpty() && pending.isNotEmpty()) throw SocketTimeoutException("DNS $server : délai dépassé")
        return Resolved(v4 + v6, ttl)
    }

    private fun tcpQuery(server: InetAddress, host: String, type: Int, deadline: Long): DnsWire.Answer {
        val left = ((deadline - System.nanoTime()) / 1_000_000).toInt()
        if (left <= 0) throw SocketTimeoutException("DNS TCP : délai dépassé")
        Socket().use { s ->
            s.connect(InetSocketAddress(server, port), left)
            s.soTimeout = left
            val id = random.nextInt(0x10000)
            val q = DnsWire.query(id, host, type)
            s.getOutputStream().apply { write(byteArrayOf((q.size shr 8).toByte(), q.size.toByte())); write(q); flush() }
            val input = DataInputStream(s.getInputStream())
            val len = input.readUnsignedShort()
            val body = ByteArray(len).also { input.readFully(it) }
            val ans = DnsWire.parse(body, len, host)
            if (ans.id != id) throw IOException("DNS TCP : identifiant inattendu")
            return ans
        }
    }

    private companion object { val random = SecureRandom() }
}

/**
 * DNS-over-HTTPS via okhttp-dnsoverhttps. [client] ne doit PAS utiliser [AppDns] (récursion) : c'est un client
 * dédié, au DNS du système, dont seule l'adresse du serveur DoH est court-circuitée par [bootstrap].
 * La bibliothèque ne remonte pas le TTL : le cache applique sa durée par défaut.
 */
class DohResolver(url: HttpUrl, bootstrap: List<InetAddress>, client: OkHttpClient) : DnsResolver {
    private val doh = DnsOverHttps.Builder()
        .client(client)
        .url(url)
        .apply { if (bootstrap.isNotEmpty()) bootstrapDnsHosts(bootstrap) }
        .includeIPv6(true)
        // Par défaut la bibliothèque refuse les adresses privées : un fournisseur IPTV sur le réseau local doit rester joignable.
        .resolvePrivateAddresses(true)
        .build()

    override fun resolve(host: String): Resolved = Resolved(doh.lookup(host), null)
}

/** Résolveur correspondant à un réglage, ou null pour « Système » / un réglage inutilisable. */
fun resolverFor(settings: DnsSettings, dohClient: () -> OkHttpClient, udpTimeoutMs: Long = 1_800): DnsResolver? {
    if (!settings.usable) return null
    return when (settings.mode) {
        DnsMode.SYSTEM -> null
        DnsMode.DOH -> DohResolver(settings.dohHttpUrl() ?: return null, settings.dohBootstrap(), dohClient())
        else -> UdpDnsResolver(settings.plainServers(), udpTimeoutMs)
    }
}
