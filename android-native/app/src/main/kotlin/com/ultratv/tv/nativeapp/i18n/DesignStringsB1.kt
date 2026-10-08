package com.ultratv.tv.nativeapp.i18n

/**
 * Libellés du lot B1 (actions sur un programme, enregistrements programmés, fiches enrichies).
 * Fichier à part pour ne pas toucher à [DesignStrings] (évite les conflits entre branches).
 */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

// Dialogue « programme » (GuideActions)
val DesignStrings.actReplay get() = t("Watch from the start", "Revoir depuis le début", "Ver desde el principio", "المشاهدة من البداية")
val DesignStrings.actReplayHint get() = t("Source replay", "Replay de la source", "Repetición de la fuente", "إعادة من المصدر")
val DesignStrings.actReplayNone get() = t("Replay not available for this programme", "Pas de replay pour ce programme", "Sin repetición para este programa", "لا توجد إعادة لهذا البرنامج")
val DesignStrings.actWatchChannel get() = t("Watch the channel", "Regarder la chaîne", "Ver el canal", "مشاهدة القناة")
val DesignStrings.actWatchChannelHint get() = t("Live now", "En direct maintenant", "En directo ahora", "مباشر الآن")
val DesignStrings.actRemind get() = t("Remind me", "Me rappeler", "Recordármelo", "ذكّرني")
val DesignStrings.actRemindHint get() = t("One minute before it starts", "Une minute avant le début", "Un minuto antes de empezar", "قبل البدء بدقيقة")
val DesignStrings.actRecord get() = t("Record", "Enregistrer", "Grabar", "تسجيل")
val DesignStrings.actRecordHint get() = t("This programme or the whole series", "Programme ou série entière", "Programa o serie completa", "البرنامج أو المسلسل كاملًا")
val DesignStrings.actRecordThis get() = t("This programme only", "Ce programme seulement", "Solo este programa", "هذا البرنامج فقط")
val DesignStrings.actRecordSeries get() = t("Whole series", "Série entière", "Serie completa", "المسلسل كاملًا")
val DesignStrings.actRecordSeriesHint get() = t("Every broadcast with the same title on this channel", "Chaque diffusion du même titre sur cette chaîne", "Cada emisión con el mismo título en este canal", "كل بث بنفس العنوان على هذه القناة")
val DesignStrings.yesterday get() = t("Yesterday", "Hier", "Ayer", "أمس")
val DesignStrings.replayTag get() = t("Replay", "Replay", "Repetición", "إعادة")
fun DesignStrings.replayBadge(days: Int) = t("REPLAY $days D", "REPLAY $days J", "REPETICIÓN $days D", "إعادة $days أيام")

// Rappels et enregistrements : retours à l'utilisateur
val DesignStrings.remindSet get() = t("Reminder set", "Rappel programmé", "Recordatorio programado", "تم ضبط التذكير")
val DesignStrings.startsInOneMin get() = t("Starts in 1 min", "Commence dans 1 min", "Empieza en 1 min", "يبدأ بعد دقيقة")
val DesignStrings.watchWord get() = t("Watch", "Regarder", "Ver", "مشاهدة")
fun DesignStrings.recScheduled(n: Int) = when (lang) {
    AppLang.French -> if (n <= 1) "Enregistrement programmé" else "$n enregistrements programmés"
    AppLang.Spanish -> if (n <= 1) "Grabación programada" else "$n grabaciones programadas"
    AppLang.Arabic -> if (n <= 1) "تمت جدولة التسجيل" else "تمت جدولة $n تسجيلات"
    else -> if (n <= 1) "Recording scheduled" else "$n recordings scheduled"
}
val DesignStrings.recNoSpace get() = t("Not enough free space to record this", "Pas assez d’espace libre pour enregistrer", "No hay espacio suficiente para grabar", "لا توجد مساحة كافية للتسجيل")
val DesignStrings.recNothing get() = t("This programme is already scheduled or over", "Ce programme est déjà programmé ou terminé", "Este programa ya está programado o terminó", "هذا البرنامج مجدول بالفعل أو انتهى")
val DesignStrings.recScheduledBadge get() = t("SCHED.", "PROG.", "PROG.", "مجدول")
val DesignStrings.recConnectionBusy get() = t("A recording is in progress on your only connection", "Un enregistrement est en cours sur votre seule connexion", "Hay una grabación en curso en su única conexión", "هناك تسجيل جارٍ على اتصالك الوحيد")

// Fiches enrichies TMDB
val DesignStrings.trailerLabel get() = t("Trailer", "Bande-annonce", "Tráiler", "الإعلان")
val DesignStrings.tmdbAttribution get() = t(
    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
    "Ce produit utilise l’API TMDB mais n’est ni approuvé ni certifié par TMDB.",
    "Este producto utiliza la API de TMDB pero no está avalado ni certificado por TMDB.",
    "يستخدم هذا المنتج واجهة TMDB لكنه غير معتمد أو مصدّق من TMDB.",
)

/** Locale de FORMATAGE (noms de jours, de langues, dates) alignée sur la langue de l'application, pas sur celle de l'appareil. Chiffres latins en arabe. */
val DesignStrings.locale: java.util.Locale get() = when (lang) {
    AppLang.French -> java.util.Locale.FRENCH
    AppLang.Spanish -> java.util.Locale("es")
    AppLang.Arabic -> java.util.Locale.forLanguageTag("ar-u-nu-latn")
    else -> java.util.Locale.ENGLISH
}

// Trakt : rangées de l'accueil (seulement ce qui est disponible dans la playlist) et marque « vu »
val DesignStrings.traktWatchlist get() = t("My Trakt watchlist", "Ma watchlist Trakt", "Mi lista de seguimiento de Trakt", "قائمة المشاهدة على Trakt")
val DesignStrings.traktRecommended get() = t("Recommended for you (Trakt)", "Recommandé pour toi (Trakt)", "Recomendado para ti (Trakt)", "موصى به لك (Trakt)")
val DesignStrings.traktTrending get() = t("Trending (Trakt)", "Tendances (Trakt)", "Tendencias (Trakt)", "الأكثر رواجاً (Trakt)")
val DesignStrings.traktPopular get() = t("Popular (Trakt)", "Populaires (Trakt)", "Populares (Trakt)", "الأكثر شعبية (Trakt)")
val DesignStrings.traktWatched get() = t("Watched", "Vu", "Visto", "تمت المشاهدة")
