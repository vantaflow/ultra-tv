package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.profile.ProfileHiddenCategoryEntity
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catégories PAR PROFIL. « Gérer les catégories » affiche / masque pour le profil courant ; le téléchargement (catégorie
 * activée pour la source) suit : une catégorie est téléchargée tant qu'au moins un profil l'affiche, et ne l'est plus
 * quand tous la masquent (box aussi légère qu'avant).
 */
@Singleton
class ProfileCategories @Inject constructor(
    private val db: UltraDb,
    private val manager: CategoryManager,
    private val profiles: ProfileRepository,
) {
    private val dao get() = db.profileDao()

    fun key(kind: String, pid: Long, remoteId: String) = "$kind:$pid:$remoteId"

    /** Affiche ou masque [ids] pour le profil courant ; [enabledNow] = catégories téléchargées actuellement. */
    suspend fun setVisible(pid: Long, kind: String, ids: List<String>, visible: Boolean, enabledNow: Set<String>) {
        if (ids.isEmpty()) return
        val me = profiles.currentIdNow
        val all = dao.observeAll().first().map { it.id }.ifEmpty { listOf(me) }
        val hiders = dao.allHidden().groupBy({ it.categoryKey }, { it.profileId })
        val enable = mutableListOf<String>(); val disable = mutableListOf<String>()
        for (id in ids) {
            val k = key(kind, pid, id)
            if (visible) {
                dao.unhide(me, k)
                // Catégorie jusque-là désactivée pour tous : on la télécharge, mais les AUTRES profils ne la voient pas d'un coup.
                if (id !in enabledNow) { enable += id; all.filter { it != me }.forEach { dao.hide(ProfileHiddenCategoryEntity(it, k)) } }
            } else {
                dao.hide(ProfileHiddenCategoryEntity(me, k))
                if (id in enabledNow && all.all { it == me || it in hiders[k].orEmpty() }) disable += id
            }
        }
        manager.setEnabled(pid, kind, enable, true)
        manager.setEnabled(pid, kind, disable, false)
    }

    /** Catégories masquées par profil pour la source [pid] : profileId → (type → identifiants du fournisseur). */
    suspend fun hiddenByProfile(pid: Long): Map<Long, Map<String, List<String>>> =
        dao.allHidden().mapNotNull { h -> parse(h.categoryKey, pid)?.let { (k, r) -> Triple(h.profileId, k, r) } }
            .groupBy { it.first }.mapValues { (_, l) -> l.groupBy({ it.second }, { it.third }) }

    /** Remplace les catégories masquées du profil [profileId] pour la source [pid] (reçues d'un autre appareil). */
    suspend fun replaceHidden(profileId: Long, pid: Long, byKind: Map<String, List<String>>) {
        val want = byKind.flatMap { (k, ids) -> ids.map { key(k, pid, it) } }.toSet()
        val have = dao.allHidden().filter { it.profileId == profileId && parse(it.categoryKey, pid) != null }.map { it.categoryKey }.toSet()
        (have - want).forEach { dao.unhide(profileId, it) }
        (want - have).forEach { dao.hide(ProfileHiddenCategoryEntity(profileId, it)) }
    }

    private fun parse(key: String, pid: Long): Pair<String, String>? {
        val parts = key.split(':', limit = 3)
        return if (parts.size == 3 && parts[1] == pid.toString()) parts[0] to parts[2] else null
    }
}
