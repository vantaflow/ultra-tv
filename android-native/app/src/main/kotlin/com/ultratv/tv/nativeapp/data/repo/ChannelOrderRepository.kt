package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ChannelOrderDao
import com.ultratv.tv.nativeapp.data.db.ChannelOrderEntity
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ordre personnel des chaînes du PROFIL COURANT (catégories du fournisseur et favoris) : lectures utilisées par l'écran
 * Direct, le tiroir du lecteur et la file de zapping — la file suit TOUJOURS l'ordre affiché — et écritures du
 * déplacement. Une liste sans ordre personnel garde les requêtes naturelles d'origine (servies par l'index).
 * L'ordre reste LOCAL : il n'est pas envoyé au cloud (seuls les favoris eux-mêmes le sont).
 */
@Singleton
class ChannelOrderRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val orderDao: ChannelOrderDao,
    private val profiles: ProfileRepository,
) {
    /** Listes (identifiant de catégorie ou [ChannelOrder.FAVORITES_SCOPE]) qui ont un ordre personnel, pour la source donnée. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeScopes(pid: Long): Flow<Set<String>> =
        profiles.currentId.flatMapLatest { p -> orderDao.observeScopes(p, pid) }.map { it.toSet() }.distinctUntilChanged()

    /** Favoris du profil, dans l'ordre affiché. */
    suspend fun favorites(pid: Long): List<ChannelEntity> {
        val p = profiles.currentIdNow
        return if (orderDao.has(p, pid, ChannelOrder.FAVORITES_SCOPE)) channelDao.favoritesListOrdered(pid, p) else channelDao.favoritesList(pid, p)
    }

    /**
     * Fenêtre de [limit] chaînes de la catégorie [cat] dans l'ordre affiché, centrée sur [center] (début de liste si null) :
     * jamais toute la catégorie en mémoire.
     */
    suspend fun categoryWindow(pid: Long, cat: String, center: ChannelEntity?, limit: Int): List<ChannelEntity> {
        val p = profiles.currentIdNow
        val custom = orderDao.has(p, pid, cat)
        val rank = when {
            center == null -> 0
            custom -> channelDao.rankCategoryOrdered(pid, cat, p, orderDao.positionOf(p, pid, cat, center.remoteId) ?: ChannelOrder.NO_POSITION, center.num, center.id)
            else -> channelDao.rankCategory(pid, cat, center.num, center.id)
        }
        val offset = if (center == null) 0 else (rank - limit / 2).coerceAtLeast(0)
        return if (custom) channelDao.windowCategoryOrdered(pid, cat, p, limit, offset) else channelDao.windowCategory(pid, cat, limit, offset)
    }

    /** Identifiants fournisseur de la liste dans l'ordre affiché (point de départ d'un déplacement). */
    suspend fun orderedKeys(pid: Long, scope: String): List<String> {
        val p = profiles.currentIdNow
        return if (scope == ChannelOrder.FAVORITES_SCOPE) channelDao.orderedKeysForFavorites(pid, p) else channelDao.orderedKeysForCategory(pid, scope, p)
    }

    /** Places actuelles de la liste (vide = ordre naturel) : sert à annuler un déplacement. */
    suspend fun positions(pid: Long, scope: String): Map<String, Int> =
        orderDao.positions(profiles.currentIdNow, pid, scope).associate { it.remoteId to it.position }

    /** Enregistre l'ordre [keys] de la liste ; seules les places qui changent sont réécrites. */
    suspend fun save(pid: Long, scope: String, keys: List<String>) {
        val p = profiles.currentIdNow
        val old = orderDao.positions(p, pid, scope).associate { it.remoteId to it.position }
        val rows = ChannelOrder.changed(old, ChannelOrder.positionsOf(keys)).map { (k, pos) -> ChannelOrderEntity(p, pid, scope, k, pos) }
        if (rows.isNotEmpty()) orderDao.upsertAll(rows)
    }

    /** Remet la liste telle qu'elle était avant un déplacement annulé ([before] vide = ordre naturel). */
    suspend fun restore(pid: Long, scope: String, before: Map<String, Int>) {
        val p = profiles.currentIdNow
        orderDao.clear(p, pid, scope)
        if (before.isNotEmpty()) orderDao.upsertAll(before.map { (k, pos) -> ChannelOrderEntity(p, pid, scope, k, pos) })
    }

    /** « Réinitialiser l'ordre » : la liste retrouve l'ordre naturel. */
    suspend fun reset(pid: Long, scope: String) = orderDao.clear(profiles.currentIdNow, pid, scope)
}
