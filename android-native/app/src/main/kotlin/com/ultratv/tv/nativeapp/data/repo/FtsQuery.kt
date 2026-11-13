package com.ultratv.tv.nativeapp.data.repo

/**
 * Construit une requête FTS4 sûre depuis une saisie libre : on ne garde que les lettres et chiffres
 * (Unicode : accents, arabe, cyrillique…), chaque mot devient un préfixe (« spor » trouve « Sport 1 »).
 * Retourne null s'il ne reste rien d'interrogeable. Aucun opérateur FTS (OR, NEAR, guillemets, `-`,
 * `*` isolé…) ne peut passer : la saisie ne peut pas casser la requête.
 */
object FtsQuery {
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}\\p{M}]+")

    fun of(raw: String): String? {
        // Découpe sur tout ce qui n'est ni lettre, ni chiffre, ni signe diacritique, comme le tokenizer « unicode61 »
        // de l'index : « Spider-Man » → spider* man*, « L'Odyssée » → l* odyssée*. Avant, la ponctuation était
        // supprimée et les morceaux recollés (« spiderman* », « lodyssée* ») : aucun titre ne correspondait.
        val tokens = raw.trim().split(SEPARATORS)
            .filter { it.isNotEmpty() }
            .take(6)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}
