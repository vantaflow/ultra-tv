package com.ultratv.tv.nativeapp.data.parental

import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Ce que la porte doit savoir de la configuration parentale. Séparé pour être testé sur la JVM. */
interface LockSource {
    /** Clés « fournisseur:identifiant » des chaînes verrouillées. */
    suspend fun lockedChannelKeys(): Set<String>
    /** Identifiants (fournisseur) des catégories LIVE verrouillées d'un fournisseur. */
    suspend fun lockedCategoryIds(providerId: Long): Set<String>
}

/** Un changement de chaîne en attente du code parental : [apply] bascule vraiment la lecture et rend l'URL à lancer. */
class PendingSwitch(val channel: ChannelEntity, val apply: () -> String?)

/**
 * Porte du code parental dans le lecteur : AUCUNE chaîne verrouillée (ou d'une catégorie verrouillée) ne se lance sans
 * code, quelle que soit la voie (zapping, chaîne précédente, numéro, liste, télécommande du téléphone). Un code accepté vaut
 * pour la session de lecture (jusqu'à [endSession], à la fermeture du lecteur) pour CETTE chaîne seulement.
 * Un refus ou une annulation laisse la chaîne actuelle : rien n'a été modifié avant l'accord.
 */
open class ParentalGate(private val source: LockSource) {
    private val unlocked = HashSet<String>()
    private val _pending = MutableStateFlow<PendingSwitch?>(null)
    val pending: StateFlow<PendingSwitch?> = _pending

    companion object {
        fun keyOf(c: ChannelEntity) = "${c.providerId}:${c.remoteId}"

        /** Logique pure : la chaîne exige-t-elle le code ? */
        fun isBlocked(c: ChannelEntity, lockedKeys: Set<String>, lockedCategories: Set<String>, unlocked: Set<String>): Boolean {
            val key = keyOf(c)
            if (key in unlocked) return false
            return key in lockedKeys || (c.categoryId != null && c.categoryId in lockedCategories)
        }
    }

    /**
     * Demande le passage à [target]. Libre : [apply] est exécuté tout de suite et son URL rendue. Verrouillée : le passage est mis en
     * attente (le lecteur affiche la fenêtre de code) et on rend null ; la lecture en cours n'est pas touchée.
     */
    suspend fun request(target: ChannelEntity, apply: () -> String?): String? {
        val blocked = isBlocked(target, source.lockedChannelKeys(), source.lockedCategoryIds(target.providerId), unlocked)
        if (!blocked) return apply()
        _pending.value = PendingSwitch(target, apply)
        return null
    }

    /** Code accepté : la chaîne est débloquée pour la session et le passage a lieu ; rend l'URL à lancer. */
    fun grant(): String? {
        val p = _pending.value ?: return null
        _pending.value = null
        unlocked.add(keyOf(p.channel))
        return p.apply()
    }

    /** Code refusé ou fenêtre annulée : on reste sur la chaîne actuelle. */
    fun cancel() { _pending.value = null }

    /** Fermeture du lecteur : les chaînes débloquées se reverrouillent. */
    fun endSession() { unlocked.clear(); _pending.value = null }
}

@Singleton
class ParentalGuard @Inject constructor(store: LockedChannelsStore, categories: CategoryDao) : ParentalGate(object : LockSource {
    override suspend fun lockedChannelKeys(): Set<String> = store.locked.first()
    override suspend fun lockedCategoryIds(providerId: Long): Set<String> =
        categories.forProviderKind(providerId, "LIVE").filter { it.locked }.map { it.remoteId }.toSet()
})
