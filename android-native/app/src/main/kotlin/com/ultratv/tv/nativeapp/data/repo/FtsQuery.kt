package com.ultratv.tv.nativeapp.data.repo

/**
 * Construit une requête FTS4 sûre depuis une saisie libre : on ne garde que les lettres et chiffres
 * (Unicode : accents, arabe, cyrillique…), chaque mot devient un préfixe (« spor » trouve « Sport 1 »).
 * Retourne null s'il ne reste rien d'interrogeable. Aucun opérateur FTS (OR, NEAR, guillemets, `-`,
 * `*` isolé…) ne peut passer : la saisie ne peut pas casser la requête.
 */
object FtsQuery {
    fun of(raw: String): String? {
        val tokens = raw.trim().split(Regex("\\s+"))
            .map { t -> t.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
            .take(6)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}
