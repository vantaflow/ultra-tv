package com.ultratv.tv.nativeapp.data.config

import java.security.MessageDigest

/**
 * Cadence adaptative de la synchro cloud (quota Worker : 100 000 requêtes par jour).
 *
 * 2 min pendant [burstMs] (10 min) après un changement — fait sur l'appareil ou reçu d'un autre —, puis [idleMs]
 * (10 min) tant que rien ne bouge ; le premier changement ramène à 2 min. Au retour au premier plan, une synchro
 * immédiate, au plus une par [foregroundMinMs] (1 min). Pure (l'heure est passée en paramètre), donc testée.
 */
class SyncCadence(
    private val activeMs: Long = 2 * 60_000L,
    private val idleMs: Long = 10 * 60_000L,
    private val burstMs: Long = 10 * 60_000L,
    private val foregroundMinMs: Long = 60_000L,
    start: Long = System.currentTimeMillis(),
) {
    /** Dernier changement connu ; le démarrage compte comme un changement (cadence rapide les 10 premières minutes). */
    @Volatile private var lastChange = start

    fun noteChange(now: Long = System.currentTimeMillis()) { lastChange = now }

    /** Délai à respecter entre deux synchros périodiques à l'instant [now]. */
    fun intervalMs(now: Long): Long = if (now - lastChange < burstMs) activeMs else idleMs

    /** Une synchro périodique est-elle due ? [lastRun] : instant de la dernière synchro (0 = jamais). */
    fun periodicDue(now: Long, lastRun: Long): Boolean = now - lastRun >= intervalMs(now)

    /** Le retour au premier plan déclenche-t-il une synchro ? Au plus une par minute. */
    fun foregroundDue(now: Long, lastRun: Long): Boolean = now - lastRun >= foregroundMinMs
}

/**
 * Empreinte en mémoire de ce qui a été envoyé au cloud : un contenu inchangé depuis le dernier envoi réussi n'est pas
 * renvoyé. Les points d'échange (profils, favoris et reprises) renvoient aussi ce que les autres appareils ont changé :
 * un contenu inchangé est donc échangé quand même au plus toutes les [refreshMs] (10 min) pour recevoir ces changements.
 */
class SyncPushGate(private val refreshMs: Long = 10 * 60_000L) {
    private class Sent(val print: String, val at: Long)
    private val sent = HashMap<String, Sent>()

    /** Faut-il échanger [key] maintenant ? Oui si le contenu [print] a changé, ou si le dernier échange est trop ancien. */
    @Synchronized
    fun shouldExchange(key: String, print: String, now: Long): Boolean {
        val s = sent[key] ?: return true
        return s.print != print || now - s.at >= refreshMs
    }

    /** À appeler après un échange réussi. */
    @Synchronized
    fun markSent(key: String, print: String, now: Long) { sent[key] = Sent(print, now) }

    /** Oublie tout (dé-appairage, révocation, changement de Worker). */
    @Synchronized
    fun reset() { sent.clear() }

    companion object {
        /** Empreinte courte (SHA-256, 16 premiers octets en hexadécimal) d'un contenu envoyé. */
        fun print(content: String): String =
            MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }
    }
}
