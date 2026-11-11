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
