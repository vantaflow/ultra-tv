package com.ultratv.tv.nativeapp.data.stalker

import com.ultratv.tv.nativeapp.LogSanitizer
import com.ultratv.tv.nativeapp.data.net.HttpStatusException

/**
 * Journal de la synchro Stalker (films, séries) : étape, type de contenu, code HTTP et classe d'erreur SEULEMENT.
 * Jamais le message de l'exception (il peut contenir l'hôte ou la MAC), ni l'adresse, ni le nom de la catégorie.
 * Au plus [MAX_LINES] lignes détaillées par synchro, puis une ligne de bilan.
 */
object StalkerSyncLog {
    const val MAX_LINES = 20

    /** Étapes journalisées. */
    const val STEP_CATEGORIES = "categories"
    const val STEP_LIST = "list"
    const val STEP_PAGE = "page"
    const val STEP_PART = "part"

    fun httpOf(t: Throwable?): Int? = (t as? HttpStatusException)?.code

    /** Classe d'erreur lisible : délai, coupure, refus, réponse vide… (jamais le message). */
    fun errorClass(t: Throwable?): String = when (t) {
        null -> "unknown"
        is java.net.SocketTimeoutException -> "timeout"
        is java.net.UnknownHostException -> "dns"
        is java.net.ConnectException -> "connect"
        is StalkerLinkException -> "empty_${t.reason}"
        is HttpStatusException -> if (t.code == 401 || t.code == 403) "denied" else "http"
        is java.io.EOFException -> "truncated"
        is java.io.IOException -> "io"
        else -> t::class.java.simpleName.ifBlank { "unknown" }
    }

    /** Une catégorie (ou une partie) sautée : [type] = vod | series, [step] = categories | list | page | part. */
    fun skipped(step: String, type: String, t: Throwable?): String =
        LogSanitizer.sanitize("stalker sync skip step=$step type=$type http=${httpOf(t) ?: "-"} error=${errorClass(t)}")

    /** Nouvelle tentative d'une page (délai, 5xx, 429). */
    fun retry(type: String, attempt: Int, t: Throwable?): String =
        LogSanitizer.sanitize("stalker sync retry step=$STEP_PAGE type=$type attempt=$attempt http=${httpOf(t) ?: "-"} error=${errorClass(t)}")

    /** Bilan d'une partie : catégories lues, sautées, éléments écrits. */
    fun summary(type: String, read: Int, skipped: Int, items: Int): String =
        LogSanitizer.sanitize("stalker sync done type=$type categories=$read skipped=$skipped items=$items")

    /** Module « séries » (type=series) détecté ou absent. */
    fun seriesModule(present: Boolean, categories: Int): String =
        LogSanitizer.sanitize("stalker sync series_module=${if (present) "yes" else "no"} categories=$categories")
}
