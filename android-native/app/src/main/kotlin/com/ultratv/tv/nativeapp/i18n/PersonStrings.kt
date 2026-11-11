package com.ultratv.tv.nativeapp.i18n

/** Libellés de l'en-tête des pages acteur / réalisateur. Fichier à part, comme les autres chantiers de libellés. */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

/** Métier principal d'après le département TMDB ; null si inconnu (rien n'est affiché). */
fun DesignStrings.personRole(department: String?): String? = when (department) {
    "Acting" -> t("Actor", "Acteur", "Actor", "ممثل")
    "Directing" -> t("Director", "Réalisateur", "Director", "مخرج")
    "Writing" -> t("Writer", "Scénariste", "Guionista", "كاتب")
    "Production" -> t("Producer", "Producteur", "Productor", "منتج")
    else -> null
}

fun DesignStrings.personBorn(date: String) = t("born $date", "né(e) le $date", "nac. $date", "وُلد $date")
val DesignStrings.personReadMore get() = t("Read more", "Lire la suite", "Leer más", "اقرأ المزيد")
val DesignStrings.personReadLess get() = t("Show less", "Réduire", "Mostrar menos", "عرض أقل")
fun DesignStrings.personInPlaylist(n: Int) = t("In your playlist · $n", "Dans votre playlist · $n", "En tu lista · $n", "في قائمتك · $n")
val DesignStrings.personBadgeMovie get() = t("MOVIE", "FILM", "PELÍCULA", "فيلم")
val DesignStrings.personBadgeSeries get() = t("SERIES", "SÉRIE", "SERIE", "مسلسل")
