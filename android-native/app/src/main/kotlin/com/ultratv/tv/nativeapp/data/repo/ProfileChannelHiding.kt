package com.ultratv.tv.nativeapp.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.ultratv.tv.nativeapp.data.db.ProfileHiddenChannelEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.prefs.HiddenChannel
import com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Logique pure des chaînes masquées PAR PROFIL (testable sans base). */
object ChannelHidingRules {
    /** Chaînes masquées par TOUS les profils (intersection) ; aucun profil = rien. Ce sont les seules supprimées de la base. */
    fun hiddenByAll(perProfile: Map<Long, Set<String>>): Set<String> {
        val it = perProfile.values.iterator()
        if (!it.hasNext()) return emptySet()
        val acc = HashSet(it.next())
        while (it.hasNext() && acc.isNotEmpty()) acc.retainAll(it.next())
        return acc
    }

    /** Chaînes masquées par ce seul profil (et pas par tous) : celles que le cloud transporte par NOM de profil. */
    fun partial(mine: Set<String>, all: Set<String>): Set<String> = mine - all

    /**
     * Réglages reçus du cloud → chaînes masquées de chaque profil local (par nom, en minuscules).
     *  - [remoteAll] : champ historique `hidden` = masquées par TOUS les profils (ancien appareil : sa liste globale) ;
     *  - [remotePartial] : champ `pHiddenCh` (nom du profil → ses masquées propres), `null` s'il n'a pas été transmis
     *    (appareil plus ancien) : les choix propres de chaque profil local sont alors CONSERVÉS.
     * Résultat d'un profil = (ses choix propres) ∪ [remoteAll].
     */
    fun mergeRemote(local: Map<String, Set<String>>, remoteAll: Set<String>, remotePartial: Map<String, Set<String>>?): Map<String, Set<String>> {
        val localAll = hiddenByAllNamed(local)
        return local.mapValues { (name, mine) ->
            val own = if (remotePartial == null) mine - localAll else remotePartial[name].orEmpty()
            own + remoteAll
        }
    }

    private fun hiddenByAllNamed(local: Map<String, Set<String>>): Set<String> = hiddenByAll(local.entries.mapIndexed { i, e -> i.toLong() to e.value }.toMap())
}

/**
 * Chaînes masquées PAR PROFIL (même modèle que [ProfileCategories]). Source de vérité : `profile_hidden_channel`.
 *
 *  - Une chaîne masquée par UN profil reste en base : les autres profils la voient, le profil courant la filtre à la
 *    lecture (projection `channel_hidden_now`, voir [com.ultratv.tv.nativeapp.data.db.Hid]).
 *  - Une chaîne masquée par TOUS les profils est supprimée de la base ET ignorée par la synchro (box légère) : c'est
 *    l'ancien [HiddenChannelsStore], qui garde donc le sens « masquées par tous » (la synchro n'a pas changé).
 *  - Numéros de chaîne : `channel.seq` reste GLOBAL ; il n'est resserré que pour les chaînes masquées par tous les
 *    profils. Un profil qui en masque une seule voit un trou dans la numérotation (un renumérotage par profil
 *    imposerait de réécrire `seq` à chaque changement de profil sur 180 000 lignes).
 */
