package com.ultratv.tv.nativeapp.data.replay

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.profile.AdultHeuristic
import com.ultratv.tv.nativeapp.data.repo.EpgRetention
import java.util.Calendar
import java.util.TimeZone

/** Un jour proposé par la rubrique Replay : [offset] 0 = aujourd'hui, 1 = hier… ; bornes locales [startMs]..[endMs[. */
data class ReplayDay(val offset: Int, val startMs: Long, val endMs: Long)

/** Programme passé d'un jour, avec sa disponibilité en rattrapage (jamais d'URL ici). */
data class ReplayRow(val prog: EpgEntity, val availability: ReplayAvailability, val onNow: Boolean) {
    val playable: Boolean get() = availability == ReplayAvailability.AVAILABLE
}

/** Catégorie du filtre de la rubrique : seulement celles qui contiennent une chaîne à archive visible. */
data class ReplayCategory(val id: String, val name: String, val count: Int, val locked: Boolean)

/** Pourquoi la rubrique est vide. */
enum class ReplayEmpty { NO_SOURCE, NO_ARCHIVE, ALL_HIDDEN }

/**
 * Règles PURES de la rubrique Replay (aucune E/S) : chaînes de la source active qui ont une archive utilisable, filtrées par
 * le profil (catégories masquées, adultes en profil Enfants) ; jours proposés selon la durée d'archive de la chaîne (plafond
 * de [EpgRetention.MAX_ARCHIVE_DAYS] jours, comme les programmes passés gardés, #248) ; programmes déjà commencés du jour.
 */
object ReplayRubric {
    /** Filtre « toutes les chaînes ». */
    const val ALL = "__all__"

    /** La chaîne a-t-elle une archive que l'appli sait lire (gabarit M3U ou adresse Xtream) ? */
    fun supported(c: ChannelEntity, nowMs: Long): Boolean = ReplayUrls.rewindStart(c, nowMs, 0) != null

    /**
     * Chaînes à archive affichables pour le profil courant, dans l'ordre reçu (celui du Direct).
     * @param hiddenCategoryKeys clés `LIVE:source:catégorie` masquées par le profil (adultes comprises en profil Enfants)
     * @param disabledCategoryIds catégories désactivées de la source (normalement déjà purgées : garde-fou)
     */
    fun visibleChannels(
        channels: List<ChannelEntity>, providerId: Long, hiddenCategoryKeys: Set<String>, disabledCategoryIds: Set<String>,
        kids: Boolean, nowMs: Long,
    ): List<ChannelEntity> = channels.filter { c ->
        supported(c, nowMs) &&
            (c.categoryId == null || ("LIVE:$providerId:${c.categoryId}" !in hiddenCategoryKeys && c.categoryId !in disabledCategoryIds)) &&
            !(kids && (AdultHeuristic.isAdult(c.name) || AdultHeuristic.isAdult(c.title)))
    }

    /** Catégories (ordre du Direct, [cats]) qui contiennent au moins une des [channels], avec leur compteur. */
    fun categories(channels: List<ChannelEntity>, cats: List<CategoryEntity>): List<ReplayCategory> {
        val counts = channels.groupingBy { it.categoryId }.eachCount()
        return cats.mapNotNull { c -> counts[c.remoteId]?.let { n -> ReplayCategory(c.remoteId, c.name, n, c.locked) } }
    }

    /** Chaînes de la catégorie [categoryId] ; [ALL] ou catégorie inconnue (masquée depuis) : toutes. */
    fun filter(channels: List<ChannelEntity>, categoryId: String): List<ChannelEntity> {
        if (categoryId == ALL) return channels
        val inCat = channels.filter { it.categoryId == categoryId }
        return inCat.ifEmpty { channels }
    }

    /** Jours proposés : aujourd'hui, hier… tant que le jour recoupe la fenêtre d'archive (bornée à 7 jours). */
    fun days(catchupDays: Int, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): List<ReplayDay> {
        val archive = EpgRetention.archiveDays(catchupDays)
        if (archive <= 0) return emptyList()
        val earliest = nowMs - archive * EpgRetention.DAY_MS
        val out = ArrayList<ReplayDay>(archive + 1)
        for (k in 0..archive) {
            val start = Calendar.getInstance(zone).apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, -k)
            }
            val s = start.timeInMillis
            start.add(Calendar.DAY_OF_MONTH, 1)
            val e = start.timeInMillis
            if (e <= earliest) break
            out += ReplayDay(k, s, e)
        }
        return out
    }

    /** Programmes du jour [day] déjà commencés à [nowMs], triés, un seul par heure de début, avec leur disponibilité. */
    fun rows(channel: ChannelEntity, programmes: List<EpgEntity>, day: ReplayDay, nowMs: Long): List<ReplayRow> =
        programmes.asSequence()
            .filter { it.startMs >= day.startMs && it.startMs < day.endMs && it.startMs <= nowMs && it.endMs > it.startMs }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }
            .map { ReplayRow(it, ReplayUrls.availabilityAt(channel, it.startMs, nowMs), onNow = it.endMs > nowMs) }
            .toList()

    /**
     * Raison d'un écran vide, ou null s'il y a des chaînes.
     * @param archived chaînes de la source dont l'archive est lisible (avant les filtres du profil)
     * @param visible celles qui restent pour le profil courant
     */
    fun emptyReason(hasSource: Boolean, archived: Int, visible: Int): ReplayEmpty? = when {
        !hasSource -> ReplayEmpty.NO_SOURCE
        archived == 0 -> ReplayEmpty.NO_ARCHIVE
        visible == 0 -> ReplayEmpty.ALL_HIDDEN
        else -> null
    }
}
