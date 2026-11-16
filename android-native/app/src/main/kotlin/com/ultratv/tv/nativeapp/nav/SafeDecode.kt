package com.ultratv.tv.nativeapp.nav

/**
 * Décodage d'URL tolérant : un « % » qui n'est pas suivi de deux chiffres hexadécimaux (titre « 100% … », « %+p »)
 * faisait planter URLDecoder (IllegalArgumentException). En cas d'échec, le texte est gardé tel quel.
 */
object SafeDecode {
    fun url(s: String): String = try { java.net.URLDecoder.decode(s, "UTF-8") } catch (_: IllegalArgumentException) { s }
}
