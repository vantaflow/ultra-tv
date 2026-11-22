package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.EpgEntity

/**
 * Rattrapage des jours passés dans le guide (#248) : les programmes passés ne sont gardés (import et purge) que pour les
 * chaînes qui ont une archive (`catchupDays` > 0, `tv_archive_duration` d'Xtream), et seulement dans cette archive,
 * plafonnée à [MAX_ARCHIVE_DAYS] jours ; leur description est raccourcie (place limitée sur les box de 2 Go).
 * Sans chaîne à archive, rien ne change. Logique pure, testée à part.
 */
object EpgRetention {
    const val MAX_ARCHIVE_DAYS = 7
    const val DAY_MS = 24L * 3_600_000L
    /** Programmes passés gardés pour toutes les chaînes (comme avant) : 3 h. */
    const val PAST_KEEP_MS = 3L * 3_600_000L
    const val ARCHIVE_DESC_MAX = 160

    fun archiveDays(catchupDays: Int): Int = catchupDays.coerceIn(0, MAX_ARCHIVE_DAYS)

    /** Fenêtre de lecture du flux XMLTV : [base], élargie vers le passé jusqu'à la plus longue archive. */
    fun feedWindow(base: LongRange, nowMs: Long, maxArchiveDays: Int): LongRange {
        val d = archiveDays(maxArchiveDays)
        return if (d == 0) base else minOf(base.first, nowMs - d * DAY_MS)..base.last
    }

    /** Plan d'un import : [days] = jours d'archive par identifiant de chaîne (chaînes avec archive seulement). */
    class Plan(private val nowMs: Long, private val base: LongRange, private val days: Map<Long, Int>) {
        val window: LongRange = feedWindow(base, nowMs, days.values.maxOrNull() ?: 0)

        /** Programme à écrire tel quel (fenêtre normale), raccourci (passé d'une archive), ou null (écarté). */
        fun keep(e: EpgEntity): EpgEntity? {
            if (e.endMs >= base.first) return e
            val d = archiveDays(days[e.channelId] ?: 0)
            if (d == 0 || e.endMs < nowMs - d * DAY_MS) return null
            val desc = e.description
            return if (desc != null && desc.length > ARCHIVE_DESC_MAX) e.copy(description = desc.take(ARCHIVE_DESC_MAX) + "…") else e
        }

        fun apply(batch: List<EpgEntity>): List<EpgEntity> = if (days.isEmpty()) batch else batch.mapNotNull(::keep)
    }
}
