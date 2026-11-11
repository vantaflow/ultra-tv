package com.ultratv.tv.nativeapp.ui.memory

import androidx.compose.runtime.Composable
import com.ultratv.tv.nativeapp.data.memory.SizeFormat
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.LocalDs

/**
 * Libellés de Réglages › Mémoire et stockage (en / fr / es / ar), à part des autres tables pour ne pas se marcher
 * dessus. Vérifiés par TranslationsCompleteTest.
 */
class MemoryStrings(val lang: AppLang) {
    private fun t(en: String, fr: String, es: String, ar: String): String = when (lang) {
        AppLang.French -> fr
        AppLang.Spanish -> es
        AppLang.Arabic -> ar
        else -> en
    }

    fun size(bytes: Long) = SizeFormat.format(bytes, lang)

    val title get() = t("Memory and storage", "Mémoire et stockage", "Memoria y almacenamiento", "الذاكرة والتخزين")
    val subtitle get() = t(
        "Free up space and keep the app fast", "Libérer de la place et garder l’appli rapide",
        "Liberar espacio y mantener la app rápida", "تحرير مساحة والحفاظ على سرعة التطبيق",
    )

    // Espace utilisé
    val groupUsed get() = t("Space used", "Espace utilisé", "Espacio usado", "المساحة المستخدمة")
    val calculating get() = t("Calculating…", "Calcul en cours…", "Calculando…", "جارٍ الحساب…")
    val database get() = t("Catalogue database", "Base du catalogue", "Base de datos del catálogo", "قاعدة بيانات الكتالوج")
    val databaseHint get() = t("Including its write-ahead log", "Avec son journal d’écriture (WAL)", "Con su registro de escritura (WAL)", "مع سجل الكتابة (WAL)")
    val images get() = t("Image cache", "Cache d’images", "Caché de imágenes", "ذاكرة الصور المؤقتة")
    val downloads get() = t("Downloads", "Téléchargements", "Descargas", "التنزيلات")
    val timeshift get() = t("Live pause buffer", "Tampon de pause du direct", "Búfer de pausa del directo", "مخزن إيقاف البث المباشر")
    val temp get() = t("Temporary files", "Fichiers temporaires", "Archivos temporales", "الملفات المؤقتة")
    val tempHint get() = t("Sync leftovers and subtitles", "Restes de synchro et sous-titres", "Restos de sincronización y subtítulos", "بقايا المزامنة والترجمات")
    val logs get() = t("Logs", "Journaux", "Registros", "السجلات")
    val totalUsed get() = t("Total used by the app", "Total utilisé par l’appli", "Total usado por la app", "إجمالي ما يستخدمه التطبيق")
    val freeSpace get() = t("Free space on the device", "Espace libre de l’appareil", "Espacio libre del dispositivo", "المساحة الحرة في الجهاز")

    // Mémoire vive
    val groupRam get() = t("RAM", "Mémoire vive", "Memoria RAM", "الذاكرة العشوائية")
    val ramAvailable get() = t("Available memory", "Mémoire disponible", "Memoria disponible", "الذاكرة المتاحة")
    fun ramValue(avail: Long, total: Long) = t(
        "${size(avail)} of ${size(total)}", "${size(avail)} sur ${size(total)}", "${size(avail)} de ${size(total)}", "${size(avail)} من ${size(total)}",
    )
    val device get() = t("Device", "Appareil", "Dispositivo", "الجهاز")
    val deviceLimited get() = t("Limited memory", "Mémoire limitée", "Memoria limitada", "ذاكرة محدودة")
    val deviceComfortable get() = t("Comfortable", "Confortable", "Cómodo", "مريح")
    val limitedNote get() = t(
        "Limited-memory device: in Auto, the app keeps fewer images in memory, skips fade animations and turns off the Live video preview.",
        "Appareil à mémoire limitée : en Auto, l’appli garde moins d’images en mémoire, supprime les fondus et désactive l’aperçu vidéo du Direct.",
        "Dispositivo con memoria limitada: en Auto, la app guarda menos imágenes en memoria, quita los fundidos y desactiva la vista previa de vídeo del Directo.",
        "جهاز بذاكرة محدودة: في الوضع التلقائي يحتفظ التطبيق بصور أقل في الذاكرة ويلغي التلاشي ويعطّل معاينة الفيديو في البث المباشر.",
    )

