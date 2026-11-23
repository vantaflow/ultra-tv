package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ChannelGroupDao
import com.ultratv.tv.nativeapp.data.db.ChannelGroupEntity
import com.ultratv.tv.nativeapp.data.db.ChannelGroupRow
import com.ultratv.tv.nativeapp.data.profile.KidsRules
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Règles PURES des groupes de chaînes personnels (mêmes bornes que la version bureau, web/src/lib/channelGroups.ts).
 * Un groupe apparaît dans les listes de catégories sous l'identifiant `__grp:<id>` (jamais celui d'une catégorie du
 * fournisseur).
 */
object ChannelGroups {
    const val PREFIX = "__grp:"
    const val MAX_GROUPS = 50
    const val MAX_NAME = 40
    const val MAX_SIZE = 2000

    fun scope(id: Long) = "$PREFIX$id"
    fun isScope(scope: String?): Boolean = scope != null && scope.startsWith(PREFIX) && scope.length > PREFIX.length
    /** Identifiant du groupe d'une portée `__grp:<id>` ; null pour toute autre liste. */
    fun idOf(scope: String?): Long? = if (isScope(scope)) scope!!.substring(PREFIX.length).toLongOrNull() else null

    /** Nom nettoyé : espaces resserrés, 40 caractères au plus ; vide = refusé. */
    fun cleanName(n: String): String = n.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    /**
     * Catégories dont les chaînes sont exclues des groupes : celles masquées pour un profil Enfants (adultes comprises).
     * Un profil ordinaire voit toutes les chaînes qu'il a rangées, même celles d'une catégorie qu'il a masquée.
     * [hidden] : clés `LIVE:<source>:<catégorie>` du profil courant.
     */
    fun excludedCategories(kids: Boolean, hidden: Set<String>, providerId: Long): List<String> {
        if (!kids) return emptyList()
        val prefix = "LIVE:$providerId:"
        return hidden.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.sorted()
    }
}

/**
 * Groupes de chaînes du PROFIL COURANT : lectures (liste des groupes, chaînes d'un groupe) et écritures. Locaux à
 * l'appareil (pas de synchro cloud : le Worker ne garde que les champs de réglages qu'il connaît).
 */
@Singleton
class ChannelGroupRepository @Inject constructor(
    private val dao: ChannelGroupDao,
    private val profiles: ProfileRepository,
) {
    private val p get() = profiles.currentIdNow

    /** Catégories exclues pour le profil courant (voir [ChannelGroups.excludedCategories]). */
    fun excluded(hidden: Set<String>, providerId: Long): List<String> =
        ChannelGroups.excludedCategories(KidsRules.forcesParentalControl(profiles.current.value), hidden, providerId)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(pid: Long, excluded: List<String>): Flow<List<ChannelGroupRow>> =
        profiles.currentId.flatMapLatest { prof -> dao.observeGroups(prof, pid, if (excluded.isEmpty()) 0 else 1, excluded) }.distinctUntilChanged()

    suspend fun groups(pid: Long): List<ChannelGroupEntity> = dao.groups(p, pid)

    /** Crée un groupe (avec, facultatif, une première chaîne) ; son identifiant, ou null si refusé (nom vide, 50 groupes). */
    suspend fun create(pid: Long, name: String, firstRemoteId: String? = null): Long? {
        val n = ChannelGroups.cleanName(name)
        val prof = p
        if (n.isEmpty() || dao.countGroups(prof, pid) >= ChannelGroups.MAX_GROUPS) return null
        val id = dao.insertGroup(ChannelGroupEntity(profileId = prof, providerId = pid, name = n, position = dao.maxGroupPosition(prof, pid) + 1, createdAt = System.currentTimeMillis()))
        if (firstRemoteId != null) dao.addMember(id, firstRemoteId)
        return id
    }

    suspend fun rename(id: Long, name: String): Boolean {
        val n = ChannelGroups.cleanName(name)
        return n.isNotEmpty() && dao.rename(p, id, n) > 0
    }

    suspend fun delete(id: Long) = dao.deleteGroup(p, id)

    /** Ajoute la chaîne à la fin du groupe ; faux si elle y est déjà, si le groupe n'est pas au profil ou s'il est plein. */
    suspend fun add(id: Long, remoteId: String): Boolean {
        if (dao.group(p, id) == null || dao.memberCount(id) >= ChannelGroups.MAX_SIZE) return false
        return dao.addMember(id, remoteId)
    }

    suspend fun remove(id: Long, remoteId: String): Boolean = dao.group(p, id) != null && dao.removeMember(id, remoteId) > 0

    /** Groupes (du profil courant) qui contiennent la chaîne. */
    suspend fun groupsOf(pid: Long, remoteId: String): Set<Long> = dao.groupsOf(p, pid, remoteId).toSet()

    /** Ordre du groupe (identifiants fournisseur). */
    suspend fun keys(id: Long): List<String> = if (dao.group(p, id) == null) emptyList() else dao.keys(id)

    suspend fun setOrder(id: Long, keys: List<String>) { if (dao.group(p, id) != null) dao.setOrder(id, keys) }

    fun paged(pid: Long, id: Long, useLang: Int, langs: List<String>, excluded: List<String>) =
        dao.paged(pid, id, useLang, langs, if (excluded.isEmpty()) 0 else 1, excluded)

    fun pagedWithEpg(pid: Long, id: Long, from: Long, to: Long, excluded: List<String>) =
        dao.pagedWithEpg(pid, id, from, to, if (excluded.isEmpty()) 0 else 1, excluded)

    /** Chaînes du groupe dans son ordre (file de zapping, tiroir du lecteur). Vide si le groupe n'est pas au profil courant. */
    suspend fun channels(pid: Long, id: Long, excluded: List<String>, limit: Int = ChannelGroups.MAX_SIZE): List<ChannelEntity> =
        if (dao.group(p, id) == null) emptyList() else dao.list(pid, id, if (excluded.isEmpty()) 0 else 1, excluded, limit)
}
