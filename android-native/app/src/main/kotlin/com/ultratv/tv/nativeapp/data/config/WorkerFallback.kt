package com.ultratv.tv.nativeapp.data.config

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Repli automatique entre les deux adresses du Worker (domaine personnalisé / workers.dev), certains réseaux
 * bloquant *.workers.dev. Décision pure ici, interceptor OkHttp plus bas.
 */
object WorkerFallbackLogic {
    const val PRIMARY_HOST = "tv.weedy.dpdns.org"
    const val LEGACY_HOST = "ultratv-config.khalilbenaz.workers.dev"
    /** Site (tableau de bord, appairage par QR) : distinct de l'adresse de l'API, qui reste [PRIMARY_HOST]. */
    const val SITE_HOST = "ultratv.dpdns.org"

    /** Adresse du site à AFFICHER (et à mettre dans le QR) pour une adresse d'API : nos deux hôtes → le site ; un Worker
     *  auto-hébergé garde la sienne. */
    fun siteBase(workerBase: String): String {
        val host = runCatching { java.net.URI(workerBase.trim()).host?.lowercase() }.getOrNull()
        return if (host == PRIMARY_HOST || host == LEGACY_HOST) "https://$SITE_HOST" else workerBase
    }

    /** Cible du QR de la carte des Réglages › Sources : appairé → racine du site (tableau de bord) ; non appairé → page
     *  d'envoi d'une source sans compte (/envoyer), car la racine renvoie vers /login. */
    fun dashboardQrTarget(workerBase: String, paired: Boolean): String {
        val base = siteBase(workerBase).trimEnd('/')
        return if (paired) base else "$base/envoyer"
    }

    /** Dernier hôte qui a répondu (mémoire du processus) ; null = aucun encore. */
    @Volatile var lastWorking: String? = null

    /** Hôte de repli pour [host], ou null s'il ne s'agit pas d'un hôte du Worker. */
    fun alternate(host: String): String? = when (host.lowercase()) {
        PRIMARY_HOST -> LEGACY_HOST
        LEGACY_HOST -> PRIMARY_HOST
        else -> null
    }

    /** Hôte à utiliser d'emblée : le dernier qui a marché s'il fait partie des deux, sinon celui demandé. */
    fun preferredHost(requested: String, last: String?): String {
        val r = requested.lowercase()
        return if (alternate(r) != null && last != null && alternate(last.lowercase()) != null) last.lowercase() else r
    }

    /** Repli seulement pour une panne réseau (DNS, connexion, TLS, délai), jamais pour un code HTTP. */
    fun shouldFallback(e: Throwable): Boolean = e is IOException

    fun withHost(url: HttpUrl, host: String): HttpUrl = url.newBuilder().host(host).build()
}

class WorkerFallbackInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (WorkerFallbackLogic.alternate(req.url.host) == null) return chain.proceed(req)
        // Lectures de la liste blanche : secours Vercel en dernier recours. Écritures et autres routes : jamais.
        if (!BackupFailover.eligible(req.method, req.url.encodedPath)) return viaHosts(chain, req)
        val breaker = BackupFailover.breaker
        val triedFirst = breaker.preferBackup()
        if (triedFirst) tryBackup(chain, req)?.let { return it }
        var resp: Response? = null
        var err: IOException? = null
        try { resp = viaHosts(chain, req) } catch (e: IOException) { err = e }
        if (resp != null && !BackupFailover.isOutageStatus(resp.code)) { breaker.success(); return resp }
        breaker.failure()
        if (!triedFirst && !chain.call().isCanceled()) {
            tryBackup(chain, req)?.let { resp?.close(); return it }
        }
        return resp ?: throw err!!
    }

    /** Requête rejouée sur le secours ; null si le secours ne répond pas correctement (le jeton ne part que vers cet hôte fixe). */
    private fun tryBackup(chain: Interceptor.Chain, req: okhttp3.Request): Response? {
        val url = req.url.newBuilder().scheme("https").host(BackupFailover.HOST).port(443).build()
        val c = chain.withConnectTimeout(BackupFailover.TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .withReadTimeout(BackupFailover.TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        return try {
            val r = c.proceed(req.newBuilder().url(url).build())
            if (BackupFailover.accepted(r.code)) r else { r.close(); null }
        } catch (e: IOException) { null }
    }

    /** Repli domaine <-> workers.dev (panne réseau seulement), comme avant le secours. */
    private fun viaHosts(chain: Interceptor.Chain, req: okhttp3.Request): Response {
        val requested = req.url.host
        val first = WorkerFallbackLogic.preferredHost(requested, WorkerFallbackLogic.lastWorking)
        val firstReq = if (first == requested.lowercase()) req else req.newBuilder().url(WorkerFallbackLogic.withHost(req.url, first)).build()
        try {
            val resp = chain.proceed(firstReq)
            WorkerFallbackLogic.lastWorking = first
            return resp
        } catch (e: IOException) {
            if (!WorkerFallbackLogic.shouldFallback(e) || chain.call().isCanceled()) throw e
            val other = WorkerFallbackLogic.alternate(first) ?: throw e
            val retry = req.newBuilder().url(WorkerFallbackLogic.withHost(req.url, other)).build()
            val resp = try { chain.proceed(retry) } catch (e2: IOException) { e.addSuppressed(e2); throw e }
            WorkerFallbackLogic.lastWorking = other
            return resp
        }
    }
}
