package com.ultratv.tv.nativeapp.data.repo

import androidx.room.withTransaction
import com.ultratv.tv.nativeapp.data.db.ProfileCategoryOrderEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.profile.ProfileSwitchHook
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réglages de Direct PAR PROFIL qui sont stockés en PROJECTION du profil courant (pour garder des requêtes indexées sans
 * paramètre de profil) : chaînes masquées (`channel_hidden_now`) et ordre des catégories (`category.position`, derrière
 * `channel.catRank`). Recalculés au changement de profil.
 *
 * Coût d'un changement de profil : une recopie de la petite liste de chaînes masquées ; pour l'ordre, un UPDATE de
 * `category.position` limité aux lignes qui changent, puis — seulement pour les sources dont l'ordre diffère — une mise à
 * jour de `catRank` par catégorie (comme à l'enregistrement d'un ordre). Deux profils sans ordre personnalisé : aucune écriture.
 */
@Singleton
class ProfileSwitcher @Inject constructor(
    private val db: UltraDb,
    private val hiding: ProfileChannelHiding,
) : ProfileSwitchHook {
    override suspend fun onSwitch(profileId: Long) {
        hiding.refreshProjection(profileId)
        projectCategoryOrder(profileId)
    }

    override suspend fun onProfileCreated(profileId: Long) = hiding.inheritAll(profileId)

    override suspend fun onProfilesChanged() = hiding.reconcileAll()

    /** Réécrit l'ordre des catégories (et le rang des chaînes du Direct) d'après l'ordre du profil. */
    suspend fun projectCategoryOrder(profileId: Long) {
        val dao = db.profileViewDao()
        db.withTransaction {
            val changed = dao.providersWithLiveOrderChange(profileId)
            dao.projectPositions(profileId)
            for (pid in changed) LiveOrder.apply(db.channelDao(), pid, LiveOrder.ranks(db.categoryDao().forProviderKind(pid, LiveOrder.KIND)))
        }
    }

    /** Enregistre l'ordre complet [orderedIds] (1..n) d'un type pour le profil [profileId]. */
    suspend fun saveOrderFor(profileId: Long, pid: Long, kind: String, orderedIds: List<String>) {
        val dao = db.profileViewDao()
        dao.clearOrder(profileId, pid, kind)
        dao.putOrder(orderedIds.mapIndexed { i, id -> ProfileCategoryOrderEntity(profileId, pid, kind, id, i + 1) })
    }
}
