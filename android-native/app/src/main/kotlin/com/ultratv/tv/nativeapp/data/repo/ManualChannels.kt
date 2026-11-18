package com.ultratv.tv.nativeapp.data.repo

import androidx.room.withTransaction
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.CategoryMaxRow
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ManualChannelEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chaînes ajoutées à la main à une source existante. La synchro vide et réécrit la table `channel` ET celle des
 * catégories : les chaînes vivent donc dans `manual_channel` (jamais touchée par la synchro) et [reinject] les recopie
 * dans `channel` après chaque écriture du catalogue du Direct (synchro complète, par catégorie, M3U) et à chaque
 * ajout / modification / suppression. Aucune URL n'est journalisée : elles peuvent contenir des identifiants.
 */
object ManualChannels {
    /** Préfixe des identifiants fournisseur des chaînes manuelles (identique sur toutes les plateformes). */
    const val PREFIX = "custom:"

    /** Identifiant de la catégorie dédiée « Mes chaînes », créée à la demande (jamais envoyée au serveur). */
    const val CUSTOM_CAT = "custom:cat"

    fun isManual(remoteId: String): Boolean = remoteId.startsWith(PREFIX)
    fun newRemoteId(): String = PREFIX + java.util.UUID.randomUUID().toString()

    /** Adresse de flux acceptée : un protocole de lecture courant et un hôte non vide. */
    fun isValidStreamUrl(raw: String): Boolean {
        val s = raw.trim()
        val scheme = s.substringBefore("://", "").lowercase()
        if (scheme !in setOf("http", "https", "rtmp", "rtsp", "udp", "rtp", "mms", "srt")) return false
        return s.substringAfter("://").substringBefore('/').substringAfterLast('@').isNotBlank()
    }

    /** Logo facultatif : vide ou adresse http(s). */
    fun isValidLogoUrl(raw: String): Boolean {
        val s = raw.trim()
        return s.isEmpty() || ((s.startsWith("http://", true) || s.startsWith("https://", true)) && s.substringAfter("://").isNotBlank())
    }

    /** Résultat de [plan] : catégorie à (re)créer éventuellement, et chaînes à écrire dans `channel`. */
    class Plan(val categoryToAdd: CategoryEntity?, val channels: List<ChannelEntity>)

    /**
     * Calcule les lignes à réinjecter. Pur (testé sans base).
     *  - [cats] : catégories LIVE actuelles de la source ; [maxima] : plus grands num / seq du fournisseur par catégorie ;
     *    [ids] : identifiant `channel.id` actuel de chaque chaîne manuelle déjà présente (conservé : guide, liens).
     *  - Catégorie choisie désactivée : chaîne sautée (elle revient à la réactivation). Catégorie disparue du serveur :
     *    la chaîne bascule dans « Mes chaînes ».
     *  - num / seq continuent après ceux du fournisseur, dans l'ordre de création.
     */
    fun plan(
        pid: Long,
        manual: List<ManualChannelEntity>,
        cats: List<CategoryEntity>,
        maxima: List<CategoryMaxRow>,
        ids: Map<String, Long>,
        defaultCatName: String = "Mes chaînes",
    ): Plan {
        if (manual.isEmpty()) return Plan(null, emptyList())
        val byRemote = cats.associateBy { it.remoteId }
        val num = HashMap<String?, Int>(); val seq = HashMap<String?, Int>()
        for (m in maxima) { num[m.categoryId] = m.maxNum; seq[m.categoryId] = m.maxSeq }
        var added: CategoryEntity? = null
        val rows = ArrayList<ChannelEntity>()
        for (m in manual.sortedWith(compareBy({ it.createdAt }, { it.remoteId }))) {
            var cat = m.categoryId
            val known = byRemote[cat]
            if (cat != CUSTOM_CAT && known == null) cat = CUSTOM_CAT
            if (cat == CUSTOM_CAT) {
                val own = byRemote[CUSTOM_CAT]
                if (own == null && added == null) added = CategoryEntity(providerId = pid, kind = "LIVE", remoteId = CUSTOM_CAT, name = m.categoryName?.takeIf { it.isNotBlank() } ?: defaultCatName)
                if (own != null && !own.enabled) continue
            } else if (known != null && !known.enabled) continue
            val n = (num[cat] ?: 0) + 1; num[cat] = n
            val s = (seq[cat] ?: 0) + 1; seq[cat] = s
            rows += ChannelEntity(
                id = ids[m.remoteId] ?: 0, providerId = pid, remoteId = m.remoteId, name = m.name, logo = m.logo?.takeIf { it.isNotBlank() },
                categoryId = cat, streamUrl = m.streamUrl, epgChannelId = m.epgChannelId?.takeIf { it.isNotBlank() },
                num = n, seq = s,
            ).copy(junk = false, isSeparator = false)   // un nom comme « 2 » ou « ### » reste affiché : c'est le choix de l'utilisateur
        }
        return Plan(added, rows)
    }

