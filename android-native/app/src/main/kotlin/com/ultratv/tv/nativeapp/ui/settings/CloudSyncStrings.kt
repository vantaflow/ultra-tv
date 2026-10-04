package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.i18n.AppLang

/** Libellés de la synchro cloud multi-appareils (FR · EN · ES · AR), dans un fichier dédié. */
class CloudSyncStrings(private val lang: AppLang) {
    private fun t(en: String, fr: String, es: String, ar: String) = when (lang) { AppLang.French -> fr; AppLang.Spanish -> es; AppLang.Arabic -> ar; else -> en }

    val group get() = t("Cloud account", "Compte cloud", "Cuenta en la nube", "حساب السحابة")
    val syncedWithCloud get() = t("Synchronized with the cloud", "Synchronisé avec le cloud", "Sincronizado con la nube", "متزامن مع السحابة")
    val notSynced get() = t("Not synchronized yet", "Pas encore synchronisé", "Aún no sincronizado", "لم تتم المزامنة بعد")
    val syncFailed get() = t("Cloud unreachable, retrying later", "Cloud injoignable, nouvelle tentative plus tard", "Nube inaccesible, se reintentará", "السحابة غير متاحة، ستتم إعادة المحاولة")
    fun devicesCount(n: Int) = t("$n device(s)", "$n appareil(s)", "$n dispositivo(s)", "$n جهاز")
    fun ago(minutes: Long): String = when {
        minutes < 1 -> t("just now", "à l'instant", "ahora mismo", "الآن")
        minutes < 60 -> t("$minutes min ago", "il y a $minutes min", "hace $minutes min", "منذ $minutes دقيقة")
        minutes < 1440 -> t("${minutes / 60} h ago", "il y a ${minutes / 60} h", "hace ${minutes / 60} h", "منذ ${minutes / 60} ساعة")
        else -> t("${minutes / 1440} d ago", "il y a ${minutes / 1440} j", "hace ${minutes / 1440} d", "منذ ${minutes / 1440} يوم")
    }
    val syncNow get() = t("Sync now", "Synchroniser maintenant", "Sincronizar ahora", "مزامنة الآن")
    val thisDevice get() = t("This device's name", "Nom de cet appareil", "Nombre de este dispositivo", "اسم هذا الجهاز")
    val deviceNameHint get() = t("Shown in the dashboard and in sharing lists", "Affiché dans le tableau de bord et dans les listes de partage", "Se muestra en el panel y en las listas", "يظهر في لوحة التحكم وقوائم المشاركة")
    val rename get() = t("Rename", "Renommer", "Renombrar", "إعادة التسمية")
    val accountDevices get() = t("Devices on this account", "Appareils du compte", "Dispositivos de la cuenta", "أجهزة الحساب")
    val manageOnDashboard get() = t("Manage on the dashboard", "Gérer sur le tableau de bord", "Gestionar en el panel", "الإدارة في لوحة التحكم")

    val localPrivate get() = t("Local · private to this device", "Locale · privée à cet appareil", "Local · privada de este dispositivo", "محلي · خاص بهذا الجهاز")
    fun cloudShared(n: Int) = t("Cloud · shared with $n device(s)", "Cloud · partagée avec $n appareil(s)", "Nube · compartida con $n dispositivo(s)", "سحابة · مشتركة مع $n جهاز")
    val shareSource get() = t("Share this source", "Partager cette source", "Compartir esta fuente", "مشاركة هذا المصدر")
    val editSharing get() = t("Edit sharing", "Modifier le partage", "Editar el uso compartido", "تعديل المشاركة")
    val stopSharing get() = t("Stop sharing (keep locally)", "Ne plus partager (garder en local)", "Dejar de compartir (mantener local)", "إيقاف المشاركة (إبقاؤه محليًا)")
    val shareTitle get() = t("Share with…", "Partager avec…", "Compartir con…", "مشاركة مع…")
    val allDevices get() = t("All devices", "Tous les appareils", "Todos los dispositivos", "كل الأجهزة")
    val thisOne get() = t("this device", "cet appareil", "este dispositivo", "هذا الجهاز")
    val syncDisplayPrefs get() = t("Sync categories and languages", "Synchroniser catégories et langues", "Sincronizar categorías e idiomas", "مزامنة الفئات واللغات")
    val syncDisplayPrefsHint get() = t("Categories you hide and the languages you choose apply on all your devices", "Les catégories masquées et les langues choisies s’appliquent sur tous vos appareils", "Las categorías ocultas y los idiomas elegidos se aplican en todos tus dispositivos", "تُطبَّق الفئات المخفية واللغات المختارة على جميع أجهزتك")
    val confirm get() = t("Share", "Partager", "Compartir", "مشاركة")
    val offerTitle get() = t("Share this source with your other devices?", "Partager cette source avec vos autres appareils ?", "¿Compartir esta fuente con tus otros dispositivos?", "مشاركة هذا المصدر مع أجهزتك الأخرى؟")
    val offerBody get() = t("It stays on this device only unless you choose.", "Elle reste sur cet appareil tant que vous ne choisissez pas.", "Se queda solo en este dispositivo salvo que elijas.", "تبقى على هذا الجهاز ما لم تختر غير ذلك.")
    val notNow get() = t("Not now", "Pas maintenant", "Ahora no", "ليس الآن")
    val pairFirst get() = t("Pair this device with the cloud first", "Appairez d'abord cet appareil au cloud", "Empareja primero este dispositivo con la nube", "اقرن هذا الجهاز بالسحابة أولاً")

    val connectionWarning get() = t(
        "Your subscription only allows one stream at a time: watching on two devices at once will cut one of them.",
        "Votre abonnement n'autorise qu'un flux à la fois : regarder sur deux appareils en même temps coupera l'un des deux.",
        "Tu suscripción solo permite una transmisión a la vez: ver en dos dispositivos a la vez cortará una de ellas.",
        "اشتراكك يسمح ببث واحد فقط في كل مرة: المشاهدة على جهازين معًا ستقطع أحدهما.",
    )

    val removalTitle get() = t("Remove sources from this device?", "Retirer des sources de cet appareil ?", "¿Quitar fuentes de este dispositivo?", "إزالة مصادر من هذا الجهاز؟")
    fun removalBody(name: String, n: Int) = t(
        "\"$name\" is no longer shared with this device. $n favorite(s) or recording(s) depend on it.",
        "« $name » n'est plus partagée avec cet appareil. $n favori(s) ou enregistrement(s) en dépendent.",
        "«$name» ya no se comparte con este dispositivo. $n favorito(s) o grabación(es) dependen de ella.",
        "لم يعد «$name» مشتركًا مع هذا الجهاز. يعتمد عليه $n من المفضلة أو التسجيلات.",
    )
    val removeAnyway get() = t("Remove", "Retirer", "Quitar", "إزالة")
    val keepLocal get() = t("Keep locally", "Garder en local", "Mantener local", "إبقاء محليًا")
}
