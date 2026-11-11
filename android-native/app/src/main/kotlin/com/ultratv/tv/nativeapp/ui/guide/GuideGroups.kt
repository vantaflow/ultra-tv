package com.ultratv.tv.nativeapp.ui.guide

import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES
import com.ultratv.tv.nativeapp.ui.live.DirectCategory

/**
 * Groupe de chaînes affiché par le Guide TV. Mémorisé : [saved] (choix du profil). Sinon le groupe ouvert dans le Direct
 * ([directLast]), sinon Tout. Un groupe mémorisé qui n'existe plus (catégorie supprimée ou masquée) retombe sur le même défaut.
 * Favoris et Tout sont toujours proposés, même vides.
 */
fun resolveGuideGroup(saved: String?, directLast: String?, groups: List<DirectCategory>): String {
    fun ok(id: String?) = id != null && groups.any { it.id == id }
    return when {
        ok(saved) -> saved!!
        ok(directLast) -> directLast!!
        else -> CATEGORY_ALL
    }
}

/**
 * Choix du sélecteur : (identifiant, libellé), dans l'ordre du Direct. Le libellé porte le compteur ; il sert aussi de clé
 * à la boîte de choix et doit donc être unique (deux catégories homonymes sont départagées par leur identifiant).
 */
fun guideGroupOptions(groups: List<DirectCategory>, nameOf: (DirectCategory) -> String): List<Pair<String, String>> {
    val seen = HashSet<String>()
    return groups.map { g ->
        val base = "${nameOf(g)} (${g.count})"
        val label = if (seen.add(base)) base else "$base · ${g.id}".also { seen.add(it) }
        g.id to label
    }
}

/** Libellé court du groupe (pastille de l'en-tête), sans compteur. */
fun guideGroupName(groups: List<DirectCategory>, id: String, nameOf: (DirectCategory) -> String): String =
    groups.firstOrNull { it.id == id }?.let(nameOf) ?: nameOf(DirectCategory(CATEGORY_ALL, null, 0))

/** Favoris et Tout ne sont pas des catégories du fournisseur. */
fun isProviderGroup(id: String) = id != CATEGORY_ALL && id != CATEGORY_FAVORITES