    /**
     * Recopie les chaînes manuelles de [pid] dans `channel` (et recrée « Mes chaînes » si besoin). À appeler APRÈS toute
     * réécriture du catalogue du Direct. Idempotent : rejouable sans effet de bord.
     */
    suspend fun reinject(db: UltraDb, pid: Long) {
        val manual = db.manualChannelDao().forProvider(pid)
        if (manual.isEmpty()) return
        db.withTransaction {
            val categoryDao = db.categoryDao(); val channelDao = db.channelDao()
            val cats = categoryDao.forProviderKind(pid, LiveOrder.KIND)
            val plan = plan(pid, manual, cats, channelDao.maxNumbersByCategory(pid), channelDao.idsByRemote(pid).filter { isManual(it.remoteId) }.associate { it.remoteId to it.id })
            plan.categoryToAdd?.let { categoryDao.upsertAll(listOf(it)) }
            if (plan.channels.isEmpty()) return@withTransaction
            val ranks = LiveOrder.ranks(if (plan.categoryToAdd != null) categoryDao.forProviderKind(pid, LiveOrder.KIND) else cats)
            channelDao.upsertAll(ranks.stamp(plan.channels))
        }
    }
}

/** Ajout / modification / suppression des chaînes manuelles d'une source (écran Paramètres › Catégories). */
@Singleton
class ManualChannelRepository @Inject constructor(private val db: UltraDb) {
    fun observe(pid: Long): Flow<List<ManualChannelEntity>> = db.manualChannelDao().observeForProvider(pid)

    suspend fun get(pid: Long, remoteId: String): ManualChannelEntity? = db.manualChannelDao().byRemoteId(pid, remoteId)

    /** Catégories du Direct de la source (identifiant, libellé), dans l'ordre d'affichage, hors « Mes chaînes » (proposée à part). */
    suspend fun categoryChoices(pid: Long): List<Pair<String, String>> =
        LiveOrder.displayOrder(db.categoryDao().forProviderKind(pid, LiveOrder.KIND)).filter { it.remoteId != ManualChannels.CUSTOM_CAT }.map { it.remoteId to it.name }

    /** Crée la chaîne et renvoie son identifiant « custom:<uuid> ». [categoryName] : libellé de « Mes chaînes » si c'est la catégorie choisie. */
    suspend fun add(pid: Long, name: String, url: String, logo: String?, epgId: String?, categoryId: String, categoryName: String?): String {
        val rid = ManualChannels.newRemoteId()
        db.withTransaction {
            db.manualChannelDao().upsert(
                ManualChannelEntity(pid, rid, name.trim(), url.trim(), logo?.trim()?.ifEmpty { null }, epgId?.trim()?.ifEmpty { null }, categoryId, categoryName, System.currentTimeMillis()),
            )
            ManualChannels.reinject(db, pid)
        }
        return rid
    }

    /** Modifie la chaîne (même identifiant : favoris et historique restent attachés). */
    suspend fun update(pid: Long, remoteId: String, name: String, url: String, logo: String?, epgId: String?, categoryId: String, categoryName: String?) {
        db.withTransaction {
            val old = db.manualChannelDao().byRemoteId(pid, remoteId) ?: return@withTransaction
            db.manualChannelDao().upsert(
                old.copy(name = name.trim(), streamUrl = url.trim(), logo = logo?.trim()?.ifEmpty { null }, epgChannelId = epgId?.trim()?.ifEmpty { null }, categoryId = categoryId, categoryName = categoryName),
            )
            // La réinjection REMPLACE la ligne de `channel` en gardant son identifiant (guide) : catégorie et numéros recalculés.
            ManualChannels.reinject(db, pid)
        }
    }

    suspend fun delete(pid: Long, remoteId: String) {
        db.withTransaction {
            db.manualChannelDao().delete(pid, remoteId)
            db.channelDao().deleteByRemoteIds(pid, listOf(remoteId))
        }
    }
}
