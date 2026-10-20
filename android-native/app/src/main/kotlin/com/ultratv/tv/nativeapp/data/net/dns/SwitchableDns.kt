package com.ultratv.tv.nativeapp.data.net.dns

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * [Dns] OkHttp qui lit le réglage COURANT à chaque résolution : changer de serveur s'applique aux requêtes
 * suivantes, sans redémarrer l'application ni reconstruire les clients.
 *
 * Garde-fous (un mauvais réglage ne doit jamais rendre l'appli inutilisable) :
 *  - « Système » ou réglage inutilisable : DNS du système, sans cache ni fil supplémentaire (comportement d'origine) ;
 *  - échec, réponse vide ou délai de [timeoutMs] dépassé : repli sur le DNS du système ;
 *  - cache : TTL annoncé, au minimum [MIN_TTL_S] (au plus [MAX_TTL_S]) ; un repli est mémorisé [MIN_TTL_S] pour
 *    ne pas payer le délai à chaque requête quand le serveur choisi est injoignable.
 */
class SwitchableDns(
    private val settings: () -> DnsSettings,
    private val resolverFactory: (DnsSettings) -> DnsResolver?,
    private val system: Dns = Dns.SYSTEM,
    private val timeoutMs: Long = 2_000,
    private val now: () -> Long = System::currentTimeMillis,
    private val executor: ExecutorService = sharedExecutor,
) : Dns {

    private class Entry(val addresses: List<InetAddress>, val expiresAt: Long)

    private val cache = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > MAX_ENTRIES
    }

    @Volatile private var current: Pair<DnsSettings, DnsResolver?>? = null

    private fun resolverOf(s: DnsSettings): DnsResolver? {
        current?.let { (cs, r) -> if (cs == s) return r }
        val r = runCatching { resolverFactory(s) }.getOrNull()
        current = s to r
        return r
    }

    fun clearCache() = synchronized(cache) { cache.clear() }

    override fun lookup(hostname: String): List<InetAddress> {
        val s = settings()
        if (s.mode == DnsMode.SYSTEM) return system.lookup(hostname)
        IpLiteral.parse(hostname)?.let { return listOf(it) }
        if (isLocalName(hostname)) return system.lookup(hostname)
        val resolver = resolverOf(s) ?: return system.lookup(hostname)
        val key = s.cacheKey + "|" + hostname.lowercase()
        val t = now()
        synchronized(cache) { cache[key]?.let { if (it.expiresAt > t) return it.addresses else cache.remove(key) } }

        val res = runCatching { withTimeout { resolver.resolve(hostname) } }.getOrNull()
        if (res != null && res.addresses.isNotEmpty()) {
            val ttl = (res.ttlSeconds ?: DEFAULT_TTL_S).coerceIn(MIN_TTL_S, MAX_TTL_S)
            put(key, res.addresses, ttl)
            return res.addresses
        }
        // Repli : le DNS du système (lève UnknownHostException s'il échoue lui aussi, comme sans réglage).
        val fallback = system.lookup(hostname)
        put(key, fallback, MIN_TTL_S)
        return fallback
    }

    /** Résultat du bouton « Tester » : résolution DIRECTE par le serveur choisi (sans cache), puis repli éventuel. */
    data class Probe(val host: String, val addresses: List<InetAddress>, val millis: Long, val viaSystem: Boolean, val fellBack: Boolean, val error: String?)

    fun probe(host: String): Probe {
        val s = settings()
        val resolver = if (s.mode == DnsMode.SYSTEM) null else resolverOf(s)
        val start = System.nanoTime()
        fun ms() = (System.nanoTime() - start) / 1_000_000
        if (resolver == null) {
            return runCatching { system.lookup(host) }.fold(
                { Probe(host, it, ms(), viaSystem = true, fellBack = s.mode != DnsMode.SYSTEM, error = null) },
                { Probe(host, emptyList(), ms(), viaSystem = true, fellBack = s.mode != DnsMode.SYSTEM, error = it.message ?: it.javaClass.simpleName) },
            )
        }
        val direct = runCatching { withTimeout { resolver.resolve(host) } }
        val took = ms()
        direct.getOrNull()?.takeIf { it.addresses.isNotEmpty() }?.let { return Probe(host, it.addresses, took, viaSystem = false, fellBack = false, error = null) }
        val err = direct.exceptionOrNull()?.let { (it as? TimeoutException)?.let { "timeout" } ?: it.message ?: it.javaClass.simpleName } ?: "no address"
        val sys = runCatching { system.lookup(host) }.getOrDefault(emptyList())
        return Probe(host, sys, took, viaSystem = true, fellBack = true, error = err)
    }

    private fun put(key: String, addresses: List<InetAddress>, ttlSeconds: Long) {
        synchronized(cache) { cache[key] = Entry(addresses, now() + ttlSeconds * 1_000) }
    }

    private fun <T> withTimeout(block: () -> T): T {
        val f: Future<T> = executor.submit<T> { block() }
        return try {
            f.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            f.cancel(true); throw e
        } catch (e: java.util.concurrent.ExecutionException) {
            throw e.cause ?: e
        } catch (e: InterruptedException) {
            f.cancel(true); Thread.currentThread().interrupt(); throw UnknownHostException("interrompu")
        }
    }

    companion object {
        const val MIN_TTL_S = 60L
        const val MAX_TTL_S = 6 * 3600L
        /** DoH : la bibliothèque ne remonte pas le TTL. */
        const val DEFAULT_TTL_S = 300L
        private const val MAX_ENTRIES = 256

        fun isLocalName(host: String): Boolean {
            val h = host.lowercase().trimEnd('.')
            return h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || '.' !in h
        }

        /** Fils démons à la demande (aucun au repos), pour borner la durée d'une résolution. */
        private val sharedExecutor: ExecutorService by lazy {
            ThreadPoolExecutor(0, 32, 30, TimeUnit.SECONDS, SynchronousQueue()) { r -> Thread(r, "ultra-dns").apply { isDaemon = true } }
                .apply { rejectedExecutionHandler = ThreadPoolExecutor.CallerRunsPolicy() }
        }
    }
}
