package com.ultratv.tv.nativeapp.ui.common

import com.ultratv.tv.nativeapp.LogSanitizer

/**
 * Tout texte dérivé d'une exception ou d'une réponse réseau passe ici AVANT d'être affiché (toast, bannière, statut) :
 * les identifiants sont masqués (LogSanitizer) et toute adresse (`schéma://…`) est remplacée par « … ». Une URL de flux
 * contient l'utilisateur et le mot de passe de l'abonnement : elle ne doit JAMAIS apparaître à l'écran.
 */
object UserText {
    private val ANY_URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://\S*""")

    fun safe(text: String, max: Int = 240): String =
        LogSanitizer.sanitize(text, max).replace(ANY_URL, "…").trim()
}
