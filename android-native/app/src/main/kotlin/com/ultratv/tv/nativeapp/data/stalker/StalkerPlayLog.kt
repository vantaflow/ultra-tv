package com.ultratv.tv.nativeapp.data.stalker

import com.ultratv.tv.nativeapp.LogSanitizer
import com.ultratv.tv.nativeapp.data.net.HttpStatusException

/**
 * Lignes de journal pour diagnostiquer l'ouverture d'un flux Stalker : code HTTP et type d'erreur SEULEMENT. Jamais le
 * message de l'exception (il peut contenir l'hôte ou la MAC), ni l'adresse. Passées par [LogSanitizer] par sécurité.
 */
object StalkerPlayLog {
    /** Codes par lesquels un portail dit « cette MAC n'a pas le droit de lire ici » (403, 458 = MAC utilisée ailleurs). */
    fun isPortalRefusal(code: Int?) = code == 401 || code == 403 || code == 458

    fun httpOf(t: Throwable): Int? = (t as? HttpStatusException)?.code

    /** Échec de create_link (ou de la session) avant l'ouverture du flux. */
    fun openFailed(t: Throwable): String =
        LogSanitizer.sanitize("stalker live open failed step=create_link http=${httpOf(t) ?: "-"} type=${t::class.java.simpleName}")

    /** `itv/set_played` en échec (ignoré par la lecture). */
    fun setPlayedFailed(t: Throwable): String =
        LogSanitizer.sanitize("stalker set_played failed http=${httpOf(t) ?: "-"} type=${t::class.java.simpleName}")

    /** Reconnexion du direct : numéro, dernier code HTTP connu et durée de lecture avant la coupure. */
    fun reconnect(n: Int, http: Int?, playedMs: Long): String =
        LogSanitizer.sanitize("stalker live reconnect n=$n http=${http ?: "-"} played=${playedMs / 1000}s")

    /** Erreur du lecteur sur un flux Stalker déjà ouvert. */
    fun playerError(kind: String, http: Int?, engine: String): String =
        LogSanitizer.sanitize("stalker live play error step=player kind=$kind http=${http ?: "-"} engine=$engine")
}
