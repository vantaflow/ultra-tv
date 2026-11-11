package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.data.repo.Genre
import com.ultratv.tv.nativeapp.ui.catalog.CatalogLayout
import com.ultratv.tv.nativeapp.ui.catalog.CategorySource

/** Textes des écrans Films / Séries « catégories + grille » et des genres automatiques (EN / FR / ES / AR). */
private fun DesignStrings.t(en: String, fr: String, es: String, ar: String): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

fun DesignStrings.catalogCategoriesTitle(movies: Boolean) =
    if (movies) t("Movies · Categories", "Films · Catégories", "Películas · Categorías", "الأفلام · الفئات")
    else t("Series · Categories", "Séries · Catégories", "Series · Categorías", "المسلسلات · الفئات")

fun DesignStrings.categoriesCount(n: Int) = t("$n categories", "$n catégories", "$n categorías", "$n فئة")

fun DesignStrings.catalogSourceName(s: CategorySource) = when (s) {
    CategorySource.GENRES -> t("Auto genres", "Genres automatiques", "Géneros automáticos", "أنواع تلقائية")
    CategorySource.PROVIDER -> t("Provider categories", "Catégories du fournisseur", "Categorías del proveedor", "فئات المزوّد")
}

fun DesignStrings.catalogLayoutName(l: CatalogLayout) = when (l) {
    CatalogLayout.SPLIT -> t("List + grid", "Liste + grille", "Lista + cuadrícula", "قائمة + شبكة")
    CatalogLayout.ROWS -> t("Rows", "Rangées", "Filas", "صفوف")
}

/** [setting] 0 = automatique ; [auto] = nombre de colonnes que l'automatique retient. */
fun DesignStrings.catalogColumnsName(setting: Int, auto: Int) =
    if (setting == 0) t("Auto ($auto)", "Auto ($auto)", "Auto ($auto)", "تلقائي ($auto)")
    else t("$setting columns", "$setting colonnes", "$setting columnas", "$setting أعمدة")

val DesignStrings.genreHint get() = t(
    "Genres are being detected in the background. Categories appear gradually.",
    "Les genres sont détectés en arrière-plan. Les catégories apparaissent au fur et à mesure.",
    "Los géneros se detectan en segundo plano. Las categorías aparecen poco a poco.",
    "يجري اكتشاف الأنواع في الخلفية. تظهر الفئات تدريجيًا.",
)

fun DesignStrings.genreDetecting(done: Int, total: Int, percent: Int): String {
    fun n(v: Int): String = String.format(java.util.Locale.ROOT, "%,d", v).replace(',', if (lang == AppLang.French || lang == AppLang.Spanish) ' ' else ',')
    val pct = if (lang == AppLang.French) "$percent %" else "$percent%"
    return t("Detecting genres", "Détection des genres", "Detección de géneros", "اكتشاف الأنواع") + " ${n(done)} / ${n(total)} · $pct"
}

val DesignStrings.catalogEmptyDetecting get() = t(
    "No titles with a known genre yet.", "Aucun titre avec un genre connu pour l'instant.",
    "Aún no hay títulos con género conocido.", "لا توجد عناوين ذات نوع معروف بعد.",
)

/** Nom localisé d'un genre automatique (code de [Genre]) ; un code inconnu est rendu tel quel. */
fun DesignStrings.genreName(code: String): String = when (runCatching { Genre.valueOf(code) }.getOrNull()) {
    Genre.ACTION -> t("Action", "Action", "Acción", "أكشن")
    Genre.ADVENTURE -> t("Adventure", "Aventure", "Aventura", "مغامرة")
    Genre.ANIMATION -> t("Animation", "Animation", "Animación", "رسوم متحركة")
    Genre.ANIME -> t("Anime", "Anime", "Anime", "أنمي")
    Genre.COMEDY -> t("Comedy", "Comédie", "Comedia", "كوميديا")
    Genre.CRIME -> t("Crime", "Policier", "Crimen", "جريمة")
    Genre.DOCUMENTARY -> t("Documentary", "Documentaire", "Documental", "وثائقي")
    Genre.DRAMA -> t("Drama", "Drame", "Drama", "دراما")
    Genre.FAMILY -> t("Family", "Famille", "Familia", "عائلي")
    Genre.FANTASY -> t("Fantasy", "Fantastique", "Fantasía", "فانتازيا")
    Genre.HISTORY -> t("History", "Histoire", "Historia", "تاريخ")
    Genre.HORROR -> t("Horror", "Horreur", "Terror", "رعب")
    Genre.MUSIC -> t("Music", "Musique", "Música", "موسيقى")
    Genre.MYSTERY -> t("Mystery", "Mystère", "Misterio", "غموض")
    Genre.ROMANCE -> t("Romance", "Romance", "Romance", "رومانسي")
    Genre.SCIFI -> t("Sci-Fi", "Science-fiction", "Ciencia ficción", "خيال علمي")
    Genre.THRILLER -> t("Thriller", "Thriller", "Suspense", "إثارة")
    Genre.WAR -> t("War", "Guerre", "Bélica", "حرب")
    Genre.WESTERN -> t("Western", "Western", "Western", "وسترن")
    Genre.OTHER -> t("Other", "Autres", "Otros", "أخرى")
    null -> code
}
