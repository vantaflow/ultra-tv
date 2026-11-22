package com.ultratv.tv.nativeapp.ui.account

/**
 * Adresse du serveur affichée sur l'écran « Abonnement » : celle SAISIE par l'utilisateur (adresse de la source), jamais
 * `server_info.url` renvoyée par le panneau (souvent le serveur amont d'un revendeur, que l'utilisateur n'a pas à voir).
 * Ni identifiant (`user:mot@`), ni paramètre (`?mac=…`), ni barre finale.
 */
object AccountServer {
    fun displayed(baseUrl: String?): String? {
        val raw = baseUrl?.trim()?.substringBefore('?')?.substringBefore('#').orEmpty()
        if (raw.isEmpty()) return null
        val scheme = if ("://" in raw) raw.substringBefore("://") else "http"
        val rest = if ("://" in raw) raw.substringAfter("://") else raw
        val authority = rest.substringBefore('/')
        val path = if ('/' in rest) "/" + rest.substringAfter('/') else ""
        val host = authority.substringAfterLast('@')
        if (host.isEmpty()) return null
        return "$scheme://$host${path.trimEnd('/')}"
    }
}
