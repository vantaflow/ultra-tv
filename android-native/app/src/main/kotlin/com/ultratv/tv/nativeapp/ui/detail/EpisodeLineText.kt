package com.ultratv.tv.nativeapp.ui.detail

import com.ultratv.tv.nativeapp.data.extmeta.EpisodeLine
import com.ultratv.tv.nativeapp.data.extmeta.TvEpisode
import com.ultratv.tv.nativeapp.data.sport.SportLogic
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.extLastEpisode
import com.ultratv.tv.nativeapp.i18n.extNextEpisode
import com.ultratv.tv.nativeapp.i18n.extSeriesEnded
import com.ultratv.tv.nativeapp.i18n.extYesterday
import com.ultratv.tv.nativeapp.i18n.locale
import com.ultratv.tv.nativeapp.i18n.sportToday
import com.ultratv.tv.nativeapp.i18n.sportTomorrow
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

/** Jour de diffusion : « Aujourd'hui », « Demain », « Hier », « sam. 14 nov. » dans la semaine (mois toujours écrit : « Mar. 17 » se lisait « mars »), sinon la date (langue de l'appli). */
fun airDayLabel(D: DesignStrings, airAt: Long, nowMs: Long): String {
    val loc = D.locale
    return when (val off = SportLogic.dayOffset(SportLogic.dayStart(airAt), nowMs)) {
        0 -> D.sportToday
        1 -> D.sportTomorrow
        -1 -> D.extYesterday
        else -> if (off in -7..7) SimpleDateFormat("EEE d MMM", loc).format(Date(airAt)).replaceFirstChar { it.titlecase(loc) }
        else DateFormat.getDateInstance(DateFormat.MEDIUM, loc).format(Date(airAt))
    }
}

/** « S2 É5 · Titre · Demain » ; éléments absents omis. */
fun episodeDetail(D: DesignStrings, ep: TvEpisode, nowMs: Long): String = listOfNotNull(
    if (ep.season != null && ep.number != null) D.episodeTag(ep.season, ep.number) else null,
    ep.name?.trim()?.takeIf { it.isNotEmpty() },
    ep.airAt?.let { airDayLabel(D, it, nowMs) },
).joinToString(" · ")

/** Ligne « Prochain épisode : … » / « Dernier épisode diffusé : … » / « Série terminée » de la fiche série ; null si rien. */
fun episodeLineText(D: DesignStrings, line: EpisodeLine?, nowMs: Long): String? = when (line) {
    null -> null
    is EpisodeLine.Next -> episodeDetail(D, line.ep, nowMs).takeIf { it.isNotEmpty() }?.let { D.extNextEpisode(it) }
    is EpisodeLine.Last -> episodeDetail(D, line.ep, nowMs).takeIf { it.isNotEmpty() }?.let { D.extLastEpisode(it) }
    EpisodeLine.Ended -> D.extSeriesEnded
}
