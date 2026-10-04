package com.ultratv.tv.nativeapp.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Textes des écrans « Claude Design », EN / FR / ES / AR côte à côte (une ligne = une chaîne,
 * impossible d'oublier une langue). Complète [Strings]. Aucune donnée factice : ce ne sont que
 * des libellés d'interface.
 */
class DesignStrings(val lang: AppLang) {
    private fun t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
        AppLang.French -> fr
        AppLang.Spanish -> es
        AppLang.Arabic -> ar
        else -> en
    }

    // Rail
    val railProfile get() = t("Profile", "Profil", "Perfil", "الملف الشخصي")
    val railSwitchProfile get() = t("Switch profile", "Changer de profil", "Cambiar de perfil", "تبديل الملف")

    // Commun
    val live get() = t("LIVE", "EN DIRECT", "EN DIRECTO", "مباشر")
    val watch get() = t("Watch", "Regarder", "Ver", "مشاهدة")
    val tvGuide get() = t("TV Guide", "Guide TV", "Guía TV", "دليل التلفاز")
    val until get() = t("until %s", "jusqu’à %s", "hasta %s", "حتى %s")
    val channelsCount get() = t("%d channels", "%d chaînes", "%d canales", "%d قناة")
    val minLeft get() = t("%d min left", "Reste %d min", "Quedan %d min", "متبقي %d د")
    val hourMinLeft get() = t("%1\$d h %2\$02d left", "Reste %1\$d h %2\$02d", "Quedan %1\$d h %2\$02d", "متبقي %1\$d س %2\$02d")

    // Accueil
    val featured get() = t("Featured", "À la une", "Destacado", "الأبرز")
    val continueWatching get() = t("Continue watching", "Reprendre la lecture", "Seguir viendo", "متابعة المشاهدة")
    val favoriteChannels get() = t("Favorite channels", "Chaînes favorites", "Canales favoritos", "القنوات المفضلة")
    val homeEmpty get() = t("Your catalog is loading…", "Votre catalogue se charge…", "Cargando tu catálogo…", "جارٍ تحميل الكتالوج…")
    val homeNoSource get() = t("Add a source to get started", "Ajoutez une source pour commencer", "Añade una fuente para empezar", "أضف مصدرًا للبدء")
    val syncCloud get() = t("Sync from the cloud", "Synchroniser depuis le cloud", "Sincronizar desde la nube", "المزامنة من السحابة")
    val syncCloudHint get() = t("Shows a short code to enter on the dashboard", "Affiche un code à saisir sur le tableau de bord", "Muestra un código para introducir en el panel", "يعرض رمزًا لإدخاله في لوحة التحكم")

    // Première synchronisation (Chargement.dc.html)
    val firstSync get() = t("FIRST SYNC", "PREMIÈRE SYNCHRONISATION", "PRIMERA SINCRONIZACIÓN", "المزامنة الأولى")
    val preparing get() = t("Preparing your catalog", "Préparation de votre catalogue", "Preparando tu catálogo", "جارٍ تجهيز الكتالوج")
    val preparingBody get() = t("Live channels arrive first. Movies, series and the TV guide continue in the background.", "Les chaînes en direct arrivent en premier. Films, séries et guide TV continuent en arrière-plan.", "Los canales en directo llegan primero. Películas, series y la guía TV siguen en segundo plano.", "تصل القنوات المباشرة أولًا. تستمر الأفلام والمسلسلات والدليل في الخلفية.")
    val etaAbout get() = t("about %s left", "environ %s restantes", "unos %s restantes", "حوالي %s متبقية")
    val etaLessThanMinute get() = t("under a minute", "moins d’une minute", "menos de un minuto", "أقل من دقيقة")
    val etaMinutes get() = t("%d min", "%d min", "%d min", "%d د")
    val watchLive get() = t("Watch live TV", "Regarder le direct", "Ver el directo", "مشاهدة المباشر")
    val availableWhenReady get() = t("Available as soon as the channels are ready", "Disponible dès que les chaînes sont prêtes", "Disponible en cuanto los canales estén listos", "متاح بمجرد جاهزية القنوات")
    val stepLive get() = t("Live channels", "Chaînes en direct", "Canales en directo", "القنوات المباشرة")
    val stepMovies get() = t("Movies", "Films", "Películas", "الأفلام")
    val stepSeries get() = t("Series", "Séries", "Series", "المسلسلات")
    val stepGuide get() = t("TV guide", "Guide TV", "Guía TV", "دليل التلفاز")
    val stateWaiting get() = t("Waiting", "En attente", "En espera", "في الانتظار")
    val stateDone get() = t("Done", "Terminé", "Listo", "اكتمل")
    val stateFailed get() = t("Failed", "Échec", "Error", "فشل")
    val countChannels get() = t("%s channels", "%s chaînes", "%s canales", "%s قناة")
    val countMovies get() = t("%s movies", "%s films", "%s películas", "%s فيلم")
    val countSeries get() = t("%s series", "%s séries", "%s series", "%s مسلسل")
    val countProgrammes get() = t("%s programmes", "%s programmes", "%s programas", "%s برنامج")
    val nextOpenInstant get() = t("Next launch will be instant: the catalog is kept on the device and updated in the background.", "La prochaine ouverture sera instantanée : le catalogue est gardé sur l’appareil et mis à jour en arrière-plan.", "La próxima apertura será instantánea: el catálogo se guarda en el dispositivo y se actualiza en segundo plano.", "الفتح التالي فوري: يُحفظ الكتالوج على الجهاز ويُحدَّث في الخلفية.")
    val hintOk get() = t("OK", "OK")
    val hintWatch get() = t("Watch", "Regarder", "Ver", "مشاهدة")
    val retry get() = t("Retry", "Réessayer", "Reintentar", "إعادة المحاولة")
    val fixSource get() = t("Fix the source", "Corriger la source", "Corregir la fuente", "تصحيح المصدر")
    val syncing get() = t("Syncing", "Synchronisation", "Sincronizando", "جارٍ المزامنة")
}

val LocalDs = compositionLocalOf { DesignStrings(AppLang.English) }

@Composable
fun designStringsFor(lang: AppLang): DesignStrings {
    val resolved = if (lang == AppLang.System) {
        val sys = LocalConfiguration.current.locales.get(0)?.language ?: "en"
        AppLang.entries.firstOrNull { it.code == sys } ?: AppLang.English
    } else lang
    return remember(resolved) { DesignStrings(resolved) }
}