    // Actions
    val groupActions get() = t("Free up space", "Libérer de la place", "Liberar espacio", "تحرير المساحة")
    val clearImages get() = t("Clear the image cache", "Vider le cache d’images", "Vaciar la caché de imágenes", "مسح ذاكرة الصور المؤقتة")
    val clearImagesHint get() = t("Posters and logos are downloaded again when needed", "Affiches et logos sont retéléchargés au besoin", "Los carteles y logos se vuelven a descargar si hace falta", "تُحمَّل الملصقات والشعارات من جديد عند الحاجة")
    val clearTemp get() = t("Delete temporary files", "Supprimer les fichiers temporaires", "Eliminar archivos temporales", "حذف الملفات المؤقتة")
    val clearTempHint get() = t("Not while a sync is running", "Jamais pendant une synchro", "Nunca durante una sincronización", "ليس أثناء المزامنة")
    val compact get() = t("Compact the database", "Compacter la base", "Compactar la base de datos", "ضغط قاعدة البيانات")
    val compactHint get() = t("Reclaims unused space; never during a sync", "Récupère l’espace inutilisé ; jamais pendant une synchro", "Recupera el espacio sin usar; nunca durante una sincronización", "يستعيد المساحة غير المستخدمة؛ ليس أثناء المزامنة")
    val clearHistory get() = t("Clear watch history", "Effacer l’historique", "Borrar el historial", "مسح سجل المشاهدة")
    val clearHistoryHint get() = t("Current profile or all profiles", "Profil courant ou tous les profils", "Perfil actual o todos los perfiles", "الملف الحالي أو كل الملفات")
    val clearResume get() = t("Clear resume positions", "Effacer les positions de reprise", "Borrar las posiciones de reanudación", "مسح مواضع الاستئناف")
    val clearResumeHint get() = t("Titles restart from the beginning", "Les titres repartent du début", "Los títulos vuelven a empezar", "تبدأ العناوين من البداية")
    val clearTimeshift get() = t("Empty the live pause buffer", "Vider le tampon de pause du direct", "Vaciar el búfer de pausa del directo", "تفريغ مخزن إيقاف البث المباشر")
    val clearTimeshiftHint get() = t("Not while the player is using it", "Pas pendant que le lecteur l’utilise", "No mientras el reproductor lo usa", "ليس أثناء استخدام المشغّل له")
    val working get() = t("Working…", "En cours…", "En curso…", "قيد التنفيذ…")

    val scopeTitle get() = t("Which profiles?", "Quels profils ?", "¿Qué perfiles?", "أي الملفات؟")
    val scopeCurrent get() = t("Current profile", "Profil courant", "Perfil actual", "الملف الحالي")
    val scopeAll get() = t("All profiles", "Tous les profils", "Todos los perfiles", "كل الملفات")

    val confirm get() = t("Confirm", "Confirmer", "Confirmar", "تأكيد")
    val cancel get() = t("Cancel", "Annuler", "Cancelar", "إلغاء")
    val confirmImagesTitle get() = t("Clear the image cache?", "Vider le cache d’images ?", "¿Vaciar la caché de imágenes?", "مسح ذاكرة الصور المؤقتة؟")
    val confirmImagesBody get() = t("Images will be downloaded again as you browse.", "Les images seront retéléchargées au fil de la navigation.", "Las imágenes se volverán a descargar mientras navegas.", "ستُحمَّل الصور من جديد أثناء التصفح.")
    val confirmTempTitle get() = t("Delete temporary files?", "Supprimer les fichiers temporaires ?", "¿Eliminar los archivos temporales?", "حذف الملفات المؤقتة؟")
    val confirmTempBody get() = t("Only leftovers of the catalogue sync and downloaded subtitles are removed.", "Seuls les restes de la synchro du catalogue et les sous-titres téléchargés sont supprimés.", "Solo se eliminan los restos de la sincronización del catálogo y los subtítulos descargados.", "تُحذف فقط بقايا مزامنة الكتالوج والترجمات التي نُزِّلت.")
    val confirmCompactTitle get() = t("Compact the database?", "Compacter la base ?", "¿Compactar la base de datos?", "ضغط قاعدة البيانات؟")
    val confirmCompactBody get() = t("It can take a few seconds to a minute. Your sources and favourites are kept.", "Cela peut prendre de quelques secondes à une minute. Vos sources et favoris sont conservés.", "Puede tardar de unos segundos a un minuto. Se conservan tus fuentes y favoritos.", "قد يستغرق ذلك من بضع ثوانٍ إلى دقيقة. تبقى مصادرك ومفضلاتك.")
    fun confirmHistoryTitle(all: Boolean) = if (all)
        t("Clear the history of all profiles?", "Effacer l’historique de tous les profils ?", "¿Borrar el historial de todos los perfiles?", "مسح سجل كل الملفات؟")
    else t("Clear this profile’s history?", "Effacer l’historique de ce profil ?", "¿Borrar el historial de este perfil?", "مسح سجل هذا الملف؟")
    val confirmHistoryBody get() = t("Recently watched lists and resume positions are emptied. This cannot be undone.", "Les listes « récemment regardé » et les positions de reprise sont vidées. Irréversible.", "Se vacían las listas de vistos recientemente y las posiciones de reanudación. No se puede deshacer.", "تُفرَّغ قوائم المشاهدة الأخيرة ومواضع الاستئناف. لا يمكن التراجع.")
    fun confirmResumeTitle(all: Boolean) = if (all)
        t("Clear resume positions of all profiles?", "Effacer les positions de reprise de tous les profils ?", "¿Borrar las posiciones de reanudación de todos los perfiles?", "مسح مواضع الاستئناف لكل الملفات؟")
    else t("Clear this profile’s resume positions?", "Effacer les positions de reprise de ce profil ?", "¿Borrar las posiciones de reanudación de este perfil?", "مسح مواضع الاستئناف لهذا الملف؟")
    val confirmResumeBody get() = t("Titles restart from the beginning. The history list is kept.", "Les titres repartent du début. La liste de l’historique est conservée.", "Los títulos vuelven a empezar. Se conserva la lista del historial.", "تبدأ العناوين من البداية. تبقى قائمة السجل.")
    val confirmTimeshiftTitle get() = t("Empty the pause buffer?", "Vider le tampon de pause ?", "¿Vaciar el búfer de pausa?", "تفريغ مخزن الإيقاف؟")
    val confirmTimeshiftBody get() = t("Temporary live recordings kept for pausing are deleted.", "Les enregistrements temporaires du direct gardés pour la pause sont supprimés.", "Se eliminan las grabaciones temporales del directo guardadas para la pausa.", "تُحذف التسجيلات المؤقتة للبث المباشر المحفوظة للإيقاف.")

