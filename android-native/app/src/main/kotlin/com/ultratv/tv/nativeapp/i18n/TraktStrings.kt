package com.ultratv.tv.nativeapp.i18n

/**
 * Libellés Trakt « complets » (gestes depuis les fiches, À suivre, listes, réglages).
 * Fichier à part pour ne pas toucher à [DesignStrings] (évite les conflits entre branches).
 */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

// Fiches : watchlist, vu / non vu, note
val DesignStrings.traktAddWatchlist get() = t("Add to my Trakt watchlist", "Ajouter à ma watchlist Trakt", "Añadir a mi lista de Trakt", "أضف إلى قائمة المشاهدة على Trakt")
val DesignStrings.traktRemoveWatchlist get() = t("Remove from my Trakt watchlist", "Retirer de ma watchlist Trakt", "Quitar de mi lista de Trakt", "إزالة من قائمة المشاهدة على Trakt")
val DesignStrings.traktInWatchlist get() = t("In my watchlist", "Dans ma watchlist", "En mi lista", "في قائمة المشاهدة")
val DesignStrings.traktMarkWatched get() = t("Mark as watched", "Marquer comme vu", "Marcar como visto", "وضع علامة كمُشاهَد")
val DesignStrings.traktMarkUnwatched get() = t("Mark as unwatched", "Marquer comme non vu", "Marcar como no visto", "وضع علامة كغير مُشاهَد")
val DesignStrings.traktRate get() = t("Rate", "Noter", "Valorar", "تقييم")
val DesignStrings.traktRateTitle get() = t("Rate on Trakt", "Noter sur Trakt", "Valorar en Trakt", "التقييم على Trakt")
val DesignStrings.traktRemoveRating get() = t("Remove my rating", "Retirer ma note", "Quitar mi valoración", "إزالة تقييمي")
fun DesignStrings.traktYourRating(n: Int) = t("My rating $n/10", "Ma note $n/10", "Mi nota $n/10", "تقييمي $n/10")
val DesignStrings.traktButton get() = "Trakt"
fun DesignStrings.traktSeasonWatched(n: Int) = t("Mark season $n as watched", "Marquer la saison $n comme vue", "Marcar la temporada $n como vista", "وضع علامة على الموسم $n كمُشاهَد")
fun DesignStrings.traktSeasonUnwatched(n: Int) = t("Mark season $n as unwatched", "Marquer la saison $n comme non vue", "Marcar la temporada $n como no vista", "وضع علامة على الموسم $n كغير مُشاهَد")
val DesignStrings.traktEpisodesHint get() = t("Select an episode to mark it watched or unwatched", "Sélectionnez un épisode pour le marquer vu ou non vu", "Selecciona un episodio para marcarlo como visto o no visto", "اختر حلقة لوضع علامة مُشاهَدة أو غير مُشاهَدة")

// Retours
val DesignStrings.traktUpdated get() = t("Trakt updated", "Trakt mis à jour", "Trakt actualizado", "تم تحديث Trakt")
val DesignStrings.traktUnmatched get() = t("This title was not found on Trakt", "Titre introuvable sur Trakt", "Título no encontrado en Trakt", "لم يُعثر على هذا العنوان في Trakt")
val DesignStrings.traktFailed get() = t("Trakt is unavailable, try again later", "Trakt est indisponible, réessaie plus tard", "Trakt no está disponible, inténtalo más tarde", "Trakt غير متاح، حاول لاحقاً")
val DesignStrings.traktNotLinked get() = t("Link Trakt from your account page first", "Reliez d’abord Trakt depuis votre espace client", "Vincula Trakt desde tu cuenta primero", "اربط Trakt من صفحة حسابك أولاً")

// Accueil
val DesignStrings.traktUpNext get() = t("Up next (Trakt)", "À suivre (Trakt)", "A continuación (Trakt)", "التالي (Trakt)")
fun DesignStrings.traktListRow(name: String) = t("Trakt list: $name", "Liste Trakt : $name", "Lista de Trakt: $name", "قائمة Trakt: $name")

// Réglages
val DesignStrings.traktSettings get() = "Trakt"
val DesignStrings.traktAutoWatched get() = t("Mark as watched automatically at the end", "Marquer vu automatiquement à la fin", "Marcar como visto automáticamente al final", "وضع علامة مُشاهَد تلقائياً عند النهاية")
val DesignStrings.traktAutoWatchedHintOn get() = t("On: the end marks it as watched on Trakt", "Activé : la fin marque le titre comme vu sur Trakt", "Activado: el final lo marca como visto en Trakt", "عند التفعيل: تضع النهاية علامة مُشاهَد على Trakt")
val DesignStrings.traktAutoWatchedHint get() = t("Off: the end is saved as a pause on Trakt", "Désactivé : la fin est enregistrée comme une pause sur Trakt", "Desactivado: el final se guarda como una pausa en Trakt", "عند الإيقاف: تُحفظ النهاية كإيقاف مؤقت على Trakt")
val DesignStrings.traktListsHome get() = t("Trakt lists on Home", "Listes Trakt à l’accueil", "Listas de Trakt en Inicio", "قوائم Trakt في الرئيسية")
val DesignStrings.traktListsHomeHint get() = t("Up to 5 rows, only titles available in your playlist", "5 rangées au plus, seulement les titres présents dans votre playlist", "Hasta 5 filas, solo títulos disponibles en tu lista", "حتى 5 صفوف، فقط العناوين المتوفرة في قائمتك")