@Singleton
class ProfileChannelHiding @Inject constructor(
    private val db: UltraDb,
    private val all: HiddenChannelsStore,
    private val sync: SyncCoordinator,
    @ApplicationContext private val ctx: Context,
) {
    private val dao get() = db.profileViewDao()
    private val channels get() = db.channelDao()

    /** Chaînes masquées par le profil [profileId] pour la source [pid] (nom et catégorie conservés pour l'écran Catégories). */
    fun observe(profileId: Long, pid: Long): Flow<List<HiddenChannel>> =
        dao.observeHiddenOf(profileId, pid).map { l -> l.map { HiddenChannel(it.remoteId, it.categoryId, it.name) } }

    suspend fun listOf(profileId: Long, pid: Long): List<HiddenChannel> =
        dao.hiddenOf(profileId, pid).map { HiddenChannel(it.remoteId, it.categoryId, it.name) }

    /** Masque [items] pour le profil [profileId] ; supprimées de la base seulement si tous les profils les masquent. */
    suspend fun hide(profileId: Long, pid: Long, items: List<HiddenChannel>, current: Long) {
        if (items.isEmpty()) return
        db.withTransaction {
            dao.hideAll(items.map { ProfileHiddenChannelEntity(profileId, pid, it.id, it.cat, it.name.take(120)) })
            if (profileId == current) refreshProjection(current)
        }
        reconcile(pid)
    }

    /** Réaffiche [ids] pour le profil [profileId] ; rechargées par la synchro si elles avaient été supprimées (masquées par tous). */
    suspend fun show(profileId: Long, pid: Long, ids: Collection<String>, current: Long) {
        if (ids.isEmpty()) return
        db.withTransaction {
            ids.chunked(500).forEach { dao.unhide(profileId, pid, it) }
            if (profileId == current) refreshProjection(current)
        }
        reconcile(pid)
    }

    /** Remplace TOUT ce que masque le profil [profileId] pour la source [pid] (réglages reçus d'un autre appareil). */
    suspend fun replaceFor(profileId: Long, pid: Long, items: List<HiddenChannel>, current: Long) {
        db.withTransaction {
            dao.clearHidden(profileId, pid)
            if (items.isNotEmpty()) dao.hideAll(items.map { ProfileHiddenChannelEntity(profileId, pid, it.id, it.cat, it.name.take(120)) })
            if (profileId == current) refreshProjection(current)
        }
    }

    /** Recopie les chaînes masquées du profil [profileId] dans la projection lue par les listes (seulement si elle diffère). */
    suspend fun refreshProjection(profileId: Long) {
        val want = dao.rowsOfProfile(profileId).toHashSet()
        val have = dao.nowRows().toHashSet()
        if (want == have) return
        db.withTransaction { dao.clearNow(); dao.projectNow(profileId) }
    }

    /**
     * Aligne la liste « masquées par TOUS » ([HiddenChannelsStore], lue par la synchro) sur les choix des profils :
     * les chaînes devenues unanimes sont supprimées de la base ; celles qui ne le sont plus voient leur catégorie rechargée.
     */
    suspend fun reconcile(pid: Long) {
        val profileIds = db.profileDao().observeAll().first().map { it.id }
        val rows = dao.hiddenOfProvider(pid)
        val byProfile = rows.groupBy { it.profileId }
        val unanimous = ChannelHidingRules.hiddenByAll(profileIds.associateWith { id -> byProfile[id].orEmpty().mapTo(HashSet()) { it.remoteId } })
        val stored = all.list(pid)
        val storedIds = stored.mapTo(HashSet()) { it.id }
        val added = unanimous - storedIds
        val removed = storedIds - unanimous
        if (added.isNotEmpty()) {
            val info = rows.associateBy { it.remoteId }
            all.add(pid, added.map { id -> HiddenChannel(id, info[id]?.categoryId, info[id]?.name.orEmpty()) })
            val cats = added.mapNotNull { info[it]?.categoryId }.distinct()
            db.withTransaction {
                added.chunked(500).forEach { channels.deleteByRemoteIds(pid, it) }
                // Numéros resserrés : masquer les chaînes 1 et 2 pour tout le monde fait de la 3 la chaîne 1.
                cats.forEach { cat -> ChannelSeq.compact(channels.seqsInCategory(pid, cat).map { it.id to it.seq }).forEach { (id, n) -> channels.setSeq(id, n) } }
            }
        }
        if (removed.isNotEmpty()) {
            val cats = stored.filter { it.id in removed }.mapNotNull { it.cat }.distinct()
            all.remove(pid, removed)
            if (cats.isNotEmpty()) sync.requestCategories(pid, "LIVE", cats)
        }
    }

    /** Un profil créé hérite des chaînes déjà supprimées de la base (masquées par tous) : rien à recharger, il peut les réafficher. */
    suspend fun inheritAll(profileId: Long) {
        for (pid in all.providerIds()) {
            val items = all.list(pid)
            if (items.isNotEmpty()) dao.hideAll(items.map { ProfileHiddenChannelEntity(profileId, pid, it.id, it.cat, it.name.take(120)) })
        }
    }

    /** Un profil supprimé peut rendre unanimes des chaînes masquées par tous les autres : liste « par tous » à réaligner. */
    suspend fun reconcileAll() { for (pid in all.providerIds()) reconcile(pid) }

    /**
     * Migration 23 → 24 : les chaînes masquées globales (préférences) sont attribuées à TOUS les profils existants, pour que
     * rien ne réapparaisse. Une seule fois.
     */
    suspend fun importLegacyOnce() {
        val sp = ctx.getSharedPreferences("hidden_channels_profiles", Context.MODE_PRIVATE)
        if (sp.getBoolean("imported", false)) return
        val profileIds = db.profileDao().observeAll().first().map { it.id }.ifEmpty { listOf(com.ultratv.tv.nativeapp.data.profile.DEFAULT_PROFILE_ID) }
        for (pid in all.providerIds()) {
            val items = all.list(pid)
            if (items.isEmpty()) continue
            for (id in profileIds) dao.hideAll(items.map { ProfileHiddenChannelEntity(id, pid, it.id, it.cat, it.name.take(120)) })
        }
        sp.edit().putBoolean("imported", true).apply()
    }
}
