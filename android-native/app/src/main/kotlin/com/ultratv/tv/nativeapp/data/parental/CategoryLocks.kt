package com.ultratv.tv.nativeapp.data.parental

import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.prefs.LockedChannelsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verrou parental PAR CATÉGORIE (Gérer les catégories › cadenas) : règles pures, réutilisables par les fiches film / série
 * (catégorie verrouillée = code exigé) comme par le Direct.
 */
object CategoryLocks {
    /** La catégorie [categoryId] fait-elle partie des catégories verrouillées [lockedCategoryIds] (même source, même type) ? */
    fun isCategoryLocked(categoryId: String?, lockedCategoryIds: Set<String>): Boolean =
        !categoryId.isNullOrEmpty() && categoryId in lockedCategoryIds

    /** Clés `source:identifiant` des chaînes qui exigent le code : verrouillées une à une, ou rangées dans une catégorie verrouillée. */
    fun effectiveKeys(channelKeys: Set<String>, categoryLockedKeys: List<String>): Set<String> =
        if (categoryLockedKeys.isEmpty()) channelKeys else channelKeys + categoryLockedKeys
}

/**
 * Chaînes qui exigent le code parental, catégories verrouillées comprises : listes du Direct, Guide, mode simple, Sport
 * (cadenas affiché, aperçu jamais lancé, code demandé avant la lecture).
 */
@Singleton
class ParentalLocks @Inject constructor(store: LockedChannelsStore, channelDao: ChannelDao) {
    val effective: Flow<Set<String>> =
        combine(store.locked, channelDao.observeCategoryLockedKeys()) { keys, inCats -> CategoryLocks.effectiveKeys(keys, inCats) }.distinctUntilChanged()
}

/**
 * Code déjà saisi par l'écran de départ (liste du Direct, Favoris, mode simple, Sport) juste avant d'ouvrir le lecteur :
 * le lecteur ne le redemande pas. Valable une fois et 60 s au plus, pour cette chaîne seulement.
 */
object ParentalUnlocks {
    private const val VALID_NS = 60_000_000_000L
    private val granted = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun grant(c: ChannelEntity) { granted[ParentalGate.keyOf(c)] = System.nanoTime() }

    /** Vrai (et consommé) si le code vient d'être saisi pour [c]. */
    fun consume(c: ChannelEntity): Boolean {
        val at = granted.remove(ParentalGate.keyOf(c)) ?: return false
        return System.nanoTime() - at <= VALID_NS
    }

    fun clear() = granted.clear()
}
