package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés des écrans Direct et Guide TV (cartes de programme, barre d'aide). Fichier à part : aucun conflit avec [DesignStrings]. */

/** « Ensuite · 10:32 » : le premier programme à venir. */
val DesignStrings.nextWord get() = tx(R.string.dg_next_word)
val DesignStrings.nowBadge get() = tx(R.string.dg_now_badge)
val DesignStrings.nowButton get() = tx(R.string.dg_now_button)
fun DesignStrings.minutesRemaining(m: Int) = tp(R.plurals.dg_minutes_remaining, m, m)

// Barre d'aide du guide : « ◀ ▶ Programmes · ▲ ▼ Chaînes · OK Regarder · ⏪⏩ 1 jour · Retour »
val DesignStrings.helpPrograms get() = tx(R.string.dg_help_programs)
val DesignStrings.helpChannels get() = tx(R.string.dg_help_channels)
val DesignStrings.helpWatch get() = tx(R.string.dg_help_watch)
val DesignStrings.helpOneDay get() = tx(R.string.dg_help_one_day)
val DesignStrings.helpBackKey get() = tx(R.string.dg_help_back_key)

// ── Guide indisponible (échec du téléchargement du guide) et progression de son chargement ──
val DesignStrings.guideUnavailable get() = tx(R.string.dg_guide_unavailable)
val DesignStrings.guideRetry get() = tx(R.string.dg_guide_retry)
val DesignStrings.guideLoadingTitle get() = tx(R.string.dg_guide_loading_title)
val DesignStrings.guideLoadingHint get() = tx(R.string.dg_guide_loading_hint)

/** Raison courte d'un échec du guide ; le détail technique n'est que dans les journaux nettoyés. */
fun epgReason(D: DesignStrings, r: com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason): String = with(D) {
    when (r) {
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.TIMEOUT -> tx(R.string.dg_epg_reason_timeout)
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.NETWORK -> tx(R.string.dg_epg_reason_network)
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.SERVER -> tx(R.string.dg_epg_reason_server)
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.AUTH -> tx(R.string.dg_epg_reason_auth)
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.NOT_FOUND -> tx(R.string.dg_epg_reason_not_found)
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.BAD_FORMAT -> tx(R.string.dg_epg_reason_bad_format)
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.EMPTY -> tx(R.string.dg_epg_reason_empty)
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.OTHER -> tx(R.string.dg_epg_reason_other)
    }
}

private fun mo(b: Long) = "%.1f".format(java.util.Locale.US, b / 1_048_576.0)

/** « 12,4 / 45,0 Mo · 20 000 programmes » (taille inconnue : « 12,4 Mo · … »). */
fun epgProgressText(D: DesignStrings, bytes: Long, total: Long, programmes: Int): String {
    val unit = D.tx(R.string.dg_epg_progress_text)
    val size = if (total > 0) "${mo(bytes)} / ${mo(total)} $unit" else "${mo(bytes)} $unit"
    val n = java.text.NumberFormat.getIntegerInstance().format(programmes)
    return if (programmes > 0) "$size · ${D.countProgrammes.replace("%s", n)}" else size
}
// Sélecteur de groupe de chaînes du Guide TV.
val DesignStrings.guideGroupTitle get() = tx(R.string.dg_guide_group_title)
fun DesignStrings.guideGroupPill(name: String) = tx(R.string.dg_guide_group_pill, name)
val DesignStrings.guideGroupEmpty get() = tx(R.string.dg_guide_group_empty)

// Lisibilité du Guide : logos des chaînes et largeur de la colonne des noms (Réglages › Affichage).
val DesignStrings.channelLogos get() = tx(R.string.dg_channel_logos)
val DesignStrings.guideNameWidthLabel get() = tx(R.string.dg_guide_name_width_label)
fun DesignStrings.guideNameWidthName(w: com.ultratv.tv.nativeapp.ui.common.GuideNameWidth) = when (w) {
    com.ultratv.tv.nativeapp.ui.common.GuideNameWidth.NARROW -> tx(R.string.dg_guide_name_width_name_narrow)
    com.ultratv.tv.nativeapp.ui.common.GuideNameWidth.NORMAL -> tx(R.string.dg_guide_name_width_name_normal)
    com.ultratv.tv.nativeapp.ui.common.GuideNameWidth.WIDE -> tx(R.string.dg_guide_name_width_name_wide)
}
