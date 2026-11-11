package com.ultratv.tv.nativeapp.i18n

/** Libellés des écrans Direct et Guide TV (cartes de programme, barre d'aide). Fichier à part : aucun conflit avec [DesignStrings]. */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

/** « Ensuite · 10:32 » : le premier programme à venir. */
val DesignStrings.nextWord get() = t("Next", "Ensuite", "Después", "بعد ذلك")
val DesignStrings.nowBadge get() = t("NOW", "MAINTENANT", "AHORA", "الآن")
val DesignStrings.nowButton get() = t("Now", "Maintenant", "Ahora", "الآن")
fun DesignStrings.minutesRemaining(m: Int) = when (lang) {
    AppLang.French -> if (m <= 1) "1 min restante" else "$m min restantes"
    AppLang.Spanish -> if (m <= 1) "1 min restante" else "$m min restantes"
    AppLang.Arabic -> "متبقّي $m د"
    else -> "$m min left"
}

// Barre d'aide du guide : « ◀ ▶ Programmes · ▲ ▼ Chaînes · OK Regarder · ⏪⏩ 1 jour · Retour »
val DesignStrings.helpPrograms get() = t("Programmes", "Programmes", "Programas", "البرامج")
val DesignStrings.helpChannels get() = t("Channels", "Chaînes", "Canales", "القنوات")
val DesignStrings.helpWatch get() = t("Watch", "Regarder", "Ver", "مشاهدة")
val DesignStrings.helpOneDay get() = t("1 day", "1 jour", "1 día", "يوم")
val DesignStrings.helpBackKey get() = t("Back", "Retour", "Atrás", "رجوع")

// Sélecteur de groupe de chaînes du Guide TV.
val DesignStrings.guideGroupTitle get() = t("Channel group", "Groupe de chaînes", "Grupo de canales", "مجموعة القنوات")
fun DesignStrings.guideGroupPill(name: String) = when (lang) {
    AppLang.French -> "Groupe : $name"
    AppLang.Spanish -> "Grupo: $name"
    AppLang.Arabic -> "المجموعة: $name"
    else -> "Group: $name"
}
val DesignStrings.guideGroupEmpty get() = t("No channel with a guide in this group for this time.", "Aucune chaîne de ce groupe n'a de guide sur cette période.", "Ningún canal de este grupo tiene guía en este periodo.", "لا توجد قناة في هذه المجموعة لها دليل في هذه الفترة.")

// Lisibilité du Guide : logos des chaînes et largeur de la colonne des noms (Réglages › Affichage).
val DesignStrings.channelLogos get() = t("Channel logos", "Logos des chaînes", "Logotipos de canales", "شعارات القنوات")
val DesignStrings.guideNameWidthLabel get() = t("Guide channel column", "Colonne des chaînes du guide", "Columna de canales de la guía", "عمود القنوات في الدليل")
fun DesignStrings.guideNameWidthName(w: com.ultratv.tv.nativeapp.ui.common.GuideNameWidth) = when (w) {
    com.ultratv.tv.nativeapp.ui.common.GuideNameWidth.NARROW -> t("Narrow", "Étroite", "Estrecha", "ضيّق")
    com.ultratv.tv.nativeapp.ui.common.GuideNameWidth.NORMAL -> t("Normal", "Normale", "Normal", "عادي")
    com.ultratv.tv.nativeapp.ui.common.GuideNameWidth.WIDE -> t("Wide", "Large", "Ancha", "عريض")
}