    // Résultats
    fun freed(bytes: Long) = t("${size(bytes)} freed", "${size(bytes)} libérés", "${size(bytes)} liberados", "تم تحرير ${size(bytes)}")
    val nothingToFree get() = t("Nothing to free", "Rien à libérer", "Nada que liberar", "لا شيء للتحرير")
    fun cleared(n: Int) = t("$n items cleared", "$n éléments effacés", "$n elementos borrados", "تم مسح $n عنصرًا")
    val blockedSync get() = t("A sync is running — try again when it is done", "Une synchro est en cours — réessayez quand elle sera finie", "Hay una sincronización en curso: inténtalo cuando termine", "المزامنة جارية — أعد المحاولة بعد انتهائها")
    val timeshiftBusy get() = t("The pause buffer is in use by the player", "Le tampon de pause est utilisé par le lecteur", "El reproductor está usando el búfer de pausa", "المشغّل يستخدم مخزن الإيقاف حاليًا")
    val noSpace get() = t("Not enough free space to compact the database", "Pas assez d’espace libre pour compacter la base", "No hay espacio libre suficiente para compactar la base", "لا توجد مساحة كافية لضغط قاعدة البيانات")
    val failed get() = t("The action failed", "L’action a échoué", "La acción ha fallado", "فشل الإجراء")

    // Réglages
    val groupSettings get() = t("Settings", "Réglages", "Ajustes", "الإعدادات")
    val imageLimit get() = t("Image cache size", "Taille du cache d’images", "Tamaño de la caché de imágenes", "حجم ذاكرة الصور المؤقتة")
    val imageLimitHint get() = t("Applied at next launch; a smaller limit empties the cache now", "Appliquée au prochain démarrage ; une limite plus basse vide le cache tout de suite", "Se aplica al próximo inicio; un límite menor vacía la caché ahora", "تُطبَّق عند التشغيل التالي؛ حدّ أصغر يفرّغ الذاكرة الآن")
    fun autoLimit(bytes: Long) = t("Auto · ${size(bytes)}", "Auto · ${size(bytes)}", "Auto · ${size(bytes)}", "تلقائي · ${size(bytes)}")
    val auto get() = t("Auto", "Auto", "Auto", "تلقائي")
    val on get() = t("On", "Activé", "Activado", "مفعّل")
    val off get() = t("Off", "Désactivé", "Desactivado", "معطّل")
    val autoClean get() = t("Automatic cleanup", "Nettoyage automatique", "Limpieza automática", "التنظيف التلقائي")
    val autoCleanHint get() = t("At launch and daily: temporary files and image cache over the limit", "Au démarrage et chaque jour : fichiers temporaires et cache au-delà de la limite", "Al iniciar y cada día: archivos temporales y caché por encima del límite", "عند التشغيل ويوميًا: الملفات المؤقتة وذاكرة الصور فوق الحد")
    val economy get() = t("Memory saving mode", "Mode économie de mémoire", "Modo de ahorro de memoria", "وضع توفير الذاكرة")
    val economyHint get() = t("Smaller image memory, no fades, no Live video preview in Auto", "Moins d’images en mémoire, pas de fondus, pas d’aperçu vidéo du Direct en Auto", "Menos imágenes en memoria, sin fundidos, sin vista previa de vídeo del Directo en Auto", "صور أقل في الذاكرة، بلا تلاشٍ، وبلا معاينة فيديو في البث المباشر عند التلقائي")
    fun economyAuto(limited: Boolean) = "$auto · " + if (limited) on else off
}

@Composable
fun rememberMemoryStrings(): MemoryStrings {
    val lang = LocalDs.current.lang
    return androidx.compose.runtime.remember(lang) { MemoryStrings(lang) }
}
