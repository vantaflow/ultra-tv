package com.ultratv.tv.nativeapp.data.trakt

import org.json.JSONObject

/**
 * Service de suivi choisi par le client (Trakt, Simkl ou aucun). Le Worker aiguille lui-même les routes
 * `/api/device/trakt/...` (ou `/api/device/tracking/...`) vers le service actif : le reste de l'appli (historique, watchlist,
 * notes, scrobble) ne connaît qu'une seule bibliothèque « de suivi », au format de Trakt.
 */
object TrackingService {
    const val TRAKT = "trakt"
    const val SIMKL = "simkl"
    const val NONE = "none"

    /** Service dont la bibliothèque est affichée (celui de la dernière réponse du Worker) ; sert aux libellés. */
    @Volatile var active: String = TRAKT

    fun displayName(service: String?): String = if (service == SIMKL) "Simkl" else "Trakt"
}

/**
 * Les libellés existants nomment Trakt : avec Simkl comme service actif, le nom est remplacé (même phrase, dans toutes
 * les langues). Sans effet pour Trakt.
 */
fun String.forTracker(): String = if (TrackingService.active == TrackingService.SIMKL) replace("Trakt", "Simkl") else this

/**
 * État du suivi renvoyé par `GET /api/device/tracking` : [service] = service actif (null si aucun), [choice] = choix
 * enregistré (trakt / simkl / none), [available] = services proposés (Simkl absent tant que le Worker n'est pas
 * configuré), [linked] = services reliés depuis la page du compte.
 */
data class TrackingStatus(val service: String?, val choice: String, val available: List<String>, val linkedTrakt: Boolean, val linkedSimkl: Boolean) {
    fun isLinked(service: String) = when (service) { TrackingService.TRAKT -> linkedTrakt; TrackingService.SIMKL -> linkedSimkl; else -> true }

    companion object {
        /** Jamais d'exception : réponse illisible = null. */
        fun parse(body: String?): TrackingStatus? = try {
            val o = JSONObject(body.orEmpty())
            val avail = o.optJSONArray("available")
            val list = buildList { if (avail != null) for (i in 0 until avail.length()) avail.optString(i, "").takeIf { it == TrackingService.TRAKT || it == TrackingService.SIMKL }?.let(::add) }
            val linked = o.optJSONObject("linked")
            TrackingStatus(
                service = o.optString("service", "").takeIf { it.isNotEmpty() && !o.isNull("service") },
                choice = o.optString("choice", TrackingService.NONE).takeIf { it == TrackingService.TRAKT || it == TrackingService.SIMKL } ?: TrackingService.NONE,
                available = list,
                linkedTrakt = linked?.optBoolean("trakt", false) ?: false,
                linkedSimkl = linked?.optBoolean("simkl", false) ?: false,
            )
        } catch (_: Exception) { null }
    }
}
