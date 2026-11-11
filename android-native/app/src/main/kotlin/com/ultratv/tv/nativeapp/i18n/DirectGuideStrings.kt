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

// ── Guide indisponible (échec du téléchargement du guide) et progression de son chargement ──
val DesignStrings.guideUnavailable get() = t("Guide unavailable", "Guide indisponible", "Guía no disponible", "الدليل غير متاح")
val DesignStrings.guideRetry get() = t("Retry", "Réessayer", "Reintentar", "إعادة المحاولة")
val DesignStrings.guideLoadingTitle get() = t("Loading the guide…", "Chargement du guide…", "Cargando la guía…", "جارٍ تحميل الدليل…")
val DesignStrings.guideLoadingHint get() = t("Programmes appear here as they arrive.", "Les programmes apparaissent ici au fur et à mesure.", "Los programas aparecen aquí a medida que llegan.", "تظهر البرامج هنا تدريجيًا فور وصولها.")

/** Raison courte d'un échec du guide ; le détail technique n'est que dans les journaux nettoyés. */
fun epgReason(D: DesignStrings, r: com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason): String = with(D) {
    when (r) {
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.TIMEOUT -> t("The provider took too long to send the guide.", "Le fournisseur met trop de temps à envoyer le guide.", "El proveedor tarda demasiado en enviar la guía.", "المزوّد يستغرق وقتًا طويلًا لإرسال الدليل.")
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.NETWORK -> t("The provider cannot be reached. Check your connection.", "Le fournisseur est injoignable. Vérifiez votre connexion.", "No se puede contactar con el proveedor. Compruebe su conexión.", "تعذّر الوصول إلى المزوّد. تحقق من اتصالك.")
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.SERVER -> t("The provider's server returned an error.", "Le serveur du fournisseur a renvoyé une erreur.", "El servidor del proveedor devolvió un error.", "أعاد خادم المزوّد خطأً.")
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.AUTH -> t("The provider refused access to the guide.", "Le fournisseur refuse l'accès au guide.", "El proveedor rechaza el acceso a la guía.", "يرفض المزوّد الوصول إلى الدليل.")
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.NOT_FOUND -> t("No guide was found at this address.", "Aucun guide à cette adresse.", "No hay ninguna guía en esta dirección.", "لا يوجد دليل في هذا العنوان.")
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.BAD_FORMAT -> t("The guide file is unreadable.", "Le fichier du guide est illisible.", "El archivo de la guía no se puede leer.", "ملف الدليل غير قابل للقراءة.")
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.EMPTY -> t("The guide has no programmes for your channels.", "Le guide ne contient aucun programme pour vos chaînes.", "La guía no contiene programas para sus canales.", "لا يحتوي الدليل على برامج لقنواتك.")
        com.ultratv.tv.nativeapp.data.xmltv.EpgFailReason.OTHER -> t("The guide could not be loaded.", "Le guide n'a pas pu être chargé.", "No se pudo cargar la guía.", "تعذّر تحميل الدليل.")
    }
}

private fun mo(b: Long) = "%.1f".format(java.util.Locale.US, b / 1_048_576.0)

/** « 12,4 / 45,0 Mo · 20 000 programmes » (taille inconnue : « 12,4 Mo · … »). */
fun epgProgressText(D: DesignStrings, bytes: Long, total: Long, programmes: Int): String {
    val unit = D.t("MB", "Mo", "MB", "ميغابايت")
    val size = if (total > 0) "${mo(bytes)} / ${mo(total)} $unit" else "${mo(bytes)} $unit"
    val n = java.text.NumberFormat.getIntegerInstance().format(programmes)
    return if (programmes > 0) "$size · ${D.countProgrammes.replace("%s", n)}" else size
}
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
