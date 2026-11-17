package com.ultratv.tv.nativeapp.data.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException

/**
 * Détection des serveurs multimédia du réseau local : Jellyfin (UDP 7359, « Who is JellyfinServer? »), Emby (UDP 7359,
 * « who is EmbyServer? ») et Plex (GDM : « M-SEARCH * HTTP/1.1 » en multicast 239.0.0.250:32414).
 *
 * Le PARSING des réponses est fait par des fonctions pures (testées sans réseau). L'écoute ([scan]) est asynchrone, bornée
 * (3 s par défaut), ne lève jamais d'exception et émet la liste fusionnée au fil des réponses. Aucune adresse n'est journalisée.
 */
object MediaDiscovery {
    enum class Kind { JELLYFIN, EMBY, PLEX }

    /** Serveur détecté : [address] = adresse http(s) sans barre finale ; [id] = identifiant du serveur (sert au dédoublonnage). */
    data class Found(val kind: Kind, val name: String, val address: String, val id: String)

    const val JELLYFIN_MSG = "Who is JellyfinServer?"
    const val EMBY_MSG = "who is EmbyServer?"
    const val MEDIA_PORT = 7359
    const val GDM_MSG = "M-SEARCH * HTTP/1.1\r\n\r\n"
    const val GDM_ADDR = "239.0.0.250"
    const val GDM_PORT = 32414
    const val DEFAULT_TIMEOUT_MS = 3_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Adresse http(s) propre (sans barre finale) ou null. */
    fun cleanAddress(raw: String?): String? {
        val s = raw?.trim()?.trimEnd('/') ?: return null
        return s.takeIf { Regex("^https?://[^\\s/]+", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    }

    /** Réponse JSON d'un serveur Jellyfin / Emby : `{Address, Id, Name}` ; repli sur l'expéditeur (port 8096) sans adresse valide. */
    fun parseMediaReply(text: String, kind: Kind, from: String?): Found? {
        val o = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim()
        val address = cleanAddress(str("Address")) ?: from?.takeIf { it.isNotBlank() }?.let { "http://$it:8096" } ?: return null
        val name = str("Name").orEmpty().ifBlank { if (kind == Kind.EMBY) "Emby" else "Jellyfin" }
        return Found(kind, name, address, str("Id").orEmpty().ifBlank { address })
    }

    /** Réponse GDM de Plex : en-têtes « Clé: valeur » ; adresse = expéditeur, port annoncé (32400 par défaut). */
    fun parseGdmReply(text: String, from: String?): Found? {
        if (!Regex("^HTTP/1\\.[01] 200", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        if (from.isNullOrBlank()) return null
        val h = HashMap<String, String>()
        for (line in text.lineSequence().drop(1)) {
            val i = line.indexOf(':')
            if (i > 0) h[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val port = h["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 32400
        val id = h["resource-identifier"].orEmpty().ifBlank { "$from:$port" }
        return Found(Kind.PLEX, h["name"].orEmpty().ifBlank { "Plex" }, "http://$from:$port", id)
    }

    /** Fusionne les réponses : un serveur répondant plusieurs fois n'apparaît qu'une fois ; tri par nom puis type. */
    fun merge(list: List<Found?>): List<Found> =
        list.filterNotNull().distinctBy { "${it.kind}:${it.id}" }.sortedWith(compareBy<Found>({ it.name.lowercase() }, { it.kind.name }))

    /**
     * Cherche les serveurs pendant [timeoutMs] : émet la liste fusionnée à chaque nouvelle réponse (flux progressif), puis se termine.
     * Relançable (nouvel appel = nouvelle recherche). Le verrou multicast Wi-Fi est tenu pendant l'écoute puis relâché.
     */
    fun scan(context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Flow<List<Found>> = channelFlow {
        val lock = runCatching {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createMulticastLock("ultratv-discovery")?.apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val found = java.util.Collections.synchronizedList(ArrayList<Found?>())
        val emitLock = Any()
        fun push(f: Found?) { if (f == null) return; synchronized(emitLock) { found += f; trySend(merge(found.toList())) } }
        try {
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.coroutineScope {
                    launch { probe(JELLYFIN_MSG, MEDIA_PORT, "255.255.255.255", false, timeoutMs) { t, from -> push(parseMediaReply(t, Kind.JELLYFIN, from)) } }
                    launch { probe(EMBY_MSG, MEDIA_PORT, "255.255.255.255", false, timeoutMs) { t, from -> push(parseMediaReply(t, Kind.EMBY, from)) } }
                    launch { probe(GDM_MSG, GDM_PORT, GDM_ADDR, true, timeoutMs) { t, from -> push(parseGdmReply(t, from)) } }
                }
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    /** Une sonde = une socket (le type du serveur se déduit de la sonde qui l'a fait répondre) ; ne lève jamais. */
    private fun probe(message: String, port: Int, target: String, multicast: Boolean, timeoutMs: Long, onReply: (String, String?) -> Unit) {
        val socket: DatagramSocket = try { if (multicast) MulticastSocket().apply { timeToLive = 2 } else DatagramSocket().apply { broadcast = true } } catch (_: Throwable) { return }
        try {
            val data = message.toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(data, data.size, InetAddress.getByName(target), port))
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(2048)
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) break
                socket.soTimeout = left.toInt().coerceAtLeast(1)
                val p = DatagramPacket(buf, buf.size)
                try { socket.receive(p) } catch (_: SocketTimeoutException) { break }
                runCatching { onReply(String(p.data, 0, p.length, Charsets.UTF_8), p.address?.hostAddress) }
            }
        } catch (_: Throwable) {
            // Réseau absent, broadcast interdit, appareil en veille : la liste reste simplement vide.
        } finally {
            runCatching { socket.close() }
        }
    }
}
